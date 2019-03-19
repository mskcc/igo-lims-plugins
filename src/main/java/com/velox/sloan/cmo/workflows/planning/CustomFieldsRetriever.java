package com.velox.sloan.cmo.workflows.planning;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.QCDatumModel;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;

public class CustomFieldsRetriever extends DefaultGenericPlugin {

    List<String> librarySampleTypes = Arrays.asList("dna library", "cdna library", "pooled library");
    public CustomFieldsRetriever() {
        setTaskEntry(true);
        setOrder(PluginOrder.LATE.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().keySet().contains("DISPLAY CUSTOM FIELDS");
    }

    @Override
    public PluginResult run() throws com.velox.api.util.ServerException {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedPlanningProtocols = activeTask.getAttachedDataRecords("PlanningStepProtocol1", user);

            if (!hasValidAttachments(attachedSamples, attachedPlanningProtocols)) {
                clientCallback.displayError("Task does not have valid attachments or there are no records to display\n" +
                        "There must be 'Sample' and 'PlanningStepProtocol1' records attached to this task to display custom fields.");

                logError("Task does not have valid attachments or there are no records to display\n" +
                        "There must be 'Sample' and 'PlanningStepProtocol1' records attached to this task to display custom fields.");
            }
            setPlanningStepValues(attachedSamples, attachedPlanningProtocols);

        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while retrieving custom fields for samples", e));
            logError(String.format("Error while retrieving custom fields for samples"), e);
            return new PluginResult(false);
        }

        return new PluginResult(true);
    }

    private boolean hasValidAttachments(List<DataRecord> attachedSamples, List<DataRecord> attachedPlanninProtocols) {
        return !(attachedSamples.isEmpty() && attachedPlanninProtocols.isEmpty());
    }

    private String getSampleLibraryIndexId(DataRecord sample) throws NotFound, RemoteException, IoError {
        if (sample.getStringVal("ExemplarSampleType", user).toLowerCase().equals(librarySampleTypes.get(2))){
            return getIndexIdsForPooledSample(sample);
        }
        DataRecord[] indexBarcodeRecord = sample.getChildrenOfType("IndexBarcode", user);
        if (indexBarcodeRecord.length > 0) {
            return indexBarcodeRecord[0].getStringVal("IndexId", user);
        } else {
            List<DataRecord> parentSamples = sample.getAncestorsOfType("Sample", user);
            for (DataRecord samp : parentSamples) {
                if (samp.getChildrenOfType("IndexBarcode", user).length > 0) {
                    return samp.getChildrenOfType("IndexBarcode", user)[0].getStringVal("IndexId", user);
                }
            }
        }
        return "";
    }

    private String getSampleLibraryIndexTag(DataRecord sample) throws NotFound, RemoteException, IoError {
        if (sample.getStringVal("ExemplarSampleType", user).toLowerCase().equals(librarySampleTypes.get(2))){
            return getIndexTagsForPooledSample(sample);
        }
        DataRecord[] indexBarcodeRecords = sample.getChildrenOfType("IndexBarcode", user);
        if (indexBarcodeRecords.length > 0) {
            return indexBarcodeRecords[0].getStringVal("IndexTag", user);
        } else {
            List<DataRecord> parentSamples = sample.getAncestorsOfType("Sample", user);
            for (DataRecord samp : parentSamples) {
                if (samp.getChildrenOfType("IndexBarcode", user).length > 0) {
                    return samp.getChildrenOfType("IndexBarcode", user)[0].getStringVal("IndexTag", user);
                }
            }
        }
        return "";
    }

    private String getIndexIdsForPooledSample(DataRecord sample) throws RemoteException, NotFound, IoError {
        List <DataRecord> samplesInPool = sample.getAncestorsOfType("Sample", user);
        List<String> indexIds = new ArrayList<>();
        for (DataRecord samp : samplesInPool){
            String sampleType = samp.getStringVal("ExemplarSampleType", user).toLowerCase();
            if ((sampleType.equals(librarySampleTypes.get(0)) || sampleType.equals(librarySampleTypes.get(1)))
                    && samp.getChildrenOfType("IndexBarcode", user).length > 0) {
                indexIds.add(samp.getChildrenOfType("IndexBarcode", user)[0].getStringVal("IndexId", user));
            }
        }
        return StringUtils.join(indexIds,",");
    }

