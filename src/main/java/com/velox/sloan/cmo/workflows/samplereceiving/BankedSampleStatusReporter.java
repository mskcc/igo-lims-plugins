package com.velox.sloan.cmo.workflows.samplereceiving;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginDirective;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.api.workflow.Workflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.managers.TaskUtilManager;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
 * @description : this plugin is designed to check the status 'Promoted' of Banked Samples using iLabs request ID.
 *              If the request is promoted, the plugin will return the list of samples in the request and prompt user to launch the
 *              "Webform receiving" workflow with promoted samples attached to the task "Select Received Samples" in the workflow.
 */
public class BankedSampleStatusReporter extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin"};

    public BankedSampleStatusReporter() {
        setActionMenu(true);
        setLine1Text("Banked Sample");
        setLine2Text("Status Check");
        setDescription("Check the status of the banked samples using iLabs request ID");
        setUserGroupList(permittedUsers);
    }

    @Override
    public PluginResult run() throws ServerException {
        String iLabsRequestId = clientCallback.showInputDialog("Please enter iLabs Request ID.");
        ActiveWorkflow activeWebFormReceivingWorkflow;
        try {
            if (StringUtils.isEmpty(iLabsRequestId)) {
                return new PluginResult(false);
            }
            List<DataRecord> bankedSamples = dataRecordManager.queryDataRecords("BankedSample", "ServiceId = '" + iLabsRequestId + "'", user);
            String samplesNotFound = String.format("No Banked Samples found for the entered request ID '%s'.\n" +
                    "Please make sure you have correct iLabs request ID.", iLabsRequestId);
            if (bankedSamples == null || bankedSamples.size() == 0) {
                clientCallback.displayWarning(samplesNotFound);
                logInfo(samplesNotFound);
                return new PluginResult(false);
            }
            List<DataRecord> promotedBankedSamples = getPromotedBankedSamples(bankedSamples);
            List<DataRecord> notPromotedBankedSamples = getNonPromotedBankedSamples(bankedSamples);
            if (promotedBankedSamples.isEmpty()) {
                clientCallback.displayWarning(String.format("None of the Samples associated with Request ID '%s' have been promoted.", iLabsRequestId));
                return new PluginResult(false);
            }
            if (!notPromotedBankedSamples.isEmpty()) {
                clientCallback.displayWarning("Found " + notPromotedBankedSamples.size() + " for this Request that are not promoted " + getBankedSamplesIds(notPromotedBankedSamples));
            }
            List<String> limsRequestIdForPromotedSamples = getLimsRequestId(promotedBankedSamples).stream().distinct().collect(Collectors.toList());
            if (isValidRequestIdForSamples(limsRequestIdForPromotedSamples, promotedBankedSamples, iLabsRequestId)
                    && shouldLaunchWorkflow(promotedBankedSamples, limsRequestIdForPromotedSamples)) {
                activeWebFormReceivingWorkflow = launchWebformReceivingWorkflow(limsRequestIdForPromotedSamples.get(0));
            } else {
                return new PluginResult(false);
            }
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while retrieving promoted samples for request: %s. " +
                    "Cause: %s", iLabsRequestId, e));
            logError(e);
            return new PluginResult(false);
        }
        return new PluginResult(true, new PluginDirective(PluginDirective.Action.RUN_ACTIVE_WORKFLOW, activeWebFormReceivingWorkflow));
    }

    private boolean isValidRequestIdForSamples(List<String> limsRequestIdForPromotedSamples, List<DataRecord> promotedBankedSamples, String iLabsRequestId) throws NotFound, RemoteException, ServerException {
        if (limsRequestIdForPromotedSamples.size() > 1) {
            clientCallback.displayError(String.format("iLabs Request ID %s has more than one assigned requestId\n%s", iLabsRequestId,
                    convertListToString(getLimsRequestId(promotedBankedSamples))));
            return false;
        }
        if (limsRequestIdForPromotedSamples.isEmpty()) {
            clientCallback.displayError(String.format("LIMS RequestID NOT FOUND for this iLabs Request: %s", iLabsRequestId));
            return false;
        }
        return true;
    }

    private boolean shouldLaunchWorkflow(List<DataRecord> promotedBankedSamples, List<String> limsRequestIdForPromotedSamples) throws ServerException {
        clientCallback.displayInfo(String.format("LIMS Request ID for %d Promoted Samples:\n%s",
                promotedBankedSamples.size(), convertListToString(limsRequestIdForPromotedSamples)));
        String dialogBoxTitle = "Launch Sample Receiving Workflow";
        String dialogBoxMessage = "Do you want to launch the 'Webform Receiving Workflow' for this request?\n" +
                "Click 'OK' to launch or 'CANCEL' to exit.";
        return clientCallback.showOkCancelDialog(dialogBoxTitle, dialogBoxMessage);
    }

    private String getBankedSamplesIds(List<DataRecord> promotedBankedSamples) throws NotFound, RemoteException {
        List<String> cmoSampleIds = new ArrayList<>();
        for (DataRecord promotedBankedSample : promotedBankedSamples) {
            String cmoSampleId = promotedBankedSample.getStringVal("OtherSampleId", user);
            logInfo(String.format("CMO Sample ID: %s", cmoSampleId));
            cmoSampleIds.add(cmoSampleId);
        }
        return StringUtils.join(cmoSampleIds, "\n");
    }

    private List<String> getLimsRequestId(List<DataRecord> promotedBankedSamples) throws NotFound, RemoteException {
        List<String> limsRequestIds = new ArrayList<>();
        for (DataRecord promotedBankedSample : promotedBankedSamples) {
            String requestId = promotedBankedSample.getStringVal("RequestId", user);
            limsRequestIds.add(requestId);
        }
        return limsRequestIds;
    }

    private List<DataRecord> getChildSamplesForRequest(String requestId) throws IoError, RemoteException, NotFound, ServerException {
        List<DataRecord> request;
        List<DataRecord> samples = new ArrayList<>();
        request = dataRecordManager.queryDataRecords("Request", "RequestId = '" + requestId + "'", user);
        if (!request.isEmpty() && request.size() == 1) {
            samples = Arrays.asList(request.get(0).getChildrenOfType("Sample", user));
        }
        if (samples.size() == 0) {
            clientCallback.displayError(String.format("No child samples were found attached to request: %s", requestId));
        }
        return samples;
    }

    private List<DataRecord> getPromotedBankedSamples(List<DataRecord> bankedSamples) {
        return bankedSamples.stream().filter(sample -> {
            try {
                return sample.getBooleanVal("Promoted", user);
            } catch (Exception e) {
                logError(e.getMessage());
                return false;
            }
        }).collect(Collectors.toList());
    }

    private List<DataRecord> getNonPromotedBankedSamples(List<DataRecord> bankedSamples) {
        return bankedSamples.stream().filter(sample -> {
            try {
                return !sample.getBooleanVal("Promoted", user);
            } catch (Exception e) {
                logError(e.getMessage());
                return false;
            }
        }).collect(Collectors.toList());
    }

    private List<Workflow> getWebformReceivingWorkflow(List<Workflow> workflows) {
        return workflows.stream().filter(workflow -> {
            try {
                return workflow.getWorkflowName().equals("Webform Receiving");
            } catch (Exception e) {
                logError(e.getMessage());
                return false;
            }
        }).collect(Collectors.toList());
    }

    private ActiveTask getWorkflowTaskToAttachSamples(List<ActiveTask> workflowTasks) throws ServerException, RemoteException, NotFound {
        ActiveTask workflowTask = null;
        for (ActiveTask task : workflowTasks) {
            if (task.getTaskName().equalsIgnoreCase("Select Received Samples")) {
                workflowTask = task;
                logInfo("Task to attach samples: " + workflowTask.getTaskName());
                break;
            }
        }
        if (workflowTask == null) {
            clientCallback.displayError("'Select Received Samples' task not found in 'Webform Receiving' workflow.\n" +
                    "Please make sure that the task names for this workflow did not change.");
            throw new NotFound("Select Received Samples not found.");
        }
        return workflowTask;
    }

    private ActiveWorkflow launchWebformReceivingWorkflow(String limsRequestIdForPromotedSamples) throws ServerException, RemoteException, IoError, NotFound {
        List<Workflow> workflows = dataMgmtServer.getWorkflowManager(user).getLatestWorkflowList(user);
        List<Workflow> webformReceivingWorkflow = getWebformReceivingWorkflow(workflows);
        if (webformReceivingWorkflow.isEmpty()) {
            clientCallback.displayError("'Webform Receiving' workflow not found. Please check if the workflow name has changed.");
            throw new NotFound("'Webform Receiving' workflow not found.");
        }
        ActiveWorkflow activeWebFormReceivingWorkflow = dataMgmtServer.getWorkflowManager(user).createActiveWorkflow(user, webformReceivingWorkflow.get(0));
        activeWebFormReceivingWorkflow.setActiveWorkflowName(activeWebFormReceivingWorkflow.getActiveWorkflowName());
        List<ActiveTask> workflowTasks = activeWebFormReceivingWorkflow.getActiveTaskList();
        ActiveTask taskToAttachSamples = getWorkflowTaskToAttachSamples(workflowTasks);
        List<DataRecord> childSamplesForRequest = getChildSamplesForRequest(limsRequestIdForPromotedSamples);
        TaskUtilManager.attachRecordsToTask(taskToAttachSamples, childSamplesForRequest);
        activeWebFormReceivingWorkflow.commitChanges(user);
        return activeWebFormReceivingWorkflow;
    }

    private String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }
}