package com.velox.sloan.cmo.workflows.General;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.SampleCMOInfoRecordsModel;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqAnalysisSampleQCModel;
import com.velox.sloan.cmo.recmodels.SeqRequirementModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import com.velox.sloan.cmo.workflows.recordmodels.DdPcrAssayResultsModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;


/**
 * A plugin that runs on save and updates the following:
 * - Request DateModified if changes are made to Request records. The information is important for Splunk indexing and change monitoring.
 * - Maps the Human Percentage values from DDPCR results to DNA QC reports for a sample.
 * - Checks/Validates that the declared Sample fields do not contain special characters.
 * - Checks/Validates that the declared SampleCmoInfo fields do not contain special characters.
 * - Checks/Validates that the declared SampleCmoInfo CMOID values are unique.
 * - Update the TotalReads sequenced and RemainingReads to sequence on SeqRequirements records for a Sample.
 *
 * @author Ajay Sharma, Anna Patruno
 */

// declaration of class
public class IgoOnSavePlugin extends DefaultGenericPlugin {
    private final List<String> CMOINFO_FIELDS_TO_CHECK_FOR_SPECIAL_CHARACTERS = Arrays.asList("CorrectedCMOID", "CorrectedInvestPatientId", "UserSampleID",
            "OtherSampleId", "CmoPatientId", "Preservation", "TumorOrNormal", "TumorType", "CollectionYear", "Gender", "SpecimenType", "TissueSource");
    private final List<String> SAMPLE_FIELDS_TO_CHECK_FOR_SPECIAL_CHARACTERS = Arrays.asList("OtherSampleId", "UserSampleID", "Preservation", "Species",
            "TumorOrNormal", "Species", "PatientId", "CmoPatientId");
    private List<String> trackableDataTypes = Arrays.asList("Request");
    private final String FAILED = "Failed";
    private String error = null;

