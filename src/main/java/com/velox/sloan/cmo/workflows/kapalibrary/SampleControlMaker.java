package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.rmi.RemoteException;
import java.util.*;

public class SampleControlMaker extends DefaultGenericPlugin{

    public SampleControlMaker(){

    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("CREATE AND ASSIGN CONTROLS");
    }

    public PluginResult run(){


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

    private boolean isRecipeImpactOrHemepact(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> recipes = getRecipeForAttachedSamples(attachedSamples);
        for (String recipe: recipes){
            if (recipe.toLowerCase().contains("impact") || recipe.toLowerCase().contains("hemepact")){
                return true;
            }
        }
        return false;
    }

    private List<DataRecord> getPooledNormalControlRecords() throws IoError, RemoteException, NotFound {
        List <DataRecord> normalControlSampleRecords = dataRecordManager.queryDataRecords("Sample", "IsControl = 1", user);
        List<DataRecord> impactHemepactControlRecords = new ArrayList<>();
        for(DataRecord pooledRecord: normalControlSampleRecords){
            String otherSampleId = pooledRecord.getStringVal("OtherSampleId", user);
            if(otherSampleId.toLowerCase().contains("ffpepoolednormal") || otherSampleId.toLowerCase().contains("frozenpoolednormal")){
                impactHemepactControlRecords.add(pooledRecord);
            }
        }
        return impactHemepactControlRecords;
    }

    private int getMostRecentPooledRecordId(List <DataRecord> impactHemepactControlRecords) throws NotFound, RemoteException {
        List <Integer> recordIds = new ArrayList<>();
        for(DataRecord record: impactHemepactControlRecords){
            recordIds.add(((Long)record.getLongVal("RecordId",user)).intValue());
        }
        return Collections.max(recordIds);
    }

    private List<String> getNextPooledNormalSampleIds(String mostRecentPooledNormalSampleId){
        List<String> newPooledIds = new ArrayList<>();
        if(mostRecentPooledNormalSampleId.toLowerCase().contains("ffpepoolednormal") || mostRecentPooledNormalSampleId.toLowerCase().contains("frozenpoolednormal")){
            List<String> idSplitted = Arrays.asList(mostRecentPooledNormalSampleId.split("-"));
            newPooledIds.add("FFPEPOOLEDNORMAL" + "-" + (Integer.parseInt(idSplitted.get(1)) + 1));
            newPooledIds.add("FROZENPOOLEDNORMAL" + "-" + (Integer.parseInt(idSplitted.get(1)) + 1));
        }
        else{
            newPooledIds.add("FFPEPOOLEDNORMAL" + "-1");
            newPooledIds.add("FROZENPOOLEDNORMAL" + "-1");
        }

        return newPooledIds;

    }

    private void addControlsToProtocol(List<Map<String,Object>> newProtocolFieldValues, List<DataRecord> attachedSamples, String recipe) throws IoError, RemoteException, NotFound, ServerException, ServerException {
        List<DataRecord> pooledNormalRecords = getPooledNormalControlRecords();
        int mostRecentPooleNormalRecordId = getMostRecentPooledRecordId(pooledNormalRecords);
        List<DataRecord> mostRecentPooledNormalRecord = dataRecordManager.queryDataRecords("Sample","RecordId = '" + Integer.toString(mostRecentPooleNormalRecordId) + "'", user);
        String mostRecentPooledNormalSampleId = mostRecentPooledNormalRecord.get(0).getStringVal("SampleId",user);
        List<String> nextPooledNormalSampleIds = getNextPooledNormalSampleIds(mostRecentPooledNormalSampleId);
        List<Map<String,Object>> newPooledSampleFields = new ArrayList<>();
        for(String id: nextPooledNormalSampleIds){
            Map<String, Object> newSampleFields = new HashMap<>();
            newSampleFields.put("SampleId", id);
            String otherSampleId = id.split("-")[0];
            newSampleFields.put("OtherSampleId", otherSampleId);
            newSampleFields.put("ContainerType", "Plate");
            newSampleFields.put("Recipe", recipe);
            newPooledSampleFields.add(newSampleFields);
        }

        dataRecordManager.addDataRecords("Sample", newPooledSampleFields,user);
        dataRecordManager.storeAndCommit("Created new Control samples" + nextPooledNormalSampleIds.toString(), user );
        //List<DataRecord> newCreatedPooledNormalSamples =
    }
}
