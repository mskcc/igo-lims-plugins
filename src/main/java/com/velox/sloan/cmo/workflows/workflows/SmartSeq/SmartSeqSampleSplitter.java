package com.velox.sloan.cmo.workflows.workflows.SmartSeq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.List;

/**
 * This plugin is designed to split each SMART Seq sample to 384 samples.
 * @author Fahimeh Mirhaj
 * */
public class SmartSeqSampleSplitter extends DefaultGenericPlugin {

    public SmartSeqSampleSplitter () {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("SPLIT SMARTSEQ SAMPLE")
                && !activeTask.getTask().getTaskOptions().containsKey("_SMARTSEQ SAMPLE SPLITTED");
    }

    public PluginResult run () throws RemoteException {
        try {
            List<DataRecord> samplesAttachedToTask = activeTask.getAttachedDataRecords("Sample", user);
        } catch (ServerException e) {
            String errMsg = String.format("ServerException while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);

        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException while parsing the DLP plus spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
    }
}
