package com.velox.sloan.cmo.workflows.micronics;

import java.util.List;
import java.util.Map;

public interface MicronicTubeTareWeightDataValidator {

    public boolean isCsvFile(String file);

    public boolean dataFileHasValidHeader(String[] fileData);

    public boolean dataFileHasValidData(String[] fileData);

    public boolean rowsInFileHasValidData(String[] fileData);

    public List<String> getMicronicTubeBarcodesFromTubeRecords(List<Map<String, Object>> micronicTubeRecords);

    public List<String> getDuplicateBarcodesInExistingBarcodes(List<String> existingBarcodesList, List<String> newBarcodesList);

    public List<String> getDuplicateValuesInNewBarcodesList(List<String> newBarcodesList);

}
