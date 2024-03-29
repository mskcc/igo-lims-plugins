package com.velox.sloan.cmo.workflows.ampliseqpooling;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This plugin is designed for 'AmpliSeq Library Preparation' workflow. In this workflow multiple aliquots of a sample are created, processed and then
 * combined into one sample. The combining of samples is done using pooling plugin from Sapio. But the pooling creates pooled Sample with SampleId containing
 * 'Pool-'. It is desirable for this workflow to have regular SampleId after the samples are combined. This plugin is designed to run 'ON ENTRY' and
 * rename 'Pooled SampleId' to 'regular SampleId' if the sample attached to the task has 'ExemplarSampleType=Pooled Library'. Otherwise sample is left alone.
 *
 * @author Ajay Sharma
 */
public class PoolIdReplacer extends DefaultGenericPlugin {

    public PoolIdReplacer() {
        setTaskEntry(true);
        setOrder(PluginOrder.FIRST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("OVERWRITE POOLID WITH SAMPLEID");
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            logInfo("running PoolId Replacer Plugin.");
            if (attachedSampleRecords == null || attachedSampleRecords.isEmpty()) {
                clientCallback.displayError("Cannot run PoolIdReplacer plugin, No Sample records found attached to this task");
                logError("Cannot run PoolIdReplacer plugin, No Sample records found attached to this task");
                return new PluginResult(false);
            }

            replacePoolIdWithSampleId(attachedSampleRecords);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while sample assignment to plates. CAUSE:\n%s", ExceptionUtils.getStackTrace(e)));
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);

        }
        return new PluginResult(true);
    }

    /**
     * Gets all the existing aliquots that contain the sampleID of given sample.
     *
     * @param sample
     * @return List of DataRecords
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     */
    private List<DataRecord> getExistingSampleAliquots(DataRecord sample) {
        String sampleId = "";
        List<DataRecord> existingSampleAliquots = new ArrayList<>();
        try {
            sampleId = sample.getStringVal("SampleId", user);
            if (sampleId.split("_").length > 2) {
                String[] splitSampleId = sampleId.split("_");
                String baseSampleId = splitSampleId[0] + splitSampleId[1];
                return dataRecordManager.queryDataRecords("Sample", "SampleId = '" + baseSampleId + "%'", user);
            }
            existingSampleAliquots = dataRecordManager.queryDataRecords("Sample", "SampleId = '" + sampleId + "%'", user);
        } catch (RemoteException | ServerException e) {
            logError(String.format("RemoteException -> Error while getting Existing Aliquots for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error while getting Existing Aliquots for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error while getting Existing Aliquots for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
        }
        return existingSampleAliquots;
    }

    /**
     * Gets ascending order sorted sample Ids for the list of given samples
     *
     * @param samples
     * @return list of SampleId strings
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getSortedSampleIds(List<DataRecord> samples){
        List<String> sampleIds = new ArrayList<>();
        for (DataRecord sample : samples) {
            try {
                sampleIds.add(sample.getStringVal("SampleId", user));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while Reading SampleId for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while Reading SampleId for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
        }
        return sampleIds.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
    }

    /**
     * Gets the next sample Id given a Sample DataRecord. It is important to make sure the next sample ID
     * that is generated is not already assigned to any of the aliquots existing under the parent project
     * of a given sample.
     *
     * @param sample
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String getNextSampleId(DataRecord sample) throws ServerException {
        List<DataRecord> parentSamples = new ArrayList<>();
        try {
            parentSamples = sample.getParentsOfType("Sample", user);
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error while getting parent samples for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException Exception -> Error while getting parent samples for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        }
        List<DataRecord> existingSampleAliquots = getExistingSampleAliquots(parentSamples.get(0));
        List<String> existingSampleIds = getSortedSampleIds(existingSampleAliquots);
        String nextSampleId = getSortedSampleIds(parentSamples).get(parentSamples.size() - 1) + "_1";
        while (existingSampleIds.contains(nextSampleId)) {
            nextSampleId += "_1";
        }
        return nextSampleId;
    }

    /**
     * Method to get ExemplarSampleType from parent samples.
     * @param sample
     * @return
     */
    private String getSampleTypeToAssign(DataRecord sample) {
        String sampleType = "";
        try {
            sample.getParentsOfType(SampleModel.DATA_TYPE_NAME, user).get(0).getStringVal(SampleModel.EXEMPLAR_SAMPLE_TYPE, user);
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error while getting ExemplarSampleType value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
        } catch (RemoteException | ServerException e) {
            logError(String.format("Exception -> Error while getting ExemplarSampleType value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error while getting ExemplarSampleType value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
        }
        return sampleType;
    }

    /**
     * Replaces the 'SampleId' value for pooled sample with regular SampleId that does not contains 'Pool-'
     * substring in SampleId value. Also, removes children of type "SeqRequirementPooled" from the sample as it
     * will not be relevant anymore and it will never be used.
     *
     * @param samples
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     */
    private void replacePoolIdWithSampleId(List<DataRecord> samples){
        for (DataRecord sample : samples) {
            try {
                if (sample.getStringVal("SampleId", user).contains("Pool-")) {
                    Map<String, Object> newValues = new HashMap<>();
                    newValues.put("SampleId", getNextSampleId(sample));
                    newValues.put("ExemplarSampleType", getSampleTypeToAssign(sample));
                    sample.setFields(newValues, user);
                    if (sample.getChildrenOfType("SeqRequirementPooled", user).length > 0) {
                        List<DataRecord> seqRequirementPooledChild = Arrays.asList(sample.getChildrenOfType("SeqRequirementPooled", user));
                        logInfo("Found SeqRequirementPooled for Sample " + seqRequirementPooledChild.get(0).getStringVal("SampleId", user) +
                                ". It is an ampliseq DNA pool, therefore associated  SeqRequirementPooled records will be deleted. Because the PoolId is overwritten, the " +
                                "associated SeqRequirementPooled are not relevant.");
                        dataRecordManager.deleteDataRecords(seqRequirementPooledChild, null, false, user);
                    }
                }
            } catch (RemoteException e) {
                logError(String.format("RemoteException Exception -> Error while replacing Pool SamplId value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (IoError ioError) {
                logError(String.format("IoError Exception -> Error while replacing Pool SamplId value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while replacing Pool SamplId value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (ServerException e) {
                logError(String.format("ServerException Exception -> Error while replacing Pool SamplId value for Sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
        }
    }
}
