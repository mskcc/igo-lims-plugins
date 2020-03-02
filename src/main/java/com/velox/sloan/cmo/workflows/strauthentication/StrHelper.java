package com.velox.sloan.cmo.workflows.strauthentication;

import com.google.gson.Gson;
import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.util.*;

public class StrHelper {

    JSONParser parser;
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private final List<String> STR_MARKERS = Arrays.asList("Amelogenin","CSF1PO","D2S1338","D3S1358","D5S818","D7S820","D8S1179","D13S317","D16S539","D18S51", "D19S433", "D21S11", "FGA", "TH01", "TPOX", "vWA");

    public StrHelper(){
        parser = new JSONParser();
    }

    /**
     * Method to read and aggregate sample level data from the uploaded file.
     * @param fileData
     * @param headerValueMap
     * @return Map<String, Map<String, Object>>
     * @throws ServerException
     */
     public Map<String, Map<String, Object>> aggregateDataBySample(List<String> fileData, Map<String, Integer> headerValueMap, String species) throws ServerException {
        Map<String, Map<String, Object>> sampleData = new HashMap<>();
        for (int i = 1; i < fileData.size(); i++) {
            List<String> rowData = Arrays.asList(fileData.get(i).split(","));
            Map<String, Object> data = new HashMap<>();
            String sampleName = rowData.get(headerValueMap.get("Sample Name")).trim();
            String marker = rowData.get(headerValueMap.get("Marker")).trim();
            if (marker.equalsIgnoreCase("amel")){
                marker = "Amelogenin";
            }
            if (species.equalsIgnoreCase("mouse") && (marker.equalsIgnoreCase(" D4S2408") || marker.equalsIgnoreCase(" D8S1106"))){
                continue;
            }
            if (species.equalsIgnoreCase("mouse")){
                marker = "Mouse STR " + marker;
            }
            String allele1 = rowData.get(headerValueMap.get("Allele 1")).trim();
            String allele2 = rowData.get(headerValueMap.get("Allele 2")).trim();
            String allele3 = rowData.get(headerValueMap.get("Allele 3")).trim();
            String allele4 = rowData.get(headerValueMap.get("Allele 4")).trim();
            String alleles = utils.convertListToCommaSeparatedString(Arrays.asList(allele1, allele2, allele3, allele4));
            data.putIfAbsent("description", sampleName);
            data.putIfAbsent("algorithm", 1);
            data.putIfAbsent("scoringMode", 1);
            data.putIfAbsent("scoreFilter", 75);
            data.putIfAbsent("includeAmelogenin", true);
            data.putIfAbsent("species", species);
            sampleData.putIfAbsent(sampleName, data);
            if (!StringUtils.isBlank(alleles)) {
                data.putIfAbsent(marker, "");
                sampleData.get(sampleName).put(marker, alleles);
            }
        }
        return sampleData;
    }

    /**
     * Method to convert the sample level aggregated data to List of JSON string objects. We are calling API using JSON data.
     * @param sampleData
     * @return List<String>
     */
    public List<String> convertSampleDataToJson(Map<String, Map<String, Object>> sampleData) {
        List<String> jsonData = new ArrayList<>();
        for (String entry : sampleData.keySet()) {
            try {
                Gson gson = new Gson();
                String json = gson.toJson(sampleData.get(entry));
                jsonData.add(json);
            } catch (Exception e) {
                System.out.println(String.format("Error parsing data to json values %s",Arrays.toString(e.getStackTrace())));
            }
        }
        return jsonData;
    }

    /**
     * Method to get value from JSONObject using key.
     * @param obj
     * @param key
     * @return String
     */
    public String getValueFromJsonObj(JSONObject obj, String key){
        Object val = obj.get(key);
        if(val != null){
            return val.toString();
        }
        return "";
    }

    /**
     * Method to get value from HashMap. We use method to handle null values, in this case we will return null.
     * @param obj
     * @param key
     * @return String
     */
    public String getValueFromMap(Map<String, Object> obj, String key){
        Object val = obj.get(key);
        if(val != null){
            return val.toString();
        }
        return "";
    }

    /**
     * Method to get the sample level parameters that were sent to the API.
     * @param sampleParamsData
     * @param sampleName
     * @return List<String>
     */
    public List<String> getSampleParamsSentToApi(Map<String,Map<String, Object>> sampleParamsData, String sampleName, List<String> markers) {
        Map<String, Object> sampleParams = sampleParamsData.get(sampleName);
        List<String> values = new ArrayList<>();
        if (!sampleParams.isEmpty()) {
            values.add(""); // add empty value for igo ids to be populated later
            values.add(sampleName);
            values.add(sampleName);
            values.add("");// add empty value for score
            for (String mark : markers) {

                values.add(getValueFromMap(sampleParams, mark));
            }
        }
        return values;
    }

    /**
     * Method to get data from API aggregated by sample.
     * @param dataFromApi
     * @return Map<String, Map<String,Map<String, Object>>>
     * @throws ParseException
     * @throws ServerException
     */
    public Map<String, Map<String,Map<String, Object>>> getSampleDataFromApiData(JSONArray dataFromApi) throws ParseException{
        //map of matching accession and related marker and values
        Map<String, Map<String,Map<String, Object>>> hitsData = new HashMap<>();
        for (Object o : dataFromApi) {
            JSONObject jsonObject = (JSONObject) parser.parse(o.toString());
            String sampleName = getValueFromJsonObj(jsonObject,"description");
            hitsData.putIfAbsent(sampleName, new HashMap<>());
            JSONArray results = (JSONArray) jsonObject.get("results");
            for (Object r : results){
                JSONObject rObj = (JSONObject) parser.parse(r.toString());
                JSONArray profiles = (JSONArray) rObj.get("profiles");
                String accession = getValueFromJsonObj(rObj, "accession");
                String name = getValueFromJsonObj(rObj,"name");
                String species = getValueFromJsonObj(rObj,"species");
                Map<String, Object> accessionValues = new HashMap<>();
                hitsData.get(sampleName).putIfAbsent(accession, accessionValues);
                hitsData.get(sampleName).get(accession).putIfAbsent("accession", accession);
                hitsData.get(sampleName).get(accession).putIfAbsent("name", name);
                hitsData.get(sampleName).get(accession).putIfAbsent("species",species);
                for (Object p : profiles){
                    JSONObject pObj = (JSONObject) parser.parse(p.toString());
                    JSONArray markers = (JSONArray) pObj.get("markers");
                    String score = pObj.get("score").toString();
                    hitsData.get(sampleName).get(accession).putIfAbsent("score",score);
                    for (Object m : markers){
                        JSONObject mObj = (JSONObject) parser.parse(m.toString());
                        JSONArray alleles = (JSONArray) mObj.get("alleles");
                        String markerName = getValueFromJsonObj(mObj, "name");
                        List<String> alleleValues = new ArrayList<>();
                        for (Object a : alleles){
                            JSONObject aObj = (JSONObject) parser.parse(a.toString());
                            String val = getValueFromJsonObj(aObj,"value");
                            Boolean matched = (Boolean) aObj.get("matched");
                            if (matched){
                                alleleValues.add( val);
                            }
                        }
                        hitsData.get(sampleName).get(accession).putIfAbsent("species",species);
                        hitsData.get(sampleName).get(accession).putIfAbsent(markerName,StringUtils.join(alleleValues,","));
                    }
                }
            }
        }
        return hitsData;
    }
}
