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
 * @author sharmaa1
 */
public class SampleFieldUpdater extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sapio Admin"};
    public SampleFieldUpdater(){
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
            List<DataRecord> records =  dataRecordList;
            String dataFieldToUpdate = clientCallback.showInputDialog("Please enter the DataFieldName to update for the samples in this table: eg: 'OtherSampleId'");
            if (StringUtils.isEmpty(dataFieldToUpdate)){
                clientCallback.displayInfo("DataFieldName Value not provided. You need to provide a DataFieldName to update on child samples.");
                logInfo("DataFieldName Value not provided. You need to provide a DataFieldName to update on child samples.");
                return new PluginResult(false);
            }
            if (records.size() > 0 ){
                if (records.get(0).getDataTypeName().equals("Sample")){
                    updateFieldsOnDescendants(records, dataFieldToUpdate);
                    dataRecordManager.storeAndCommit(String.format("Updated '%s' values on all child records of '%b', where applicable.", dataFieldToUpdate, records), null, user);
                }
            }
            if (records.size()==0){
                clientCallback.displayInfo("Sample table in view has no records.");
                logInfo("Sample table in view has no records.");
                return new PluginResult(false);
            }
        }catch (Exception e){
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private boolean isValidOtherSampleId(Object otherSampleId) throws ServerException {
        String sampleName = String.valueOf(otherSampleId).toLowerCase();
        Pattern mrnpattern = Pattern.compile(".*\\d{9}.*"); // to match 9 digit string in sample name
        Matcher mrnMatcher = mrnpattern.matcher(sampleName);
        Pattern mrnpattern1 = Pattern.compile(".*[m][r][n]\\d{9}.*"); //to match 'mrn' followed by 9 digit string in sample name
        Matcher mrnpattern1Matcher = mrnpattern1.matcher(sampleName);
        if (mrnMatcher.matches() || mrnpattern1Matcher.matches()){
            clientCallback.displayError(String.format("Sample name '%s' cannot contain characters and numbers matching a MRN.", String.valueOf(otherSampleId)));
            return false;
        }
        Pattern igoIdPattern = Pattern.compile("^[0-9]{5}[_][a-zA-Z0-9]{1,9}|[0-9]{5}[_][a-zA-Z]{1,9}[_][0-9]{1,1000000000}$");
        Matcher igoIdPatternMatcher = igoIdPattern.matcher(sampleName);
        if (igoIdPatternMatcher.matches()){
            clientCallback.displayError(String.format("Sample name '%s' cannot contain IGO ID like strings: no 5 numbers_number or 5 numbers_letters_numbers etc.", String.valueOf(otherSampleId)));
            return false;
        }
        Pattern specialCharPattern = Pattern.compile("[^a-z0-9_-]", Pattern.CASE_INSENSITIVE);
        Matcher specialCharMatcher = specialCharPattern.matcher(sampleName);
        boolean specialCharPresent = specialCharMatcher.find();
        if(specialCharPresent){
            clientCallback.displayError(String.format("Sample name '%s' cannot contain special characters except '_' or '-'", String.valueOf(otherSampleId)));
            return false;
        }

        if (sampleName.contains("sample")
                || sampleName.contains("samples")
                || sampleName.contains("igo")
                || sampleName.length() < 3
        ){
            clientCallback.displayError(String.format("Sample name '%s'  must be at least 3 characters long and cannot contain reserved kewords like 'Sample(s)', 'sample', 'sample(s)', 'igo' etc.", String.valueOf(otherSampleId)));
            return false;
        }
        return true;
    }

    private void updateFieldsOnDescendants(List<DataRecord> samples, String fieldNameToUpdate) throws IoError, RemoteException, ServerException, NotFound, InvalidValue, org.omg.CORBA.DynAnyPackage.InvalidValue {
        for (DataRecord sample : samples) {
            Object valueToOverwrie = sample.getStringVal(fieldNameToUpdate, user);
            List<DataRecord> descendantSamples = getDescendantSamplesWithinRequest(sample);
            for (DataRecord samp : descendantSamples) {
                logInfo("samp: " + sample.getStringVal("SampleId", user));
                logInfo("descendant samp: " + samp.getStringVal("SampleId", user));
                updateNonPooledSamples(samp, valueToOverwrie, fieldNameToUpdate);
            }
        }
    }

    public List<DataRecord> getParentRequests(DataRecord sample) throws IoError, RemoteException, NotFound {
        List<DataRecord> requests = new ArrayList<>();
        if (sample.getStringVal("SampleId", user).toLowerCase().startsWith("pool-")){
            return requests;
        }
        if (sample.getParentsOfType("Request", user).size() > 0){
            return sample.getParentsOfType("Request", user);
        }
        Stack<DataRecord> sampleStack = new Stack<>();
        if (sample.getParentsOfType("Sample", user).size() > 0){
            sampleStack.push(sample.getParentsOfType("Sample", user).get(0));
        }
        while (!sampleStack.isEmpty()){
            DataRecord nextSample = sampleStack.pop();
            if (nextSample.getParentsOfType("Request", user).size() > 0){
                return nextSample.getParentsOfType("Request", user);
            }
            else if(nextSample.getParentsOfType("Sample", user).size() > 0){
                sampleStack.push(nextSample.getParentsOfType("Sample", user).get(0));
            }
        }
        return requests;
    }


    private List<DataRecord> getDescendantSamplesWithinRequest(DataRecord sample) throws RemoteException, NotFound, ServerException, IoError {
        List<DataRecord> descendantSamplesInRequest = new ArrayList<>();
        descendantSamplesInRequest.add(sample);
        List<DataRecord> allDescendantSamples = sample.getDescendantsOfType("Sample", user);
        Stack<DataRecord> sampleStack = new Stack<>();
        sampleStack.add(sample);
        while (!sampleStack.isEmpty()){
            DataRecord nextSample = sampleStack.pop();
            if (nextSample.getChildrenOfType("Sample", user).length > 0){
                for (DataRecord samp : nextSample.getChildrenOfType("Sample", user)){
                    if (!"pooled library".equals(samp.getStringVal("ExemplarSampleType", user).toLowerCase())
                            && samp.getValue("RequestId", user) != null
                            && sample.getValue("RequestId", user) != null
                            && samp.getStringVal("RequestId", user).equals(sample.getStringVal("RequestId", user))) {
                        descendantSamplesInRequest.add(samp);
                        logInfo("desc in request: " + samp.getStringVal("SampleId", user));
                        sampleStack.push(samp);
                    }
                }
            }
        }
        return descendantSamplesInRequest;
    }

    /**
     * Method to get all the Descendant DataType names for a sample.
     * @param sample
     * @return Arraylist
     * @throws IoError
     * @throws RemoteException
     */
    private List<String> getDescendentDataTypeNames(DataRecord sample) throws IoError, RemoteException {
        List<DataRecordAttributes> attributesList  = Arrays.asList(sample.getChildAttributesList(user));
        List<String> descendentDataTypeNames = new ArrayList<>(); //Except Sample Descendant type.
        for (DataRecordAttributes att : attributesList){
            if (!"Sample".equals(att.dataTypeName)) {
                descendentDataTypeNames.add(att.dataTypeName);
            }
        }
        logInfo(descendentDataTypeNames.toString());
        return new ArrayList<>(descendentDataTypeNames);
    }

    /**
     * Update the samples that are not pools.
     * @param descSamp
     * @param valueToOverWrite
     * @param fieldNameToUpdate
     * @throws IoError
     * @throws RemoteException
     * @throws InvalidValue
     * @throws NotFound
     */
    private void updateNonPooledSamples(DataRecord descSamp, Object valueToOverWrite, String fieldNameToUpdate) throws IoError, RemoteException, InvalidValue, NotFound, ServerException, org.omg.CORBA.DynAnyPackage.InvalidValue {
        if ("OtherSampleId".equals(fieldNameToUpdate) && !isValidOtherSampleId(valueToOverWrite)){
            throw new org.omg.CORBA.DynAnyPackage.InvalidValue(String.format("Invalid OtherSampleId '%s", String.valueOf(valueToOverWrite)));
        }
        List<String> childDataTypesExceptSample = getDescendentDataTypeNames(descSamp);
        descSamp.setDataField(fieldNameToUpdate, valueToOverWrite, user);
        for(String descendentType : childDataTypesExceptSample ) {
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
}
