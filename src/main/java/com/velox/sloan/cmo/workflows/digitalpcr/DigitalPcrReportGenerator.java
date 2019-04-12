package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

public class DigitalPcrReportGenerator extends DefaultGenericPlugin {
    public DigitalPcrReportGenerator(){
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("GENERATE DDPCR REPORT")
                && !activeTask.getTask().getTaskOptions().containsKey("_DDPCR REPORT GENERATED");
    }

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> ddPcrResults = activeTask.getAttachedDataRecords("DdPcrAssayResults", user);
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            if(ddPcrResults.isEmpty()){
                clientCallback.displayError("No 'ddPcrResults' records found attached to this task.");
                logError("No attached 'ddPcrResults' records found attached to this task.");
                return new PluginResult(false);
            }
            if(attachedSamples.isEmpty()){
                clientCallback.displayError("No 'Sample' records found attached to this task.");
                logError("No attached 'ddPcrResults' records found attached to this task.");
                return new PluginResult(false);
            }

        }catch (Exception e){
            clientCallback.displayError(String.format(":( Error while generating DDPCR Report:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
        }
        return new PluginResult(true);
    }

    private String getMicronicTubeIdfromParentSample(DataRecord ddPcrReportRecord){

        return "";
    }

    public void setFieldsForReport(List<DataRecord> dd){

    }
}