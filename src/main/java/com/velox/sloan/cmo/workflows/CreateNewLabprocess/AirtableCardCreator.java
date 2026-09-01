package com.velox.sloan.cmo.workflows.CreateNewLabprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Plugin that fires on task entry of the first task in a new downstream lab process.
 * Reads Request metadata from LIMS and creates an Airtable card via the Airtable REST API.
 *
 * In Velox "Create New Lab Process for Samples" workflows the task has Samples attached
 * (not a Request record directly), so the plugin reads fields off the Sample records and
 * groups them by RequestId. If a Request record happens to be attached instead (other
 * downstream workflows), that path is also handled.
 *
 * Task option required on the LIMS task: CREATE AIRTABLE CARD
 */
public class AirtableCardCreator extends DefaultGenericPlugin {

    private static final String AIRTABLE_ENV = "/srv/www/sapio/lims/lims-scripts/airtable/.env";
    private static final long DUE_DATE_OFFSET_MS = 28L * 24 * 60 * 60 * 1000; // 4 weeks

    public AirtableCardCreator() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException, ServerException {
        return activeTask.getTask().getTaskOptions().containsKey("CREATE AIRTABLE CARD")
                && activeTask.getStatus() != ActiveTask.COMPLETE;
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            // Primary path: "Create New Lab Process" tasks attach Samples, not Requests.
            // Group samples by RequestId so we create exactly one card per downstream request.
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            if (!samples.isEmpty()) {
                processFromSamples(samples);
                return new PluginResult(true);
            }

            // Fallback path: some downstream workflows attach the Request record directly.
            List<DataRecord> requests = activeTask.getAttachedDataRecords("Request", user);
            if (!requests.isEmpty()) {
                processFromRequests(requests);
                return new PluginResult(true);
            }

            logInfo("AirtableCardCreator: no Sample or Request records attached — skipping.");
        } catch (Exception e) {
            // Non-fatal — log and let the task proceed
            logError("AirtableCardCreator unexpected error: " + ExceptionUtils.getStackTrace(e));
        }
        return new PluginResult(true);
    }

    /**
     * Groups attached samples by RequestId, then fires one Airtable card per unique request.
     * Fields (recipe, materials, preservation) are read from the first sample in each group.
     */
    private void processFromSamples(List<DataRecord> samples) {
        // Collect per-requestId: (recipe, materials, preservation, count)
        Map<String, RequestInfo> byRequestId = new LinkedHashMap<>();

        for (DataRecord sample : samples) {
            String requestId   = getStringField(sample, "RequestId");
            if (StringUtils.isBlank(requestId)) continue;

            if (!byRequestId.containsKey(requestId)) {
                String recipe       = getStringField(sample, "Recipe");
                String materials    = getStringField(sample, "ExemplarSampleType"); // material type on sample
                String preservation = getStringField(sample, "Preservation");
                byRequestId.put(requestId, new RequestInfo(recipe, materials, preservation, 0));
            }
            byRequestId.get(requestId).count++;
        }

        for (Map.Entry<String, RequestInfo> entry : byRequestId.entrySet()) {
            String requestId   = entry.getKey();
            RequestInfo info   = entry.getValue();
            String error = createAirtableCard(requestId, info.recipe, info.materials,
                    info.preservation, info.count);
            if (error == null) {
                logInfo("AirtableCardCreator: card created for " + requestId
                        + " (" + info.count + " samples)");
            } else {
                logError("AirtableCardCreator: card creation FAILED for " + requestId + ": " + error);
                notifyUserOfFailure(requestId, error);
            }
        }
    }

    /**
     * Fallback when Request records are directly attached (other workflow configurations).
     */
    private void processFromRequests(List<DataRecord> requests) {
        for (DataRecord request : requests) {
            String requestId = getStringField(request, "RequestId");
            if (StringUtils.isBlank(requestId)) continue;

            String recipe       = getStringField(request, "Recipe");
            String materials    = getStringField(request, "RequestType");
            String preservation = getSamplePreservation(request);
            int    sampleCount  = 0;
            try {
                sampleCount = request.getChildrenOfType("Sample", user).length;
            } catch (Exception e) {
                logError("AirtableCardCreator: could not count samples: " + e.getMessage());
            }

            String error = createAirtableCard(requestId, recipe, materials, preservation, sampleCount);
            if (error == null) {
                logInfo("AirtableCardCreator: card created for " + requestId);
            } else {
                logError("AirtableCardCreator: card creation FAILED for " + requestId + ": " + error);
                notifyUserOfFailure(requestId, error);
            }
        }
    }

    private void notifyUserOfFailure(String requestId, String error) {
        try {
            clientCallback.displayError("Airtable card was not created for request "
                    + requestId + ": " + error);
        } catch (RemoteException | ServerException e) {
            logError("AirtableCardCreator: could not display error popup: " + e.getMessage());
        }
    }

    private static class RequestInfo {
        String recipe, materials, preservation;
        int count;
        RequestInfo(String recipe, String materials, String preservation, int count) {
            this.recipe = recipe; this.materials = materials;
            this.preservation = preservation; this.count = count;
        }
    }

    // ── Airtable ──────────────────────────────────────────────────────────────

    /** Returns null on success, or a human-readable failure reason for the client popup. */
    private String createAirtableCard(String requestId, String recipe, String materials,
                                       String preservation, int sampleCount) {
        try {
            Map<String, String> cfg = readEnvFile(AIRTABLE_ENV);
            if (cfg == null) {
                return "Airtable config file not found or unreadable on the LIMS server.";
            }

            String token   = cfg.get("AIRTABLE_API_TOKEN");
            String baseId  = cfg.get("AIRTABLE_BASE_ID");
            String tableId = cfg.get("AIRTABLE_TABLE_ID");
            if (StringUtils.isAnyBlank(token, baseId, tableId)) {
                logError("AirtableCardCreator: missing credentials in .env");
                return "Airtable credentials are missing or incomplete on the LIMS server.";
            }

            String url = "https://api.airtable.com/v0/" + baseId + "/" + tableId;
            Map<String, Object> fields = buildFields(requestId, recipe, materials, preservation, sampleCount);
            String jsonBody = toAirtableJson(fields);
            logInfo("AirtableCardCreator JSON: " + jsonBody);

            int status = post(url, token, jsonBody);
            if (status == 200 || status == 201) return null;

            if (status != 422) {
                logError("AirtableCardCreator: unexpected HTTP status " + status);
                return "Airtable rejected the request (HTTP " + status + ").";
            }

            // 422 with invalid select options → retry without multi-select fields
            logError("AirtableCardCreator: 422 — retrying without Application/Material/Preservation");
            fields.remove("Application");
            fields.remove("Material");
            fields.remove("Preservation");
            status = post(url, token, toAirtableJson(fields));
            if (status == 200 || status == 201) return null;

            if (status != 422) {
                logError("AirtableCardCreator: unexpected HTTP status on retry " + status);
                return "Airtable rejected the request (HTTP " + status + ").";
            }

            // Still 422 for some other reason → fall back to a bare-minimum card with just the project number
            logError("AirtableCardCreator: 422 again — retrying with Task Name only");
            Map<String, Object> minimalFields = new LinkedHashMap<>();
            minimalFields.put("Task Name", fields.get("Task Name"));
            status = post(url, token, toAirtableJson(minimalFields));
            if (status == 200 || status == 201) return null;

            logError("AirtableCardCreator: unexpected HTTP status on minimal retry " + status);
            return "Airtable rejected the request even with minimal fields (HTTP " + status + ").";

        } catch (Exception e) {
            logError("AirtableCardCreator failed: " + e.getMessage());
            return "Could not reach Airtable: " + e.getMessage();
        }
    }

    private Map<String, Object> buildFields(String requestId, String recipe, String materials,
                                            String preservation, int sampleCount) {
        Map<String, Object> fields = new LinkedHashMap<>();

        fields.put("Task Name", requestId + " (" + sampleCount + ")");
        fields.put("iLab Comment", "");

        if (!isBlankOrNull(recipe))      fields.put("Application",  Collections.singletonList(recipe));
        if (!isBlankOrNull(materials))   fields.put("Material",     Collections.singletonList(materials));
        if (!isBlankOrNull(preservation)) fields.put("Preservation", Collections.singletonList(preservation));

        String dueDate = new SimpleDateFormat("yyyy-MM-dd")
                .format(new Date(System.currentTimeMillis() + DUE_DATE_OFFSET_MS));
        fields.put("Due Date", dueDate);
        fields.put("Board Column", "Sample Receiving");

        return fields;
    }

    private String toAirtableJson(Map<String, Object> fields) throws Exception {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("fields", fields);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", Collections.singletonList(record));
        return new ObjectMapper().writeValueAsString(body);
    }

    /** Returns HTTP status code. */
    private int post(String url, String token, String jsonBody) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.setHeader("Authorization", "Bearer " + token);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(jsonBody, "UTF-8"));

            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            logInfo("AirtableCardCreator response [" + status + "]: " + responseBody);
            return status;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> readEnvFile(String path) {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (FileNotFoundException e) {
            logError("AirtableCardCreator: .env not found at " + path);
            return null;
        } catch (IOException e) {
            logError("AirtableCardCreator: error reading .env: " + e.getMessage());
            return null;
        }
        return config;
    }

    private String getSamplePreservation(DataRecord request) {
        try {
            DataRecord[] samples = request.getChildrenOfType("Sample", user);
            if (samples.length > 0) {
                return samples[0].getStringVal("Preservation", user);
            }
        } catch (Exception e) {
            logError("AirtableCardCreator: could not read Preservation: " + e.getMessage());
        }
        return "";
    }

    private String getStringField(DataRecord record, String field) {
        try {
            Object val = record.getValue(field, user);
            return val != null ? val.toString() : "";
        } catch (Exception e) {
            logError("AirtableCardCreator: could not read field " + field + ": " + e.getMessage());
            return "";
        }
    }

    private boolean isBlankOrNull(String s) {
        return s == null || s.trim().isEmpty() || s.equalsIgnoreCase("NULL");
    }
}
