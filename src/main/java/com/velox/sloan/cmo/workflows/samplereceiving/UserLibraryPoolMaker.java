package com.velox.sloan.cmo.workflows.samplereceiving;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.managers.TaskUtilManager;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqRequirementModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return activeTask.getTask().getTaskOptions().containsKey("CREATE USER LIBRARY POOLS") && isValidSampleType(attachedSamples)
                && !activeTask.getTask().getTaskOptions().containsKey("USER POOLS CREATED");
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            if (attachedSamples.size() == 0) {
                clientCallback.displayError(String.format("No Samples found attached to task '%s'", activeTask.getTaskName()));
                return new PluginResult(false);
            }

            if (!isValidMicronicTubeBarcode(attachedSamples)) {
                return new PluginResult(false);
            }
            List<String> sortedSampleIds = getSampleIdsSortedAscending(attachedSamples);
            List<DataRecord> sortedSampleDataRecords = sortDataRecordsBySampleId(attachedSamples, sortedSampleIds);
            List<String> uniqueMicronicBarcodesOnAttachedSamples = getUniqueMicronicTubeBarcodesInAttachedSamples(sortedSampleDataRecords);
            List<List<DataRecord>> samplesSeparatedByPools = getListOfSamplesToPoolTogether(attachedSamples, uniqueMicronicBarcodesOnAttachedSamples);
            if (samplesSeparatedByPools.size() == 0) {
                clientCallback.displayError("Could not find potential samples to pool together. Please check that the samples have Micronic Tube Barcodes distinguishing samples in pool.");
                return new PluginResult(false);
            }
            createPoolsForAllSamples(samplesSeparatedByPools, attachedSamples);
            setSampleStatusForSamples(attachedSamples);
            activeTask.getTask().getTaskOptions().put("USER POOLS CREATED", "");
        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException -> Error while creating user pools. CAUSE:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to check if the samples have valid ExemplarSampleType for pooling.
     *
     * @param attachedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private boolean isValidSampleType(List<DataRecord> attachedSamples) {
        for (DataRecord sample : attachedSamples) {
            try {
                String sampleId = sample.getStringVal(SampleModel.SAMPLE_ID, user);
                String sampleType = sample.getStringVal(SampleModel.EXEMPLAR_SAMPLE_TYPE, user);
                if (!"Pooled Library".equals(sampleType)) {
                    clientCallback.displayError(String.format("Sample %s has invalid sample type %s for pooling. Expected Sample Type is 'Pooled Library'.", sampleId, sampleType));
                    return false;
                }
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while validating '%s' for sample with recordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_TYPE, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error validating '%s' for sample with recordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_TYPE, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (ServerException e) {
                logError(String.format("ServerException -> Error validating '%s' for sample with recordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_TYPE, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
        }
        return true;
    }

    /**
     * Method to check all attached samples have MicronicTube Barcode values.
     *
     * @param attachedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private boolean isValidMicronicTubeBarcode(List<DataRecord> attachedSamples) {
        for (DataRecord sample : attachedSamples) {
            try {
                String micronicBarcode = sample.getStringVal("MicronicTubeBarcode", user);
                String sampleId = sample.getStringVal("SampleId", user);
                if (StringUtils.isEmpty(micronicBarcode)) {
                    clientCallback.displayError(String.format("Sample %s is missing 'MicronicTubeBarcode' value.", sampleId));
                    return false;
                }
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while validating '%s' for sample with recordId %d:\n%s", SampleModel.MICRONIC_TUBE_BARCODE, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while validating '%s' for sample with recordId %d:\n%s", SampleModel.MICRONIC_TUBE_BARCODE, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (ServerException e) {
                logError(String.format("ServerException -> Error while validating '%s' for sample with recordId %d:\n%s", SampleModel.MICRONIC_TUBE_BARCODE, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
        }
        return true;
    }

    /**
     * Method to get Samples Ids sorted in ascending order.
     *
     * @param attchedSamples
     * @return
     */
    private List<String> getSampleIdsSortedAscending(List<DataRecord> attchedSamples) {
        List<String> sampleIds = new ArrayList<>();
        for (DataRecord sample : attchedSamples) {
            try {
                sampleIds.add(sample.getStringVal("SampleId", user));
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
        }
        return sampleIds.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
    }

    /**
     * Method to sort DataRecords by SampleId field values.
     *
     * @param attachedSamples
     * @param sortedSampleIds
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<DataRecord> sortDataRecordsBySampleId(List<DataRecord> attachedSamples, List<String> sortedSampleIds) {
        List<DataRecord> sortedSampleDataRecords = new ArrayList<>();
        for (String sampleId : sortedSampleIds) {
            for (DataRecord sample : attachedSamples) {
                try {
                    String id = sample.getStringVal("SampleId", user);
                    if (id.equals(sampleId)) {
                        sortedSampleDataRecords.add(sample);
                    }
                } catch (RemoteException e) {
                    logError(String.format("RemoteException -> Error while reading '%s' for sample with recordId %d during sorting or DataRecords:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
                } catch (NotFound notFound) {
                    logError(String.format("NotFound Exception -> Error while reading '%s' for sample with recordId %d during sorting or DataRecords:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
                }
            }
        }
        return sortedSampleDataRecords;
    }

    /**
     * Method to get Unique MicronicTubeBarcode values from Samples.
     *
     * @param attachedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getUniqueMicronicTubeBarcodesInAttachedSamples(List<DataRecord> attachedSamples) {
        List<String> uniqueMicronicBarcodes = new ArrayList<>();

        for (DataRecord sample : attachedSamples) {
            try {
                String micronicBarcode = sample.getStringVal("MicronicTubeBarcode", user);
                if (!uniqueMicronicBarcodes.contains(micronicBarcode)) {
                    uniqueMicronicBarcodes.add(micronicBarcode);
                }
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while reading '%s' for sample with recordId %d while getting unique MicronicTubeBarcode values:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while reading '%s' for sample with recordId %d while getting unique MicronicTubeBarcode values:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
        }
        return uniqueMicronicBarcodes;
    }

    /**
     * Method to get List of sample to pool.
     *
     * @param attachedSamples
     * @param uniqueBarcodesAssociatedWithSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<List<DataRecord>> getListOfSamplesToPoolTogether(List<DataRecord> attachedSamples, List<String> uniqueBarcodesAssociatedWithSamples) {
        List<List<DataRecord>> userPools = new ArrayList<>();
        for (String tubeBarcode : uniqueBarcodesAssociatedWithSamples) {
            List<DataRecord> samplesToPoolTogether = new ArrayList<>();
            for (DataRecord sample : attachedSamples) {
                try {
                    String sampleTubeBarcode = sample.getStringVal("MicronicTubeBarcode", user);
                    if (sampleTubeBarcode.equals(tubeBarcode)) {
                        samplesToPoolTogether.add(sample);
                    }
                } catch (RemoteException e) {
                    logError(String.format("RemoteException -> Error while reading '%s' for sample with recordId %d while getting List of Samples to pool:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
                } catch (NotFound notFound) {
                    logError(String.format("NotFound Exception -> Error while reading '%s' for sample with recordId %d while getting List of Samples to pool:\n%s", SampleModel.SAMPLE_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
                }
            }
            if (samplesToPoolTogether.size() > 0) {
                userPools.add(samplesToPoolTogether);
            }
        }
        return userPools;
    }

    /**
     * Method to get RequestId values for Sample.
     *
     * @param sample
     * @return
     * @throws NullPointerException
     * @throws RemoteException
     * @throws ServerException
     * @throws NotFound
     */
    private String getRequestIdForSample(DataRecord sample) {
        String requestId = "";
        try {
            List<DataRecord> request = sample.getAncestorsOfType("Request", user);
            if (request.size() == 0) {
                clientCallback.displayError("Request record not found for attached samples");
                throw new NotFound("Request record not found for attached samples");
            }
            if (request.size() == 1) {
                requestId = request.get(0).getStringVal("RequestId", user);
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.REQUEST_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.REQUEST_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.REQUEST_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (IoError io) {
            logError(String.format("IoError -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.REQUEST_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(io)));
        }
        return requestId;
    }

    /**
     * Method to get new PoolId.
     *
     * @param requestId
     * @param counter
     * @return
     */
    private String getPoolId(String requestId, int counter) {
        return "Pool-" + requestId + "-Tube" + counter;
    }

    /**
     * Method to concatenate string values.
     *
     * @param userPool
     * @param fieldType
     * @return
     * @throws NullPointerException
     * @throws NotFound
     * @throws RemoteException
     */
    private String concatenateStringValues(List<DataRecord> userPool, String fieldType) {
        String concatenatedOtherSampleIds = "";
        List<String> valuesToConcatenate = new ArrayList<>();
        for (DataRecord sample : userPool) {
            try {
                if (sample.getValue(fieldType, user) != null) {
                    valuesToConcatenate.add(sample.getStringVal(fieldType, user));
                }
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while reading '%s' for sample with recordId %d during concatenation of values:\n%s", fieldType, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while reading '%s' for sample with recordId %d during concatenation of values:\n%s", fieldType, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
        }
        if (valuesToConcatenate.size() > 0) {
            concatenatedOtherSampleIds = StringUtils.join(valuesToConcatenate, ",");
        }
        return concatenatedOtherSampleIds;
    }

    /**
     * Method to get Sequencing RunType value for Samples.
     *
     * @param userPoolSamples
     * @return
     * @throws NullPointerException
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private String getSequencingRunType(List<DataRecord> userPoolSamples) {
        String sequencingRunType = "";
        DataRecord firstSample = userPoolSamples.get(0);
        try {
            DataRecord sequencingRequirementRec = firstSample.getDescendantsOfType(SeqRequirementModel.DATA_TYPE_NAME, user).get(0);
            sequencingRunType = sequencingRequirementRec.getStringVal(SeqRequirementModel.SEQUENCING_RUN_TYPE, user);
            if (StringUtils.isBlank(sequencingRunType)) {
                clientCallback.displayError("Sequencing RunType not defined for Samples");
                logError("Sequencing RunType not defined for Samples");
                return "";
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.SEQUENCING_RUN_TYPE, firstSample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.SEQUENCING_RUN_TYPE, firstSample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.SEQUENCING_RUN_TYPE, firstSample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (IoError io) {
            logError(String.format("IoError -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.SEQUENCING_RUN_TYPE, firstSample.getRecordId(), ExceptionUtils.getStackTrace(io)));
        }
        return sequencingRunType;
    }

    /**
     * Method to get Requested reads for pool.
     *
     * @param userPoolSamples
     * @return
     * @throws NullPointerException
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private double getTotalRequestedReadsForPool(List<DataRecord> userPoolSamples) {
        double totalReads = 0.0;
        for (DataRecord sample : userPoolSamples) {
            try {
                double readsRequestedForSample = sample.getDescendantsOfType(SeqRequirementModel.DATA_TYPE_NAME, user).get(0).getDoubleVal(SeqRequirementModel.REQUESTED_READS, user);
                if ((Object) readsRequestedForSample == null) {
                    clientCallback.displayError(String.format("Minimum number of Reads required is not defined for Sample : %s", sample.getStringVal("SampleId", user)));
                    logError(String.format("Minimum number of Reads required is not defined for Sample : %s", sample.getStringVal("SampleId", user)));
                }
                totalReads += readsRequestedForSample;
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.REQUESTED_READS, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.REQUESTED_READS, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (ServerException e) {
                logError(String.format("ServerException -> Error while getting '%s' for sample with recordId %d:\n%s", SeqRequirementModel.REQUESTED_READS, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (IoError io) {
                logError(String.format("IoError -> Error while reading '%s' for sample with recordId %d:\n%s", SampleModel.REQUEST_ID, sample.getRecordId(), ExceptionUtils.getStackTrace(io)));
            }
        }
        return totalReads;
    }

    /**
     * Method to created sample pools.
     *
     * @param userPoolSamples
     * @param counter
     * @return
     * @throws ServerException
     * @throws RemoteException
     * @throws NotFound
     * @throws IoError
     */
    private DataRecord createSamplePool(List<DataRecord> userPoolSamples, int counter) {
        List<DataRecord> pooledSampleRecords = new ArrayList<>();
        try {
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
            dataRecordManager.storeAndCommit(String.format("Created user pool %s", sampleId), null, user);
            pooledSampleRecords = dataRecordManager.queryDataRecords("Sample", "SampleId= '" + sampleId + "'", user);
            pooledSampleRecords.get(0).addChild("SeqRequirementPooled", sequencingRequirementsPooled, user);
            for (DataRecord sample : userPoolSamples) {
                sample.addChildIfNotExists(pooledSampleRecords.get(0), user);
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while creating Sample pools:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error while creating Sample pools:\n%s", ExceptionUtils.getStackTrace(ioError)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error while creating Sample pools:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error while creating Sample pools:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return pooledSampleRecords.get(0);
    }

    /**
     * Method to create pools for all samples.
     *
     * @param userPools
     * @param attachedSamples
     * @throws ServerException
     * @throws RemoteException
     * @throws NotFound
     * @throws IoError
     */
    private void createPoolsForAllSamples(List<List<DataRecord>> userPools, List<DataRecord> attachedSamples) {
        List<DataRecord> sampleIdsForSamplePools = new ArrayList<>();
        int counter = 1;
        for (List<DataRecord> samplesToPoolTogether : userPools) {
            sampleIdsForSamplePools.add(createSamplePool(samplesToPoolTogether, counter));
            counter++;
        }
        try {
            List<ActiveTask> activeTasks = activeWorkflow.getActiveTaskList();
            for (ActiveTask task : activeTasks) {
                if (task.getTask().getTaskOptions().containsKey("CREATE USER LIBRARY POOLS") || "Store Samples".equals(task.getTaskName()) ||
                        "Downstream Process Assignment".equals(task.getTaskName())) {
                    TaskUtilManager.removeRecordsFromTask(task, attachedSamples);
                    TaskUtilManager.attachRecordsToTask(task, sampleIdsForSamplePools);
                }
            }
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error while running pooling plugin:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while running pooling plugin:\n%s", ExceptionUtils.getStackTrace(e)));
        }
    }

    /**
     * Method to set ExemplarSampleStatus.
     *
     * @param attachedSamples
     * @throws ServerException
     * @throws RemoteException
     * @throws NotFound
     */
    private void setSampleStatusForSamples(List<DataRecord> attachedSamples) {
        for (DataRecord sample : attachedSamples) {
            try {
                sample.setDataField(SampleModel.EXEMPLAR_SAMPLE_STATUS, "Processing Completed", user);
                dataRecordManager.storeAndCommit("Exemplar Sample Status changed.",null, user);
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while setting '%s' of sample with RecordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_STATUS, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound -> Error while setting '%s' of sample with RecordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_STATUS, sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (ServerException e) {
                logError(String.format("ServerException -> Error while setting '%s' of sample with RecordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_STATUS, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (InvalidValue invalidValue) {
                logError(String.format("InvalidValue -> Error while setting '%s' of sample with RecordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_STATUS, sample.getRecordId(), ExceptionUtils.getStackTrace(invalidValue)));
            } catch (IoError ioError) {
                logError(String.format("IoError -> Error while setting '%s' of sample with RecordId %d:\n%s", SampleModel.EXEMPLAR_SAMPLE_STATUS, sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
            }
        }
    }
}