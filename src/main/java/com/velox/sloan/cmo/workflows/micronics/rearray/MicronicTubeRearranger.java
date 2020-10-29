package com.velox.sloan.cmo.workflows.micronics.rearray;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.datatype.fielddefinition.VeloxFieldDefinition;
import com.velox.api.datatype.fielddefinition.VeloxStringFieldDefinition;
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
import java.util.stream.Collectors;

import static com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils.createTempDataForm;
import static java.util.Comparator.comparingInt;
import static java.util.Map.Entry.comparingByValue;

public class MicronicTubeRearranger extends DefaultGenericPlugin{

    private final int RACK_ROWS = 12;
    private final int RACK_COL = 8;
    private final String STORAGE_UNIT_TYPE = "Micronic Rack";
    private final int MAX_COLUMNS_96_WELL = 12;
    private final int MAX_ROWS_96_WELL = 8;
    private final List<String> ROW_NAMES_96 = Arrays.asList("A", "B", "C","D","E","F","G","H");

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

//        String query = String.format("%s = '%s'", StorageUnitModel.STORAGE_UNIT_ID, rackBarcode );
//        List<DataRecord> storageUnits = dataRecordManager.queryDataRecords(StorageUnitModel.DATA_TYPE_NAME, query, user);
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
    private int getNumAvailableOpenSpaces(List<DataRecord> micronicRacks) throws ServerException {
        int totalAvailableSpaces = 0;
        try{
            for (DataRecord rack : micronicRacks){
                Object capacity = rack.getValue(StorageUnitModel.STORAGE_UNIT_CAPACITY, user);
                Object occupied = rack.getValue(StorageUnitModel.OCCUPIED_COUNT, user);
                if (!Objects.isNull(capacity) && ! Objects.isNull(occupied)){
                    totalAvailableSpaces += (int)capacity - (int)occupied;
                }
            }
        }catch (Exception e){
            String msg = String.format("%s -> Error while computing open space on micronic racks: %s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(msg);
            logError(msg);
        }
        return totalAvailableSpaces;
    }

    /**
     * Method to compute number of samples exceeding the available space on Micronic Racks.
     * @param micronicRacks
     * @param sampleSize
     * @return
     */
    private int computeSpaceOverflow(List<DataRecord> micronicRacks, int sampleSize) throws ServerException {
        int overFlow = 0;
        try{
            int totalCapacity = 0;
            for (DataRecord mr : micronicRacks){
                Object capacity = mr.getValue(StorageUnitModel.STORAGE_UNIT_CAPACITY, user);
                if (!Objects.isNull(capacity)){
                    totalCapacity += (int)capacity;
                }
            }
            if (totalCapacity < sampleSize){
                return sampleSize - totalCapacity;
            }
        } catch (Exception e) {
            String msg = String.format("%s -> Error while computing space overflow: %s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(msg);
            logError(msg);
        }
        return 0;
    }

    /**
     * Method to get the number of new racks required to complete re-arraying of samples.
     * @param samples
     * @param capacity
     */
    private double getNumberOfNewRacksRequired(int samples, int capacity){
        return Math.ceil((double)samples / (double) capacity);
    }

    /**
     * Method to prompt user to enter Micronic Rack barcodes.
     * @param numRacks
     * @return
     * @throws ServerException
     */
    private List<Object> getRackBarcodes(int numRacks) throws ServerException {
        List<Object> rackBarcodes = new ArrayList<>();
        try {
            TemporaryDataType temporaryDataType = new TemporaryDataType("MicronicRack", "Micronic Rack");
            List<VeloxFieldDefinition<?>> fieldDefList = new ArrayList<VeloxFieldDefinition<?>>();
            VeloxStringFieldDefinition storageLocationBarcode = VeloxFieldDefinition.stringFieldBuilder().displayName("Storage Location Barcode").dataFieldName("StorageLocationBarcode").visible(true).editable(true).htmlEditor(false).required(true).htmlEditor(true).isRestricted(false).build();
            fieldDefList.add(storageLocationBarcode);
            temporaryDataType.setVeloxFieldDefinitionList(fieldDefList);
            createTempDataForm(temporaryDataType, fieldDefList, "MicronicRack", "Micronic Rack");
            List<Map<String, Object>> defaultValuesList = new ArrayList<>();
            for (int i = 1; i <= numRacks; i++) {
                Map<String, Object> samples = new HashMap<>();
                samples.put("StorageLocationBarcode", "");
                defaultValuesList.add(samples);
            }
            List<Map<String, Object>> rackIds = clientCallback.showTableEntryDialog("Enter the Micronic Rack barcodes.",
                    String.format("Need %d new Rack Barcodes, Please enter Rack Barcodes."), temporaryDataType, defaultValuesList);
            for (Map<String, Object> map : rackIds) {
                Object bc = map.get("StorageLocationBarcode");
                if (bc == null || StringUtils.isBlank(bc.toString())) {
                    String msg = String.format("One or more Storage Location Barcodes entered by user are not valid. Values entered: %s", rackIds.toString());
                    logError(msg);
                    clientCallback.displayError(msg);
                }
                rackBarcodes.add(bc);
            }
        }catch (Exception e){
            String msg = String.format("%s -> Error while prompting user for Micronic Rack barcodes: \n%s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            logError(msg);
            clientCallback.displayError(msg);
        }
        return rackBarcodes;
    }

    /**
     * Method to create new storage Unit
     * @param rackBarcode
     * @return
     * @throws ServerException
     */
    private DataRecord createStorageRack(Object rackBarcode) throws ServerException {
        try{
            Map<String, Object> vals = new HashMap<>();
            vals.put(StorageUnitModel.STORAGE_UNIT_ID, rackBarcode);
            vals.put(StorageUnitModel.UNIT_ROWS, RACK_ROWS);
            vals.put(StorageUnitModel.UNIT_COLUMNS, RACK_COL);
            vals.put(StorageUnitModel.STORAGE_UNIT_CAPACITY, RACK_COL * RACK_ROWS);
            vals.put(StorageUnitModel.STORAGE_UNIT_TYPE, STORAGE_UNIT_TYPE);
            DataRecord micRack = dataRecordManager.addDataRecord(StorageUnitModel.DATA_TYPE_NAME, user);
            micRack.setFields(vals, user);
            return micRack;
        } catch (Exception e) {
            String msg = String.format("%s -> Error while creating new %s record using ID %s: \n%s", ExceptionUtils.getRootCause(e), StorageUnitModel.DATA_TYPE_NAME, rackBarcode, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(msg);
        }
        return null;
    }

    /**
     * Method to get storage unit with maximum availabe open space from a list of storage units.
     * @param storageUnits
     * @return
     * @throws ServerException
     */
    private DataRecord getStorageWithMaxSpace(List<DataRecord> storageUnits) throws ServerException {
        DataRecord maxStorage = null;
        try {
            if (storageUnits.size() ==1){
                return storageUnits.get(0);
            }
            int minOccupied = RACK_COL * RACK_ROWS;
            for (DataRecord rec : storageUnits){
                Object occupancy = rec.getValue(StorageUnitModel.OCCUPIED_COUNT, user);
                if(occupancy != null && (int) occupancy < minOccupied){
                    maxStorage = rec;
                }
            }
        }catch (Exception e){
            String msg = String.format("%s -> Error while getting storage unit with maximum available space:\n%s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            logError(msg);
            clientCallback.displayError(msg);
        }
        storageUnits.remove(maxStorage);
        return maxStorage;
    }


    /**
     * Method to get storage unit with maximum availabe open space from a list of storage units.
     * @param storageUnits
     * @return
     * @throws ServerException
     */
    private DataRecord getStorageWithMinSpace(List<DataRecord> storageUnits) throws ServerException {
        DataRecord maxStorage = null;
        try {
            if (storageUnits.size() ==1){
                return storageUnits.get(0);
            }
            int minOccupied = RACK_COL * RACK_ROWS;
            for (DataRecord rec : storageUnits){
                Object occupancy = rec.getValue(StorageUnitModel.OCCUPIED_COUNT, user);
                if(occupancy != null && (int) occupancy > minOccupied){
                    maxStorage = rec;
                }
            }
        }catch (Exception e){
            String msg = String.format("%s -> Error while getting storage unit with maximum available space:\n%s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e));
            logError(msg);
            clientCallback.displayError(msg);
        }
        storageUnits.remove(maxStorage);
        return maxStorage;
    }

    /**
     *
     * @param samples
     */
    private void rearrayWithinSourceRacks(List<DataRecord> samples){
        try {
           List<DataRecord> storageUnits = activeTask.getAttachedDataRecords(StorageUnitModel.DATA_TYPE_NAME, user);
           if (storageUnits.isEmpty()){
               clientCallback.displayError("No StorageUnit attached to the task");
           }
           DataRecord destinationRack = getStorageWithMaxSpace(storageUnits);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Method to get SampleIds from attached samples.
     * @param samples
     */
    private List<String> getSamplesIds(List<DataRecord> samples){
        try{
             return samples.stream().map(e ->{
                try {
                    return e.getStringVal(SampleModel.SAMPLE_ID, user);
                } catch (Exception a) {
                    logError(ExceptionUtils.getStackTrace(a));
                    return null;
                }
            }).collect(Collectors.toList());
        }catch (Exception e){
            logError(ExceptionUtils.getStackTrace(e));
        }
        return new ArrayList<>();
    }

    /**
     * Method to get samples grouped by Micronic Rack.
     * @param samples
     * @return
     */
    private Map<String, List<DataRecord>> getRackAndSamples(List<DataRecord>samples){
        Map<String, List<DataRecord>> rackAndSamples = new HashMap<>();
        try{
            for (DataRecord rec : samples){
                Object storageBarcode = rec.getValue(SampleModel.STORAGE_LOCATION_BARCODE, user);
                if (!Objects.isNull(storageBarcode)){
                    String key = storageBarcode.toString();
                    rackAndSamples.putIfAbsent(key, new ArrayList<>());
                    rackAndSamples.get(key).add(rec);
                }
            }
        } catch (Exception e) {
            String msg = String.format("%s -> Error while getting Micronic Rack and related Samples: %s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            logError(msg);
        }
        return rackAndSamples;
    }
}