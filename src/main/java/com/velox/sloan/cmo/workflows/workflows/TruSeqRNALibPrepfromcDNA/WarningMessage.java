package com.velox.sloan.cmo.workflows.workflows.TruSeqRNALibPrepfromcDNA;

import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

import java.rmi.RemoteException;
import java.util.Arrays;

public class WarningMessage extends DefaultGenericPlugin {

    public WarningMessage() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskName().equalsIgnoreCase("create experiment") &&
                this.activeTask.getTask().getTaskOptions().containsKey("VALIDATE cDNA QC") &&
                !this.activeTask.getTask().getTaskOptions().containsKey("QC VALIDATED");
    }

    public PluginResult run() throws ServerException, RemoteException {
        logInfo("Giving warning to the user!!");
        try {
            boolean userChoice = clientCallback.showOkCancelDialog("QC cDNA", "Have you finished the QC for cDNA from day1 " +
                    "before launching samples for Day2?");
            if (userChoice) {
                this.activeTask.getTask().getTaskOptions().put("QC VALIDATED", "");
                return new PluginResult(true);
            }
            else {
                return new PluginResult(false);
            }

        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while warning for cDNA QC:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }
    }
}
