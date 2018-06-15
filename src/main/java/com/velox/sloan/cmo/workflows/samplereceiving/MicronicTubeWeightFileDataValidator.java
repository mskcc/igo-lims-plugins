package com.velox.sloan.cmo.workflows.samplereceiving;

import java.util.Map;

public interface MicronicTubeWeightFileDataValidator {

    boolean rowInFileHasValues(String row);

    boolean allRowsHaveValidData(String[] fileData);

    Map<String, Integer> getHeaderValues(String[] fileData);

    String getColumnPosition(String row, Map<String, Integer> header);
}
