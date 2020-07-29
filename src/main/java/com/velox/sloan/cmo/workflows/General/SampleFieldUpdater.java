package com.velox.sloan.cmo.workflows.General;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This plugin is designed to copy the changes from a DataField on sample (DataFieldName provided by the user via input dialog) to it child records.
 * The fields on child DataRecords are updated only if the child DataType and DataRecords contain the given DataFieldName in the fields definition.
 * This plugin does not update 'OtherSampleId' for Pooled Samples as it is concatenation of several 'OtherSampleId's'.
 *
 * @author sharmaa1
 */
public class SampleFieldUpdater extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sapio Admin"};

    public SampleFieldUpdater() {
        setTableToolbar(true);
        setLine1Text("Update Sample Fields");
        setDescription("Updates certain fields across sample hierarchy from samples present in table and all its children.");
        setUserGroupList(permittedUsers);
    }

    @Override
    public boolean onTableToolbar() {
        return dataTypeName.equals("Sample");
    }

    public PluginResult run() throws ServerException, IoError, RemoteException {
        try {
            List<DataRecord> records = dataRecordList;
            String dataFieldToUpdate = clientCallback.showInputDialog("Please enter the DataFieldName to update for the samples in this table: eg: 'OtherSampleId'");
            if (StringUtils.isEmpty(dataFieldToUpdate)) {
                clientCallback.displayInfo("DataFieldName Value not provided. You need to provide a DataFieldName to update on child samples.");
                logInfo("DataFieldName Value not provided. You need to provide a DataFieldName to update on child samples.");
                return new PluginResult(false);
            }
            if (records.size() > 0) {
                if (records.get(0).getDataTypeName().equals("Sample")) {
                    updateFieldsOnDescendants(records, dataFieldToUpdate);
                    dataRecordManager.storeAndCommit(String.format("Updated '%s' values on all child records of '%b', where applicable.", dataFieldToUpdate, records), null, user);
                }
            }
            if (records.size() == 0) {
                clientCallback.displayInfo("Sample table in view has no records.");
                logInfo("Sample table in view has no records.");
                return new PluginResult(false);
            }
        } catch (Exception e) {
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate OtherSampleId field.
     *
     * @param otherSampleId
     * @param sampleType
     * @return Boolean
     * @throws ServerException
     */
    private boolean isValidOtherSampleId(Object otherSampleId, String sampleType) throws ServerException {
        String sampleName = String.valueOf(otherSampleId).toLowerCase();
        Pattern mrnpattern = Pattern.compile(".*\\d{9}.*"); // to match 9 digit string in sample name
        Matcher mrnMatcher = mrnpattern.matcher(sampleName);
        Pattern mrnpattern1 = Pattern.compile(".*[m][r][n]\\d{9}.*"); //to match 'mrn' followed by 9 digit string in sample name
        Matcher mrnpattern1Matcher = mrnpattern1.matcher(sampleName);
        if (mrnMatcher.matches() || mrnpattern1Matcher.matches()) {
            clientCallback.displayError(String.format("Sample name '%s' cannot contain characters and numbers matching a MRN.", String.valueOf(otherSampleId)));
            return false;
        }
        Pattern igoIdPattern = Pattern.compile("^[0-9]{5}[_][a-zA-Z0-9]{1,9}|[0-9]{5}[_][a-zA-Z]{1,9}[_][0-9]{1,1000000000}$");
        Matcher igoIdPatternMatcher = igoIdPattern.matcher(sampleName);
        if (igoIdPatternMatcher.matches()) {
            clientCallback.displayError(String.format("Sample name '%s' cannot contain IGO ID like strings: no 5 numbers_number or 5 numbers_letters_numbers etc.", String.valueOf(otherSampleId)));
            return false;
        }
        Pattern specialCharPattern = Pattern.compile("[^a-z0-9_-]", Pattern.CASE_INSENSITIVE);
        Matcher specialCharMatcher = specialCharPattern.matcher(sampleName);
        boolean specialCharPresent = specialCharMatcher.find();
        if (specialCharPresent && !"pooled library".equalsIgnoreCase(sampleType)) {
            clientCallback.displayError(String.format("Sample name '%s' cannot contain special characters except '_' or '-'", String.valueOf(otherSampleId)));
            return false;
        }
        Pattern specialCharPatternForPool = Pattern.compile("[^a-z0-9,_-]", Pattern.CASE_INSENSITIVE);
        Matcher specialCharMatcherForPool = specialCharPatternForPool.matcher(sampleName);
        boolean specialCharPresentPool = specialCharMatcherForPool.find();
        if ("pooled library".equalsIgnoreCase(sampleType) && specialCharPresentPool) {
            clientCallback.displayError(String.format("Sample name for pool '%s' cannot contain special characters except '_' or '-' or ','", String.valueOf(otherSampleId)));
            return false;
        }
        if (sampleName.contains("sample")
                || sampleName.contains("samples")
                || sampleName.contains("igo")
                || sampleName.length() < 3
        ) {
            clientCallback.displayError(String.format("Sample name '%s'  must be at least 3 characters long and cannot contain reserved kewords like 'Sample(s)', 'sample', 'sample(s)', 'igo' etc.", String.valueOf(otherSampleId)));
            return false;
        }
        return true;
    }


    /**
     * Method to get all the descendants on a DataRecord within a request.
     *
     * @param sample
     * @return List<DataRecord>
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     * @throws IoError
     */
    private List<DataRecord> getDescendantSamplesWithinRequest(DataRecord sample) throws RemoteException, NotFound, ServerException, IoError {
        List<DataRecord> descendantSamplesInRequest = new ArrayList<>();
        descendantSamplesInRequest.add(sample);
        Stack<DataRecord> sampleStack = new Stack<>();
        sampleStack.add(sample);
        while (!sampleStack.isEmpty()) {
            DataRecord nextSample = sampleStack.pop();
            if (nextSample.getChildrenOfType("Sample", user).length > 0) {
                for (DataRecord samp : nextSample.getChildrenOfType("Sample", user)) {
                    if (!"pooled library".equals(samp.getStringVal("ExemplarSampleType", user).toLowerCase())
                            && samp.getValue("RequestId", user) != null
                            && sample.getValue("RequestId", user) != null
                            && samp.getStringVal("RequestId", user).equals(sample.getStringVal("RequestId", user))) {
                        descendantSamplesInRequest.add(samp);
                        logInfo("desc in request: " + samp.getStringVal("SampleId", user));
                        sampleStack.push(samp);
                    }
                    if ("pooled library".equals(samp.getStringVal("ExemplarSampleType", user).toLowerCase())
                            && samp.getValue("RequestId", user) != null && sample.getValue("RequestId", user) != null) {
                        List<String> poolRequestIds = Arrays.asList(samp.getStringVal("RequestId", user).split(","));
                        String sampleRequestId = sample.getStringVal("RequestId", user);
                        if (poolRequestIds.contains(sampleRequestId)) {
                            descendantSamplesInRequest.add(samp);
                            logInfo("desc in request: " + samp.getStringVal("SampleId", user));
                            sampleStack.push(samp);
                        }
                    }
                }
            }
        }
        return descendantSamplesInRequest;
    }

    /**
     * Method to get the old value for a field on dataRecord. It is important to have old value when changing OtherSampleId field on Pooled Samples.
     *
     * @param sample
     * @param newValue
     * @param fieldName
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String getPrevousAssignedValueForOtherSampleId(DataRecord sample, String newValue, String fieldName) throws IoError, RemoteException, NotFound {
        DataRecord[] childSamples = sample.getChildrenOfType("Sample", user);
        if (childSamples.length > 0) {
            if (fieldName.equals("OtherSampleId") && childSamples[0].getValue("ExemplarSampleType", user) != null && !childSamples[0].getStringVal("ExemplarSampleType", user).equalsIgnoreCase("Pooled Library")) {
                if (childSamples[0].getValue("OtherSampleId", user) != null && !newValue.equalsIgnoreCase(childSamples[0].getStringVal("OtherSampleId", user))) {
                    return childSamples[0].getStringVal("OtherSampleId", user);
                }
            }
        }
        return newValue;
    }

    private String getNewSampleNameForPool(String poolSampleName, String oldSampleVal, String newSampleVal) {
        logInfo("old: " + oldSampleVal);
        logInfo("poolName: " + poolSampleName);
        logInfo("new: " + newSampleVal);
        logInfo("newPoolId: " + poolSampleName.replace(oldSampleVal, newSampleVal));
        return poolSampleName.replace(oldSampleVal, newSampleVal);
    }

    /**
     * Method to get all the Descendant DataType names for a sample.
     *
     * @param sample
     * @return Arraylist
     * @throws IoError
     * @throws RemoteException
     */
    private List<String> getDescendentDataTypeNames(DataRecord sample) throws IoError, RemoteException {
        DataRecordAttributes[] attributesList = sample.getChildAttributesList(user);
        List<String> descendentDataTypeNames = new ArrayList<>(); //Except Sample Descendant type.
        for (DataRecordAttributes att : attributesList) {
            if (!"Sample".equals(att.dataTypeName)) {
                descendentDataTypeNames.add(att.dataTypeName);
            }
        }
        logInfo(descendentDataTypeNames.toString());
        return new ArrayList<>(descendentDataTypeNames);
    }

    /**
     * ,
     * Update the samples that are not pools.
     *
     * @param descSamp
     * @param valueToOverWrite
     * @param fieldNameToUpdate
     * @throws IoError
     * @throws RemoteException
     * @throws InvalidValue
     * @throws NotFound
     */
    private void updateSampleFields(DataRecord descSamp, Object valueToOverWrite, String fieldNameToUpdate) throws IoError, RemoteException, InvalidValue, NotFound, ServerException {
        if ("OtherSampleId".equals(fieldNameToUpdate) && !isValidOtherSampleId(valueToOverWrite, descSamp.getStringVal("ExemplarSampleType", user))) {
            throw new InvalidValue(String.format("Invalid OtherSampleId '%s", String.valueOf(valueToOverWrite)));
        }
        List<String> childDataTypesExceptSample = getDescendentDataTypeNames(descSamp);
        descSamp.setDataField(fieldNameToUpdate, valueToOverWrite, user);
        for (String descendentType : childDataTypesExceptSample) {
            List<DataRecord> children = Arrays.asList(descSamp.getChildrenOfType(descendentType, user));
            clientCallback.displayInfo(" descendant type: " + descendentType);
            if (children.size() > 0) {
                for (DataRecord rec : children) {
                    Map<String, Object> fields = rec.getFields(user);
                    if (fields.containsKey(fieldNameToUpdate)) {
                        rec.setDataField(fieldNameToUpdate, valueToOverWrite, user);
                    }
                }
            }
        }
    }

    /**
     * Method to update fields on descendants of a DataRecord.
     *
     * @param samples
     * @param fieldNameToUpdate
     * @throws IoError
     * @throws RemoteException
     * @throws ServerException
     * @throws NotFound
     * @throws InvalidValue
     */

    private void updateFieldsOnDescendants(List<DataRecord> samples, String fieldNameToUpdate) throws IoError, RemoteException, ServerException, NotFound, InvalidValue {
        for (DataRecord sample : samples) {
            Object valueToOverwrie = sample.getStringVal(fieldNameToUpdate, user);
            String oldValue = getPrevousAssignedValueForOtherSampleId(sample, String.valueOf(valueToOverwrie), fieldNameToUpdate);
            List<DataRecord> descendantSamples = getDescendantSamplesWithinRequest(sample);
            for (DataRecord samp : descendantSamples) {
                if (fieldNameToUpdate.equalsIgnoreCase("OtherSampleId")
                        && samp.getValue("ExemplarSampleType", user) != null
                        && !"Pooled Library".equalsIgnoreCase(samp.getStringVal("ExemplarSampleType", user))) {
                    updateSampleFields(samp, valueToOverwrie, fieldNameToUpdate);
                } else if (fieldNameToUpdate.equalsIgnoreCase("OtherSampleId")
                        && samp.getValue("ExemplarSampleType", user) != null
                        && "Pooled Library".equalsIgnoreCase(samp.getStringVal("ExemplarSampleType", user))) {
                    updatePooledSampleFields(samp, valueToOverwrie, fieldNameToUpdate, oldValue);
                } else {
                    updateSampleFields(samp, valueToOverwrie, fieldNameToUpdate);
                }
            }
        }
    }

    /**
     * Method to update fields for pooled samples. It has to be handled differently because OtherSampleId is concatenation of same field values from all the samples that
     * are part of the pool. Other fields will be handled in the same way as
     *
     * @param descSamp
     * @param valueToOverWrite
     * @param fieldNameToUpdate
     * @param oldValueForSampleName
     * @throws IoError
     * @throws RemoteException
     * @throws InvalidValue
     * @throws NotFound
     * @throws ServerException
     */
    private void updatePooledSampleFields(DataRecord descSamp, Object valueToOverWrite, String fieldNameToUpdate, String oldValueForSampleName) throws IoError, RemoteException, InvalidValue, NotFound, ServerException {
        if ("OtherSampleId".equals(fieldNameToUpdate) && !isValidOtherSampleId(valueToOverWrite, descSamp.getStringVal("ExemplarSampleType", user))) {
            throw new InvalidValue(String.format("Invalid OtherSampleId '%s", String.valueOf(valueToOverWrite)));
        }
        List<String> childDataTypesExceptSample = getDescendentDataTypeNames(descSamp);
        String pooledSampleId = descSamp.getStringVal("OtherSampleId", user);
        String newPooledSampleId = getNewSampleNameForPool(pooledSampleId, oldValueForSampleName, String.valueOf(valueToOverWrite));
        logInfo(newPooledSampleId);
        descSamp.setDataField(fieldNameToUpdate, newPooledSampleId, user);
        for (String descendentType : childDataTypesExceptSample) {
            List<DataRecord> children = Arrays.asList(descSamp.getChildrenOfType(descendentType, user));
            if (children.size() > 0) {
                for (DataRecord rec : children) {
                    Map<String, Object> fields = rec.getFields(user);
                    if ("OtherSampleId".equals(fieldNameToUpdate) && fields.containsKey(fieldNameToUpdate)) {
                        //String newPooledSampleId = getNewSampleNameForPool(pooledSampleId, oldValueForSampleName, String.valueOf(valueToOverWrite));
                        rec.setDataField(fieldNameToUpdate, newPooledSampleId, user);
                    }
                    if (!"OtherSampleId".equals(fieldNameToUpdate) && fields.containsKey(fieldNameToUpdate)) {
                        rec.setDataField(fieldNameToUpdate, valueToOverWrite, user);
                    }
                }
            }
        }
    }
}
