package com.velox.sloan.cmo.workflows.micronics;

import java.util.List;
import java.util.Map;

public interface MicronicTubeTareWeightDataValidator {

    boolean isCsvFile(String file);

    boolean dataFileHasValidHeader(String[] fileData);

    boolean dataFileHasValidData(String[] fileData);

    boolean rowsInFileHasValidData(String[] fileData);

    List<String> getMicronicTubeBarcodesFromTubeRecords(List<Map<String, Object>> micronicTubeRecords);

    List<String> getDuplicateBarcodesInExistingBarcodes(List<String> existingBarcodesList, List<String> newBarcodesList);

    List<String> getDuplicateValuesInNewBarcodesList(List<String> newBarcodesList);

}
