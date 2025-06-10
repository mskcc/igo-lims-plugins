package com.velox.sloan.cmo.workflows.workflows.SmartSeq;

import java.rmi.RemoteException;

public class CreateSequencingRequirments extends DefaultGenericPlugin {
    public CreateSequencingRequirments() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("CREATE SEQ REQUIREMENTS")
                && !activeTask.getTask().getTaskOptions().containsKey("_SEQ REQUIREMENTS CREATED");
    }
    public PluginResult run() throws RemoteException {
        try {
            for () {

            }


            activeTask.getTask().getTaskOptions().put("_SEQ REQUIREMENTS CREATED", "");
        }
        catch() {

        }
        return new PluginResult(true);
    }
}
