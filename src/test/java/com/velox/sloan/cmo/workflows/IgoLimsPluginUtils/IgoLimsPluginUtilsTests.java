package com.velox.sloan.cmo.workflows.IgoLimsPluginUtils;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.*;

public class IgoLimsPluginUtilsTests {
    private List<String> expectedFileHeaderValues = Arrays.asList("RACKID", "TUBE", "SAMPLES", "STATUS", "VOLMED", "VOLAVG", "VOLSTDEV");
    private List<String> headerWithMustHaveValuesInRow = Arrays.asList("RACKID", "TUBE");
    private IgoLimsPluginUtils commonMethods = new IgoLimsPluginUtils();
    private byte[] byteData;
    private byte[] byteDataEmpty;
    private List<String> dataFromFile = new ArrayList<>();
    private List<String> invalidHeaderData;
    private Map<String, Integer> headerValuesMap;

    @Before
    public void setUp() {
        String fileName = "FileReaderTestFileWithData.csv";
        byteData = readCsvFileToBytes(fileName);
        String emptyFile = "FileReaderTestEmpty.csv";
        byteDataEmpty = readCsvFileToBytes(emptyFile);
        String invalidHeaderFile = "UtilsTestInvalidHeader.csv";
        byte[] byteInvalidHeaderData = readCsvFileToBytes(invalidHeaderFile);
        try {
            dataFromFile = commonMethods.readDataFromCsvFile(byteData);
            invalidHeaderData = commonMethods.readDataFromCsvFile(byteInvalidHeaderData);
            headerValuesMap = commonMethods.getCsvHeaderValueMap(dataFromFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void isCsvFile_shouldReturnTrueIfCsvFile() {
        String fileName = "abc.csv";
        String fileNamewithUpperExtension = "xyz.CSV";
        assertTrue(commonMethods.isCsvFile(fileName));
        assertTrue(commonMethods.isCsvFile(fileNamewithUpperExtension));
    }

    @Test
    public void isCsvFile_shouldReturnFalseIfNotCsvFile() {
        String fileName = "abc.xlsx";
        assertFalse(commonMethods.isCsvFile(fileName));
    }

    @Test
    public void readDataFromCsvFile_shouldReturnDataArray() {
        try {
            assertEquals(commonMethods.readDataFromCsvFile(byteData).size(), 10);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void csvFileHasData_shouldReturnTrueIfFileHasData() {
        try {
            assertEquals(commonMethods.readDataFromCsvFile(byteData).size(), 10);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void csvFileHasData_shouldReturnFalseIfFileIsEmpty() {
        try {
            assertEquals(commonMethods.readDataFromCsvFile(byteDataEmpty).size(), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void csvFileHasValidHeader_shouldReturnTrueIfExpectedHeaderPresent() {
        assertTrue(commonMethods.csvFileHasValidHeader(dataFromFile, expectedFileHeaderValues));
    }

    @Test
    public void csvFileHasValidHeader_shouldReturnFalseIfExpectedHeaderNotPresent() {
        assertFalse(commonMethods.csvFileHasValidHeader(invalidHeaderData, expectedFileHeaderValues));
    }

    @Test
    public void getCsvHeaderValueMap_shouldReturnHeaderValues() {
        assertEquals(commonMethods.getCsvHeaderValueMap(dataFromFile).size(), expectedFileHeaderValues.size());
    }

    @Test
    public void csvFileContainsRequiredHeaders_shouldReturnTrueWhenAllValuesAreContained() {
        List<String> headerValues = Arrays.asList("RACKID", "TUBE", "SAMPLES", "STATUS");
        assertTrue(commonMethods.csvFileContainsRequiredHeaders(dataFromFile, headerValues));
    }

    @Test
    public void csvFileContainsRequiredHeaders_shouldReturnFalseWhenAllValuesAreNotContained() {
        List<String> headerValues = Arrays.asList("RACKID", "TUBE", "SAMPLES", "STATUS", "ALPHA", "BETA");// ALPHA, BETA are not contained in the headers
        assertFalse(commonMethods.csvFileContainsRequiredHeaders(dataFromFile, headerValues));
    }

    @Test
    public void rowInCsvFileHasRequiredValues_shouldReturnTrueIfRowHasRequiredValues() {
        String validRowValues = "abc, A1, 0,3,12.5,12.6, 0.22";
        assertTrue(commonMethods.rowInCsvFileHasRequiredValues(validRowValues, headerWithMustHaveValuesInRow, headerValuesMap));
    }

    @Test
    public void rowInCsvFileHasRequiredValues_shouldReturnFalseIfRowIsMissingRequiredValues() {
        String rowWithMissingValues = "abc,, 0,3,,12.6, 0.22";
        assertFalse(commonMethods.rowInCsvFileHasRequiredValues(rowWithMissingValues, headerWithMustHaveValuesInRow, headerValuesMap));
    }

    @Test
    public void allRowsInCsvFileHasValidData_shouldReturnTrueIfAllRowsInFileHasValidData() {
        assertTrue(commonMethods.allRowsInCsvFileHasValidData(dataFromFile, headerWithMustHaveValuesInRow));
    }

    @Test
    public void allRowsInCsvFileHasValidData_shouldReturnFalseIfAnyRowsInFileMissingRequiredData() {
        List<String> rowsWithMissingValues = Arrays.asList("RACKID,TUBE,SAMPLES,STATUS,VOLMED,VOLAVG,VOLSTDEV", "abc1,A1,0,3,12.6,12.6,12.6", "abc2,B1,0.0,3,12.6,12.6,12.6", "abc3,,0.0,3,12.6,12.6,0.22");
        assertFalse(commonMethods.allRowsInCsvFileHasValidData(rowsWithMissingValues, headerWithMustHaveValuesInRow));
    }

    @Test
    public void getPlateWellRowPosition_shouldReturnPlateRowPosition() {
        String plateRowPosition = "A01";
        assertEquals(commonMethods.getPlateWellRowPosition(plateRowPosition), "A");
        assertNotEquals(commonMethods.getPlateWellRowPosition(plateRowPosition), "C");
    }

    @Test
    public void getPlateWellColumnPosition_shouldReturnSingleCharacterStringIfColumnValueLessThanTen() {
        String plateRowPosition = "C09";
        assertEquals(commonMethods.getPlateWellColumnPosition(plateRowPosition), "9");
    }

    @Test
    public void getPlateWellColumnPosition_shouldReturnMultiCharacterStringIfColumnValueGreaterThanTen() {
        String plateRowPosition = "C12";
        assertEquals(commonMethods.getPlateWellColumnPosition(plateRowPosition), "12");
    }

    @Test
    public void getExcelSheetDataRows_shoudReturnData() throws IOException, InvalidFormatException {
        byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignmentTemplate.xlsx");
        List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
        assertEquals(excelData.size(), 28);
    }

    @Test
    public void excelFileHasData_shouldReturnFalseWhenExcelFileEmpty() {
        try {
            byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignment_NoData.xlsx");
            List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
            System.out.print(excelData.size());
            assertFalse(commonMethods.excelFileHasData(excelData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excelFileHasValidHeader_shouldReturnTrueIfHeaderValid() {
        List<String> expectedHeader = Arrays.asList("Sample ID", "Other Sample ID", "AltId", "Assay", "Well", "Plate ID");
        try {
            byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignmentTemplate.xlsx");
            List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
            assertTrue(commonMethods.excelFileHasValidHeader(excelData, expectedHeader));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excelFileHasValidHeader_shouldReturnFalseIfHeaderNotValid() {
        List<String> expectedHeader = Arrays.asList("Sample ID", "Other Sample ID", "AltId", "Assay", "Well", "Plate ID");
        try {
            byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignment_IncorrectHeaders.xlsx");
            List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
            assertFalse(commonMethods.excelFileHasValidHeader(excelData, expectedHeader));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void isValidExcelFile_shouldReturnTrueIfValidExcelFile() {
        String fileName1 = "abc.xlsx";
        String fileName2 = "abc.xls";
        assertTrue(commonMethods.isValidExcelFile(fileName1));
        assertTrue(commonMethods.isValidExcelFile(fileName2));
    }

    @Test
    public void isValidExcelFile_shouldReturnFalseIfNotValidExcelFile() {
        String fileName1 = "abc.csv";
        String fileName2 = "abc.txt";
        assertFalse(commonMethods.isValidExcelFile(fileName1));
        assertFalse(commonMethods.isValidExcelFile(fileName2));
    }

    @Test
    public void excelFileHasData_shouldReturnTrueIfFileHasData() {
        try {
            byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignmentTemplate.xlsx");
            List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
            assertTrue(commonMethods.excelFileHasData(excelData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excelFileHasData_shouldReturnFalseIfFileHasNoData() {
        try {
            byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignment_NoData.xlsx");
            List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
            assertFalse(commonMethods.excelFileHasData(excelData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getHeaderValuesMapFromExcelRowData_shouldReturnHeaderMap() {
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("Sample ID", 0);
        headerMap.put("Other Sample ID", 1);
        headerMap.put("AltId", 2);
        headerMap.put("Assay", 3);
        headerMap.put("Well", 4);
        headerMap.put("Plate ID", 5);
        try {
            byte[] excelByteData = readExcelFileToBytes("ddpcrPlateAssignment_NoData.xlsx");
            List<Row> excelData = commonMethods.getExcelSheetDataRows(excelByteData);
            assertEquals(commonMethods.getHeaderValuesMapFromExcelRowData(excelData), headerMap);
            assertEquals(commonMethods.getHeaderValuesMapFromExcelRowData(excelData).size(), 6);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] readCsvFileToBytes(String fileName) {
        File file = new File(requireNonNull(IgoLimsPluginUtils
                .class.getClassLoader().getResource(fileName)).getPath());
        byte[] bytesArray = new byte[(int) file.length()];
        FileInputStream fileIn;
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(bytesArray);
            fileIn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytesArray;
    }

    private byte[] readExcelFileToBytes(String fileName) {
        File file = new File(requireNonNull(IgoLimsPluginUtils
                .class.getClassLoader().getResource(fileName)).getPath());
        byte[] bytesArray = new byte[(int) file.length()];
        FileInputStream fileIn;
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(bytesArray);
            fileIn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytesArray;
    }
}
