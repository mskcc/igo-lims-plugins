package com.velox.sloan.cmo.workflows.General;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


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
        return new PluginResult(error == null, error == null ? new ArrayList<Object>() : Arrays.asList((Object)error));
    }
}
