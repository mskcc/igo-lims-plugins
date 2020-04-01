package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Before;
import org.junit.Test;


import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Covid19Tests {

    List<Map<String,Object>> data = new ArrayList<>();
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private Covid19Helper helper = new Covid19Helper();
    private byte[] byteData;
    private List<String> dataFromFile = new ArrayList<>();
    List<String> qpcrData;
    Map<String, List<Map<String, Object>>> parsedQpcrData;
    private Map<String, Integer> headerValuesMap;
    @Before
    public void setUp() {
        String fileName = "covid19_qpcr_raw_results.csv";
        byteData = utils.readCsvFileToBytes(fileName);
        try {
            dataFromFile = utils.readDataFromCsvFile(byteData);
            qpcrData = helper.getQpcrResults(dataFromFile);
            parsedQpcrData = helper.parseQpcrData(qpcrData);
        } catch (IOException e) {
            String message = ExceptionUtils.getMessage(e);
            System.out.println(message);
        }

    }

    @Test
    public void getQpcrResults_test() {
        assertEquals(qpcrData.size(), 289);
    }

    @Test
    public void parseQpcrData_test(){
        assertEquals(parsedQpcrData.size(), 98);
    }

    @Test
    public void getCqValueForAssay_test(){
        List<String> keys = new ArrayList<>(parsedQpcrData.keySet());
        List<Map<String,Object>> qpcrData1 = parsedQpcrData.get(keys.get(1));
        List<Map<String,Object>> qpcrData2 = parsedQpcrData.get(keys.get(5));
        List<Map<String,Object>> qpcrData3 = parsedQpcrData.get(keys.get(10));
        assertEquals(helper.getCqValueForAssay(qpcrData1, "N2"), "Undetermined");
        assertEquals(helper.getCqValueForAssay(qpcrData1, "RP"), "28.422646");

        assertEquals(helper.getCqValueForAssay(qpcrData2, "N1"), "16.5359318");
        assertEquals(helper.getCqValueForAssay(qpcrData2, "N2"), "17.65153831");
        assertEquals(helper.getCqValueForAssay(qpcrData2, "RP"), "27.84730191");

        assertEquals(helper.getCqValueForAssay(qpcrData3, "N1"), "30.88017248");
        assertEquals(helper.getCqValueForAssay(qpcrData3, "N2"), "Undetermined");
        assertEquals(helper.getCqValueForAssay(qpcrData3, "RP"), "27.6387558");
    }

    @Test
    public void getWellIdForAssay_test(){
        List<String> keys = new ArrayList<>(parsedQpcrData.keySet());
        List<Map<String,Object>> qpcrData1 = parsedQpcrData.get(keys.get(5));
        assertEquals(helper.getWellIdForAssay(qpcrData1, "N1"), "C21");
        assertEquals(helper.getWellIdForAssay(qpcrData1, "N2"), "C22");
        assertEquals(helper.getWellIdForAssay(qpcrData1, "RP"), "D21");
    }

    @Test
    public void analyzeParsedQpcrData_Test(){
        List<Map<String, Object>> analyzedData = helper.analyzeParsedQpcrData(parsedQpcrData);
        assertEquals(analyzedData.size(), 98);
        assertEquals(analyzedData.get(0).size(), 12);
        assertTrue(analyzedData.get(0).containsKey("AssayResult"));
    }

    @Test
    public void getTranslatedCQValue_Test(){
        Object cqVal1 = "Undetermined";
        Object cqVal2 = "40.0";
        Object cqVal3 = "0.0";
        Object cqVal4 = "-1";
        Object cqVal5 = "12.5325";
        assertEquals(0, (int) helper.getTranslatedCQValue(cqVal1));
        assertEquals(0, (int) helper.getTranslatedCQValue(cqVal2));
        assertEquals(0, (int) helper.getTranslatedCQValue(cqVal3));
        assertEquals(0, (int) helper.getTranslatedCQValue(cqVal4));
        assertEquals(1, (int) helper.getTranslatedCQValue(cqVal5));
    }

    @Test
    public void getAssayResult_Test(){
        int cqVal1 = 0;
        int cqVal2 = 1;
        int cqVal3 = 2;
        int cqVal4 = 3;
        assertEquals("Invalid", helper.getAssayResult(cqVal1));
        assertEquals("Not detected", helper.getAssayResult(cqVal2));
        assertEquals("Inconclusive", helper.getAssayResult(cqVal3));
        assertEquals("Detected", helper.getAssayResult(cqVal4));
    }

    @Test
    public void getValueFromMap_Test(){
        Map<String, Object> data = new HashMap<>();
        data.put("Key1", "Key1");
        data.put("Key2", "Key2");
        data.put("Key3", 12);
        data.put("Key4", null);
        data.put("Key5", "");

        assertEquals("Key1", helper.getValueFromMap(data, "Key1"));
        assertEquals("Key2", helper.getValueFromMap(data, "Key2"));
        assertEquals("12", helper.getValueFromMap(data, "Key3"));
        assertEquals("", helper.getValueFromMap(data, "Key4"));
        assertEquals("", helper.getValueFromMap(data, "Key5"));
    }

    @Test
    public void getTotalSamples_Test(){
        List<Map<String, Object>> analyzedData = helper.analyzeParsedQpcrData(parsedQpcrData);
        assertEquals((int)helper.getTotalSamples(analyzedData), 95);
    }

    @Test
    public void getTotalPositiveSamples_Test(){
        List<Map<String, Object>> analyzedData = helper.analyzeParsedQpcrData(parsedQpcrData);
        assertEquals((int)helper.getTotalPositiveSamples(analyzedData), 28);
    }

    @Test
    public void getTotalInconclusiveSamples_Test(){
        List<Map<String, Object>> analyzedData = helper.analyzeParsedQpcrData(parsedQpcrData);
        assertEquals((int)helper.getTotalInconclusiveSamples(analyzedData), 1);
    }
}
