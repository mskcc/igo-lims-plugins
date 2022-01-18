package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.NotFound;
import org.junit.Test;

import java.rmi.RemoteException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoIndexAssignmentHelperTest {
    AutoIndexAssignmentHelper autoHelper = new AutoIndexAssignmentHelper();

    @Test
    public void getMinAdapterVolumeRequired() {
        Integer plateSize = 96;
        Integer plateSize2 = 384;

        assertTrue(autoHelper.getMinAdapterVolumeRequired(plateSize, false) == 7.50);
        assertTrue(autoHelper.getMinAdapterVolumeRequired(plateSize2, false) == 3.00);
    }

    @Test
    public void getAdapterInputVolume() {
        Double startConc = 15.0;
        Double minVolumeForAdapter = 7.50;
        Double targetConcentration = 10.0;
        String sampleType = "dna";

        Double startConc2 = 15.0;
        Double minVolumeForAdapter2 = 3.0;
        Double targetConcentration2 = 5.0;

        String rnaSampleType = "rna";
        String rnaSampleType2="cdna";

        assertTrue(autoHelper.getAdapterInputVolume(startConc, minVolumeForAdapter, targetConcentration, sampleType) == 5.0);
        assertTrue(autoHelper.getAdapterInputVolume(startConc2, minVolumeForAdapter2, targetConcentration2, sampleType) == 2.0);
        assertTrue(autoHelper.getAdapterInputVolume(startConc2, minVolumeForAdapter2, targetConcentration2, rnaSampleType) == 5.0);
        assertTrue(autoHelper.getAdapterInputVolume(startConc2, minVolumeForAdapter2, targetConcentration2, rnaSampleType2) == 5.0);
    }

    @Test
    public void getCalculatedTargetAdapterConcentration() {
        //Max volume of adapter for Library prep is 7.5 uL for 96 well plates and 3 uL for 384 well plates.
        //based on input volume and plate size the target concentration of adapter differs.
        //adapter concentration for 96 well plates plateaus at 50ng. After 50ng concentration remains 15nM
        //adapter concentration for 96 well plates plateaus at 20ng. After 20ng concentration remains 15nM
        String dnaSampleType = "dna";
        String rnaSampleType = "cdna";
        String rnaSampleType2 = "rna";
        Double dnaInputAmount = 40.00;
        Integer plateSize = 96;
        Double dnaInputAmount1 = 50.00;
        Integer plateSize1 = 96;
        Double dnaInputAmount2 = 60.00;
        Integer plateSize2 = 96;
        Double dnaInputAmount3 = 20.00;
        Integer plateSize3 = 384;
        Double dnaInputAmount4 = 15.00;
        Integer plateSize4 = 384;
        Double dnaInputAmount5 = 25.00;
        Integer plateSize5 = 384;

        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount, plateSize, dnaSampleType) == 12.00);
        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount1, plateSize1, dnaSampleType) == 15.00);
        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount2, plateSize2, dnaSampleType) == 15.00);

        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount3, plateSize3, dnaSampleType) == 15.00);
        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount4, plateSize4, dnaSampleType) == 11.25);
        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount5, plateSize5, dnaSampleType) == 15.00);

        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount2, plateSize2, rnaSampleType) == 15.00);
        assertTrue(autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount2, plateSize2, rnaSampleType2) == 15.00);
    }

    @Test
    public void getVolumeOfWater() {
        String dnaSampleType = "dna";
        String rnaSampleType = "cdna";
        String rnaSampleType2 = "rna";
        Double startAdapterConc = 15.0; // for all plates
        Double minVolumeForAdapter = 7.50; //for all plates

        Double targetAdapterConcentration = 10.0; // for 96 well plate
        Double maxPlateVolume = 150.00; // for 96 well plate

        Double targetAdapterConcentration384 = 5.0; // for 384 well plate
        Double maxPlateVolume384 = 40.00; // for 384 well plate
        assertTrue(autoHelper.getVolumeOfWater(startAdapterConc, minVolumeForAdapter, targetAdapterConcentration, maxPlateVolume, dnaSampleType) == 2.5);
        assertTrue(autoHelper.getVolumeOfWater(startAdapterConc, minVolumeForAdapter, targetAdapterConcentration384, maxPlateVolume384, dnaSampleType) == 5.00);

        assertFalse(autoHelper.getVolumeOfWater(startAdapterConc, minVolumeForAdapter, targetAdapterConcentration384, maxPlateVolume384, rnaSampleType) == 5.00);
        assertFalse(autoHelper.getVolumeOfWater(startAdapterConc, minVolumeForAdapter, targetAdapterConcentration384, maxPlateVolume384, rnaSampleType2) == 5.00);

        assertTrue(autoHelper.getVolumeOfWater(startAdapterConc, minVolumeForAdapter, targetAdapterConcentration384, maxPlateVolume384, rnaSampleType) == 0.00);
        assertTrue(autoHelper.getVolumeOfWater(startAdapterConc, minVolumeForAdapter, targetAdapterConcentration384, maxPlateVolume384, rnaSampleType2) == 0.00);
    }


    @Test
    public void getMaxVolumeLimit() {
        Integer plateSize = 96;
        Integer plateSize2 = 384;

        assertTrue(autoHelper.getMaxVolumeLimit(plateSize) == 150.00);
        assertTrue(autoHelper.getMaxVolumeLimit(plateSize2) == 40.00);
    }

    @Test
    public void getAdapterRowPosition() throws NotFound, RemoteException {
        String wellPosition = "A01";
        String wellPosition1 = "C12";

        assertTrue(autoHelper.getAdapterRowPosition(wellPosition).equals("A"));
        assertTrue(autoHelper.getAdapterRowPosition(wellPosition1).equals("C"));
    }

    @Test
    public void getAdapterColPosition() {
        String wellPosition = "A01";
        String wellPosition1 = "C12";
        assertTrue(autoHelper.getAdapterColPosition(wellPosition).equals("01"));
        assertTrue(autoHelper.getAdapterColPosition(wellPosition1).equals("12"));
    }

    @Test
    public void isOddValue() {
        int val1 = 3;
        int val2 = 33;
        int val3 = 4;
        int val4 = 14;
        assertTrue(autoHelper.isOddValue(val1));
        assertTrue(autoHelper.isOddValue(val2));
        assertFalse(autoHelper.isOddValue(val3));
        assertFalse(autoHelper.isOddValue(val4));

    }
}