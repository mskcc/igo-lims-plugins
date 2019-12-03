package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.shared.managers.ManagerBase;
import com.velox.sapioutils.shared.managers.ManagerContext;

import java.rmi.RemoteException;
import java.util.List;
import java.util.function.DoubleUnaryOperator;


/**
 * This is a helper class with common methods used across plugins. Index Barcode and Adapter terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 * @author sharmaa1
 */

public class AutoIndexAssignmentHelper extends ManagerBase{

    public AutoIndexAssignmentHelper(ManagerContext managerContext){

        setManagerContext(managerContext);
    }

    /**
     * To set the value of Volume field on the records under 'AutoIndexAssignmentConfig' DataType.
     * @param indexAssignmentConfig
     * @param adapterVolumeUsed
     * @return Updated AutoIndexAssignmentConfig DataRecord
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    public DataRecord setUpdatedIndexAssignmentConfigVol(DataRecord indexAssignmentConfig, Double adapterVolumeUsed) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        Double previousVol = indexAssignmentConfig.getDoubleVal("AdapterVolume", user);
        Double newVolume = previousVol - adapterVolumeUsed;
        indexAssignmentConfig.setDataField("AdapterVolume", newVolume, user);

        if (newVolume <= 10) {
            indexAssignmentConfig.setDataField("IsDepelted", true, user);
            indexAssignmentConfig.setDataField("IsActive", false, user);
            clientCallback.displayWarning(String.format("The Volume for adapter '%s'on Adapter Plate with ID '%s' is below 10ul.\nThis adapter will be marked as depleted and will be ignored for future assignments.",
                    indexAssignmentConfig.getStringVal("IndexId", user), indexAssignmentConfig.getStringVal("AdapterPlateId", user)));
        }
        return indexAssignmentConfig;
    }


    /**
     * Get the minimum liquid volume for the Index Barcode to be present in a plate well for a plate given its plate size.
     * @param plateSize
     * @return Double volume
     */
    public Double getMinAdapterVolumeRequired(Integer plateSize){
        if(plateSize == 96){
            return 7.50;
        }
        else {
            return 3.00;
        }
    }

    /**
     * Get the volume of Index Barcode to transfer to a well on new plate.
     * @param startingAdapterConcentration
     * @param minAdapterVolume
     * @param targetAdapterConcentration
     * @return Double volume
     */
   public Double getAdapterInputVolume(Double startingAdapterConcentration, Double minAdapterVolume, Double targetAdapterConcentration) {
        Double c1 = startingAdapterConcentration;
        Double v1 = minAdapterVolume;
        Double c2 = targetAdapterConcentration;
        Double v2 = (c2 * v1)/c1;
        if (v2 >= 2.00){
            return v2;
        }
        return 2.00;
    }


    /**
     * Get concentration of adapter in the new plate given the DNA Input amount.
     * @param dnaInputAmount
     * @return Double Concentration
     */
    public Double getCalculatedTargetAdapterConcentration(Double dnaInputAmount, Integer plateSize){
        if (plateSize == 96 && dnaInputAmount < 50.00){
            return (15 * dnaInputAmount)/50.00;
        }
        if (plateSize == 384 && dnaInputAmount <20.00){
            return (15 * dnaInputAmount)/20.00;
        }
        return 15.00;
    }

    /**
     * Get the volume of water to transfer to new plate.
     * @param startingAdapterConcentration
     * @param minAdapterVolume
     * @param targetAdapterConcentration
     * @param maxPlateVolume
     * @return Double volume
     */
    public Double getVolumeOfWater(Double startingAdapterConcentration, Double minAdapterVolume, Double targetAdapterConcentration, Double maxPlateVolume){
        Double c1 = startingAdapterConcentration;
        Double v1 = minAdapterVolume;
        Double c2 = targetAdapterConcentration;
        Double v2 = (c2 * v1)/c1;
        if (v2 >= 2.00){
            return minAdapterVolume - v2;
        }
        else {
            Double waterVol = ((2.00 * startingAdapterConcentration)/targetAdapterConcentration) - 2.00;
            if (waterVol > maxPlateVolume){
                return maxPlateVolume - 2.00;
            }
            return waterVol;
        }
    }

    /**
     *  Get the plate size given the Sample DataRecord(s) that is on a plate.
     * @param samples
     * @return Integer plate size.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    public Integer getPlateSize(List<DataRecord> samples) throws IoError, RemoteException, NotFound, ServerException {
        DataRecord plate = samples.get(0).getParentsOfType("Plate", user).get(0);
        Integer plateSizeIndex = Integer.parseInt(plate.getValue("PlateWellCnt", user).toString());
        String plateSize = dataMgmtServer.getPickListManager(user).getPickListConfig("Plate Sizes").getEntryList().get(plateSizeIndex);
        return Integer.parseInt(plateSize.split("-")[0]);
    }

    /**
     * Get the maximim volume a plate well can hold given plate size.
     * @param plateSize
     * @return Double volume
     */
    public Double getMaxVolumeLimit(Integer plateSize) {
        switch (plateSize) {
            case 96:
                return 150.00;
            case 384:
                return 40.00;
        }
        return 150.00;
    }


    /**
     * Get Row Position of Index Barcode on plate given its Well ID.
     * @param wellPosition
     * @return String Row Position.
     * @throws NotFound
     * @throws RemoteException
     */
    public String getAdapterRowPosition(String wellPosition) throws NotFound, RemoteException {
        return wellPosition.replaceAll("[^A-Za-z]+", "");
    }

    /**
     *  Get Column Position of Index Barcode on plate given its Well ID.
     * @param wellPosition
     * @return String Column Position
     */
    public String getAdapterColPosition(String wellPosition) {
        return wellPosition.replaceAll("\\D+", "");
    }

}
