package com.velox.sloan.cmo.workflows.labmedicine;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public class ThoracicBankedSampleDataReaderTests {
    private ThoracicBankedSampleDataReader dataReader = new ThoracicBankedSampleDataReader();
    private List<String> expectedHeaderValues = new ArrayList<>();
    private List<String> existingRecordUuids = new ArrayList<>();
    private Workbook validFileWorkbook;
    private Workbook emptyFileWorkbook;
    private Workbook invalidHeaderWorkbook;
    private Sheet sheetWithValidData;
    private Sheet emptySheet;
    private Sheet sheetWithInvalidHeader;

    @Before
    public void setUp() {

        expectedHeaderValues = Arrays.asList("Accession#", "DrawDate", "DrawTime", "Pi", "TubeType", "#ofTubes", "BoxDate", "SpecimenType", "Aliquot#", "Comments");
        existingRecordUuids = createBulkUuids();
        try {
            validFileWorkbook = WorkbookFactory.create(new File(Objects.requireNonNull(ThoracicBankedSampleDataReaderTests
                    .class.getClassLoader().getResource("Valid_File_test.xlsx")).getPath()));
            emptyFileWorkbook = WorkbookFactory.create(new File(Objects.requireNonNull(ThoracicBankedSampleDataReaderTests
                    .class.getClassLoader().getResource("EmptyFile_Test.xlsx")).getPath()));
            invalidHeaderWorkbook = WorkbookFactory.create(new File(Objects.requireNonNull(ThoracicBankedSampleDataReaderTests
                    .class.getClassLoader().getResource("Invalid_FileHeaders_test.xlsx")).getPath()));
            sheetWithValidData = validFileWorkbook.getSheetAt(0);
            emptySheet = emptyFileWorkbook.getSheetAt(0);
            sheetWithInvalidHeader = invalidHeaderWorkbook.getSheetAt(0);
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void ifNotValidExcelFileExtention_shouldThrowError() {
        String fileName = "abc.xslx";
        assertFalse(dataReader.isValidExcelFile(fileName));
    }

    @Test
    public void ifValidExcelFileExtention_shouldReturnTrue() {
        String fileName = "abc.xlsx";
        assertTrue(dataReader.isValidExcelFile(fileName));
    }

    @Test
    public void generateNewIdForThoracicBankedSample_shouldGenerateNewId() {
        String uuid = dataReader.generateNewIdForThoracicBankedSample();
        assertNotNull(uuid);
        assertEquals(9, uuid.length());
    }

    @Test
    public void generateNewIdForThoracicBankedSample_shouldGenerateUniqueIds() {
        List<String> bulkIdsGeneratedByMethod = existingRecordUuids;
        Set<String> uniqueIdsSet = new HashSet<>(bulkIdsGeneratedByMethod);
        assertEquals(bulkIdsGeneratedByMethod.size(), uniqueIdsSet.size());
    }

    @Test
    public void compareIdToExistingIdsAndReturnUniqueId_shouldReturnUniqueIds() {
        Set<String> uniqueIdsSet = new HashSet<>(existingRecordUuids);
        for (int i = 0; i < 10000; i++) {
            assertTrue(uniqueIdsSet.add(dataReader.generateNewIdForThoracicBankedSample()));
        }
    }

    @Test
    public void excelFileHasValidHeader_shouldReturnTrueIfValidFileHeader() {
        String fileName = Objects.requireNonNull(ThoracicBankedSampleDataReaderTests.class.getClassLoader().getResource("Valid_File_test.xlsx"))
                .getPath();
        assertTrue(dataReader.excelFileHasValidHeader(sheetWithValidData, expectedHeaderValues, fileName));
    }

    @Test
    public void excelFileHasValidHeader_shouldReturnFalseIfNotValidFileHeader() {
        String fileName = Objects.requireNonNull(ThoracicBankedSampleDataReaderTests.class.getClassLoader().getResource("Invalid_FileHeaders_test.xlsx"))
                .getPath();
        assertFalse(dataReader.excelFileHasValidHeader(sheetWithInvalidHeader, expectedHeaderValues, fileName));
    }

    @Test
    public void excelFileHasData_shouldReturnTrueWhenFileHasValidData() {
        assertTrue(dataReader.excelFileHasData(sheetWithValidData));
    }

    @Test
    public void excelFileHasData_shouldReturnFalseWhenFileHasNotValidData() {
        assertFalse(dataReader.excelFileHasData(emptySheet));
    }

    @Test
    public void parseExcelFileHeader_shouldReturnTrueIfHeaderValues() {
        assertEquals(dataReader.parseExcelFileHeader(sheetWithValidData, expectedHeaderValues).size(), expectedHeaderValues.size());
    }

    @Test
    public void parseExcelFileHeader_shouldReturnFalseIfNotValidHeader() {
        assertNotEquals(dataReader.parseExcelFileHeader(sheetWithInvalidHeader, expectedHeaderValues).size(), expectedHeaderValues.size());
    }

    @Test
    public void readThoracicBankedSampleRecordsFromFile_shouldReturnDataWhenValidFile() {
        Map<String, Integer> fileHeader = dataReader.parseExcelFileHeader(sheetWithValidData, expectedHeaderValues);
        List<Map<String, Object>> records = dataReader.readThoracicBankedSampleRecordsFromFile(sheetWithValidData, fileHeader, existingRecordUuids);
        assertNotNull(records);
    }

    @Test
    public void readThoracicBankedSampleRecordsFromFile_shouldReturnNoRecordsWhenNotValidFile() {
        Map<String, Integer> fileHeader = dataReader.parseExcelFileHeader(emptySheet, expectedHeaderValues);
        List<Map<String, Object>> records = dataReader.readThoracicBankedSampleRecordsFromFile(emptySheet, fileHeader, existingRecordUuids);
        assertTrue(records.size() == 0);
    }

    private List<String> createBulkUuids() {
        List<String> existingRecordUuids = new ArrayList<>();
        for (int i = 0; i <= 10000; i++) {
            existingRecordUuids.add(dataReader.generateNewIdForThoracicBankedSample());
        }
        return existingRecordUuids;
    }
}
