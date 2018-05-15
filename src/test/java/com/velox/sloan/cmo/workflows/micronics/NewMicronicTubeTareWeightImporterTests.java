package com.velox.sloan.cmo.workflows.micronics;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/*
    @description This plugin is designed to read empty micronic tube information from an excel file and
    store that information in LIMS under 'micronic tube tare weight' datarecord.
 */
public class NewMicronicTubeTareWeightImporterTests {

    NewMicronicTubeTareWeightImporter micronicTubeImporter = new NewMicronicTubeTareWeightImporter();
    String[] validFileData = {"Rack,Tube,Barcode,Weight", "7000137444,A01,8027127479,793.5", "7000137444,B01,8027127478,788.5", "7000137444,C01,8027127480,783.5"};
    String[] invalidRowData = {"Rack,Tube,Barcode,Weight", "7000137444,A01,8027127479,-793.5", "7000137444,B01,8027127478,788.5"};
    String[] emptyFileMockData = {"Rack,Tube,Barcode,Weight"};

    @Test
    public void ifNotValidCsvFileExtention_shouldThrowError() {
        String fileName = "abc.xslx";
        assertFalse(micronicTubeImporter.isCsvFile(fileName));
    }

    @Test
    public void ifValidCsvFileExtention_shouldReturnTrue() {
        String fileName = "abc.csv";
        assertTrue(micronicTubeImporter.isCsvFile(fileName));
    }

    @Test
    public void ifNoMicronicTubeDataInFile_shouldReturnFalse() {
        assertFalse(micronicTubeImporter.dataFileHasValidData(emptyFileMockData));
    }

    @Test
    public void ifContainsMicronicTubeDataInFile_shouldReturnTrue() {
        assertTrue(micronicTubeImporter.dataFileHasValidData(validFileData));
    }

    @Test
    public void ifFileHasInvalidHeader_shouldReturnFalse() {
        String[] invalidHeaderData = {"Rack,Tube,,Weight", "7000137444,A01,8027127479,793.5", "7000137444,B01,8027127478,788.5", "7000137444,C01,8027127480,783.5"};
        assertFalse(micronicTubeImporter.dataFileHasValidHeader(invalidHeaderData));
    }

    @Test
    public void ifFileHasValidHeader_shouldReturnTrue() {
        assertTrue(micronicTubeImporter.dataFileHasValidHeader(validFileData));
    }

    @Test
    public void ifRowDataInFileIsNotValid_shouldReturnFalse() {
        assertFalse(micronicTubeImporter.rowsInFileHasValidData(invalidRowData));
    }

    @Test
    public void ifRowDataInFileIsValid_shouldReturnTrue() {
        assertTrue(micronicTubeImporter.rowsInFileHasValidData(validFileData));
    }

    @Test
    public void readNewTubeRecordsFromFileData_shouldReturnData() {
        Assert.assertNotNull(micronicTubeImporter.readNewTubeRecordsFromFileData(validFileData));
    }

    @Test
    public void readNewTubeRecordsFromFileData_shouldReturnEmptyDataIfFileIsEmpty() {
        assertEquals(micronicTubeImporter.readNewTubeRecordsFromFileData(emptyFileMockData).size(), 0);
    }

    @Test
    public void getMicronicTubeBarcodesFromTubeRecords_shouldReturnBarcodesWhenValidData() {
        List<Map<String, Object>> micronicTubeData = micronicTubeImporter.readNewTubeRecordsFromFileData(validFileData);
        Assert.assertNotNull(micronicTubeData);
        assertEquals(micronicTubeData.size(), 3);
    }

    @Test
    public void getDuplicateBarcodesInExistingBarcodes_shouldReturnDuplicateBarcodesIfDuplicatesPresent() {
        List<String> existingBarcodesMockData = Arrays.asList("12345678", "123423412", "12341234", "12341236", "100000234", "12345670");
        List<String> newBarcodesFromFileMockData = Arrays.asList("10345678", "103423412", "12341234", "12341237", "100001234", "12345670");
        assertEquals(micronicTubeImporter.getDuplicateBarcodesInExistingBarcodes(existingBarcodesMockData, newBarcodesFromFileMockData).size(), 2);
    }

    @Test
    public void getDuplicateBarcodesInExistingBarcodes_shouldReturnEmptyListIfDuplicatesNotPresent() {
        List<String> existingBarcodesMockData = Arrays.asList("12345678", "123423412", "12341235", "12341236", "100000234", "12345671");
        List<String> newBarcodesFromFileMockData = Arrays.asList("10345678", "103423412", "12341234", "12341237", "100001234", "12345670");
        assertEquals(micronicTubeImporter.getDuplicateBarcodesInExistingBarcodes(existingBarcodesMockData, newBarcodesFromFileMockData).size(), 0);
    }

    @Test
    public void getDuplicateValuesInNewBarcodesList_shouldReturnDuplicateBarcodesIfDuplicatesPresent() {
        List<String> newBarcodesFromFileMockData = Arrays.asList("10345678", "103423412", "12341234", "12341234", "100001234", "12345670");
        assertEquals(micronicTubeImporter.getDuplicateValuesInNewBarcodesList(newBarcodesFromFileMockData).size(), 1);
    }

    @Test
    public void getDuplicateValuesInNewBarcodesList_shouldReturnEmptyListIfDuplicatesNotPresent() {
        List<String> newBarcodesFromFileMockData = Arrays.asList("10345678", "103423412", "12341234", "12341236", "100001234", "12345670");
        assertEquals(micronicTubeImporter.getDuplicateValuesInNewBarcodesList(newBarcodesFromFileMockData).size(), 0);
    }
}