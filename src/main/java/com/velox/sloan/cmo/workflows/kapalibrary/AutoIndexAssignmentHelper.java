package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.NotFound;
import com.velox.sapioutils.shared.managers.ManagerBase;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;


/**
 * This is a helper class with common methods used across plugins. Index Barcode and Adapter terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 *
 * @author sharmaa1
 */

public class AutoIndexAssignmentHelper extends ManagerBase {

    private final List<String> RNA_SAMPLE_TYPES = Arrays.asList("rna", "cdna");
    /**
     * Get the minimum liquid volume for the Index Barcode to be present in a plate well for a plate given its plate size.
     *
     * @param plateSize
     * @return Double volume
     */
    public Double getMinAdapterVolumeRequired(Integer plateSize) {
        if (plateSize == 96) {
            return 7.50;
        } else {
            return 3.00;
        }
    }

    /**
     * Get the volume of Index Barcode to transfer to a well on new plate.
     *
     * @param startingAdapterConcentration
     * @param minAdapterVolume
     * @param targetAdapterConcentration
     * @return Double volume
     */
    public Double getAdapterInputVolume(Double startingAdapterConcentration, Double minAdapterVolume, Double targetAdapterConcentration, String sampleType) {
        if (RNA_SAMPLE_TYPES.contains(sampleType.toLowerCase())){
            return 5.0;
        }
        Double c1 = startingAdapterConcentration;
        Double v1 = minAdapterVolume;
        Double c2 = targetAdapterConcentration;
        Double v2 = (c2 * v1) / c1;
        if (v2 >= 2.00) {
            return v2;
        }
        return 2.00;
    }


    /**
     * Get concentration of adapter in the new plate given the DNA Input amount.
     *
     * @param dnaInputAmount
     * @return Double Concentration
     */
    public Double getCalculatedTargetAdapterConcentration(Double dnaInputAmount, Integer plateSize, String sampleType) {
        if (RNA_SAMPLE_TYPES.contains(sampleType.toLowerCase())){
            return 15.00;
        }
        if (plateSize == 96 && dnaInputAmount < 50.00) {
            return (15 * dnaInputAmount) / 50.00;
        }
        if (plateSize == 384 && dnaInputAmount < 20.00) {
            return (15 * dnaInputAmount) / 20.00;
        }
        return 15.00;
    }

    /**
     * Get the volume of water to transfer to new plate.
     *
     * @param startingAdapterConcentration
     * @param minAdapterVolume
     * @param targetAdapterConcentration
     * @param maxPlateVolume
     * @return Double volume
     */
    public Double getVolumeOfWater(Double startingAdapterConcentration, Double minAdapterVolume, Double targetAdapterConcentration, Double maxPlateVolume, String sampleType) {
        if (RNA_SAMPLE_TYPES.contains(sampleType.toLowerCase())){
            return 0.0;
        }
        Double c1 = startingAdapterConcentration;
        Double v1 = minAdapterVolume;
        Double c2 = targetAdapterConcentration;
        Double v2 = (c2 * v1) / c1;
        if (v2 >= 2.00) {
            return minAdapterVolume - v2;
        } else {
            Double waterVol = ((2.00 * startingAdapterConcentration) / targetAdapterConcentration) - 2.00;
            if (waterVol > maxPlateVolume) {
                return maxPlateVolume - 2.00;
            }
            return waterVol;
        }
    }

    /**
     * Get the maximim volume a plate well can hold given plate size.
     *
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
     *
     * @param wellPosition
     * @return String Row Position.
     * @throws NotFound
     * @throws RemoteException
     */
    public String getAdapterRowPosition(String wellPosition) throws NotFound, RemoteException {
        return wellPosition.replaceAll("[^A-Za-z]+", "");
    }

    /**
     * Get Column Position of Index Barcode on plate given its Well ID.
     *
     * @param wellPosition
     * @return String Column Position
     */
    public String getAdapterColPosition(String wellPosition) {
        return wellPosition.replaceAll("\\D+", "");
    }

    /**
     * To check if a int value is odd.
     *
     * @param value
     * @return
     */
    public boolean isOddValue(int value) {
        return value % 2 != 0;
    }

}
