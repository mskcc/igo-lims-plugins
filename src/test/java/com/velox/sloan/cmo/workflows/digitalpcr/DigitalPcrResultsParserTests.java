package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests for DigitalPcrResultsParser plugin and dependent classes
 *
 * @author Ajay Sharma
 */
public class DigitalPcrResultsParserTests {
    List<List<String>> channel1Data;
    List<List<String>> channel2Data;
    List<Map<String, Object>> flatData;
    Map<String, List<Map<String, Object>>> groupedData;
    private List<String> dataFromFile = new ArrayList<>();
    private Map<String, Integer> headerValuesMap;
    private byte[] byteData;
    private IgoLimsPluginUtils commonMethods = new IgoLimsPluginUtils();
    private DdPcrResultsProcessor resultsProcessor = new DdPcrResultsProcessor();

    @Before
    public void setUp() {
        String fileName = "ddPCR_results_file_for_tests.csv";
        byteData = readCsvFileToBytes(fileName);
        try {
            dataFromFile = commonMethods.readDataFromCsvFile(byteData);
            headerValuesMap = commonMethods.getCsvHeaderValueMap(dataFromFile);
            channel1Data = resultsProcessor.readChannel1Data(dataFromFile, headerValuesMap);
            channel2Data = resultsProcessor.readChannel2Data(dataFromFile, headerValuesMap);
            flatData = resultsProcessor.concatenateChannel1AndChannel2Data(channel1Data, channel2Data, headerValuesMap);
            groupedData = resultsProcessor.aggregateResultsBySampleAndAssay(flatData);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getChannel1Data_shouldReturn23Values() {
        assertEquals(channel1Data.size(), 23);
    }

    @Test
    public void getChannel1Data_shouldFalseIfNot23Values() {
        assertNotEquals(channel1Data.size(), 20);
    }

    @Test
    public void getChannel2Data_shouldReturn23Values() {
        assertEquals(channel2Data.size(), 23);
    }

    @Test
    public void getChannel2Data_shouldFalseIfNot23Values() {
        assertNotEquals(channel2Data.size(), 24);
    }

    @Test
    public void concatenateChannel1AndChannel2Data_shouldReturn23Values() {
        assertEquals(resultsProcessor.concatenateChannel1AndChannel2Data(channel1Data, channel2Data, headerValuesMap).size(), 23);
    }

    @Test
    public void aggregateResultsBySampleAndAssay_shouldReturn23Values() {
        assertEquals(groupedData.size(), 13);
    }

    @Test
    public void calculateAverage_shouldReturn3() {
        Double expectedReslultForA = 4.0;
        Double expectedReslultForB = 7.5;
        Double expectedReslultForC = 6.0;
        List<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> map1 = new HashMap<>();
        map.put("a", 3);
        map.put("b", 4);
        map.put("c", 5);
        values.add(map);
        map1.put("a", 5);
        map1.put("b", 11);
        map1.put("c", 7);
        values.add(map1);
        assertEquals(resultsProcessor.calculateAverage(values, "a"), expectedReslultForA);
        assertEquals(resultsProcessor.calculateAverage(values, "b"), expectedReslultForB);
        assertEquals(resultsProcessor.calculateAverage(values, "c"), expectedReslultForC);

    }

    @Test
    public void calculateSum_shouldReturnCorrectSum() {
        Integer expectedReslultForD = 8;
        Integer expectedReslultForE = 15;
        Integer expectedReslultForF = 12;
        List<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> map1 = new HashMap<>();
        map.put("d", 3);
        map.put("e", 4);
        map.put("f", 5);
        values.add(map);
        map1.put("d", 5);
        map1.put("e", 11);
        map1.put("f", 7);
        values.add(map1);
        assertEquals(resultsProcessor.calculateSum(values, "d"), expectedReslultForD);
        assertEquals(resultsProcessor.calculateSum(values, "e"), expectedReslultForE);
        assertEquals(resultsProcessor.calculateSum(values, "f"), expectedReslultForF);
    }

    @Test
    public void calculateRatio_shouldReturnCorrectValue() {
        Double valueA = 4.0;
        Double valueB = 5.0;
        Double expectedResult1 = 0.8;
        Double expectedResult2 = 1.25;
        assertEquals(resultsProcessor.calculateRatio(valueA, valueB), expectedResult1);
        assertEquals(resultsProcessor.calculateRatio(valueB, valueA), expectedResult2);

    }

    @Test
    public void calculateTotalDnaDetected_shouldReturnCorrectValue() {
        Double valueA = 4.0;
        Double valueB = 5.0;
        Double expectedResult = 0.5940000000000001;
        assertEquals(resultsProcessor.calculateTotalDnaDetected(valueA, valueB), expectedResult);
    }

    @Test
    public void calculateTotalDnaDetected_shouldReturnValueIfDenominatorIsZero() {
        Double valueA = 4.0;
        Double valueB = 2.0;
        Double expectedResult = 0.396;
        assertEquals(resultsProcessor.calculateTotalDnaDetected(valueA, valueB), expectedResult);
    }

    @Test
    public void calculateHumanPercentage_shouldReturnCorrectValue() {
        Integer value1 = 10;
        Integer value2 = 30;
        Double expectedPercentage = 25.0;
        assertEquals(resultsProcessor.calculateHumanPercentage(value1, value2), expectedPercentage);
    }

    @Test
    public void calculateHumanPercentage_shouldReturnZeroIfNumeratorIsZero() {
        Integer numerator = 0;
        Integer denominator = 4;
        Double expectedPercentage = 0.0;
        assertEquals(resultsProcessor.calculateHumanPercentage(numerator, denominator), expectedPercentage);
    }

    @Test
    public void calculateHumanPercentage_shouldReturnHundredIfDenominatorIsZero() {
        Integer numerator = 4;
        Integer denominator = 0;
        Double expectedPercentage = 100.0;
        assertEquals(resultsProcessor.calculateHumanPercentage(numerator, denominator), expectedPercentage);
    }

    private byte[] readCsvFileToBytes(String fileName) {
        File file = new File(Objects.requireNonNull(IgoLimsPluginUtils
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