    // define constructor, what structure of class/object would look like
    public IgoOnSavePlugin() {
        setOnSave(true);
    }
    public IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    // defining the method
    public PluginResult run() throws ServerException {
        try {
            // returns results in a list if something is changed and save button is clicked
            List<DataRecord> theSavedRecords = dataRecordList;
            // if the list is greater than 0, something changed
            if (theSavedRecords.size() > 0) {
                // iterate through the list
                for (DataRecord record : theSavedRecords) {
                    // check that the fields exist in the fields present in the data type in the array listed in arrays.asList
                    if (trackableDataTypes.contains(record.getDataTypeName())) {
                        // set DateModified to current time
                        record.setDataField("DateModified", System.currentTimeMillis(), user);
                    }
                }
            }

            String dataTypeName = theSavedRecords.get(0).getDataTypeName();
            //set Human Percentage on QcReportDna DataRecords when Saving DdPcrAssayResults and HumanPercentageValues are present
            if (dataTypeName.equalsIgnoreCase(DdPcrAssayResultsModel.DATA_TYPE_NAME)) {
                mapHumanPercentageFromDdpcrResultsToDnaQcReport(theSavedRecords);
            }
            //validate fields for special characters on Sample Datatype
            if (dataTypeName.equalsIgnoreCase(SampleModel.DATA_TYPE_NAME)) {
                validateSampleFields(theSavedRecords, dataTypeName);
            }
            // validate fields for special characters on SampleCMOInfoRecords DataType
            if (dataTypeName.equalsIgnoreCase(SampleCMOInfoRecordsModel.DATA_TYPE_NAME)) {
                validateCmoInfoDataTypeFields(theSavedRecords, dataTypeName);
            }
            // update 'RemainingReads' to be sequenced on SeqRequirements when a SeqAnalysisSampleQC record is saved.
            for (DataRecord rec: theSavedRecords){
                logInfo(" Started updateing RemainingReads for SeqAnalysisSampleQC with Record Id: " + rec.getRecordId());
                if (rec.getDataTypeName().equalsIgnoreCase(SeqAnalysisSampleQCModel.DATA_TYPE_NAME) && !utils.isControlSample(rec, pluginLogger, user)){
                    updateRemainingReadsToSequence(rec);
                }
            }
            // if there's an error
        }  catch (NotFound e) {
            String errMsg = String.format("NotFound Exception while saving data:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception while saving data:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidValue e) {
            String errMsg = String.format("InvalidValue Exception while saving data:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IoError e) {
            String errMsg = String.format("IoError Exception while saving data:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        // returns results or returns error if any
        return new PluginResult(error == null, error == null ? new ArrayList<Object>() : Collections.singletonList(error));
    }

    /**
     * Method to get QcReport records for Sample.
     *
     * @param sample
     * @param requestId
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private List<DataRecord> getQcReportRecords(DataRecord sample, String requestId) throws IoError, RemoteException, NotFound {
        if (sample.getChildrenOfType("QcReportDna", user).length > 0) {
            return Arrays.asList(sample.getChildrenOfType("QcReportDna", user));
        }
        List<DataRecord> qcReports = new ArrayList<>();
        Stack<DataRecord> sampleStack = new Stack<>();
        sampleStack.add(sample);
        while (sampleStack.size() > 0) {
            DataRecord nextSample = sampleStack.pop();
            if (requestId.equalsIgnoreCase(nextSample.getStringVal("RequestId", user)) && nextSample.getChildrenOfType("QcReportDna", user).length > 0) {
                return Arrays.asList(nextSample.getChildrenOfType("QcReportDna", user));
            }
            List<DataRecord> parentSamples = nextSample.getParentsOfType("Sample", user);
            if (parentSamples.size() > 0 && parentSamples.get(0).getValue("RequestId", user) != null
                    && parentSamples.get(0).getStringVal("RequestId", user).equals(requestId)) {
                sampleStack.addAll(parentSamples);
            }
        }
        return qcReports;
    }

    /**
     * Method to map Human Percentage values from DdPcrAssayResults to QCReport records.
     *
     * @param savedRecords
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws InvalidValue
     * @throws ServerException
     */
    private void mapHumanPercentageFromDdpcrResultsToDnaQcReport(List<DataRecord> savedRecords) throws IoError, RemoteException, NotFound, InvalidValue {
        for (DataRecord rec : savedRecords) {
            if (rec.getDataTypeName().equalsIgnoreCase("DdPcrAssayResults") && rec.getValue("HumanPercentage", user) != null) {
                List<DataRecord> parentSamples = rec.getParentsOfType("Sample", user);
                Double humanPercentage = rec.getDoubleVal("HumanPercentage", user);
                if (parentSamples.size() > 0) {
                    DataRecord parentSample = parentSamples.get(0);
                    String requestId = parentSample.getStringVal("RequestId", user);
                    List<DataRecord> qcReports = getQcReportRecords(parentSample, requestId);
                    for (DataRecord qr : qcReports) {
                        qr.setDataField("HumanPercentage", humanPercentage, user);
                    }
                }
            }
        }
    }

    /**
     * Method to get Sum of Sequencing ReadsExamined/Sequenced from SeqAnalysisSampleQcRecords for a sample.
     * @param seqAnalysisSampleQcRecords
     * @return
     */
    private long getSumSequencingReadsExamined(List<DataRecord>seqAnalysisSampleQcRecords, DataRecord savedSequencingQcRecord){
        logInfo("Total SeqAnalysis records: " + seqAnalysisSampleQcRecords.size());
        long sumReadsExamined = 0;
        try{
            // this plugin is run before changes are committed changes to db. If the record being saved is new record,
            // it might not be in the db and therefore not part of SeqAnalysisSampleQcRecords returned by LIMS. Check
            // and add it's reads towards the sum of reads calculated in this method
            if (!utils.isIncludedInRecords(seqAnalysisSampleQcRecords, savedSequencingQcRecord, pluginLogger)){
                logInfo("Seq Qc Record Id: " + savedSequencingQcRecord.getRecordId());
                Object readsExamined = savedSequencingQcRecord.getValue(SeqAnalysisSampleQCModel.READS_EXAMINED, user);
                logInfo("Reads examined: " + readsExamined);
                Object seqQcStatus = savedSequencingQcRecord.getValue(SeqAnalysisSampleQCModel.SEQ_QCSTATUS, user);
                if (readsExamined != null && !seqQcStatus.toString().equalsIgnoreCase(FAILED)){
                    sumReadsExamined += (long)readsExamined;
                }
            }
            for (DataRecord rec : seqAnalysisSampleQcRecords){
                logInfo("Seq Qc Record Id: " + rec.getRecordId());
                Object readsExamined = rec.getValue(SeqAnalysisSampleQCModel.READS_EXAMINED, user);
                logInfo("Reads examined: " + readsExamined);
                Object seqQcStatus = rec.getValue(SeqAnalysisSampleQCModel.SEQ_QCSTATUS, user);
                if (readsExamined != null && !seqQcStatus.toString().equalsIgnoreCase(FAILED)){
                    sumReadsExamined += (long)readsExamined;
                }
            }
        } catch (RemoteException | NotFound e) {
            logError(String.format("%s => Error while calculating sum of Reads Examined:\n%s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e)));
        }
        return sumReadsExamined;
    }

    /**
     * Method to update remaining reads on SeqRequirements record.
     * @param sampleLevelSequencingQc
     */
    private void updateRemainingReadsToSequence(DataRecord sampleLevelSequencingQc){
        List<DataRecord> seqQcRecords;

        try{
            List<DataRecord> parentSample = sampleLevelSequencingQc.getParentsOfType(SampleModel.DATA_TYPE_NAME, user);
            if (parentSample.size() == 1){
                seqQcRecords = utils.getSequencingQcRecords(parentSample.get(0), pluginLogger, user, clientCallback);
                List<DataRecord> seqRequirements = utils.getRecordsOfTypeFromParents(seqQcRecords.get(0),
                        SampleModel.DATA_TYPE_NAME, SeqRequirementModel.DATA_TYPE_NAME, user, pluginLogger);
                if(seqRequirements.size()==0){
                    throw new NotFound(String.format("Cannot find %s DataRecord for Sample with Record Id %d", SeqRequirementModel.DATA_TYPE_NAME, parentSample.get(0).getRecordId()));
                }
                DataRecord seqRequirementRecord = seqRequirements.get(0);
                logInfo("Sequencing requirement record id: " + seqRequirementRecord.getRecordId());
                Object readsRequested = seqRequirementRecord.getValue(SeqRequirementModel.REQUESTED_READS, user);
                logInfo("Requested Reads : " + readsRequested);
                long totalReadsExamined = getSumSequencingReadsExamined(seqQcRecords, sampleLevelSequencingQc);
                logInfo("Total reads examined : " + totalReadsExamined);
                assert readsRequested != null;
                long remainingReads = 0L;
                if((Math.floor((double)readsRequested) * 1000000L) > totalReadsExamined){
                    remainingReads = ((long)Math.floor((double)readsRequested) * 1000000L) - totalReadsExamined;
                }
                seqRequirementRecord.setDataField("RemainingReads", remainingReads, user);
                seqRequirementRecord.setDataField(SeqRequirementModel.READ_TOTAL, totalReadsExamined, user);
                String msg = String.format("Updated 'RemainingReads' and 'ReadTotal'on %s related to %s record with Record Id: %d",
                        SeqRequirementModel.DATA_TYPE_NAME, SeqAnalysisSampleQCModel.DATA_TYPE_NAME, sampleLevelSequencingQc.getRecordId());
                logInfo(msg);
            }
        } catch (IoError | RemoteException | NotFound | InvalidValue e) {
            logError(String.format("%s => Error while updating Remaining Reads to Sequence on %s related to %s " +
                    "Record with Record Id: %d\n%s", ExceptionUtils.getRootCauseMessage(e), SeqRequirementModel.DATA_TYPE_NAME, SeqAnalysisSampleQCModel.DATA_TYPE_NAME, sampleLevelSequencingQc.getRecordId(),
                    ExceptionUtils.getStackTrace(e)));
        }
    }

    /**
     * Method to check if the ExemplarSampleType is 'Pooled Library'.
     *
     * @param rec
     * @return
     */
    private boolean isPooledSampleType(DataRecord rec) {
        try {
            Object sampleType = rec.getValue("ExemplarSampleType", user);
            Object altIds = rec.getStringVal("AltId", user);
            if (altIds!=null){
                return altIds.toString().split(",").length>1;
            }
            if (sampleType != null) {
                return "Pooled Library".equalsIgnoreCase(sampleType.toString().trim());
            }
        } catch (NotFound | RemoteException e) {
            error = String.format("Cannot validate SampleType:\n%s", e.getMessage());
        }
        return false;
    }

    /**
     * Method to check for special characters in SampleCMOInfoRecords saved Data and Duplicate CorrectedCMOID field
     * values. Special characters except underscore, hyphen and (comma only for Pooled Library Samples) are not allowed
     * in LIMS for certain DataType Fields.
     *
     * @param savedRecords
     * @param dataTypeName
     */
    private void validateCmoInfoDataTypeFields(List<DataRecord> savedRecords, String dataTypeName) {
        for (DataRecord rec : savedRecords) {
            try {
                Map<String, Object> fields = rec.getFields(user);
                for (String key : fields.keySet()) {
                    if (CMOINFO_FIELDS_TO_CHECK_FOR_SPECIAL_CHARACTERS.contains(key.trim())) {
                        Object fieldValue = rec.getValue(key, user);
                        if (fieldValue != null && !utils.hasValidCharacters(fieldValue.toString(), false)) {
                            error = String.format("Invalid Characters found in value '%s' on '%s' record Field '%s' " +
                                    "Special characters except '_' and '-' are not allowed for this Field", fieldValue.toString(), dataTypeName, key);
                            logError(error);
                        }
                    }
                }
                Object cmoId = rec.getValue("CorrectedCMOID", user);
                Object recordId = rec.getRecordId();
                if (isDuplicateCmoId(cmoId, recordId)) {
                    error = String.format("CorrectedCMOID '%s' already exists in '%s'. Choose a different value", cmoId, rec.getDataTypeName());
                    clientCallback.displayError(error);
                    logError(error);
                }
            } catch (RemoteException | NotFound | ServerException e) {
                error = String.format("Error while validating %s record %d for special characters:\n%s", rec.getDataTypeName(), rec.getRecordId(), e.getMessage());
                logError(error);
            }
        }
    }

    /**
     * Method to check Sample Field values for Special characters. Special characters except underscore, hyphen and
     * (comma only for Pooled Library Samples) are not allowed in LIMS for certain DataType Fields.
     * @param savedRecords
     * @param dataTypeName
     */
    private void validateSampleFields(List<DataRecord> savedRecords, String dataTypeName) {
        for (DataRecord rec : savedRecords) {
            try {
                boolean isPoolSample = isPooledSampleType(rec);
                Map<String, Object> fields = rec.getFields(user);
                for (String key : fields.keySet()) {
                    if (SAMPLE_FIELDS_TO_CHECK_FOR_SPECIAL_CHARACTERS.contains(key.trim())) {
                        Object fieldValue = rec.getValue(key, user);
                        if (fieldValue != null && !utils.hasValidCharacters(fieldValue.toString(), isPoolSample)) {
                            error = String.format("Invalid Characters found in value '%s' on '%s' record Field '%s' " +
                                    "Special characters except '_' '-' ',' (comma only for Pooled Library Samples) are not allowed for this Field", fieldValue.toString(), dataTypeName, key);
                            logError(error);
                        }
                    }
                }
            } catch (RemoteException | NotFound e) {
                error = String.format("Error while validating %s record %d for special characters:\n%s", rec.getDataTypeName(), rec.getRecordId(), e.getMessage());
                logError(error);
            }
        }
    }

    /**
     * Method to check if CorrectedCMOID value already exists in SampleCMOInfoRecords table.
     *
     * @param cmoId
     * @return
     */
    private boolean isDuplicateCmoId(Object cmoId, Object recordId) {
        try {
            List<DataRecord> cmoInfoRecords = dataRecordManager.queryDataRecords("SampleCMOInfoRecords", "CorrectedCMOID = '" + cmoId + "' AND RecordId != " + recordId, user);
            logInfo("Total CmoInfo records: " + cmoInfoRecords.size());
            return cmoId !=null && !StringUtils.isBlank(cmoId.toString()) && cmoInfoRecords.size() > 0;
        } catch (RemoteException | IoError | NotFound e) {
            e.printStackTrace();
        }
        return false;
    }
}
