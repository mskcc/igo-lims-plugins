package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

class Covid19Helper {

    private final List<String> QPCR_CONTROLS = Arrays.asList("pos", "ntc", "hel");
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();


    /**
     * Method to get data from user uploaded file
     *
     * @param fileData
     * @return
     */
    List<String> getQpcrResults(List<String> fileData) {
        List<String> data = new ArrayList<>();
        for (String line : fileData) {
            if (!line.contains("#")) {
                data.add(line);
            }
        }
        return data;
    }

    /**
     * Method to parse QPCR data from file into values for DataRecord.
     *
     * @param qpcrDataRows
     * @return
     */
    Map<String, List<Map<String, Object>>> parseQpcrData(List<String> qpcrDataRows) {
        Map<String, Integer> headerValuesMap = utils.getCsvHeaderValueMap(qpcrDataRows);
        Map<String, List<Map<String, Object>>> parsedData = new HashMap<>();
        for (int i = 1; i < qpcrDataRows.size(); i++) {
            List<String> rowValues = Arrays.asList(qpcrDataRows.get(i).split(","));
            if (rowValues.size() > 0) {
                String otherSampleId = rowValues.get(headerValuesMap.get("Sample")).trim();
                if (!StringUtils.isBlank(otherSampleId)) {
                    Map<String, Object> parsedValues = new HashMap<>();
                    parsedData.putIfAbsent(otherSampleId, new ArrayList<>());
                    parsedValues.put("OtherSampleId", otherSampleId);
                    parsedValues.put("TargetAssay", rowValues.get(headerValuesMap.get("Target")));
                    parsedValues.put("CqValue", rowValues.get(headerValuesMap.get("Cq")));
                    //parsedValues.put("CqMean", rowValues.get(headerValuesMap.get("Cq Mean")));
                    parsedData.get(otherSampleId).add(parsedValues);
                }
            }
        }
        return parsedData;
    }

    /**
     * Method to get CQ Value for an assay from QPCR data for sample.
     *
     * @param qpcrValues
     * @param assayName
     * @return
     */
    Object getCqValueForAssay(List<Map<String, Object>> qpcrValues, String assayName) {
        for (Map<String, Object> vals : qpcrValues) {
            Object targetAssay = vals.get("TargetAssay");
            Object cqValue = vals.get("CqValue");
            if (targetAssay != null && targetAssay.toString().equalsIgnoreCase(assayName)) {
                if (cqValue != null && !cqValue.toString().equalsIgnoreCase("undetermined")) {
                    return cqValue;
                }
            }
        }
        return "Undetermined";
    }

    /**
     * Method to generate analyzed QPCR data.
     *
     * @param parsedData
     * @return
     * @throws ServerException
     */
    List<Map<String, Object>> analyzeParsedQpcrData(Map<String, List<Map<String, Object>>> parsedData) {
        List<Map<String, Object>> analyzedData = new ArrayList<>();
        for (String key : parsedData.keySet()) {
            Map<String, Object> analyzedValues = new HashMap<>();
            List<Map<String, Object>> sampleQpcrValues = parsedData.get(key);
            analyzedValues.put("OtherSampleId", key);
            //analyzedValues.put("CqMean", getCqMean(sampleQpcrValues));
            //extract cq values for each assay from parse sample values
            Object cqN1 = getCqValueForAssay(sampleQpcrValues, "N1");
            Object cqN2 = getCqValueForAssay(sampleQpcrValues, "N2");
            Object cqRP = getCqValueForAssay(sampleQpcrValues, "RP");
            //parse cq values to 0 and 1 based on cq values range. Presumption : cq = undetermined = 0
            Integer translatedCQN1 = getTranslatedCQValue(cqN1);
            Integer translatedCQN2 = getTranslatedCQValue(cqN2);
            Integer translatedCQRP = getTranslatedCQValue(cqRP);
            //get the sum of translated values
            Integer translatedSum = translatedCQN1 + translatedCQN2 + translatedCQRP;
            analyzedValues.put("CqN1", cqN1);
            analyzedValues.put("CqN2", cqN2);
            analyzedValues.put("CqRP", cqRP);
            analyzedValues.put("TranslatedCQN1", translatedCQN1);
            analyzedValues.put("TranslatedCQN2", translatedCQN2);
            analyzedValues.put("TranslatedCQRP", translatedCQRP);
            analyzedValues.put("SumCqForAssays", translatedSum);
            analyzedValues.put("AssayResult", getAssayResult(translatedSum));
            analyzedData.add(analyzedValues);
        }
        return analyzedData;
    }

    /**
     * Methos to get Translate CQ value from QPCR cq values.
     *
     * @param cqValue
     * @return
     */
    Integer getTranslatedCQValue(Object cqValue) {
        if (!cqValue.toString().equalsIgnoreCase("undetermined")) {
            Double cq = Double.parseDouble(cqValue.toString());
            if (cq > 0 && cq < 40.0) {
                return 1;
            }
        }
        return 0;
    }

//    /**
//     * Method to get Mean CQ values from QPCR data for sample.
//     * @param qpcrValues
//     * @return
//     */
//    Object getCqMean (List<Map<String, Object>> qpcrValues){
//        for (Map<String, Object> vals : qpcrValues){
//            Object cqMean = vals.get("CqMean");
//            if (cqMean != null && !vals.get("CqMean").toString().equalsIgnoreCase("undetermined")){
//                return vals.get("CqMean");
//            }
//        }
//        return 0;
//    }

    /**
     * Method to get annotation value for test results as Positive/Invalid/Undetected/Inconclusive
     *
     * @param translatedCQSum
     * @return
     */
    String getAssayResult(Integer translatedCQSum) {
        switch (translatedCQSum) {
            case 0:
                return "Invalid";
            case 1:
                return "Not detected";
            case 2:
                return "Inconclusive";
            case 3:
                return "Detected";
        }
        return "Invalid";
    }

    /**
     * Method to get values from Map using key value.
     *
     * @param data
     * @param key
     * @return
     */
    String getValueFromMap(Map<String, Object> data, String key) {
        if (data.get(key) != null) {
            return data.get(key).toString();
        }
        return "";
    }

    /**
     * Method to generate report for Inconclusive Samples.
     *
     * @param analyzedData
     * @return
     */
    Integer getTotalSamples(List<Map<String, Object>> analyzedData) {
        return (int) analyzedData.stream().filter(i -> !QPCR_CONTROLS.contains(getValueFromMap(i, "OtherSampleId").toLowerCase())).count();
    }

    /**
     * Method to generate report for Positive Samples.
     *
     * @param analyzedData
     * @return
     */
    Integer getTotalPositiveSamples(List<Map<String, Object>> analyzedData) {
        return (int) analyzedData.stream().filter(i -> getValueFromMap(i, "AssayResult").equalsIgnoreCase("detected")
                && !QPCR_CONTROLS.contains(getValueFromMap(i, "OtherSampleId").toLowerCase())).count();
    }

    /**
     * Method to generate report for Inconclusive Samples.
     *
     * @param analyzedData
     * @return
     */
    Integer getTotalInconclusiveSamples(List<Map<String, Object>> analyzedData) {
        return (int) analyzedData.stream().filter(i -> getValueFromMap(i, "AssayResult").equalsIgnoreCase("inconclusive")
                && !QPCR_CONTROLS.contains(getValueFromMap(i, "OtherSampleId").toLowerCase())).count();
    }
}
