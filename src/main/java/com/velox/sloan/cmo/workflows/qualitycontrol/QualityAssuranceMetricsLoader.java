package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.datatype.datatypelayout.DataFormComponent;
import com.velox.api.datatype.datatypelayout.DataTypeLayout;
import com.velox.api.datatype.datatypelayout.DataTypeTabDefinition;
import com.velox.api.datatype.fielddefinition.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to Integrate Quality Assurance Measurements upload during the Quality Control workflow.
 * The plugin will add the uploaded Quality Assurance Measurements as stand alone values to the LIMS without any
 * relationships to any other data in LIMS.
 *
 * @author sharmaa1
 */
public class QualityAssuranceMetricsLoader extends DefaultGenericPlugin {

    private final String QA_METRICS_UPLOAD_TAG = "LOAD QA METRICS";

    public QualityAssuranceMetricsLoader() {
        setTaskEntry(true);
        setTaskToolbar(true);
        setLine1Text("Add QA");
        setLine2Text("Measures");
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
                return new PluginResult(false);
            }
            //prompt user for how many values they would like to add.
            String numOfQaMetricsToLoad = clientCallback.showInputDialog("How many rows would you like to add?");
            //validate the entry to make sure it is an Integer value.
            if (isValidInteger(numOfQaMetricsToLoad)){
                //prompt the user to add Quality Asurance Measurements
                List<Map<String,Object>> dataFieldValues = getQaDataFromUser(Integer.parseInt(numOfQaMetricsToLoad));
                if (dataFieldValues.size()>0) {
                    logInfo("Adding QA values to table");
                    logInfo(dataFieldValues.toString());
                    // create and add Quality Asurance Measurements to LIMS.
                    List<DataRecord> newQaRecords = dataRecordManager.addDataRecords("QualityAssuranceMeasure", dataFieldValues, user);
                    logInfo("New added records size: " + newQaRecords.size());
                    //attach newly created datarecords to current tak running this plugin in workflow.
                    activeTask.addAttachedDataRecords(newQaRecords);
                }
                else {
                    logInfo("User did not upload any QualityAssuranceMeasure values");
                    return new PluginResult(false);
                }
            }
        }catch (Exception e){
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate that the user input is an Integer value
     * @param input
     * @return boolean
     * @throws ServerException
     */
    private boolean isValidInteger(String input) throws ServerException {
        try {
            Integer.parseInt(input);
            return true;
        }catch (Exception e){
            clientCallback.displayError(String.format("Please enter a valid number. %s is not a valid entry.", input));
            return false;
        }
    }

    /**
     * Method to create a TemporaryDataType and layout to display as pop up table for the user to enter Quality Assurance Measurement values.
     * @param numToAdd
     * @return List<Map<String, Object>>
     * @throws ServerException
     * @throws RemoteException
     */
    private List<Map<String, Object>> getQaDataFromUser(Integer numToAdd) throws ServerException, RemoteException {
        TemporaryDataType tempPlate = new TemporaryDataType("QualityAssuranceMeasure", "Quality Assurance Measurement");
        List<VeloxFieldDefinition<?>> fieldDefList = new ArrayList<VeloxFieldDefinition<?>>();
        VeloxPickListFieldDefinition validationType = VeloxFieldDefinition.pickListFieldBuilder().displayName("Quality Assurance Validation Type").dataFieldName("QAValidationType").visible(true).pickListName("Quality Assurance Measurement Type").build();
        VeloxPickListFieldDefinition instrument = VeloxFieldDefinition.pickListFieldBuilder().displayName("Instrument Analyzed On").dataFieldName("Instrument").visible(true).pickListName("QC Instruments").build();
        VeloxPickListFieldDefinition qaReagentType = VeloxFieldDefinition.pickListFieldBuilder().displayName("Material Used for QA Validation").dataFieldName("QAReagentType").visible(true).pickListName("Quality Assurance Reagent").build();
        VeloxPickListFieldDefinition controlMeasured = VeloxFieldDefinition.pickListFieldBuilder().displayName("Control Measured").dataFieldName("ControlMeasured").visible(true).pickListName("Quality Assurance Sample Type").build();
        VeloxDoubleFieldDefinition concentration = VeloxFieldDefinition.doubleFieldBuilder().displayName("Concentration (ng/uL) Measured").dataFieldName("Concentration").visible(true).maxValue(100000000).minValue(0).precision((short)3).build();
        VeloxIntegerFieldDefinition bpSize = VeloxFieldDefinition.integerFieldBuilder().displayName("Base Pair Size").dataFieldName("BPSize").visible(true).maxValue(10000000).minValue(0).build();
        VeloxDoubleFieldDefinition din = VeloxFieldDefinition.doubleFieldBuilder().displayName("DIN").dataFieldName("DIN").visible(true).maxValue(100000000).minValue(0).precision((short)3).build();
        VeloxDoubleFieldDefinition rin = VeloxFieldDefinition.doubleFieldBuilder().displayName("RIN").dataFieldName("RIN").visible(true).maxValue(100000000).minValue(0).precision((short)3).build();
        VeloxDoubleFieldDefinition totalMass = VeloxFieldDefinition.doubleFieldBuilder().displayName("Total Mass (ng)").dataFieldName("TotalMass").visible(true).maxValue(100000000).minValue(0).precision((short)3).build();
        VeloxBooleanFieldDefinition isVerified = VeloxFieldDefinition.booleanFieldBuilder().displayName("Result Verified").dataFieldName("Verified").visible(true).build();
        VeloxStringFieldDefinition comments = VeloxFieldDefinition.stringFieldBuilder().displayName("Comments").dataFieldName("Comments").visible(true).maxLength(100000000).numLines(1).build();
        fieldDefList.add(validationType);
        fieldDefList.add(instrument);
        fieldDefList.add(qaReagentType);
        fieldDefList.add(controlMeasured);
        fieldDefList.add(concentration);
        fieldDefList.add(bpSize);
        fieldDefList.add(din);
        fieldDefList.add(rin);
        fieldDefList.add(totalMass);
        fieldDefList.add(isVerified);
        fieldDefList.add(comments);
        tempPlate.setVeloxFieldDefinitionList(fieldDefList);
        setTempDataTypeLayout(tempPlate, fieldDefList);
        List<Map<String, Object>> defaultValuesList = new ArrayList<>();
        for (int i = 1; i <= numToAdd; i++) {
            Map<String, Object> values = new HashMap<>();
            values.put("QAValidationType", "");
            defaultValuesList.add(values);
        }
        return clientCallback.showTableEntryDialog("Enter QA information",
                "Enter QA Information in the table below.", tempPlate, defaultValuesList);
    }

    /**
     * Method to set the layout on the TemporaryDataType. Without the layout the table structure is not visible in the pop up dialog.
     * @param temporaryDataType
     * @param temporaryDataTypeFieldDefinitions
     * @throws ServerException
     */
    private void setTempDataTypeLayout(TemporaryDataType temporaryDataType, List<VeloxFieldDefinition<?>> temporaryDataTypeFieldDefinitions) throws ServerException {
        String formName = "Quality Assurance form";
        // Create form
        DataFormComponent form = new DataFormComponent(formName, formName);
        form.setCollapsed(false);
        form.setColumn(0);
        form.setColumnSpan(4);
        form.setOrder(0);
        form.setHeight(10);
        // Add fields to the form
        for (int i = 0; i < temporaryDataTypeFieldDefinitions.size(); i++) {
            logInfo(temporaryDataTypeFieldDefinitions.get(i).getColumnName());
            VeloxFieldDefinition<?> fieldDef = temporaryDataTypeFieldDefinitions.get(i);
            FieldDefinitionPosition pos = new FieldDefinitionPosition(fieldDef.getDataFieldName());
            pos.setFormColumn(0);
            pos.setFormColumnSpan(4);
            pos.setOrder(i);
            pos.setFormName(formName);
            form.setFieldDefinitionPosition(pos);
            logInfo(form.getFieldDefinitionPositionList().toString());
        }
        // Create a tab with the form on it
        DataTypeTabDefinition tabDef = new DataTypeTabDefinition("Tab1", "Tab 1");
        tabDef.setDataTypeLayoutComponent(form);
        tabDef.setTabOrder(0);
        // Create a layout with the tab on it
        DataTypeLayout layout = new DataTypeLayout("Quality Assurance Measures", "Quality Assurance Measures", "Default layout for Quality Assurance Measures.");
        layout.setDataTypeTabDefinition(tabDef);
        // Add the layout to the TDT
        temporaryDataType.setDataTypeLayout(layout);
    }
}