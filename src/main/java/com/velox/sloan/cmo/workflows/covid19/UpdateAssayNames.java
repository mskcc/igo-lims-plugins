package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

public class UpdateAssayNames extends DefaultGenericPlugin {

    private final String UPDATE_COVID19_ASSAY_NAMES = "UPDATE COVID19 ASSAY NAMES";
    private final List<String> ASSAY_NAMES = Arrays.asList("QUAD4_NA", "N1", "N2", "RP", "QUAD4_NA");

    public UpdateAssayNames() {
        setTaskEntry(true);
        setTaskToolbar(true);
        setLine1Text("Update Assay Names");
        setIcon("com/velox/sloan/cmo/resources/import_32.gif");
        setOrder(PluginOrder.LAST.getOrder() + 2);
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(UPDATE_COVID19_ASSAY_NAMES);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(UPDATE_COVID19_ASSAY_NAMES) &&
                    activeTask.getStatus() == ActiveTask.COMPLETE;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public PluginResult run() throws ServerException {
        try {
            IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user);
            if (attachedProtocolRecords.isEmpty()) {
                clientCallback.displayError(String.format("%s protocol records are not attached to the task.", activeTask.getInputDataTypeName()));
                logError(String.format("%s protocol records are not attached to the task.", activeTask.getInputDataTypeName()));
                return new PluginResult(false);
            }
            for (DataRecord rec : attachedProtocolRecords) {
                String wellPositon = rec.getStringVal("TargetWellPosAliq", user);
                int quadPosition = utils.getPlateQuadrant(wellPositon);
                rec.setDataField("QpcrAssayName", ASSAY_NAMES.get(quadPosition), user);
            }
        } catch (Exception e) {
            logError(e.getMessage());
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }
}
