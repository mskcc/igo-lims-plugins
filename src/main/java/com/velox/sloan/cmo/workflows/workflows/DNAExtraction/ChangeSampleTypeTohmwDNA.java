package com.velox.sloan.cmo.workflows.workflows.DNAExtraction;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;


import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeSampleTypeTohmwDNA extends DefaultGenericPlugin {

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskName().equalsIgnoreCase("create experiment") &&
                this.activeTask.getTask().getTaskOptions().containsKey("CHANGE SAMPLE TYPE TO HMWDNA");
    }
    public PluginResult run() {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            Boolean isONT= activeTask.getAttachedDataRecords("DNAExperiment", user).get(0).getBooleanVal("IsONT", user);
            Map<String, Object> sampleType = new HashMap<>();
            if (isONT) {
                for (DataRecord sample : samples) {
                    sampleType.put("SampleType", "hmwDNA");
                    sample.setFields(sampleType, user);
                }
            }

        } catch (NotFound | com.velox.api.util.ServerException | RemoteException e) {

        }
        return new PluginResult(true);
    }
}
