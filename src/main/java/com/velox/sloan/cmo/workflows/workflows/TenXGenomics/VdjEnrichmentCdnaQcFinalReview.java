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
     * Move samples to the Pending User Decision process queue.
     * Creates ONE AssignedProcess record PER sample (one-to-one relationship).
     * @param samples List of samples to move
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
                
                // Create a new AssignedProcess record for this sample
                DataRecord assignedProcess = dataRecordManager.addDataRecord("AssignedProcess", user);
                Map<String, Object> processValues = new HashMap<>();
                processValues.put("ProcessName", PENDING_USER_DECISION_QUEUE);
                processValues.put("SampleId", sampleId);
                processValues.put("SampleRecordId", sampleRecordId);
                processValues.put("OtherSampleId", otherSampleId);
                processValues.put("Status", "Ready for - Pending User Decision");  // Status for Work Queue display
                assignedProcess.setFields(processValues, user);
                
                // Add sample as child of AssignedProcess (AP is parent of Sample)
                assignedProcess.addChild(sample, user);
                
                logInfo(String.format("Created AssignedProcess for sample %s -> '%s' process queue", sampleId, PENDING_USER_DECISION_QUEUE));
            }
            
            // Commit the changes
            dataRecordManager.storeAndCommit(
                String.format("Assigned %d samples to %s process queue", samples.size(), PENDING_USER_DECISION_QUEUE), 
                null, 
                user
            );
            
            // Remove samples from current workflow task
            TaskUtilManager.removeRecordsFromTask(activeTask, samples);
            
            logInfo(String.format("Successfully moved %d samples to '%s' process queue", samples.size(), PENDING_USER_DECISION_QUEUE));
            return true;
        } catch (Exception e) {
            logError(String.format("Error moving samples to pending queue: %s", ExceptionUtils.getStackTrace(e)));
            return false;
        }
    }

    /**
     * Get IGO QC Recommendation for a sample from its QCProtocol or QcReport records.
     * Checks IGOQC field in QCProtocol and IgoQcRecommendation field in QcReportDna/Rna/Library.
     * @param sample DataRecord for the sample
     * @return recommendation value (Pass/Try/Fail) or empty string if not found
     */
    private String getIgoQcRecommendation(DataRecord sample) {
        try {
            String sampleId = sample.getStringVal("SampleId", user);

            // Check child QCProtocol records
            DataRecord[] qcProtocolRecords = sample.getChildrenOfType("QCProtocol", user);
            for (DataRecord qcProtocol : qcProtocolRecords) {
                String recommendation = getIgoRecommendationFromRecord(qcProtocol);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in child QCProtocol for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Check child QcReportDna records
            DataRecord[] qcReportDnaRecords = sample.getChildrenOfType("QcReportDna", user);
            for (DataRecord qcReport : qcReportDnaRecords) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in child QcReportDna for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Check child QcReportRna records
            DataRecord[] qcReportRnaRecords = sample.getChildrenOfType("QcReportRna", user);
            for (DataRecord qcReport : qcReportRnaRecords) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in child QcReportRna for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Check child QcReportLibrary records
            DataRecord[] qcReportLibraryRecords = sample.getChildrenOfType("QcReportLibrary", user);
            for (DataRecord qcReport : qcReportLibraryRecords) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in child QcReportLibrary for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Check parent QCProtocol records
            List<DataRecord> parentQcProtocols = sample.getParentsOfType("QCProtocol", user);
            for (DataRecord qcProtocol : parentQcProtocols) {
                String recommendation = getIgoRecommendationFromRecord(qcProtocol);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in parent QCProtocol for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Query QCProtocol by SampleId
            List<DataRecord> qcProtocols = dataRecordManager.queryDataRecords("QCProtocol", "SampleId = '" + sampleId + "'", user);
            for (DataRecord qcProtocol : qcProtocols) {
                String recommendation = getIgoRecommendationFromRecord(qcProtocol);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in queried QCProtocol for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Query QcReportDna by SampleId (fallback)
            List<DataRecord> queriedQcReportDna = dataRecordManager.queryDataRecords("QcReportDna", "SampleId = '" + sampleId + "'", user);
            for (DataRecord qcReport : queriedQcReportDna) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in queried QcReportDna for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Query QcReportRna by SampleId (fallback)
            List<DataRecord> queriedQcReportRna = dataRecordManager.queryDataRecords("QcReportRna", "SampleId = '" + sampleId + "'", user);
            for (DataRecord qcReport : queriedQcReportRna) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in queried QcReportRna for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            // Query QcReportLibrary by SampleId (fallback)
            List<DataRecord> queriedQcReportLib = dataRecordManager.queryDataRecords("QcReportLibrary", "SampleId = '" + sampleId + "'", user);
            for (DataRecord qcReport : queriedQcReportLib) {
                String recommendation = getIgoRecommendationFromRecord(qcReport);
                if (!StringUtils.isBlank(recommendation)) {
                    logInfo(String.format("Found IGO recommendation '%s' in queried QcReportLibrary for sample %s", recommendation, sampleId));
                    return recommendation;
                }
            }

            logInfo(String.format("No IGO QC Recommendation found for sample %s", sampleId));

        } catch (NotFound | RemoteException | IoError | ServerException e) {
            logError(String.format("Error getting IGO QC Recommendation for sample: %s", e.getMessage()));
        }
        return "";
    }

    /**
     * Extract IGO QC Recommendation from a QC record.
     * @param record QCProtocol or QcReport DataRecord
     * @return IGO recommendation value (Pass/Try/Fail) or empty string if not found
     */
    private String getIgoRecommendationFromRecord(DataRecord record) {
        // Field names for IGO QC Recommendation across different DataTypes
        // Note: InvestigatorDecision is intentionally NOT included - it's a separate field
        // where the PI/investigator can override IGO's recommendation
        String[] possibleFieldNames = {
            "IGOQC",                    // QCProtocol field
            "IgoQcRecommendation",      // QcReportDna/Rna/Library field
            "IgoRecommendation"         // Alternative name
        };
        for (String fieldName : possibleFieldNames) {
            try {
                Object value = record.getValue(fieldName, user);
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

}
