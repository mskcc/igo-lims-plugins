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
 * @author patelo2
 */
public class ControlFieldsPopulator extends DefaultGenericPlugin {
    
  private static final String DUMMY_REQUEST_ID = "06000_C";
    
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
        return this.activeTask.getTask().getTaskOptions().containsKey("POPULATE CONTROL FIELDS") &&
               !this.activeTask.getTask().getTaskOptions().containsKey("_CONTROL FIELDS POPULATED");
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
            
            // Step 1: Find species and recipe from non-control samples
            String species = "";
            String recipe = "";
            List<DataRecord> samplesAssignedProcess = new LinkedList<>();
            for (DataRecord sample : samples) {
                Object isControl = sample.getValue("IsControl", user);
                // Only process non-control samples (ignore IsControl = false samples completely)
                if (isControl == null || !(Boolean) isControl) {
                    species = sample.getStringVal("Species", user);
                    recipe = sample.getStringVal("Recipe", user);
                    samplesAssignedProcess = sample.getParentsOfType("AssignedProcess", user);
                    if (species != null && recipe != null) {
                        logInfo("Found species: " + species + ", recipe: " + recipe + ", process name: " +
                                samplesAssignedProcess.get(0).getStringVal("ProcessName", user) + " from non-control sample");
                        break;
                    }
                }
            }
            // Step 2: Populate control samples with species and recipe
            for (DataRecord sample : samples) {
                Object isControl = sample.getValue("IsControl", user);
                if (isControl != null && (Boolean) isControl) {
                    String sampleId = sample.getStringVal("SampleId", user);
                    sample.setDataField("Species", species, user);
                    sample.setDataField("Recipe", recipe, user);
                    sample.setDataField("RequestId", DUMMY_REQUEST_ID, user);
                    logInfo("Updated control sample " + sampleId + " with species: " + species + ", recipe: " + recipe);
                }
            }
            // Step 3: Link all control samples to the constant dummy request record
            DataRecord dummyRecord = getDummyRecord();
            if (dummyRecord != null) {
                for (DataRecord sample : samples) {
                    Object isControl = sample.getValue("IsControl", user);
                    if (isControl != null && (Boolean) isControl) {
                        String sampleId = sample.getStringVal("SampleId", user);
                        dummyRecord.addChild(sample, user);
                        samplesAssignedProcess.get(0).addChild(sample, user);
                        logInfo("Linked control sample " + sampleId + " to dummy request record");
                        logInfo("Linked control sample " + sampleId + " to parent process record");
                    }
                }
            } else {
                logError("Could not find or create dummy request record");
                return new PluginResult(false);
            }
            
            // Mark task as completed
            this.activeTask.getTask().getTaskOptions().put("_CONTROL FIELDS POPULATED", "");
        } catch (ServerException e) {
            String errMsg = String.format("ServerException while populating control fields:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        
        return new PluginResult(true);
    }
    
    /**
     * Gets the constant dummy request record for linking controls.
     * This method queries for the existing dummy request record using the predefined RequestId.
     * The dummy request record should already exist in the system.
     * 
     * @return DataRecord representing the dummy request, or null if not found
     */
    private DataRecord getDummyRecord() {
        try {
            List<DataRecord> dummyRecords = dataRecordManager.queryDataRecords("Request", 
                "RequestId = '" + DUMMY_REQUEST_ID + "'", user);
            
            if (dummyRecords != null && !dummyRecords.isEmpty()) {
                logInfo("Found existing dummy request record with ID: " + DUMMY_REQUEST_ID);
                return dummyRecords.get(0);
            } else {
                logError("No dummy request record found with ID: " + DUMMY_REQUEST_ID + ". Please ensure the record exists in the system.");
                return null;
            }
            
        } catch (Exception e) {
            logError("Error querying for dummy request record: " + e.getMessage());
        }
        return null;
    }
}