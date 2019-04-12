package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;

public class DigitalPcrResultsParser extends DefaultGenericPlugin{

    IgoLimsPluginUtils igoUtils = new IgoLimsPluginUtils();
    private List<String> expectedRawResultsHeaders = Arrays.asList("Well", "ExptType", "Experiment", "Sample", "TargetType", "Target",
            "Status", "Concentration","Supermix", "CopiesPer20uLWell", "TotalConfMax", "TotalConfMin", "PoissonConfMax", "PoissonConfMin",
            "Positives", "Negatives", "Ch1+Ch2+", "Ch1+Ch2-", "Ch1-Ch2+", "Ch1-Ch2-", "Linkage", "AcceptedDroplets");

    public DigitalPcrResultsParser(){
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("PARSE DDPCR RESULTS")
                && !activeTask.getTask().getTaskOptions().containsKey("_DDPCR RESULTS PARSED");
    }

    public PluginResult run() throws ServerException {
        try {
            String fileWithDigitalPcrRawData = clientCallback.showFileDialog("Please upload Raw Data file", null);
            if (StringUtils.isEmpty(fileWithDigitalPcrRawData)) {
                return new PluginResult(false);
            }
            List<String> fileData = igoUtils.readDataFromCsvFile(clientCallback.readBytes(fileWithDigitalPcrRawData));
            if (!isValidFile(fileWithDigitalPcrRawData, fileData)) {
                return new PluginResult(false);
            }
            Map<String, Integer> headerValueMap = igoUtils.getCsvHeaderValueMap(fileData);
            List<List<String>> channel1Data = getChannel1Data(fileData,headerValueMap);
            List<List<String>> channel2Data = getChannel2Data(fileData,headerValueMap);
            List<Map<String, Object>> channel1Channe2CombinedData = flattenChannel1AndChannel2Data(channel1Data, channel2Data, headerValueMap);
            Map<String,List<Map<String,Object>>> groupedData = groupResultsBySampleAndAssay(channel1Channe2CombinedData);
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords("DdPcrProtocol1", user);
            if(attachedProtocolRecords.isEmpty()){
                clientCallback.displayError("No attached 'DdPcrProtocol1' records found attached to this task.");
                logError("No attached 'DdPcrProtocol1' records found attached to this task.");
                return new PluginResult(false);
            }
            List<Map<String, Object>> analyzedData = runDataAnalysisForAssays(groupedData, attachedProtocolRecords);
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            if(attachedProtocolRecords.isEmpty()){
                clientCallback.displayError("No attached 'Sample' records found attached to this task.");
                logError("No attached 'Sample' records found attached to this task.");
                return new PluginResult(false);
            }
            addResultsAsChildRecords(analyzedData, attachedSampleRecords);
        }catch (Exception e){
            clientCallback.displayError(String.format("Error while parsing the ddPCR Results file:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
        }
        return new PluginResult(true);
    }

    private boolean isValidFile(String fileName, List<String> fileData) throws ServerException {
        if (!igoUtils.isCsvFile(fileName)){
            clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            logError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            return false;
        }
        if(!igoUtils.csvFileContainsNeededHeaderValues(fileData, expectedRawResultsHeaders)){
            clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            return false;
        }
        if(!igoUtils.csvFileHasData(fileData)){
            clientCallback.displayError(String.format("Uploaded file '%s' has does not contain data. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has does not contain data. Please check the file", fileName));
            return false;
        }
        return true;
    }

    private List<List<String>> getChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap){
        List<List<String>> channel1RawData = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (valuesInRow.get(headerValueMap.get("TargetType")).contains("Ch1")) {
                channel1RawData.add(valuesInRow);
            }
        }
        return channel1RawData;
    }

