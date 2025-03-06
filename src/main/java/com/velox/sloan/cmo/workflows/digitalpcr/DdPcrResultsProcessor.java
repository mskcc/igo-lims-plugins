package com.velox.sloan.cmo.workflows.digitalpcr;

import java.math.BigDecimal;
import java.util.*;
import com.velox.api.plugin.PluginLogger;
import java.util.function.DoubleUnaryOperator;

public class DdPcrResultsProcessor implements DdPcrResultsReader {
    @Override
    public List<List<String>> readChannel1Data(List<String> fileData, Map<String, Integer> headerValueMap, boolean isQX200) {
        List<List<String>> channel1RawData = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (isQX200) {
                if (valuesInRow.get(headerValueMap.get("TargetType")).contains("Unknown")) {
                    channel1RawData.add(valuesInRow);
                }
            }
        }
        return channel1RawData;
    }

    @Override
    public List<List<String>> readChannel2Data(List<String> fileData, Map<String, Integer> headerValueMap, boolean isQX200) {
        List<List<String>> channel2RawData = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (isQX200) {
                if (valuesInRow.get(headerValueMap.get("TargetType")).contains("Ref")) {
                    channel2RawData.add(valuesInRow);
                }
            }
        }
        return channel2RawData;
    }

    @Override
    public List<List<List<String>>> readAllChannelsData(List<String> fileData, Map<String, Integer> headerValueMap, boolean isQX200) {
        List<List<List<String>>> allChannelsRawData = new ArrayList<>();
        List<List<String>> eachSampleChannels = new ArrayList<>();
        for (String row : fileData) {
            List<String> valuesInRow = Arrays.asList(row.split(","));
            if (!isQX200) {
                eachSampleChannels.add(valuesInRow);
            }
            allChannelsRawData.add(eachSampleChannels);
        }
        return allChannelsRawData;
    }

    @Override
    public List<Map<String, Object>> concatenateChannel1AndChannel2Data(List<List<String>> channel1Data, List<List<String>> channel2Data, Map<String, Integer> headerValueMap, boolean isQX200, PluginLogger logger) {
        List<Map<String, Object>> flatData = new ArrayList<>();
        for (List<String> s1 : channel1Data) {
            String s1Well = s1.get(headerValueMap.get("Well"));
            String s1SampleId = "";
            String s2SampleId = "";
            logger.logInfo("s1 MergedWells = " + s1.get(headerValueMap.get("MergedWells")));
            if (!isQX200) {
                s1SampleId = s1.get(headerValueMap.get("Sample description 2"));
                logger.logInfo("QX600: sample name" + s1SampleId);
            } else { // QX200
                s1SampleId = s1.get(headerValueMap.get("Sample"));
            }
            for (List<String> s2 : channel2Data) {
                String s2Well = s2.get(headerValueMap.get("Well"));
                if (isQX200) {
                    s2SampleId = s2.get(headerValueMap.get("Sample"));
                } else { // QX600
                    s2SampleId = s2.get(headerValueMap.get("Sample description 2"));
                }
                if (s2Well.equalsIgnoreCase(s1Well) && s2SampleId.equalsIgnoreCase(s1SampleId)) {
                    Map<String, Object> sampleValues = new HashMap<>();
                    sampleValues.put("Well", s1.get(headerValueMap.get("Well")));
                    if (isQX200) {
                        sampleValues.put("Sample", s1.get(headerValueMap.get("Sample")));
                        sampleValues.put("ConcentrationMutation", Double.parseDouble(s1.get(headerValueMap.get("Concentration"))));
                        sampleValues.put("ConcentrationWildType", Double.parseDouble(s2.get(headerValueMap.get("Concentration"))));
                        sampleValues.put("AcceptedDroplets", Integer.parseInt(s1.get(headerValueMap.get("AcceptedDroplets"))));
                        sampleValues.put("Target", s1.get(headerValueMap.get("Target")));
                        if (Double.parseDouble(s1.get(headerValueMap.get("Concentration"))) + Double.parseDouble(s2.get(headerValueMap.get("Concentration"))) == 0) {
                            sampleValues.put("FractionalAbundance", 0.0);
                        }
                        else {
                            Double fractionalAbundance = Double.parseDouble(s1.get(headerValueMap.get("Concentration"))) /
                                    (Double.parseDouble(s1.get(headerValueMap.get("Concentration"))) + Double.parseDouble(s2.get(headerValueMap.get("Concentration"))));
                            sampleValues.put("FractionalAbundance", fractionalAbundance);
                        }
                    } else { // QX600
                        logger.logInfo("Conc(copies/µL): " + headerValueMap.get("Conc(copies/µL)"));
                        logger.logInfo("s1 size = " + s1.size());
                        logger.logInfo("header value map of Conc(Copies/µl) = " + headerValueMap.get("Conc(copies/µL)"));
                        sampleValues.put("Sample", s1.get(headerValueMap.get("Sample description 2")));
                        int index = headerValueMap.get("Conc(copies/µL)");
                        if (s1.get(index) != null && !s1.get(index).isEmpty() && !s1.get(index).isBlank()) {
                            sampleValues.put("ConcentrationMutation", Double.parseDouble(s1.get(index)));
                        }
                        if (s2.get(index) != null && !s2.get(index).isEmpty() && !s2.get(index).isBlank()) {
                            sampleValues.put("ConcentrationWildType", Double.parseDouble(s2.get(index)));
                        }
                        sampleValues.put("AcceptedDroplets", Integer.parseInt(s1.get(headerValueMap.get("Accepted Droplets"))));
                        sampleValues.put("TargetGene", s1.get(headerValueMap.get("Target")));
                        sampleValues.put("TargetRef", s2.get(headerValueMap.get("Target")));
                        if (Double.parseDouble(s1.get(headerValueMap.get("Conc(copies/µL)"))) + Double.parseDouble(s2.get(headerValueMap.get("Conc(copies/µL)"))) == 0) {
                            sampleValues.put("FractionalAbundance", 0.0);
                        }
                        else {
                            Double fractionalAbundance = Double.parseDouble(s1.get(headerValueMap.get("Conc(copies/µL)"))) /
                                    (Double.parseDouble(s1.get(headerValueMap.get("Conc(copies/µL)"))) + Double.parseDouble(s2.get(headerValueMap.get("Conc(copies/µL)"))));
                            sampleValues.put("FractionalAbundance", fractionalAbundance);
                        }
                    }
                    //sampleValues.put("Target", s1.get(headerValueMap.get("Target")));
                    logger.logInfo("s1.get(headerValueMap.get(\"Ch1+Ch2+\")) = " + s1.get(headerValueMap.get("Ch1+Ch2+")));
                    sampleValues.put("Channel1PosChannel2Pos", Integer.parseInt(s1.get(headerValueMap.get("Ch1+Ch2+"))));
                    sampleValues.put("Channel1PosChannel2Neg", Integer.parseInt(s1.get(headerValueMap.get("Ch1+Ch2-"))));
                    sampleValues.put("Channel1NegChannel2Pos", Integer.parseInt(s1.get(headerValueMap.get("Ch1-Ch2+"))));
                    if (s1.get(headerValueMap.get("CNV")) != null && !s1.get(headerValueMap.get("CNV")).isEmpty() && !s1.get(headerValueMap.get("CNV")).isBlank()) {
                        sampleValues.put("CNV", Double.parseDouble(s1.get(headerValueMap.get("CNV"))));
                    }
                    else {
                        sampleValues.put("CNV", 0.0);
                    }
                    flatData.add(sampleValues);
                }
            }
        }
        return flatData;
    }

    public List<Map<String, Object>> concatenateAllChannels(List<List<List<String>>> allChannelsData, Map<String, Integer> headerValueMap, boolean isQX200, PluginLogger logger) {
        List<Map<String, Object>> flatData = new ArrayList<>();
        for (int i  = 0; i < allChannelsData.size(); i++) {
            for (List<String> eachChannel : allChannelsData.get(i)) {
                int index = headerValueMap.get("Conc(copies/µL)");
                String targetWell = eachChannel.get(headerValueMap.get("Well"));
                String targetSampleId = "";
                String refSampleId = "";
                String s2Well = "";
                String refIndex = "";
                String refConcentration = "";
                if (i == 1) { // channel 2 is the reference
                    s2Well = eachChannel.get(headerValueMap.get("Well"));
                    refSampleId = eachChannel.get(headerValueMap.get("Sample description 2"));
                    refIndex = eachChannel.get(index);
                    refConcentration = eachChannel.get(headerValueMap.get("Conc(copies/µL)"));
                }

                if (s2Well.equalsIgnoreCase(targetWell) && refSampleId.equalsIgnoreCase(targetSampleId)) {
                    Map<String, Object> sampleValues = new HashMap<>();
                    sampleValues.put("Well", allChannelsData.get(i).get(headerValueMap.get("Well")));
                    logger.logInfo("Conc(copies/µL): " + headerValueMap.get("Conc(copies/µL)"));
                    logger.logInfo("s1 size = " + allChannelsData.get(i).size());
                    logger.logInfo("header value map of Conc(Copies/µl) = " + headerValueMap.get("Conc(copies/µL)"));
                    sampleValues.put("Sample", allChannelsData.get(i).get(headerValueMap.get("Sample description 2")));

                    if (allChannelsData.get(i).get(index) != null && !allChannelsData.get(i).get(index).isEmpty() && !eachChannel.get(index).isBlank()) {
                        sampleValues.put("ConcentrationMutation", Double.parseDouble(eachChannel.get(index)));
                    }
                    if (allChannelsData.get(1).get(index) != null && !allChannelsData.get(1).get(index).isEmpty() && !refIndex.isBlank()) {
                        sampleValues.put("ConcentrationWildType", Double.parseDouble(refIndex));
                    }
                    sampleValues.put("AcceptedDroplets", Integer.parseInt(eachChannel.get(headerValueMap.get("Accepted Droplets"))));
                    sampleValues.put("TargetGene", allChannelsData.get(i).get(headerValueMap.get("Target")));
                    sampleValues.put("TargetRef", allChannelsData.get(1).get(headerValueMap.get("Target")));
                    if ((Double.parseDouble(eachChannel.get(headerValueMap.get("Conc(copies/µL)"))) + Double.parseDouble(refConcentration)) == 0) {
                        sampleValues.put("FractionalAbundance", 0.0);
                    } else {
                        Double fractionalAbundance = Double.parseDouble(eachChannel.get(headerValueMap.get("Conc(copies/µL)"))) /
                                (Double.parseDouble(eachChannel.get(headerValueMap.get("Conc(copies/µL)"))) + Double.parseDouble(refConcentration));
                        sampleValues.put("FractionalAbundance", fractionalAbundance);
                    }

                    logger.logInfo("s1.get(headerValueMap.get(\"Ch1+Ch2+\")) = " + allChannelsData.get(i).get(headerValueMap.get("Ch1+Ch2+")));
                    sampleValues.put("Channel1PosChannel2Pos", Integer.parseInt(eachChannel.get(headerValueMap.get("Ch1+Ch2+"))));
                    sampleValues.put("Channel1PosChannel2Neg", Integer.parseInt(eachChannel.get(headerValueMap.get("Ch1+Ch2-"))));
                    sampleValues.put("Channel1NegChannel2Pos", Integer.parseInt(eachChannel.get(headerValueMap.get("Ch1-Ch2+"))));
                    if (allChannelsData.get(i).get(headerValueMap.get("CNV")) != null && !allChannelsData.get(i).get(headerValueMap.get("CNV")).isEmpty() && !eachChannel.get(headerValueMap.get("CNV")).isBlank()) {
                        sampleValues.put("CNV", Double.parseDouble(eachChannel.get(headerValueMap.get("CNV"))));
                    } else {
                        sampleValues.put("CNV", 0.0);
                    }
                    flatData.add(sampleValues);
                }
            }
        }
        return flatData;
    }

    @Override
    public Map<String, List<Map<String, Object>>> aggregateResultsBySampleAndAssay(List<Map<String, Object>> flatData, boolean QX200) {
        Map<String, List<Map<String, Object>>> groupedData = new HashMap<>();
        boolean toggle = true;
        for (Map<String, Object> data : flatData) {
            toggle = true;
            String keyValue = "";
            if (QX200) {
                keyValue = data.get("Sample").toString() + "/" + data.get("Target").toString();
            }
            else { //QX600
                if (toggle) {
                    keyValue = data.get("Sample").toString() + "/Gene:" + data.get("TargetGene").toString();
                    toggle = false;
                }
                else {
                    keyValue = data.get("Sample").toString() + "/Ref:" + data.get("TargetRef").toString();
                    toggle = true;
                }
            }
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
    public Double calculateRatio(Double concentrationGene, Double concentrationRef) {
        if (concentrationRef <= 0.0) {
            concentrationRef = 1.0;
        }
        return concentrationGene / concentrationRef;
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

