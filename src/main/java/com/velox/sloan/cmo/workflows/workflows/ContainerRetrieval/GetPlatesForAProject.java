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
            Set<DataRecord> plates = new HashSet<>();
            Set<DataRecord> racks = new HashSet<>();
            String requestIds = clientCallback.showInputDialog("Please enter the comma separated request IDs of the samples to return");
            if (requestIds.isEmpty() || requestIds.isBlank()) {
                return new PluginResult(true);
            }
            String[] requestsArr = requestIds.split(",");
            for (String eachRequest : requestsArr) {
                List<DataRecord> requests = dataRecordManager.queryDataRecords("Request", "RequestId = '" + eachRequest.trim() + "'", user);
                DataRecord[] relatedSamples = requests.get(0).getChildrenOfType("Sample", user);
                for (DataRecord sample: relatedSamples) {
                    if (sample.getParentsOfType("Plate", user) != null && sample.getParentsOfType("Plate", user).size() > 0) {
                        plates.add(Arrays.asList(sample.getParentsOfType("Plate", user).get(0)).get(0));
                        logInfo("Adding plate: " + Arrays.asList(sample.getParentsOfType("Plate", user).get(0)).get(0).getStringVal("StorageLocationBarcode", user));
                    }
                    String micronicTubeBarcode = sample.getStringVal("StorageLocationBarcode", user);
                    //String containerType = sample.getStringVal("ContainerType", user);
                    if (micronicTubeBarcode != null && micronicTubeBarcode.length() > 1 && micronicTubeBarcode.matches("\\d+")) { // storage location barcodes for micronic racks are all digits and the length should be > 1, excluding
                        List<DataRecord> micronicRacks = dataRecordManager.queryDataRecords("StorageUnit", "StorageUnitId = '" + micronicTubeBarcode + "'", user);
                        logInfo("Adding rack: " + micronicRacks.get(0).getStringVal("StorageUnitId", user));
                        racks.add(micronicRacks.get(0));
                    }
                }
                activeTask.addAttachedDataRecords(plates);
                activeTask.addAttachedDataRecords(racks);
            }

        } catch (Exception e) {
            clientCallback.displayError(String.format("An exception occurred while finding plates of the entered project ID:\n%s", ExceptionUtils.getStackTrace(e)));
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);
        }

        return new PluginResult(true);
    }
}
