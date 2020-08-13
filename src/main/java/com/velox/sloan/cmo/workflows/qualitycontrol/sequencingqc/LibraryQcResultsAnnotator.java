package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LibraryQcResultsAnnotator extends DefaultGenericPlugin {
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private List<String> expectedHeaderValues = Arrays.asList("FileName", "WellId", "Sample Description", "From [bp]",
            "To [bp]", "Average Size [bp]", "Conc. [ng/µl]", "Region Molarity [nmol/l]", "% of Total");
    public LibraryQcResultsAnnotator(){
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException, ServerException, NotFound {
        return activeTask.getTask().getTaskOptions().containsKey("ANNOTATE SAMPLE QC RESULTS");
    }

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords(SampleModel.DATA_TYPE_NAME, user);
            if (samples.isEmpty()){
                logError(String.format("No samples found attached to the task '%s'", activeTask.getTask().getTaskName() ));
                return new PluginResult(false);
            }
            List<String> files = clientCallback.showMultiFileDialog("Upload QC Files", "Please upload QC files.");
            if (files.isEmpty()){
                logError("Client cancelled file upload prompt/did not upload any files.");
                return new PluginResult(false);
            }
            if(!isValidFileType(files)){
                return new PluginResult(false);
            }
            List<String> fileData = utils.readDataFromFiles(files);
            if(!isValidsFileData(fileData)){
                return new PluginResult(false);
            }




        }catch (Exception e){
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }


    private boolean isValidFileType(List<String> files) throws ServerException {
        for (String file : files){
            if (!utils.isCsvFile(file)) {
                String errmsg = String.format("'%s' is not a valid CSV file.", file);
                clientCallback.displayError(errmsg);
                logError(errmsg);
                return false;
            }
        }
        return true;
    }
    /**
     * Method to validate if the file is
     *
     * @param tapeStationFileData
     * @return
     * @throws ServerException
     */
    public boolean isValidsFileData(List<String> tapeStationFileData) throws ServerException {
        try {
            if (!utils.csvFileHasValidHeader(tapeStationFileData, expectedHeaderValues)) {
                String errMsg = String.format("One of the uploaded file is missing valid header values. Expected header values are %s.", expectedHeaderValues);
                clientCallback.displayError(errMsg);
                logError(errMsg);
                return false;
            }
            if (!utils.csvFileHasData(tapeStationFileData)) {
                String errMsg = String.format("Uploaded file(s) do not have parsable data rows.");
                clientCallback.displayError(errMsg);
                logError(errMsg);
                return false;
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while parsing tapestation data from uploaded files .\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return false;
        }
        return true;

    }
}
