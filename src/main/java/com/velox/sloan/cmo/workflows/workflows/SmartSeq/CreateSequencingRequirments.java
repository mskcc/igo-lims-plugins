package com.velox.sloan.cmo.workflows.workflows.SmartSeq;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import com.velox.api.plugin.PluginLogger;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.*;

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
    public PluginResult run() throws Exception {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> seqReuirements = new LinkedList<>();
            for (DataRecord sample : samples) {
                Map<String, Object> values = new HashMap<>();
                String sampleId = sample.getStringVal("SampleId", user);
                String otherSampleId = sample.getStringVal("OtherSampleId", user);
                String tumorOrNormal = sample.getStringVal("TumorOrNormal", user);
                values.put("sampleId", sampleId);
                values.put("otherSampleId", otherSampleId);
                values.put("SequencingRunType","PE100");
                values.put("RequestedReads", "5-10M");
                values.put("TumorOrNormal", tumorOrNormal);

                DataRecord newSeqReqRecord = dataRecordManager.addDataRecord("SeqRequirement", user);
                newSeqReqRecord.setFields(values, user);
                sample.addChild(newSeqReqRecord, user);
                seqReuirements.add(newSeqReqRecord);

            }
            this.activeTask.addAttachedDataRecords(seqReuirements);
            activeTask.getTask().getTaskOptions().put("_SEQ REQUIREMENTS CREATED", "");
        } catch (ServerException e) {
            String errMsg = String.format("ServerException while splitting SmartSeq samples:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
//        } catch (Exception e) {
//            clientCallback.displayError(e.getMessage());
//            return new PluginResult(false);
//        }
        return new PluginResult(true);
    }
}
