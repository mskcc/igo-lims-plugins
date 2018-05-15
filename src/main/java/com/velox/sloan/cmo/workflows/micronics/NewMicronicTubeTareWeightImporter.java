package com.velox.sloan.cmo.workflows.micronics;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class NewMicronicTubeTareWeightImporter implements MicronicTubeTareWeightFileReader, MicronicTubeTareWeightDataValidator {

    @Override
    public boolean isCsvFile(String file) {
        return (file.endsWith(".csv"));
    }

    @Override
    public boolean dataFileHasValidData(String[] fileData) {
        return fileData.length > 1;
    }

    @Override
    public boolean dataFileHasValidHeader(String[] fileData) {
        String[] headerRow = fileData[0].split(",");
        if (headerRow.length < 4) {
            return false;
        }
        return (headerRow[0].equals("Rack") && headerRow[1].equals("Tube") && headerRow[2].equals("Barcode") && headerRow[3].equals("Weight"));
    }

    @Override
    public boolean rowsInFileHasValidData(String[] fileData) {
        int firstRowAfterHeader = 1;
        for (int rowNum = firstRowAfterHeader; rowNum <= fileData.length - 1; rowNum++) {
            if (StringUtils.isEmpty(fileData[rowNum].split(",")[2]) || StringUtils.isEmpty(fileData[rowNum].split(",")[3])
                    || Double.parseDouble(fileData[rowNum].split(",")[3]) < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Map<String, Object>> readNewTubeRecordsFromFileData(String[] fileData) {
        List<Map<String, Object>> micronicTubes = new ArrayList<>();
        int firstRowAfterHeader = 1;
        for (int rowNum = firstRowAfterHeader; rowNum <= fileData.length - 1; rowNum++) {
            String rowData = fileData[rowNum];
            Map<String, Object> newMicronicTube = new HashMap<>();
            newMicronicTube.put("MicronicTubeBarcode", rowData.split(",")[2]);
            newMicronicTube.put("MicronicTubeWeight", Double.parseDouble(rowData.split(",")[3]));
            micronicTubes.add(newMicronicTube);
        }
        return micronicTubes;
    }

    @Override
    public List<String> getMicronicTubeBarcodesFromTubeRecords(List<Map<String, Object>> micronicTubeRecords) {
        List<String> newMicronicTubeBarcodes = new ArrayList<>();
        for (Map<String, Object> record : micronicTubeRecords) {
            newMicronicTubeBarcodes.add((String) record.get("MicronicTubeBarcode"));
        }
        return newMicronicTubeBarcodes;
    }

    @Override
    public List<String> getDuplicateBarcodesInExistingBarcodes(List<String> existingTubeBarcodesList, List<String> newTubeBarcodesList) {
        List<String> commonBarcodes = new ArrayList<>();
        if (existingTubeBarcodesList.size() > 0) {
            for (String newBarcode : newTubeBarcodesList) {
                if (existingTubeBarcodesList.contains(newBarcode)) {
                    commonBarcodes.add(newBarcode);
                }
            }
        }
        return commonBarcodes;
    }

    @Override
    public List<String> getDuplicateValuesInNewBarcodesList(List<String> newBarcodesList) {
        Set<String> testingSet = new HashSet<>();
        List<String> duplicateBarcodesList = new ArrayList<>();
        if (newBarcodesList.size() > 0) {
            for (String barcode : newBarcodesList) {
                if (!testingSet.add(barcode)) {
                    duplicateBarcodesList.add(barcode);
                }
            }
        }
        return duplicateBarcodesList;
    }
}
