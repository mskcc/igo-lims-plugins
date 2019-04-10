package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.IntStream;

public class DigitalPcrResultsParser extends DefaultGenericPlugin{

    IgoLimsPluginUtils igoUtils = new IgoLimsPluginUtils();
    private List<String> expectedRawResultsHeaders = Arrays.asList("Well", "Sample", "Ch1 Target", "Ch1 Target Type", "Ch2 Target", "Ch2 Target Type",
            "Experiment", "Expt Type", "Expt FG Color",	"Expt BG Color", "ReferenceCopyNumber", "TargetCopyNumber", "ReferenceAssayNumber",
            "TargetAssayNumber", "ReactionVolume", "DilutionFactor", "Supermix", "Cartridge", "Expt Comments");

    public DigitalPcrResultsParser(){
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("PARSE DDPCR RESULTS");
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
            Map<String, List<String>> dataBySampleIdAndAssay = groupDataBySampleId(fileData);
            logInfo("Map Data: \n" + dataBySampleIdAndAssay.toString());

        }catch (Exception e){
            clientCallback.displayError(String.format("Error while creating user pools. CAUSE:\n%s", e));
            logError(e.getStackTrace().toString());
        }
        return new PluginResult(true);


    }

    private boolean isValidFile(String fileName, List<String> fileData) throws ServerException {
        if (!igoUtils.isCsvFile(fileName)){
            clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            logError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            return false;
        }
        if(!igoUtils.csvFileHasValidHeader(fileData, expectedRawResultsHeaders)){
            clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            return false;
        }
        if(!igoUtils.csvFileHasData(fileData)){
            clientCallback.displayError(String.format("Uploaded file '%s' has does not contain data. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has does not contain data. Please check the file", fileName));
            return false;
        }
        if(!igoUtils.allRowsInCsvFileHasValidData(fileData, expectedRawResultsHeaders)){
            clientCallback.displayError(String.format("Uploaded file '%s' has missing row data. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has missing row data. Please check the file", fileName));
            return false;
        }
        return true;
    }

    private Map<String, List<String>> groupDataBySampleId(List<String> fileData){
        Map<String, Integer> headerValueMap = igoUtils.getCsvHeaderValueMap(fileData);
        Map<String,List<String>> groupedResults = new HashMap<>();
        IntStream.range(1,fileData.size()-1).forEach(i -> {
            List<String> newList = new ArrayList<>();
            List<String> valuesInRow = Arrays.asList(fileData.get(i).split(","));
            String keyValue = valuesInRow.get(headerValueMap.get("Sample")) + "/" + valuesInRow.get(headerValueMap.get("Ch1 Target"));
            logInfo(keyValue);
            groupedResults.putIfAbsent(keyValue, newList);
            groupedResults.get(keyValue).add(fileData.get(i));
            logInfo("");
        });
        return groupedResults;
    }
}
