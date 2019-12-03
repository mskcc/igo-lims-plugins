package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * This is the plugin class is designed to update the values for 'AutoIndexAssignmentConfig' records based on the 'IndexBarcode' DataRecord values manually assigned by the Users. This will help to track the
 * Volumes for 'AutoIndexAssignmentConfig' DataRecords.
 * 'Index Barcode and Adapter' terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 * @author sharmaa1
 */
public class ManualIndexAssignmentHandler extends DefaultGenericPlugin {
    private final List<String> RECIPES_TO_USE_SPECIAL_ADAPTERS = Arrays.asList("CRISPRSeq", "AmpliconSeq");
    private final String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";
    AutoIndexAssignmentHelper autohelper;

    public ManualIndexAssignmentHandler() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().containsKey("UPDATE INDEX VOLUMES POST MANUAL INDEX ASSIGNMENT") && !activeTask.getTask().getTaskOptions().containsKey("_UPDATE_INDEX_VOLUMES_POST_MANUAL_INDEX_ASSIGNMENT");
    }

    public PluginResult run() throws ServerException {
        autohelper = new AutoIndexAssignmentHelper(managerContext);
        try {
            List<DataRecord> attachedSamplesList = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IndexBarcode", user);
            if (attachedIndexBarcodeRecords.isEmpty()) {
                clientCallback.displayError(String.format("Could not find any IndexBarcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                logError(String.format("Could not find any IndexBarcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            if (attachedSamplesList.isEmpty()) {
                clientCallback.displayError("No Samples found attached to this task.");
                logError("No Samples found attached to this task.");
                return new PluginResult(false);
            }
            List<DataRecord> activeIndexAssignmentConfigs = dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IsActive=1", user);
            if (activeIndexAssignmentConfigs.isEmpty()) {
                clientCallback.displayError("Could not find any active 'AutoIndexAssignmentConfig'");
                logError("Could not find any active 'AutoIndexAssignmentConfig'");
                return new PluginResult(false);
            }
            Integer plateSize = autohelper.getPlateSize(attachedSamplesList);
            Double minAdapterVolInPlate = autohelper.getMinAdapterVolumeRequired(plateSize);
            Double maxPlateVolume = autohelper.getMaxVolumeLimit(plateSize);
            setUpdatedIndexAssignmentValues(activeIndexAssignmentConfigs, attachedIndexBarcodeRecords, minAdapterVolInPlate, maxPlateVolume, plateSize);
            checkIndexAssignmentsForDepletedAdapters(activeIndexAssignmentConfigs);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while updating Index assignment values after manual Index assignment to samples:\n%s", ExceptionUtils.getStackTrace(e)));
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to add the metadata for 'IndexBarcode' records, that is not added during Manual Index Assignment process.
     * @param indexAssignmentConfigs
     * @param indexBarcodeRecords
     * @param minVolInAdapterPlate
     * @param maxPlateVolume
     * @throws NotFound
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws ServerException
     */
    private void setUpdatedIndexAssignmentValues(List<DataRecord> indexAssignmentConfigs, List<DataRecord> indexBarcodeRecords, Double minVolInAdapterPlate, Double maxPlateVolume, Integer plateSize) throws NotFound, RemoteException, InvalidValue, IoError, ServerException {
        for (DataRecord indexBarcodeRec : indexBarcodeRecords) {
            boolean found = false;
            for (DataRecord indexConfig : indexAssignmentConfigs) {
                if (indexBarcodeRec.getStringVal("IndexId", user).equals(indexConfig.getStringVal("IndexId", user))
                        && indexBarcodeRec.getStringVal("IndexTag", user).equals(indexConfig.getStringVal("IndexTag", user))) {
                    found=true;
                    Double dnaInputAmount = Double.parseDouble(indexBarcodeRec.getStringVal("InitialInput", user));
                    String wellPosition = indexConfig.getStringVal("WellId", user);
                    String adapterSourceRow = autohelper.getAdapterRowPosition(wellPosition);
                    String adapterSourceCol = autohelper.getAdapterColPosition(wellPosition);
                    Double adapterStartConc = indexConfig.getDoubleVal("AdapterConcentration", user);
                    Double targetAdapterConc = autohelper.getCalculatedTargetAdapterConcentration(dnaInputAmount, plateSize);
                    Double adapterVolume = autohelper.getAdapterInputVolume(adapterStartConc, minVolInAdapterPlate, targetAdapterConc);
                    Double waterVolume = autohelper.getVolumeOfWater(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, maxPlateVolume);
                    Double actualTargetAdapterConc = adapterStartConc/((waterVolume + adapterVolume)/adapterVolume);
                    //Double adapterConcentration = autohelper.getAdapterConcentration(indexConfig, adapterVolume, waterVolume);
                    autohelper.setUpdatedIndexAssignmentConfigVol(indexConfig, adapterVolume);
                    indexBarcodeRec.setDataField("BarcodePlateID", indexConfig.getStringVal("AdapterPlateId", user), user);
                    indexBarcodeRec.setDataField("IndexRow", adapterSourceRow, user);
                    indexBarcodeRec.setDataField("IndexCol", adapterSourceCol, user);
                    indexBarcodeRec.setDataField("BarcodeVolume", adapterVolume, user);
                    indexBarcodeRec.setDataField("Aliq1WaterVolume", waterVolume, user);
                    indexBarcodeRec.setDataField("IndexBarcodeConcentration", actualTargetAdapterConc, user);
                }
            }
            if (!found){
                clientCallback.displayError(String.format("No Active '%s' record found for Index ID '%s'. Please double check to avoid discrepancies in '%s' record volumes.",
                        INDEX_ASSIGNMENT_CONFIG_DATATYPE, indexBarcodeRec.getStringVal("IndexId", user), INDEX_ASSIGNMENT_CONFIG_DATATYPE));
            }
            activeTask.getTask().getTaskOptions().put("_UPDATE_INDEX_VOLUMES_POST_MANUAL_INDEX_ASSIGNMENT", "");
        }
    }

    /**
     * Method to check for the DataRecords in 'AutoIndexAssignmentConfig' for which the value of 'IsDepelted' should to marked to true.
     * @param indexAssignmentConfigs
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    private void checkIndexAssignmentsForDepletedAdapters(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        for (DataRecord rec: indexAssignmentConfigs){
            if (rec.getDoubleVal("AdapterVolume", user) < 10.00){
                rec.setDataField("IsDepelted", true, user);
                rec.setDataField("IsActive", false, user);
                clientCallback.displayWarning(String.format("AutoIndexAssignmentConfig with Index ID '%s' on Adapter Plate '%s' has volume less than 10ul. It is now marked as Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
                logInfo(String.format("AutoIndexAssignmentConfig with Index ID '%s' on Adapter Plate '%s' has volume less than 10ul. It is now marked as Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
            }
        }
    }
}
