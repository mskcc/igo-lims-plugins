package com.velox.sloan.cmo.workflows.workflows;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plugin to validate Illumina sequencer run modes and the selected sequencer names.
 * The plugin ensures the user-provided Run Mode exists in the approved list and
 * the chosen sequencer belongs to the set of permitted sequencers for that mode.
 */
public class IlluminaRunModeValidator extends DefaultGenericPlugin {

    private static final String TASK_OPTION_VALIDATE = "VALIDATE ILLUMINA RUN MODE";
    private static final String DATA_TYPE_NAME = "IlluminaSeqExperiment";
    private static final String RUN_MODE_FIELD = "SequencingRunMode";
    private static final String[] SEQUENCER_FIELDS = {"Instrumentname", "SequencerInstrument"};

    private static final Map<String, Set<String>> RUN_MODE_TO_SEQUENCERS = buildRunModeMap();

    public IlluminaRunModeValidator() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE
                && activeTask.getTask().getTaskOptions().containsKey(TASK_OPTION_VALIDATE);
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> records = activeTask.getAttachedDataRecords(DATA_TYPE_NAME, user);
            if (records.isEmpty()) {
                clientCallback.displayWarning("No records attached to validate Illumina run modes.");
                return new PluginResult(false);
            }

            List<String> issues = new ArrayList<>();

            for (DataRecord record : records) {
                Optional<String> runModeOpt = getFieldValue(record, RUN_MODE_FIELD);
                Optional<String> sequencerOpt = getFirstPopulatedValue(record, SEQUENCER_FIELDS);

                if (!runModeOpt.isPresent()) {
                    issues.add(buildIssueMessage(record, "Illumina Sequencing Run Mode is required but not provided."));
                    continue;
                }

                String normalizedRunMode = normalize(runModeOpt.get());
                if (!RUN_MODE_TO_SEQUENCERS.containsKey(normalizedRunMode)) {
                    issues.add(buildIssueMessage(record, String.format(
                            "Invalid Illumina Sequencing Run Mode: '%s'.\n" +
                            "Please select a valid run mode from the approved list:\n%s",
                            runModeOpt.get(),
                            getApprovedRunModesList())));
                    continue;
                }

                Set<String> allowedSequencers = RUN_MODE_TO_SEQUENCERS.get(normalizedRunMode);
                if (allowedSequencers.isEmpty()) {
                    // Run mode validated, no specific sequencer restriction.
                    continue;
                }

                if (!sequencerOpt.isPresent()) {
                    issues.add(buildIssueMessage(record, String.format(
                            "Illumina Sequencer is required for run mode '%s'.\n" +
                            "Please select one of the following sequencers: %s",
                            runModeOpt.get(),
                            String.join(", ", allowedSequencers))));
                    continue;
                }

                List<String> submittedSequencers = splitValues(sequencerOpt.get());
                List<String> invalidSequencers = submittedSequencers.stream()
                        .filter(value -> !allowedSequencers.contains(normalize(value)))
                        .collect(Collectors.toList());

                if (!invalidSequencers.isEmpty()) {
                    issues.add(buildIssueMessage(record, String.format(
                            "Invalid Illumina Sequencer(s) for run mode '%s': %s\n" +
                            "Allowed sequencers for this run mode: %s",
                            runModeOpt.get(),
                            String.join(", ", invalidSequencers),
                            String.join(", ", allowedSequencers))));
                }
            }

            if (!issues.isEmpty()) {
                String warningMessage = "⚠️ ILLUMINA SEQUENCING WORKFLOW VALIDATION FAILED ⚠️\n\n" +
                        "The following validation errors were found:\n\n" +
                        String.join("\n\n" + "─".repeat(80) + "\n\n", issues) +
                        "\n\n" + "─".repeat(80) + "\n\n" +
                        "Please correct these issues before proceeding with the Illumina Sequencing workflow.";
                clientCallback.displayWarning(warningMessage);
                return new PluginResult(false);
            }

        } catch (Exception e) {
            logError(String.format("Error validating Illumina run modes: %s", e.getMessage()));
            clientCallback.displayError(String.format("Error validating Illumina Sequencing workflow:\n%s", e));
            return new PluginResult(false);
        }

        return new PluginResult(true);
    }

    private Optional<String> getFieldValue(DataRecord record, String fieldName) {
        try {
            Object value = record.getValue(fieldName, user);
            if (value != null && !StringUtils.isBlank(value.toString())) {
                return Optional.of(value.toString());
            }
        } catch (Exception e) {
            // Field not found or not accessible
        }
        return Optional.empty();
    }

    private Optional<String> getFirstPopulatedValue(DataRecord record, String[] fieldNames) {
        for (String fieldName : fieldNames) {
            Optional<String> value = getFieldValue(record, fieldName);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return StringUtils.normalizeSpace(value).toUpperCase();
    }

    private List<String> splitValues(String value) {
        return Arrays.stream(value.split(",|;|\\n"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private String buildIssueMessage(DataRecord record, String detail) {
        return String.format("Record ID: %s (Data Type: %s)\n%s",
                record.getRecordId(),
                record.getDataTypeName(),
                detail);
    }

    private String getApprovedRunModesList() {
        List<String> runModes = new ArrayList<>(RUN_MODE_TO_SEQUENCERS.keySet());
        Collections.sort(runModes);
        return "  • " + String.join("\n  • ", runModes);
    }

    private static Map<String, Set<String>> buildRunModeMap() {
        Map<String, Set<String>> map = new HashMap<>();
        addEntry(map, "HiSeq High Output");
        addEntry(map, "HiSeq Rapid Run");
        addEntry(map, "HiSeq X");
        addEntry(map, "MiSeq", "AYYAN", "JOHNSAWYERS");
        addEntry(map, "NextSeq");
        addEntry(map, "NextSeq 1000 P1", "AMELIE");
        addEntry(map, "NextSeq 1000 P2", "AMELIE");
        addEntry(map, "NextSeq 2000");
        addEntry(map, "NextSeq 2000 P1", "AMELIE", "PEPE");
        addEntry(map, "NextSeq 2000 P2", "AMELIE", "PEPE");
        addEntry(map, "NextSeq 2000 P3", "PEPE");
        addEntry(map, "NextSeq 2000 P4", "PEPE");
        addEntry(map, "NovaSeq SP", "RUTH");
        addEntry(map, "NovaSeq S1", "RUTH");
        addEntry(map, "NovaSeq S2", "RUTH");
        addEntry(map, "NovaSeq S3");
        addEntry(map, "NovaSeq S4", "RUTH");
        addEntry(map, "NovaSeq X 1.5B", "BONO", "FAUCI2");
        addEntry(map, "NovaSeq X 10B", "BONO", "FAUCI2");
        addEntry(map, "NovaSeq X 25B", "BONO", "FAUCI2");
        return Collections.unmodifiableMap(map);
    }

    private static void addEntry(Map<String, Set<String>> map, String runMode, String... sequencers) {
        Set<String> values = Arrays.stream(sequencers)
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(IlluminaRunModeValidator::normalize)
                .collect(Collectors.toCollection(HashSet::new));
        map.put(normalize(runMode), Collections.unmodifiableSet(values));
    }
}