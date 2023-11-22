package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

class QualityControlData {
    private String sampleDescription;
    private int fromBp;
    private int toBp;
    private double concentration;
    private double fractionVal;
    private String observation;
    private double calibratedConcentration;
    private double integratedArea;
    private int sizeBp;

    QualityControlData(String sampleDescription, int fromBp, int toBp, double concentration, double fractionVal, String observation) {
        this.sampleDescription = sampleDescription;
        this.fromBp = fromBp;
        this.toBp = toBp;
        this.concentration = concentration;
        this.fractionVal = fractionVal;
        this.observation = observation;
    }
    QualityControlData(String sampleDescription, int fromBp, int toBp, double calibratedConcentration, double integratedArea, String observation, int sizeBp) {
        this.sampleDescription = sampleDescription;
        this.fromBp = fromBp;
        this.toBp = toBp;
        this.calibratedConcentration = calibratedConcentration;
        this.integratedArea = integratedArea;
        this.observation = observation;
        this.sizeBp = sizeBp;
    }

    public String getSampleDescription() {
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

    public int getToBp() {
        return toBp;
    }

    private void setToBp(int toBp) {
        this.toBp = toBp;
    }

    double getConcentration() {
        return concentration;
    }

    private void setConcentration(double concentration) {
        this.concentration = concentration;
    }

    public double getTotalFraction() {
        return fractionVal;
    }

    public void setTotalFraction(double totalFraction) {
        this.fractionVal = totalFraction;
    }

    public double getFractionVal() {
        return fractionVal;
    }

    public void setFractionVal(double fractionVal) {
        this.fractionVal = fractionVal;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public void setCalibratedConcentration(double calibratedConcentration) { this.calibratedConcentration = calibratedConcentration; }

    public double getCalibratedConcentration() {return calibratedConcentration; }

    public void setIntegratedArea(double integratedArea) { this.integratedArea = integratedArea; }
    public double getIntegratedArea() {return integratedArea; }
    public int getSizeBp() { return sizeBp; }

    public void setSizeBp(int sizeBp) { this.sizeBp = sizeBp; }

    @Override
    public String toString() {
        return "QualityControlData{" +
                "sampleDescription='" + sampleDescription + '\'' +
                ", fromBp=" + fromBp +
                ", toBp=" + toBp +
                ", concentration=" + concentration +
                ", fractionVal=" + fractionVal +
                ", observation='" + observation +
                ", sizeBp=" + sizeBp + '\'' +
                '}';
    }

}
