package com.velox.sloan.cmo.workflows.labmedicine;

import com.velox.util.DateFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Class to read Thoracic Banked Sample data from excel file and eneter into LIMS with new UUID and unique values.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class ThoracicBankedSampleDataReader implements ThoracicBankedSampleGenerator {

    /**
     * Method to generate a random Alphanumeric ID for new Banked Samp to be stored in LIMS.
     * @return unique ID.
     */
    @Override
    public String generateNewIdForThoracicBankedSample() {
        Random random = new Random();
        int uuidSubstringLength = 6;
        String alphabets = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int lengthOfAlphabets = alphabets.length();
        StringBuilder uuidCharacterString = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            uuidCharacterString.append(alphabets.charAt(random.nextInt(lengthOfAlphabets)));
        }
        String time = Long.toString(System.nanoTime());
        return String.format("%s%s%s", uuidCharacterString, time.substring(time.length() - uuidSubstringLength), alphabets.charAt(random.nextInt(lengthOfAlphabets)));
    }

    /**
     * Method to generate only unique IDs. If a generated unique ID exists in LIMS then it is skipped and a new ID is generated.
     * @param existingUuids
     * @return unique ID
     */
    @Override
    public String compareIdToExistingIdsAndReturnUniqueId(List<String> existingUuids) {
        String uniqueId = "";
        if (!existingUuids.isEmpty()) {
            do {
                uniqueId = generateNewIdForThoracicBankedSample();
            } while (existingUuids.contains(uniqueId));
        } else {
            uniqueId = generateNewIdForThoracicBankedSample();
        }
        return uniqueId;
    }

    /**
     * Method to valid if the excel file has valid extension.
     * @param fileName
     * @return true/false
     */
    @Override
    public boolean isValidExcelFile(String fileName) {
        return fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls");
    }

    /**
     * Method to check if excel file has row data other than header row.
     * @param sheet
     * @return true/false
     */
    @Override
    public boolean excelFileHasData(Sheet sheet) {
        return sheet.getLastRowNum() > 1;
    }

    /**
     * Method to check if excel file has valid Header values.
     * @param sheet
     * @param expectedHeaderValues
     * @param fileName
     * @return true/false
     */
    @Override
    public boolean excelFileHasValidHeader(Sheet sheet, List<String> expectedHeaderValues, String fileName) {
        Row row = sheet.getRow(0);
        List<String> headerValuesInFile = new ArrayList<>();
        for (String value : expectedHeaderValues) {
            for (Cell cell : row) {
                if (value.equals(cell.getStringCellValue())) {
                    headerValuesInFile.add(cell.getStringCellValue());
                }
            }
        }
        return headerValuesInFile.containsAll(expectedHeaderValues);
    }

    /**
     * Method to parse header values from the excel file.
     * @param sheet
     * @param headerValues
     * @return Map of Header value and Index position
     */
    @Override
    public Map<String, Integer> parseExcelFileHeader(Sheet sheet, List<String> headerValues) {
        Map<String, Integer> headerNames = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        for (String value : headerValues) {
            for (Cell cell : headerRow) {
                if (value.equals(cell.getStringCellValue().trim())) {
                    headerNames.put(cell.getStringCellValue(), cell.getColumnIndex());
                }
            }
        }
        return headerNames;
    }

    /**
     * Method to read and create Thoracic Banked Sample records from excel file.
     * @param sheet
     * @param fileHeader
     * @param existingUuids
     * @return Thoracic Banked Sample records.
     */
    @Override
    public List<Map<String, Object>> readThoracicBankedSampleRecordsFromFile(Sheet sheet, Map<String, Integer> fileHeader, List<String> existingUuids) {
        List<Map<String, Object>> thoracicBankSampleRecords = new ArrayList<>();
        int firstRowAfterHeaderRowWithData = 1;
        for (int rowNum = firstRowAfterHeaderRowWithData; rowNum < sheet.getPhysicalNumberOfRows(); rowNum++) {
            Map<String, Object> newThoracicSampleRecord = new HashMap<>();
            Row row = sheet.getRow(rowNum);
            newThoracicSampleRecord.put("Uuid", compareIdToExistingIdsAndReturnUniqueId(existingUuids));
            newThoracicSampleRecord.put("AccessionNumber", row.getCell(fileHeader.get("Accession#")).getStringCellValue());
            newThoracicSampleRecord.put("NumberOfTubes", row.getCell(fileHeader.get("#ofTubes")).getNumericCellValue());
            newThoracicSampleRecord.put("TubeType", row.getCell(fileHeader.get("TubeType")).getStringCellValue());
            newThoracicSampleRecord.put("AliquotNumber", row.getCell(fileHeader.get("Aliquot#")).getStringCellValue());
            newThoracicSampleRecord.put("SpecimenType", row.getCell(fileHeader.get("SpecimenType")).getStringCellValue());
            newThoracicSampleRecord.put("DrawDate", DateFormatter.formatDate(row.getCell(fileHeader.get("DrawDate")).getDateCellValue()));
            newThoracicSampleRecord.put("Pi", row.getCell(fileHeader.get("Pi")).getStringCellValue());
            newThoracicSampleRecord.put("DrawTime", new SimpleDateFormat("HH:mm:ss").format(row.getCell(fileHeader.get("DrawTime")).getDateCellValue()));
            newThoracicSampleRecord.put("BoxDate", DateFormatter.formatDate(row.getCell(fileHeader.get("BoxDate")).getDateCellValue()));
            newThoracicSampleRecord.put("Comments", row.getCell(fileHeader.get("Comments")).getStringCellValue());
            newThoracicSampleRecord.put("ExemplarSampleStatus", "Received");
            thoracicBankSampleRecords.add(newThoracicSampleRecord);
        }
        return thoracicBankSampleRecords;
    }
}
