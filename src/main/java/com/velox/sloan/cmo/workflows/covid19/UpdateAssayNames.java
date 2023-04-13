package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

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
        } catch (RemoteException e) {
            String message = String.format("Error while setting toolbar button. Remote Exception:\n%s", ExceptionUtils.getStackTrace(e));
            logError(message);
            return false;
        }
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
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
        } catch (RemoteException e) {
            String message = String.format("Error while updating Assay names. Remote Exception:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(message);
            logError(message);
            return new PluginResult(false);
        } catch (IoError e) {
            String message = String.format("Error while updating Assay names. IO Exception:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(message);
            logError(message);
            return new PluginResult(false);
        } catch (NotFound e) {
            String message = String.format("Error while updating Assay names. Not Found Exception:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(message);
            logError(message);
            return new PluginResult(false);
        } catch (InvalidValue e) {
            String message = String.format("Error while updating Assay names. Invalid Value Exception:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(message);
            logError(message);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }
}
