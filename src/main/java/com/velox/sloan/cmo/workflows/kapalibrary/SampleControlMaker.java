package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.managers.TaskUtilManager;

import java.rmi.RemoteException;
import java.util.*;

public class SampleControlMaker extends DefaultGenericPlugin{

    public SampleControlMaker(){
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException, com.velox.api.util.ServerException, NotFound{
        if (activeTask.getTask().getTaskOptions().containsKey("CREATE AND ASSIGN CONTROLS")) {
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            if (isRecipeImpactOrHemepact(attachedSampleRecords)) {
                logInfo("Need to add controls for IMPACT or HEMEPACT recipe.");
                return true;
            }
        }
        return false;
    }

    private boolean isRecipeImpactOrHemepact(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> recipes = getRecipeForAttachedSamples(attachedSamples);
        for (String recipe: recipes){
            if (recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")){
                return true;
            }
        }
        return false;
    }

    public PluginResult run() throws RemoteException, NotFound, com.velox.api.util.ServerException, InvalidValue, IoError {
        // recipe verified in shouldRun() as IMPACT or HEMEPACT
        List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
        // if any samples are FFPE add FFPE pooled normal sample
        // if any samples are not FFPE add frozen pooled normal sample
        String recipe = getRecipeForAttachedSamples(attachedSampleRecords).get(0);
        boolean addFrozen = isControlTypeFrozen(attachedSampleRecords);
        int maxRecordId = getMostRecentPooledRecordId(attachedSampleRecords, "frozenPooledNormal");
        List<DataRecord> mostRecentPooledNormalRecord = dataRecordManager.queryDataRecords("Sample","RecordId = '" + Integer.toString(maxRecordId) + "'", user);
        String sampleId = mostRecentPooledNormalRecord.get(0).getStringVal("SampleId", user);
        String nextFrozenId = getNextFrozenPooledNormalSampleId(sampleId);

        List<Map<String,Object>> newPooledSampleFields = new ArrayList<>();
        Map<String, Object> newSampleFields = new HashMap<>();
        newSampleFields.put("SampleId", nextFrozenId);
        newSampleFields.put("OtherSampleId", nextFrozenId);
        newSampleFields.put("ContainerType", "Plate");
        newSampleFields.put("Recipe", recipe);
        newPooledSampleFields.add(newSampleFields);
        dataRecordManager.addDataRecords("Sample", newPooledSampleFields, user);
        dataRecordManager.storeAndCommit("Created new Control samples" + nextFrozenId.toString(), user);
        List<DataRecord> r = dataRecordManager.queryDataRecords("Sample", "SampleId", Collections.singletonList(nextFrozenId), user);
        for (DataRecord x : r) {
            logInfo(x.getStringVal("SampleId", user));
        }
        TaskUtilManager.attachRecordsToTask(activeTask, r);

        logInfo("Controls added.");
        return new PluginResult(true);
    }

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

    private List<DataRecord> getPooledNormalControlRecords() throws IoError, RemoteException, NotFound {
        List <DataRecord> normalControlSampleRecords = dataRecordManager.queryDataRecords("Sample", "IsControl = 1", user);
        List<DataRecord> impactHemepactControlRecords = new ArrayList<>();
        for(DataRecord pooledRecord: normalControlSampleRecords){
            String otherSampleId = pooledRecord.getStringVal("OtherSampleId", user);
            if (otherSampleId.toLowerCase().contains("ffpepoolednormal") || otherSampleId.toLowerCase().contains("frozenpoolednormal")) {
                impactHemepactControlRecords.add(pooledRecord);
            }
        }
        return impactHemepactControlRecords;
    }

    private int getMostRecentPooledRecordId(List <DataRecord> impactHemepactControlRecords, String controlType) throws NotFound, RemoteException {
        List <Integer> recordIds = new ArrayList<>();
        for (DataRecord record: impactHemepactControlRecords) {
            if (record.getStringVal("OtherSampleId", user).toLowerCase().contains(controlType.toLowerCase()))
                recordIds.add(((Long)record.getLongVal("RecordId",user)).intValue());
        }
        return Collections.max(recordIds);
    }

    protected static String getNextFrozenPooledNormalSampleId(String previous){
        List<String> newPooledIds = new ArrayList<>();
        if (previous.toLowerCase().contains("frozenpoolednormal")){
            String[] split = previous.split("-");
            Integer last = Integer.parseInt(split[1]);
            return split[0] + "-" + last+1;
        }

        return "FROZENPOOLEDNORMAL-1";
    }

    public static List<String> getControlPrefix(String recipe, boolean addFrozen, boolean addFFPE) {
        List<String> r = new ArrayList<>();
        if (recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")){
            if (addFrozen)
                r.add("FROZENPOOLEDNORMAL");
            if (addFFPE)
                r.add("FFPEPOOLEDNORMAL");
            return r;
        } else {
            // add controls for impact & hemepact, user must drag and drop for other recipes
            return r;
        }
    }

    /**
     * Adds one or both - FROZENPOOLEDNORMAL or FFPEPOOLEDNORMAL
     */
    private void addControlsToProtocol(String recipe, String control, List<DataRecord> attachedSamples) throws IoError, RemoteException, NotFound, ServerException, ServerException {
        List<DataRecord> pooledNormalRecords = getPooledNormalControlRecords();

        int mostRecentPooleNormalRecordId = getMostRecentPooledRecordId(pooledNormalRecords, "");
        List<DataRecord> mostRecentPooledNormalRecord = dataRecordManager.queryDataRecords("Sample","RecordId = '" + Integer.toString(mostRecentPooleNormalRecordId) + "'", user);
        String mostRecentPooledNormalSampleId = mostRecentPooledNormalRecord.get(0).getStringVal("SampleId", user);
        List<String> nextPooledNormalSampleIds = null; //getNextPooledNormalSampleIds(mostRecentPooledNormalSampleId);
        List<Map<String,Object>> newPooledSampleFields = new ArrayList<>();
        for (String id: nextPooledNormalSampleIds) {
            Map<String, Object> newSampleFields = new HashMap<>();
            newSampleFields.put("SampleId", id);
            String otherSampleId = id.split("-")[0];
            newSampleFields.put("OtherSampleId", otherSampleId);
            newSampleFields.put("ContainerType", "Plate");
            newSampleFields.put("Recipe", recipe);
            newPooledSampleFields.add(newSampleFields);
        }

        dataRecordManager.addDataRecords("Sample", newPooledSampleFields, user);
        dataRecordManager.storeAndCommit("Created new Control samples" + nextPooledNormalSampleIds.toString(), user );
        List<DataRecord> r = dataRecordManager.queryDataRecords("Sample", "SampleId", Collections.singletonList(nextPooledNormalSampleIds), user);
        for (DataRecord x: r) {
            logInfo(x.getStringVal("SampleId", user));
        }
        TaskUtilManager.attachRecordsToTask(activeTask, r);
    }

    private boolean isControlTypeFrozen(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        // if there is any sample that is not FFPE then attach frozen control
        for (DataRecord r: attachedSamples) {
            if (!r.getStringVal("SampleOrigin", user).toLowerCase().contains("ffpe")) {
                return true;
            }
        }
        return false;
    }

    protected boolean isControlTypeFFPEorFrozen(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        for (DataRecord r: attachedSamples) {
            if (r.getStringVal("SampleOrigin", user).toLowerCase().contains("ffpe")) {
                return true;
            }
        }
        return false;
    }
}