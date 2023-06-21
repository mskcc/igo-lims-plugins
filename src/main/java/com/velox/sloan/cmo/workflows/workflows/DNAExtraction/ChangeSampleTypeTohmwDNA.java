package com.velox.sloan.cmo.workflows.workflows.DNAExtraction;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;


import java.rmi.RemoteException;
import java.util.List;

public class ChangeSampleTypeTohmwDNA extends DefaultGenericPlugin {

    public ChangeSampleTypeTohmwDNA() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskName().equalsIgnoreCase("DNA Extraction") &&
                this.activeTask.getTask().getTaskOptions().containsKey("CHANGE SAMPLE TYPE TO HMWDNA");
    }
    public PluginResult run() {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> dnaExtractionExpriments = activeTask.getAttachedDataRecords("DNAExtractionExperiment", user);
            int i = 0;
                for (DataRecord sample : samples) {
                    Boolean isONT= dnaExtractionExpriments.get(i++).getBooleanVal("IsONT", user);
                    if (isONT) {
                        logInfo("isONT = true, changing sample type");
                        sample.setDataField("ExemplarSampleType", "hmwDNA", user);
                    }

                }
                i = 0;
                for (DataRecord dnaExperiment : dnaExtractionExpriments) {
                    Boolean isONT= dnaExtractionExpriments.get(i++).getBooleanVal("IsONT", user);
                    if (isONT) {
                        logInfo("isONT = true, changing extraction sample type");
                        dnaExperiment.setDataField("ExtractionSampleType", "hmwDNA", user);
                    }
                }


        } catch (InvalidValue | IoError |NotFound | com.velox.api.util.ServerException | RemoteException e) {
            logError("An exception occurred while setting sample type to hmwDNA", e);

        }
        return new PluginResult(true);
    }
}
