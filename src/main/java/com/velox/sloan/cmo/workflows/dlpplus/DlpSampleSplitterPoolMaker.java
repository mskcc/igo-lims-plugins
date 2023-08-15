package com.velox.sloan.cmo.workflows.dlpplus;


import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mockito.internal.matchers.Null;

import javax.xml.crypto.Data;
import java.io.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This plugin is designed to split a DLP Plus sample into multiple sample aliquots based on the smartchip app excel file import. If a sample in the excel input is indicated as dead or alive,
 * a child sample record is created for the sample in the excel row. The plugin also creates the AssignedIndex record for each child sample created based on the row and column location indicated in the
 * excel file. Finally all the aliquots created by this plugin are linked to a Sample Pool. Sample pools are created based on quadrants.
 * Created by sharmaa1 on 7/23/19.
 * @author Fahimeh Mirhaj, updated: May 2023
 */
public class DlpSampleSplitterPoolMaker extends DefaultGenericPlugin {

    private final List<String> DLP_UPLOAD_SHEET_EXPECTED_HEADERS = Arrays.asList("Sample", "Row", "Column", "Img_Col", "File_Ch1", "File_Ch2", "Fld_Section", "Fld_Index", "Num_Live", "Num_Dead", "Num_Other",
            "Rev_Live", "Rev_Dead", "Rev_Other", "Rev_Class", "Condition", "Index_I7", "Primer_I7", "Index_I5", "Primer_I5", "Pick_Met", "Spot_Well", "Num_Drops");

    // As of 7/9/21 we are allowing spotting on all coordinates b/c 11881_B & 11881_C required more areas to spot
    private final List<String> ROW_NUMBERS_TO_SKIP = Arrays.asList(); // these are the row numbers on DLP chip edges that are not spotted with samples and must be skipped.
    private final List<String> COLUMN_NUMBERS_TO_SKIP = Arrays.asList(); // these are the column numbers on DLP chip edges, and in middle of the chip, that are not spotted with samples and must be skipped.

    private final List<String> CELL_TYPES_TO_PROCESS = Arrays.asList("Live", "Dead", "Live/Dead");
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    String recipe = ""; // Recipe to assign to new pool and new child records
    String chipId = ""; // DLP chip ID
    Boolean usualControlLocation = Boolean.TRUE;
    String[] positiveContorlChoises = {"184hTERT", "rpe1htert"};
    Map<String, String> seqRunTypeByQuadrant = new HashMap<>();
    private String DLP_SMARTCHIP_PATH = "/skimcs/mohibullahlab/LIMS/DLP/SmartchipSheet/";

