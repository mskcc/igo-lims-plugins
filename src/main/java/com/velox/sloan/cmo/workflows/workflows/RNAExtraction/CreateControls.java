package com.velox.sloan.cmo.workflows.workflows.RNAExtraction;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This plugin enables assignment of controls on a Micronic Rack.
 *
 * @author mirhajf
 * */
public class CreateControls extends DefaultGenericPlugin {
    private List<String> controlTypes = Arrays.asList("Positive", "Negative", "CONTROL_RNA_5K", "CONTROL_RNA_20K", "CONTROL_RNA_100K", "Other");
    public CreateControls() {
        setTaskEntry(true);
        setOrder(PluginOrder.MIDDLE.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("Generate Control Records")
                && !activeTask.getTask().getTaskOptions().containsKey("Control Records Generated");
    }
    public PluginResult run() {
        try {
            List<String> controlList = clientCallback.showListDialog("Please Select All the Type of the Control(s) to Generate:", controlTypes, true, user);
            List<DataRecord> allControls = new LinkedList<>();
            logInfo("controlList size = " + controlList.size());
            for (String control : controlList) {
                // The query below times out if multiple controls are selected due to the search delay on the sample table
                //List<DataRecord> existingControls = dataRecordManager.queryDataRecords("Sample", "IsControl = " + Boolean.TRUE + " AND OtherSampleId like '%" + control + "%' ", user);
                Date now = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                Map<String, Object> values = new HashMap<>();
                values.put("Volume", 30);
                values.put("SampleId", control + "_" + sdf.format(now));
                logInfo("control ID = " + values.get("SampleId"));
                values.put("OtherSampleId", control);
                values.put("ExemplarSampleType", "RNA");
                values.put("isControl", Boolean.TRUE);
                DataRecord controlRec = dataRecordManager.addDataRecord("Sample", user);
                controlRec.setFields(values, user);
                allControls.add(controlRec);
                logInfo("in the controls for loop!");
            }
            this.activeTask.addAttachedDataRecords(allControls);
            activeTask.getTask().getTaskOptions().put("Control Records Generated", "");
        }
        catch (Exception e) {
            String errMsg = String.format("Remote Exception Error while generating control records:\n%s", e.getStackTrace());
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }
}
