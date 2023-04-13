package com.velox.sloan.cmo.workflows.workflows.TCRseq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.recmodels.SampleModel;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MixedSampleSpeciesValidator extends DefaultGenericPlugin {
    private final HashSet<String> MIXED_SAMPLESPECIES_TO_AVOID = new HashSet<>(Arrays.asList("human", "mouse"));

    public MixedSampleSpeciesValidator() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return this.activeTask.getTask().getTaskName().equalsIgnoreCase("create experiment") &&
                this.activeTask.getTask().getTaskOptions().containsKey("VALIDATE MIXED SAMPLE SPECIES");
    }

    public PluginResult run() throws RemoteException, ServerException {
        try {
            logInfo("I should run species validator plugin!!!");
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            Set<String> sampleSpeciesValues = new HashSet<>();
            for (DataRecord sample : samples) {
                Object sampleSapecies = sample.getValue(SampleModel.SPECIES, user);
                if (sampleSapecies == null || StringUtils.isBlank((String) sampleSapecies)) {
                    clientCallback.displayWarning(String.format("Sample Species value not found for sample %s", sample.getStringVal("SampleId", user)));
                } else {
                    sampleSpeciesValues.add(sampleSapecies.toString().toLowerCase());
                    logInfo(sampleSapecies.toString());
                }
            }
            if (sampleSpeciesValues.size() == 0) {
                logInfo("Sample species values not found on samples");
                clientCallback.displayWarning("Cannot validate Species for attached samples. Species values not found for samples in this task.");
                return new PluginResult(true);
            }

                boolean isUniqueSampleSpecies = sampleSpeciesValues.size() == 1;
                if (!isUniqueSampleSpecies && hasAllMixedSampleSpeciesToAvoid(sampleSpeciesValues)){
                    logInfo(String.format("Mixed SPECIES WARNING -> %s samples are being launched together.", MIXED_SAMPLESPECIES_TO_AVOID.toString()));
                    if (!clientCallback.showOkCancelDialog("SAMPLES HAVE MIXED SAMPLE SPECIES WHICH SHOULD NOT BE LAUNCHED TOGETHER!",
                            String.format("Samples launched in the workflow contain %s Species.\nDO YOU WANT TO CONTINUE?", MIXED_SAMPLESPECIES_TO_AVOID.toString()))) {
                        logInfo(String.format("User %s elected to cancel workflow %s to avoid 'Human' and 'Mouse' samples being launched together.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                        return new PluginResult(false);
                    } else {
                        logInfo(String.format("User %s elected to continue workflow %s regardless of mixed Sample Species warning.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                        return new PluginResult(true);
                    }
                }

        }
        catch(ServerException | RemoteException | NotFound e) {
            clientCallback.displayError(String.format("Error while validating Sample Species:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);

        }
        return new PluginResult(true);
    }

    /**
     * Method to check if a set contains all Sample Species values in another set.
     * @param sampleSpeciesValues values found on samples
     * @return boolean
     */
    private boolean hasAllMixedSampleSpeciesToAvoid(Set<String> sampleSpeciesValues){
        if (sampleSpeciesValues.size() > 1){
            sampleSpeciesValues.retainAll(MIXED_SAMPLESPECIES_TO_AVOID);
            return sampleSpeciesValues.size() == MIXED_SAMPLESPECIES_TO_AVOID.size();
        }
        return false;
    }
}
