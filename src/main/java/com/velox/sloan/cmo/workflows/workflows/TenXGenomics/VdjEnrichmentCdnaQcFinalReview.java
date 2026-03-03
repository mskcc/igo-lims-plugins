package com.velox.sloan.cmo.workflows.workflows.TenXGenomics;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.managers.TaskUtilManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

/**
 * Plugin to implement the Final Review step for VDJ Enrichment cDNA QC in the 10x Genomics workflow.
 * PASS samples proceed to next step. TRY and FAILED samples move to Pending User Decision queue.
 *
 * @author patelo2
 */
public class VdjEnrichmentCdnaQcFinalReview extends DefaultGenericPlugin {
    private static final List<String> PASS_DECISIONS = Arrays.asList("Passed", "Pass");
    private static final List<String> TRY_DECISIONS = Arrays.asList("Try");
    private static final List<String> FAIL_DECISIONS = Arrays.asList("Failed", "Fail");
    private static final String PENDING_USER_DECISION_QUEUE = "Pending User Decision";

    public VdjEnrichmentCdnaQcFinalReview() {
        setTaskEntry(true);
        setLine1Text("VDJ QC");
        setLine2Text("Final Review");
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("VDJ ENRICHMENT CDNA QC FINAL REVIEW")
                && activeTask.getStatus() != ActiveTask.COMPLETE
                && !activeTask.getTask().getTaskOptions().containsKey("_VDJ_FINAL_REVIEW_COMPLETE");
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);

            if (attachedSamples.isEmpty()) {
                clientCallback.displayError("No samples found attached to this task.");
                logError("No samples found attached to this task.");
                return new PluginResult(false);
            }

            logInfo(String.format("Processing %d samples for VDJ Enrichment cDNA QC Final Review", attachedSamples.size()));

            // Categorize samples based on QC decision
            List<DataRecord> samplesPass = new ArrayList<>();
            List<DataRecord> samplesToPendingQueue = new ArrayList<>();

            for (DataRecord sample : attachedSamples) {
                String sampleId = sample.getStringVal("SampleId", user);
                if (StringUtils.isBlank(sampleId)) {
                    logError(String.format("Sample with recordId %d has no SampleId, skipping", sample.getRecordId()));
                    continue;
                }

                String igoRecommendation = getIgoQcRecommendation(sample);
                logInfo(String.format("Sample %s - IGO Recommendation: '%s'", sampleId, igoRecommendation));

                if (isPassDecision(igoRecommendation)) {
                    samplesPass.add(sample);
                } else if (isTryDecision(igoRecommendation) || isFailDecision(igoRecommendation)) {
                    samplesToPendingQueue.add(sample);
                } else {
                    clientCallback.displayError(String.format("Sample %s has no IGO QC Recommendation. Please set Pass/Try/Failed before proceeding.", sampleId));
                    logError(String.format("Sample %s has invalid/missing IGO recommendation '%s'", sampleId, igoRecommendation));
                    return new PluginResult(false);
                }
            }

            logInfo(String.format("Categorization: PASS=%d, TRY/FAILED=%d", samplesPass.size(), samplesToPendingQueue.size()));

            // Move TRY/FAILED samples to Pending User Decision process queue
            if (!samplesToPendingQueue.isEmpty()) {
                if (!moveSamplesToPendingQueue(samplesToPendingQueue)) {
                    clientCallback.displayError("Failed to move TRY/FAILED samples to Pending User Decision queue. Please try again.");
                    return new PluginResult(false);
                }
            }

            // Mark the final review as complete to prevent re-run
            activeTask.getTask().getTaskOptions().put("_VDJ_FINAL_REVIEW_COMPLETE", "");

            logInfo(String.format("Final Review complete. PASS=%d (proceeding), TRY/FAILED=%d (moved to pending queue)",
                    samplesPass.size(), samplesToPendingQueue.size()));

