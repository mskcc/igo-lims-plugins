package com.velox.sloan.cmo.workflows.workflows.normalization;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.recmodels.SampleModel;
import org.apache.commons.lang3.StringUtils;

import javax.xml.crypto.Data;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plugin to check if the samples launched in the workflow contain SampleTypes that should not be launched together.
 * If true, user is displayed a warning along with options to cancel the workflow or continue running the workflow.
 * @author  sharmaa1 on 8/5/19.
 */
public class MixedSampleTypeValidator extends DefaultGenericPlugin {
    private final HashSet<String> MIXED_SAMPLETYPES_TO_AVOID = new HashSet<>(Arrays.asList("cfdna", "dna"));
    public MixedSampleTypeValidator() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskName().equalsIgnoreCase("create experiment") && activeTask.getTask().getTaskOptions().containsKey("VALIDATE MIXED SAMPLE TYPES");
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            // This check is for making sure samples are in the correct process as normalization workflow is the first step of different processes


            Set<String> sampleTypeValues = new HashSet<>();
            Set<String> assignedProcessValues = new HashSet<>();
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            for (DataRecord sample : samples) {
                DataRecord assignedProcess = sample.getParentsOfType("AssignedProcess", user).get(0);
                if (assignedProcess == null || StringUtils.isBlank(assignedProcess.getStringVal("ProcessName", user))) {
                    clientCallback.displayError((String.format("Assigned process is blank for sample %s", sample.getStringVal("SampleId", user))));
                }
                else {
                    assignedProcessValues.add(assignedProcess.getStringVal("ProcessName", user).toLowerCase());
                }
            }
            if (assignedProcessValues.size() > 1) {
                logInfo("Samples are assigned to different processes in the Normalization queue!");
                if (!clientCallback.showOkCancelDialog("Samples' assigned process validation", "You are launching samples assigned to different processes, would you like to move forward?")) {
                    return new PluginResult(false);
                } else {
                    logInfo(String.format("User %s elected to continue workflow %s regardless of mixed processes message.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(true);
                }
            }
            for (DataRecord samp : samples) {
                Object sampleType = samp.getValue(SampleModel.EXEMPLAR_SAMPLE_TYPE, user);
                if (sampleType == null || StringUtils.isBlank((String) sampleType)) {
                    clientCallback.displayWarning(String.format("SampleType value not found for sample %s", samp.getStringVal("SampleId", user)));
                } else {
                    sampleTypeValues.add(sampleType.toString().toLowerCase());
                    logInfo(sampleType.toString());
                }
            }
            if (sampleTypeValues.size() == 0) {
                logInfo("SampleType values not found on samples");
                clientCallback.displayWarning("Cannot validate SampleType for attached samples. SampleType values not found for samples in this task.");
                return new PluginResult(true);
            }

            boolean isUniqueSampleType = sampleTypeValues.size() == 1;
            if (!isUniqueSampleType && hasAllMixedSampleTypesToAvoid(sampleTypeValues)){
                logInfo(String.format("Mixed Recipes WARNING -> %s samples are being launched together.", MIXED_SAMPLETYPES_TO_AVOID.toString()));
                if (!clientCallback.showOkCancelDialog("SAMPLES HAVE MIXED SAMPLE TYPES WHICH SHOULD NOT BE LAUNCHED TOGETHER!", String.format("Samples launched in the workflow contain %s SampleTypes.\nDO YOU WANT TO CONTINUE?", MIXED_SAMPLETYPES_TO_AVOID.toString()))) {
                    logInfo(String.format("User %s elected to cancel workflow %s to avoid 'DNA' and 'cfDNA' samples being launched together.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(false);
                } else {
                    logInfo(String.format("User %s elected to continue workflow %s regardless of mixed SampleType warning.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(true);
                }
            }
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while validating SampleTypes:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to check if a set contains all SampleType values in another set.
     * @param sampleTypeValues values found on samples
     * @return boolean
     */
    private boolean hasAllMixedSampleTypesToAvoid(Set<String> sampleTypeValues){
        if (sampleTypeValues.size() > 1){
            sampleTypeValues.retainAll(MIXED_SAMPLETYPES_TO_AVOID);
            return sampleTypeValues.size() == MIXED_SAMPLETYPES_TO_AVOID.size();
        }
        return false;
    }
}