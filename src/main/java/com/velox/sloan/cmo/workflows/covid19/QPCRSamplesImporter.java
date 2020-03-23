package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;

import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to read the 9 columns of interest in the csv file from the output of the qPCR machine.
 */
public class QPCRSamplesImporter extends DefaultGenericPlugin {
    private final String IMPORT_QPCR_RESULTS = "IMPORT QPCR RESULTS";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    public QPCRSamplesImporter() {
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
                return new PluginResult(false);
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

            //parse data to create datarecords.
            List<Map<String, Object>> parsedData = parseQpcrData(qpcrValueRows);
            saveQpcrData(parsedData);
            logInfo(String.format("Saved %d %s DataRecords created from uploaded file %s", parsedData.size(), activeTask.getInputDataTypeName(), csvFilePath));
        } catch (Exception e) {
            String errMsg = String.format("Error reading qPCR Sample Information", Arrays.toString(e.getStackTrace()));
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
    private List<Map<String, Object>> parseQpcrData(List<String> qpcrDataRows){
        Map<String, Integer> headerValuesMap = utils.getCsvHeaderValueMap(qpcrDataRows);
        List<Map<String, Object>> parsedData = new ArrayList<>();
        for (int i=1; i<qpcrDataRows.size(); i++){
            List<String> rowValues = Arrays.asList(qpcrDataRows.get(i).split(","));
            if (rowValues.size()> 0){
                Map<String , Object> parsedValues = new HashMap<>();
                parsedValues.put("OtherSampleId", rowValues.get(headerValuesMap.get("Sample")));
                parsedValues.put("TargetAssay", rowValues.get(headerValuesMap.get("Target")));
                parsedValues.put("CqValue", rowValues.get(headerValuesMap.get("Cq")));
                parsedValues.put("CqMean", rowValues.get(headerValuesMap.get("Cq Mean")));
                parsedValues.put("CqStdDev", rowValues.get(headerValuesMap.get("Cq SD")));
                parsedValues.put("Threshold", rowValues.get(headerValuesMap.get("Threshold")));
                parsedData.add(parsedValues);
            }
        }
        return parsedData;
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
}