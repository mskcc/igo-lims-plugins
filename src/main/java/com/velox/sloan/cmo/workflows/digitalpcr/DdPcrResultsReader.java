package com.velox.sloan.cmo.workflows.digitalpcr;

import java.util.List;
import java.util.Map;

public interface DdPcrResultsReader {
    List<List<String>> readChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap);
    List<List<String>> readChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap);
    List<Map<String, Object>> concatenateChannel1AndChannel2Data(List<List<String>>channel1Data, List<List<String>>channel2Data, Map<String, Integer> headerValueMap);
    Map<String,List<Map<String,Object>>> aggregateResultsBySampleAndAssay(List<Map<String, Object>> flatData);
    Double calculateAverage(List<Map<String,Object>> sampleData, String fieldName);
    Integer calculateSum(List<Map<String,Object>> sampleData, String fieldName);
    Double calculateRatio(Double dropletCountMutation, Double dropletCountWildType);
    Double calculateTotalDnaDetected(Double concentrationMutation, Double ConcentrationWildType);
    Double calculateHumanPercentage(Integer dropletCountMutation, Integer dropletCountWildType);
}
