package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

public class TapeStationData {
    private String sampleDescription;
    private int fromBp;
    private int toBp;
    private double averageSize;
    private double concentration;

    TapeStationData(String sampleDescription, int fromBp, int toBp, double averageSize, double concentration){
        this.sampleDescription = sampleDescription;
        this.fromBp = fromBp;
        this.toBp = toBp;
        this.averageSize = averageSize;
        this.concentration = concentration;
    }

    private String getSampleDescription() {
        return sampleDescription;
    }

    private void setSampleDescription(String sampleDescription) {
        this.sampleDescription = sampleDescription;
    }

    int getFromBp() {
        return fromBp;
    }

    private void setFromBp(int fromBp) {
        this.fromBp = fromBp;
    }

    private int getToBp() {
        return toBp;
    }

    private void setToBp(int toBp) {
        this.toBp = toBp;
    }

    private double getAverageSize() {
        return averageSize;
    }

    private void setAverageSize(double averageSize) {
        this.averageSize = averageSize;
    }

    double getConcentration() {
        return concentration;
    }

    private void setConcentration(double concentration) {
        this.concentration = concentration;
    }
}
