package com.velox.sloan.cmo.workflows.strauthentication;

import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

public class GenerateStrReport extends DefaultGenericPlugin {

    private final String STR_REPORT_GENERATOR_TAG = "GENERATE STR REPORT";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public GenerateStrReport() {
        setTaskEntry(true);
        setTaskToolbar(true);
        setLine1Text("Generate");
        setLine2Text("STR Report");
        setIcon("com/velox/sloan/cmo/resources/import_32.gif");
        setOrder(PluginOrder.EARLY.getOrder()+1);
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(STR_REPORT_GENERATOR_TAG);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(STR_REPORT_GENERATOR_TAG) &&
                    activeTask.getStatus() == ActiveTask.COMPLETE;
        }catch (Throwable e){
            return false;
        }
    }

    @Override
    public PluginResult run() throws ServerException {
        try {
            //get file from user via upload dialog
            String uploadedFile = clientCallback.showFileDialog("Please upload Raw data files", null);
            if (!StringUtils.isBlank(uploadedFile)) {
                logInfo("User did not upload the file.");
                return new PluginResult(false);
            }

            //validate the uploaded file
            if (!isValidFile(uploadedFile)) {
                return new PluginResult(false);
            }
            //read data from uploaded file
            List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(uploadedFile));
            Map<String, Integer> headerValueMap = utils.getCsvHeaderValueMap(fileData);

            Map<String,Map<String, Object>> data = aggregateDataBySample(fileData, headerValueMap);
            getStrResults(convertSampleDataToJson(data));

        } catch (Exception e) {
            logInfo(Arrays.toString(e.getStackTrace()));
            clientCallback.displayError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }

        return new PluginResult(true);
    }

    private boolean isValidFile(String uploadedFile) throws ServerException, IOException {
        if (!utils.isCsvFile(uploadedFile)) {
            clientCallback.displayError(String.format("Not a valid csv file\n%s", uploadedFile));
            return false;
        }
        List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(uploadedFile));
        if (!utils.csvFileHasData(fileData)) {
            clientCallback.displayError(String.format("The uploaded file does not contain data\n%s", uploadedFile));
            return false;
        }
        return true;
    }

    private Map<String, Map<String, Object>> aggregateDataBySample(List<String> fileData, Map<String, Integer> headerValueMap) {
        Map<String, Map<String, Object>> sampleData = new HashMap<>();
        for (int i = 1; i < fileData.size(); i++) {
            List<String> rowData = Arrays.asList(fileData.get(i).split(","));
            Map<String, Object> data = new HashMap<>();
            String sampleName = rowData.get(headerValueMap.get("Sample Name")).trim();
            String marker = rowData.get(headerValueMap.get("Marker")).trim();
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
            data.put(marker, alleles);
            sampleData.putIfAbsent(sampleName, data);
            sampleData.get(sampleName).putIfAbsent(marker, alleles);
        }
        return sampleData;
    }

    private JSONArray convertSampleDataToJson(Map<String, Map<String, Object>> sampleData) {
        List<JSONObject> jsonObj = new ArrayList<>();
        for (String entry : sampleData.keySet()) {
            JSONObject obj = new JSONObject(sampleData.get(entry));
            jsonObj.add(obj);
        }
        return new JSONArray(jsonObj);
    }

    private JSONObject getStrResults(JSONArray sampleData) {
        try {
            String postUrl = "https://web.expasy.org/cellosaurus-str-search/api/batch";
            CloseableHttpClient httpClient    = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(postUrl);
            post.setEntity(new StringEntity(sampleData.toString()));
            post.setHeader("Content-type", "application/json");
            CloseableHttpResponse response = httpClient.execute(post);
            clientCallback.displayInfo(response.toString());
        } catch (Exception e) {
            logError(String.format("Error occured while querying end point for STR Data %s\n\n %s", sampleData.toString(), Arrays.toString(e.getStackTrace())));
        }
        return null;
    }
}