            return new PluginResult(true);

        } catch (NotFound e) {
            String errMsg = String.format("NotFound error during VDJ Enrichment cDNA QC Final Review: %s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException during VDJ Enrichment cDNA QC Final Review: %s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
    }

    /**
     * Move Try/Failed samples to the Pending User Decision process queue.
     * For each sample, this method:
     * 1. Creates one AssignedProcess record with ProcessName = 'Pending User Decision'.
     * 2. Updates the sample's ExemplarSampleStatus to 'Ready for - Pending User Decision'.
     * 3. Removes any orphaned TenXLibraryPrepProtocol1 records created by 'CREATE PROTOCOLS ENTRY'
     *    for these samples, to prevent downstream errors in the aliquot maker.
     * 4. Removes the samples and their protocol records from ALL tasks in the active workflow.
     * @param samples List of Try/Failed samples to move
     * @return true if successful, false otherwise
     */
    private boolean moveSamplesToPendingQueue(List<DataRecord> samples) {
        try {
            logInfo(String.format("Assigning %d samples to '%s' process queue", samples.size(), PENDING_USER_DECISION_QUEUE));
            
            // Create one AssignedProcess record per sample (one-to-one relationship)
            for (DataRecord sample : samples) {
                String sampleId = sample.getStringVal("SampleId", user);
                String otherSampleId = sample.getStringVal("OtherSampleId", user);
                long sampleRecordId = sample.getRecordId();
                
                // Get RequestRecordId from the sample's parent Request
                long requestRecordId = 0;
                String requestId = sample.getStringVal("RequestId", user);
                if (!StringUtils.isBlank(requestId)) {
                    List<DataRecord> requests = dataRecordManager.queryDataRecords("Request", "RequestId = '" + requestId + "'", user);
                    if (!requests.isEmpty()) {
                        requestRecordId = requests.get(0).getRecordId();
                    }
                }
                
                // Create a new AssignedProcess record for this sample
                DataRecord assignedProcess = dataRecordManager.addDataRecord("AssignedProcess", user);
                Map<String, Object> processValues = new HashMap<>();
                processValues.put("ProcessName", PENDING_USER_DECISION_QUEUE);
                processValues.put("ProcessStepNumber", 1);
                processValues.put("SampleId", sampleId);
                processValues.put("SampleRecordId", sampleRecordId);
                processValues.put("OtherSampleId", otherSampleId);
                processValues.put("RequestRecordId", requestRecordId);
                processValues.put("Status", "Ready for - Pending User Decision");
                assignedProcess.setFields(processValues, user);
                
                // Add sample as child of AssignedProcess (AP is parent of Sample)
                assignedProcess.addChild(sample, user);
                
                // Update sample's ExemplarSampleStatus to reflect the new queue
                sample.setDataField("ExemplarSampleStatus", "Ready for - Pending User Decision", user);
                
                logInfo(String.format("Created AssignedProcess for sample %s -> '%s' process queue (RequestRecordId: %d)", 
                    sampleId, PENDING_USER_DECISION_QUEUE, requestRecordId));
            }
            
            // Commit the changes
            dataRecordManager.storeAndCommit(
                String.format("Assigned %d samples to %s process queue", samples.size(), PENDING_USER_DECISION_QUEUE),
                null,
                user
            );
            
            // Build set of Try/Fail sample IDs for filtering protocol records
            Set<String> pendingSampleIds = new HashSet<>();
            for (DataRecord sample : samples) {
                pendingSampleIds.add(sample.getStringVal("SampleId", user));
            }

            // Collect protocol records created for Try/Fail samples by CREATE PROTOCOLS ENTRY
            
            List<DataRecord> protocolsToRemove = new ArrayList<>();
            try {
                List<DataRecord> attachedProtocols = activeTask.getAttachedDataRecords("TenXLibraryPrepProtocol1", user);
                for (DataRecord protocol : attachedProtocols) {
                    String protocolSampleId = protocol.getStringVal("SampleId", user);
                    if (pendingSampleIds.contains(protocolSampleId)) {
                        protocolsToRemove.add(protocol);
                        logInfo(String.format("Found orphaned protocol record %d for Try/Fail sample %s",
                            protocol.getRecordId(), protocolSampleId));
                    }
                }
            } catch (Exception ex) {
                logInfo(String.format("Note: Could not retrieve TenXLibraryPrepProtocol1 records: %s", ex.getMessage()));
            }

            // Remove samples AND their protocol records from ALL tasks in the workflow
            List<ActiveTask> allWorkflowTasks = activeWorkflow.getActiveTaskList();
            for (ActiveTask task : allWorkflowTasks) {
                try {
                    TaskUtilManager.removeRecordsFromTask(task, samples);
                    logInfo(String.format("Removed %d samples from task '%s'", samples.size(), task.getTaskName()));
                } catch (Exception ex) {
                    // Some tasks may not have these samples attached — that's OK, continue
                    logInfo(String.format("Note: Could not remove samples from task '%s': %s", task.getTaskName(), ex.getMessage()));
                }
                // Also remove protocol records for Try/Fail samples
                if (!protocolsToRemove.isEmpty()) {
                    try {
                        TaskUtilManager.removeRecordsFromTask(task, protocolsToRemove);
                        logInfo(String.format("Removed %d protocol records from task '%s'", protocolsToRemove.size(), task.getTaskName()));
                    } catch (Exception ex) {
                        logInfo(String.format("Note: Could not remove protocol records from task '%s': %s", task.getTaskName(), ex.getMessage()));
                    }
                }
            }
            
            logInfo(String.format("Successfully moved %d samples to '%s' process queue", samples.size(), PENDING_USER_DECISION_QUEUE));
            return true;
        } catch (Exception e) {
            logError(String.format("Error moving samples to pending queue: %s", ExceptionUtils.getStackTrace(e)));
            return false;
        }
    }

    /**
     * Get IGO QC Recommendation for a sample by querying QcReportDna directly by SampleId.
     * QcReportDna is the only authoritative table for VDJ Enrichment cDNA QC — the IGO scientist
     * sets the Pass/Try/Fail recommendation here after reviewing cDNA QC metrics.
     * Direct query is used (rather than parent/child hierarchy traversal) because it is more
     * reliable — a QC record may exist in the database without being formally linked to the
     * Sample record via the LIMS data model hierarchy.
     * @param sample DataRecord for the sample
     * @return recommendation value (Pass/Try/Fail) or empty string if not found
     */
    private String getIgoQcRecommendation(DataRecord sample) {
        try {
            String sampleId = sample.getStringVal("SampleId", user);

            // QcReportDna is the only authoritative table for VDJ Enrichment cDNA QC
            List<DataRecord> queriedQcReportDna = dataRecordManager.queryDataRecords(
                "QcReportDna", "SampleId = '" + sampleId + "'", user);
            for (DataRecord qcReport : queriedQcReportDna) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' via QcReportDna for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            logInfo(String.format("No IGO QC Recommendation found in QcReportDna for sample %s", sampleId));

        } catch (NotFound | RemoteException | IoError | ServerException e) {
            logError(String.format("Error getting IGO QC Recommendation for sample: %s", e.getMessage()));
        }
        return "";
    }

    /**
     * Extract IGO QC Recommendation from a QcReportDna record.
     * The DB field is IgoQcRecommendation 
    
     * @param record QcReportDna DataRecord
     * @return IGO recommendation value (Pass/Try/Fail) or empty string if not found
     */
    private String getIgoRecommendationFromRecord(DataRecord record) {
        try {
            Object value = record.getValue("IgoQcRecommendation", user);
            if (value != null && !StringUtils.isBlank(value.toString())) {
                return value.toString().trim();
            }
        } catch (NotFound | RemoteException e) {
            // Field not found on this record
        }
        return "";
    }

    /**
     * Check if decision is PASS (sample proceeds to next step).
     */
    private boolean isPassDecision(String decision) {
        for (String passDecision : PASS_DECISIONS) {
            if (decision.equalsIgnoreCase(passDecision)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if decision is TRY (sample held for later review).
     */
    private boolean isTryDecision(String decision) {
        for (String tryDecision : TRY_DECISIONS) {
            if (decision.equalsIgnoreCase(tryDecision)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if decision is FAIL (sample stopped).
     */
    private boolean isFailDecision(String decision) {
        for (String failDecision : FAIL_DECISIONS) {
            if (decision.equalsIgnoreCase(failDecision)) {
                return true;
            }
        }
        return false;
    }

}