    private String getIndexTagsForPooledSample(DataRecord sample) throws RemoteException, NotFound, IoError {
        List <DataRecord> samplesInPool = sample.getAncestorsOfType("Sample", user);
        List<String> indexTags = new ArrayList<>();
        for (DataRecord samp : samplesInPool){
            String sampleType = samp.getStringVal("ExemplarSampleType", user).toLowerCase();
            if ((sampleType.equals(librarySampleTypes.get(0)) || sampleType.equals(librarySampleTypes.get(1)))
                    && samp.getChildrenOfType("IndexBarcode", user).length > 0) {
                indexTags.add(samp.getChildrenOfType("IndexBarcode", user)[0].getStringVal("IndexTag", user));
            }
        }
        return StringUtils.join(indexTags,",");
    }

    private Double getRequestedReadsForsample(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        String sequencingRequirementDatatype = getSequencingRequirementDataType(sample);
        Double requestedReads = null;
        DataRecord[] sequencingRequirementRecords = sample.getChildrenOfType(sequencingRequirementDatatype, user);
        if (sequencingRequirementRecords.length > 0 && sequencingRequirementRecords[0].getValue("RequestedReads", user)!=null) {
            requestedReads = sequencingRequirementRecords[0].getDoubleVal("RequestedReads", user);
        } else {
            List<DataRecord> parentSamples = sample.getAncestorsOfType("Sample", user);
            for (DataRecord samp : parentSamples) {
                if (samp.getChildrenOfType(sequencingRequirementDatatype, user).length > 0) {
                    requestedReads = samp.getChildrenOfType(sequencingRequirementDatatype, user)[0].getDoubleVal("RequestedReads", user);
                    break;
                }
            }
        }
        if (requestedReads !=null && requestedReads > 0.0) {
            return requestedReads;
        } else {
            clientCallback.displayWarning(String.format("Invalid Sequencing Requirements '%.4f' for sample '%s'.\nPlease double check.", requestedReads, sample.getStringVal("SampleId", user)));
            return 0.0;
        }
    }

    private Integer getCoverageTargetForsample(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        String sequencingRequirementDatatype = getSequencingRequirementDataType(sample);
        logInfo("SeqRequirement DataType: " + sequencingRequirementDatatype);
        Integer coverageTarget = null;
        DataRecord[] sequencingRequirementRecords = sample.getChildrenOfType(sequencingRequirementDatatype, user);
        if (sequencingRequirementRecords.length > 0 && sequencingRequirementRecords[0].getValue("CoverageTarget", user)!=null) {
            coverageTarget = sequencingRequirementRecords[0].getIntegerVal("CoverageTarget", user);
        } else {
            List<DataRecord> parentSamples = sample.getAncestorsOfType("Sample", user);
            for (DataRecord samp : parentSamples) {
                if (samp.getChildrenOfType(sequencingRequirementDatatype, user).length > 0 && samp.getChildrenOfType(sequencingRequirementDatatype, user)[0].getValue("CoverageTarget", user)!=null) {
                    coverageTarget = samp.getChildrenOfType(sequencingRequirementDatatype, user)[0].getIntegerVal("CoverageTarget", user);
                    break;
                }
            }
        }
        if (coverageTarget!=null && coverageTarget > 0) {
            return coverageTarget;
        } else {
            clientCallback.displayWarning(String.format("Invalid Target Coverage '%d' for sample '%s'.\nPlease double check.", coverageTarget, sample.getStringVal("SampleId", user)));
            return 0;
        }
    }

