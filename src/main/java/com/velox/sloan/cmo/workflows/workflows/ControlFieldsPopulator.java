package com.velox.sloan.cmo.workflows.workflows;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.*;

/**
 * Plugin to populate fields for control samples.
 * 
 * This plugin performs three main steps:
 * 1. Reads species and recipe values from non-control samples
 * 2. Populates control samples with the same species and recipe values
 * 3. Links all control samples to a constant dummy request record
 * 
 * The plugin identifies controls using the "IsControl" boolean field in Sample records.
 * Controls inherit the species and recipe from regular samples in the same workflow,
 * ensuring consistency and enabling automated processing.
 * 
 * @author IGO Development Team
 */
public class ControlFieldsPopulator extends DefaultGenericPlugin {
    
    /**
     * Constructor - sets plugin execution parameters
     */
    public ControlFieldsPopulator() {
        setTaskEntry(true);
        setOrder(PluginOrder.MIDDLE.getOrder());
    }

    /**
     * Determines whether this plugin should run for the current task.
     * 
     * @return true if plugin should execute, false otherwise
     * @throws RemoteException if there's an error accessing task information
     */
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE &&
               activeTask.getTask().getTaskOptions().containsKey("POPULATE CONTROL FIELDS") &&
               !activeTask.getTask().getTaskOptions().containsKey("_CONTROL FIELDS POPULATED");
    }
    
    /**
     * Main plugin execution method.
     * 
     * Executes the three-step control population process:
     * 1. Find non-control samples and extract their species/recipe values
     * 2. Populate all control samples with the extracted values
     * 3. Link all controls to a constant dummy request record
     * 
     * @return PluginResult indicating success or failure
     * @throws Exception if any error occurs during execution
     */
    public PluginResult run() throws Exception {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            
            String species = "";
            String recipe = "";
            for (DataRecord sample : samples) {
                Object isControl = sample.getValue("IsControl", user);
                if (isControl == null || !(Boolean) isControl) {
                    species = sample.getStringVal("Species", user);
                    recipe = sample.getStringVal("Recipe", user);
                    break;
                }
            }
            
            for (DataRecord sample : samples) {
                Object isControl = sample.getValue("IsControl", user);
                if (isControl != null && (Boolean) isControl) {
                    String sampleId = sample.getStringVal("SampleId", user);
                    sample.setDataField("Species", species, user);
                    sample.setDataField("Recipe", recipe, user);
                    logInfo("Updated control: " + sampleId);
                }
            }
            
            DataRecord dummyRecord = getDummyRecord();
            if (dummyRecord != null) {
                for (DataRecord sample : samples) {
                    Object isControl = sample.getValue("IsControl", user);
                    if (isControl != null && (Boolean) isControl) {
                        dummyRecord.addChild(sample, user);
                    }
                }
            }
            
            activeTask.getTask().getTaskOptions().put("_CONTROL FIELDS POPULATED", "");
            
        } catch (ServerException e) {
            String errMsg = String.format("ServerException while populating control fields:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        
        return new PluginResult(true);
    }
    
    /**
     * Gets or creates the constant dummy request record for linking controls.
     * 
     * @return DataRecord representing the dummy request, or null if creation fails
     */
    private DataRecord getDummyRecord() {
        try {
            List<DataRecord> dummyRecords = dataRecordManager.queryDataRecords("Request", 
                "RequestId = 'CONSTANT_DUMMY_REQUEST'", user);
            
            if (dummyRecords != null && !dummyRecords.isEmpty()) {
                return dummyRecords.get(0);
            }
            
            Map<String, Object> values = new HashMap<>();
            values.put("RequestId", "CONSTANT_DUMMY_REQUEST");
            values.put("IsDummy", true);
            
            DataRecord dummyRecord = dataRecordManager.addDataRecord("Request", user);
            dummyRecord.setFields(values, user);
            return dummyRecord;
            
        } catch (Exception e) {
            logError("Error with dummy record: " + e.getMessage());
        }
        return null;
    }
}