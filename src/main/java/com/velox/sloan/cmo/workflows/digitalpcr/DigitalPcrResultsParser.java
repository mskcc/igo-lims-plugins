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

import javax.swing.plaf.SplitPaneUI;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This plugin is designed to import ddPCR results into LIMS. The Raw data from csv file is parsed,
 * and plugin will calculate the final results and store in "DdPcrAssayResults" DataType as child to sample.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class DigitalPcrResultsParser extends DefaultGenericPlugin {
    private final static String IGO_ID_WITHOUT_ALPHABETS_PATTERN = "^[0-9]+_[0-9]+.*$";  // sample id without alphabets
    private final static String IGO_ID_WITH_ALPHABETS_PATTERN = "^[0-9]+_[A-Z]+_[0-9]+.*$";  // sample id without alphabets

    public int qxResultType = 0;
    String numOfChannels = "";
    String reference = "";

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
private final List<String> expectedQx600RawResultsHeaders = Arrays.asList("Well", "Sample description 1", "Sample description 2",
        "Status","Experiment", "SampleType","TargetType","Supermix","DyeName(s)", "TotalConfMax","TotalConfMin","PoissonConfMax",
        "PoissonConfMin","Positives","Negatives","CNV","TotalCNVMax", "TotalCNVMin","PoissonCNVMax","PoissonCNVMin","ReferenceCopies",
        "UnknownCopies","Threshold1","Threshold2","Threshold3", "ThresholdSigmaAbove","ThresholdSigmaBelow","ReferenceUsed",
        "Ratio","TotalRatioMax","TotalRatioMin","PoissonRatioMax", "PoissonRatioMin", "Fractional Abundance", "TotalFractionalAbundanceMax",
        "TotalFractionalAbundanceMin", "PoissonFractionalAbundanceMax", "PoissonFractionalAbundanceMin","MeanAmplitudeOfPositives",
        "MeanAmplitudeOfNegatives", "MeanAmplitudeTotal", "ExperimentComments","MergedWells","TotalConfidenceMax68",
        "TotalConfidenceMin68", "PoissonConfidenceMax68", "PoissonConfidenceMin68","TotalCNVMax68","TotalCNVMin68",
        "PoissonCNVMax68","PoissonCNVMin68", "TotalRatioMax68", "TotalRatioMin68","PoissonRatioMax68","PoissonRatioMin68",
        "TotalFractionalAbundanceMax68", "TotalFractionalAbundanceMin68", "PoissonFractionalAbundanceMax68",
        "PoissonFractionalAbundanceMin68", "Ch1+Ch2+","Ch1+Ch2-","Ch1-Ch2+", "Ch1-Ch2-","Ch3+Ch4+","Ch3+Ch4-",
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
                Map<String, Integer> headerValueMap = igoUtils.getCsvHeaderValueMap(fileData, pluginLogger);
                List<Map<String, Object>> combinedChannelsData = new ArrayList<>();
                if (isQX200) {
                    List<List<String>> channel1Data = getChannel1Data(fileData, headerValueMap, isQX200);
                    List<List<String>> channel2Data = getChannel2Data(fileData, headerValueMap, isQX200);
                    combinedChannelsData = flattenChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap, isQX200, pluginLogger);
                    logInfo("Flattened QX200 data");
                }
                else { // QX600
                    numOfChannels = clientCallback.showInputDialog("How many channels used for this QX600 experiment?");
                    reference = clientCallback.showInputDialog("Please enter the exact reference name:");
                    List<List<String>> refChannelsData = getRefChannelsData(fileData, headerValueMap, numOfChannels, reference);
                    List<List<String>> targetChannelsData = getTargetChannelsData(fileData, headerValueMap, numOfChannels, reference);
                    combinedChannelsData = flattenRefTargetChannels(targetChannelsData, refChannelsData, headerValueMap, numOfChannels, pluginLogger);
                    logInfo("Flattened QX600 data");
                }

                Map<String, List<Map<String, Object>>> groupedData = groupResultsBySampleAndAssay(combinedChannelsData, isQX200);
                logInfo(groupedData.toString());
                logInfo("grouped data size = " + groupedData.size());
                List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords("DdPcrProtocol1SixChannels", user);
                List<DataRecord> attachedProtocolRecordsQX200 = activeTask.getAttachedDataRecords("DdPcrProtocol1", user);
                if (attachedProtocolRecords.isEmpty() && attachedProtocolRecordsQX200.isEmpty()) {
                    clientCallback.displayError("No attached 'DdPcrProtocol1SixChannels' or 'DdPcrProtocol1' records found attached to this task.");
                    logError("No attached 'DdPcrProtocol1SixChannels'/'DdPcrProtocol1' records found attached to this task.");
                    return new PluginResult(false);
                }
                List<Map<String, Object>> analyzedData = new LinkedList<>();
                if (!attachedProtocolRecords.isEmpty()) {
                    analyzedData = runDataAnalysisForAssays(groupedData, attachedProtocolRecords, isQX200);
                }
                else {
                    analyzedData = runDataAnalysisForAssays(groupedData, attachedProtocolRecordsQX200, isQX200);
                }
                List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
                if (attachedSampleRecords.isEmpty()) {
                    clientCallback.displayError("No attached 'Sample' records found attached to this task.");
                    logError("No attached 'Sample' records found attached to this task.");
                    return new PluginResult(false);
                }
                addResultsAsChildRecords(analyzedData, attachedSampleRecords, isQX200);
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
    private List<List<String>> getChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap, boolean isQX200) {
        return resultsProcessor.readChannel1Data(fileData, headerValueMap, isQX200);
    }

    /**
     * @param fileData
     * @param headerValueMap
     * @return data related to channel2 in the raw data under "TargetType" column in ddPCR results.
     */
    private List<List<String>> getChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap, boolean isQX200) {
        return resultsProcessor.readChannel2Data(fileData, headerValueMap, isQX200);
    }
    private List<List<String>> getRefChannelsData(List<String> fileData, Map<String, Integer> headerValueMap, String numOfChannels, String ref) {
        return resultsProcessor.readRefChannelsData(fileData, headerValueMap, numOfChannels, ref);
    }
    private List<List<String>> getTargetChannelsData(List<String> fileData, Map<String, Integer> headerValueMap, String numOfChannels, String ref) {
        return resultsProcessor.readTargetChannelsData(fileData, headerValueMap, numOfChannels, ref);
    }

    /**
     * Add Channel2 data to the rows containing Channel1 data as Concentration of the Reference.
     *
     * @param channel1Data
     * @param channel2Data
     * @param headerValueMap
     * @return Channel1 and Channel2 combined data.
     */
    private List<Map<String, Object>> flattenChannel1AndChannel2Data(List<List<String>> channel1Data, List<List<String>> channel2Data, Map<String, Integer> headerValueMap, boolean isQX200, PluginLogger logger) {
        return resultsProcessor.concatenateChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap, isQX200, logger);
    }

    private List<Map<String, Object>> flattenRefTargetChannels(List<List<String>> targetChannels, List<List<String>> refChannels, Map<String, Integer> headerValueMap, String numOfChannels, PluginLogger logger) {
        return resultsProcessor.concatenateRefTargetChannels(targetChannels, refChannels, headerValueMap, numOfChannels, logger);
    }
    /**
     * Group the data based on Sample and Target values in the results.
     *
     * @param flatData
     * @return data grouped by Sample and Target values.
     */
    private Map<String, List<Map<String, Object>>> groupResultsBySampleAndAssay(List<Map<String, Object>> flatData, boolean QX200) {
        return resultsProcessor.aggregateResultsBySampleAndAssay(flatData, QX200);
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
    private Double getSum(List<Map<String, Object>> sampleData, String fieldName) {
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
    private Double getTotalInputForSample(String sampleName, String assayName, List<DataRecord> protocolRecords, boolean QX200) {
        for (DataRecord record : protocolRecords) {
            try {
                Object sampleNameOnProtocol = record.getValue("OtherSampleId", user);
                Object assayOnProtocol = null;
                if (QX200) {
                    assayOnProtocol = record.getValue("Ch1Target", user);
                }
                else { //QX600
                    assayOnProtocol = record.getValue("TargetName", user);
                }

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
    private Double calculateHumanPercentage(Double dropletCountMutation, Double dropletCountWildType) {
        return resultsProcessor.calculateHumanPercentage(dropletCountMutation, dropletCountWildType);
    }

    public Comparator<Map<Object, Object>> mapComparator = new Comparator<Map<Object, Object>>() {
        public int compare(Map<Object, Object> m1, Map<Object, Object> m2) {
            return m1.get("sample").toString().compareTo(m2.get("sample").toString());
        }
    };

    /**
     * Calculate final result values from the raw data.
     *
     * @param groupedData
     * @param protocolRecords
     * @return final ddPCR results. One row per sample.
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Map<String, Object>> runDataAnalysisForAssays(Map<String, List<Map<String, Object>>> groupedData, List<DataRecord> protocolRecords, boolean QX200){
        logInfo("Analyzing ddPCR Results.");
        List<Map<String, Object>> analyzedDataValues = new ArrayList<>();
        // from protocol records read igo id
        List<String> igoIds = new LinkedList<>();
        try {
            for (DataRecord ddpcrprtcl1 : protocolRecords) {
                igoIds.add(getBaseSampleId(ddpcrprtcl1.getStringVal("SampleId", user)));
            }
            String commaSeparatedIgoIds = String.join(", ", igoIds);
            for (String key : groupedData.keySet()) {
                Map<String, Object> analyzedData = new HashMap<>();
                String sampleName = key.split("/")[0];
                String target = key.split("/")[1];
                if (target.contains("Gene:")) {
                    target = target.split("Gene:")[1];
                }
                else if (target.contains("Ref:")) {
                    logInfo("Skipping target = " + target);
                    continue;
                }
                String whereClause = "OtherSampleId = '" + sampleName + "'";
                logInfo("whereClause: " + whereClause);
                int reactionCount = 1;
                List<DataRecord> ddpcrprtcl2Recs = dataRecordManager.queryDataRecords("DdPcrProtocol2", whereClause, user);
                if (ddpcrprtcl2Recs.size() > 0) {
                    for (DataRecord prtcl2Rec : ddpcrprtcl2Recs) {
                        for (String igoId : igoIds) {
                            if (prtcl2Rec.getStringVal("SampleId", user).equals(igoId)) {
                                reactionCount = prtcl2Rec.getIntegerVal("NumberOfReplicates", user);
                            }
                        }
                    }
                }
                logInfo("reactionCount = " + reactionCount);
                analyzedData.put("Assay", target);
                //if (QX200) {
                    analyzedData.put("OtherSampleId", sampleName);
                //}
//                else {
//                    analyzedData.put("SampleId", sampleName);
//                }
                analyzedData.put("CNV", getAverage(groupedData.get(key), "CNV"));
                analyzedData.put("FractionalAbundance", (Double) getAverage(groupedData.get(key), "FractionalAbundance") * 100.00);
                analyzedData.put("ConcentrationMutation", getSum(groupedData.get(key), "ConcentrationMutation") * 20); //Copies Gene
                analyzedData.put("ConcentrationWildType", getSum(groupedData.get(key), "ConcentrationWildType") * 20); // Copies Ref
                logInfo("Grouped data key is: " + key);
                analyzedData.put("Channel1PosChannel2Pos", getSum(groupedData.get(key), "Channel1PosChannel2Pos"));
                analyzedData.put("Channel1PosChannel2Neg", getSum(groupedData.get(key), "Channel1PosChannel2Neg"));
                analyzedData.put("Channel1NegChannel2Pos", getSum(groupedData.get(key), "Channel1NegChannel2Pos"));
                Double dropletCountMutation = (Double) analyzedData.get("Channel1PosChannel2Pos") + (Double) analyzedData.get("Channel1PosChannel2Neg");
                Double dropletCountWildType = (Double) analyzedData.get("Channel1PosChannel2Pos") + (Double) analyzedData.get("Channel1NegChannel2Pos");
                Double totalDnaDetected = calculateTotalDnaDetected(getAverage(groupedData.get(key), "ConcentrationMutation"), getAverage(groupedData.get(key),"ConcentrationWildType"));
                analyzedData.put("DropletCountMutation", dropletCountMutation);
                analyzedData.put("DropletCountWildType", dropletCountWildType);
                analyzedData.put("TotalDnaDetected", totalDnaDetected);
                Double ratio = getRatio(Double.valueOf(analyzedData.get("ConcentrationMutation").toString()), Double.valueOf(analyzedData.get("ConcentrationWildType").toString()));
                analyzedData.put("Ratio", ratio);
                analyzedData.put("AcceptedDroplets", getSum(groupedData.get(key), "AcceptedDroplets"));
                if (target.equalsIgnoreCase(HUMAN_MOUSE_PERCENTAGE_ASSAY_NAME) ||  target.contains("Human_PTGER2") || target.contains("Mouse_PTGER2") || target.contains("PTGER2_Mouse") || target.contains("PTGER2_Human")) {
                    Double humanPercentage = calculateHumanPercentage((Double) analyzedData.get("ConcentrationMutation"), (Double) analyzedData.get("ConcentrationWildType"));
                    analyzedData.put("HumanPercentage", humanPercentage);
                }
                analyzedData.put("TotalInput", getTotalInputForSample(sampleName, target, protocolRecords, QX200));
                analyzedDataValues.add(analyzedData);
            }
        } catch (NotFound | RemoteException | IoError | ServerException e) {
            throw new RuntimeException(e);
        }
        logInfo("analyzedDataValues size = " + analyzedDataValues.size());
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
    private void addResultsAsChildRecords(List<Map<String, Object>> analyzedDataValues, List<DataRecord> attachedSamples, boolean QX200) throws RemoteException, ServerException {
        List<DataRecord> recordsToAttachToTask = new ArrayList<>();
        logInfo(Integer.toString(analyzedDataValues.size()));
        logInfo("QX200 = " + QX200);
        for (Map<String, Object> data : analyzedDataValues) {
            logInfo(data.toString());

            String analyzedDataSampleId = "";
            //if (QX200) {
                analyzedDataSampleId = data.get("OtherSampleId").toString();
//            }
//            else {
//                analyzedDataSampleId = data.get("SampleId").toString();
//            }
            for (DataRecord sample : attachedSamples) {
                try {
                    Object sampleId = sample.getValue("SampleId", user);
                    Object otherSampleId = sample.getValue("OtherSampleId", user);
                    logInfo("analyzedDataSampleId = " + analyzedDataSampleId + " and sampleID = " + otherSampleId);
                    if (analyzedDataSampleId.equals(otherSampleId) && data.get("SampleId") == null) {
                        data.put("SampleId", sampleId);
                        logInfo("Added sampleId value and attach the record " + data.toString());
                        DataRecord recordToAttach = sample.addChild(activeTask.getInputDataTypeName(), data, user);
                        recordsToAttachToTask.add(recordToAttach);
                    }

//                    else if (!QX200 && analyzedDataSampleId.equals(sampleId) && data.get("SampleId") == null) {
//                        data.put("SampleId", sampleId);
//                        logInfo("Adding QX600 results as samples' child records; " + data.toString());
//                        DataRecord recordToAttach = sample.addChild(activeTask.getInputDataTypeName(), data, user);
//                        recordsToAttachToTask.add(recordToAttach);
//                    }
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

    /**
     * Method to get base Sample ID when aliquot annotation is present.
     * Example: for sample id 012345_1_1_2, base sample id is 012345_1
     * Example2: for sample id 012345_B_1_1_2, base sample id is 012345_B_1
     * @param sampleId
     * @return
     */
    public static String getBaseSampleId(String sampleId){
        Pattern alphabetPattern = Pattern.compile(IGO_ID_WITH_ALPHABETS_PATTERN);
        Pattern withoutAlphabetPattern = Pattern.compile(IGO_ID_WITHOUT_ALPHABETS_PATTERN);
        if (alphabetPattern.matcher(sampleId).matches()){
            String[] sampleIdValues =  sampleId.split("_");
            return String.join("_", Arrays.copyOfRange(sampleIdValues,0,4));
        }
        if(withoutAlphabetPattern.matcher(sampleId).matches()){
            String[] sampleIdValues =  sampleId.split("_");
            return String.join("_", Arrays.copyOfRange(sampleIdValues,0,3));
        }
        return sampleId;
    }
}

