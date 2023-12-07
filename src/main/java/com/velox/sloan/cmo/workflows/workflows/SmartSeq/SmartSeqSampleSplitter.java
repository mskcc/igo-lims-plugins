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
//        setTaskSubmit(true);
//        setOrder(PluginOrder.LAST.getOrder());
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("SPLIT SMARTSEQ SAMPLE")
                && !activeTask.getTask().getTaskOptions().containsKey("_SMARTSEQ SAMPLE SPLITTED");
    }

    public PluginResult run() throws RemoteException, com.velox.api.util.ServerException {
        try {
            List<DataRecord> samplesAttachedToTask = activeTask.getAttachedDataRecords("Sample", user);

            List<Long> attachedSamples = new LinkedList<>();
            List<Long> toGetAttached = new LinkedList<>();
            for (DataRecord sample : samplesAttachedToTask) {
                //attachedSamples.add(sample.getRecordId());
                //List<DataRecord> newSamples = new LinkedList<>();
                logInfo("Removed sample with record id: " + sample.getRecordId() + "\n");

                String sampleId = sample.getStringVal("SampleId", user);
                String recipe = sample.getStringVal("Recipe", user);
                String otherSampleId = sample.getStringVal("OtherSampleId", user);
                String requestId = sample.getStringVal("RequestId", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);
                String cellCount = sample.getStringVal("CellCount", user);
                String species = sample.getStringVal("Species", user);
                String sampleOrigin = sample.getStringVal("SampleOrigin", user);
                String preservation = sample.getStringVal("Preservation", user);
                String plateId = "";
                if (sample.getParentsOfType("Plate", user).size() > 0) {
                    plateId = sample.getParentsOfType("Plate", user).get(0).getStringVal("PlateId", user);
                }

                int idIndex = 1;

                for (int i = 1; i <= 23; i+=2) {
                    for (char c : TO_APPEND_1) {
                        Map<String, Object> values = new HashMap<>();
                        values.put("SampleId", sampleId + "_" + idIndex++);
                        logInfo("Sample ID: " + sampleId + "_" + idIndex + "\n");

                        values.put("OtherSampleId", otherSampleId + "_" + String.valueOf(c) + String.valueOf(i));
                        logInfo("Sample name: " + otherSampleId + "_" + String.valueOf(c) + String.valueOf(i) + "\n");
                        values.put("RequestId", requestId);
                        values.put("ExemplarSampleType", sampleType);
                        values.put("Recipe", recipe);
                        values.put("StorageUnitPath", plateId);
                        values.put("CellCount", cellCount);
                        values.put("Species", species);
                        values.put("SampleOrigin", sampleOrigin);
                        values.put("Preservation", preservation);
                        DataRecord newRecord = dataRecordManager.addDataRecord("Sample", user);
                        newRecord.setFields(values, user);
                        //sample.addChild("Sample", values, user);
                        toGetAttached.add(newRecord.getRecordId());
                    }
                }
                for (int i = 2; i <= 24; i+=2) {
                    for (char c : TO_APPEND_1) {
                        Map<String, Object> values = new HashMap<>();
                        values.put("SampleId", sampleId + "_" + idIndex++);
                        logInfo("Sample ID: " + sampleId + "_" + idIndex + "\n");

                        values.put("OtherSampleId", otherSampleId + "_" + String.valueOf(c) + String.valueOf(i));
                        logInfo("Sample name: " + otherSampleId + "_" + String.valueOf(c) + String.valueOf(i) + "\n");
                        values.put("RequestId", requestId);
                        values.put("ExemplarSampleType", sampleType);
                        values.put("Recipe", recipe);
                        values.put("StorageUnitPath", plateId);
                        values.put("CellCount", cellCount);
                        values.put("Species", species);
                        values.put("SampleOrigin", sampleOrigin);
                        values.put("Preservation", preservation);
                        DataRecord newRecord = dataRecordManager.addDataRecord("Sample", user);
                        newRecord.setFields(values, user);
                        //sample.addChild("Sample", values, user);
                        toGetAttached.add(newRecord.getRecordId());
                    }
                }
                for (int i = 1; i <= 23; i+=2) {
                    for (char c : TO_APPEND_2) {
                        Map<String, Object> values = new HashMap<>();
                        values.put("SampleId", sampleId + "_" + idIndex++);
                        logInfo("Sample ID: " + sampleId + "_" + idIndex + "\n");

                        values.put("OtherSampleId", otherSampleId + "_" + String.valueOf(c) + String.valueOf(i));
                        logInfo("Sample name: " + otherSampleId + "_" + String.valueOf(c) + String.valueOf(i) + "\n");
                        values.put("RequestId", requestId);
                        values.put("ExemplarSampleType", sampleType);
                        values.put("Recipe", recipe);
                        values.put("StorageUnitPath", plateId);
                        values.put("CellCount", cellCount);
                        values.put("Species", species);
                        values.put("SampleOrigin", sampleOrigin);
                        values.put("Preservation", preservation);
                        DataRecord newRecord = dataRecordManager.addDataRecord("Sample", user);
                        newRecord.setFields(values, user);
                        //sample.addChild("Sample", values, user);
                        toGetAttached.add(newRecord.getRecordId());
                    }
                }
                for (int i = 2; i <= 24; i+=2) {
                    for (char c : TO_APPEND_2) {
                        Map<String, Object> values = new HashMap<>();
                        values.put("SampleId", sampleId + "_" + idIndex++);
                        logInfo("Sample ID: " + sampleId + "_" + idIndex + "\n");

                        values.put("OtherSampleId", otherSampleId + "_" + String.valueOf(c) + String.valueOf(i));
                        logInfo("Sample name: " + otherSampleId + "_" + String.valueOf(c) + String.valueOf(i) + "\n");
                        values.put("RequestId", requestId);
                        values.put("ExemplarSampleType", sampleType);
                        values.put("Recipe", recipe);
                        values.put("StorageUnitPath", plateId);
                        values.put("CellCount", cellCount);
                        values.put("Species", species);
                        values.put("SampleOrigin", sampleOrigin);
                        values.put("Preservation", preservation);
                        DataRecord newRecord = dataRecordManager.addDataRecord("Sample", user);
                        newRecord.setFields(values, user);
                        //sample.addChild("Sample", values, user);
                        toGetAttached.add(newRecord.getRecordId());
                    }
                }
                this.activeTask.addAttachedRecordIdList(toGetAttached);
                logInfo("Attached new splitted samples");
                sample.setDataField("ExemplarSampleStatus", "Completed - Smart-Seq cDNA Preparation", user);
                logInfo("Original sample status updated to: Completed - Smart-Seq cDNA Preparation");
                this.activeTask.removeTaskAttachment(sample.getRecordId());
                logInfo("Removed the attached sample!!");
            }


            activeTask.getTask().getTaskOptions().put("_SMARTSEQ SAMPLE SPLITTED", "");
            logInfo("Task option updated!");

        } catch (NotFound | InvalidValue e) {
            String errMsg = String.format("Not Found or Invalid Value exception while splitting SmartSeq samples:\n%s", ExceptionUtils.getStackTrace(e));

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
        logInfo("Right before returning true for plugin result!");
        return new PluginResult(true);
    }
}