    private List<List<String>> getChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap){
        List<List<String>> channel2RawData = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (valuesInRow.get(headerValueMap.get("TargetType")).contains("Ch2")) {
                channel2RawData.add(valuesInRow);
            }
        }
        return channel2RawData;
    }

    private List<Map<String, Object>> flattenChannel1AndChannel2Data(List<List<String>>channel1Data, List<List<String>>channel2Data, Map<String, Integer> headerValueMap){
        List<Map<String,Object>> flatData = new ArrayList<>();
        for (List<String> s1 : channel1Data){
            String s1Well = s1.get(headerValueMap.get("Well"));
            String s1SampleId = s1.get(headerValueMap.get("Sample"));
            for (List<String> s2 : channel2Data ){
                String s2Well = s2.get(headerValueMap.get("Well"));
                String s2SampleId = s2.get(headerValueMap.get("Sample"));
                if (s2Well.equalsIgnoreCase(s1Well) && s2SampleId.equalsIgnoreCase(s1SampleId)) {
                    Map<String, Object> sampleValues = new HashMap<>();
                    sampleValues.put("Well", s1.get(headerValueMap.get("Well")));
                    sampleValues.put("Sample", s1.get(headerValueMap.get("Sample")));
                    sampleValues.put("Target", s1.get(headerValueMap.get("Target")));
                    sampleValues.put("ConcentrationMutation", Double.parseDouble(s1.get(headerValueMap.get("Concentration"))));
                    sampleValues.put("ConcentrationWildType", Double.parseDouble(s2.get(headerValueMap.get("Concentration"))));
                    sampleValues.put("Channel1PosChannel2Pos", Integer.parseInt(s1.get(headerValueMap.get("Ch1+Ch2+"))));
                    sampleValues.put("Channel1PosChannel2Neg", Integer.parseInt(s1.get(headerValueMap.get("Ch1+Ch2-"))));
                    sampleValues.put("Channel1NegChannel2Pos", Integer.parseInt(s1.get(headerValueMap.get("Ch1-Ch2+"))));
                    sampleValues.put("AcceptedDroplets", Integer.parseInt(s1.get(headerValueMap.get("AcceptedDroplets"))));
                    flatData.add(sampleValues);
                }

            }
        }
        return flatData;
    }

    private Map<String,List<Map<String,Object>>> groupResultsBySampleAndAssay(List<Map<String, Object>> flatData){
        Map<String,List<Map<String,Object>>> groupedData = new HashMap<>();
        for (Map<String, Object> data : flatData){
            String keyValue = data.get("Sample").toString() + "/" + data.get("Target").toString();
            groupedData.putIfAbsent(keyValue, new ArrayList<>());
            groupedData.get(keyValue).add(data);
        }
        return groupedData;
    }

    private Double getAverage(List<Map<String,Object>> sampleData, String fieldName){
        Double sum = 0.0;
        for (Map<String, Object> data : sampleData){
            sum += Double.parseDouble(data.get(fieldName).toString());
        }
        return sum/sampleData.size();
    }

    private Integer getSum(List<Map<String,Object>> sampleData, String fieldName){
        int sum = 0;
        for (Map<String, Object> data : sampleData){
            sum += Integer.parseInt(data.get(fieldName).toString());
        }
        return sum/sampleData.size();
    }

    private Double getTotalInputForSample(String sampleName, String assayName, List<DataRecord> protocolRecords) throws NotFound, RemoteException {
        for(DataRecord record : protocolRecords){
            Object sampleNameOnProtocol = record.getValue("OtherSampleId", user);
            Object assayOnProtocol = record.getValue("Ch1Target", user);
            Object igoSampleIdOnProtocol = record.getStringVal("SampleId", user);
            logInfo("IgoId: " + igoSampleIdOnProtocol.toString() + " ----> SampleName : " + sampleNameOnProtocol);
            if(sampleNameOnProtocol != null && assayOnProtocol!=null && sampleName.equalsIgnoreCase(sampleNameOnProtocol.toString())
            && assayName.equalsIgnoreCase(assayOnProtocol.toString())){
                Object totalInput = record.getValue("Aliq1TargetMass", user);
                if (totalInput!=null){
                    logInfo(totalInput.toString());
                    return (Double)totalInput;
                }
            }
            else if(sampleNameOnProtocol != null && assayOnProtocol!=null && sampleName.equalsIgnoreCase(igoSampleIdOnProtocol.toString())
                    && assayName.equalsIgnoreCase(assayOnProtocol.toString())){
                Object totalInput = record.getValue("Aliq1TargetMass", user);
                if (totalInput!=null){
                    return (Double)totalInput;
                }
            }
        }
        return 0.0;
    }

    private Double getRatio(Double dropletCountMutation, Double dropletCountWildType){
        if (dropletCountWildType<=0){
            dropletCountWildType=1.0;
        }
        return dropletCountMutation/dropletCountWildType;
    }

    private Double calculateTotalDnaDetected(Double concentrationMutation, Double ConcentrationWildType){
        if (ConcentrationWildType<=0){
            ConcentrationWildType=1.0;
        }
        return (concentrationMutation/ConcentrationWildType) * 0.066;
    }

    private Double calculateHumanPercentage(Integer dropletCountMutation, Integer dropletCountWildType){
        if(dropletCountWildType<=0){
            dropletCountWildType = 1;
        }
        return (dropletCountMutation/(dropletCountMutation+dropletCountWildType))*100.0;
    }

    private List<Map<String, Object>> runDataAnalysisForAssays(Map<String,List<Map<String,Object>>> groupedData, List<DataRecord>protocolRecords) throws NotFound, RemoteException {
        List<Map<String, Object>> analyzedDataValues = new ArrayList<>();
        for (String key : groupedData.keySet()){
            Map<String, Object> analyzedData = new HashMap<>();
            String sampleName = key.split("/")[0];
            String target = key.split("/")[1];
            analyzedData.put("Assay", target);
            analyzedData.put("OtherSampleId", sampleName);
            analyzedData.put("ConcentrationMutation", getAverage(groupedData.get(key),"ConcentrationMutation"));
            analyzedData.put("ConcentrationWildType", getAverage(groupedData.get(key),"ConcentrationWildType"));
            analyzedData.put("Channel1PosChannel2Pos", getSum(groupedData.get(key),"Channel1PosChannel2Pos"));
            analyzedData.put("Channel1PosChannel2Neg", getSum(groupedData.get(key), "Channel1PosChannel2Neg"));
            analyzedData.put("Channel1NegChannel2Pos", getSum(groupedData.get(key), "Channel1NegChannel2Pos"));
            Integer dropletCountMutation = (Integer) analyzedData.get("Channel1PosChannel2Pos") + (Integer) analyzedData.get("Channel1PosChannel2Neg");
            Integer dropletCountWildType = (Integer) analyzedData.get("Channel1PosChannel2Pos") + (Integer) analyzedData.get("Channel1NegChannel2Pos");
            Double totalDnaDetected = calculateTotalDnaDetected((Double)analyzedData.get("ConcentrationMutation"), (Double)analyzedData.get("ConcentrationWildType"));
            analyzedData.put("DropletCountMutation", dropletCountMutation);
            analyzedData.put("DropletCountWildType", dropletCountWildType);
            analyzedData.put("TotalDnaDetected", totalDnaDetected);
            Double ratio = getRatio(Double.valueOf(analyzedData.get("DropletCountMutation").toString()),Double.valueOf(analyzedData.get("DropletCountWildType").toString()));
            analyzedData.put("Ratio", ratio);
            analyzedData.put("AcceptedDroplets", getSum(groupedData.get(key), "AcceptedDroplets"));
            Double humanPercentage = calculateHumanPercentage(dropletCountMutation, dropletCountWildType);
            analyzedData.put("HumanPercentage", humanPercentage);
            analyzedData.put("TotalInput", getTotalInputForSample(sampleName, target, protocolRecords));
            analyzedDataValues.add(analyzedData);
        }
        return analyzedDataValues;
    }

    private void addResultsAsChildRecords( List<Map<String, Object>> analyzedDataValues, List<DataRecord> attachedSamples) throws NotFound, RemoteException, ServerException, IoError {
        List<Object> alreadyAdded = new ArrayList<>();
        List<DataRecord> recordsToAttachToTask = new ArrayList<>();
        for (DataRecord record : attachedSamples){
            Object sampleId = record.getValue("SampleId", user);
            Object otherSampleId = record.getValue("OtherSampleId", user);
            for(Map<String, Object> data: analyzedDataValues){
                String analyzedDataSampleId = data.get("OtherSampleId").toString();
                if((otherSampleId != null && !alreadyAdded.contains(otherSampleId) && otherSampleId.toString().equalsIgnoreCase(analyzedDataSampleId))
                        || sampleId.toString().equalsIgnoreCase(analyzedDataSampleId)){
                    if(sampleId!=null){
                        data.put("SampleId", sampleId);
                    }
                    recordsToAttachToTask.add(record.addChild("DdPcrAssayResults", data, user));
                    alreadyAdded.add(otherSampleId);
                }
            }
        }
        activeTask.addAttachedDataRecords(recordsToAttachToTask);
        activeTask.getTask().getTaskOptions().put("_DDPCR RESULTS PARSED","");
    }

}
