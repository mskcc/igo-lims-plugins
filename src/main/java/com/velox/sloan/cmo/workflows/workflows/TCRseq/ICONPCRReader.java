package com.velox.sloan.cmo.workflows.workflows.TCRseq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task-entry plugin: reads an IconPCR export CSV and updates {@link #FIELD_PCR_CYCLE} on {@code TCRSeqPrepProtocol3}
 * records <b>already attached</b> to the active workflow step (does not create new protocol records).
 * <p>
 * Expected CSV layout (matches instrument export):
 * <ul>
 *   <li>A header row containing {@code Timestamp} (typically column 2 if column 1 is empty) and remaining columns are well IDs (e.g. D3, E3, A4).</li>
 *   <li>Comma- or semicolon-separated plain text CSV (not Apple Numbers / Excel {@code .xlsx} — use <i>Export to CSV</i>).</li>
 *   <li>The row below that holds per-column <b>sample identifiers</b> (IGO IDs on top of each sample column), or {@code U} / blank for well-only matching.</li>
 *   <li>Subsequent rows start with {@code Cycle N} and contain fluorescence/read values per well.</li>
 * </ul>
 * For each column, <b>PCR cycle</b> is the <b>last</b> (highest) cycle number whose row still has a numeric value in that
 * column — i.e. the last amplification cycle with a reading for that sample.
 * <p>
 * Each CSV column is matched to an attached protocol by IGO ID from the ID row when present, otherwise by well
 * (CSV well header vs. parent {@code Sample} {@value #FIELD_ROW_POSITION} + {@value #FIELD_COL_POSITION}).
 * <p>
 * Field updated: {@value #FIELD_PCR_CYCLE}. Enable via task option: {@value #TASK_OPTION_KEY}.
 */
public class ICONPCRReader extends DefaultGenericPlugin {

    public static final String TASK_OPTION_KEY = "IMPORT ICON PCR OUTPUT";
    public static final String PROTOCOL_DATA_TYPE = "TCRSeqPrepProtocol3";
    public static final String FIELD_SAMPLE_ID = "SampleId";
    public static final String FIELD_SAMPLE_NAME = "OtherSampleId";
    public static final String FIELD_PCR_CYCLE = "PCRcycle";

    private static final Pattern IGO_ID_PATTERN = Pattern.compile("^\\d+_[A-Za-z0-9].*$");

    /** Sample fields used to build plate well (must match IconPCR column headers, e.g. D3). */
    public static final String FIELD_ROW_POSITION = "RowPosition";
    public static final String FIELD_COL_POSITION = "ColPosition";

    private final IgoLimsPluginUtils util = new IgoLimsPluginUtils();

    public ICONPCRReader() {
        setTaskEntry(true);
        setOrder(PluginOrder.FIRST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE
                && activeTask.getTask().getTaskOptions().containsKey(TASK_OPTION_KEY);
    }

    @Override
    public PluginResult run() throws Throwable {
        try {
            String path = clientCallback.showFileDialog("Select IconPCR CSV export", ".csv");
            if (StringUtils.isBlank(path)) {
                logInfo("User cancelled file selection.");
                return new PluginResult(true);
            }
            if (!util.isCsvFile(path)) {
                clientCallback.displayError("Please select a .csv file.");
                return new PluginResult(false);
            }
            byte[] bytes = clientCallback.readBytes(path);
            if (looksLikeZipArchive(bytes)) {
                clientCallback.displayError(
                        "This file is a ZIP archive (often a mis-saved Apple Numbers document), not a plain CSV. "
                                + "In Numbers: File → Export To → CSV…, or open in Excel and Save As CSV (UTF-8).");
                return new PluginResult(false);
            }
            List<String> rawLines = util.readDataFromCsvFile(bytes);
            if (rawLines == null || rawLines.isEmpty()) {
                clientCallback.displayError("CSV file is empty.");
                return new PluginResult(false);
            }
            List<String> lines = new ArrayList<>(rawLines.size());
            for (String row : rawLines) {
                if (row == null) {
                    lines.add("");
                } else {
                    lines.add(row.replaceFirst("^\uFEFF", "").trim());
                }
            }

            IconPcrParseResult parsed;
            try {
                parsed = parseIconPcrCsv(lines);
            } catch (IllegalArgumentException ex) {
                clientCallback.displayError(ex.getMessage());
                logError(ExceptionUtils.getStackTrace(ex));
                return new PluginResult(false);
            }

            List<DataRecord> protocols = activeTask.getAttachedDataRecords(PROTOCOL_DATA_TYPE, user);
            if (protocols == null || protocols.isEmpty()) {
                clientCallback.displayError("No " + PROTOCOL_DATA_TYPE + " records attached to this task. Attach them to the step before importing.");
                return new PluginResult(false);
            }

            Map<String, List<DataRecord>> protocolsByIgoId = new HashMap<>();
            Map<String, List<DataRecord>> protocolsByWell = new HashMap<>();
            indexAttachedProtocols(protocols, protocolsByIgoId, protocolsByWell);

            int updatedCount = 0;
            List<String> warnings = new ArrayList<>();

            for (Map.Entry<String, IconColumnInfo> e : parsed.getColumnsByWell().entrySet()) {
                String well = e.getKey();
                IconColumnInfo col = e.getValue();
                int colIndex = col.getColumnIndex();
                Integer pcrCycle = parsed.getLastPcrCycleByColumnIndex().get(colIndex);
                if (pcrCycle == null) {
                    warnings.add("No numeric cycle data for well " + well + "; skipped.");
                    continue;
                }

                List<DataRecord> targets = resolveAttachedProtocols(col.getIdToken(), well, protocolsByIgoId, protocolsByWell);
                if (targets.isEmpty()) {
                    warnings.add("No attached " + PROTOCOL_DATA_TYPE + " for well " + well + " (ID token: " + col.getIdToken() + ").");
                    continue;
                }
                if (targets.size() > 1) {
                    warnings.add("Multiple " + PROTOCOL_DATA_TYPE + " records matched well " + well + "; updating all " + targets.size() + ".");
                }

                for (DataRecord protocol : targets) {
                    // Use setFields + Long (datatype alignment); omit storeAndCommit so changes stay in the active-task
                    // transaction and commit with task validation—same pattern as SampleToPlateAssignmentViaFileUplaod.
                    Map<String, Object> cycleOnly = new HashMap<>();
                    cycleOnly.put(FIELD_PCR_CYCLE, Long.valueOf(pcrCycle));
                    protocol.setFields(cycleOnly, user);
                    updatedCount++;
                    logInfo(String.format("Updated %s recordId=%s well %s PCRCycle=%d",
                            PROTOCOL_DATA_TYPE, protocol.getRecordId(), well, pcrCycle));
                }
            }

            if (updatedCount == 0) {
                clientCallback.displayError("No " + PROTOCOL_DATA_TYPE + " records were updated. Check CSV format, wells, sample IDs, and attachments.");
                if (!warnings.isEmpty()) {
                    clientCallback.displayError(String.join("\n", warnings));
                }
                return new PluginResult(false);
            }

            StringBuilder msg = new StringBuilder("Updated PCRCycle on ").append(updatedCount).append(" ").append(PROTOCOL_DATA_TYPE).append(" record(s).");
            msg.append(" Validate/save this workflow step so changes commit to the database.");
            if (!warnings.isEmpty()) {
                msg.append("\n\nWarnings:\n").append(String.join("\n", warnings));
            }
            clientCallback.displayInfo(msg.toString());
            return new PluginResult(true);
        } catch (IOException ioe) {
            logError(ExceptionUtils.getStackTrace(ioe));
            clientCallback.displayError("Failed to read CSV: " + ioe.getMessage());
            return new PluginResult(false);
        } catch (IoError | ServerException ex) {
            logError(ExceptionUtils.getStackTrace(ex));
            clientCallback.displayError(ex.getMessage());
            return new PluginResult(false);
        }
    }

    /**
     * Indexes attached protocol records by IGO {@link #FIELD_SAMPLE_ID} (on the protocol or parent {@code Sample})
     * and by parent sample well.
     */
    private void indexAttachedProtocols(List<DataRecord> protocols,
                                        Map<String, List<DataRecord>> byIgoId,
                                        Map<String, List<DataRecord>> byWell) throws RemoteException, IoError, ServerException {
        for (DataRecord protocol : protocols) {
            String igo = readProtocolSampleId(protocol);
            if (StringUtils.isNotBlank(igo)) {
                byIgoId.computeIfAbsent(normalizeIgoId(igo), k -> new ArrayList<>()).add(protocol);
            }
            DataRecord parentSample = resolveParentSample(protocol);
            if (parentSample != null) {
                String w = readWellFromSample(parentSample);
                if (w != null) {
                    byWell.computeIfAbsent(normalizeWell(w), k -> new ArrayList<>()).add(protocol);
                }
            }
        }
    }

    private DataRecord resolveParentSample(DataRecord protocol) throws RemoteException, IoError, ServerException {
        List<DataRecord> parents = protocol.getParentsOfType("Sample", user);
        if (parents == null || parents.isEmpty()) {
            return null;
        }
        return parents.get(0);
    }

    private String readProtocolSampleId(DataRecord protocol) throws RemoteException, IoError, ServerException {
        try {
            String sid = protocol.getStringVal(FIELD_SAMPLE_ID, user);
            if (StringUtils.isNotBlank(sid)) {
                return sid;
            }
        } catch (NotFound e) {
            logInfo(PROTOCOL_DATA_TYPE + " missing SampleId on record; trying parent Sample.");
        }
        DataRecord parent = resolveParentSample(protocol);
        if (parent != null) {
            try {
                return parent.getStringVal(FIELD_SAMPLE_ID, user);
            } catch (NotFound e) {
                logInfo("Parent Sample missing SampleId for protocol indexing.");
            }
        }
        return "";
    }

    private List<DataRecord> resolveAttachedProtocols(String idToken, String wellHeader,
                                                      Map<String, List<DataRecord>> protocolsByIgoId,
                                                      Map<String, List<DataRecord>> protocolsByWell) {
        String token = StringUtils.trimToEmpty(idToken);
        if (StringUtils.isNotBlank(token) && !"U".equalsIgnoreCase(token) && IGO_ID_PATTERN.matcher(token).matches()) {
            List<DataRecord> list = protocolsByIgoId.get(normalizeIgoId(token));
            if (list != null && !list.isEmpty()) {
                return new ArrayList<>(list);
            }
        }
        List<DataRecord> byW = protocolsByWell.get(normalizeWell(wellHeader));
        return byW != null ? new ArrayList<>(byW) : Collections.emptyList();
    }

    /**
     * Well = {@link #FIELD_ROW_POSITION} + {@link #FIELD_COL_POSITION} (trimmed), e.g. {@code D} + {@code 3} → {@code D3}.
     * Matching to the CSV uses {@link #normalizeWell(String)} (case-insensitive, no spaces).
     */
    private String readWellFromSample(DataRecord sample) throws RemoteException, ServerException {
        try {
            Object rowObj = sample.getValue(FIELD_ROW_POSITION, user);
            Object colObj = sample.getValue(FIELD_COL_POSITION, user);
            if (rowObj == null || colObj == null) {
                return null;
            }
            String rowPart = StringUtils.trimToEmpty(rowObj.toString());
            String colPart = StringUtils.trimToEmpty(colObj.toString());
            if (StringUtils.isAnyBlank(rowPart, colPart)) {
                return null;
            }
            return rowPart + colPart;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeIgoId(String id) {
        return StringUtils.trimToEmpty(id);
    }

    private static String normalizeWell(String well) {
        if (well == null) {
            return "";
        }
        String t = well.trim().toUpperCase().replaceAll("\\s+", "");
        return t;
    }

    /**
     * Parses IconPCR CSV: finds Timestamp header row, ID row, and cycle rows; computes last cycle index per column.
     * Tries comma-separated lines first, then semicolon (common in European Excel exports).
     */
    static IconPcrParseResult parseIconPcrCsv(List<String> lines) {
        try {
            return parseIconPcrCsvWithDelimiter(lines, ',');
        } catch (IllegalArgumentException exComma) {
            try {
                return parseIconPcrCsvWithDelimiter(lines, ';');
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException(
                        "Could not parse as IconPCR CSV. Need a plain text export with a row containing \"Timestamp\" "
                                + "and well columns (e.g. D3), comma- or semicolon-separated. "
                                + "If you used Apple Numbers, choose File → Export To → CSV (a .numbers file is not CSV). "
                                + "Detail: " + exComma.getMessage());
            }
        }
    }

    static boolean looksLikeZipArchive(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        // Local file header (PK\x03\x04), empty zip (PK\x05\x06), ZIP64 spanning (PK\x07\x08)
        return bytes[0] == 'P' && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
                && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
    }

    private static Pattern cycleRowPattern(char delimiter) {
        return Pattern.compile("^Cycle\\s+(\\d+)\\s*" + Pattern.quote(String.valueOf(delimiter)), Pattern.CASE_INSENSITIVE);
    }

    private static IconPcrParseResult parseIconPcrCsvWithDelimiter(List<String> lines, char delimiter) {
        Pattern cyclePattern = cycleRowPattern(delimiter);

        int headerLineIdx = -1;
        String[] headerCells = null;
        int timestampCol = -1;
        for (int i = 0; i < lines.size(); i++) {
            String[] cells = splitCsvLine(lines.get(i), delimiter);
            for (int c = 0; c < cells.length; c++) {
                if (!"Timestamp".equalsIgnoreCase(StringUtils.trimToEmpty(cells[c]))) {
                    continue;
                }
                // Need at least one column after Timestamp for well headers
                if (c + 1 >= cells.length) {
                    continue;
                }
                headerLineIdx = i;
                headerCells = cells;
                timestampCol = c;
                break;
            }
            if (headerLineIdx >= 0) {
                break;
            }
        }
        if (headerLineIdx < 0 || headerCells == null || timestampCol < 0) {
            throw new IllegalArgumentException("No row with \"Timestamp\" and well columns found.");
        }

        int idRowIdx = headerLineIdx + 1;
        if (idRowIdx >= lines.size()) {
            throw new IllegalArgumentException("CSV is missing the sample ID row below the well headers.");
        }
        String[] idCells = splitCsvLine(lines.get(idRowIdx), delimiter);

        Map<String, IconColumnInfo> columnsByWell = new LinkedHashMap<>();
        for (int c = timestampCol + 1; c < headerCells.length; c++) {
            String well = StringUtils.trimToEmpty(headerCells[c]);
            if (StringUtils.isBlank(well)) {
                continue;
            }
            String idToken = c < idCells.length ? StringUtils.trimToEmpty(idCells[c]) : "";
            columnsByWell.put(normalizeWell(well), new IconColumnInfo(c, well, idToken));
        }
        if (columnsByWell.isEmpty()) {
            throw new IllegalArgumentException("No well columns found after Timestamp.");
        }

        List<CycleRow> cycleRows = new ArrayList<>();
        for (int r = idRowIdx + 1; r < lines.size(); r++) {
            String line = lines.get(r);
            Matcher m = cyclePattern.matcher(line);
            if (!m.find()) {
                continue;
            }
            int cycleNum = Integer.parseInt(m.group(1));
            String[] rowCells = splitCsvLine(line, delimiter);
            cycleRows.add(new CycleRow(cycleNum, rowCells));
        }
        if (cycleRows.isEmpty()) {
            throw new IllegalArgumentException("No rows starting with 'Cycle N' were found.");
        }

        Map<Integer, Integer> lastPcrCycleByColumnIndex = new HashMap<>();
        for (IconColumnInfo col : columnsByWell.values()) {
            int colIdx = col.getColumnIndex();
            Integer lastCycle = null;
            for (int i = cycleRows.size() - 1; i >= 0; i--) {
                CycleRow cr = cycleRows.get(i);
                if (colIdx >= cr.getCells().length) {
                    continue;
                }
                String cell = StringUtils.trimToEmpty(cr.getCells()[colIdx]);
                if (isNumericReading(cell)) {
                    lastCycle = cr.getCycleNumber();
                    break;
                }
            }
            if (lastCycle != null) {
                lastPcrCycleByColumnIndex.put(colIdx, lastCycle);
            }
        }

        return new IconPcrParseResult(columnsByWell, lastPcrCycleByColumnIndex);
    }

    static boolean isNumericReading(String cell) {
        if (StringUtils.isBlank(cell)) {
            return false;
        }
        try {
            Double.parseDouble(cell);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Split on delimiter; IconPCR exports typically have no quoted delimiters inside fields. */
    static String[] splitCsvLine(String line, char delimiter) {
        if (line == null) {
            return new String[0];
        }
        return line.split(Pattern.quote(String.valueOf(delimiter)), -1);
    }

    /** Split on comma (convenience for callers/tests). */
    static String[] splitCsvLine(String line) {
        return splitCsvLine(line, ',');
    }

    static final class IconColumnInfo {
        private final int columnIndex;
        private final String wellLabel;
        private final String idToken;

        IconColumnInfo(int columnIndex, String wellLabel, String idToken) {
            this.columnIndex = columnIndex;
            this.wellLabel = wellLabel;
            this.idToken = idToken;
        }

        int getColumnIndex() {
            return columnIndex;
        }

        String getWellLabel() {
            return wellLabel;
        }

        String getIdToken() {
            return idToken;
        }
    }

    static final class CycleRow {
        private final int cycleNumber;
        private final String[] cells;

        CycleRow(int cycleNumber, String[] cells) {
            this.cycleNumber = cycleNumber;
            this.cells = cells;
        }

        int getCycleNumber() {
            return cycleNumber;
        }

        String[] getCells() {
            return cells;
        }
    }

    static final class IconPcrParseResult {
        private final Map<String, IconColumnInfo> columnsByWell;
        private final Map<Integer, Integer> lastPcrCycleByColumnIndex;

        IconPcrParseResult(Map<String, IconColumnInfo> columnsByWell,
                           Map<Integer, Integer> lastPcrCycleByColumnIndex) {
            this.columnsByWell = columnsByWell;
            this.lastPcrCycleByColumnIndex = lastPcrCycleByColumnIndex;
        }

        Map<String, IconColumnInfo> getColumnsByWell() {
            return columnsByWell;
        }

        Map<Integer, Integer> getLastPcrCycleByColumnIndex() {
            return lastPcrCycleByColumnIndex;
        }
    }
}
