package com.velox.sloan.cmo.workflows.General;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.*;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import com.velox.sloan.cmo.workflows.recordmodels.DdPcrAssayResultsModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;


/**
 * A plugin that runs on task entry and updates the following:
 * - Checks/Validates that the declared Sample fields do not contain special characters.
 *
 * @author Ajay Sharma, Anna Patruno, Fahimeh Mirhaj
 */
public class ValidateSampleFieldsPlugin extends DefaultGenericPlugin {
    private final List<String> SAMPLE_FIELDS_TO_CHECK_FOR_SPECIAL_CHARACTERS = Arrays.asList("OtherSampleId", "UserSampleID", "TumorOrNormal", "PatientId", "CmoPatientId");
    private String error = null;

    public IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public ValidateSampleFieldsPlugin() {
        setTaskEntry(true);
    }
    public boolean shouldRun() throws RemoteException {
        return this.activeTask.getTask().getTaskOptions().containsKey("VALIDATE SAMPLE FIELDS")
                && !this.activeTask.getTask().getTaskOptions().containsKey("SAMPLE FIELDS VALIDATED");
    }
    public PluginResult run() throws ServerException, RemoteException {
        try {this.logInfo("Running Validate Sample Fields plugin");
            List<DataRecord> attachedSamples = this.activeTask.getAttachedDataRecords("Sample", this.user);

            if (attachedSamples.isEmpty()) {
                this.clientCallback.displayError("No sample is attached to this task.");
                return new PluginResult(false);
            }

            for(DataRecord dr: attachedSamples) {
                logInfo("Attached sample record's data type name is: " + dr.getDataTypeName() + " the record ID is:"
                        + dr.getValue("RecordId", user).toString());
            }

            for (DataRecord rec: attachedSamples){
                String dtTypeName = rec.getDataTypeName();
                logInfo("Saved DataRecord's DataType: " + dtTypeName);

                //validate fields for special characters on Sample Datatype
                if (dtTypeName.equalsIgnoreCase(SampleModel.DATA_TYPE_NAME)) {
                    logInfo("Checking for Special Characters in Sample Record with RecordId: " + rec.getRecordId());
                    validateSampleFields(rec, dtTypeName);
                }
            }
            this.activeTask.getTask().getTaskOptions().put("SAMPLE FIELDS VALIDATED", "");
        }
        catch (NotFound e) {
            String errMsg = String.format("NotFound Exception while saving data:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception while saving data:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(error == null, error == null ? new ArrayList<Object>() : Collections.singletonList(error));
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
            if (sampleType != null) {
                logInfo("Sample Type: " + sampleType.toString());
                return "Pooled Library".equalsIgnoreCase(sampleType.toString().trim());
            }
            if (altIds!=null){
                return altIds.toString().split(",").length>1;
            }
        } catch (NotFound | RemoteException e) {
            error = String.format("Cannot validate SampleType:\n%s", e.getMessage());
        }
        return false;
    }


    /**
     * Method to check Sample Field values for Special characters. Special characters except underscore, hyphen and
     * (comma only for Pooled Library Samples) are not allowed in LIMS for certain DataType Fields.
     * @param rec
     * @param dataTypeName
     */
    private void validateSampleFields(DataRecord rec, String dataTypeName) {
        try {
            boolean isPoolSample = isPooledSampleType(rec);
            logInfo("Is Pooled Sample: " + isPoolSample);
            Map<String, Object> fields = rec.getFields(user);
            for (String key : fields.keySet()) {
                if (SAMPLE_FIELDS_TO_CHECK_FOR_SPECIAL_CHARACTERS.contains(key.trim())) {
                    Object fieldValue = rec.getValue(key, user);

                    if (fieldValue != null && !utils.hasValidCharacters(fieldValue.toString(), isPoolSample, pluginLogger)) {
                        error = String.format("Invalid Characters found in value '%s' on '%s' record Field '%s' " +
                                "Special characters except '_' '-' ',' (comma only for Pooled Library Samples) are not allowed for this Field. " +
                                "Please check for whitespaces in between/beginning/trailing the entered field values or dropdown field values you are using.", fieldValue.toString(), dataTypeName, key);
                        logError(error);
                        try {
                            this.clientCallback.displayError(error);
                        } catch (ServerException se) {
                            String errMsg = String.format("Server Exception while saving data:\n%s", ExceptionUtils.getStackTrace(se));
                            logError(errMsg);
                        }
                    }
                }
            }
        } catch (RemoteException | NotFound e) {
            error = String.format("Error while validating %s record %d for special characters:\n%s", rec.getDataTypeName(), rec.getRecordId(), e.getMessage());
            logError(error);
        }
    }
}
