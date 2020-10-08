package com.velox.sloan.cmo.workflows.micronics;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.StorageUnitModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

public class MicronicTubeRearranger extends DefaultGenericPlugin {
    public MicronicTubeRearranger() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("AUTO REARRAY MICRONICS");
    }

    @Override
    public PluginResult run() throws ServerException {
    return new PluginResult(true);
    }

    /**
     * Method to get list of Storage units attached to the task.
     * @param attachedSamples
     * @return
     * @throws ServerException
     */
    private List<DataRecord> getMicronicRacks(List<DataRecord> attachedSamples) throws ServerException {
        Set<String> storageUnitBarcodes = new HashSet<>();
        List<DataRecord> storageUnits = new ArrayList<>();
        try {
            for (DataRecord rec : attachedSamples) {
                Object rackBarcode = rec.getValue(SampleModel.STORAGE_LOCATION_BARCODE, user);
                if (!Objects.isNull(rackBarcode)){
                    storageUnitBarcodes.add(rackBarcode.toString());
                }
            }
            String queryClause;
            if (!storageUnitBarcodes.isEmpty()) {
                queryClause = String.format("%s IN %s", StorageUnitModel.STORAGE_UNIT_ID, "('" + StringUtils.join(storageUnitBarcodes, "','") + "')");
                storageUnits = dataRecordManager.queryDataRecords(StorageUnitModel.DATA_TYPE_NAME, queryClause, user);
            }
        }catch (Exception e){
            String msg = String.format("%s -> Error while getting Storage information from attached Samples: %s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(msg);
            logError(msg);
        }
        return storageUnits;
    }

    /**
     * Method to get total available open space on the racks.
     * @param micronicRacks
     * @return
     * @throws ServerException
     */
    private int getAvailableOpenSpaces(List<DataRecord> micronicRacks) throws ServerException {
        int totalAvailableSpace = 0;
        try{
            for (DataRecord rack : micronicRacks){
                Object capacity = rack.getValue(StorageUnitModel.STORAGE_UNIT_CAPACITY, user);
                Object occupied = rack.getValue(StorageUnitModel.OCCUPIED_COUNT, user);
                if (!Objects.isNull(capacity) && ! Objects.isNull(occupied)){
                    totalAvailableSpace += (int)capacity - (int)occupied;
                }
            }
        }catch (Exception e){
            String msg = String.format("%s -> Error while computing open space on micronic racks: %s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(msg);
            logError(msg);
        }
        return totalAvailableSpace;
    }

    /**
     * Method to compute number of samples exceeding the available space on Micronic Racks.
     * @param micronicRacks
     * @param sampleSize
     * @return
     */
    private int computeSpaceOverflow(List<DataRecord> micronicRacks, int sampleSize){
        return 0;
    }
}
