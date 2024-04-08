package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ClientCallbackRMI;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.api.util.ServerException;
import com.velox.api.exception.recoverability.serverexception.UnrecoverableServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This toolbar plugin invoked through a button in the toolbar. It is designed to generate a report exported as .xlsx file.
 * This report could be shared with the users as final results.
 * The plugin will change the header names in the output file based on the report type values selected by the user.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class DigitalPcrReportGenerator extends DefaultGenericPlugin {
    private List<String> ddPCRReportTypes = Arrays.asList("GEX", "RED", "CNV", "LAB MEDICINE", "METHYLATED", "PDX");
    private List<String> gexReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Gene (Copies)", "Concentration Ref (Copies)", "Ratio ([Gene]/[Ref])", "Droplet Count Gene", "Droplet Count Ref", "Accepted Droplets");
    private List<String> cnvReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Gene (Copies)", "Concentration Ref (Copies)", "CNV", "Droplet Count Gene", "Droplet Count Ref", "Accepted Droplets");
    private List<String> redReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration MU (Copies)", "Concentration WT (Copies)", "Fractional abundance", "Droplet Count MU", "Droplet Count WT", "Accepted Droplets");
    private List<String> PDXReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Human (Copies)", "Concentration Mouse (Copies)", "Ratio ([Human]/[Mouse])", "Droplet Count Human", "Droplet Count Mouse", "Accepted Droplets", "Human %");
    private List<String> methylatedReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Methylated (Copies)", "Concentration Unmethylated (Copies)", "Ratio ([Methylated]/[Unmethylated])", "Droplet Count Methylated", "Droplet Count Unmethylated", "Accepted Droplets");
    private List<String> labMedicineReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Gene (Copies)", "Concentration Ref (Copies)", "Ratio ([Gene]/[Ref])", "Droplet Count Gene", "Droplet Count Ref", "Total Detected (ng)", "Accepted Droplets", "Micronic Tube Barcode");

    public DigitalPcrReportGenerator() {
        setTaskToolbar(true);
        setFormToolbar(true);
        setLine1Text("Generate ddPCR");
        setLine2Text("Report");
        setDescription("Generates report for ddPCR experiment with specific columns.");
        setIcon("com/velox/sloan/cmo/resources/export_32.gif");
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("GENERATE DDPCR REPORT");
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("GENERATE DDPCR REPORT");
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while setting toolbar button:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
        }
        return false;
    }

    @Override
    public boolean onTaskFormToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        return onTaskToolbar(activeWorkflow, activeTask);
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> ddPcrResults = activeTask.getAttachedDataRecords("DdPcrAssayResults", user);
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            if (ddPcrResults.isEmpty()) {
                clientCallback.displayError("No 'ddPcrResults' records found attached to this task.");
                logError("No attached 'ddPcrResults' records found attached to this task.");
                return new PluginResult(false);
            }
            if (attachedSamples.isEmpty()) {
                clientCallback.displayError("No 'Sample' records found attached to this task.");
                logError("No attached 'ddPcrResults' records found attached to this task.");
                return new PluginResult(false);
            }
            String reportType = getReportTypeFromUser();
            if (StringUtils.isBlank(reportType)) {
                logError("ddPCR Report type not provided by user. Dialog canceled.");
                return new PluginResult(false);
            }

            List<String> headerForReport = getHeaderBasedOnReportType(reportType);
            XSSFWorkbook workbook = new XSSFWorkbook();
            List<Map<String, Object>> valuesForReport = setFieldsForReport(ddPcrResults);
            generateExcelDataWorkbook(reportType, headerForReport, valuesForReport, workbook);
            String fileName = generateFileNameFromRequestIds(attachedSamples);
            exportReport(workbook, fileName);

        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while generating DDPCR Report:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Get the micronic tube ID for the sample.
     *
     * @param ddPcrReportRecord
     * @return Micronic Tube ID for sample if found else returns "".
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private Object getMicronicTubeIdFromParentSample(DataRecord ddPcrReportRecord){
        try {
            List<DataRecord> parentSampleRecords = ddPcrReportRecord.getParentsOfType("Sample", user);
            if (!parentSampleRecords.isEmpty()) {
                Object micronicTubeBarcode = parentSampleRecords.get(0).getValue("MicronicTubeBarcode", user);
                if (micronicTubeBarcode != null) {
                    return micronicTubeBarcode;
                }
            }
        } catch (RemoteException | ServerException e) {
            logError(String.format("RemoteException -> Error getting MicronitTubeBarcode from parent sample on active task:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error getting MicronitTubeBarcode from parent sample on active task:\n%s", ExceptionUtils.getStackTrace(ioError)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error getting MicronitTubeBarcode from parent sample on active task:\n%s", ExceptionUtils.getStackTrace(notFound)));
        }
        return "";
    }

    /**
     * Create a data structure to hold the values to be included in the report.
     *
     * @param ddPcrResults
     * @return values for the report for each sample.
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     */
    private List<Map<String, Object>> setFieldsForReport(List<DataRecord> ddPcrResults){
        List<Map<String, Object>> reportFieldValueMaps = new ArrayList<>();
        for (DataRecord record : ddPcrResults) {
            Map<String, Object> reportFieldValues = new HashMap<>();
            try {
                reportFieldValues.put("SampleId", record.getValue("SampleId", user));
                reportFieldValues.put("OtherSampleId", record.getValue("OtherSampleId", user));
                reportFieldValues.put("Assay", record.getValue("Assay", user));
                reportFieldValues.put("TotalInput", record.getValue("TotalInput", user));
                reportFieldValues.put("TotalDetected", record.getValue("TotalDnaDetected", user));
                reportFieldValues.put("DropletCountTest", record.getValue("DropletCountMutation", user));
                reportFieldValues.put("DropletCountRef", record.getValue("DropletCountWildType", user));
                reportFieldValues.put("Ratio", record.getValue("Ratio", user));
                reportFieldValues.put("AcceptedDroplets", record.getValue("AcceptedDroplets", user));
                reportFieldValues.put("HumanPercentage", record.getValue("HumanPercentage", user));
                reportFieldValues.put("MicronicTubeBarcode", getMicronicTubeIdFromParentSample(record));
                reportFieldValues.put("FractionalAbundance", record.getValue("FractionalAbundance", user));
                reportFieldValues.put("CNV", record.getValue("CNV", user));
                reportFieldValues.put("ConcentrationMutation", record.getValue("ConcentrationMutation", user));
                reportFieldValues.put("ConcentrationWildType", record.getValue("ConcentrationWildType", user));
                reportFieldValueMaps.add(reportFieldValues);
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error setting field values for report:\n%s", ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error setting field values for report:\n%s", ExceptionUtils.getStackTrace(notFound)));
            }
        }
        sortMapBySampleId(reportFieldValueMaps);
        return reportFieldValueMaps;
    }

    /**
     * Sort the data based on the SampleId value.
     *
     * @param data
     */
    private void sortMapBySampleId(List<Map<String, Object>> data) {
        data.sort(Comparator.comparing(o -> o.get("SampleId").toString()));
    }

    /**
     * Prompt user for the Type of report to generate.
     *
     * @return Report Type
     */
    private String getReportTypeFromUser() throws RemoteException, ServerException {
        List plateDim = clientCallback.showListDialog("Please Select the Type of ddPCR Report to Generate:", ddPCRReportTypes, false, user);
        return plateDim.get(0).toString();
    }

    /**
     * Get the Header values to include in the report based on user selection of "Report Type".
     *
     * @param reportType
     * @return Header values for the Report.
     */
    private List<String> getHeaderBasedOnReportType(String reportType) {
        switch (reportType.toLowerCase()) {
            case "cnv":
                return cnvReportHeaders;
            case "gex":
                return gexReportHeaders;
            case "red":
                return redReportHeaders;
            case "pdx":
                return PDXReportHeaders;
            case "lab medicine":
                return labMedicineReportHeaders;
            case "methylated":
                return methylatedReportHeaders;
        }
        return cnvReportHeaders;
    }

    /**
     * Create styles for the Header Cells.
     *
     * @param workbook
     * @return Cell style for the header.
     */
    private CellStyle getHeaderStyle(XSSFWorkbook workbook) {
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 14);
        font.setFontName("Calibri");
        font.setColor(IndexedColors.BLACK.getIndex());
        font.setBold(true);
        font.setItalic(false);
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setFont(font);
        return style;
    }

    /**
     * Create styles for the non header data cells.
     *
     * @param workbook
     * @param cell
     * @return Cell style for the data cells.
     */
    private Cell setDataCellStyle(XSSFWorkbook workbook, Cell cell) {
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 14);
        font.setFontName("Calibri");
        font.setColor(IndexedColors.BLACK.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setWrapText(true);
        style.setFont(font);
        cell.setCellStyle(style);
        return cell;
    }

    /**
     * Auto-size columns to show complete test in the cells.
     *
     * @param sheet
     * @param header
     */
    private void autoSizeColumns(XSSFSheet sheet, List<String> header) {
        for (int i = 0; i < header.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Add data values and styles to the excel workbook.
     *
     * @param headerValues
     * @param dataValues
     * @param workbook
     */
    private void generateExcelDataWorkbook(String reportType, List<String> headerValues, List<Map<String, Object>> dataValues, XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("ddPCR Report");
        int rowId = 0;
        XSSFRow row = sheet.createRow(rowId);
        rowId++;
        int cellId = 0;
        for (String headerValue : headerValues) {
            Cell cell = row.createCell(cellId);
            cell.setCellValue(headerValue);
            cell.setCellStyle(getHeaderStyle(workbook));
            sheet.autoSizeColumn(cellId);
            cellId++;
        }
        /** private List<String> gexReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Gene", "Concentration Ref", "Ratio ([Gene]/[Ref])", "Droplet Count Gene", "Droplet Count Ref", "Accepted Droplets");
         *     private List<String> cnvReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Gene", "Concentration Ref", "CNV", "Droplet Count Gene", "Droplet Count Ref", "Accepted Droplets");
         *     private List<String> redReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration MU", "Concentration WT", "Fractional abundance", "Droplet Count MU", "Droplet Count WT", "Accepted Droplets");
         *     private List<String> PDXReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Human", "Concentration Mouse", "Ratio ([Human]/[Mouse])", "Droplet Count Human", "Droplet Count Mouse", "Accepted Droplets", "Human %");
         *     private List<String> methylatedReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Methylated", "Concentration Unmethylated", "Ratio ([Methylated]/[Unmethylated])", "Droplet Count Methylated", "Droplet Count Unmethylated", "Accepted Droplets");
         *     private List<String> labMedicineReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Concentration Gene", "Concentration Ref", "Ratio ([Gene]/[Ref])", "Droplet Count Gene", "Droplet Count Ref", "Total Detected (ng)", "Accepted Droplets", "Micronic Tube Barcode");
         */
        for (Map<String, Object> data : dataValues) {
            row = sheet.createRow(rowId);
            setDataCellStyle(workbook, row.createCell(0)).setCellValue(data.get("Assay").toString());
            setDataCellStyle(workbook, row.createCell(1)).setCellValue(data.get("OtherSampleId").toString());
            setDataCellStyle(workbook, row.createCell(2)).setCellValue(data.get("SampleId").toString());
            setDataCellStyle(workbook, row.createCell(3)).setCellValue(Double.parseDouble(data.get("TotalInput").toString()));
            setDataCellStyle(workbook, row.createCell(4)).setCellValue(Double.parseDouble(data.get("ConcentrationMutation").toString()));
            setDataCellStyle(workbook, row.createCell(5)).setCellValue(Double.parseDouble(data.get("ConcentrationWildType").toString()));
            setDataCellStyle(workbook, row.createCell(7)).setCellValue(Integer.parseInt(data.get("DropletCountTest").toString()));
            setDataCellStyle(workbook, row.createCell(8)).setCellValue(Integer.parseInt(data.get("DropletCountRef").toString()));

            if (reportType.equals("CNV")) {
                setDataCellStyle(workbook, row.createCell(6)).setCellValue(Double.parseDouble(data.get("CNV").toString()));
                setDataCellStyle(workbook, row.createCell(9)).setCellValue(Integer.parseInt(data.get("AcceptedDroplets").toString()));
            }
            else if (reportType.equals("RED")) {
                setDataCellStyle(workbook, row.createCell(6)).setCellValue(Double.parseDouble(data.get("FractionalAbundance").toString()));
                setDataCellStyle(workbook, row.createCell(9)).setCellValue(Integer.parseInt(data.get("AcceptedDroplets").toString()));
            }
            else if (reportType.equals("GEX") || reportType.equals("METHYLATED")) {
                setDataCellStyle(workbook, row.createCell(6)).setCellValue(Double.parseDouble(data.get("Ratio").toString()));
                setDataCellStyle(workbook, row.createCell(9)).setCellValue(Integer.parseInt(data.get("AcceptedDroplets").toString()));
            }
            else if (reportType.equals("PDX")) {
                setDataCellStyle(workbook, row.createCell(6)).setCellValue(Double.parseDouble(data.get("Ratio").toString()));
                setDataCellStyle(workbook, row.createCell(9)).setCellValue(Integer.parseInt(data.get("AcceptedDroplets").toString()));
                setDataCellStyle(workbook, row.createCell(10)).setCellValue(Double.parseDouble(data.get("HumanPercentage").toString()));
            }
            else if (reportType.equals("LAB MEDICINE")) {
                setDataCellStyle(workbook, row.createCell(6)).setCellValue(Double.parseDouble(data.get("Ratio").toString()));
                setDataCellStyle(workbook, row.createCell(9)).setCellValue(Double.parseDouble(data.get("TotalDetected").toString()));
                setDataCellStyle(workbook, row.createCell(10)).setCellValue(Integer.parseInt(data.get("AcceptedDroplets").toString()));
                if (data.get("MicronicTubeBarcode") != null) {
                    setDataCellStyle(workbook, row.createCell(11)).setCellValue(data.get("MicronicTubeBarcode").toString());
                } else {
                    setDataCellStyle(workbook, row.createCell(11)).setCellValue("");
                }
            }
            rowId++;
        }
        autoSizeColumns(sheet, headerValues);
    }

    /**
     * Generate output file name for Report
     *
     * @param attachedRecords
     * @return Project ID's separated by "_" and prefixed with "Project_"
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String generateFileNameFromRequestIds(List<DataRecord> attachedRecords) {
        Set<String> requestIds = new HashSet<>();
        for (DataRecord sample : attachedRecords) {
            String requestId = getParentRequestId(sample);
            if (!StringUtils.isBlank(requestId)) {
                requestIds.add(requestId);
            }
        }
        return "Project_" + StringUtils.join(requestIds, "_");
    }

    /**
     * This method is designed to find and return the Sample in hierarchy with a desired child DataType
     *
     * @param sample
     * @return Sample DataRecord
     * @throws IoError
     * @throws RemoteException
     */
    private String getParentRequestId(DataRecord sample){
        DataRecord record = null;
        Stack<DataRecord> samplePile = new Stack<>();
        samplePile.push(sample);
        try {
            do {
                DataRecord startSample = samplePile.pop();
                List<DataRecord> parentRecords = startSample.getParentsOfType("Sample", user);
                if (!parentRecords.isEmpty() && parentRecords.get(0).getParentsOfType("Request", user).size() > 0) {
                    record = parentRecords.get(0);
                }
                if (!parentRecords.isEmpty() && record == null) {
                    samplePile.push(parentRecords.get(0));
                }
            } while (!samplePile.empty());

            if ((record != null) && (record.getValue("RequestId", user) != null)) {
                return (String) record.getValue("RequestId", user);
            }
        } catch (RemoteException | ServerException e) {
            logError(String.format("Exception -> Error getting Parent RequestId for Sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error getting Parent RequestId for Sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error getting Parent RequestId for Sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
        }
        return "";
    }

    /**
     * Export the final report as Excel file.
     *
     * @param workbook
     * @param outFileName
     * @throws IOException
     * @throws ServerException
     */
    private void exportReport(XSSFWorkbook workbook, String outFileName) {
        if (StringUtils.isBlank(outFileName)) {
            outFileName = "Project_";
        }
        logInfo("Generating ddPCR Report " + outFileName + "_ddPCR_Report.xlsx");
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            workbook.write(byteStream);
            byteStream.close();
            byte[] bytes = byteStream.toByteArray();
            clientCallback.writeBytes(bytes, outFileName + "_ddPCR_Report.xlsx");
        } catch (ServerException e) {
            logError(String.format("RemoteException -> Error while exporting DdPcr report:\n%s",ExceptionUtils.getStackTrace(e)));
        } catch (IOException e) {
            logError(String.format("IOException -> Error while exporting DdPcr report:\n%s", ExceptionUtils.getStackTrace(e)));
        } finally {
            try {
                byteStream.close();
            } catch (IOException e) {
                logError(String.format("IOException -> Error while closing the ByteArrayOutputStream:\n%s", ExceptionUtils.getStackTrace(e)));
            }
        }

    }

    /**
     * Method to map Human Percentage values from DdPcrAssayResults to QCReport records.
     *
     * @param savedRecords
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws InvalidValue
     * @throws ServerException
     */
    private void mapHumanPercentageFromDdpcrResultsToDnaQcReport(List<DataRecord> savedRecords) throws IoError, RemoteException, NotFound, InvalidValue, ServerException {
        for (DataRecord rec : savedRecords) {
            if (rec.getDataTypeName().equalsIgnoreCase("DdPcrAssayResults") && rec.getValue("HumanPercentage", user) != null) {
                List<DataRecord> parentSamples = rec.getParentsOfType("Sample", user);
                Double humanPercentage = rec.getDoubleVal("HumanPercentage", user);
                if (parentSamples.size() > 0) {
                    DataRecord parentSample = parentSamples.get(0);
                    String requestId = parentSample.getStringVal("RequestId", user);
                    List<DataRecord> qcReports = getQcReportRecords(parentSample, requestId);
                    for (DataRecord qr : qcReports) {
                        qr.setDataField("HumanPercentage", humanPercentage, user);
                    }
                }
            }
        }
    }

    /**
     * Method to get QcReport records for Sample.
     *
     * @param sample
     * @param requestId
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private List<DataRecord> getQcReportRecords(DataRecord sample, String requestId) throws IoError, RemoteException, NotFound, ServerException, UnrecoverableServerException{
        if (sample.getChildrenOfType("QcReportDna", user).length > 0) {
            return Arrays.asList(sample.getChildrenOfType("QcReportDna", user));
        }
        List<DataRecord> qcReports = new ArrayList<>();
        Stack<DataRecord> sampleStack = new Stack<>();
        sampleStack.add(sample);
        while (sampleStack.size() > 0) {
            DataRecord nextSample = sampleStack.pop();
            if (requestId.equalsIgnoreCase(nextSample.getStringVal("RequestId", user)) && nextSample.getChildrenOfType("QcReportDna", user).length > 0) {
                return Arrays.asList(nextSample.getChildrenOfType("QcReportDna", user));
            }
            List<DataRecord> parentSamples = nextSample.getParentsOfType("Sample", user);
            if (parentSamples.size() > 0 && parentSamples.get(0).getValue("RequestId", user) != null
                    && parentSamples.get(0).getStringVal("RequestId", user).equals(requestId)) {
                sampleStack.addAll(parentSamples);
            }
        }
        return qcReports;
    }
}

