package com.velox.sloan.cmo.workflows.workflows.ContainerRetrieval;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

public class GetPlatesForAProject extends DefaultGenericPlugin {

    public GetPlatesForAProject() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("FIND CONTAINERS OF A REQUEST") &&
                !this.activeTask.getTask().getTaskOptions().containsKey("CONTAINERS RETURNED");
    }

    public PluginResult run() throws Throwable {
        try {
            String requestId = clientCallback.showInputDialog("Please enter the request ID of the samples to return");
            List<DataRecord> requests = dataRecordManager.queryDataRecords("Request", "RequestId = '" + requestId + "'", user);
            DataRecord[] relatedSamples = requests.get(0).getChildrenOfType("Sample", user);
            Set<DataRecord> plates = new HashSet<>();
            Set<DataRecord> racks = new HashSet<>();
            for (DataRecord sample: relatedSamples) {
                if (sample.getParentsOfType("Plate", user) != null && sample.getParentsOfType("Plate", user).size() > 0) {
                    plates.add(Arrays.asList(sample.getParentsOfType("Plate", user).get(0)).get(0));
                }
                String micronicTubeBarcode = sample.getStringVal("MicronicTubeBarcode", user);
                if (sample.getParentsOfType("MicronicRack", user) != null && sample.getParentsOfType("MicronicRack", user).size() > 0) {
                    racks.add(Arrays.asList(sample.getParentsOfType("MicronicRack", user).get(0)).get(0));
                }
            }
            activeTask.addAttachedDataRecords(plates);
            activeTask.addAttachedDataRecords(racks);

        } catch (Exception e) {
            clientCallback.displayError(String.format("An exception occurred while finding plates of the entered project ID:\n%s", ExceptionUtils.getStackTrace(e)));
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);
        }

        return new PluginResult(true);
    }
}
