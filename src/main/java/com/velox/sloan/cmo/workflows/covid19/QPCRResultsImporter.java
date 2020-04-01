package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
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
 * This plugin is designed to read QPCR raw data, analyze and transform it to create reports, export reports and
 * save analyzed data in LIMS.
 *
 * @author sharmaa1
 */
public class QPCRResultsImporter extends DefaultGenericPlugin {

    private final String IMPORT_QPCR_RESULTS = "IMPORT QPCR RESULTS";
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private Covid19Helper helper = new Covid19Helper();
    public QPCRResultsImporter() {
        setTaskEntry(true);
        setTaskToolbar(true);
        setLine1Text("Upload qPCR");
        setLine2Text(" CSV File");
        setDescription("Use this button to import qPCR results from a .csv file.");
        setOrder(PluginOrder.EARLY.getOrder()+1);
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(IMPORT_QPCR_RESULTS);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(IMPORT_QPCR_RESULTS) &&
                    activeTask.getStatus() == ActiveTask.COMPLETE;
        }catch (Throwable e){
            return false;
        }
    }

    public PluginResult run() throws ServerException {
        try {
            String csvFilePath = clientCallback.showFileDialog("Upload the csv file with qPCR results.", null);
            if (csvFilePath == null) {
                logInfo("Path to csv file is empty or file not uploaded and process cancelled by the user.");
                return new PluginResult(true);
            }
            if (!isValidCsvFile(csvFilePath)) {
                clientCallback.displayError(String.format("Not a valid csv file %s.", csvFilePath));
                logError(String.format("Not a valid csv file %s.", csvFilePath));
                return new PluginResult(false);
            }
            //remove already attached records from task if already created. This is done to allow for the task to run again and not create duplicate rows of data;
            if (activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user).size()>0){
                List<Long> recordIds = new ArrayList<>();
                List<DataRecord> protocolRecords = activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user);
                for (DataRecord rec: protocolRecords){
                    recordIds.add(rec.getRecordId());
                }
                activeTask.removeTaskAttachments(recordIds);
                dataRecordManager.deleteDataRecords(protocolRecords, null, false, user);
                logInfo(String.format("QPCR results file re-uploaded -> Deleted %s records attached to task created by previous QPCR results upload", activeTask.getInputDataTypeName()));
            }
            //entire data from file
            List<String> entireFile = utils.readDataFromCsvFile(clientCallback.readBytes(csvFilePath));

            //data without unnecessary rows in the beginning of file with # and should be skipped.
            List<String> qpcrValueRows = getQpcrResults(entireFile, csvFilePath);

            //parse QPCR data for each row separated by sample name.
            Map<String, List<Map<String, Object>>> parsedData = helper.parseQpcrData(qpcrValueRows);

            //analyze parsed data.
            List<Map<String, Object>> analyzedData = helper.analyzeParsedQpcrData(parsedData);

            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            if(attachedSamples.size()==0){
                clientCallback.displayWarning("Samples not found attached to the task. RNA Sample/Plate information may not be available in the report.");
            }
            appendSampleInfoToReport(analyzedData, attachedSamples);
            saveQpcrData(analyzedData);
            exportReport(analyzedData);
            logInfo(String.format("Saved %d %s DataRecords created from uploaded file %s", analyzedData.size(), activeTask.getInputDataTypeName(), csvFilePath));
        } catch (Exception e) {
            String errMsg = String.format("Error reading qPCR Sample Information %s", Arrays.toString(e.getStackTrace()));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to get data from user uploaded file
     * @param fileData
     * @return
     */
    private List<String> getQpcrResults(List<String> fileData, String fileName) throws ServerException {
        List<String>data = helper.getQpcrResults(fileData);
        if(data.size()<2){
            clientCallback.displayError(String.format("uploaded file '%s' does not contain data", fileName));
            logError(String.format("uploaded file '%s' does not contain data", fileName));
            return null;
        }
        return data;
    }

    /**
     * Method to check if csv file has the valid extension.
     *
     * @param fileName
     * @return true/false
     * @throws ServerException
     */
    private boolean isValidCsvFile(String fileName) throws ServerException {
        if (!fileName.toLowerCase().endsWith(".csv")) {
            String errMsg = String.format("File '%s' is invalid file type. Only csv files with the extension .csv are accepted.", fileName);
            logError(errMsg);
            clientCallback.displayError(errMsg);
            return false;
        }
        return true;
    }

    /**
     * Method to check sample data in the uploaded file has corresponding sample in the attached samples.
     * @param analyzedData
     * @param attachedSamples
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private void checkForExtraSamplesInQpcrData(List<Map<String, Object>> analyzedData, List<DataRecord> attachedSamples) throws NotFound, RemoteException, ServerException {
        for (Map<String, Object> data : analyzedData){
            String sampleName = data.get("OtherSampleId").toString();
            boolean sampleFound = false;
            for (DataRecord sample: attachedSamples){
                Object otherSampleId = sample.getValue("OtherSampleId", user);
                Object isControl = sample.getValue("IsControl", user);
                if(otherSampleId!=null && isControl !=null && !(boolean)isControl && otherSampleId.toString().equalsIgnoreCase(sampleName)){
                    sampleFound=true;
                    break;
                }
            }
            if(!sampleFound){
                clientCallback.displayWarning(String.format("Sample with Sample Name %s in uploaded file not found attached to the task.", sampleName));
            }
        }
    }

    /**
     * Method to append sample level information (RNA Plate ID and Well ID) to analyzed data.
     * @param analyzedData
     * @param attachedSamples
     * @throws NotFound
     * @throws RemoteException
     */
    private void appendSampleInfoToReport(List<Map<String, Object>> analyzedData, List<DataRecord> attachedSamples) throws NotFound, RemoteException, ServerException {
        checkForExtraSamplesInQpcrData(analyzedData, attachedSamples);
        for (DataRecord sample : attachedSamples){
            Object otherSampleId = sample.getValue("OtherSampleId", user);
            Object plateId = sample.getValue("RelatedRecord23", user);
            Object rowPosition = sample.getValue("RowPosition", user);
            Object colPositon = sample.getStringVal("ColPosition", user);
            for(Map<String, Object> ad : analyzedData){
                if(otherSampleId != null && ad.get("OtherSampleId") == otherSampleId){
                    if(plateId!=null){
                        ad.put("RNAPlateId", plateId);
                    }
                    if(rowPosition!= null && colPositon!= null){
                        String wellId = rowPosition.toString() + colPositon.toString();
                        ad.put("RNAPlateWellId", wellId);
                    }
                }
            }
        }
    }

    /**
     *  Method to generate report for Positive Samples.
     * @param analyzedData
     * @throws ServerException
     * @throws IOException
     */
    private List<List<String>> getPositiveSamplesReport(List<Map<String,Object>> analyzedData){
        List<List<String>> reportData = new ArrayList<>();
        List<String> header = Arrays.asList("Sample Name", "Cq N1", "Cq N2", "Cq RP", "Translated Cq N1", "Translated Cq N2", "Translated Cq RP", "Sum Translated Cq Values", "Assay Results");
        reportData.add(header);
        for (Map<String, Object> data : analyzedData){
            Object assayResult = data.get("AssayResult");
            if( assayResult != null && assayResult.toString().equalsIgnoreCase("detected")) {
                List<String> rowValues = new ArrayList<>();
                String sampleName = helper.getValueFromMap(data, "OtherSampleId");
                //String cqMean = helper.getValueFromMap(data, "CqMean");
                String cqN1 = helper.getValueFromMap(data, "CqN1");
                String cqN2 = helper.getValueFromMap(data, "CqN2");
                String cqRP = helper.getValueFromMap(data, "CqRP");
                String translatedCQN1 = helper.getValueFromMap(data, "TranslatedCQN1");
                String translatedCQN2 = helper.getValueFromMap(data, "TranslatedCQN2");
                String translatedCQRP = helper.getValueFromMap(data, "TranslatedCQRP");
                String sumTranslatedVals = helper.getValueFromMap(data, "SumCqForAssays");
                String result = helper.getValueFromMap(data, "AssayResult");
                rowValues.add(sampleName);
                //rowValues.add(cqMean);
                rowValues.add(cqN1);
                rowValues.add(cqN2);
                rowValues.add(cqRP);
                rowValues.add(translatedCQN1);
                rowValues.add(translatedCQN2);
                rowValues.add(translatedCQRP);
                rowValues.add(sumTranslatedVals);
                rowValues.add(result);
                reportData.add(rowValues);
            }
        }
        return reportData;
    }

    /**
     *  Method to generate report for Inconclusive Samples.
     * @param analyzedData
     * @throws IOException
     * @throws ServerException
     */
    private List<List<String>> getInconclusiveSamplesReport(List<Map<String, Object>> analyzedData) throws IOException, ServerException {
        List<List<String>> reportData = new ArrayList<>();
        List<String> header = Arrays.asList("Sample Name", "Cq N1", "Cq N2", "Cq RP", "Translated Cq N1", "Translated Cq N2", "Translated Cq RP", "Sum Translated Cq Values", "Assay Results", "RNA Plate ID", "Plate Well ID");
        reportData.add(header);
        for (Map<String, Object> data : analyzedData) {
            Object assayResult = data.get("AssayResult");
            if (assayResult != null && assayResult.toString().equalsIgnoreCase("inconclusive")) {
                List<String> rowValues = new ArrayList<>();
                String sampleName = helper.getValueFromMap(data, "OtherSampleId");
                //String cqMean = helper.getValueFromMap(data, "CqMean");
                String cqN1 = helper.getValueFromMap(data, "CqN1");
                String cqN2 = helper.getValueFromMap(data, "CqN2");
                String cqRP = helper.getValueFromMap(data, "CqRP");
                String translatedCQN1 = helper.getValueFromMap(data, "TranslatedCQN1");
                String translatedCQN2 = helper.getValueFromMap(data, "TranslatedCQN2");
                String translatedCQRP = helper.getValueFromMap(data, "TranslatedCQRP");
                String sumTranslatedVals = helper.getValueFromMap(data, "SumCqForAssays");
                String result = helper.getValueFromMap(data, "AssayResult");
                String plateId = helper.getValueFromMap(data, "RNAPlateId");
                String wellId = helper.getValueFromMap(data, "RNAPlateWellId");
                rowValues.add(sampleName);
                //rowValues.add(cqMean);
                rowValues.add(cqN1);
                rowValues.add(cqN2);
                rowValues.add(cqRP);
                rowValues.add(translatedCQN1);
                rowValues.add(translatedCQN2);
                rowValues.add(translatedCQRP);
                rowValues.add(sumTranslatedVals);
                rowValues.add(result);
                rowValues.add(plateId);
                rowValues.add(wellId);
                reportData.add(rowValues);
            }
        }
        return reportData;
    }

    /**
     * Method to generate Complete QPCR report.
     * @param analyzedData
     * @throws IOException
     * @throws ServerException
     * @throws NotFound
     */
    private List<List<String>> getQPCRCompleteReport(List<Map<String, Object>> analyzedData) throws IOException, ServerException, NotFound {
        List<List<String>> reportData = new ArrayList<>();
        List<String> reportRow1 = Arrays.asList("Total Samples", String.valueOf(helper.getTotalSamples(analyzedData)));
        List<String> reportRow2 = Arrays.asList("Total Positive", String.valueOf(helper.getTotalPositiveSamples(analyzedData)));
        List<String> reportRow3 = Arrays.asList("Total Inconclusive", String.valueOf(helper.getTotalInconclusiveSamples(analyzedData)));
        List<String> header = Arrays.asList("Sample Name", "Cq N1", "Cq N2", "Cq RP", "Translated Cq N1", "Translated Cq N2", "Translated Cq RP", "Sum Translated Cq Values", "Assay Results", "RNA Plate ID", "Plate Well ID");
        reportData.add(reportRow1);
        reportData.add(reportRow2);
        reportData.add(reportRow3);
        reportData.add(header);
        for (Map<String, Object> data : analyzedData) {
            List<String> rowValues = new ArrayList<>();
            String sampleName = helper.getValueFromMap(data, "OtherSampleId");
            //String cqMean = helper.getValueFromMap(data, "CqMean");
            String cqN1 = helper.getValueFromMap(data, "CqN1");
            String cqN2 = helper.getValueFromMap(data, "CqN2");
            String cqRP = helper.getValueFromMap(data, "CqRP");
            String translatedCQN1 = helper.getValueFromMap(data, "TranslatedCQN1");
            String translatedCQN2 = helper.getValueFromMap(data, "TranslatedCQN2");
            String translatedCQRP = helper.getValueFromMap(data, "TranslatedCQRP");
            String sumTranslatedVals = helper.getValueFromMap(data, "SumCqForAssays");
            String result = helper.getValueFromMap(data, "AssayResult");
            String plateId = helper.getValueFromMap(data, "RNAPlateId");
            String wellId = helper.getValueFromMap(data, "RNAPlateWellId");
            rowValues.add(sampleName);
            //rowValues.add(cqMean);
            rowValues.add(cqN1);
            rowValues.add(cqN2);
            rowValues.add(cqRP);
            rowValues.add(translatedCQN1);
            rowValues.add(translatedCQN2);
            rowValues.add(translatedCQRP);
            rowValues.add(sumTranslatedVals);
            rowValues.add(result);
            rowValues.add(plateId);
            rowValues.add(wellId);
            reportData.add(rowValues);
        }
        return reportData;
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
        style.setFont(font);
        cell.setCellStyle(style);
        return cell;
    }

    /**
     * Method to create new DataRecords using parsed data and attach them to the workflow task running the plugin.
     * @param parsedData
     * @throws ServerException
     * @throws RemoteException
     */
    private void saveQpcrData(List<Map<String, Object>> parsedData) throws ServerException, RemoteException {
        if(parsedData.size()==0){
            clientCallback.displayError("Cannot parse any QPCR data from the file. Plase make sure that the data is in correct format.");
        }
        else{
            List<DataRecord> qpcrData = dataRecordManager.addDataRecords("Covid19TestProtocol5", parsedData, user);
            activeTask.addAttachedDataRecords(qpcrData);
        }
    }

    /**
     * Method to add worksheets with report data to workbook.
     * @param data
     * @param worksheetName
     * @param workbook
     */
    private void addWorkbookSheet(List<List<String>> data, String worksheetName, XSSFWorkbook workbook){
        XSSFSheet sheet = workbook.createSheet(worksheetName);
        boolean header = true;
        boolean isHeaderRow = true;
        int rowId = 0;
        for (List<String> list : data ){
            XSSFRow row = sheet.createRow(rowId);
            int cellId = 0;
            for (String value : list){
                if(header){
                    Cell cell = row.createCell(cellId);
                    cell.setCellValue(value);
                    cell.setCellStyle(getHeaderStyle(workbook));
                    sheet.autoSizeColumn(cellId);
                }
                else {
                    setDataCellStyle(workbook, row.createCell(cellId)).setCellValue(value);
                }
                if(value.equalsIgnoreCase("Sample Name")){
                    isHeaderRow = false;
                }
                cellId++;
            }
            header = isHeaderRow;
            rowId++;
        }
    }

    /**
     * Method to export excel report.
     * @param analyzedData
     * @throws ServerException
     * @throws IOException
     */
    private void exportReport(List<Map<String, Object>> analyzedData) throws ServerException, IOException {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            List<List<String>> completeReport = getQPCRCompleteReport(analyzedData);
            addWorkbookSheet(completeReport, "QPCR Report", workbook);

            List<List<String>> positiveReport = getPositiveSamplesReport(analyzedData);
            addWorkbookSheet(positiveReport, "Positives", workbook);

            List<List<String>> inconclusiveReport = getInconclusiveSamplesReport(analyzedData);
            addWorkbookSheet(inconclusiveReport, "Inconclusives", workbook);

            String date = java.time.LocalDate.now().toString();
            String outFileName = " QPCR_Report_" + date + ".xlsx";
            logInfo("Generating QPCR Report: " + outFileName);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try {
                workbook.write(byteStream);
            } catch (Exception e) {
                String errMsg = String.format("Error reading qPCR Sample Information %s", Arrays.toString(e.getStackTrace()));
                clientCallback.displayError(errMsg);
                logError(errMsg);
            } finally {
                byteStream.close();
            }
            byte[] bytes = byteStream.toByteArray();
            clientCallback.writeBytes(bytes, outFileName, true);
        } catch (Exception e) {
            String message = String.format("Error occured while exporting report:\n%s", ExceptionUtils.getMessage(e));
            clientCallback.displayError(message);
            logError(message);
        }
    }
}