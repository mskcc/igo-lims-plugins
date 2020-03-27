package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.utilities.CsvHelper;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

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
                for (DataRecord rec: activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user)){
                    recordIds.add(rec.getRecordId());
                }
                activeTask.removeTaskAttachments(recordIds);
            }
            //entire data from file
            List<String> entireFile = utils.readDataFromCsvFile(clientCallback.readBytes(csvFilePath));

            //data without unnecessary rows in the beginning of file with # and should be skipped.
            List<String> qpcrValueRows = getQpcrResults(entireFile, csvFilePath);

            //parse QPCR data for each row separated by sample name.
            Map<String, List<Map<String, Object>>> parsedData = parseQpcrData(qpcrValueRows);

            //analyze parsed data.
            List<Map<String, Object>> analyzedData = analyzeParsedQpcrData(parsedData);
            saveQpcrData(analyzedData);

            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            if(attachedSamples.size()==0){
                clientCallback.displayWarning("Samples not found attached to the task. Some information may not be available in the report.");
            }
            appendSampleInfoToReport(analyzedData, attachedSamples);
            exportQPCRCompleteReport(analyzedData);
            exportPositiveSamplesReport(analyzedData);
            exportInconclusiveSamplesReport(analyzedData);
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
        List<String>data = new ArrayList<>();
        for (String line: fileData){
            if(!line.contains("#")){
                data.add(line);
            }
        }
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
     * Method to parse QPCR data from file into values for DataRecord.
     * @param qpcrDataRows
     * @return
     */
    private Map<String, List<Map<String, Object>>> parseQpcrData(List<String> qpcrDataRows) throws ServerException {
        Map<String, Integer> headerValuesMap = utils.getCsvHeaderValueMap(qpcrDataRows);
        Map<String, List<Map<String, Object>>> parsedData = new HashMap<>();
        for (int i=1; i<qpcrDataRows.size(); i++){
            List<String> rowValues = Arrays.asList(qpcrDataRows.get(i).split(","));
            if (rowValues.size()> 0){
                String otherSampleId = rowValues.get(headerValuesMap.get("Sample")).trim();
                if(!StringUtils.isBlank(otherSampleId)) {
                    Map<String, Object> parsedValues = new HashMap<>();
                    parsedData.putIfAbsent(otherSampleId, new ArrayList<>());
                    parsedValues.put("OtherSampleId", otherSampleId);
                    parsedValues.put("TargetAssay", rowValues.get(headerValuesMap.get("Target")));
                    parsedValues.put("CqValue", rowValues.get(headerValuesMap.get("Cq")));
                    parsedValues.put("CqMean", rowValues.get(headerValuesMap.get("Cq Mean")));
                    parsedData.get(otherSampleId).add(parsedValues);
                }
            }
        }
        return parsedData;
    }

    /**
     * Method to get Mean CQ values from QPCR data for sample.
     * @param qpcrValues
     * @return
     */
    private Object getCqMean (List<Map<String, Object>> qpcrValues) throws ServerException {
        for (Map<String, Object> vals : qpcrValues){
            Object cqMean = vals.get("CqMean");
            if (cqMean != null && !vals.get("CqMean").toString().equalsIgnoreCase("undetermined")){
                return vals.get("CqMean");
            }
        }
        return 0;
    }

    /**
     * Method to get CQ Value for an assay from QPCR data for sample.
     * @param qpcrValues
     * @param assayName
     * @return
     */
    private Object getCqValueForAssay(List<Map<String, Object>> qpcrValues, String assayName){
        for (Map<String, Object> vals : qpcrValues){
            Object targetAssay = vals.get("TargetAssay");
            Object cqValue = vals.get("CqValue");
            if(targetAssay != null && targetAssay.toString().equalsIgnoreCase(assayName)){
                if (cqValue!= null && !cqValue.toString().equalsIgnoreCase("undetermined")){
                    return cqValue;
                }
            }
        }
        return "Undetermined";
    }

    /**
     * Methos to get Translate CQ value from QPCR cq values.
     * @param cqValue
     * @return
     */
    private Integer getTranslatedCQValue(Object cqValue){
        if(!cqValue.toString().equalsIgnoreCase("undetermined")){
            Double cq = Double.parseDouble(cqValue.toString());
            if (cq > 0 && cq < 40.0) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Method to get annotation value for test results as Positive/Invalid/Undetected/Inconclusive
     * @param translatedCQSum
     * @return
     */
    private String getAssayResult(Integer translatedCQSum) {
        switch (translatedCQSum) {
            case 0:
                return "Invalid";
            case 1:
                return "Not detected";
            case 2:
                return "Inconclusive";
            case 3:
                return "Detected";
        }
        return "Invalid";
    }

    /**
     * Method to generate analyzed QPCR data.
     * @param parsedData
     * @return
     * @throws ServerException
     */
    private List<Map<String, Object>> analyzeParsedQpcrData(Map<String, List<Map<String, Object>>> parsedData) throws ServerException {
        List<Map<String, Object>> analyzedData = new ArrayList<>();
        for (String key : parsedData.keySet()){
            Map<String, Object> analyzedValues = new HashMap<>();
            List<Map<String, Object>> sampleQpcrValues = parsedData.get(key);
            analyzedValues.put("OtherSampleId", key);
            analyzedValues.put("CqMean", getCqMean(sampleQpcrValues));
            //extract cq values for each assay from parse sample values
            Object cqN1 = getCqValueForAssay(sampleQpcrValues, "N1");
            Object cqN2 = getCqValueForAssay(sampleQpcrValues, "N2");
            Object cqRP = getCqValueForAssay(sampleQpcrValues, "RP");
            //parse cq values to 0 and 1 based on cq values range. Presumption : cq = undetermined = 0
            Integer translatedCQN1 = getTranslatedCQValue(cqN1);
            Integer translatedCQN2 = getTranslatedCQValue(cqN2);
            Integer translatedCQRP = getTranslatedCQValue(cqRP);
            //get the sum of translated values
            Integer translatedSum = translatedCQN1 + translatedCQN2 + translatedCQRP;
            analyzedValues.put("CqN1", cqN1);
            analyzedValues.put("CqN2", cqN2);
            analyzedValues.put("CqRP", cqRP);
            analyzedValues.put("TranslatedCQN1", translatedCQN1);
            analyzedValues.put("TranslatedCQN2", translatedCQN2);
            analyzedValues.put("TranslatedCQRP", translatedCQRP);
            analyzedValues.put("SumCqForAssays", translatedSum);
            analyzedValues.put("AssayResult", getAssayResult(translatedSum));
            analyzedData.add(analyzedValues);
        }
        return analyzedData;
    }

    /**
     * Method to append sample level information (RNA Plate ID and Well ID) to analyzed data.
     * @param analyzedData
     * @param attachedSamples
     * @throws NotFound
     * @throws RemoteException
     */
    private void appendSampleInfoToReport(List<Map<String, Object>> analyzedData, List<DataRecord> attachedSamples) throws NotFound, RemoteException {
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
     * Method to get values from Map using key value.
     * @param data
     * @param key
     * @return
     */
    private String getValueFromMap(Map<String, Object> data, String key){
        if(data.get(key)!=null){
            return data.get(key).toString();
        }
        return "";
    }

    /**
     *  Method to generate report for Positive Samples.
     * @param analyzedData
     * @throws ServerException
     * @throws IOException
     */
    private void exportPositiveSamplesReport(List<Map<String,Object>> analyzedData) throws ServerException, IOException {
        List<List<String>> reportData = new ArrayList<>();
        List<String> header = Arrays.asList("Sample Name", "CQ Mean", "Cq N1", "Cq N2", "Cq RP", "Translated Cq N1", "Translated Cq N2", "Translated Cq RP", "Sum Translated Cq Values", "Assay Results");
        reportData.add(header);
        for (Map<String, Object> data : analyzedData){
            Object assayResult = data.get("AssayResult");
            if( assayResult != null && assayResult.toString().equalsIgnoreCase("detected")) {
                List<String> rowValues = new ArrayList<>();
                String sampleName = getValueFromMap(data, "OtherSampleId");
                String cqMean = getValueFromMap(data, "CqMean");
                String cqN1 = getValueFromMap(data, "CqN1");
                String cqN2 = getValueFromMap(data, "CqN2");
                String cqRP = getValueFromMap(data, "CqRP");
                String translatedCQN1 = getValueFromMap(data, "TranslatedCQN1");
                String translatedCQN2 = getValueFromMap(data, "TranslatedCQN2");
                String translatedCQRP = getValueFromMap(data, "TranslatedCQRP");
                String sumTranslatedVals = getValueFromMap(data, "SumCqForAssays");
                String result = getValueFromMap(data, "AssayResult");
                rowValues.add(sampleName);
                rowValues.add(cqMean);
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
        if(reportData.size()> 1) {
            String fileName = StringUtils.join("POSITIVE_COVID-19_POSITIVE_CASES_Report.csv");
            byte[] sampleSheetBytes = CsvHelper.writeCSV(reportData, null);
            clientCallback.writeBytes(sampleSheetBytes, fileName, true);
        }
    }

    /**
     *  Method to generate report for Inconclusive Samples.
     * @param analyzedData
     * @throws IOException
     * @throws ServerException
     */
    private void exportInconclusiveSamplesReport(List<Map<String, Object>> analyzedData) throws IOException, ServerException {
        List<List<String>> reportData = new ArrayList<>();
        List<String> header = Arrays.asList("Sample Name", "CQ Mean", "Cq N1", "Cq N2", "Cq RP", "Translated Cq N1", "Translated Cq N2", "Translated Cq RP", "Sum Translated Cq Values", "Assay Results", "RNA Plate ID", "Plate Well ID");
        reportData.add(header);
        for (Map<String, Object> data : analyzedData) {
            Object assayResult = data.get("AssayResult");
            if (assayResult != null && assayResult.toString().equalsIgnoreCase("inconclusive")) {
                List<String> rowValues = new ArrayList<>();
                String sampleName = getValueFromMap(data, "OtherSampleId");
                String cqMean = getValueFromMap(data, "CqMean");
                String cqN1 = getValueFromMap(data, "CqN1");
                String cqN2 = getValueFromMap(data, "CqN2");
                String cqRP = getValueFromMap(data, "CqRP");
                String translatedCQN1 = getValueFromMap(data, "TranslatedCQN1");
                String translatedCQN2 = getValueFromMap(data, "TranslatedCQN2");
                String translatedCQRP = getValueFromMap(data, "TranslatedCQRP");
                String sumTranslatedVals = getValueFromMap(data, "SumCqForAssays");
                String result = getValueFromMap(data, "AssayResult");
                String plateId = getValueFromMap(data, "RNAPlateId");
                String wellId = getValueFromMap(data, "RNAPlateWellId");
                rowValues.add(sampleName);
                rowValues.add(cqMean);
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
        if (reportData.size() > 1) {
            String fileName = StringUtils.join("INCONCLUSIVE_COVID-19_I_CASES_Report.csv");
            byte[] sampleSheetBytes = CsvHelper.writeCSV(reportData, null);
            clientCallback.writeBytes(sampleSheetBytes, fileName, true);
        }
    }

    /**
     * Method to generate report for Positive Samples.
     * @param analyzedData
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private Integer getTotalPositiveSamples(List<Map<String, Object>> analyzedData) throws NotFound, RemoteException {
        return (int)analyzedData.stream().filter(i -> getValueFromMap(i, "AssayResult").equalsIgnoreCase("detected")).count();
    }

    /**
     * Method to generate report for Inconclusive Samples.
     * @param analyzedData
     * @return
     */
    private Integer getTotalInconclusiveSamples(List<Map<String, Object>> analyzedData) {
        return (int)analyzedData.stream().filter(i -> getValueFromMap(i, "AssayResult").equalsIgnoreCase("inconclusive")).count();
    }

    /**
     * Method to generate Complete QPCR report.
     * @param analyzedData
     * @throws IOException
     * @throws ServerException
     * @throws NotFound
     */
    private void exportQPCRCompleteReport(List<Map<String, Object>> analyzedData) throws IOException, ServerException, NotFound {
        List<List<String>> reportData = new ArrayList<>();
        List<String> reportRow1 = Arrays.asList("Total Samples", String.valueOf(analyzedData.size()));
        List<String> reportRow2 = Arrays.asList("Total Positive", String.valueOf(getTotalPositiveSamples(analyzedData)));
        List<String> reportRow3 = Arrays.asList("Total Inconclusive", String.valueOf(getTotalInconclusiveSamples(analyzedData)));
        List<String> header = Arrays.asList("Sample Name", "CQ Mean", "Cq N1", "Cq N2", "Cq RP", "Translated Cq N1", "Translated Cq N2", "Translated Cq RP", "Sum Translated Cq Values", "Assay Results", "RNA Plate ID", "Plate Well ID");
        reportData.add(reportRow1);
        reportData.add(reportRow2);
        reportData.add(reportRow3);
        reportData.add(header);
        for (Map<String, Object> data : analyzedData) {
            List<String> rowValues = new ArrayList<>();
            String sampleName = getValueFromMap(data, "OtherSampleId");
            String cqMean = getValueFromMap(data, "CqMean");
            String cqN1 = getValueFromMap(data, "CqN1");
            String cqN2 = getValueFromMap(data, "CqN2");
            String cqRP = getValueFromMap(data, "CqRP");
            String translatedCQN1 = getValueFromMap(data, "TranslatedCQN1");
            String translatedCQN2 = getValueFromMap(data, "TranslatedCQN2");
            String translatedCQRP = getValueFromMap(data, "TranslatedCQRP");
            String sumTranslatedVals = getValueFromMap(data, "SumCqForAssays");
            String result = getValueFromMap(data, "AssayResult");
            String plateId = getValueFromMap(data, "RNAPlateId");
            String wellId = getValueFromMap(data, "RNAPlateWellId");
            rowValues.add(sampleName);
            rowValues.add(cqMean);
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
        if (reportData.size() > 1) {
            String fileName = StringUtils.join("COVID-19_I_CASES_COMPLETE_Report.csv");
            byte[] sampleSheetBytes = CsvHelper.writeCSV(reportData, null);
            clientCallback.writeBytes(sampleSheetBytes, fileName, true);
        }
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
            clientCallback.displayInfo(parsedData.toString());
            List<DataRecord> qpcrData = dataRecordManager.addDataRecords("Covid19TestProtocol5", parsedData, user);
            activeTask.addAttachedDataRecords(qpcrData);
        }
    }
}