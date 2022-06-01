package com.velox.sloan.cmo.workflows.workflows.TruSeqRNALibPrepfromcDNA;

import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

public class WarningMessage extends DefaultGenericPlugin {

    public WarningMessage() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskOptions().containsKey("VALIDATE cDNA QC");
    }
    public PluginResult run() throws ServerException {
        boolean userChoice = clientCallback.showOkCancelDialog("QC cDNA", "Have you finished the QC for cDNA from day1 " +
                        "before launching samples for Day2?");
        if(userChoice) {
            return new PluginResult(true);
        }
        return new PluginResult(false);
    }
}
