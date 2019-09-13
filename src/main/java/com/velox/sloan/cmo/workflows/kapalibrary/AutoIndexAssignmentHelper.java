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
 * Created by sharmaa1 on 9/10/19.
 */

public class AutoIndexAssignmentHelper extends ManagerBase{

    public AutoIndexAssignmentHelper(ManagerContext managerContext){
        setManagerContext(managerContext);
    }

    public DataRecord setUpdatedIndexAssignmentConfigVol(DataRecord indexAssignmentConfig, Double adapterVolumeUsed) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        Double previousVol = indexAssignmentConfig.getDoubleVal("AdapterVolume", user);
        Double newVolume = previousVol - adapterVolumeUsed;
        indexAssignmentConfig.setDataField("AdapterVolume", newVolume, user);

        if (newVolume <= 10) {
            indexAssignmentConfig.setDataField("IsDepelted", true, user);
            indexAssignmentConfig.setDataField("IsActive", false, user);
            clientCallback.displayWarning(String.format("The Volume for adapter '%s'on Adapter Plate with ID '%s' is below 10ul.\nThis adapter will be marked as depleted and will ignored for future assignments.",
                    indexAssignmentConfig.getStringVal("IndexId", user), indexAssignmentConfig.getStringVal("AdapterPlateId", user)));
        }
        return indexAssignmentConfig;
    }


    public Double getMinAdapterVolumeRequired(Integer plateSize){
        if(plateSize == 96){
            return 7.50;
        }
        else {
            return 3.00;
        }
    }

    //new methods
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


    //new method
    public Double getCalculatedTargetAdapterConcentration(Double dnaInputAmount){
        if (dnaInputAmount<50.00){
            return (15 * dnaInputAmount)/50.00;
        }
        return 15.00;
    }

    //new methods
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

    public Integer getPlateSize(List<DataRecord> samples) throws IoError, RemoteException, NotFound, ServerException {
        DataRecord plate = samples.get(0).getParentsOfType("Plate", user).get(0);
        Integer plateSizeIndex = Integer.parseInt(plate.getValue("PlateWellCnt", user).toString());
        String plateSize = dataMgmtServer.getPickListManager(user).getPickListConfig("Plate Sizes").getEntryList().get(plateSizeIndex);
        return Integer.parseInt(plateSize.split("-")[0]);
    }

    public Double getMaxVolumeLimit(Integer plateSize) {
        switch (plateSize) {
            case 96:
                return 150.00;
            case 384:
                return 40.00;
        }
        return 150.00;
    }

//    public Double getVolumeOfWater(Double adapterVol, Double adapterConc, Double maxVolume) {
//        Double targerConc = 7.50;
//        if (adapterVol < maxVolume) {
//            return maxVolume - adapterVol;
//        }
//        return maxVolume;
//    }

    public String getAdapterRowPosition(String wellPosition) throws NotFound, RemoteException {
        return wellPosition.replaceAll("[^A-Za-z]+", "");
    }

    public String getAdapterColPosition(String wellPosition) {
        return wellPosition.replaceAll("\\D+", "");
    }

//    public Double getAdapterConcentration(DataRecord indexAssignmentConfig, Double adapterVolume, Double waterVolume) throws NotFound, RemoteException {
//        Double adapterConcentration = indexAssignmentConfig.getDoubleVal("AdapterConcentration", user);
//        Double dilutionFactor = (adapterVolume + waterVolume) / adapterVolume;
//        return adapterConcentration / dilutionFactor;
//    }

}
