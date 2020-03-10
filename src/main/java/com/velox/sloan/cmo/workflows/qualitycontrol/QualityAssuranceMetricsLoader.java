package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

import java.rmi.RemoteException;

public class QualityAssuranceMetricsLoader extends DefaultGenericPlugin {

    private final String QA_METRICS_UPLOAD_TAG = "LOAD QA METRICS";

    public QualityAssuranceMetricsLoader() {
        setTaskEntry(true);
        setTaskToolbar(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(QA_METRICS_UPLOAD_TAG);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(QA_METRICS_UPLOAD_TAG) && activeTask.getStatus()==ActiveTask.COMPLETE;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public PluginResult run() throws ServerException {
        try {
            boolean loadQaMetrics = clientCallback.showYesNoDialog("Do you want to uplaod QA Measurements data?", "If you would like to load QA Measurements data please click YES, to cancel click NO");
            if (!loadQaMetrics){
                activeTask.setStatus(ActiveTask.COMPLETE);
                return new PluginResult(true);
            }
            String numOfQaMetricsToLoad = clientCallback.showInputDialog("How many rows would you like to add?");
            if (isValidInteger(numOfQaMetricsToLoad)){
            }
        }catch (Exception e){
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private boolean isValidInteger(String input) throws ServerException {
        try {
            Integer.parseInt(input);
            return true;
        }catch (Exception e){
            clientCallback.displayError(String.format("Please enter a valid number. %s is not a valid entry.", input));
            return false;
        }
    }

    private void getQaDataFromUser() throws ServerException, RemoteException {
        TemporaryDataType temporaryDataType = new TemporaryDataType("QualityAssuranceMeasure", "QA Metrics");
        clientCallback.displayInfo(temporaryDataType.getDataTypeLayout().getDescription());
    //dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions("QA").getDataFieldDefinitionList().
    }


}
