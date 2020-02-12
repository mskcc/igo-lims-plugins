package com.velox.sloan.cmo.workflows.General;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;


/**
 * A plugin that runs on save and updates the dateModified if changes are made to records within the request data type or any specified data type.
 * The information is important for Splunk indexing and change monitoring.
 *
 * @author Ajay Sharma, Anna Patruno
 */


// declaration of class
public class IgoOnSavePlugin extends DefaultGenericPlugin {
    private List<String> trackableDataTypes = Arrays.asList("Request");

    // define constructor, what structure of class/object would look like
    public IgoOnSavePlugin() {
        setOnSave(true);
    }

    // defining the method
    public PluginResult run() throws ServerException {
        String error = null;
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

            //set Human Percentage on QcReportDna DataRecords when Saving DdPcrAssayResults and HumanPercentageValues are present
            if(theSavedRecords.get(0).getDataTypeName().equalsIgnoreCase("DdPcrAssayResults")){
                mapHumanPercentageFromDdpcrResultsToDnaQcReport(theSavedRecords);
            }

            // if there's an error
        } catch (Exception e) {
            // display the error to the user
            clientCallback.displayError(e.toString());
            // log the error
            logError(e.toString());
            error = e.getMessage();
            return new PluginResult();
        }
        // if there's no error
        return new PluginResult(error == null, error == null ? new ArrayList<Object>() : Arrays.asList(error));
    }

    private List<DataRecord> getQcReportRecords(DataRecord sample, String requestId) throws IoError, RemoteException, NotFound {
        if (sample.getChildrenOfType("QcReportDna", user).length>0){
            return Arrays.asList(sample.getChildrenOfType("QcReportDna", user));
        }
        List<DataRecord> qcReports = new ArrayList<>();
        Stack<DataRecord> sampleStack = new Stack();
        sampleStack.add(sample);
        while (sampleStack.size()>0){
            DataRecord nextSample = sampleStack.pop();
            if (requestId.equalsIgnoreCase(nextSample.getStringVal("RequestId",user)) && nextSample.getChildrenOfType("QcReportDna", user).length > 0){
                return Arrays.asList(nextSample.getChildrenOfType("QcReportDna", user));
            }
            List<DataRecord> parentSamples = nextSample.getParentsOfType("Sample", user);
            if (parentSamples.size()>0 && parentSamples.get(0).getValue("RequestId", user)!= null
                    && parentSamples.get(0).getStringVal("RequestId", user).equals(requestId)){
                sampleStack.addAll(parentSamples);
            }
        }
        return qcReports;
    }

    private void mapHumanPercentageFromDdpcrResultsToDnaQcReport(List<DataRecord> savedRecords) throws IoError, RemoteException, NotFound, InvalidValue, ServerException {
        for (DataRecord rec : savedRecords){
            if (rec.getDataTypeName().equalsIgnoreCase("DdPcrAssayResults") && rec.getValue("HumanPercentage", user) != null){
                List<DataRecord> parentSamples = rec.getParentsOfType("Sample", user);
                Double humanPercentage = rec.getDoubleVal("HumanPercentage", user);
                if (parentSamples.size()> 0){
                    DataRecord parentSample = parentSamples.get(0);
                    String requestId = parentSample.getStringVal("RequestId", user);
                    List<DataRecord> qcReports = getQcReportRecords(parentSample, requestId);
                    for (DataRecord qr : qcReports){
                        qr.setDataField("HumanPercentage", humanPercentage, user);
                    }
                }
            }
        }
    }
}
