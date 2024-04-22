package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

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

    public int qxResultType = 0;
    private final String HUMAN_MOUSE_PERCENTAGE_ASSAY_NAME = "Mouse_Human_CNV_PTGER2";
private final List<String> expectedQx200RawResultsHeaders = Arrays.asList("Well", "ExptType", "Experiment", "Sample", "TargetType", "Target",
        "Status", "Concentration", "Supermix", "CopiesPer20uLWell", "TotalConfMax", "TotalConfMin", "PoissonConfMax", "PoissonConfMin",
        "Positives", "Negatives", "Ch1+Ch2+", "Ch1+Ch2-", "Ch1-Ch2+", "Ch1-Ch2-", "Linkage", "AcceptedDroplets", "CNV", "FractionalAbundance");

//    private final List<String> expectedQx600RawResultsHeaders = Arrays.asList("Well","Sample description 1","Sample description 2",
//            "Sample description 3","Sample description 4","Target","Conc(copies/µL)","pg/µL","Status","Status Reason","Experiment",
//            "SampleType","TargetType","Supermix","DyeName(s)","Copies/20µLWell","TotalConfMax","TotalConfMin","PoissonConfMax",
//            "PoissonConfMin","Accepted Droplets","Positives","Negatives","Copies/uL linked molecules","CNV","TotalCNVMax",
//            "TotalCNVMin","PoissonCNVMax","PoissonCNVMin","ReferenceCopies","UnknownCopies","Threshold1","Threshold2","Threshold3",
//            "ThresholdSigmaAbove","ThresholdSigmaBelow","ReferenceUsed","Ratio","TotalRatioMax","TotalRatioMin","PoissonRatioMax",
//            "PoissonRatioMin","Fractional Abundance","TotalFractionalAbundanceMax","TotalFractionalAbundanceMin",
//            "PoissonFractionalAbundanceMax","PoissonFractionalAbundanceMin","MeanAmplitudeOfPositives","MeanAmplitudeOfNegatives",
//            "MeanAmplitudeTotal","ExperimentComments","MergedWells","TotalConfidenceMax68","TotalConfidenceMin68",
//            "PoissonConfidenceMax68","PoissonConfidenceMin68","TotalCNVMax68","TotalCNVMin68","PoissonCNVMax68","PoissonCNVMin68",
//            "TotalRatioMax68","TotalRatioMin68","PoissonRatioMax68","PoissonRatioMin68","TotalFractionalAbundanceMax68",
//            "TotalFractionalAbundanceMin68","PoissonFractionalAbundanceMax68","PoissonFractionalAbundanceMin68","TiltCorrected",
//            "Ch1+Ch2+","Ch1+Ch2-","Ch1-Ch2+","Ch1-Ch2-","Ch3+Ch4+","Ch3+Ch4-","Ch3-Ch4+","Ch3-Ch4-","Ch5+Ch6+","Ch5+Ch6-","Ch5-Ch6+","Ch5-Ch6-");
private final List<String> expectedQx600RawResultsHeaders = Arrays.asList("Well","Sample description 1", "Sample description 2",
        "Status","Experiment", "SampleType","TargetType","Supermix","DyeName(s)", "TotalConfMax","TotalConfMin","PoissonConfMax",
        "PoissonConfMin","Positives","Negatives","CNV","TotalCNVMax", "TotalCNVMin","PoissonCNVMax","PoissonCNVMin","ReferenceCopies",
        "UnknownCopies","Threshold1","Threshold2","Threshold3", "ThresholdSigmaAbove","ThresholdSigmaBelow","ReferenceUsed",
        "Ratio","TotalRatioMax","TotalRatioMin","PoissonRatioMax", "PoissonRatioMin", "Fractional Abundance", "TotalFractionalAbundanceMax",
        "TotalFractionalAbundanceMin", "PoissonFractionalAbundanceMax", "PoissonFractionalAbundanceMin","MeanAmplitudeOfPositives",
        "MeanAmplitudeOfNegatives", "MeanAmplitudeTotal", "ExperimentComments","MergedWells","TotalConfidenceMax68",
        "TotalConfidenceMin68", "PoissonConfidenceMax68", "PoissonConfidenceMin68","TotalCNVMax68","TotalCNVMin68",
        "PoissonCNVMax68","PoissonCNVMin68", "TotalRatioMax68", "TotalRatioMin68","PoissonRatioMax68","PoissonRatioMin68",
        "TotalFractionalAbundanceMax68", "TotalFractionalAbundanceMin68", "PoissonFractionalAbundanceMax68",
        "PoissonFractionalAbundanceMin68","TiltCorrected", "Ch1+Ch2+","Ch1+Ch2-","Ch1-Ch2+", "Ch1-Ch2-","Ch3+Ch4+","Ch3+Ch4-",
        "Ch3-Ch4+","Ch3-Ch4-","Ch5+Ch6+","Ch5+Ch6-","Ch5-Ch6+","Ch5-Ch6-");

    IgoLimsPluginUtils igoUtils = new IgoLimsPluginUtils();
    DdPcrResultsProcessor resultsProcessor = new DdPcrResultsProcessor();

    public DigitalPcrResultsParser() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
        setDescription("Generates report for ddPCR experiment with specific columns.");
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("PARSE DDPCR RESULTS");
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<String> filesWithDigitalPcrRawData = clientCallback.showMultiFileDialog("Please upload Raw Data files", null);
            if (filesWithDigitalPcrRawData.size()==0) {
                clientCallback.displayError("User did not upload results file.");
                return new PluginResult(false);
            }
            List<String> dataInFiles = igoUtils.readDataFromFiles(filesWithDigitalPcrRawData, clientCallback);
            String[] QXResultSheetType = {"QX200", "QX600"};
            qxResultType = clientCallback.showOptionDialog("QX Manager Type", "Which QX result type have you uploaded?", QXResultSheetType, 0);
            logInfo("qxResultType = " + qxResultType);
            boolean isQX200 = true;
            if (qxResultType == 1) {
                isQX200 = false;
            }
            if (!isValidFile(filesWithDigitalPcrRawData, dataInFiles)) {
                return new PluginResult(false);
            }
            //remove already attached records from task if already created. This is done to allow for the task to run again;
            if (activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user).size()>0){
                List<Long> recordIds = new ArrayList<>();
                List<DataRecord> protocolRecords = activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user);
                for (DataRecord rec: protocolRecords){
                    recordIds.add(rec.getRecordId());
                }
                activeTask.removeTaskAttachments(recordIds);
                dataRecordManager.deleteDataRecords(protocolRecords, null, false, user);
                logInfo(String.format("DDPCR results file re-uploaded -> Deleted %s records attached to task created by previous DDPCR results upload", activeTask.getInputDataTypeName()));
            }
            //read data from file and create new ddpcr assay results.
            for (String file : filesWithDigitalPcrRawData) {
                List<String> fileData = igoUtils.readDataFromCsvFile(clientCallback.readBytes(file));
                Map<String, Integer> headerValueMap = igoUtils.getCsvHeaderValueMap(fileData);
                List<List<String>> channel1Data = getChannel1Data(fileData, headerValueMap);
                List<List<String>> channel2Data = getChannel2Data(fileData, headerValueMap);
                List<Map<String, Object>> channel1Channe2CombinedData = flattenChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap, isQX200);
                logInfo("Flattened data");
                Map<String, List<Map<String, Object>>> groupedData = groupResultsBySampleAndAssay(channel1Channe2CombinedData);
                logInfo(groupedData.toString());
                List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords("DdPcrProtocol1SixChannels", user); // DdPcrProtocol1SixChannels
                if (attachedProtocolRecords.isEmpty()) {
                    clientCallback.displayError("No attached 'DdPcrProtocol1SixChannels' records found attached to this task."); // DdPcrProtocol1SixChannels
                    logError("No attached 'DdPcrProtocol1SixChannels' records found attached to this task."); // DdPcrProtocol1SixChannels
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
            }
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while parsing DDPCR results file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IOException e) {
            String errMsg = String.format("IOException Error while parsing DDPCR results file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Check if the passed file is a valid CSV file.
     *
     * @param fileNames
     * @param fileData
     * @return
     * @throws ServerException
     */
    private boolean isValidFile(List<String> fileNames, List<String> fileData) throws ServerException, RemoteException {
        for (String name : fileNames) {
            if (!igoUtils.isCsvFile(name)) {
                clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", name));
                logError(String.format("Uploaded file '%s' is not a '.csv' file", name));
                return false;
            }
            logInfo("file data = " + Arrays.asList(fileData.get(0).split(",")));
            if (qxResultType == 0) {
                if (!igoUtils.csvFileContainsRequiredHeaders(fileData, expectedQx200RawResultsHeaders, pluginLogger)) {
                    clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", name));
                    logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", name));
                    return false;
                }
            }
            else {
                if (!igoUtils.csvFileContainsRequiredHeaders(fileData, expectedQx600RawResultsHeaders, pluginLogger)) {
                    clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", name));
                    logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", name));
                    return false;
                }
            }
            if (!igoUtils.csvFileHasData(fileData)) {
                clientCallback.displayError(String.format("Uploaded file '%s' has does not contain data. Please check the file", name));
                logError(String.format("Uploaded file '%s' has does not contain data. Please check the file", name));
                return false;
            }
        }
        return true;
    }



//    /**
//     * Remove duplicate headers when data from multiple files is combined
//     *
//     * @param data
//     */
//    private void removeDuplicateHeaderFromCombinedData(List<String> data) {
//        if (Arrays.asList(data.get(0).split(",")).containsAll(expectedRawResultsHeaders)) {
//            for (int i = 1; i < data.size(); i++) {
//                if (Arrays.asList(data.get(i).split(",")).containsAll(expectedRawResultsHeaders)) {
//                    data.remove(i);
//                }
//            }
//        }
//    }

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
    private List<Map<String, Object>> flattenChannel1AndChannel2Data(List<List<String>> channel1Data, List<List<String>> channel2Data, Map<String, Integer> headerValueMap, boolean isQX200) {
        return resultsProcessor.concatenateChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap, isQX200);
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
    private Double getTotalInputForSample(String sampleName, String assayName, List<DataRecord> protocolRecords) {
        for (DataRecord record : protocolRecords) {
            try {
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
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while reading data from InputDataType with OtherSampleId '%s':\n%s", sampleName, ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while reading data from InputDataType with OtherSampleId '%s':\n%s", sampleName, ExceptionUtils.getStackTrace(notFound)));
            }
        }
        return 0.0;
    }
    /**
     * Calculate Ration between two values.
     *
     * @param concentrationGene
     * @param concentrationRef
     * @return ration of concentrationGene/concentrationRef.
     */
    private Double getRatio(Double concentrationGene, Double concentrationRef) {
        return resultsProcessor.calculateRatio(concentrationGene, concentrationRef);
    }

    /**
     * Calculate total DNA detected in the ddPCR experiment.
     *
     * @param concentrationGene
     * @param concentrationRef
     * @return total DNA amount detected in the ddPCR experiment results.
     */
    private Double calculateTotalDnaDetected(Double concentrationGene, Double concentrationRef) {
        return resultsProcessor.calculateTotalDnaDetected(concentrationGene, concentrationRef);
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
    private List<Map<String, Object>> runDataAnalysisForAssays(Map<String, List<Map<String, Object>>> groupedData, List<DataRecord> protocolRecords){
        logInfo("Analyzing ddPCR Results.");
        List<Map<String, Object>> analyzedDataValues = new ArrayList<>();
        for (String key : groupedData.keySet()) {
            Map<String, Object> analyzedData = new HashMap<>();
            String sampleName = key.split("/")[0];
            String target = key.split("/")[1];
            analyzedData.put("Assay", target);
            analyzedData.put("OtherSampleId", sampleName);
            analyzedData.put("CNV", groupedData.get(key).get(0).get("CNV"));
            analyzedData.put("FractionalAbundance", groupedData.get(key).get(0).get("FractionalAbundance"));
            analyzedData.put("ConcentrationMutation", getAverage(groupedData.get(key), "ConcentrationMutation")); // Mu, Gene, Methyl, Human
            analyzedData.put("ConcentrationWildType", getAverage(groupedData.get(key), "ConcentrationWildType")); // WT, Ref, Unmethyl, Mouse
            analyzedData.put("Channel1PosChannel2Pos", getSum(groupedData.get(key), "Channel1PosChannel2Pos"));
            analyzedData.put("Channel1PosChannel2Neg", getSum(groupedData.get(key), "Channel1PosChannel2Neg"));
            analyzedData.put("Channel1NegChannel2Pos", getSum(groupedData.get(key), "Channel1NegChannel2Pos"));
            Integer dropletCountMutation = (Integer) analyzedData.get("Channel1PosChannel2Pos") + (Integer) analyzedData.get("Channel1PosChannel2Neg");
            Integer dropletCountWildType = (Integer) analyzedData.get("Channel1PosChannel2Pos") + (Integer) analyzedData.get("Channel1NegChannel2Pos");
            Double totalDnaDetected = calculateTotalDnaDetected((Double) analyzedData.get("ConcentrationMutation"), (Double) analyzedData.get("ConcentrationWildType"));
            analyzedData.put("DropletCountMutation", dropletCountMutation);
            analyzedData.put("DropletCountWildType", dropletCountWildType);
            analyzedData.put("TotalDnaDetected", totalDnaDetected);
            Double ratio = getRatio(Double.valueOf(analyzedData.get("ConcentrationMutation").toString()), Double.valueOf(analyzedData.get("ConcentrationWildType").toString()));
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
    private List<DataRecord> getQcReportRecords(DataRecord sample, String requestId) throws IoError, RemoteException, NotFound, ServerException {
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
    private void addResultsAsChildRecords(List<Map<String, Object>> analyzedDataValues, List<DataRecord> attachedSamples) throws RemoteException, ServerException {
        List<DataRecord> recordsToAttachToTask = new ArrayList<>();
        logInfo(Integer.toString(analyzedDataValues.size()));
        for (Map<String, Object> data : analyzedDataValues) {
            logInfo(data.toString());
            String analyzedDataSampleId = data.get("OtherSampleId").toString();
            for (DataRecord sample : attachedSamples) {
                try {
                    Object sampleId = sample.getValue("SampleId", user);
                    Object otherSampleId = sample.getValue("OtherSampleId", user);
                    if (analyzedDataSampleId.equals(otherSampleId) && data.get("SampleId") == null) {
                        data.put("SampleId", sampleId);
                        logInfo(data.toString());
                        DataRecord recordToAttach = sample.addChild(activeTask.getInputDataTypeName(), data, user);
                        recordsToAttachToTask.add(recordToAttach);
                    }
                } catch (RemoteException e) {
                    logError(String.format("RemoteException -> Error while setting child records on sample with recordid  '%d':\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
                } catch (NotFound notFound) {
                    logError(String.format("NotFound Exception -> Error while setting child records on sample with recordid  '%d':\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
                } catch (ServerException e) {
                    logError(String.format("ServerException -> Error while setting child records on sample with recordid  '%d':\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
                }
            }
        }
        try {
            //set Human Percentage on QcReportDna DataRecords when Saving DdPcrAssayResults and HumanPercentageValues are present
            mapHumanPercentageFromDdpcrResultsToDnaQcReport(recordsToAttachToTask);
            logInfo("mapHumanPercentageFromDdpcrResultsToDnaQcReport is called!");
        } catch (IoError io) {
            String errMsg = String.format("Remote Exception Error while mapping human percentage from DDPCR results to DNA QC Report:\n%s", ExceptionUtils.getStackTrace(io));
            logError(errMsg);

        } catch (InvalidValue iv) {
            String errMsg = String.format("Remote Exception Error while mapping human percentage from DDPCR results to DNA QC Report:\n%s", ExceptionUtils.getStackTrace(iv));
            logError(errMsg);
        } catch (NotFound nf) {
            String errMsg = String.format("Remote Exception Error while mapping human percentage from DDPCR results to DNA QC Report:\n%s", ExceptionUtils.getStackTrace(nf));
            logError(errMsg);
        }

        activeTask.addAttachedDataRecords(recordsToAttachToTask);
    }
}

