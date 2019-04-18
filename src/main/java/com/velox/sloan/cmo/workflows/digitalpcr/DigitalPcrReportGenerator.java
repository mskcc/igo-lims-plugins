package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
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
 * @author Ajay Sharma
 */

public class DigitalPcrReportGenerator extends DefaultGenericPlugin {

    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin", "Path Extraction Techs", "Group Leaders", "NA Team"};
    private List<String> ddPCRReportTypes = Arrays.asList("GEX", "RED", "CNV");
    private List<String> gexReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Droplet # gene", "Droplet # Ref", "Ratio ([GOI]/[Ref])", "Accepted Droplets", "Micronic Tube Barcode");
    private List<String> cnvReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Droplet Count Mu", "Droplet Count WT", "Ratio ([Mu]/[WT])", "Accepted Droplets", "Micronic Tube Barcode");
    private List<String> redReportHeaders = Arrays.asList("Assay", "Sample ID", "IGO ID", "Total Input (ng)", "Droplet Count Mu", "Droplet Count WT", "Ratio ([Mu]/[WT])", "Accepted Droplets", "Micronic Tube Barcode");

    public DigitalPcrReportGenerator() {
        setTaskTableToolbar(true);
        setTaskFormToolbar(true);
        setLine1Text("Generate ddPCR");
        setLine2Text("Report");
        setDescription("Generates report for ddPCR experiment with specific columns.");
        setIcon("com/velox/sloan/cmo/resources/export_32.gif");
        setUserGroupList(permittedUsers);
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("GENERATE DDPCR REPORT");
    }

    @Override
    public boolean onTaskFormToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("GENERATE DDPCR REPORT");
        } catch (RemoteException e) {
            logError(Arrays.toString(e.getStackTrace()));
        }
        return false;
    }

    @Override
    public boolean onTaskTableToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("GENERATE DDPCR REPORT");
        } catch (RemoteException e) {
            logError(Arrays.toString(e.getStackTrace()));
        }
        return false;
    }

    public PluginResult run() throws ServerException {
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
            generateExcelDataWorkbook(headerForReport, valuesForReport, workbook);
            String fileName = generateFileNameFromRequestIds(attachedSamples);
            exportReport(workbook, fileName);
        } catch (Exception e) {
            clientCallback.displayError(String.format(":( Error while generating DDPCR Report:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
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
    private Object getMicronicTubeIdFromParentSample(DataRecord ddPcrReportRecord) throws IoError, RemoteException, NotFound {
        List<DataRecord> parentSampleRecords = ddPcrReportRecord.getParentsOfType("Sample", user);
        if (!parentSampleRecords.isEmpty()) {
            Object micronicTubeBarcode = parentSampleRecords.get(0).getValue("MicronicTubeBarcode", user);
            if (micronicTubeBarcode != null) {
                return micronicTubeBarcode;
            }
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
    private List<Map<String, Object>> setFieldsForReport(List<DataRecord> ddPcrResults) throws NotFound, RemoteException, IoError {
        List<Map<String, Object>> reportFieldValueMaps = new ArrayList<>();
        for (DataRecord record : ddPcrResults) {
            Map<String, Object> reportFieldValues = new HashMap<>();
            reportFieldValues.put("SampleId", record.getValue("SampleId", user));
            reportFieldValues.put("OtherSampleId", record.getValue("OtherSampleId", user));
            reportFieldValues.put("Assay", record.getValue("Assay", user));
            reportFieldValues.put("TotalInput", record.getValue("TotalInput", user));
            reportFieldValues.put("DropletCountTest", record.getValue("DropletCountMutation", user));
            reportFieldValues.put("DropletCountRef", record.getValue("DropletCountWildType", user));
            reportFieldValues.put("Ratio", record.getValue("Ratio", user));
            reportFieldValues.put("AcceptedDroplets", record.getValue("AcceptedDroplets", user));
            reportFieldValues.put("MicronicTubeBarcode", getMicronicTubeIdFromParentSample(record));
            reportFieldValueMaps.add(reportFieldValues);
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
    private String getReportTypeFromUser() {
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
    private void generateExcelDataWorkbook(List<String> headerValues, List<Map<String, Object>> dataValues, XSSFWorkbook workbook) {
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
        for (Map<String, Object> data : dataValues) {
            row = sheet.createRow(rowId);
            setDataCellStyle(workbook, row.createCell(0)).setCellValue(data.get("Assay").toString());
            setDataCellStyle(workbook, row.createCell(1)).setCellValue(data.get("OtherSampleId").toString());
            setDataCellStyle(workbook, row.createCell(2)).setCellValue(data.get("SampleId").toString());
            setDataCellStyle(workbook, row.createCell(3)).setCellValue(Double.parseDouble(data.get("TotalInput").toString()));
            setDataCellStyle(workbook, row.createCell(4)).setCellValue(Integer.parseInt(data.get("DropletCountTest").toString()));
            setDataCellStyle(workbook, row.createCell(5)).setCellValue(Integer.parseInt(data.get("DropletCountRef").toString()));
            setDataCellStyle(workbook, row.createCell(6)).setCellValue(Double.parseDouble(data.get("Ratio").toString()));
            setDataCellStyle(workbook, row.createCell(7)).setCellValue(Integer.parseInt(data.get("AcceptedDroplets").toString()));
            if (data.get("MicronicTubeBarcode") != null) {
                setDataCellStyle(workbook, row.createCell(8)).setCellValue(data.get("MicronicTubeBarcode").toString());
            } else {
                setDataCellStyle(workbook, row.createCell(8)).setCellValue("");
            }
            rowId++;
        }
        autoSizeColumns(sheet, headerValues);
    }

    /**
     * Generate output file name for Report
     * @param attachedRecords
     * @return Project ID's separated by "_" and prefixed with "Project_"
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String generateFileNameFromRequestIds(List<DataRecord> attachedRecords) throws IoError, RemoteException, NotFound {
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
    private String getParentRequestId(DataRecord sample) throws IoError, RemoteException, NotFound {
        DataRecord record = null;
        Stack<DataRecord> samplePile = new Stack<>();
        samplePile.push(sample);
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

        if((record != null) && (record.getValue("RequestId", user) != null)){
            return (String)record.getValue("RequestId",user);
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
    private void exportReport(XSSFWorkbook workbook, String outFileName) throws IOException, ServerException {
        if (StringUtils.isBlank(outFileName)) {
            outFileName = "Project_";
        }
        logInfo("Generating ddPCR Report " + outFileName + "_ddPCR_Report.xlsx");
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            workbook.write(byteStream);
        } finally {
            byteStream.close();
        }
        byte[] bytes = byteStream.toByteArray();
        clientCallback.writeBytes(bytes, outFileName + "_ddPCR_Report.xlsx");
    }
}

