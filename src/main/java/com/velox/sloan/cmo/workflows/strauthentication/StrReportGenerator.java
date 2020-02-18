package com.velox.sloan.cmo.workflows.strauthentication;

import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrReportGenerator extends DefaultGenericPlugin {

    private final  String STR_REPORT_GENERATOR_TAG = "GENERATE STR REPORT";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public StrReportGenerator(){
        setTaskEntry(true);
        setTaskToolbar(true);
        setOrder(PluginOrder.EARLY.getOrder());
        setLine1Text("Create STR Report");
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(STR_REPORT_GENERATOR_TAG);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(STR_REPORT_GENERATOR_TAG) &&
                    activeTask.getStatus() == ActiveTask.COMPLETE;
        }catch (Throwable e){
            logInfo(Arrays.toString(e.getStackTrace()));
            return false;
        }
    }

    @Override
    public PluginResult run() throws ServerException {
        try {
            //get file from user via upload dialog
            String uploadedFile = clientCallback.showFileDialog("Please upload Raw data files", null);
            if (!StringUtils.isAllBlank(uploadedFile)){
                logInfo("User did not upload the file.");
                return new PluginResult(false);
            }

            //validate the uploaded file
            if (!isValidFile(uploadedFile)){
                return new PluginResult(false);
            }
            //read data from uploaded file
            List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(uploadedFile));
            Map<String, Integer> headerValueMap = utils.getCsvHeaderValueMap(fileData);

        }catch (Exception e){
            logInfo(Arrays.toString(e.getStackTrace()));
            clientCallback.displayError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }

        return new PluginResult(true);
}

    private boolean isValidFile(String uploadedFile) throws ServerException, IOException {
        if(!utils.isCsvFile(uploadedFile)){
            clientCallback.displayError(String.format("Not a valid csv file\n%s", uploadedFile));
            return false;
        }
        List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(uploadedFile));
        if (!utils.csvFileHasData(fileData)){
            clientCallback.displayError(String.format("The uploaded file does not contain data\n%s", uploadedFile));
            return false;
        }
        return true;
    }

    private Map<String, Map<String, String>> aggregateDataBySample(List<String> fileData, Map<String, Integer> headerValueMap){
        Map<String, Map<String, String>> sampleData = new HashMap<>();
        for (int i = 1; i < fileData.size(); i++){
            List<String> rowData = Arrays.asList(fileData.get(i).split(","));
            Map<String, String> data = new HashMap<>();
            String sampleName = rowData.get(headerValueMap.get("Sample Name")).trim();
            String Marker = rowData.get(headerValueMap.get("Marker")).trim();

            //Allele 1	 Allele 2	 Allele 3	 Allele 4

        }
        return sampleData;
    }


    }
