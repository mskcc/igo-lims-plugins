package com.velox.sloan.cmo.workflows.workflows.SmartSeq;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.xml.crypto.Data;
import java.io.IOError;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This plugin is designed to split each SMART Seq sample to 384 samples.
 * @author Fahimeh Mirhaj
 * */
public class SmartSeqSampleSplitter extends DefaultGenericPlugin {
    public static final char[] TO_APPEND_1 = {'A', 'C', 'E', 'G', 'I', 'K', 'M', 'O'};
    public static final char[] TO_APPEND_2 = {'B', 'D', 'F', 'H', 'J', 'L', 'N', 'P'};
    public SmartSeqSampleSplitter () {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("SPLIT SMARTSEQ SAMPLE")
                && !activeTask.getTask().getTaskOptions().containsKey("_SMARTSEQ SAMPLE SPLITTED");
    }

    public PluginResult run () throws RemoteException, com.velox.api.util.ServerException {
        try {
            List<DataRecord> samplesAttachedToTask = activeTask.getAttachedDataRecords("Sample", user);
            Map<String, Object> values = new HashMap<>();
            List<Long> attachedSamples = new LinkedList<>();
            List<Long> toGetAttached = new LinkedList<>();
            List<DataRecord> newSamples = new LinkedList<>();

            for (DataRecord sample : samplesAttachedToTask) {
                Long attachedSampleRecordId = sample.getRecordId();
                attachedSamples.add(attachedSampleRecordId);
                String sampleId = sample.getStringVal("SampleId", user);
                String recipe = sample.getStringVal("Recipe", user);
                String otherSampleId = sample.getStringVal("OtherSampleId", user);
                String requestId = sample.getStringVal("RequestId", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);
                int volume;

                for (int i = 1; i <= 23; i+=2) {
                    for (char c : TO_APPEND_1) {
                        values.put("SampleId", sampleId + "_" + TO_APPEND_1 + i);
                        values.put("OtherSampleId", otherSampleId);
                        values.put("AltId", otherSampleId);
                        values.put("RequestId", requestId);
                        values.put("ExemplarSampleType", sampleType);
                        values.put("Recipe", recipe);
                        DataRecord newRecord = dataRecordManager.addDataRecord("Sample", user);
                        newRecord.setFields(values, user);
                        newSamples.add(newRecord);
                        toGetAttached.add(newRecord.getRecordId());
                    }
                }
                for (char c : TO_APPEND_2) {
                    for (int i = 2; i <= 24; i+=2) {
                        values.put("SampleId", sampleId + "_" + TO_APPEND_2 + i);
                        values.put("OtherSampleId", otherSampleId);
                        values.put("AltId", otherSampleId);
                        values.put("RequestId", requestId);
                        values.put("ExemplarSampleType", sampleType);
                        values.put("Recipe", recipe);
                        DataRecord newRecord = dataRecordManager.addDataRecord("Sample", user);
                        newRecord.setFields(values, user);
                        newSamples.add(newRecord);
                        toGetAttached.add(newRecord.getRecordId());
                    }
                }
                this.activeTask.removeTaskAttachments(attachedSamples);
                this.activeTask.addAttachedRecordIdList(toGetAttached);
            }

        } catch (NotFound | InvalidValue e) {

        } catch (ServerException e) {
            String errMsg = String.format("ServerException while splitting SmartSeq samples:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);

        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException while splitting SmartSeq samples:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IoError e) {
            throw new RuntimeException(e);
        } catch (AlreadyExists e) {
            throw new RuntimeException(e);
        }
        return new PluginResult(true);
    }
}
