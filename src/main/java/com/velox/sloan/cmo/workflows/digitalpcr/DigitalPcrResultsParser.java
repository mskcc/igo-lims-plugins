package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to import ddPCR results into LIMS. The Raw data from csv file is parsed,
 * and plugin will calculate the final results and store in "DdPcrAssayResults" DataType as child to sample.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class DigitalPcrResultsParser extends DefaultGenericPlugin {

    private final String HUMAN_MOUSE_PERCENTAGE_ASSAY_NAME = "Mouse_Human_CNV_PTGER2";
    private final List<String> expectedRawResultsHeaders = Arrays.asList("Well", "ExptType", "Experiment", "Sample", "TargetType", "Target",
            "Status", "Concentration", "Supermix", "CopiesPer20uLWell", "TotalConfMax", "TotalConfMin", "PoissonConfMax", "PoissonConfMin",
            "Positives", "Negatives", "Ch1+Ch2+", "Ch1+Ch2-", "Ch1-Ch2+", "Ch1-Ch2-", "Linkage", "AcceptedDroplets");
    IgoLimsPluginUtils igoUtils = new IgoLimsPluginUtils();
    DdPcrResultsProcessor resultsProcessor = new DdPcrResultsProcessor();

    public DigitalPcrResultsParser() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
        setDescription("Generates report for ddPCR experiment with specific columns.");
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("PARSE DDPCR RESULTS")
                && !activeTask.getTask().getTaskOptions().containsKey("_DDPCR RESULTS PARSED");
    }

    public PluginResult run() throws ServerException {
        try {
            List<String> filesWithDigitalPcrRawData = clientCallback.showMultiFileDialog("Please upload Raw Data files", null);
            if (filesWithDigitalPcrRawData.isEmpty()) {
                return new PluginResult(false);
            }
            List<String> fileData = readDataFromFiles(filesWithDigitalPcrRawData);
            if (!isValidFile(filesWithDigitalPcrRawData, fileData)) {
                return new PluginResult(false);
            }
            removeDuplicateHeaderFromCombinedData(fileData);
            Map<String, Integer> headerValueMap = igoUtils.getCsvHeaderValueMap(fileData);
            List<List<String>> channel1Data = getChannel1Data(fileData, headerValueMap);
            List<List<String>> channel2Data = getChannel2Data(fileData, headerValueMap);
            List<Map<String, Object>> channel1Channe2CombinedData = flattenChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap);
            Map<String, List<Map<String, Object>>> groupedData = groupResultsBySampleAndAssay(channel1Channe2CombinedData);
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords("DdPcrProtocol1", user);
            if (attachedProtocolRecords.isEmpty()) {
                clientCallback.displayError("No attached 'DdPcrProtocol1' records found attached to this task.");
                logError("No attached 'DdPcrProtocol1' records found attached to this task.");
                return new PluginResult(false);
            }
            List<Map<String, Object>> analyzedData = runDataAnalysisForAssays(groupedData, attachedProtocolRecords);
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            if (attachedProtocolRecords.isEmpty()) {
                clientCallback.displayError("No attached 'Sample' records found attached to this task.");
                logError("No attached 'Sample' records found attached to this task.");
                return new PluginResult(false);
            }
            addResultsAsChildRecords(analyzedData, attachedSampleRecords);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while parsing the ddPCR Results file:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
        }
        return new PluginResult(true);
    }

    /**
     * Check if the passed file is a valid CSV file.
     * @param fileNames
     * @param fileData
     * @return
     * @throws ServerException
     */
    private boolean isValidFile(List<String> fileNames, List<String> fileData) throws ServerException {
        for (String name : fileNames) {
            if (!igoUtils.isCsvFile(name)) {
                clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", name));
                logError(String.format("Uploaded file '%s' is not a '.csv' file", name));
                return false;
            }
            if (!igoUtils.csvFileContainsRequiredHeaders(fileData, expectedRawResultsHeaders)) {
                clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", name));
                logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", name));
                return false;
            }
            if (!igoUtils.csvFileHasData(fileData)) {
                clientCallback.displayError(String.format("Uploaded file '%s' has does not contain data. Please check the file", name));
                logError(String.format("Uploaded file '%s' has does not contain data. Please check the file", name));
                return false;
            }
        }
        return true;
    }

    /**
     * Method to read data when users upload multiple CSV files.
     * @param fileNames
     * @return Combined data from multiples CSV files
     * @throws ServerException
     * @throws IOException
     */
    private List<String> readDataFromFiles(List<String> fileNames) throws ServerException, IOException {
        List<String> combinedFileData = new ArrayList<>();
        for (String file : fileNames){
            List<String> data = igoUtils.readDataFromCsvFile(clientCallback.readBytes(file));
            combinedFileData.addAll(data);
        }
        return combinedFileData;
    }

    /**
     * Remove duplicate headers when data from multiple files is combined
     * @param data
     */
    private void removeDuplicateHeaderFromCombinedData(List<String> data){
        if(Arrays.asList(data.get(0).split(",")).containsAll(expectedRawResultsHeaders)){
            for (int i = 1; i < data.size(); i++) {
                if (Arrays.asList(data.get(i).split(",")).containsAll(expectedRawResultsHeaders)) {
                    data.remove(i);
                }
            }
        }
    }

    /**
     * Get the data related to channel1 in the raw data under "TargetType" column in ddPCR results.
     *
     * @param fileData
     * @param headerValueMap
     * @return data related to channel1 in the raw data under "TargetType" column in ddPCR results.
     */
    private List<List<String>> getChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap) {
        return resultsProcessor.readChannel1Data(fileData, headerValueMap);
    }

    /**
     * @param fileData
     * @param headerValueMap
     * @return data related to channel2 in the raw data under "TargetType" column in ddPCR results.
     */
    private List<List<String>> getChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap) {
        return resultsProcessor.readChannel2Data(fileData, headerValueMap);
    }

    /**
     * Add Channel2 data to the rows containing Channel1 data as Concentration of the Reference.
     *
     * @param channel1Data
     * @param channel2Data
     * @param headerValueMap
     * @return Channel1 and Channel2 combined data.
     */
    private List<Map<String, Object>> flattenChannel1AndChannel2Data(List<List<String>> channel1Data, List<List<String>> channel2Data, Map<String, Integer> headerValueMap) {
        return resultsProcessor.concatenateChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap);
    }

    /**
     * Group the data based on Sample and Target values in the results.
     *
     * @param flatData
     * @return data grouped by Sample and Target values.
     */
    private Map<String, List<Map<String, Object>>> groupResultsBySampleAndAssay(List<Map<String, Object>> flatData) {
        return resultsProcessor.aggregateResultsBySampleAndAssay(flatData);
    }

    /**
     * Get average of specific values from the List of HashMaps.
     *
     * @param sampleData
     * @param fieldName
     * @return average of values under key identified by fieldName passed to the method.
     */
    private Double getAverage(List<Map<String, Object>> sampleData, String fieldName) {
        return resultsProcessor.calculateAverage(sampleData, fieldName);
    }

    /**
     * Get Sum of specific values from the List of HashMaps.
     *
     * @param sampleData
     * @param fieldName
     * @return sun of values under key identified by fieldName passed to the method.
     */
    private Integer getSum(List<Map<String, Object>> sampleData, String fieldName) {
        return resultsProcessor.calculateSum(sampleData, fieldName);
    }

    /**
     * Get total input used for sample and replicates to perform the experiment.
     *
     * @param sampleName
     * @param assayName
     * @param protocolRecords
     * @return DNA input amount used to perform the experiment.
     * @throws NotFound
     * @throws RemoteException
     */
    private Double getTotalInputForSample(String sampleName, String assayName, List<DataRecord> protocolRecords) throws NotFound, RemoteException {
        for (DataRecord record : protocolRecords) {
            Object sampleNameOnProtocol = record.getValue("OtherSampleId", user);
            Object assayOnProtocol = record.getValue("Ch1Target", user);
            Object igoSampleIdOnProtocol = record.getStringVal("SampleId", user);
            if (sampleNameOnProtocol != null && assayOnProtocol != null && sampleName.equalsIgnoreCase(sampleNameOnProtocol.toString())
                    && assayName.equalsIgnoreCase(assayOnProtocol.toString())) {
                Object totalInput = record.getValue("Aliq1TargetMass", user);
                if (totalInput != null) {
                    return (Double) totalInput;
                }
            } else if (sampleNameOnProtocol != null && assayOnProtocol != null && sampleName.equalsIgnoreCase(igoSampleIdOnProtocol.toString())
                    && assayName.equalsIgnoreCase(assayOnProtocol.toString())) {
                Object totalInput = record.getValue("Aliq1TargetMass", user);
                if (totalInput != null) {
                    return (Double) totalInput;
                }
            }
        }
        return 0.0;
    }

    /**
     * Calculate Ration between two values.
     *
     * @param dropletCountMutation
     * @param dropletCountWildType
     * @return ration of dropletCountMutation/dropletCountWildType.
     */
    private Double getRatio(Double dropletCountMutation, Double dropletCountWildType) {
        return resultsProcessor.calculateRatio(dropletCountMutation, dropletCountWildType);
    }

    /**
     * Calculate total DNA detected in the ddPCR experiment.
     *
     * @param concentrationMutation
     * @param concentrationWildType
     * @return total DNA amount detected in the ddPCR experiment results.
     */
    private Double calculateTotalDnaDetected(Double concentrationMutation, Double concentrationWildType) {
        return resultsProcessor.calculateTotalDnaDetected(concentrationMutation, concentrationWildType);
    }

    /**
     * Calculate the percentage of Human and Mouse DNA in case the samples are xenografts.
     *
     * @param dropletCountMutation
     * @param dropletCountWildType
     * @return Percentage of Human sample in the sample used for ddPCR Assay.
     */
    private Double calculateHumanPercentage(Integer dropletCountMutation, Integer dropletCountWildType) {
        return resultsProcessor.calculateHumanPercentage(dropletCountMutation, dropletCountWildType);
    }

    /**
     * Calculate final result values from the raw data.
     *
     * @param groupedData
     * @param protocolRecords
     * @return final ddPCR results. One row per sample.
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Map<String, Object>> runDataAnalysisForAssays(Map<String, List<Map<String, Object>>> groupedData, List<DataRecord> protocolRecords) throws NotFound, RemoteException {
        logInfo("Analyzing ddPCR Results.");
        List<Map<String, Object>> analyzedDataValues = new ArrayList<>();
        for (String key : groupedData.keySet()) {
            Map<String, Object> analyzedData = new HashMap<>();
            String sampleName = key.split("/")[0];
            String target = key.split("/")[1];
            analyzedData.put("Assay", target);
            analyzedData.put("OtherSampleId", sampleName);
            analyzedData.put("ConcentrationMutation", getAverage(groupedData.get(key), "ConcentrationMutation"));
            analyzedData.put("ConcentrationWildType", getAverage(groupedData.get(key), "ConcentrationWildType"));
            analyzedData.put("Channel1PosChannel2Pos", getSum(groupedData.get(key), "Channel1PosChannel2Pos"));
            analyzedData.put("Channel1PosChannel2Neg", getSum(groupedData.get(key), "Channel1PosChannel2Neg"));
            analyzedData.put("Channel1NegChannel2Pos", getSum(groupedData.get(key), "Channel1NegChannel2Pos"));
            Integer dropletCountMutation = (Integer) analyzedData.get("Channel1PosChannel2Pos") + (Integer) analyzedData.get("Channel1PosChannel2Neg");
            Integer dropletCountWildType = (Integer) analyzedData.get("Channel1PosChannel2Pos") + (Integer) analyzedData.get("Channel1NegChannel2Pos");
            Double totalDnaDetected = calculateTotalDnaDetected((Double) analyzedData.get("ConcentrationMutation"), (Double) analyzedData.get("ConcentrationWildType"));
            analyzedData.put("DropletCountMutation", dropletCountMutation);
            analyzedData.put("DropletCountWildType", dropletCountWildType);
            analyzedData.put("TotalDnaDetected", totalDnaDetected);
            Double ratio = getRatio(Double.valueOf(analyzedData.get("DropletCountMutation").toString()), Double.valueOf(analyzedData.get("DropletCountWildType").toString()));
            analyzedData.put("Ratio", ratio);
            analyzedData.put("AcceptedDroplets", getSum(groupedData.get(key), "AcceptedDroplets"));
            if (target.equalsIgnoreCase(HUMAN_MOUSE_PERCENTAGE_ASSAY_NAME)) {
                Double humanPercentage = calculateHumanPercentage(dropletCountMutation, dropletCountWildType);
                analyzedData.put("HumanPercentage", humanPercentage);
            }
            analyzedData.put("TotalInput", getTotalInputForSample(sampleName, target, protocolRecords));
            analyzedDataValues.add(analyzedData);
        }
        return analyzedDataValues;
    }


        /**
     * Add the results as Children to the Sample DataType.
     *
     * @param analyzedDataValues
     * @param attachedSamples
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws IoError
     */
    private void addResultsAsChildRecords(List<Map<String, Object>> analyzedDataValues, List<DataRecord> attachedSamples) throws NotFound, RemoteException, ServerException, IoError {
        //List<Object> alreadyAdded = new ArrayList<>();
        List<DataRecord> recordsToAttachToTask = new ArrayList<>();
        logInfo(Integer.toString(analyzedDataValues.size()));
        for (Map<String, Object> data : analyzedDataValues) {
            logInfo(data.toString());
            String analyzedDataSampleId = data.get("OtherSampleId").toString();
            for (DataRecord sample : attachedSamples) {
                Object sampleId = sample.getValue("SampleId", user);
                Object otherSampleId = sample.getValue("OtherSampleId", user);
                if (analyzedDataSampleId.equals(otherSampleId) && data.get("SampleId") == null) {
                    data.put("SampleId", sampleId);
                    logInfo(data.toString());
                    DataRecord  recordToAttach = sample.addChild(activeTask.getInputDataTypeName(), data, user);
                    recordsToAttachToTask.add(recordToAttach);
                }
            }
        }
        activeTask.addAttachedDataRecords(recordsToAttachToTask);
        activeTask.getTask().getTaskOptions().put("_DDPCR RESULTS PARSED", "");
    }


}

