package com.velox.sloan.cmo.workflows.samplereceiving;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class MicronicTubeVolumeDataReaderTests {
    MicronicTubeVolumeDataReader volumeDataReader = new MicronicTubeVolumeDataReader();
    String[] validFileData = {"Rack,Tube,Barcode,Weight", "7000137444,A01,8027127479,793.5", "7000137444,B01,8027127478,788.5", "7000137444,C01,8027127480,783.5"};

    @Test
    public void isValidFile_shouldReturnFalseIfNotCsvFile(){
        assertFalse(volumeDataReader.isValidFile("abc.xlsx"));
    }

    @Test
    public void isValidFile_shouldReturnTrueIfCsvFile(){
        assertTrue(volumeDataReader.isValidFile("abc.csv"));
    }

    @Test
    public void fileHasData_shouldReturnTrueIfFileHasData(){
        assertTrue(volumeDataReader.fileHasData(validFileData));
    }

    @Test
    public void fileHasData_shouldReturnFalseIfFileDoesNotHaveData(){
        String[] emptyFileMockData = {"Rack,Tube,Barcode,Weight"};
        assertFalse(volumeDataReader.fileHasData(emptyFileMockData));
    }

    @Test
    public void isValidHeader_shouldReturnTrueIfFileHasValidHeader(){
        assertTrue(volumeDataReader.isValidHeader(validFileData));
    }

    @Test
    public void isValidHeader_shouldReturnFalseIfFileDoesNotHaveValidHeader(){
        String[] invalidHeaderData = {"Rack,Tube,Barcode,Snack", "7000137444,A01,8027127479,793.5", "7000137444,B01,8027127478,788.5"};
        assertFalse(volumeDataReader.isValidHeader(invalidHeaderData));
    }

    @Test
    public void rowInFileHasValues_shouldReturnTrueIfRowHasValues(){
        assertTrue(volumeDataReader.rowInFileHasValues(validFileData[2]));
    }

    @Test
    public void rowInFileHasValues_shouldReturnFalseIfRowIsMissingAnyValues(){
        String invalidRowData = "7000137444,A01,,793.5";
        String invalidRowData2 = "7000137444,A01,793.5";
        assertFalse(volumeDataReader.rowInFileHasValues(invalidRowData));
        assertFalse(volumeDataReader.rowInFileHasValues(invalidRowData2));
    }

    @Test
    public void allRowsHaveValidData_shouldReturnTrueIfAllRowsHaveValidData() {
        assertTrue(volumeDataReader.allRowsHaveValidData(validFileData));
    }

    @Test
    public void allRowsHaveValidData_shouldReturnFalseIfAnyRowHasInvalidData() {
        String[] invalidRowData = {"Rack,Tube,Barcode,Weight", "7000137444,A01,8027127479,-793.5", "7000137444,B01,8027127478,788.5"};
        assertFalse(volumeDataReader.allRowsHaveValidData(invalidRowData));
    }

    @Test
    public void getHeaderValues_shouldReturnMapWithHeaderValues(){
        String[] validFileData = {"Rack,Tube,Barcode,Weight", "7000137444,A01,8027127479,793.5", "7000137444,B01,8027127478,788.5", "7000137444,C01,8027127480,783.5"};
        assertNotNull(volumeDataReader.getHeaderValues(validFileData));
    }

    @Test
    public void getColumnPosition_shouldReturnSingleCharacterIfColumnValueLessThanTen(){
        String row = "7000137444,A01,8027127479,793.5"; // 01 in A01 is column value
        Map<String,Integer> header = new HashMap<>();
        header.put("Rack",0);
        header.put("Tube",1);
        header.put("Barcode",2);
        header.put("Weight",3);
        assertEquals(volumeDataReader.getColumnPosition(row,header).length(),1);
    }

    @Test
    public void getColumnPosition_shouldReturnStringOfSize2IfColumnValueGreaterThan9(){
        String row = "7000137444,A10,8027127479,793.5";// 10 in A10 is column value
        Map<String,Integer> header = new HashMap<>();
        header.put("Rack",0);
        header.put("Tube",1);
        header.put("Barcode",2);
        header.put("Weight",3);
        assertEquals(volumeDataReader.getColumnPosition(row,header).length(),2);
    }
}
