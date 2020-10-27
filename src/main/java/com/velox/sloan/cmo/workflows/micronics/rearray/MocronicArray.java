package com.velox.sloan.cmo.workflows.micronics.rearray;

import java.util.HashMap;
import java.util.Map;

public class MocronicArray {

    public Object sampleId;
    public Object otherSampleId;
    public Object altId;
    public Object micronicBarcode;
    public Object sourceRack;
    public Object sourceRow;
    public Object sourceCol;
    public Object destinationRack;
    public Object destinationRow;
    public Object destinationCol;
    public Object robotRackId;
    public Object robotRow;
    public Object robotCol;

    public MocronicArray(Object sampleId, Object otherSampleId, Object altId, Object micronicBarcode, Object sourceRack,
                         Object sourceRow, Object sourceCol, Object destinationRack, Object destinationRow,
                         Object destinationCol, Object robotRackId, Object robotRow, Object robotCol) {
        this.sampleId = sampleId;
        this.otherSampleId = otherSampleId;
        this.altId = altId;
        this.micronicBarcode = micronicBarcode;
        this.sourceRack = sourceRack;
        this.sourceRow = sourceRow;
        this.sourceCol = sourceCol;
        this.destinationRack = destinationRack;
        this.destinationRow = destinationRow;
        this.destinationCol = destinationCol;
        this.robotRackId = robotRackId;
        this.robotRow = robotRow;
        this.robotCol = robotCol;
    }

    public Object getSampleId() {
        return sampleId;
    }

    public void setSampleId(Object sampleId) {
        this.sampleId = sampleId;
    }

    public Object getOtherSampleId() {
        return otherSampleId;
    }

    public void setOtherSampleId(Object otherSampleId) {
        this.otherSampleId = otherSampleId;
    }

    public Object getAltId() {
        return altId;
    }

    public void setAltId(Object altId) {
        this.altId = altId;
    }

    public Object getMicronicBarcode() {
        return micronicBarcode;
    }

    public void setMicronicBarcode(Object micronicBarcode) {
        this.micronicBarcode = micronicBarcode;
    }

    public Object getSourceRack() {
        return sourceRack;
    }

    public void setSourceRack(Object sourceRack) {
        this.sourceRack = sourceRack;
    }

    public Object getSourceRow() {
        return sourceRow;
    }

    public void setSourceRow(Object sourceRow) {
        this.sourceRow = sourceRow;
    }

    public Object getSourceCol() {
        return sourceCol;
    }

    public void setSourceCol(Object sourceCol) {
        this.sourceCol = sourceCol;
    }

    public Object getDestinationRack() {
        return destinationRack;
    }

    public void setDestinationRack(Object destinationRack) {
        this.destinationRack = destinationRack;
    }

    public Object getDestinationRow() {
        return destinationRow;
    }

    public void setDestinationRow(Object destinationRow) {
        this.destinationRow = destinationRow;
    }

    public Object getDestinationCol() {
        return destinationCol;
    }

    public void setDestinationCol(Object destinationCol) {
        this.destinationCol = destinationCol;
    }

    public Object getRobotRackId() {
        return robotRackId;
    }

    public void setRobotRackId(Object robotRackId) {
        this.robotRackId = robotRackId;
    }

    public Object getRobotRow() {
        return robotRow;
    }

    public void setRobotRow(Object robotRow) {
        this.robotRow = robotRow;
    }

    public Object getRobotCol() {
        return robotCol;
    }

    public void setRobotCol(Object robotCol) {
        this.robotCol = robotCol;
    }

    @Override
    public String toString() {
        return "MocronicArray{" +
                "sampleId=" + sampleId +
                ", otherSampleId=" + otherSampleId +
                ", altId=" + altId +
                ", micronicBarcode=" + micronicBarcode +
                ", sourceRack=" + sourceRack +
                ", sourceRow=" + sourceRow +
                ", sourceCol=" + sourceCol +
                ", destinationRack=" + destinationRack +
                ", destinationRow=" + destinationRow +
                ", destinationCol=" + destinationCol +
                ", robotRackId=" + robotRackId +
                ", robotRow=" + robotRow +
                ", robotCol=" + robotCol +
                '}';
    }

    /**
     * Method to get HashMap of object.
     * @return
     */
    public Map<String, Object> getMap(){
        Map<String, Object> map = new HashMap<>();
        map.put("SampleId", sampleId);
        map.put("OtherSampleId", otherSampleId);
        map.put("AltId", altId);
        map.put("MicronicTubeBarcode", micronicBarcode);
        map.put("StorageLocationBarcode", sourceRack);
        map.put("RowPosition", sourceRow);
        map.put("ColPosition", sourceCol);
        map.put("TargetMicronicRackBarcode", destinationRack);
        map.put("TargetRowPosition", destinationRow);
        map.put("TargetColPosition", destinationCol);
        map.put("RackBarcodeRobot", robotRackId);
        map.put("RowRobot", robotRow);
        map.put("ColumnRobot", robotCol);
        return map;
    }
}
