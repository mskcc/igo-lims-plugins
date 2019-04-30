package com.velox.sloan.cmo.workflows.IgoLimsPluginUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Cell;

import java.io.*;
import java.util.*;

/**
 * This class will contain all the common methods which are often used repeatedly across different plugins.
 */
public class IgoLimsPluginUtils {

    public boolean isCsvFile(String fileName) {
        return fileName.toLowerCase().endsWith(".csv");
    }

    public List<String> readDataFromCsvFile(byte[] fileContent) throws IOException {
        List<String> rowDataValues = new ArrayList<>();
        InputStream dataStream = new ByteArrayInputStream(fileContent);
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(dataStream))) {
            String temp;
            while ((temp = fileReader.readLine()) != null) {
                String rowData;
                rowData = temp;
                rowDataValues.add(rowData);
            }
        }
        return rowDataValues;
    }

    public boolean csvFileHasData(List<String> fileData) {
        return fileData.size() > 1;
    }

    public boolean csvFileHasValidHeader(List<String> fileData, List<String> expectedHeaderValues) {
        return Arrays.asList(fileData.get(0).split(",")).equals(expectedHeaderValues);
    }

    public boolean csvFileContainsRequiredHeaders(List<String> fileData, List<String> expectedHeaderValues){
        return Arrays.asList(fileData.get(0).split(",")).containsAll(expectedHeaderValues);
    }

    public String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }

    public Map<String, Integer> getCsvHeaderValueMap(List<String> fileData) {
        List<String> headerRow = Arrays.asList(fileData.get(0).split(","));
        Map<String, Integer> headerValues = new HashMap<>();
        for (String value : headerRow) {
            headerValues.put(value, headerRow.indexOf(value));
        }
        return headerValues;
    }

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

    public String getPlateWellRowPosition(String plateWellPosition) {
        return String.valueOf(plateWellPosition.charAt(0));
    }

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
            headerValuesMap.put(cell.getStringCellValue(), i);
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
        if (excelFileName.toLowerCase().endsWith("xlsx") || excelFileName.toLowerCase().endsWith("xls")) {
            return true;
        }
        return false;
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
