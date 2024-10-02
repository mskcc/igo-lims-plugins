package com.velox.sloan.cmo.workflows.workflows.SpatialTranscriptomics;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.util.List;

public class DiactivateWorkflowStepsForXenium extends DefaultGenericPlugin {
    public DiactivateWorkflowStepsForXenium() {
        setTaskSubmit(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskName().equalsIgnoreCase("Record Permeabilization Time and Fluorescence") &&
                this.activeTask.getTask().getTaskOptions().containsKey("Deactivate through six if Xenium");
    }
    public PluginResult run() {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedcDNAProtocols = activeTask.getAttachedDataRecords("VisiumcDNAPrepProtocol1", user);
            if (attachedcDNAProtocols.get(0).getBooleanVal("Xenium", user)) {
                logInfo("Disabling steps 4 through 6");
                logInfo("Active task ID = " + this.activeTask.getTask().getTaskId());
                this.activeWorkflow.getActiveTask(1143772L).setStatus(0);
                this.activeWorkflow.getActiveTask(1143773L).setStatus(0);
                this.activeWorkflow.getActiveTask(1143774L).setStatus(0);
            }
        } catch (Exception e) {
            logError("Exception occurred on step 3 of Spatial Transcriptomics workflow: " + e.getMessage() + "\n" +
                    e.getStackTrace());
        }
        return new PluginResult(true);
    }
}
