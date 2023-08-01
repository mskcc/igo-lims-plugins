package com.velox.sloan.cmo.workflows.samplereceiving;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.directive.ActiveWorkflowDirective;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.api.workflow.Workflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.managers.TaskUtilManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

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
    public PluginResult run() throws ServerException, RemoteException{
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
        } catch (RemoteException e) {
            String errMsg = String.format("Error while retrieving promoted samples for request: %s. " +
                    "Cause: %s", iLabsRequestId, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IoError ioError) {
            String errMsg = String.format("IoError while retrieving promoted samples for request: %s. " +
                    "Cause: %s", iLabsRequestId, ExceptionUtils.getStackTrace(ioError));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (NotFound notFound) {
            String errMsg = String.format("NotFound Exception Error while retrieving promoted samples for request: %s. " +
                    "Cause: %s", iLabsRequestId, ExceptionUtils.getStackTrace(notFound));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true, new ActiveWorkflowDirective(activeWebFormReceivingWorkflow));
    }

    /**
     * Method to validate iLabsRequestId is associated to only one LimsRequestId if promoted.
     * @param limsRequestIdForPromotedSamples
     * @param promotedBankedSamples
     * @param iLabsRequestId
     * @return
     */
    private boolean isValidRequestIdForSamples(List<String> limsRequestIdForPromotedSamples, List<DataRecord> promotedBankedSamples, String iLabsRequestId) {

        try {
            if (limsRequestIdForPromotedSamples.size() > 1) {
                clientCallback.displayError(String.format("iLabs Request ID %s has more than one assigned requestId\n%s", iLabsRequestId,
                        convertListToString(getLimsRequestId(promotedBankedSamples))));
                return false;
            }
            if (limsRequestIdForPromotedSamples.isEmpty()) {
                clientCallback.displayError(String.format("LIMS RequestID NOT FOUND for this iLabs Request: %s", iLabsRequestId));
                return false;
            }
        } catch (Exception se) {
            String errMsg = String.format("InvalidValue Exception while validating Ilab Request ID %s:\n%s", iLabsRequestId, ExceptionUtils.getStackTrace(se));
            logError(errMsg);
        }
        return true;
    }

    /**
     * Method to prompt user to auto-launch Sample Receiving workflow in LIMS.
     * @param promotedBankedSamples
     * @param limsRequestIdForPromotedSamples
     * @return
     * @throws ServerException
     */
    private boolean shouldLaunchWorkflow(List<DataRecord> promotedBankedSamples, List<String> limsRequestIdForPromotedSamples) throws ServerException, RemoteException {
        clientCallback.displayInfo(String.format("LIMS Request ID for %d Promoted Samples:\n%s",
                promotedBankedSamples.size(), convertListToString(limsRequestIdForPromotedSamples)));
        String dialogBoxTitle = "Launch Sample Receiving Workflow";
        String dialogBoxMessage = "Do you want to launch the 'Webform Receiving Workflow' for this request?\n" +
                "Click 'OK' to launch or 'CANCEL' to exit.";
        return clientCallback.showOkCancelDialog(dialogBoxTitle, dialogBoxMessage);
    }

    /**
     * Method to get Sample ID's for Banked Samples.
     * @param promotedBankedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private String getBankedSamplesIds(List<DataRecord> promotedBankedSamples){
        List<String> cmoSampleIds = new ArrayList<>();
        for (DataRecord promotedBankedSample : promotedBankedSamples) {
            String cmoSampleId = null;
            try {
                cmoSampleId = promotedBankedSample.getStringVal("OtherSampleId", user);
            } catch (NotFound notFound) {
                logError(String.format("'OtherSampleId' field value not found for Banked Sample with Record Id %d:\n%s", promotedBankedSample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException while reading 'OtherSampleId' field value for Banked Sample with Record Id %d:\n%s", promotedBankedSample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
            logInfo(String.format("CMO Sample ID: %s", cmoSampleId));
            cmoSampleIds.add(cmoSampleId);
        }
        return StringUtils.join(cmoSampleIds, "\n");
    }

    /**
     * Method to get LIMS RequestId(s) related to Banked Samples.
     * @param promotedBankedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getLimsRequestId(List<DataRecord> promotedBankedSamples){
        List<String> limsRequestIds = new ArrayList<>();
        for (DataRecord promotedBankedSample : promotedBankedSamples) {
            String requestId = null;
            try {
                requestId = promotedBankedSample.getStringVal("RequestId", user);
            } catch (NotFound notFound) {
                logError(String.format("'RequestId' field value not found for Banked Sample with Record Id %d:\n%s", promotedBankedSample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException while reading 'RequestId' field value for Banked Sample with Record Id %d:\n%s", promotedBankedSample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
            limsRequestIds.add(requestId);
        }
        return limsRequestIds;
    }

    /**
     * Method to get child samples for Request.
     * @param requestId
     * @return
     */
    private List<DataRecord> getChildSamplesForRequest(String requestId){
        List<DataRecord> request;
        List<DataRecord> samples = new ArrayList<>();
        try {
            request = dataRecordManager.queryDataRecords("Request", "RequestId = '" + requestId + "'", user);
            if (!request.isEmpty() && request.size() == 1) {
                samples = Arrays.asList(request.get(0).getChildrenOfType("Sample", user));
            }
            if (samples.size() == 0) {
                clientCallback.displayError(String.format("No child samples were found attached to request: %s", requestId));
            }
        }catch (NotFound notFound) {
            logError(String.format("Cannot find child Samples for Request %s:\n%s", requestId, ExceptionUtils.getStackTrace(notFound)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting child Samples for Request %s:\n%s", requestId, ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            ioError.printStackTrace();
        } catch (ServerException e) {
            e.printStackTrace();
        }
        return samples;
    }

    /**
     * Method to get Banked Samples that are marked promoted from a given set of Banked Samples.
     * @param bankedSamples
     * @return
     */
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

    /**
     * Method to  get Banked Samples that are NOT marked promoted from a given set of Banked Samples.
     * @param bankedSamples
     * @return
     */
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

    /**
     * Method to get Webform Receiving workflow.
     * @param workflows
     * @return
     */
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

    /**
     * Method to get workflow task to attach samples to.
     * @param workflowTasks
     * @return
     * @throws ServerException
     * @throws RemoteException
     * @throws NotFound
     */
    private ActiveTask getWorkflowTaskToAttachSamples(List<ActiveTask> workflowTasks){
        ActiveTask workflowTask = null;
        try {
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
        }catch (RemoteException re) {
            logError(String.format("Error while getting workflow task to attach Samples:\n%s", ExceptionUtils.getStackTrace(re)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Error while getting workflow task to attach Samples:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting workflow task to attach Samples:\n%s", ExceptionUtils.getStackTrace(se)));
        }
        return workflowTask;
    }

    /**
     * Method to launch WebformReceiving workflow.
     * @param limsRequestIdForPromotedSamples
     * @return
     */
    private ActiveWorkflow launchWebformReceivingWorkflow(String limsRequestIdForPromotedSamples){
        try {
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
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> failed to launch Webform Receiving Workflow:\n%s", ExceptionUtils.getStackTrace(e) ));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> failed to launch Webform Receiving Workflow:\n%s", ExceptionUtils.getStackTrace(notFound) ));
        } catch (ServerException e) {
            logError(String.format("ServerException -> failed to launch Webform Receiving Workflow:\n%s", ExceptionUtils.getStackTrace(e) ));
        }
        return null;
    }

    /**
     * Method to convert ArrayList to String.
     * @param listWithValues
     * @return
     */
    private String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }
}