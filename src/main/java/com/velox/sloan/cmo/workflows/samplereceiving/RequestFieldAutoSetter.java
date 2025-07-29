package com.velox.sloan.cmo.workflows.samplereceiving;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

public class RequestFieldAutoSetter extends DefaultGenericPlugin {
    private static final String[] NEEDS_EXTRACTION_FALSE = {
            "ATACSeq", "DNALibraryPrep", "QualityControl", "SAILcDNA", "SingleCell", "UserLibrary"
    };
    private static final String[] NEEDS_EXTRACTION_TRUE = {
            "Extraction", "PEDPEG"
    };
    private static final String[] SEQUENCER_TYPE_ILLUMINA = {
            "ATACSeq", "DNALibraryPrep", "HybridCapture", "MethylSeq", "PEDPEG", "RNALibraryPrep", "SAILcDNA",
            "SingleCell", "TCRSeq", "UserLibrary", "WholeExome", "WholeGenome"
    };
    private static final String[] SEQUENCER_TYPE_ONT = {"Nanopore"};
    private static final String[] SEQUENCER_TYPE_NA = {"ddPCR", "Extraction", "QualityControl"};

    //private String[] permittedUsers = {"Sample Receiving", "Sapio Admin"};

    public RequestFieldAutoSetter() {
        setTaskEntry(true);
        //setUserGroupList(permittedUsers);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException, ServerException, NotFound {
        return activeTask.getTask().getTaskOptions().containsKey("SET REQUEST FIELDS AUTOMATICALLY")
                && activeTask.getStatus() != ActiveTask.COMPLETE;
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> attachedRequests = activeTask.getAttachedDataRecords("Request", user);
            if (attachedRequests.isEmpty()) {
                clientCallback.displayError("No requests attached to this task.");
                return new PluginResult(false);
            }
            for (DataRecord request : attachedRequests) {
                // Use the actual request name field (assume "Name" is the request name field)
                String requestName = null;
                try {
                    Object nameObj = request.getValue("RequestName", user);
                    if (nameObj != null && !StringUtils.isBlank(nameObj.toString())) {
                        requestName = nameObj.toString();
                    }
                } catch (Exception e) {
                    logError("Error getting request name: " + ExceptionUtils.getStackTrace(e));
                }
                if (StringUtils.isBlank(requestName)) {
                    logError("Request name is missing for Request recordId: " + request.getRecordId());
                    continue;
                }
                // Set NeedsExtraction
                Boolean needsExtraction = null;
                if (matchesAny(requestName, NEEDS_EXTRACTION_FALSE) || isDownstreamRequest(requestName)) {
                    needsExtraction = false;
                } else if (matchesAny(requestName, NEEDS_EXTRACTION_TRUE)) {
                    needsExtraction = true;
                }
                if (needsExtraction != null) {
                    request.setDataField("NeedExtraction", needsExtraction, user);
                }
                // Set SequencerType
                String sequencerType = null;
                if (matchesAny(requestName, SEQUENCER_TYPE_ILLUMINA)) {
                    sequencerType = "Illumina";
                } else if (matchesAny(requestName, SEQUENCER_TYPE_ONT)) {
                    sequencerType = "ONT";
                } else if (matchesAny(requestName, SEQUENCER_TYPE_NA)) {
                    sequencerType = "N/A";
                }
                if (sequencerType != null) {
                    request.setDataField("SequencerType", sequencerType, user);
                }
                dataRecordManager.storeAndCommit("Set NeedExtraction and SequencerType for RequestId: " + getRequestId(request), null, user);
            }
        } catch (Exception e) {
            String errMsg = "Exception while setting Request fields: " + ExceptionUtils.getStackTrace(e);
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private boolean matchesAny(String value, String[] options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    // Placeholder for downstream request logic
    private boolean isDownstreamRequest(String requestName) {
        // Implement logic if you have a way to identify downstream requests by name or other field
        return requestName.toLowerCase().contains("downstream");
    }

    private String getRequestName(DataRecord request) {
        try {
            // Try Recipe, then Application, then RequestType
            Object recipe = request.getValue("Recipe", user);
            if (recipe != null && !StringUtils.isBlank(recipe.toString())) {
                return recipe.toString();
            }
            Object application = request.getValue("Application", user);
            if (application != null && !StringUtils.isBlank(application.toString())) {
                return application.toString();
            }
            Object requestType = request.getValue("RequestType", user);
            if (requestType != null && !StringUtils.isBlank(requestType.toString())) {
                return requestType.toString();
            }
        } catch (Exception e) {
            logError("Error getting request name: " + ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    private String getRequestId(DataRecord request) {
        try {
            Object requestId = request.getValue("RequestId", user);
            if (requestId != null) {
                return requestId.toString();
            }
        } catch (Exception e) {
            logError("Error getting RequestId: " + ExceptionUtils.getStackTrace(e));
        }
        return "";
    }
} 