    public DlpSampleSplitterPoolMaker() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("PARSE DLP SPOTTING FILE")
                && !activeTask.getTask().getTaskOptions().containsKey("_DLP SPOTTING FILE PARSED");
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            String DLPSmartChipFile = "";
            List<DataRecord> samplesAttachedToTask = activeTask.getAttachedDataRecords("Sample", user);
            if (samplesAttachedToTask.isEmpty()) {
                clientCallback.displayError("No samples found attached to this task.");
                return new PluginResult(false);
            }
            File folder = new File(DLP_SMARTCHIP_PATH);
            boolean multipleSamplesOnOneChip = clientCallback.showYesNoDialog("Multiple Samples",
                    "Are multiple samples on one chip?");
            if (multipleSamplesOnOneChip) {
                File[] listOfFiles = folder.listFiles();
                for (File file : listOfFiles) {
                    if (file.getName().contains(samplesAttachedToTask.get(0).getStringVal("SampleId", user))
                            && isValidExcelFile(file.getName())) {
                        DLPSmartChipFile = file.getName();
                        List<String> splitFileName = Arrays.asList(DLPSmartChipFile.replaceAll("\\s", "_").split("_|-|\\s"));
                        String endOfFileName = splitFileName.get(splitFileName.size() - 1);
                        chipId = endOfFileName.split("\\.")[0];
                        if (chipId.equals("")) {
                            clientCallback.displayError("ChipID cannot be empty. Please make sure the Smartchip " +
                                    "sheet exists on the shared drive LIMS -> DLP -> SmartchipSheet/");
                        }
                        if (chipId.length() < 7) {
                            clientCallback.displayError("ChipID length is not correct. Please make sure the correct chipID is in the Smartchip sheet file name.");
                        }
                        byte[] excelFileData = fillOutSmartChipSheetForMultiSamples(samplesAttachedToTask, file);
                        List<Row> rowData = utils.getExcelSheetDataRows(excelFileData);
                        if (!fileHasData(rowData, DLPSmartChipFile) || !hasValidHeader(rowData, DLP_UPLOAD_SHEET_EXPECTED_HEADERS, DLPSmartChipFile)) {
                            return new PluginResult(false);
                        }
                        clientCallback.displayInfo("This process Will take some time. Please be patient.");
                        HashMap<String, Integer> headerValuesMap = utils.getHeaderValuesMapFromExcelRowData(rowData);
                        Map<String, List<Row>> rowsSeparatedBySampleMap = getRowsBySample(samplesAttachedToTask, rowData, headerValuesMap);

                        // TODO: to remove
                        if (rowsSeparatedBySampleMap.isEmpty()) {
                            clientCallback.displayError(String.format("Did not find matching SAMPLE ID's for samples attached to the task in the template file.\nPlease make sure that file has correct Sample Info."));
                            return new PluginResult(false);
                        }
                        List<String> cellTypeChoices = clientCallback.showListDialog("Please choose the cell type to process", CELL_TYPES_TO_PROCESS, false, user);
                        if (cellTypeChoices.isEmpty()) {
                            clientCallback.displayError("Cell type to process not selected. You need to select correct cell type to process");
                            return new PluginResult(false);
                        }
                        String cellTypeToProcess = cellTypeChoices.get(0);
                        boolean isCorrectCellTypeToProcess = clientCallback.showYesNoDialog("Confirm CELL TYPE to process", String.format("Are you sure you want to process '%s' cells", cellTypeToProcess));
                        if (!isCorrectCellTypeToProcess) {
                            logInfo("User did not positively confirm CELL TYPE to process. Aborting plugin task.");
                            return new PluginResult(false);
                        }
                        Object recipe = samplesAttachedToTask.get(0).getValue(SampleModel.RECIPE, user);
                        Object dlpRequestedReads = getDlpRequestedReads(recipe);
                        Map<String, List<DataRecord>> newDlpSamples = createDlpSamplesAndProtocolRecords(rowsSeparatedBySampleMap, headerValuesMap, samplesAttachedToTask, cellTypeToProcess);
                        assert dlpRequestedReads != null;
                        createPools(newDlpSamples, (Double) dlpRequestedReads);
                    }
                }
            }
            else {
                for (DataRecord sample : samplesAttachedToTask) {
                    String sampleId = sample.getStringVal("SampleId", user);
                    File[] listOfFiles = folder.listFiles();
                    for (File file : listOfFiles) {
                        if (file.getName().contains(sampleId) && isValidExcelFile(file.getName())) {
                            DLPSmartChipFile = file.getName();
                            List<String> splitFileName = Arrays.asList(DLPSmartChipFile.replaceAll("\\s", "_").split("_|-|\\s"));
                            String endOfFileName = splitFileName.get(splitFileName.size() - 1);
                            chipId = endOfFileName.split("\\.")[0];
                            if (chipId.equals("")) {
                                clientCallback.displayError("ChipID cannot be empty. Please make sure the Smartchip " +
                                        "sheet exists on the shared drive LIMS -> DLP -> SmartchipSheet/");
                            }
                            if (chipId.length() < 7) {
                                clientCallback.displayError("ChipID length is not correct. Please make sure the correct chipID is in the Smartchip sheet file name.");
                            }
                            boolean controlExperiment = clientCallback.showYesNoDialog("Control Usage", String.format
                                    ("Does experiment for sample %s have controls? Yes or No", sampleId));
                            if (controlExperiment) {
                                usualControlLocation = clientCallback.showYesNoDialog("Controls Locations",
                                        String.format("Are the controls for sample %s located at their usual spot?", sampleId));
                            }
                            String positiveControlLoc = "";
                            String negativeControlLoc = "";
                            if (!usualControlLocation) {
                                positiveControlLoc = clientCallback.showInputDialog("Please enter the column(s) where POSITIVE CONTROLS are located, separated by '-':");
                                negativeControlLoc = clientCallback.showInputDialog("Please enter the column(s) where NEGATIVE CONTROLS are located, separated by '-':");
                            }
                            byte[] excelFileData = fillOutSmartChipSheet(sample, file, controlExperiment, usualControlLocation, positiveControlLoc, negativeControlLoc);
                            logInfo("After exiting the fillOutSmartChipSheet function");
                            List<Row> rowData = utils.getExcelSheetDataRows(excelFileData);

                            if (!fileHasData(rowData, DLPSmartChipFile) || !hasValidHeader(rowData, DLP_UPLOAD_SHEET_EXPECTED_HEADERS, DLPSmartChipFile)) {
                                return new PluginResult(false);
                            }
                            clientCallback.displayInfo("This process Will take some time. Please be patient.");
                            HashMap<String, Integer> headerValuesMap = utils.getHeaderValuesMapFromExcelRowData(rowData);

                            Map<String, List<Row>> rowsSeparatedBySampleMap = getRowsBySample(samplesAttachedToTask, rowData, headerValuesMap);

                            // TODO: to remove
                            if (rowsSeparatedBySampleMap.isEmpty()) {
                                clientCallback.displayError(String.format("Did not find matching SAMPLE ID's for samples attached to the task in the template file.\nPlease make sure that file has correct Sample Info."));
                                return new PluginResult(false);
                            }
                            List<String> cellTypeChoices = clientCallback.showListDialog("Please choose the cell type to process", CELL_TYPES_TO_PROCESS, false, user);
                            if (cellTypeChoices.isEmpty()) {
                                clientCallback.displayError("Cell type to process not selected. You need to select correct cell type to process");
                                return new PluginResult(false);
                            }
                            String cellTypeToProcess = cellTypeChoices.get(0);
                            logInfo("selected cell type:" + cellTypeToProcess);
                            boolean isCorrectCellTypeToProcess = clientCallback.showYesNoDialog("Confirm CELL TYPE to process", String.format("Are you sure you want to process '%s' cells", cellTypeToProcess));
                            if (!isCorrectCellTypeToProcess) {
                                logInfo("User did not positively confirm CELL TYPE to process. Aborting plugin task.");
                                return new PluginResult(false);
                            }
                            Object recipe = samplesAttachedToTask.get(0).getValue(SampleModel.RECIPE, user);
                            Object dlpRequestedReads = getDlpRequestedReads(recipe);
                            logInfo("dlpRequestedReads= " + dlpRequestedReads);
                            Map<String, List<DataRecord>> newDlpSamples = createDlpSamplesAndProtocolRecords(rowsSeparatedBySampleMap, headerValuesMap, samplesAttachedToTask, cellTypeToProcess);
                            logInfo("After createDlpSamplesAndProtocolRecords function call");
                            assert dlpRequestedReads != null;
                            logInfo("After assert");
                            createPools(newDlpSamples, (Double) dlpRequestedReads);
                        }
                    }
                }
            }
        }

        catch(IoError | InvalidValue e){
            String errMsg = String.format("IoError Exception while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch(InvalidFormatException e){
            String errMsg = String.format("InvalidFormat Exception while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch(NotFound e){
            String errMsg = String.format("NotFound Exception while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch(AlreadyExists e){
            String errMsg = String.format("AlreadyExists Exception while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch(RemoteException e){
            String errMsg = String.format("RemoteException while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch(IOException e){
            String errMsg = String.format("IOException while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }


    /**
     * Validate the uploaded excel file as excel with valid extension
     *
     * @param fileName
     * @return boolean true/false
     * @throws ServerException
     */
    private boolean isValidExcelFile(String fileName) throws ServerException, RemoteException {
        boolean isValid = utils.isValidExcelFile(fileName);
        if (!isValid) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a valid excel file", fileName));
            return false;
        }
        return true;
    }


    /**
     * Method to validate file has data.
     *
     * @param fileData
     * @param fileName
     * @return boolean true/false
     * @throws ServerException
     */
    private boolean fileHasData(List<Row> fileData, String fileName) throws ServerException, RemoteException {
        boolean hasData = utils.excelFileHasData(fileData);
        if (!hasData) {
            clientCallback.displayError(String.format("Uploaded file '%s' is Empty", fileName));
            logError(String.format("Uploaded file '%s' is Empty", fileName));
            return false;
        }
        return true;
    }


    /**
     * Method to validate file has valid header values.
     *
     * @param dataRows
     * @param expectedHeaderValues
     * @param fileName
     * @return boolean true/false
     * @throws ServerException
     */
    private boolean hasValidHeader(List<Row> dataRows, List<String> expectedHeaderValues, String fileName) throws ServerException, RemoteException {
        boolean isValidHeader = utils.excelFileHasValidHeader(dataRows, expectedHeaderValues);
        if (!isValidHeader) {
            clientCallback.displayError(String.format("Uploaded file '%s' does not have a valid header. Valid file Headers are\n'%s'", fileName, utils.convertListToString(expectedHeaderValues)));
            logError(String.format("Uploaded file '%s' does not have a valid header.Valid file Headers are\n'%s'", fileName, utils.convertListToString(expectedHeaderValues)));
            return false;
        }
        return true;
    }


    /**
     * Method to validate if the chip spot should be processed.
     *
     * @param rowNum
     * @param columnNum
     * @return boolean true/false
     */
    private boolean isValidChipSpotToProcess(String rowNum, String columnNum) {
        return !ROW_NUMBERS_TO_SKIP.contains(rowNum) && !COLUMN_NUMBERS_TO_SKIP.contains(columnNum);
    }

    /**
     * To validate file row for presence of only one live cell and not other cells.
     *
     * @param row
     * @param headerValuesMap
     * @return
     */
    private boolean chipHasOneliveCell(Row row, HashMap<String, Integer> headerValuesMap) {
        Integer num_live = Integer.parseInt(row.getCell(headerValuesMap.get("Num_Live")).toString().split("\\.")[0]);
        Integer num_dead = Integer.parseInt(row.getCell(headerValuesMap.get("Num_Dead")).toString().split("\\.")[0]);
        Integer rev_live = Integer.parseInt(row.getCell(headerValuesMap.get("Rev_Live")).toString().split("\\.")[0]);
        Integer rev_dead = Integer.parseInt(row.getCell(headerValuesMap.get("Rev_Dead")).toString().split("\\.")[0]);
        return (num_live == 1.0 && num_dead <= 0.0 && rev_dead <= 0.0 && rev_live <= 0.0) // must have only one live cell
                || (rev_live == 1.0 && num_dead <= 0.0 && rev_dead <= 0.0 && num_live <= 0.0); // OR must have only one revised live cell
    }

    /**
     * To validate file row for presence of only one dead cell and not other cells.
     *
     * @param row
     * @param headerValuesMap
     * @return
     */
    private boolean chipSpotHasOneDeadCell(Row row, HashMap<String, Integer> headerValuesMap) {
        Integer num_live = Integer.parseInt(row.getCell(headerValuesMap.get("Num_Live")).toString().split("\\.")[0]);
        Integer num_dead = Integer.parseInt(row.getCell(headerValuesMap.get("Num_Dead")).toString().split("\\.")[0]);
        Integer rev_live = Integer.parseInt(row.getCell(headerValuesMap.get("Rev_Live")).toString().split("\\.")[0]);
        Integer rev_dead = Integer.parseInt(row.getCell(headerValuesMap.get("Rev_Dead")).toString().split("\\.")[0]);
        return (num_dead == 1.0 && rev_dead <= 0.0 && rev_live <= 0.0 && num_live <= 0.0) // must have only one dead cell
                || (rev_dead == 1.0 && num_dead <= 0.0 && num_live <= 0.0 && rev_live <= 0.0); // OR there can only be one revised dead cell
    }

    /**
     * To validate the row based on user cell type to process.
     *
     * @param row
     * @param headerValuesMap
     * @param cellTypeToProcess
     * @return
     */
    private boolean isValidCellTypeToProcess(Row row, HashMap<String, Integer> headerValuesMap, String cellTypeToProcess) {
        switch (cellTypeToProcess) {
            case "Live":
                return chipHasOneliveCell(row, headerValuesMap);
            case "Dead":
                return chipSpotHasOneDeadCell(row, headerValuesMap);
            case "Live/Dead":
                return chipHasOneliveCell(row, headerValuesMap) || chipSpotHasOneDeadCell(row, headerValuesMap);

        }
        return false;
    }


    /**
     * Method to get the quadrant on the chip based on row and column position.
     *
     * @param rowNum
     * @param columnNum
     * @return String Quadrant ID
     */
    private String getQuandrant(String rowNum, String columnNum) {
        int rowid = (int) Double.parseDouble(rowNum);
        int columnid = (int) Double.parseDouble(columnNum);
        if (rowid <= 36 && rowid > 0 && columnid > 0 && columnid <= 36) {
            return "1";
        }
        if (rowid <= 36 && rowid > 0 && columnid > 36 && columnid <= 72) {
            return "2";
        }
        if (rowid <= 72 && rowid > 36 && columnid > 0 && columnid <= 36) {
            return "3";
        }
        if (rowid <= 72 && rowid > 36 && columnid > 36 && columnid <= 72) {
            return "4";
        }
        return "0";
    }


    /**
     * The first thing in the method to do is to divide
     *
     * @param samples
     * @param rows
     * @param headerValuesMap
     * @return HashMap with sample as Key and List of samples as Key Value
     * @throws NotFound
     * @throws RemoteException
     */
    private Map<String, List<Row>> getRowsBySample(List<DataRecord> samples, List<Row> rows, HashMap<String, Integer> headerValuesMap) throws NotFound, RemoteException {
        Map<String, List<Row>> rowDataSeparatedBySampleMap = new HashMap<>();
        for (DataRecord sample : samples) {
            String sampleId = sample.getStringVal("SampleId", user);
            rowDataSeparatedBySampleMap.putIfAbsent(sampleId, new ArrayList<>());
        }
        for (int i = 1; i < rows.size(); i++) {
            rows.get(i).getCell(headerValuesMap.get("Sample")).setCellType(CellType.STRING);
            String sampleId = rows.get(i).getCell(headerValuesMap.get("Sample")).getStringCellValue();
            if (rowDataSeparatedBySampleMap.containsKey(sampleId)) {
                rowDataSeparatedBySampleMap.get(sampleId).add(rows.get(i));
            }
        }
        return rowDataSeparatedBySampleMap;
    }


    /**
     * Method to get the DLP index ID using row and column position on Chip. A position on DLP chip always get the same Index ID
     *
     * @param rowNum
     * @param colNum
     * @return String IndexId
     */
    private String getIndexId(String rowNum, String colNum) {
        String rowId = Integer.toString((int) Double.parseDouble(rowNum));
        String colId = Integer.toString((int) Double.parseDouble(colNum));
        if (Integer.parseInt(rowId) <= 9) {
            rowId = "0" + rowId;
        }
        if (Integer.parseInt(colId) <= 9) {
            colId = "0" + colId;
        }
        return "DLPi7_" + colId + "-" + "i5_" + rowId;
    }


    /**
     * A method to add IndexBarcode record as child to the sample.
     *
     * @param sample
     * @param barcodeId
     * @return DataRecord Sample with added DataRecord IndexBarcode as child
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private DataRecord addIndexBarcodeRecordAsChild(DataRecord sample, String barcodeId) throws IoError, RemoteException, NotFound, ServerException {
        List<DataRecord> indexAssignments = dataRecordManager.queryDataRecords("IndexAssignment", "IndexId = '" + barcodeId + "'", user);
        Map<String, Object> assignedIndexRecordValues = new HashMap<>();
        assignedIndexRecordValues.put("SampleId", sample.getStringVal("SampleId", user));
        assignedIndexRecordValues.put("OtherSampleId", sample.getStringVal("OtherSampleId", user));
        assignedIndexRecordValues.put("IndexId", indexAssignments.get(0).getStringVal("IndexId", user));
        assignedIndexRecordValues.put("IndexTag", indexAssignments.get(0).getStringVal("IndexTag", user));

        sample.addChild("IndexBarcode", assignedIndexRecordValues, user);
        return sample;
    }


    /**
     * Method to create a child DataRecord of type Sample for a Sample.
     *
     * @param sample
     * @param sampleId
     * @param otherSampleId
     * @return DataRecord Sample with added DataRecord Sample as child
     * @throws ServerException
     * @throws RemoteException
     */
    private DataRecord createChildSample(DataRecord sample, String sampleId, String otherSampleId) throws ServerException, RemoteException {
        Map<String, Object> sampleFields = sample.getFields(user);
        Map<String, Object> valuesToApplyToChildSample = new HashMap<>();
        valuesToApplyToChildSample.put("SampleId", sampleId);
        valuesToApplyToChildSample.put("OtherSampleId", otherSampleId);
        valuesToApplyToChildSample.put("AltId", sampleFields.get("AltId"));
        valuesToApplyToChildSample.put("Recipe", sampleFields.get("Recipe"));
        valuesToApplyToChildSample.put("ExemplarSampleType", "DNA Library");
        valuesToApplyToChildSample.put("RequestId", sampleFields.get("RequestId"));
        valuesToApplyToChildSample.put("Species", sampleFields.get("Species"));
        valuesToApplyToChildSample.put("isControl", false);

        return sample.addChild("Sample", valuesToApplyToChildSample, user);
    }


    /**
     * Method to create an Independent DataRecord of type DLPLibraryPreparationProtocol2 for a Control Sample.
     * Control samples are added during the workflows when needed and does not have a parent when first created.
     *
     * @param values
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws NotFound
     * @throws AlreadyExists
     * @throws ServerException
     */
    private void createDLPLibProtocol2(Map<String, Object> values) throws RemoteException, InvalidValue, IoError, NotFound, AlreadyExists, ServerException {
        values.put("Aliq1IsNewControl", true);
        values.put("Aliq1ControlType", values.get("OtherSampleId"));
        DataRecord protocolRecord = dataRecordManager.addDataRecord("DLPLibraryPreparationProtocol2", user);
        protocolRecord.setFields(values, user);
    }


    /**
     * Method to create an Independent DataRecord of type Sample for a Control Sample.
     * Control samples are added during the workflows when needed and does not have a parent when first created.
     *
     * @param sample
     * @param newSampleId
     * @param newOtherSampleId
     * @return DataRecord Sample
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws NotFound
     * @throws AlreadyExists
     * @throws ServerException
     */
    private DataRecord createControlSampleRecord(DataRecord sample, String newSampleId, String newOtherSampleId) {
        DataRecord controlSample = null;
        try {
            Map<String, Object> sampleFields = sample.getFields(user);
            Map<String, Object> valuesForControlRec = new HashMap<>();
            valuesForControlRec.put("SampleId", newSampleId);
            valuesForControlRec.put("OtherSampleId", newOtherSampleId);
            valuesForControlRec.put("AltId", newSampleId);
            valuesForControlRec.put("Recipe", sampleFields.get("Recipe"));
            valuesForControlRec.put("ExemplarSampleType", "DNA Library");
            valuesForControlRec.put("Species", sampleFields.get("Species"));
            valuesForControlRec.put("RequestId", sampleFields.get("RequestId"));
            valuesForControlRec.put("IsControl", true);
            controlSample = dataRecordManager.addDataRecord("Sample", user);
            controlSample.setFields(valuesForControlRec, user);
        } catch (NotFound | AlreadyExists | InvalidValue | IoError | ServerException | RemoteException notFound) {
            logError(Arrays.toString(notFound.getStackTrace()));
        }
        return controlSample;
    }


    /**
     * Method to get SampleId value for DLP control with highest numeric value associated with SampleId value. Control SamplId increments when new controls are added.
     * Therefore to create a new Control SampleId, we need the last SampleId value created.
     *
     * @param controlTypeIdentifier
     * @return String SampleId
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String getMostRecentDLPControl(String controlTypeIdentifier) throws IoError, RemoteException, NotFound, ServerException {
        List<DataRecord> controlSampleRecords = dataRecordManager.queryDataRecords("Sample", "IsControl = 1 AND SampleId LIKE '" + controlTypeIdentifier + "%'", user);
        logInfo("ControlSamples Size: " + controlSampleRecords.size());
        List<String> controlSampleIds = new ArrayList<>();
        for (DataRecord record : controlSampleRecords) {
            String sampleId = record.getStringVal("SampleId", user);
            String otherSampleId = record.getStringVal("OtherSampleId", user);
            if (otherSampleId.toLowerCase().contains(controlTypeIdentifier.toLowerCase()) && sampleId.toLowerCase().contains(controlTypeIdentifier.toLowerCase())) {
                controlSampleIds.add(sampleId);
            }
        }

        if (controlSampleIds.isEmpty()) {
            return controlTypeIdentifier;
        }
        List<String> sortedControlSampleIds = controlSampleIds.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
        //logInfo("Sorted controls:" + sortedControlSampleIds.toString());
        return sortedControlSampleIds.get(sortedControlSampleIds.size() - 1).split("_")[0];
    }


    /**
     * Method to get the numeric value associated with Control Sample SampleID.
     *
     * @param mostRecentControlId
     * @return int value
     */
    private int getIncrementingNumberOnControl(String mostRecentControlId) {
        if (mostRecentControlId.split("-").length > 1) {
            return Integer.parseInt(mostRecentControlId.split("-")[1]);
        }
        return 0;
    }


    /**
     * Method to get unique RequestId values for samples getting pooled together. Request ID's are added to the SampleId of the Pool Sample.
     *
     * @param samples
     * @throws RemoteException
     * @throws NotFound
     */
    private String getRequestIdsAsString(List<DataRecord> samples, String quadrant) throws NotFound, RemoteException {
        Set<String> requestIds = new HashSet<>();
        for (DataRecord sam : samples) {
            if (sam.getValue("RequestId", user) != null || sam.getValue("RequestId", user) != "") {
                requestIds.add(sam.getStringVal("RequestId", user));
                logInfo("requestIds:" + requestIds.toString());
            }
        }
        if (requestIds.isEmpty()) {
            throw new NotFound(String.format("Request ID not found for samples in quadrant '%s'", quadrant));
        }
        logInfo(StringUtils.join(requestIds.toArray(), "_"));
        return StringUtils.join(requestIds.toArray(), "_").replaceAll("^_+", "");
    }


    /**
     * Method to get Sequencing RunType associated with Sample.
     *
     * @param sample
     * @return String SequencingRunType
     * @throws RemoteException
     * @throws IoError
     * @throws NotFound
     */
    private String getSequencingRunType(DataRecord sample) throws RemoteException, IoError, NotFound, ServerException{
        List<DataRecord> ancestorSamples = sample.getAncestorsOfType("Sample", user);
        if (!ancestorSamples.isEmpty()) {
            for (DataRecord samp : ancestorSamples) {
                DataRecord[] seqRequirements = samp.getChildrenOfType("SeqRequirement", user);
                if (seqRequirements.length > 0 && seqRequirements[0].getValue("SequencingRunType", user) != null) {
                    return seqRequirements[0].getStringVal("SequencingRunType", user);
                }
            }
        }
        return "";
    }


    /**
     * Method to add Sequencing Run Type to the Map.
     *
     * @param sequencingRunType
     * @param seqRunTypeByQuadrant
     * @param quadrant
     */
    private void addSeqRunTypeToMap(String sequencingRunType, Map<String, String> seqRunTypeByQuadrant, String quadrant) {
        if (!seqRunTypeByQuadrant.containsKey(quadrant)) {
            if (!sequencingRunType.equals(""))
                seqRunTypeByQuadrant.put(quadrant, sequencingRunType);
        }
    }

    /**
     * Get the next Sample ID that we can use to start creating aliquot ID's. If the sample is being reporcessed, Some aliquot Sample ID's may already exist, and we need to find next aliquot sample ID.
     *
     * @param sampleId
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String getNextSampleId(String sampleId) throws IoError, RemoteException, NotFound, ServerException {
        int counter = 1;
        String nextSampleId = sampleId + "_" + counter; //this SampleId can already exist if sample is being reprocessed for DLP. We check that in next if block
        // if poolid exists, extend it with a number and increment the number until we have a pool id that doesn't exist in the LIMS.
        while (dataRecordManager.queryDataRecords("Sample", "SampleId = '" + nextSampleId + "'", user).size() > 0) {
            counter += 1;
            nextSampleId = sampleId + "_" + counter;
        }
        logInfo("Next Sample ID : " + nextSampleId);
        return nextSampleId;
    }

    /**
     * This method is long and does few things:
     * 1. Creates Child Sample records.
     * 2. Creates Child IndexBarcode records Child Sample records
     * 3. Creates Child DLPLibraryPreparationProtocol2 records.
     * 4. Creates Independent DataRecord of type DLPLibraryPreparationProtocol2/Sample for Controls.
     * <p>
     * Method returns new Sample Records separated by quadrant. We will pool samples by quadrant, therefore separation by quadrant will be helpful for Pooling method.
     *
     * @param rowsSeparatedBySampleMap
     * @param headerValuesMap
     * @param samples
     * @return Map<String, List < DataRecord>> Samples separated by quadrant on chip
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     * @throws AlreadyExists
     */
    private Map<String, List<DataRecord>> createDlpSamplesAndProtocolRecords(Map<String, List<Row>> rowsSeparatedBySampleMap, HashMap<String, Integer> headerValuesMap, List<DataRecord> samples, String cellTypeToProcess) throws NotFound, RemoteException, IoError, InvalidValue, ServerException, AlreadyExists {
        Map<String, List<DataRecord>> newlyCreatedChildSamplesByQuadrant = new HashMap<>();
        int negativeControlIncrement = getIncrementingNumberOnControl(getMostRecentDLPControl("DLPNegativeCONTROL"));
        int salControlIncrement = getIncrementingNumberOnControl(getMostRecentDLPControl("DLPSalCONTROL"));
        int gmControlIncrement = getIncrementingNumberOnControl(getMostRecentDLPControl("DLPGmCONTROL"));
        int cellControlIncrement = getIncrementingNumberOnControl(getMostRecentDLPControl("DLPcellCONTROL"));
        int noCellControlIncrement = getIncrementingNumberOnControl(getMostRecentDLPControl("DLPNoCellCONTROL"));
        recipe = samples.get(0).getStringVal("Recipe", user);
        for (DataRecord sample : samples) {
            String sampleId = sample.getStringVal("SampleId", user);
            String otherSampleId = sample.getStringVal("OtherSampleId", user);
            String altId = sample.getStringVal("AltId", user);
            String sequencingRunType = getSequencingRunType(sample);
            List<Row> sampleDataRows = rowsSeparatedBySampleMap.get(sampleId);
            String nextAliquotSampleId = getNextSampleId(sampleId); // This will provide the next sample ID that we can use to start creating aliquot ID's. If the sample is being reporcessed, the aliquot ID's may exist, and we need to find next aliquot sample ID.
            int aliquotIncrementValue = 1;
            for (Row row : sampleDataRows) {
                String chipRow = row.getCell(headerValuesMap.get("Row")).toString();
                String chipColumn = row.getCell(headerValuesMap.get("Column")).toString();
                String condition = row.getCell(headerValuesMap.get("Condition")).toString();
                if (isValidChipSpotToProcess(chipRow, chipColumn) && isValidCellTypeToProcess(row, headerValuesMap, cellTypeToProcess)) {
                    String newSampleId;
                    String newOtherSampleId;
                    boolean isControl = false;
                    Map<String, Object> dlpRecordValues = new HashMap<>();
                    switch (condition.trim().toLowerCase()) {
                        case "184htert":
                        case "rpe1htert":
                            cellControlIncrement += 1;
                            newSampleId = "DLPcellCONTROL" + "-" + cellControlIncrement;
                            newOtherSampleId = "DLPcellCONTROL" + "_" + chipId + "_" + (int) Double.parseDouble(chipRow) + "_" + (int) Double.parseDouble(chipColumn);
                            altId = newSampleId;
                            isControl = true;
                            break;
                        case "ntc":
                            negativeControlIncrement += 1;
                            newSampleId = "DLPNegativeCONTROL" + "-" + negativeControlIncrement;
                            newOtherSampleId = "DLPNegativeCONTROL" + "_" + chipId + "_" + (int) Double.parseDouble(chipRow) + "_" + (int) Double.parseDouble(chipColumn);
                            altId = newSampleId;
                            isControl = true;
                            break;

                        default:
                            newSampleId = nextAliquotSampleId + "_" + aliquotIncrementValue;
                            newOtherSampleId = otherSampleId + "_" + chipId + "_" + (int) Double.parseDouble(chipRow) + "_" + (int) Double.parseDouble(chipColumn);
                            break;
                    }
                    dlpRecordValues.put("SampleId", newSampleId);
                    dlpRecordValues.put("OtherSampleId", newOtherSampleId);
                    dlpRecordValues.put("AltId", altId);
                    dlpRecordValues.put("ChipRow", chipRow);
                    dlpRecordValues.put("ChipColumn", chipColumn);
                    dlpRecordValues.put("ImageColumn", row.getCell(headerValuesMap.get("Img_Col")).toString());
                    dlpRecordValues.put("ImageFileChannel1", row.getCell(headerValuesMap.get("File_Ch1")).toString());
                    dlpRecordValues.put("ImageFileChannel2", row.getCell(headerValuesMap.get("File_Ch2")).toString());
                    dlpRecordValues.put("PrimerIDi5", row.getCell(headerValuesMap.get("Index_I5")).toString());
                    dlpRecordValues.put("PrimerSequencei5", row.getCell(headerValuesMap.get("Primer_I5")).toString());
                    dlpRecordValues.put("PrimerIDi7", row.getCell(headerValuesMap.get("Index_I7")).toString());
                    dlpRecordValues.put("PrimerSequencei7", row.getCell(headerValuesMap.get("Primer_I7")).toString());
                    dlpRecordValues.put("NumberLiveCells", row.getCell(headerValuesMap.get("Num_Live")).toString());
                    dlpRecordValues.put("RevisedLiveCells", row.getCell(headerValuesMap.get("Rev_Live")).toString());
                    dlpRecordValues.put("NumberDeadCells", row.getCell(headerValuesMap.get("Num_Dead")));
                    dlpRecordValues.put("RevisedDeadCells", row.getCell(headerValuesMap.get("Rev_Dead")));
                    String quadrant = getQuandrant(chipRow, chipColumn);
                    addSeqRunTypeToMap(sequencingRunType, seqRunTypeByQuadrant, quadrant);
                    dlpRecordValues.put("Quadrant", quadrant);
                    DataRecord newDLPSample = null;
                    if (!isControl) {
                        sample.addChild("DLPLibraryPreparationProtocol2", dlpRecordValues, user);
                        newDLPSample = createChildSample(sample, newSampleId, newOtherSampleId);
                    }
                    if (isControl) {
                        createDLPLibProtocol2(dlpRecordValues);
                        logInfo("dlp protocol 2 created: "  + dlpRecordValues.get("Aliq1ControlType"));
                        newDLPSample = createControlSampleRecord(sample, newSampleId, newOtherSampleId);
                    }
                    String indexId = getIndexId(chipRow, chipColumn);
                    newDLPSample = addIndexBarcodeRecordAsChild(newDLPSample, indexId);
                    newlyCreatedChildSamplesByQuadrant.putIfAbsent(quadrant, new ArrayList<>());
                    newlyCreatedChildSamplesByQuadrant.get(quadrant).add(newDLPSample);
                    aliquotIncrementValue += 1;
                }
            }
        }
        return newlyCreatedChildSamplesByQuadrant;
    }


    private Object getDlpRequestedReads(Object recipe) throws IoError, RemoteException, NotFound , ServerException {
        String queryClause = String.format("PlatformApplication = '%s' AND ReferenceOnly != 1", recipe);
        List<DataRecord> coverageReqRefs = dataRecordManager.queryDataRecords("ApplicationReadCoverageRef",
                queryClause, user);
        if (coverageReqRefs.isEmpty()){
            String message = String.format("Could not fetch 'ApplicationReadCoverageRef' for recipe '%s'. Please make " +
                    "that the recipe exists with valid reads values in 'ApplicationReadCoverageRef' table", recipe);
            throw new NotFound(message);
        }
        return coverageReqRefs.get(0).getValue("MillionReadsHuman", user);
    }

    /**
     * Method to get values for SeqRequirementPooled DataRecord to set on new Pooled Samples to be created.
     *
     * @param poolId
     * @param otherSampleId
     * @param numberOfSamples
     * @param quadrant
     * @return Map<String, Object> values
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Map<String, Object> getSeqRequirementPooledValues(String poolId, String otherSampleId, int numberOfSamples, String quadrant, Double requestedReadsPerSample) throws NotFound, RemoteException, ServerException {
        Map<String, Object> seqReqValues = new HashMap<>();
        seqReqValues.put("SampleId", poolId);
        seqReqValues.put("OtherSampleId", otherSampleId);
        seqReqValues.put("AltId", otherSampleId);
        seqReqValues.put("SequencingRunType", seqRunTypeByQuadrant.get(quadrant));
        seqReqValues.put("RequestedReads", numberOfSamples * requestedReadsPerSample); //requestedReadsPerSample value is fetched from 'ApplicationReadCoverageRef' table in LIMS.
        return seqReqValues;
    }


    /**
     * Get Pool ID.
     *
     * @param requestId
     * @param quadrant
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String getPoolId(String requestId, String quadrant) throws IoError, RemoteException, NotFound, ServerException {
        String poolId = "Pool-" + requestId + "-Tube" + quadrant; //this pool  ID can already exist if samples from same request were processed before. We check that in next if block
        if (dataRecordManager.queryDataRecords("Sample", "SampleId = '" + poolId + "'", user).isEmpty()) {
            logInfo("Pool ID : " + poolId);
            return poolId;
        }
        int counter = 1;
        String extendedPoolId = poolId + "_" + counter; // if poolid exists, extend it with a number and increment the number until we have a pool id that doesn't exist in the LIMS.
        while (dataRecordManager.queryDataRecords("Sample", "SampleId = '" + extendedPoolId + "'", user).size() > 0) {
            counter += 1;
            extendedPoolId = poolId + "_" + counter;
        }
        logInfo("PoolId: " + extendedPoolId);
        return extendedPoolId;
    }

    /**
     * Method to create Sample Pools. Samples on each Quadrant on the chip will be pooled together.
     * After pooling new pools are attached to the active task
     *
     * @param newlyCreatedChildSamplesByQuadrant
     * @throws NotFound
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws AlreadyExists
     * @throws ServerException
     */
    private void createPools(Map<String, List<DataRecord>> newlyCreatedChildSamplesByQuadrant, Double requestedReadsPerSample) throws NotFound, RemoteException, IoError, AlreadyExists, ServerException {
        List<DataRecord> pooledSampleRecords = new ArrayList<>();
        for (Map.Entry entry : newlyCreatedChildSamplesByQuadrant.entrySet()) {
            String quadrant = (String) entry.getKey();
            List<Map<String, Object>> newPoolRecordvalues = new ArrayList<>();
            List<DataRecord> samples = newlyCreatedChildSamplesByQuadrant.get(entry.getKey());
            String requestIds = getRequestIdsAsString(samples, quadrant);
            logInfo("requestIds: " + requestIds);
            Map<String, Object> pooledSampleValues = new HashMap<>();
            String poolId = getPoolId(requestIds, quadrant);
            String otherSampleId = poolId + "_" + chipId;
            pooledSampleValues.put("SampleId", poolId);
            pooledSampleValues.put("OtherSampleId", otherSampleId);
            pooledSampleValues.put("AltId", otherSampleId);
            pooledSampleValues.put("RequestId", requestIds);
            pooledSampleValues.put("ExemplarSampleType", "Pooled Library");
            pooledSampleValues.put("Recipe", recipe);
            newPoolRecordvalues.add(pooledSampleValues);
            DataRecord pooledSample = dataRecordManager.addDataRecords("Sample", newPoolRecordvalues, user).get(0);
            logInfo("Adding pool as child to Sample!");
            for (DataRecord sample : samples) {
                sample.addChild(pooledSample, user);
            }
            dataRecordManager.storeAndCommit("Adding pool Info for DLP sample " + poolId, null, user);
            DataRecord seqReq = dataRecordManager.queryDataRecords("SeqRequirementPooled", "SampleId = '" + poolId + "'", user).get(0);
            seqReq.setFields(getSeqRequirementPooledValues(poolId, otherSampleId, samples.size(), quadrant, requestedReadsPerSample), user);
            pooledSampleRecords.add(pooledSample);
        }
        activeTask.removeAllTaskAttachments();
        activeTask.addAttachedDataRecords(pooledSampleRecords);
        activeWorkflow.getNext(activeTask).addAttachedDataRecords(pooledSampleRecords);
        activeTask.getTask().getTaskOptions().put("_DLP SPOTTING FILE PARSED", "");
    }

    /**
     * This method uses a template SmartChip sheet in the DLP folder on the shared drive and fill out a row for every single
     * well on the chip. Based on whether a well contains a sample or a control the "condition" column gets populated with
     * sample name or positive/negative controls.
     * NOTE: this function gets called when there is only one sample on the entire chip
     *
     * */
    private byte[] fillOutSmartChipSheet(DataRecord sample, File file, boolean controlExperiment, boolean usualControlLoc, String posCtrlLoc, String negCtrlLoc) {
        byte[] bytes = null;
        try {
            logInfo("Inside the fillOutSmartChipSheet function");
            Workbook smartChipWorkBook = WorkbookFactory.create(file);
            Sheet summary = smartChipWorkBook.getSheetAt(0);
            int rowCount = 0;
            //String[] positiveContorlChoises = {"184hTERT", "rpe1htert"};
            String[] positiveLocations = posCtrlLoc.split("-");
            String[] negativeLocations = negCtrlLoc.split("-");
            String sampleId = sample.getStringVal("SampleId", user);
            String sampleName = sample.getStringVal("OtherSampleId", user);
            int selectedPositiveControl = 0;
            if (controlExperiment) {
                selectedPositiveControl = clientCallback.showOptionDialog("Positive Control Selection", "Which positive control " +
                        "has been used for sample " + sampleId, positiveContorlChoises, 0);
            }
            for (Row row : summary) {
                if (row != null) {
                    if (rowCount == 0) {
                        rowCount++;
                        continue;
                    }
                    row.getCell(0).setCellValue(sampleId);
                    row.getCell(8).setCellValue("1");
                    row.getCell(9).setCellValue("-1");
                    row.getCell(10).setCellValue("-1");
                    if (!controlExperiment) {
                        row.getCell(15).setCellValue(sampleName);
                    }
                    else {
                        // Prefixed conditions
                        if (usualControlLoc) {
                            if (row.getCell(2).getNumericCellValue() == 3.0) { // negative control
                                row.getCell(15).setCellValue("ntc");
                            } else if (row.getCell(2).getNumericCellValue() == 5.0) { // positive control
                                row.getCell(15).setCellValue(positiveContorlChoises[selectedPositiveControl]);
                            } else {
                                row.getCell(15).setCellValue(sampleName);
                            }
                        } else {
                            for (String neg : negativeLocations) {
                                if (row.getCell(2).getNumericCellValue() == Double.parseDouble(neg)) { // negative control
                                    row.getCell(15).setCellValue("ntc");
                                }
                            }
                            for (String pos : positiveLocations) {
                                if (row.getCell(2).getNumericCellValue() == Double.parseDouble(pos)) { // positive control
                                    row.getCell(15).setCellValue(positiveContorlChoises[selectedPositiveControl]);
                                }
                            }
                            if (!row.getCell(15).getStringCellValue().equalsIgnoreCase("ntc") &&
                                    !row.getCell(15).getStringCellValue().equalsIgnoreCase("184hTERT") &&
                                    !row.getCell(15).getStringCellValue().equalsIgnoreCase("rpe1htert")) {
                                row.getCell(15).setCellValue(sampleName);

                            }
                        }
                    }
                }
            }
            logInfo("Writing SmartChip Report " + file.getName() + ".xls");
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

            try {
                smartChipWorkBook.write(byteStream);
                byteStream.close();
                bytes = byteStream.toByteArray();
                clientCallback.writeBytes(bytes, file.getName() + ".xls");
            } catch (ServerException e) {
                logError(String.format("RemoteException -> Error while exporting SmartChip report:\n%s",ExceptionUtils.getStackTrace(e)));
            } catch (IOException e) {
                logError(String.format("IOException -> Error while exporting SmartChip report:\n%s", ExceptionUtils.getStackTrace(e)));
            } finally {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    logError(String.format("IOException -> Error while closing the ByteArrayOutputStream:\n%s", ExceptionUtils.getStackTrace(e)));
                }
            }
        }
        catch (Exception e) {
            logInfo("An exception occurred when pre-populating SmartChip sheet", e);
        }
        return bytes;
    }

    /**
     * This method uses a template SmartChip sheet in the DLP folder on the shared drive and fill out a row for every single
     * well on the chip. Based on whether a well contains samples or a control the "condition" column gets populated with
     * sample names or positive/negative controls.
     * NOTE: this function gets called when there are multiple samples placed on a single chip (generating one sheet)
     *
     * */
    private byte[] fillOutSmartChipSheetForMultiSamples(List<DataRecord> samples, File file) {
        byte[] bytes = null;
        try {
            logInfo("Inside the fillOutSmartChipSheetForMultiSamples function");
            Workbook smartChipWorkBook = WorkbookFactory.create(file);
            Sheet summary = smartChipWorkBook.getSheetAt(0);
            int rowCount = 0;
            for (Row row : summary) {
                if (row != null) {
                    if (rowCount == 0) {
                        rowCount++;
                        continue;
                    }
                    row.getCell(15).setCellValue("~");
                }
            }
            List<String> positiveControlLocations = new LinkedList<>();
            List<String> negativeControlLocations = new LinkedList<>();
            int i = 0;
            for (DataRecord sample : samples) {
                rowCount = 0;
                String sampleId = sample.getStringVal("SampleId", user);
                String sampleName = sample.getStringVal("OtherSampleId", user);
                int selectedPositiveControl = 0;
                String[] positiveLocations = {};
                String[] negativeLocations = {};


                boolean controlExperiment = clientCallback.showYesNoDialog("Control Usage", String.format("Does experiment for sample %s have controls? Yes or No?", sampleId));

                if (controlExperiment) {
                        usualControlLocation = clientCallback.showYesNoDialog("Controls Locations",
                                String.format("Are the controls for sample %s located at their usual spot?", sampleId));
                        if (!usualControlLocation) {
                            String positiveControlLoc = clientCallback.showInputDialog(String.format("Please enter the column(s) where POSITIVE CONTROLS are located for %s, separated by '-':", sampleId));
                            String negativeControlLoc = clientCallback.showInputDialog(String.format("Please enter the column(s) where NEGATIVE CONTROLS are located for %s, separated by '-':", sampleId));
                            positiveControlLocations.add(positiveControlLoc);
                            negativeControlLocations.add(negativeControlLoc);
                        }
                }

                if (controlExperiment) {
                    if (!usualControlLocation) {
                        positiveLocations = positiveControlLocations.get(i).split("-");
                        negativeLocations = negativeControlLocations.get(i).split("-");
                        i++;
                    }
                    selectedPositiveControl = clientCallback.showOptionDialog("Positive Control Selection", "Which positive control " +
                            "has been used for sample " + sampleId, positiveContorlChoises, 0);
                }
                // Prompt for the location of samples, rows and columns ranges
                String minRowOfCurrentSample = clientCallback.showInputDialog(String.format("Enter the FIRST ROW where sample %s is on the chip: ", sampleId));
                String maxRowOfCurrentSample = clientCallback.showInputDialog(String.format("Enter the LAST ROW where sample %s is on the chip: ", sampleId));
                String minColOfCurrentSample = clientCallback.showInputDialog(String.format("Enter the FIRST COLUMN where sample %s is on the chip: ", sampleId));
                String maxColOfCurrentSample = clientCallback.showInputDialog(String.format("Enter the LAST COLUMN where sample %s is on the chip: ", sampleId));
                for (Row row : summary) {
                    if (row != null) {
                        if (rowCount == 0) {
                            rowCount++;
                            continue;
                        }
                        row.getCell(8).setCellValue("1");
                        row.getCell(9).setCellValue("-1");
                        row.getCell(10).setCellValue("-1");

                        if ((row.getCell(1).getNumericCellValue() >= Double.parseDouble(minRowOfCurrentSample) &&
                                row.getCell(1).getNumericCellValue() <= Double.parseDouble(maxRowOfCurrentSample))) {
                            row.getCell(0).setCellValue(sampleId);
                            if (usualControlLocation) {
                                if (row.getCell(2).getNumericCellValue() == Double.valueOf("3") &&
                                        row.getCell(15).getStringCellValue().equals("~")) { // negative control
                                    logInfo("putting default neg value");
                                    row.getCell(15).setCellValue("ntc");
                                } else if (row.getCell(2).getNumericCellValue() == Double.valueOf("5") &&
                                        row.getCell(15).getStringCellValue().equals("~")) { // positive control
                                    logInfo("putting default pos value");
                                    row.getCell(15).setCellValue(positiveContorlChoises[selectedPositiveControl]);
                                }
//                            else {
//                                row.getCell(15).setCellValue(sampleName);
//                            }
                            } else {
                                for (String neg : negativeLocations) {
                                    if (row.getCell(2).getNumericCellValue() == Double.parseDouble(neg)) { // negative control
                                        logInfo("negative control entered is: " + neg);
                                        row.getCell(15).setCellValue("ntc");
                                    }
                                }
                                for (String pos : positiveLocations) {
                                    if (row.getCell(2).getNumericCellValue() == Double.parseDouble(pos)) { // positive control
                                        logInfo("positive control entered is: " + pos);
                                        row.getCell(15).setCellValue(positiveContorlChoises[selectedPositiveControl]);
                                    }
                                }
                            }
                        }

                        if ((row.getCell(1).getNumericCellValue() >= Double.parseDouble(minRowOfCurrentSample) &&
                                row.getCell(1).getNumericCellValue() <= Double.parseDouble(maxRowOfCurrentSample))
                        && (row.getCell(2).getNumericCellValue() >= Double.parseDouble(minColOfCurrentSample) &&
                                row.getCell(2).getNumericCellValue() <= Double.parseDouble(maxColOfCurrentSample))) {

                            row.getCell(0).setCellValue(sampleId);
                            if (!controlExperiment) {
                                row.getCell(15).setCellValue(sampleName);
                            } else {
                                if (!row.getCell(15).getStringCellValue().equalsIgnoreCase("ntc") &&
                                        !row.getCell(15).getStringCellValue().equalsIgnoreCase("184hTERT") &&
                                        !row.getCell(15).getStringCellValue().equalsIgnoreCase("rpe1htert")) {
                                    row.getCell(15).setCellValue(sampleName);
                                }
                            }
                        }
//                        if (!row.getCell(15).getStringCellValue().equalsIgnoreCase("ntc") &&
//                                !row.getCell(15).getStringCellValue().equalsIgnoreCase("184hTERT") &&
//                                !row.getCell(15).getStringCellValue().equalsIgnoreCase("rpe1htert") &&
//                                !row.getCell(15).getStringCellValue().equalsIgnoreCase(sampleName)) {
//                            row.getCell(15).setCellValue("~");
//                        }
                    }
                }
            }
            logInfo("Writing SmartChip Report " + file.getName() + ".xls");
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

            try {
                smartChipWorkBook.write(byteStream);
                byteStream.close();
                bytes = byteStream.toByteArray();
                clientCallback.writeBytes(bytes, file.getName() + ".xls");
            } catch (ServerException e) {
                logError(String.format("RemoteException -> Error while exporting SmartChip report:\n%s",ExceptionUtils.getStackTrace(e)));
            } catch (IOException e) {
                logError(String.format("IOException -> Error while exporting SmartChip report:\n%s", ExceptionUtils.getStackTrace(e)));
            } finally {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    logError(String.format("IOException -> Error while closing the ByteArrayOutputStream:\n%s", ExceptionUtils.getStackTrace(e)));
                }
            }
        }
        catch (Exception e) {
            logInfo("An exception occurred when pre-populating SmartChip sheet", e);
        }
        return bytes;
    }
}