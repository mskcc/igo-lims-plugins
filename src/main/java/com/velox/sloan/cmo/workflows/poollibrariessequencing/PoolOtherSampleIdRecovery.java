package com.velox.sloan.cmo.workflows.poollibrariessequencing;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

/**
 * Recovers the OtherSampleId on pool-of-pools children before they are named.
 *
 * Background (IGODATA-589): in the pooling workflow a pool-of-pools child (a Pooled Library whose
 * parents are themselves pools) can reach the naming step before its OtherSampleId has propagated
 * down from its parent pools. It therefore arrives empty, the naming logic cannot build a proper
 * name from it, and the pool keeps a bare 'Pool-&lt;recordId&gt;' name (e.g. Pool-16064 instead of
 * Pool-13893_AA-F1). Verified in prod (DB): the parent pools already hold the full constituent list
 * in their OtherSampleId at that point; only the copy-down to the child is late.
 *
 * Naming formula (per the IGO rename plugin): the base pool name is
 * 'Pool-{ProjectId}-{WellLocation}', with '_1' (and '_2', '_3', ...) appended as a uniqueness suffix
 * when a pool with that base name already exists. ProjectId is resolved from the pool's
 * sortedRequests (parent-request chain); WellLocation is derived by splitting the pool's
 * OtherSampleId on ',' and looking up each constituent sample. If OtherSampleId is empty, the
 * constituent lookup returns nothing and the pool keeps the bare 'Pool-&lt;recordId&gt;' name that
 * Sapio's built-in POOL MAKER (com.velox.plugin.workflows.poolmaker.CreateSamplePoolsSubmit)
 * assigned when it created the record. The pretty rename is IGO custom code, registered on Review
 * Pool Assignments as 'EXECUTE CUSTOM PLUGIN = 63, 46' (plugin 46 emits the '[renameSampleRecords]'
 * log lines).
 *
 * What this plugin does: for each attached Pooled Library whose OtherSampleId is empty, it walks the
 * parent pools (getParentsOfType("Sample", ...)), concatenates their OtherSampleId values, and writes
 * the rebuilt value back on the child so the existing downstream rename can name it normally. Pools
 * that already have a populated OtherSampleId are left untouched.
 *
 * Safety: this only ever repairs records that are already empty (working pools are untouched); if a
 * child has no usable parent data it is left exactly as-is (no name change, no error); and any
 * exception is swallowed so the repair can never fail the workflow. Gated on a task option so it only
 * runs where explicitly enabled. Runs at task submit, ordered before the rename so the rebuilt value
 * is in place when the rename reads it.
 *
 * Logging: this plugin logs verbosely on purpose. Because the underlying bug only occurs in prod and
 * cannot be reproduced in dev, the logs are the only way to confirm what happened. Every run emits a
 * START line, a per-pool classification, a parentDiag line per parent, an explicit outcome per empty
 * pool (RECOVERED / RECOVERY FAILED / RECOVERY SKIPPED / RECOVERY ERROR), and a SUMMARY line with
 * counts. Grep the log for "[PoolOtherSampleIdRecovery]" to see exactly what a run did.
 *
 * @author patelo2
 */
public class PoolOtherSampleIdRecovery extends DefaultGenericPlugin {

    private static final String LOG_PREFIX = "[PoolOtherSampleIdRecovery]";
    private static final String TASK_OPTION = "RECOVER POOL OTHERSAMPLEID";
    private static final String POOLED_LIBRARY = "Pooled Library";
    private static final String OTHER_SAMPLE_ID = "OtherSampleId";
    private static final String SAMPLE_ID = "SampleId";
    private static final String SAMPLE_TYPE = "ExemplarSampleType";
    private static final String SAMPLE_DATA_TYPE = "Sample";

