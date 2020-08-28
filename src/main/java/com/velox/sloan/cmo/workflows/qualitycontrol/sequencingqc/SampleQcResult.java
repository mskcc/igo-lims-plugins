package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import java.util.HashMap;
import java.util.Map;

public class SampleQcResult {
    private String sampleDescription;
    private double quantity;
    private double adapterPercentage;
    private double percentfragmentsUpTo1kb;
    private double percentFragmentsLargerThan1kb;
    private boolean isUserLibrary;
    private final String PASSED = "Passed";
    private final String TRY = "Try";
    private final String FAILED = "Failed";
    private String igoRecommendation;

    public SampleQcResult( String sampleDescription, double quantity, double adapterPercentage, double fragmentsLargerThan1kb, boolean isUserLibrary){
        this.sampleDescription = sampleDescription;
        this.quantity = quantity;
        this.adapterPercentage = adapterPercentage;
        this.percentFragmentsLargerThan1kb = fragmentsLargerThan1kb;
        this.percentfragmentsUpTo1kb = getPercentFragmentsUpTo1kb();
        this.isUserLibrary = isUserLibrary;
        this.igoRecommendation = getIgoRecommendationAnnotation();
    }

    /**
     * get sampleDescription value
     * @return
     */
    public String getSampleDescription() {
        return sampleDescription;
    }

    /**
     * set sampleDescription value
     * @param sampleDescription
     */
    public void setSampleDescription(String sampleDescription) {
        this.sampleDescription = sampleDescription;
    }

    /**
     * get quantity from SampleQcResult
     */
    public double getQuantity() {
        return quantity;
    }

    /**
     * Set quantity value.
     * @param quantity
     */
    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    /**
     * get adapterPercentage value
     * @return
     */
    public double getAdapterPercentage() {
        return adapterPercentage;
    }

    /**
     * set adapterPercentage value
     * @param adapterPercentage
     */
    public void setAdapterPercentage(double adapterPercentage) {
        this.adapterPercentage = adapterPercentage;
    }

    /**
     * get percentfragmentsUpTo1kb value
     * @return
     */
    public double getPercentFragmentsUpTo1kb() {
        return Math.abs(100.0 - (this.adapterPercentage + this.percentFragmentsLargerThan1kb));
    }

    /**
     * set percentfragmentsUpTo1kb value
     * @return
     */
    public void setPercentFragmentsUpTo1kb(double fragmentsUpTo1kb) {
        this.percentfragmentsUpTo1kb = fragmentsUpTo1kb;
    }

    /**
     * get fragmentsLargerThan1kb value
     * @return
     */
    public double getPercentFragmentsLargerThan1kb() {
        return percentFragmentsLargerThan1kb;
    }

    /**
     * set fragmentsLargerThan1kb value
     * @param fragmentsLargerThan1kb
     */
    public void setPercentFragmentsLargerThan1kb(double fragmentsLargerThan1kb) {
        this.percentFragmentsLargerThan1kb = fragmentsLargerThan1kb;
    }

    /**
     * set isUserLibrary value
     * @return
     */
    public boolean isUserLibrary() {
        return isUserLibrary;
    }

    /**
     * get isUserLibrary value
     * @param userLibrary
     */
    public void setUserLibrary(boolean userLibrary) {
        isUserLibrary = userLibrary;
    }

    /**
     * Method to check quality
     * @return
     */
    private String getQuantityCheck(){
        if ((!this.isUserLibrary && this.quantity > 20.0) || (this.isUserLibrary && this.quantity > 60.0)) {
            return PASSED;
        }
        if ((!this.isUserLibrary && this.quantity >= 10.0) || (this.isUserLibrary && this.quantity > 20.0)) {
            return TRY;
        }
        return FAILED;
    }

    /**
     * Method to check adapeterPercentage
     * @return
     */
    private String getAdapterCheck(){
        if ((!this.isUserLibrary && this.adapterPercentage < 1.0) || (this.isUserLibrary && this.adapterPercentage < 0.5)) {
            return PASSED;
        }
        if ((!this.isUserLibrary && this.adapterPercentage <= 2.0) || (this.isUserLibrary && this.adapterPercentage <= 10.0)) {
            return TRY;
        }
        return FAILED;
    }

