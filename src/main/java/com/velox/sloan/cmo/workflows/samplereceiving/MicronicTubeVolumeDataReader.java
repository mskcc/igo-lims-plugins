package com.velox.sloan.cmo.workflows.samplereceiving;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class MicronicTubeVolumeDataReader implements MicronicTubeWeightFileDataValidator {
    @Override
    public boolean isValidHeader(String[] fileData) {
        String[] headerRow = fileData[0].split(",");
        return headerRow[0].equals("Rack") && headerRow[1].equals("Tube") && headerRow[2].equals("Barcode") && headerRow[3].equals("Weight");
    }

    @Override
    public boolean rowInFileHasValues(String row) {
        String[] cellValues = row.split(",");
        return cellValues.length == 4 && !(StringUtils.isEmpty(cellValues[0]) || StringUtils.isEmpty(cellValues[1]) || StringUtils.isEmpty(cellValues[2]) || StringUtils.isEmpty(cellValues[3]));
    }

    @Override
    public boolean allRowsHaveValidData(String[] fileData) {
        int rowNumForFirstRowWithValues = 1;
        for (int rowNum = rowNumForFirstRowWithValues; rowNum <= fileData.length - 1; rowNum++) {
            String row = fileData[rowNum];
            double weight = Double.parseDouble(row.split(",")[3]);
            if (!rowInFileHasValues(row) || weight < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Map<String, Integer> getHeaderValues(String[] fileData) {
        Map<String, Integer> headerColumnPositions = new HashMap<>();
        String[] headerData = fileData[0].split(",");
        for (int i = 0; i < headerData.length; i++) {
            headerColumnPositions.put(headerData[i], i);
        }
        return headerColumnPositions;
    }

    @Override
    public String getColumnPosition(String row, Map<String, Integer> header) {
        if (Integer.parseInt(row.split(",")[header.get("Tube")].substring(1)) < 10
                && Integer.parseInt(row.split(",")[header.get("Tube")].substring(1)) > 0) {
            return Character.toString(row.split(",")[header.get("Tube")].substring(1).charAt(1));
        } else {
            return row.split(",")[header.get("Tube")].substring(1);
        }
    }
}
