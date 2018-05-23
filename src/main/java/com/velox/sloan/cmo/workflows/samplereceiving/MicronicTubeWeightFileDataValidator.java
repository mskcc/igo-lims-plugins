package com.velox.sloan.cmo.workflows.samplereceiving;

import java.util.Map;

public interface MicronicTubeWeightFileDataValidator {
    boolean isValidFile(String fileName);

    boolean fileHasData(String[] fileData);

    boolean isValidHeader(String[] fileData);

    boolean rowInFileHasValues(String row);

    boolean allRowsHaveValidData(String[] fileData);

    Map<String, Integer> getHeaderValues(String[] fileData);

    String getColumnPosition(String row, Map<String, Integer> header);
}