    /**
     * method to check fragmentsLargerThan1kb
     * @return
     */
    private String getFragmentSizeCheck(){
        if ((!this.isUserLibrary && this.percentFragmentsLargerThan1kb < 10.0) || (this.isUserLibrary && this.percentFragmentsLargerThan1kb < 2.0)) {
            return PASSED;
        }
        if ((!this.isUserLibrary && this.percentFragmentsLargerThan1kb <= 20.0) || (this.isUserLibrary && this.percentFragmentsLargerThan1kb <= 50.0)) {
            return TRY;
        }
        return FAILED;
    }

    /**
     * Method to show overall SampleQcResult/IGO recommendation based on SampleQcResult variables.
     * @return
     */
    String getIgoRecommendationAnnotation(){
        if (getQuantityCheck().equalsIgnoreCase(FAILED) || getAdapterCheck().equalsIgnoreCase(FAILED) || getFragmentSizeCheck().equalsIgnoreCase(FAILED)){
            return FAILED;
        }
        if ((getQuantityCheck().equalsIgnoreCase(TRY) && getAdapterCheck().equalsIgnoreCase(PASSED) && getFragmentSizeCheck().equalsIgnoreCase(PASSED)) ||
                (getQuantityCheck().equalsIgnoreCase(PASSED) && getAdapterCheck().equalsIgnoreCase(TRY) && getFragmentSizeCheck().equalsIgnoreCase(PASSED)) ||
                (getQuantityCheck().equalsIgnoreCase(PASSED) && getAdapterCheck().equalsIgnoreCase(PASSED) && getFragmentSizeCheck().equalsIgnoreCase(TRY)) ||
                (getQuantityCheck().equalsIgnoreCase(PASSED) && getAdapterCheck().equalsIgnoreCase(TRY) && getFragmentSizeCheck().equalsIgnoreCase(TRY)) ||
                (getQuantityCheck().equalsIgnoreCase(TRY) && getAdapterCheck().equalsIgnoreCase(TRY) && getFragmentSizeCheck().equalsIgnoreCase(TRY)) ||
                (getQuantityCheck().equalsIgnoreCase(TRY) && getAdapterCheck().equalsIgnoreCase(TRY) && getFragmentSizeCheck().equalsIgnoreCase(PASSED)) ||
                (getQuantityCheck().equalsIgnoreCase(TRY) && getAdapterCheck().equalsIgnoreCase(PASSED) && getFragmentSizeCheck().equalsIgnoreCase(TRY))
        ){
            return TRY;
        }
        if (getQuantityCheck().equalsIgnoreCase(PASSED) && getAdapterCheck().equalsIgnoreCase(PASSED) && getFragmentSizeCheck().equalsIgnoreCase(PASSED)){
            return PASSED;
        }
       return "";
    }

    @Override
    public String toString() {
        return "SampleQcResult{" +
                "sampleDescription='" + sampleDescription + '\'' +
                ", quantity=" + quantity +
                ", adapterPercentage=" + adapterPercentage +
                ", percentFragmentsLargerThan1kb=" + percentFragmentsLargerThan1kb +
                ", percentFragmentsUpto1kb=" + percentfragmentsUpTo1kb +
                ", isUserLibrary=" + isUserLibrary +
                ", igoRecommendation='" + igoRecommendation + '\'' +
                '}';
    }

    /**
     * Method to return HashMap of SampleQcResult object.
     * @return
     */
    public Map<String, Object> getHashMap(){
        Map<String, Object> map = new HashMap<>();
        map.put("SampleId", this.sampleDescription);
        map.put("AdapterPercentage", this.adapterPercentage);
        map.put("PercentUpto1kb", this.percentfragmentsUpTo1kb);
        map.put("PercentGreaterThan1kb", this.percentFragmentsLargerThan1kb);
        map.put("IsUserLibrary", this.isUserLibrary);
        map.put("igoRecommendation", this.igoRecommendation);
        return map;
    }
}
