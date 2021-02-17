package com.velox.sloan.cmo.workflows.micronics;

import java.util.List;
import java.util.Map;

/**
 * Interface to Read and validate Micronic Tube data to enter into LIMS:
 * Empty Micronic Tubes are weighed and their weight should be stored in LIMS to automatically calculate
 * Sample volume based on the data from the volume reader output file.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public interface MicronicTubeTareWeightDataValidator {

    boolean isCsvFile(String file);

    boolean dataFileHasValidHeader(String[] fileData);

    boolean dataFileHasValidData(String[] fileData);

    boolean rowsInFileHasValidData(String[] fileData);

    List<String> getMicronicTubeBarcodesFromTubeRecords(List<Map<String, Object>> micronicTubeRecords);

    List<String> getDuplicateBarcodesInExistingBarcodes(List<String> existingBarcodesList, List<String> newBarcodesList);

    List<String> getDuplicateValuesInNewBarcodesList(List<String> newBarcodesList);

}
