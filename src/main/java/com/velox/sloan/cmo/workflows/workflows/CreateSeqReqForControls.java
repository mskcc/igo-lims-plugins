package com.velox.sloan.cmo.workflows.workflows;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CreateSeqReqForControls extends DefaultGenericPlugin {
    public CreateSeqReqForControls() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("CREATE SEQ REQUIREMENTS FOR CONTROLS")
                && !activeTask.getTask().getTaskOptions().containsKey("_CONTROLS SEQ REQUIREMENTS CREATED");
    }

    public PluginResult run() throws Exception {
        try {
            List<DataRecord> coverageReqRefs = this.dataRecordManager.queryDataRecords("ApplicationReadCoverageRef",
                    "ReferenceOnly != 1 AND isControl = 1 ", this.user);
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> seqReuirements = new LinkedList<>();
            for (DataRecord sample : samples) {
                boolean isControl = sample.getBooleanVal("isControl", user);
                if (isControl) {
                    Map<String, Object> values = new HashMap<>();
                    String sampleId = sample.getStringVal("SampleId", user);
                    String sampleName = sample.getStringVal("OtherSampleId", user);
                    String recipe = sample.getStringVal("Recipe", user);
                    String species = sample.getStringVal("Species", user);
                    for (DataRecord ref : coverageReqRefs) {
                        if (ref.getStringVal("PlatformApplication", user).equalsIgnoreCase(recipe)) {
                            values.put("SequencingRunType", ref.getStringVal("SequencingRunType", user));
                            if (species.equalsIgnoreCase("Human")) {
                                values.put("RequestedReads", ref.getStringVal("MillionReadsHuman", user));
                            }
                            else if (species.equalsIgnoreCase("Mouse")) {
                                values.put("RequestedReads", ref.getStringVal("MillionReadsMouse", user));
                            }
                        }
                    }
                    values.put("SampleId", sampleId);
                    values.put("OtherSampleId", sampleName);
                    DataRecord newSeqReqRecord = dataRecordManager.addDataRecord("SeqRequirement", user);
                    newSeqReqRecord.setFields(values, user);
                    sample.addChild(newSeqReqRecord, user);
                    seqReuirements.add(newSeqReqRecord);
                }
            }
            this.activeTask.addAttachedDataRecords(seqReuirements);

            activeTask.getTask().getTaskOptions().put("_CONTROLS SEQ REQUIREMENTS CREATED", "");
        } catch (ServerException e) {
            String errMsg = String.format("ServerException while setting sequencing requirements for controls:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
        }
}
