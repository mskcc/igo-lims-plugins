package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.plugin.PluginLogger;

import java.util.List;
import java.util.Map;

public interface DdPcrResultsReader {
    List<List<String>> readChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap, boolean QX200);

    List<List<String>> readChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap, boolean QX200);
    List<List<String>> readRefChannelsData(List<String> fileData, Map<String, Integer> headerValueMap, String numOfChannels, String reference);
    List<List<String>> readTargetChannelsData(List<String> fileData, Map<String, Integer> headerValueMap, String numOfChannels, String reference);

    List<Map<String, Object>> concatenateChannel1AndChannel2Data(List<List<String>> channel1Data, List<List<String>> channel2Data, Map<String, Integer> headerValueMap, boolean isQX200, PluginLogger logger);

    List<Map<String, Object>> concatenateRefTargetChannels(List<List<String>> targetChannelsData, List<List<String>> refChannelsData, Map<String, Integer> headerValueMap, String numOfChannels, PluginLogger logger);

    Map<String, List<Map<String, Object>>> aggregateResultsBySampleAndAssay(List<Map<String, Object>> flatData, boolean QX200);

    Double calculateAverage(List<Map<String, Object>> sampleData, String fieldName);

    Double calculateSum(List<Map<String, Object>> sampleData, String fieldName);

    Double calculateRatio(Double dropletCountMutation, Double dropletCountWildType);

    Double calculateTotalDnaDetected(Double concentrationMutation, Double ConcentrationWildType);

    Double calculateHumanPercentage(Double dropletCountMutation, Double dropletCountWildType);
}
