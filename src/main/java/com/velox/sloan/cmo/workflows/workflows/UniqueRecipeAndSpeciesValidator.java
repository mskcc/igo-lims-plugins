package com.velox.sloan.cmo.workflows.workflows;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plugin to validate if the samples launched in the workflow have same recipe. If different recipes found, User is displayed a warning along with options to
 * cancel the workflow or continue running the workflow.
 * Created by sharmaa1 on 8/5/19.
 */
public class UniqueRecipeValidator extends DefaultGenericPlugin {

    public UniqueRecipeValidator() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskName().toLowerCase().equals("create experiment") && activeTask.getTask().getTaskOptions().containsKey("VALIDATE UNIQUE SAMPLE RECIPE");
    }

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            Set<String> recipes = new HashSet<>();
            for (DataRecord samp : samples) {
                Object recipe = samp.getValue("Recipe", user);
                if (recipe == null || StringUtils.isBlank((String) recipe)) {
                    clientCallback.displayWarning(String.format("Recipe value not found for sample %s", samp.getStringVal("SampleId", user)));
                } else {
                    recipes.add((String) recipe);
                    logInfo(recipe.toString());
                }
            }
            if (recipes.size() == 0) {
                logInfo("Recipe values not found on samples");
                clientCallback.displayWarning("Cannot validate uniqie recipes. Recipe values not found for samples in this task.");
                return new PluginResult(true);
            }

            boolean isUniqueRecipe = recipes.size() == 1;
            if (!isUniqueRecipe) {
                logInfo(String.format("Two or more Samples launched in this workflow have different recipes: %s", recipes.toString()));
                if (!clientCallback.showOkCancelDialog("SAMPLES HAVE DIFFERENT RECIPES!", "Two or more Samples launched in this workflow have different recipes: " + recipes.toString() + "\nDO YOU WANT TO CONTINUE?")) {
                    logInfo(String.format("User %s elected to cancel workflow %s because of duplicate recipes.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(false);
                } else {
                    logInfo(String.format("User %s elected to continue workflow %s regardless of duplicate recipes warning.", activeWorkflow.getActiveWorkflowName(), user.getAccountName()));
                    return new PluginResult(true);
                }
            }
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while validating unique recipe:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }
}