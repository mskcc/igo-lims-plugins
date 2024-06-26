package com.velox.sloan.cmo.workflows.digitalpcr;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.DoubleUnaryOperator;

public class DdPcrResultsProcessor implements DdPcrResultsReader {

    @Override
    public List<List<String>> readChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap) {
        List<List<String>> channel1RawData = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (valuesInRow.get(headerValueMap.get("TargetType")).contains("Ch1")) {
                channel1RawData.add(valuesInRow);
            }
        }
        return channel1RawData;
    }

    @Override
    public List<List<String>> readChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap) {
        List<List<String>> channel2RawData = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (valuesInRow.get(headerValueMap.get("TargetType")).contains("Ch2")) {
                channel2RawData.add(valuesInRow);
            }
        }
        return channel2RawData;
    }

    @Override
    public List<Map<String, Object>> concatenateChannel1AndChannel2Data(List<List<String>> channel1Data, List<List<String>> channel2Data, Map<String, Integer> headerValueMap) {
        List<Map<String, Object>> flatData = new ArrayList<>();
        for (List<String> s1 : channel1Data) {
            String s1Well = s1.get(headerValueMap.get("Well"));
            String s1SampleId = s1.get(headerValueMap.get("Sample"));
            for (List<String> s2 : channel2Data) {
                String s2Well = s2.get(headerValueMap.get("Well"));
                String s2SampleId = s2.get(headerValueMap.get("Sample"));
                if (s2Well.equalsIgnoreCase(s1Well) && s2SampleId.equalsIgnoreCase(s1SampleId)) {
                    Map<String, Object> sampleValues = new HashMap<>();
                    sampleValues.put("Well", s1.get(headerValueMap.get("Well")));
                    sampleValues.put("Sample", s1.get(headerValueMap.get("Sample")));
                    sampleValues.put("Target", s1.get(headerValueMap.get("Target")));
                    sampleValues.put("ConcentrationMutation", Double.parseDouble(s1.get(headerValueMap.get("Concentration"))));
                    sampleValues.put("ConcentrationWildType", Double.parseDouble(s2.get(headerValueMap.get("Concentration"))));
                    sampleValues.put("Channel1PosChannel2Pos", Integer.parseInt(s1.get(headerValueMap.get("Ch1+Ch2+"))));
                    sampleValues.put("Channel1PosChannel2Neg", Integer.parseInt(s1.get(headerValueMap.get("Ch1+Ch2-"))));
                    sampleValues.put("Channel1NegChannel2Pos", Integer.parseInt(s1.get(headerValueMap.get("Ch1-Ch2+"))));
                    sampleValues.put("AcceptedDroplets", Integer.parseInt(s1.get(headerValueMap.get("AcceptedDroplets"))));
                    if (s1.get(headerValueMap.get("CNV")) != null && !s1.get(headerValueMap.get("CNV")).isEmpty() && !s1.get(headerValueMap.get("CNV")).isBlank()) {
                        sampleValues.put("CNV", Double.parseDouble(s1.get(headerValueMap.get("CNV"))));
                    }
                    else {
                        sampleValues.put("CNV", 0.0);
                    }
                    if (Double.parseDouble(s1.get(headerValueMap.get("Concentration"))) + Double.parseDouble(s2.get(headerValueMap.get("Concentration"))) == 0) {
                        sampleValues.put("FractionalAbundance", 0.0);
                    }
                    else {
                        Double fractionalAbundance = Double.parseDouble(s1.get(headerValueMap.get("Concentration"))) /
                                (Double.parseDouble(s1.get(headerValueMap.get("Concentration"))) + Double.parseDouble(s2.get(headerValueMap.get("Concentration"))));
                        sampleValues.put("FractionalAbundance", fractionalAbundance);
                    }
//                    if (s1.get(headerValueMap.get("FractionalAbundance")) != null && !s1.get(headerValueMap.get("FractionalAbundance")).isEmpty() && !s1.get(headerValueMap.get("FractionalAbundance")).isBlank()) {
//                        sampleValues.put("FractionalAbundance", Double.parseDouble(s1.get(headerValueMap.get("FractionalAbundance"))));
//                    }
//                    else {
//                        sampleValues.put("FractionalAbundance", 0.0);
//                    }
                    flatData.add(sampleValues);
                }
            }
        }
        return flatData;
    }

    @Override
    public Map<String, List<Map<String, Object>>> aggregateResultsBySampleAndAssay(List<Map<String, Object>> flatData) {
        Map<String, List<Map<String, Object>>> groupedData = new HashMap<>();
        for (Map<String, Object> data : flatData) {
            String keyValue = data.get("Sample").toString() + "/" + data.get("Target").toString();
            groupedData.putIfAbsent(keyValue, new ArrayList<>());
            groupedData.get(keyValue).add(data);
        }
        return groupedData;
    }

    @Override
    public Double calculateAverage(List<Map<String, Object>> sampleData, String fieldName) {
        Double sum = 0.0;
        for (Map<String, Object> data : sampleData) {
            sum += Double.parseDouble(data.get(fieldName).toString());
        }
        return sum / sampleData.size();
    }

    @Override
    public Double calculateSum(List<Map<String, Object>> sampleData, String fieldName) {
        double sum = 0.0;
        for (Map<String, Object> data : sampleData) {
            sum += Double.parseDouble(data.get(fieldName).toString());
        }
        return sum;
    }

    @Override
    public Double calculateRatio(Double dropletCountMutation, Double dropletCountWildType) {
        if (dropletCountWildType <= 0.0) {
            dropletCountWildType = 1.0;
        }
        return dropletCountMutation / dropletCountWildType;
    }

    @Override
    public Double calculateTotalDnaDetected(Double concentrationGene, Double concentrationRef) {
        return (concentrationGene + concentrationRef) * 0.066;
    }

    @Override
    public Double calculateHumanPercentage(Double dropletCountMutation, Double dropletCountWildType) {
        if (dropletCountWildType <= 0.0 && dropletCountMutation <= 0.0) {
            return 0.0;
        }
        return Double.valueOf(dropletCountMutation) / (dropletCountMutation + dropletCountWildType) * 100.0;
    }
}

