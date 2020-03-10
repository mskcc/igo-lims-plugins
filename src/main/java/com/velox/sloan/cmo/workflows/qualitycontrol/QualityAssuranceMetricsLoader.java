package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datafielddefinition.DataFieldDefinition;
import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.datatype.fielddefinition.VeloxFieldDefinition;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

import java.rmi.RemoteException;
import java.util.*;

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
                clientCallback.displayInfo("running load temp info");
                List<Map<String,Object>> dataFieldValues = getQaDataFromUser(Integer.parseInt(numOfQaMetricsToLoad));
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

    private List<Map<String, Object>> getQaDataFromUser(Integer numToAdd) throws ServerException, RemoteException {
        List<VeloxFieldDefinition<?>> temporaryDataTypeFieldDefinitions = dataMgmtServer.getDataTypeManager(user).getDataTypeDefinition("QualityAssuranceMeasure").getTemporaryDataType(user).getVeloxFieldDefinitionList();
        TemporaryDataType dataType = new TemporaryDataType("QualityAssuranceMeasure", "QualityAssurance Measures");
        dataType.setVeloxFieldDefinitionList(temporaryDataTypeFieldDefinitions);
        List<DataFieldDefinition> dataFieldDefinitions = dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions("QualityAssuranceMeasure").getDataFieldDefinitionList();
        List<String> columnNamesToSkip = Arrays.asList("DataRecordName","RecordId","DateCreated","CreatedBy","DateModified","RelatedRecord3","RelatedRecord151");
        List<Map<String, Object>> initialValues = new ArrayList<>();
        for(int i=0; i<numToAdd; i++){
            Map<String, Object> values = new HashMap<>();
            values.put("QAValidationType", "Hello");
//             for(DataFieldDefinition dfd : dataFieldDefinitions){
//                 String colName = dfd.dataFieldName;
//                 if (!columnNamesToSkip.contains(colName)){
//                     clientCallback.displayInfo(colName);
//                     values.put(colName, null);
//                 }
//             }
            initialValues.add(values);
            initialValues.add(values);
        }
        List<Map<String, Object>> userInput = clientCallback.showTableEntryDialog("QualityAssuranceMeasure", "Add Quality Assurance measurements in table below", dataType, initialValues);
        return null;//clientCallback.showTableEntryDialog("QualityAssuranceMeasure", "Add Quality Assurance measurements in table below", dataType, null);
    }
}