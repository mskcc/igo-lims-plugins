package com.velox.sloan.cmo.workflows.workflows.SpatialTranscriptomics;

public class DiactivateWorkflowStepsForXenium extends DefaultGenericPlugin {
    public DiactivateWorkflowStepsForXenium() {
        setTaskSubmit(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskName().equalsIgnoreCase("") &&
                this.activeTask.getTask().getTaskOptions().containsKey("");
    }
    public PluginResult run() {
        try {

        } catch (Exception e) {

        }
        return new PluginResult(true);
    }
}