    private String getSequencingRequirementDataType(DataRecord sample) throws NotFound, RemoteException, ServerException, InvalidValue {
        String sampleType = sample.getStringVal("ExemplarSampleType", user);
        logInfo("SampleType: " + sampleType);
        if (sampleType.toLowerCase().equals("dna library")){
            return "SeqRequirement";
        }
        if (sampleType.toLowerCase().equals("pooled library")){
            return "SeqRequirementPooled";
        }
        else{
            clientCallback.displayError(String.format("Invalid SampleType '%s' for sample '%s'", sampleType, sample.getStringVal("SampleId", user)));
            throw new InvalidValue(String.format("Invalid SampleType '%s' for sample '%s'", sampleType, sample.getStringVal("SampleId", user)));
        }
    }

    private String getSequencingRunTypeForsample(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        String sequencingRequirementDatatype = getSequencingRequirementDataType(sample);
        String sequencingRunType = null;
        DataRecord[] sequencingRequirementRecords = sample.getChildrenOfType(sequencingRequirementDatatype, user);
        if (sequencingRequirementRecords.length > 0 && sequencingRequirementRecords[0].getValue("SequencingRunType", user)!=null) {
            sequencingRunType = sequencingRequirementRecords[0].getStringVal("SequencingRunType", user);
        } else {
            List<DataRecord> parentSamples = sample.getAncestorsOfType("Sample", user);
            for (DataRecord samp : parentSamples) {
                if (samp.getChildrenOfType(sequencingRequirementDatatype, user).length > 0 &&
                        samp.getChildrenOfType(sequencingRequirementDatatype, user)[0].getValue("SequencingRunType", user)!=null) {
                    sequencingRunType = samp.getChildrenOfType(sequencingRequirementDatatype, user)[0].getStringVal("SequencingRunType", user);
                    break;
                }
            }
        }
        if (sequencingRunType != null) {
            return sequencingRunType;
        } else {
            clientCallback.displayWarning(String.format("Invalid SequencingRunType '%s' for sample '%s'.\nPlease double check.", sequencingRunType, sample.getStringVal("SampleId", user)));
            return "";
        }
    }

    private Double getAvgSizeForSample(DataRecord sample) throws NotFound, RemoteException, IoError, ServerException {
        Double avgSize = null;
        DataRecord[] qcDatumRecords = sample.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user);
        if (qcDatumRecords.length > 0 && qcDatumRecords[0].getValue("AvgSize", user)!=null) {
            avgSize = qcDatumRecords[0].getDoubleVal("AvgSize", user);
        } else {
            List<DataRecord> parentSamples = sample.getAncestorsOfType(QCDatumModel.DATA_TYPE_NAME, user);
            for (DataRecord samp : parentSamples) {
                if (samp.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user).length > 0 &&
                        samp.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user)[0].getValue("AvgSize", user)!=null) {
                    avgSize = samp.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user)[0].getDoubleVal("AvgSize", user);
                    break;
                }
            }
        }
        if (avgSize !=null && avgSize > 0.0) {
            return avgSize;
        } else {
            clientCallback.displayWarning(String.format("Could not find AvgSize '%.4f' for sample '%s'.\nPlease double check.", avgSize, sample.getStringVal("SampleId", user)));
            return 0.0;
        }
    }

    private void setPlanningStepValues(List<DataRecord> samples, List<DataRecord> planningStepProtocolRecords) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        for (DataRecord sample : samples) {
            String sampleId = sample.getStringVal("SampleId", user);
            for (DataRecord rec : planningStepProtocolRecords) {
                String protocolSampleId = rec.getStringVal("SampleId", user);
                if (protocolSampleId.equals(sampleId)) {
                    Map<String, Object> fieldValues = new HashMap<>();
                    fieldValues.put("IndexId", getSampleLibraryIndexId(sample));
                    fieldValues.put("IndexTag", getSampleLibraryIndexTag(sample));
                    fieldValues.put("RequestedReads", getRequestedReadsForsample(sample));
                    fieldValues.put("CoverageTarget", getCoverageTargetForsample(sample));
                    fieldValues.put("SequencingRunType", getSequencingRunTypeForsample(sample));
                    fieldValues.put("AvgSize", getAvgSizeForSample(sample));
                    logInfo("Printing values:\n" + fieldValues.toString());
                    rec.setFields(fieldValues, user);
                }
            }
        }
    }
}
