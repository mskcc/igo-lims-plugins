package com.velox.sloan.cmo.workflows.workflows.capture;

import com.velox.api.datarecord.DataRecord;
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

/**
 * Plugin to validate that all the samples launched in the workflow have same recipe, species values.
 * If not true, user is displayed a warning along with options to cancel the workflow or continue running the workflow.
 * @author  sharmaa1 on 8/5/19.
 */
public class UniqueRecipeAndSpeciesValidator extends DefaultGenericPlugin {
    public UniqueRecipeAndSpeciesValidator() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskName().toLowerCase().equals("create experiment") && activeTask.getTask().getTaskOptions().containsKey("VALIDATE UNIQUE SAMPLE SPECIES AND RECIPE");
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            Set<String> recipes = new HashSet<>();
            Set<String> speciesValues = new HashSet<>();
            for (DataRecord samp : samples) {
                Object recipe = samp.getValue(SampleModel.RECIPE, user);
                Object species = samp.getValue(SampleModel.SPECIES, user);
                if (recipe == null || StringUtils.isBlank((String) recipe)) {
                    clientCallback.displayWarning(String.format("Recipe value not found for sample %s", samp.getStringVal("SampleId", user)));
                } else {
                    recipes.add((String) recipe);
                    logInfo(recipe.toString());
                }
                if (species == null || StringUtils.isBlank((String) species)) {
                    clientCallback.displayWarning(String.format("Species value not found for sample %s", samp.getStringVal("SampleId", user)));
                } else {
                    speciesValues.add((String) species);
                    logInfo(species.toString());
                }
            }
            if (recipes.size() == 0) {
                logInfo("Recipe values not found on samples");
                clientCallback.displayWarning("Cannot validate uniqie recipes. Recipe values not found for samples in this task.");
                return new PluginResult(true);
            }
            if (speciesValues.size() == 0) {
                logInfo("Species values not found on samples");
                clientCallback.displayWarning("Cannot validate unique species. Species values not found for samples in this task.");
                return new PluginResult(true);
            }

            boolean isUniqueRecipe = recipes.size() == 1;
            if (!isUniqueRecipe) {
                logInfo(String.format("Two or more Samples launched in this workflow have mixed recipes on Samples: %s", recipes.toString()));
                if (!clientCallback.showOkCancelDialog("SAMPLES HAVE DIFFERENT RECIPES!", "Two or more Samples launched in this workflow have different recipes: " + recipes.toString() + "\nDO YOU WANT TO CONTINUE?")) {
                    logInfo(String.format("User %s elected to cancel workflow %s because of mixed recipes on Samples.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(false);
                } else {
                    logInfo(String.format("User %s elected to continue workflow %s regardless of mixed recipes warning.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(true);
                }
            }

            boolean isUniqueSpecies = speciesValues.size() == 1;
            if (!isUniqueSpecies) {
                logInfo(String.format("Two or more Samples launched in this workflow have different species: %s", speciesValues.toString()));
                if (!clientCallback.showOkCancelDialog("SAMPLES HAVE DIFFERENT SPECIES!", "Two or more Samples launched in this workflow have different species: " + speciesValues.toString() + "\nDO YOU WANT TO CONTINUE?")) {
                    logInfo(String.format("User %s elected to cancel workflow %s because of mixed species Samples.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(false);
                } else {
                    logInfo(String.format("User %s elected to continue workflow %s regardless of mixed species warning.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(true);
                }
            }
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while validating unique recipe/species:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }
}