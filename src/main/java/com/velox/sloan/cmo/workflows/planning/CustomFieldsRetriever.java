package com.velox.sloan.cmo.workflows.planning;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.exception.recoverability.serverexception.UnrecoverableServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.QCDatumModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;


/**
 * This Plugin is written to update and display samples and related fields that are important for Pool planning.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class CustomFieldsRetriever extends DefaultGenericPlugin {

    private static Set<String> unpooledLibTypes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
           "dna/cdna library", "dna library", "cdna library", "protein library", "ont library"
    )));
    private static Set<String> pooledLibTypes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "pooled library"
    )));

    public CustomFieldsRetriever() {
        setTaskEntry(true);
        setOrder(PluginOrder.LATE.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("DISPLAY CUSTOM FIELDS");
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
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

        } catch (NotFound e) {
            String errMsg = String.format("NotFound Exception while retrieving custom fields for samples:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception while retrieving custom fields for samples:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }catch (IoError e) {
            String errMsg = String.format("IoError Exception while retrieving custom fields for samples:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidValue invalidValue) {
            String errMsg = String.format("InvalidValue Exception while retrieving custom fields for samples:\n%s", ExceptionUtils.getStackTrace(invalidValue));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * The method checks if the active task contains Sample and ProtocolRecord attachments.
     *
     * @param attachedSamples
     * @param attachedPlanninProtocols
     * @return boolean value
     */
    private boolean hasValidAttachments(List<DataRecord> attachedSamples, List<DataRecord> attachedPlanninProtocols) {
        return !(attachedSamples.isEmpty() && attachedPlanninProtocols.isEmpty());
    }


    /**
     * This method is designed to find and return the Sample in hierarchy with a desired child DataType
     *
     * @param sample
     * @param childDataType
     * @return Sample DataRecord
     * @throws IoError
     * @throws RemoteException
     */
    private DataRecord getSampleParentWithDesiredChildRecord(DataRecord sample, String childDataType) throws IoError, RemoteException, NotFound, UnrecoverableServerException, ServerException {
        DataRecord record = null;
        Stack<DataRecord> samplePile = new Stack<>();
        samplePile.push(sample);
        do {
            DataRecord startSample = samplePile.pop();
            List<DataRecord> parentRecords = startSample.getParentsOfType("Sample", user);
            if (!parentRecords.isEmpty() && parentRecords.get(0).getChildrenOfType(childDataType, user).length > 0) {
                record = parentRecords.get(0);
            }
            if (!parentRecords.isEmpty() && record == null) {
                samplePile.push(parentRecords.get(0));
            }
        } while (!samplePile.empty());
        return record;
    }

    /**
     * This method looks for Sample that has a child record of type IndexBarcode. If not found on the first sample,
     * the method keep looking upstream the parent hierarchy of the sample until a parent with a child record of IndexBarcode
     * is found.
     *
     * @param sample
     * @return String IndexId value for Sample if found, else returns "" with a warning.
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     */
    private String getSampleLibraryIndexId(DataRecord sample) throws NotFound, RemoteException, IoError, ServerException {
        DataRecord[] indexBarcodeRecord = sample.getChildrenOfType("IndexBarcode", user);
        String indexId = null;
        if (indexBarcodeRecord.length > 0) {
            return indexBarcodeRecord[0].getStringVal("IndexId", user);
        } else {
            DataRecord parentSample = getSampleParentWithDesiredChildRecord(sample, "IndexBarcode");
            if (parentSample != null && parentSample.getChildrenOfType("IndexBarcode", user).length > 0
                    && parentSample.getChildrenOfType("IndexBarcode", user)[0].getValue("IndexId", user) != null) {
                indexId = parentSample.getChildrenOfType("IndexBarcode", user)[0].getStringVal("IndexId", user);
            }
        }
        if (indexId != null) {
            return indexId;
        } else {
            clientCallback.displayWarning(String.format("IndexId not found for sample '%s'.\nPlease double check.", sample.getStringVal("SampleId", user)));
            return "";
        }
    }

    /**
     * This method looks for Sample that has a child record of type IndexBarcode. If not found on the first sample,
     * the method keep looking upstream the parent hierarchy of the sample until a parent with a child record of IndexBarcode
     * is found.
     *
     * @param sample
     * @return String IndexTag value for Sample if found, else returns "" with a warning.
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws ServerException
     */
    private String getSampleLibraryIndexTag(DataRecord sample) throws NotFound, RemoteException, IoError, ServerException {
        String indexTag = null;
        DataRecord[] indexBarcodeRecords = sample.getChildrenOfType("IndexBarcode", user);
        if (indexBarcodeRecords.length > 0) {
            return indexBarcodeRecords[0].getStringVal("IndexTag", user);
        } else {
            DataRecord parentSample = getSampleParentWithDesiredChildRecord(sample, "IndexBarcode");
            if (parentSample != null && parentSample.getChildrenOfType("IndexBarcode", user).length > 0
                    && parentSample.getChildrenOfType("IndexBarcode", user)[0].getValue("IndexTag", user) != null) {
                indexTag = parentSample.getChildrenOfType("IndexBarcode", user)[0].getStringVal("IndexTag", user);
            }
        }
        if (indexTag != null) {
            return indexTag;
        } else {
            clientCallback.displayWarning(String.format("IndexTag not found for sample '%s'.\nPlease double check.", sample.getStringVal("SampleId", user)));
            return "";
        }
    }

    /**
     * This method looks for Sample that has a child records of type Sample with ExemplarSampleType= "DNA Library or cDNA Library". If not found on the first sample,
     * the method keep looking upstream the parent hierarchy of the sample until a sample with a parent samples of correct type are found.
     * is found.
     *
     * @param sample
     * @return List<DataRecord> samples that are parent of the Sample pool.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private List<DataRecord> getSamplesInPool(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException {
        DataRecord startingSample = sample;
        List<DataRecord> samplesInPool = new ArrayList<>();
        boolean found = false;
        do {
            List<DataRecord> parentSamples = startingSample.getParentsOfType("Sample", user);
            String sampleType = parentSamples.get(0).getStringVal("ExemplarSampleType", user).toLowerCase();
            if(parentSamples.size() > 0 && unpooledLibTypes.contains(sampleType)){
                samplesInPool = parentSamples;
                found = true;
            } else {
                startingSample = parentSamples.get(0);
            }
        } while (!found && startingSample.getParentsOfType("Sample", user).size() > 0);
        return samplesInPool;
    }

    /**
     * This method first finds Samples in Sample pool. And then iterate through each sample to find related IndexId. And then
     * creates the List of IndexIds for samples in pools. The values in the List are finally concatenated to return all the IndexIds
     * as a String.
     *
     * @param sample
     * @return String IndexId values for Samples in Pool if found, else returns "" with a warning.
     * @throws RemoteException
     * @throws NotFound
     * @throws IoError
     */
    private String getIndexIdsForPooledSample(DataRecord sample) throws RemoteException, NotFound, IoError, ServerException {
        List<DataRecord> samplesInPool = getSamplesInPool(sample);
        List<String> indexIds = new ArrayList<>();
        for (DataRecord samp : samplesInPool) {
            indexIds.add(getSampleLibraryIndexId(samp));
        }
        return StringUtils.join(indexIds, ",");
    }

    /**
     * This method first finds Samples in Sample pool. And then iterate through each sample to find related IndexTag. And then
     * creates the List of IndexTags for samples in pools. The values in the List are finally concatenated to return all the IndexTags
     * as a String.
     *
     * @param sample
     * @return String IndexTag values for Samples in Pool if found, else returns "" with a warning.
     * @throws RemoteException
     * @throws NotFound
     * @throws IoError
     * @throws ServerException
     */
    private String getIndexTagsForPooledSample(DataRecord sample) throws RemoteException, NotFound, IoError, ServerException {
        List<DataRecord> samplesInPool = getSamplesInPool(sample);
        List<String> indexTags = new ArrayList<>();
        if (samplesInPool.size() > 0) {
            for (DataRecord samp : samplesInPool) {
                indexTags.add(getSampleLibraryIndexTag(samp));
            }
        }
        return StringUtils.join(indexTags, ",");
    }

    /**
     * This method looks for Sample that has a child record of type SeqRequirement/SeqRequirementPooled. If not found on the starting sample,
     * the method keep looking upstream the parent hierarchy of the sample until a parent with a child record of SeqRequirement/SeqRequirementPooled
     * is found.
     *
     * @param sample
     * @return Double value RequestedReads value from SeqRequirement/SeqRequirementPooled record if found, else returns 0.0 with a warning.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     * @throws InvalidValue
     */
    private Double getRequestedReadsForSample(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        String sequencingRequirementDatatype = getSequencingRequirementDataType(sample);
        Double requestedReads = null;
        DataRecord[] sequencingRequirementRecords = sample.getChildrenOfType(sequencingRequirementDatatype, user);
        if (sequencingRequirementRecords.length > 0 && sequencingRequirementRecords[0].getValue("RequestedReads", user) != null) {
            requestedReads = sequencingRequirementRecords[0].getDoubleVal("RequestedReads", user);
        } else {
            DataRecord parentSample = getSampleParentWithDesiredChildRecord(sample, sequencingRequirementDatatype);
            if (parentSample != null && parentSample.getChildrenOfType(sequencingRequirementDatatype, user).length > 0
                    && parentSample.getChildrenOfType(sequencingRequirementDatatype, user)[0].getValue("RequestedReads", user) != null) {
                requestedReads = parentSample.getChildrenOfType(sequencingRequirementDatatype, user)[0].getDoubleVal("RequestedReads", user);
            }
        }
        if (requestedReads != null && requestedReads > 0.0) {
            return requestedReads;
        } else {
            clientCallback.displayWarning(String.format("Invalid Sequencing Requirements '%.4f' for sample '%s'.\nPlease double check.", requestedReads, sample.getStringVal("SampleId", user)));
            return 0.0;
        }
    }

    /**
     * This method looks for Sample that has a child record of type SeqRequirement/SeqRequirementPooled. If not found on the starting sample,
     * the method keep looking upstream the parent hierarchy of the sample until a parent with a child record of SeqRequirement/SeqRequirementPooled
     * is found.
     *
     * @param sample
     * @return Integer value CoverageTarget value from SeqRequirement/SeqRequirementPooled record if found, else returns 0 with a warning.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     * @throws InvalidValue
     */
    private Integer getCoverageTargetForSample(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        String sequencingRequirementDatatype = getSequencingRequirementDataType(sample);
        Integer coverageTarget = null;
        DataRecord[] sequencingRequirementRecords = sample.getChildrenOfType(sequencingRequirementDatatype, user);
        if (sequencingRequirementRecords.length > 0 && sequencingRequirementRecords[0].getValue("CoverageTarget", user) != null) {
            coverageTarget = sequencingRequirementRecords[0].getIntegerVal("CoverageTarget", user);
        } else {
            DataRecord parentSample = getSampleParentWithDesiredChildRecord(sample, sequencingRequirementDatatype);
            if (parentSample != null && parentSample.getChildrenOfType(sequencingRequirementDatatype, user).length > 0
                    && parentSample.getChildrenOfType(sequencingRequirementDatatype, user)[0].getValue("CoverageTarget", user) != null) {
                coverageTarget = parentSample.getChildrenOfType(sequencingRequirementDatatype, user)[0].getIntegerVal("CoverageTarget", user);
            }
        }
        if (coverageTarget != null && coverageTarget > 0) {
            return coverageTarget;
        } else {
            clientCallback.displayWarning(String.format("Invalid Target Coverage '%d' for sample '%s'.\nPlease double check.", coverageTarget, sample.getStringVal("SampleId", user)));
            return 0;
        }
    }

    /**
     * Because the sequencing requirements for a Library Sample and Pooled Library Sample are stored under different DataRecords,
     * this method returns the DataType name for DataRecord containing Sequencing Requirement values for a sample.
     *
     * @param sample
     * @return String value DataType name for DataRecord containing Sequencing Requirement values for a sample.
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws InvalidValue
     */
    private String getSequencingRequirementDataType(DataRecord sample) throws NotFound, RemoteException, ServerException, InvalidValue {
        String sampleType = sample.getStringVal("ExemplarSampleType", user).toLowerCase();
        if ( unpooledLibTypes.contains(sampleType) ){
            return "SeqRequirement";
        }
        if ( pooledLibTypes.contains(sampleType) ) {
            return "SeqRequirementPooled";
        } else {
            clientCallback.displayError(String.format("Invalid SampleType '%s' for sample '%s'", sampleType, sample.getStringVal("SampleId", user)));
            throw new InvalidValue(String.format("Invalid SampleType '%s' for sample '%s'", sampleType, sample.getStringVal("SampleId", user)));
        }
    }

    /**
     * This method looks for Sample that has a child record of type SeqRequirement/SeqRequirementPooled. If not found on the starting sample,
     * the method keep looking upstream the parent hierarchy of the sample until a parent with a child record of SeqRequirement/SeqRequirementPooled
     * is found.
     *
     * @param sample
     * @return String value SequencingRunType value from SeqRequirement/SeqRequirementPooled record if found, else returns "" with a warning.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     * @throws InvalidValue
     */
    private String getSequencingRunTypeForSample(DataRecord sample) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        String sequencingRequirementDatatype = getSequencingRequirementDataType(sample);
        String sequencingRunType = null;
        DataRecord[] sequencingRequirementRecords = sample.getChildrenOfType(sequencingRequirementDatatype, user);
        if (sequencingRequirementRecords.length > 0 && sequencingRequirementRecords[0].getValue("SequencingRunType", user) != null) {
            sequencingRunType = sequencingRequirementRecords[0].getStringVal("SequencingRunType", user);
        } else {
            DataRecord parentSample = getSampleParentWithDesiredChildRecord(sample, sequencingRequirementDatatype);
            if (parentSample != null && parentSample.getChildrenOfType(sequencingRequirementDatatype, user).length > 0
                    && parentSample.getChildrenOfType(sequencingRequirementDatatype, user)[0].getValue("SequencingRunType", user) != null) {
                sequencingRunType = parentSample.getChildrenOfType(sequencingRequirementDatatype, user)[0].getStringVal("SequencingRunType", user);
            }
        }
        if (sequencingRunType != null) {
            return sequencingRunType;
        } else {
            clientCallback.displayWarning(String.format("SequencingRunType not found for sample '%s'.\nPlease double check.", sample.getStringVal("SampleId", user)));
            return "";
        }
    }

    /**
     * This method loops through the QcData records and finds the record that whose values are mapped to the sample in question.
     *
     * @param qcData
     * @return Double value if found, else returns null.
     * @throws NotFound
     * @throws RemoteException
     */
    private Double getAverageSizeFromQcData(List<DataRecord> qcData) throws NotFound, RemoteException {
        for (DataRecord rec : qcData) {
            if (rec.getBooleanVal("MapToSample", user) && rec.getValue("AvgSize", user) != null) {
                return rec.getDoubleVal("AvgSize", user);
            }
        }
        return null;
    }

    /**
     * This method loops through the samples and finds associated QcData records and finds the AvgSize from the QcRecords.
     *
     * @param sample
     * @return Double value if found, else returns "" with a warning.
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws ServerException
     */
    private Double getAvgSizeForSample(DataRecord sample) throws NotFound, RemoteException, IoError, ServerException {
        Double avgSize = null;
        DataRecord[] qcDatumRecords = sample.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user);
        if (qcDatumRecords.length > 0 && qcDatumRecords[0].getValue("AvgSize", user) != null) {
            avgSize = qcDatumRecords[0].getDoubleVal("AvgSize", user);
        } else {
            DataRecord parentSample = getSampleParentWithDesiredChildRecord(sample, QCDatumModel.DATA_TYPE_NAME);
            if (parentSample != null && parentSample.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user).length > 0) {
                avgSize = getAverageSizeFromQcData(Arrays.asList(parentSample.getChildrenOfType(QCDatumModel.DATA_TYPE_NAME, user)));
            }
        }
        if (avgSize != null && avgSize > 0.0) {
            return avgSize;
        } else {
            clientCallback.displayWarning(String.format("Could not find AvgSize '%.4f' for sample '%s'.\nPlease double check.", avgSize, sample.getStringVal("SampleId", user)));
            return 0.0;
        }
    }

    /**
     * This method loops through the planningStepProtocolRecords and updates the values for fields.
     *
     * @param samples
     * @param planningStepProtocolRecords
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     * @throws InvalidValue
     */
    private void setPlanningStepValues(List<DataRecord> samples, List<DataRecord> planningStepProtocolRecords) throws IoError, RemoteException, NotFound, ServerException, InvalidValue {
        for (DataRecord sample : samples) {
            String sampleId = sample.getStringVal("SampleId", user);
            String sampleType = sample.getStringVal("ExemplarSampleType", user);
            for (DataRecord rec : planningStepProtocolRecords) {
                String protocolSampleId = rec.getStringVal("SampleId", user);
                if (protocolSampleId.equals(sampleId)) {
                    Map<String, Object> fieldValues = new HashMap<>();
                    if ( pooledLibTypes.contains(sampleType.toLowerCase()) ){
                        fieldValues.put("IndexId", getIndexIdsForPooledSample(sample));
                        fieldValues.put("IndexTag", getIndexTagsForPooledSample(sample));
                    } else {
                        fieldValues.put("IndexId", getSampleLibraryIndexId(sample));
                        fieldValues.put("IndexTag", getSampleLibraryIndexTag(sample));
                    }
                    fieldValues.put("RequestedReads", getRequestedReadsForSample(sample));
                    fieldValues.put("CoverageTarget", getCoverageTargetForSample(sample));
                    fieldValues.put("SequencingRunType", getSequencingRunTypeForSample(sample));
                    fieldValues.put("AvgSize", getAvgSizeForSample(sample));
                    rec.setFields(fieldValues, user);
                }
            }
        }
    }
}