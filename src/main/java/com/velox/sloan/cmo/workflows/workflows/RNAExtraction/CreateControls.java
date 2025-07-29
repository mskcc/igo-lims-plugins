package com.velox.sloan.cmo.workflows.workflows.RNAExtraction;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;
public class CreateControls extends DefaultGenericPlugin {
    private List<String> controlTypes = Arrays.asList("Positive", "Negative", "CONTROL_RNA_5K", "CONTROL_RNA_20K", "CONTROL_RNA_100K", "Other");
    public CreateControls() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("Generate Control Records")
                && !activeTask.getTask().getTaskOptions().containsKey("Control Records Generated");
    }
    public PluginResult run() {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            String sampleType = samples.get(0).getStringVal("ExemplarSampleType", user);
            String requestId = samples.get(0).getParentsOfType("Request", user).get(0).getStringVal("RequestId", user);
            List<String> controlList = clientCallback.showListDialog("Please Select All the Type of the Control(s) to Generate:", controlTypes, true, user);
            List<DataRecord> allControls = new LinkedList<>();
            for (String control : controlList) {
                Map<String, Object> values = new HashMap<>();
                values.put("Volume", 30);
                values.put("SampleId", control + "_" + requestId);
                values.put("OtherSampleId", control);
                values.put("ExemplarSampleType", sampleType);
                DataRecord controlRec = dataRecordManager.addDataRecord("Sample", user);
                controlRec.setFields(values, user);
                allControls.add(controlRec);
            }
            this.activeTask.addAttachedDataRecords(allControls);
            activeTask.getTask().getTaskOptions().put("Control Records Generated", "");
        }
        catch (Exception e) {
            return new PluginResult(false);
        }

        return new PluginResult(true);
    }
}
