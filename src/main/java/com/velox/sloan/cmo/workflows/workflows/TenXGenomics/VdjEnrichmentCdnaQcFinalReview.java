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

                String decision = getInvestigatorDecision(sample);
                logInfo(String.format("Sample %s - Decision: '%s'", sampleId, decision));

                if (isPassDecision(decision)) {
                    samplesPass.add(sample);
                } else if (isTryDecision(decision) || isFailDecision(decision)) {
                    samplesToPendingQueue.add(sample);
                } else {
                    clientCallback.displayError(String.format("Sample %s has no IGOQC decision. Please set Pass/Try/Failed before proceeding.", sampleId));
                    logError(String.format("Sample %s has invalid/missing decision '%s'", sampleId, decision));
                    return new PluginResult(false);
                }
            }

            logInfo(String.format("Categorization: PASS=%d, TRY/FAILED=%d", samplesPass.size(), samplesToPendingQueue.size()));

            // Move TRY/FAILED samples to Pending User Decision queue (automatic, no user confirmation)
            if (!samplesToPendingQueue.isEmpty()) {
                if (!moveSamplesToPendingQueue(samplesToPendingQueue)) {
                    logError("Failed to move samples to Pending User Decision queue.");
                    removeSamplesFromTask(samplesToPendingQueue);
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
     * Move samples to the Pending User Decision process queue by removing them from current task.
     * @param samples List of samples to move
     * @return true if successful, false otherwise
     */
    private boolean moveSamplesToPendingQueue(List<DataRecord> samples) {
        try {
            logInfo(String.format("Removing %d samples from current task to move to '%s' queue", samples.size(), PENDING_USER_DECISION_QUEUE));
            TaskUtilManager.removeRecordsFromTask(activeTask, samples);
            logInfo(String.format("Successfully moved %d samples to '%s' queue", samples.size(), PENDING_USER_DECISION_QUEUE));
            return true;
        } catch (Exception e) {
            logError(String.format("Error moving samples to pending queue: %s", ExceptionUtils.getStackTrace(e)));
            return false;
        }
    }

    /**
     * Get IGO Recommendation for a sample from its QCProtocol records.
     * @param sample DataRecord for the sample
     * @return recommendation value or empty string if not found
     */
    private String getInvestigatorDecision(DataRecord sample) {
        try {
            String sampleId = sample.getStringVal("SampleId", user);

            // Check child QCProtocol records
            DataRecord[] qcProtocolRecords = sample.getChildrenOfType("QCProtocol", user);
            for (DataRecord qcProtocol : qcProtocolRecords) {
                String recommendation = getIgoRecommendationFromRecord(qcProtocol);
                if (!StringUtils.isBlank(recommendation)) {
                    return recommendation;
                }
            }

            // Check parent QCProtocol records
            List<DataRecord> parentQcProtocols = sample.getParentsOfType("QCProtocol", user);
            for (DataRecord qcProtocol : parentQcProtocols) {
                String recommendation = getIgoRecommendationFromRecord(qcProtocol);
                if (!StringUtils.isBlank(recommendation)) {
                    return recommendation;
                }
            }

            // Query QCProtocol by SampleId as last resort
            List<DataRecord> qcProtocols = dataRecordManager.queryDataRecords("QCProtocol", "SampleId = '" + sampleId + "'", user);
            for (DataRecord qcProtocol : qcProtocols) {
                String recommendation = getIgoRecommendationFromRecord(qcProtocol);
                if (!StringUtils.isBlank(recommendation)) {
                    return recommendation;
                }
            }

        } catch (NotFound | RemoteException | IoError | ServerException e) {
            logError(String.format("Error getting decision for sample: %s", e.getMessage()));
        }
        return "";
    }

    /**
     * Check multiple possible field names for IGO Recommendation on a QCProtocol record.
     * @param qcProtocol QCProtocol DataRecord
     * @return recommendation value or empty string if not found
     */
    private String getIgoRecommendationFromRecord(DataRecord qcProtocol) {
        String[] possibleFieldNames = {"IGOQC", "IgoQcRecommendation", "IgoRecommendation"};
        for (String fieldName : possibleFieldNames) {
            try {
                Object value = qcProtocol.getValue(fieldName, user);
                if (value != null && !StringUtils.isBlank(value.toString())) {
                    return value.toString().trim();
                }
            } catch (NotFound | RemoteException e) {
                // Field not found, try next one
            }
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

    /**
     * Remove samples from the current task.
     * @param samples List of samples to remove
     */
    private void removeSamplesFromTask(List<DataRecord> samples) throws ServerException, RemoteException {
        List<Long> recordIds = new ArrayList<>();
        for (DataRecord sample : samples) {
            recordIds.add(sample.getRecordId());
        }
        activeTask.removeTaskAttachments(recordIds);
        logInfo(String.format("Removed %d samples from current task", samples.size()));
    }
}
