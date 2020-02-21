package com.velox.sloan.cmo.workflows.IgoLimsPluginUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.util.*;

/**
 * This class will contain all the common methods which are often used repeatedly across different plugins.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class IgoLimsPluginUtils {

    /**
     * Method to check if a file has .csv extension
     *
     * @param fileName
     * @return true/false
     */
    public boolean isCsvFile(String fileName) {
        return fileName.toLowerCase().endsWith(".csv");
    }

    public List<String> readDataFromCsvFile(byte[] fileContent) throws IOException {
        List<String> rowDataValues = new ArrayList<>();
        InputStream dataStream = new ByteArrayInputStream(fileContent);
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(dataStream))) {
            String temp;
            // || !temp.replace(",", "").equals(null) || !temp.replace(",", "").equals("")
            while ((temp = fileReader.readLine()) != null && !temp.replace(",", "").equals(null)) { //to check that there are no empty lines at end of file
                String rowData;
                rowData = temp;
                rowDataValues.add(rowData);
            }
        }
        return rowDataValues;
    }

    /**
     * Method to check if file has data other than header row
     *
     * @param fileData
     * @return true/false
     */
    public boolean csvFileHasData(List<String> fileData) {
        return fileData.size() > 1;
    }

    /**
     * Method to check if csv file has valid header row values
     *
     * @param fileData
     * @param expectedHeaderValues
     * @return true/false
     */
    public boolean csvFileHasValidHeader(List<String> fileData, List<String> expectedHeaderValues) {
        return Arrays.asList(fileData.get(0).split(",")).equals(expectedHeaderValues);
    }

    /**
     * Method to check if csv file header contains the values that are required.
     *
     * @param fileData
     * @param expectedHeaderValues
     * @return true/false
     */
    public boolean csvFileContainsRequiredHeaders(List<String> fileData, List<String> expectedHeaderValues) {
        return Arrays.asList(fileData.get(0).split(",")).containsAll(expectedHeaderValues);
    }

    /**
     * Method to concatenate List of string separated by new line character '\n'.
     *
     * @param listWithValues
     * @return String of values separated in new lines
     */
    public String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }


    /**
     * Method to concatenate List of string separated by comma.
     *
     * @param listWithValues
     * @return String of values separated by comma
     */
    public String convertListToCommaSeparatedString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, ",");
    }

    /**
     * Method to get Map of Header values and their Index position.
     *
     * @param fileData
     * @return Map of Header value and Index position.
     */
    public Map<String, Integer> getCsvHeaderValueMap(List<String> fileData) {
        List<String> headerRow = Arrays.asList(fileData.get(0).split(","));
        Map<String, Integer> headerValues = new HashMap<>();
        for (String value : headerRow) {
            headerValues.put(value.trim(), headerRow.indexOf(value));
        }
        return headerValues;
    }

    /**
     * Method to validate that a row in csv file has all the values that are required.
     *
     * @param rowData
     * @param requiredCsvFileColumnHeaders
     * @param headerValues
     * @return true/false
     */
    public boolean rowInCsvFileHasRequiredValues(String rowData, List<String> requiredCsvFileColumnHeaders, Map<String, Integer> headerValues) {
        List<String> rowValues = Arrays.asList(rowData.split(","));
        if (!rowValues.isEmpty()) {
            for (String value : requiredCsvFileColumnHeaders) {
                if (rowValues.size() < requiredCsvFileColumnHeaders.size() || StringUtils.isEmpty(rowValues.get(headerValues.get(value)))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Method to validate that all the rows in csv file has required values.
     *
     * @param fileData
     * @param requiredCsvFileColumnHeaders
     * @return true/false
     */
    public boolean allRowsInCsvFileHasValidData(List<String> fileData, List<String> requiredCsvFileColumnHeaders) {
        Map<String, Integer> headerValues = getCsvHeaderValueMap(fileData);
        int firstRowPositionAfterHeaderRow = 1;
        for (int i = firstRowPositionAfterHeaderRow; i <= fileData.size() - 1; i++) {
            if (!rowInCsvFileHasRequiredValues(fileData.get(i), requiredCsvFileColumnHeaders, headerValues)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method to get the Row position from given well position
     *
     * @param plateWellPosition
     * @return row position
     */
    public String getPlateWellRowPosition(String plateWellPosition) {
        return String.valueOf(plateWellPosition.charAt(0));
    }

    /**
     * Method to get the Column position from the given well position.
     *
     * @param plateWellPosition
     * @return column position
     */
    public String getPlateWellColumnPosition(String plateWellPosition) {
        if (Integer.parseInt(plateWellPosition.substring(1)) < 10) {
            return String.valueOf(plateWellPosition.charAt(2));
        }
        return plateWellPosition.substring(1);
    }

    /**
     * Method to get all the data rows from Excel File.
     *
     * @param inputData
     * @return Row data from excel file
     * @throws IOException
     */
    public List<Row> getExcelSheetDataRows(byte[] inputData) throws IOException, InvalidFormatException {
        InputStream file = new ByteArrayInputStream(inputData);
        Workbook workbook = WorkbookFactory.create(file);
        Sheet sheet = workbook.getSheetAt(0);
        List<Row> dataRows = new ArrayList<>();
        for (int rowNum = 0; rowNum < sheet.getPhysicalNumberOfRows(); rowNum++) {
            dataRows.add(sheet.getRow(rowNum));
        }
        return dataRows;
    }

    /**
     * Method to create HashMap of Header values as key and their index as value.
     *
     * @param rowData
     * @return header values and positions.
     */
    public HashMap<String, Integer> getHeaderValuesMapFromExcelRowData(List<Row> rowData) {
        HashMap<String, Integer> headerValuesMap = new HashMap<>();
        Row row = rowData.get(0);
        int i = 0;
        for (Cell cell : row) {
            headerValuesMap.put(cell.getStringCellValue().trim(), i);
            i++;
        }
        return headerValuesMap;
    }

    /**
     * Method to validate if excel file has valid extension.
     *
     * @param excelFileName
     * @return true/false
     */
    public boolean isValidExcelFile(String excelFileName) {
        return excelFileName.toLowerCase().endsWith("xlsx") || excelFileName.toLowerCase().endsWith("xls");
    }

    /**
     * Method to validate if excel file has valid Header values when compared to expected header values.
     *
     * @param dataRows
     * @param expectedHeaderValues
     * @return true/false
     */
    public boolean excelFileHasValidHeader(List<Row> dataRows, List<String> expectedHeaderValues) {
        Row headerRow = dataRows.get(0);
        List<String> fileHeaderValues = new ArrayList<>();
        int i = 0;
        for (Cell cell : headerRow) {
            fileHeaderValues.add(cell.getStringCellValue());
        }
        return fileHeaderValues.containsAll(expectedHeaderValues);
    }

    /**
     * Method to validate if the excel file has data.
     *
     * @param dataRows
     * @return true/false
     */
    public boolean excelFileHasData(List<Row> dataRows) {
        return dataRows.size() > 1;
    }
}
