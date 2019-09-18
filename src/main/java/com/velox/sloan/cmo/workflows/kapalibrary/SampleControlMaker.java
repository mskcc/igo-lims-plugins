package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.managers.TaskUtilManager;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * To create and add control samples for IMPACT and HEMEPACT and attach them to active task.
 * It can be extended to create controls for other recipes.
 * <p>
 * Strategy:
 * 1. First get the type of all the controls that we need to add.
 * 2. Iterate over the list of Pools needed to add, and get the values needed to create new controls.
 * 3. Create new controls.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */

public class SampleControlMaker extends DefaultGenericPlugin {
    public SampleControlMaker() {
        setTaskEntry(true);
        setOrder(PluginOrder.MIDDLE.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException, com.velox.api.util.ServerException, NotFound {
        if (activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("CREATE AND AUTO ASSIGN CONTROLS") && !activeTask.getTask().getTaskOptions().containsKey("CONTROLS_ADDED")) {
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            if (isRecipeImpactOrHemepact(attachedSampleRecords)) {
                logInfo("Need to add controls for IMPACT or HEMEPACT recipe.");
                boolean shouldAddControls = clientCallback.showYesNoDialog("CREATE NEW CONTROLS FOR IMPACT AND HEMEPACT?", "DO YOU WANT LIMS TO AUTO-ASSIGN CONTROLS FOR IMPACT AND HEMEPACT.");
                if (!shouldAddControls) {
                    logInfo(String.format("User %s canceled the option to autoassign controls.", user.getUsername()));
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public PluginResult run() throws com.velox.api.util.ServerException {
        try {
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            addControls(attachedSampleRecords);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while creating new Controls. CAUSE:\n%s", e));
            logError(String.format("Error while creating new Controls. CAUSE:\n%s", e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }


    /**
     * Get unique recipe value list for attached samples
     *
     * @param attachedSamples
     * @return List<String>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getRecipeForAttachedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> recipes = new ArrayList<>();
        for (DataRecord sample : attachedSamples) {
            String recipe = sample.getStringVal("Recipe", user);
            if (!recipes.contains(recipe)) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    /**
     * Confirm if the recipe for one of the attached samples is IMPACT or HEMEPACT. Run plugin only if this is true
     *
     * @param attachedSamples
     * @return boolean
     * @throws NotFound
     * @throws RemoteException
     */
    private boolean isRecipeImpactOrHemepact(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> recipes = getRecipeForAttachedSamples(attachedSamples);
        for (String recipe : recipes) {
            if (recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list of control types to add based on recipes for attached samples. Currently this is configured only for IMPACT and HEMEPACT.
     * This method could be extended to facilitate addition of more controls.
     *
     * @param attachedSamples
     * @return List<String>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getControlTypesToAdd(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> controlTypesToAdd = new ArrayList<>();
        for (DataRecord sample : attachedSamples) {
            String recipe = sample.getStringVal("Recipe", user);
            String species = sample.getStringVal("Species", user);
            String sampleOrigin = sample.getStringVal("Preservation", user);
            if ((recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")) && sampleOrigin.toLowerCase().contains("ffpe") && species.toLowerCase().equals("human")) {
                if (!controlTypesToAdd.contains("FFPEPOOLEDNORMAL")) {
                    controlTypesToAdd.add("FFPEPOOLEDNORMAL");
                }
            }
            if ((recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")) && !sampleOrigin.toLowerCase().contains("ffpe") && species.toLowerCase().equals("human")) {
                if (!controlTypesToAdd.contains("FROZENPOOLEDNORMAL")) {
                    controlTypesToAdd.add("FROZENPOOLEDNORMAL");
                }
            }
            if ((recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")) && !sampleOrigin.toLowerCase().contains("ffpe") && species.toLowerCase().equals("mouse")) {
                if (!controlTypesToAdd.contains("MOUSEPOOLEDNORMAL")) {
                    controlTypesToAdd.add("MOUSEPOOLEDNORMAL");
                }
            }

        }
        return controlTypesToAdd;
    }

    /**
     * Get the DataRecords for all the controls for given control types we need to add.
     * This will be helpful to get the last created control for a given control type, and then to create sampleId for new CONTROL Sample to be added.
     *
     * @param controlTypesToAdd
     * @return List<DataRecord>
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private List<DataRecord> getAllControlRecordsByTypesToAdd(List<String> controlTypesToAdd) throws IoError, RemoteException, NotFound {
        List<DataRecord> allControlSampleRecords = dataRecordManager.queryDataRecords("Sample", "IsControl = 1", user);
        List<DataRecord> controlRecordsByTypesToAdd = new ArrayList<>();
        for (DataRecord pooledRecord : allControlSampleRecords) {
            String otherSampleId = pooledRecord.getStringVal("OtherSampleId", user);
            for (String controlType : controlTypesToAdd) {
                if (otherSampleId.toLowerCase().contains(controlType.toLowerCase())) {
                    controlRecordsByTypesToAdd.add(pooledRecord);
                }
            }
        }
        logInfo(controlRecordsByTypesToAdd.toString());
        return controlRecordsByTypesToAdd;
    }

    /**
     * Get the RecordId for last Control created for a given control type.This will be helpful to get the last created control
     * for a given control type, and then to create sampleId for new CONTROL Sample to be added.
     *
     * @param controlRecordsByTypesToAdd
     * @param controlType
     * @return int
     * @throws NotFound
     * @throws RemoteException
     */
    private String getMostRecentControlRecordId(List<DataRecord> controlRecordsByTypesToAdd, String controlType) throws NotFound, RemoteException {
        List<String> pooledNormalControls = new ArrayList<>();
        List<String> otherControls = new ArrayList<>();
        for (DataRecord record : controlRecordsByTypesToAdd) {
            String sampleId = record.getStringVal("SampleId", user);
            String otherSampleId = record.getStringVal("OtherSampleId", user);
            if (otherSampleId.toLowerCase().contains(controlType.toLowerCase()) && sampleId.toLowerCase().contains(controlType.toLowerCase())) {
                pooledNormalControls.add(sampleId);
            } else if (otherSampleId.toLowerCase().contains(controlType.toLowerCase())) {
                otherControls.add(sampleId);
            }
        }
        if (!pooledNormalControls.isEmpty()) {
            List<String> sortedPooledNormalControls = pooledNormalControls.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
            return sortedPooledNormalControls.get(sortedPooledNormalControls.size() - 1).split("_")[0];

        } else {
            List<String> sortedOtherControls = otherControls.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
            return sortedOtherControls.get(otherControls.size() - 1).split("_")[0];
        }
    }

    /**
     * Create new SampleId for new control given the recordId for last control created and Control type for new control to create.
     *
     * @param mostRecentControlSampleId
     * @param controlType
     * @return String
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String getNextControlSampleId(String mostRecentControlSampleId, String controlType) throws IoError, RemoteException, NotFound {
        // String previousSampleId = dataRecordManager.queryDataRecords("Sample", "RecordId = '" + (long) recordId + "'", user).get(0).getStringVal("SampleId", user);
        logInfo("Previous sampleId: " + mostRecentControlSampleId);
        if (mostRecentControlSampleId.toLowerCase().contains(controlType.toLowerCase())) {
            String basePreviousSampleId = mostRecentControlSampleId.split("_")[0]; // in case previous controls were aliquoted eg FFPEPOOLEDNORMAL-1_1_1_1
            logInfo("split base: " + basePreviousSampleId);
            String[] splitPreviousSampleId = basePreviousSampleId.split("-");
            String sampleIdPrefix = splitPreviousSampleId[0];
            String sampleIdSuffix = Integer.toString(Integer.parseInt(splitPreviousSampleId[1]) + 1);
            logInfo("new control ID: " + sampleIdPrefix + "-" + sampleIdSuffix);
            return sampleIdPrefix + "-" + sampleIdSuffix;
        } else {
            return controlType + "-" + "1";
        }
    }

    /**
     * Create new control values and add to the attached Sample DataFields and attached Protocol Datatype Datafield List.
     * The values will be commited and stored in the database when the task is submitted.
     *
     * @param attachedSamples
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private void addControls(List<DataRecord> attachedSamples) throws IoError, RemoteException, NotFound, ServerException {
        List<String> controlTypesToAdd = getControlTypesToAdd(attachedSamples);
        List<DataRecord> allControlSampleRecords = getAllControlRecordsByTypesToAdd(controlTypesToAdd);
        String recipe = attachedSamples.get(0).getStringVal("Recipe", user);
        boolean yes = true;
        List<Object> newControlSampleIds = new ArrayList<>();
        List<Map<String, Object>> newControlSampleFields = new ArrayList<>();
        for (String controlType : controlTypesToAdd) {
            String lastControlRecordId = getMostRecentControlRecordId(allControlSampleRecords, controlType);
            String nextControlSampleId = getNextControlSampleId(lastControlRecordId, controlType);
            logInfo(" New control: " + nextControlSampleId);
            Map<String, Object> newSampleFields = new HashMap<>();
            newSampleFields.put("SampleId", nextControlSampleId);
            logInfo(nextControlSampleId);
            newSampleFields.put("OtherSampleId", nextControlSampleId.split("-")[0]);
            newSampleFields.put("ContainerType", "Plate");
            newSampleFields.put("Recipe", recipe);
            newSampleFields.put("ExemplarSampleType", "DNA");
            newSampleFields.put("IsControl", yes);
            newControlSampleFields.add(newSampleFields);
            newControlSampleIds.add(nextControlSampleId);
        }
        dataRecordManager.addDataRecords("Sample", newControlSampleFields, user);
        dataRecordManager.storeAndCommit(String.format("Added Controls : %s", newControlSampleFields.toString()), user);
        activeTask.getTask().getTaskOptions().put("CONTROLS_ADDED", "");
        List<DataRecord> updatedControlRecords = dataRecordManager.queryDataRecords("Sample", "SampleId", newControlSampleIds, user);
        TaskUtilManager.attachRecordsToTask(activeTask, updatedControlRecords);
    }
}
