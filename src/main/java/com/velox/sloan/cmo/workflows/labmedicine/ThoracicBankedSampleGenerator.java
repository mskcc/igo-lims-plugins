package com.velox.sloan.cmo.workflows.labmedicine;

import org.apache.poi.ss.usermodel.Sheet;

import java.util.List;
import java.util.Map;

/**
 * Interface for reading Thoracic Banked Sample data.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public interface ThoracicBankedSampleGenerator {
    String generateNewIdForThoracicBankedSample();

    String compareIdToExistingIdsAndReturnUniqueId(List<String> existingUuids);

    boolean isValidExcelFile(String fileName);

    boolean excelFileHasData(Sheet sheet);

    boolean excelFileHasValidHeader(Sheet sheet, List<String> expectedHeaderValues, String fileName);

    Map<String, Integer> parseExcelFileHeader(Sheet sheet, List<String> headerValues);

    List<Map<String, Object>> readThoracicBankedSampleRecordsFromFile(Sheet sheet, Map<String, Integer> fileHeader, List<String> existingUuids);
}
