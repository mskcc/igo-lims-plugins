package com.velox.sloan.cmo.workflows.samplereceiving;

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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ajay Sharma on 6/26/18.
 * This plugin is designed to convert user submitted libraries into pools. "Micronic Tube barcode" is used to
 * to identify the library samples that should be pooled together. Currently, user submitted pooled libraries are not imported as
 * pools. Libraries present in the pools are imported as individual samples and pooled together during pooling workflow.
 */

public class UserLibraryPoolMaker extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin"};

    public UserLibraryPoolMaker() {
        setTaskEntry(true);
        setUserGroupList(permittedUsers);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException, ServerException, NotFound {
        List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
        return activeTask.getTask().getTaskOptions().keySet().contains("CREATE USER LIBRARY POOLS") && isValidSampleType(attachedSamples)
                && !activeTask.getTask().getTaskOptions().keySet().contains("USER POOLS CREATED");
    }

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            if (attachedSamples.size() == 0) {
                clientCallback.displayError(String.format("No Samples found attached to task '%s'", activeTask.getTaskName()));
                return new PluginResult(false);
            }

            if (!isValidMicronicTubeBarcode(attachedSamples)) {
                return new PluginResult(false);
            }

            List<String> uniqueMicronicBarcodesOnAttachedSamples = getUniqueMicronicTubeBarcodesInAttachedSamples(attachedSamples);
            List<List<DataRecord>> samplesSeparatedByPools = getListOfSamplesToPoolTogether(attachedSamples, uniqueMicronicBarcodesOnAttachedSamples);

            if (samplesSeparatedByPools.size() == 0) {
                clientCallback.displayError("Could not find potential samples to pool together. Please check that the samples have Micronic Tube Barcodes distinguishing samples in pool.");
                return new PluginResult(false);
            }
            createPoolsForAllSamples(samplesSeparatedByPools, attachedSamples);
            setSampleStatusForSamples(attachedSamples);
            activeTask.getTask().getTaskOptions().put("USER POOLS CREATED", "");
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while creating user pools. CAUSE:\n%s", e));
            logError(e);
        }
        return new PluginResult(true);
    }

    private boolean isValidSampleType(List<DataRecord> attachedSamples) throws NotFound, RemoteException, ServerException {
        for (DataRecord sample : attachedSamples) {
            String sampleId = sample.getStringVal("SampleId", user);
            String sampleType = sample.getStringVal("ExemplarSampleType", user);
            if (!"Pooled Library".equals(sampleType)) {
                clientCallback.displayError(String.format("Sample %s has invalid sample type %s for pooling. Expected Sample Type is 'Pooled Library'.", sampleId, sampleType));
                return false;
            }
        }
        return true;
    }

    private boolean isValidMicronicTubeBarcode(List<DataRecord> attachedSamples) throws NotFound, RemoteException, ServerException {
        for (DataRecord sample : attachedSamples) {
            String micronicBarcode = sample.getStringVal("MicronicTubeBarcode", user);
            String sampleId = sample.getStringVal("SampleId", user);
            if (StringUtils.isEmpty(micronicBarcode)) {
                clientCallback.displayError(String.format("Sample %s is missing 'MicronicTubeBarcode' value.", sampleId));
                return false;
            }
        }
        return true;
    }

    private List<String> getUniqueMicronicTubeBarcodesInAttachedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> uniqueMicronicBarcodes = new ArrayList<>();

        for (DataRecord sample : attachedSamples) {
            String micronicBarcode = sample.getStringVal("MicronicTubeBarcode", user);
            if (!uniqueMicronicBarcodes.contains(micronicBarcode)) {
                uniqueMicronicBarcodes.add(micronicBarcode);
            }
        }
        return uniqueMicronicBarcodes;
    }

    private List<List<DataRecord>> getListOfSamplesToPoolTogether(List<DataRecord> attachedSamples, List<String> uniqueBarcodesAssociatedWithSamples) throws NotFound, RemoteException {
        List<List<DataRecord>> userPools = new ArrayList<>();

        for (String tubeBarcode : uniqueBarcodesAssociatedWithSamples) {
            List<DataRecord> samplesToPoolTogether = new ArrayList<>();
            for (DataRecord sample : attachedSamples) {
                String sampleTubeBarcode = sample.getStringVal("MicronicTubeBarcode", user);
                if (sampleTubeBarcode.equals(tubeBarcode)) {
                    samplesToPoolTogether.add(sample);
                }
            }
            if (samplesToPoolTogether.size() > 0) {
                userPools.add(samplesToPoolTogether);
            }
        }
        return userPools;
    }

    private String getRequestIdForSample(DataRecord sample) throws RemoteException, ServerException, NotFound {
        String requestId = "";
        List<DataRecord> request = sample.getAncestorsOfType("Request", user);
        if (request.size() == 0) {
            clientCallback.displayError("Request record not found for attached samples");
            throw new NotFound("Request record not found for attached samples");
        }
        if (request.size() == 1) {
            requestId = request.get(0).getStringVal("RequestId", user);
        }
        return requestId;
    }

    private String getPoolId(String requestId, int counter) {
        return "Pool-" + requestId + "-Tube" + String.valueOf(counter);
    }

    private String concatenateStringValues(List<DataRecord> userPool, String fieldType) throws NotFound, RemoteException {
        String concatenatedOtherSampleIds = "";
        List<String> valuesToConcatenate = new ArrayList<>();
        for (DataRecord sample : userPool) {
            if (sample.getValue(fieldType, user) != null) {
                valuesToConcatenate.add(sample.getStringVal(fieldType, user));
            }
        }
        if (valuesToConcatenate.size() > 0) {
            concatenatedOtherSampleIds = StringUtils.join(valuesToConcatenate, ",");
        }
        return concatenatedOtherSampleIds;
    }

    private String getSequencingRunType(List<DataRecord> userPoolSamples) throws RemoteException, NotFound {
        return userPoolSamples.get(0).getDescendantsOfType("SeqRequirement", user).get(0).getStringVal("SequencingRunType", user);
    }

    private double getTotalRequestedReadsForPool(List<DataRecord> userPoolSamples) throws RemoteException, NotFound {
        double totalReads = 0.0;
        for (DataRecord sample : userPoolSamples) {
            double readsRequestedForSample = sample.getDescendantsOfType("SeqRequirement", user).get(0).getDoubleVal("RequestedReads", user);
            totalReads += readsRequestedForSample;
        }
        return totalReads;
    }

    private DataRecord createSamplePool(List<DataRecord> userPoolSamples, int counter) throws ServerException, RemoteException, NotFound, IoError {
        List<Map<String, Object>> poolValuesMapList = new ArrayList<>();
        Map<String, Object> poolValuesMap = new HashMap<>();
        Map<String, Object> sequencingRequirementsPooled = new HashMap<>();
        String requestId = getRequestIdForSample(userPoolSamples.get(0));
        String sampleId = getPoolId(requestId, counter);
        String altId = concatenateStringValues(userPoolSamples, "AltId");
        String otherSampleId = concatenateStringValues(userPoolSamples, "OtherSampleId");
        String userSampleId = concatenateStringValues(userPoolSamples, "UserSampleID");
        String species = userPoolSamples.get(0).getStringVal("Species", user);
        String recipe = userPoolSamples.get(0).getStringVal("Recipe", user);
        String micronicTubeBarcode = userPoolSamples.get(0).getStringVal("MicronicTubeBarcode", user);
        String sampleType = userPoolSamples.get(0).getStringVal("ExemplarSampleType", user);
        String sequencingRunType = getSequencingRunType(userPoolSamples);
        double totalRequestedReadsForPool = getTotalRequestedReadsForPool(userPoolSamples);
        poolValuesMap.put("SampleId", sampleId);
        sequencingRequirementsPooled.put("SampleId", sampleId);
        poolValuesMap.put("OtherSampleId", otherSampleId);
        poolValuesMap.put("AltId", altId);
        sequencingRequirementsPooled.put("OtherSampleId", otherSampleId);
        sequencingRequirementsPooled.put("AltId", altId);
        sequencingRequirementsPooled.put("SequencingRunType", sequencingRunType);
        sequencingRequirementsPooled.put("RequestedReads", totalRequestedReadsForPool);
        poolValuesMap.put("UserSampleID", userSampleId);
        poolValuesMap.put("ExemplarSampleType", sampleType);
        poolValuesMap.put("Species", species);
        poolValuesMap.put("Recipe", recipe);
        poolValuesMap.put("MicronicTubeBarcode", micronicTubeBarcode);
        poolValuesMapList.add(poolValuesMap);

        dataRecordManager.addDataRecords("Sample", poolValuesMapList, user);
        dataRecordManager.storeAndCommit(String.format("Created user pool %s", sampleId), user);
        List<DataRecord> pooledSampleRecord = dataRecordManager.queryDataRecords("Sample", "SampleId= '" + sampleId + "'", user);
        pooledSampleRecord.get(0).addChild("SeqRequirementPooled", sequencingRequirementsPooled, user);
        for (DataRecord sample : userPoolSamples) {
            sample.addChildIfNotExists(pooledSampleRecord.get(0), user);
        }
        return pooledSampleRecord.get(0);
    }

    private void createPoolsForAllSamples(List<List<DataRecord>> userPools, List<DataRecord> attachedSamples) throws ServerException, RemoteException, NotFound, IoError {
        List<DataRecord> sampleIdsForSamplePools = new ArrayList<>();
        int counter = 1;
        for (List<DataRecord> samplesToPoolTogether : userPools) {
            sampleIdsForSamplePools.add(createSamplePool(samplesToPoolTogether, counter));
            counter++;
        }
        List<ActiveTask> activeTasks = activeWorkflow.getActiveTaskList();
        for (ActiveTask task : activeTasks) {
            if (task.getTask().getTaskOptions().keySet().contains("CREATE USER LIBRARY POOLS") || "Store Samples".equals(task.getTaskName()) ||
                    "Downstream Process Assignment".equals(task.getTaskName())) {
                TaskUtilManager.removeRecordsFromTask(task, attachedSamples);
                TaskUtilManager.attachRecordsToTask(task, sampleIdsForSamplePools);
            }
        }
    }

    private void setSampleStatusForSamples(List<DataRecord> attachedSamples) throws ServerException, RemoteException {
        List<Map<String, Object>> sampleStatusFields = new ArrayList<>();
        for (DataRecord sample : attachedSamples){
            Map<String,Object> sampleStatus = new HashMap<>();
            sampleStatus.put("ExemplarSampleStatus", "Processing Completed");
            sampleStatusFields.add(sampleStatus);
        }
        dataRecordManager.setFieldsForRecords(attachedSamples, sampleStatusFields, user);
    }
}