    /**
     * Registers the plugin on task submit at {@link PluginOrder#EARLY}. Ordering rationale: on
     * Review Pool Assignments, Sapio's built-in POOL MAKER (CreateSamplePoolsSubmit, triggered by
     * the 'POOL MAKER = CREATE Sample POOLS' option) creates the pool records with bare
     * 'Pool-&lt;recordId&gt;' names first; the IGO custom rename plugin (plugin 46 in
     * 'EXECUTE CUSTOM PLUGIN = 63, 46') then reads each pool's OtherSampleId to derive WellLocation
     * (ProjectId comes from the pool's sortedRequests) and rewrites the name. This plugin needs to
     * run in between - after pool creation, before the rename - so the child's OtherSampleId is
     * populated when the rename reads it.
     * EARLY is a safe first placement; if the first prod run's logs show
     * '[PoolOtherSampleIdRecovery] START' arriving after '[renameSampleRecords] START', bump the
     * order earlier or move to a preceding hook.
     */
    public PoolOtherSampleIdRecovery() {
        setTaskSubmit(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    /**
     * Runs only when the task is not already complete AND the task option
     * '{@value #TASK_OPTION}' is present. The option is the deploy gate: if it is not set on a
     * task, this plugin does nothing anywhere, so the jar can be deployed with zero behavior
     * change until the option is added to Review Pool Assignments.
     *
     * @return {@code true} when the plugin should fire on this task submit
     */
    @Override
    public boolean shouldRun() throws RemoteException {
        boolean enabled = activeTask.getStatus() != activeTask.COMPLETE
                && activeTask.getTask().getTaskOptions().containsKey(TASK_OPTION);
        // shouldRun is framework-called; keep it side-effect free apart from this trace.
        if (enabled) {
            logInfo(LOG_PREFIX + " shouldRun=true (task option '" + TASK_OPTION + "' present).");
        }
        return enabled;
    }

    /**
     * Classifies each attached Sample and, for empty Pooled Library pools, delegates to
     * {@link #recoverOtherSampleIdFromParents(DataRecord)}; logs a per-category SUMMARY at the end.
     * Always returns {@code PluginResult(true)} - any exception is caught and swallowed so this
     * repair step can never fail the workflow.
     */
    @Override
    public PluginResult run() throws ServerException, RemoteException {
        long taskId = -1L;
        try {
            try {
                taskId = activeTask.getTask().getTaskId();
            } catch (Exception ignore) {
                // taskId is only for logging context; ignore if unavailable.
            }
            logInfo(LOG_PREFIX + " START taskId=" + taskId + " option='" + TASK_OPTION + "'.");

            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords(SAMPLE_DATA_TYPE, user);
            if (attachedSamples == null || attachedSamples.isEmpty()) {
                logInfo(LOG_PREFIX + " No Sample records attached to this task; nothing to do. END.");
                return new PluginResult(true);
            }
            logInfo(LOG_PREFIX + " Attached Sample record count=" + attachedSamples.size() + ".");

            int totalSamples = attachedSamples.size();
            int nonPool = 0;         // not a Pooled Library
            int alreadyNamed = 0;    // pool with OtherSampleId already populated
            int emptyPools = 0;      // pool-of-pools candidates (empty OtherSampleId)
            int recovered = 0;       // successfully rebuilt + written back
            int failed = 0;          // parents present but no usable data
            int skipped = 0;         // no Sample parents found
            int errored = 0;         // exception while recovering this record

            for (DataRecord pool : attachedSamples) {
                long recId = safeRecordId(pool);
                String sampleType = pool.getStringVal(SAMPLE_TYPE, user);

                if (!POOLED_LIBRARY.equalsIgnoreCase(sampleType)) {
                    nonPool++;
                    logInfo(LOG_PREFIX + " SKIP-NONPOOL recordId=" + recId
                            + " sampleType='" + sampleType + "' (not a Pooled Library).");
                    continue;
                }

                String currentOsi = pool.getStringVal(OTHER_SAMPLE_ID, user);
                if (StringUtils.isNotBlank(currentOsi)) {
                    alreadyNamed++;
                    logInfo(LOG_PREFIX + " SKIP-POPULATED recordId=" + recId
                            + " sampleId='" + pool.getStringVal(SAMPLE_ID, user)
                            + "' (OtherSampleId already present; untouched).");
                    continue;
                }

                // This is an empty pool-of-pools candidate: attempt recovery.
                emptyPools++;
                logInfo(LOG_PREFIX + " EMPTY-CANDIDATE recordId=" + recId
                        + " sampleId='" + pool.getStringVal(SAMPLE_ID, user)
                        + "' (OtherSampleId empty; attempting recovery).");

                RecoveryOutcome outcome = recoverOtherSampleIdFromParents(pool);
                switch (outcome) {
                    case RECOVERED:    recovered++; break;
                    case FAILED:       failed++;    break;
                    case NO_PARENTS:   skipped++;   break;
                    case ERROR:        errored++;   break;
                    default: break;
                }
            }

            logInfo(LOG_PREFIX + " SUMMARY taskId=" + taskId
                    + " totalAttached=" + totalSamples
                    + " nonPool=" + nonPool
                    + " alreadyPopulated=" + alreadyNamed
                    + " emptyCandidates=" + emptyPools
                    + " recovered=" + recovered
                    + " failedNoParentData=" + failed
                    + " skippedNoParents=" + skipped
                    + " errored=" + errored + ".");
            logInfo(LOG_PREFIX + " END taskId=" + taskId + ".");
            return new PluginResult(true);

        } catch (Exception e) {
            // Never fail the task on account of this repair step; log and let the workflow proceed.
            logError(LOG_PREFIX + " UNEXPECTED ERROR at run() level; leaving records unchanged and"
                    + " allowing the workflow to proceed. CAUSE:\n" + ExceptionUtils.getStackTrace(e));
            return new PluginResult(true);
        }
    }

    /** Outcome of recovering one empty pool: rebuilt, parents-had-no-data, no-parents, or errored. */
    private enum RecoveryOutcome { RECOVERED, FAILED, NO_PARENTS, ERROR }

    /**
     * Rebuild a single empty pool's OtherSampleId from its parent pools and write it back.
     *
     * For each parent found via {@code pool.getParentsOfType("Sample", user)}, reads the parent's
     * OtherSampleId, splits it on ',', trims tokens, drops blanks, and collects them into a
     * {@link LinkedHashSet} to preserve parent-iteration order and drop duplicates. If any usable
     * constituents were collected, joins them back with ',' and writes the rebuilt string to the
     * child's OtherSampleId field.
     *
     * Logs a {@code parentDiag} line for every parent inspected (with the {@code hasOtherSampleId}
     * flag and the raw parent value) and exactly one outcome line whose category matches the
     * returned {@link RecoveryOutcome}.
     *
     * @param pool the empty Pooled Library child whose OtherSampleId is being rebuilt
     * @return one of {@link RecoveryOutcome#RECOVERED}, {@link RecoveryOutcome#FAILED},
     *         {@link RecoveryOutcome#NO_PARENTS}, or {@link RecoveryOutcome#ERROR}
     */
    private RecoveryOutcome recoverOtherSampleIdFromParents(DataRecord pool) {
        long childId = safeRecordId(pool);
        try {
            List<DataRecord> parents = pool.getParentsOfType(SAMPLE_DATA_TYPE, user);
            if (parents == null || parents.isEmpty()) {
                logInfo(LOG_PREFIX + " RECOVERY SKIPPED childRecordId=" + childId
                        + " -> no Sample parents found (nothing to rebuild from).");
                return RecoveryOutcome.NO_PARENTS;
            }
            logInfo(LOG_PREFIX + " childRecordId=" + childId + " parentCount=" + parents.size() + ".");

            // Preserve order, drop duplicates and blanks.
            LinkedHashSet<String> parts = new LinkedHashSet<>();
            int parentsWithData = 0;
            for (DataRecord parent : parents) {
                if (parent == null) {
                    continue;
                }
                long parentId = safeRecordId(parent);
                String parentSampleId = parent.getStringVal(SAMPLE_ID, user);
                String parentOsi = parent.getStringVal(OTHER_SAMPLE_ID, user);
                boolean hasData = StringUtils.isNotBlank(parentOsi);
                logInfo(LOG_PREFIX + " parentDiag childRecordId=" + childId
                        + " parentRecordId=" + parentId
                        + " parentSampleId='" + parentSampleId + "'"
                        + " hasOtherSampleId=" + hasData
                        + " parentOtherSampleId='" + parentOsi + "'");
                if (hasData) {
                    parentsWithData++;
                    for (String token : parentOsi.split(",", -1)) {
                        String t = token.trim();
                        if (StringUtils.isNotBlank(t)) {
                            parts.add(t);
                        }
                    }
                }
            }

            if (parts.isEmpty()) {
                logInfo(LOG_PREFIX + " RECOVERY FAILED childRecordId=" + childId
                        + " -> " + parents.size() + " parent(s) inspected, " + parentsWithData
                        + " had OtherSampleId, but no usable constituents were found"
                        + " (parents may still be mid-assembly). Leaving record unchanged.");
                return RecoveryOutcome.FAILED;
            }

            String rebuilt = String.join(",", parts);
            String before = pool.getStringVal(OTHER_SAMPLE_ID, user);
            pool.setDataField(OTHER_SAMPLE_ID, rebuilt, user);
            logInfo(LOG_PREFIX + " RECOVERED childRecordId=" + childId
                    + " sampleId='" + pool.getStringVal(SAMPLE_ID, user) + "'"
                    + " parentsUsed=" + parentsWithData + "/" + parents.size()
                    + " constituentCount=" + parts.size()
                    + " oldOtherSampleId='" + before + "'"
                    + " newOtherSampleId='" + rebuilt + "'");
            return RecoveryOutcome.RECOVERED;

        } catch (Exception e) {
            logError(LOG_PREFIX + " RECOVERY ERROR childRecordId=" + childId
                    + " -> leaving record unchanged. CAUSE:\n" + ExceptionUtils.getStackTrace(e));
            return RecoveryOutcome.ERROR;
        }
    }

    /** Record ID for logging, or -1L if getRecordId() throws (keeps log lines robust). */
    private long safeRecordId(DataRecord r) {
        try {
            return r.getRecordId();
        } catch (Exception ignore) {
            return -1L;
        }
    }
}