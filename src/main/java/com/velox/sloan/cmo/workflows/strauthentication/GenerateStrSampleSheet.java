package com.velox.sloan.cmo.workflows.strauthentication;

import com.velox.api.datafielddefinition.DataFieldDefinition;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.utilities.CsvHelper;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.Tags;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;


import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * This plugin is designed to generate a sample-sheet with sample information for STR instrument to process STR samples.
 * @author sharmaa1
 */
public class GenerateStrSampleSheet extends DefaultGenericPlugin {

    private String strWellIdFieldName = "";
    private String strApplicationTypeFieldName = "";
    private String strSampleTypeFieldName = "";
    private String strSizeStandardFieldName = "";
    private String strDyeSetFieldName = "";
    private String strRunModuleFieldName = "";

    private final String STR_SAMPLESHEET_PUGIN_TAG = "GENERATE STR SAMPLE SHEET";

    public GenerateStrSampleSheet() {
        setTaskSubmit(true);
        setTaskToolbar(true);
        setLine1Text("Generate");
        setLine2Text("STR Sample-sheet");
        setIcon("com/velox/sloan/cmo/resources/import_32.gif");
        setOrder(PluginOrder.LATE.getOrder()-1);
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(STR_SAMPLESHEET_PUGIN_TAG);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(STR_SAMPLESHEET_PUGIN_TAG) &&
                    activeTask.getStatus() == ActiveTask.COMPLETE;
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while setting Task Toolbar button for plugin GenerateStrSampleSheet.\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    @Override
    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user);
            if (attachedProtocolRecords.isEmpty()) {
                clientCallback.displayError(String.format("%s protocol records are not attached to the task.", activeTask.getInputDataTypeName()));
                logError(String.format("%s protocol records are not attached to the task.", activeTask.getInputDataTypeName()));
                return new PluginResult(false);
            }
            updateFieldNames();
            String plateId = activeTask.getAttachedDataRecords("Sample", user).get(0).getStringVal("RelatedRecord23", user);
            List<List<String>> sampleSheetData = getStrSampleSheetData(attachedProtocolRecords, plateId);
            generateStrSampleSheet(sampleSheetData, plateId);
        } catch (IOException e) {
            String errMsg = String.format("IOException -> Error occured while running STR Sample sheet generation plugin:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (NotFound notFound) {
            String errMsg = String.format("NotFound Exception -> Error occured while running STR Sample sheet generation plugin:\n%s", ExceptionUtils.getStackTrace(notFound));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to retrieve LIMS ColumnNames holding the values for sample-sheet columns and update class variable.
     * @throws RemoteException
     * @throws ServerException
     */
    private void updateFieldNames() throws ServerException{
        List<DataFieldDefinition> dataFieldDefinitions = null;
        try {
            dataFieldDefinitions = dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions(activeTask.getInputDataTypeName()).getDataFieldDefinitionList();
        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException -> Error occured while getting DataFieldDefinitions:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
        }
        assert dataFieldDefinitions != null;
        for (DataFieldDefinition dfd : dataFieldDefinitions) {
            String tag = dfd.tag;
            if (tag.matches(Tags.STR_SAMPLESHEET_WELL_ID)) {
                strWellIdFieldName = dfd.dataFieldName;
            }
            if (tag.matches(Tags.STR_SAMPLESHEET_APPLICATION_TYPE)) {
                strApplicationTypeFieldName = dfd.dataFieldName;
            }
            if (tag.matches(Tags.STR_SAMPLESHEET_DYE_SET)) {
                strDyeSetFieldName = dfd.dataFieldName;
            }
            if (tag.matches(Tags.STR_SAMPLESHEET_RUN_MODULE)) {
                strRunModuleFieldName = dfd.dataFieldName;
            }
            if (tag.matches(Tags.STR_SAMPLESHEET_SAMPLE_TYPE)) {
                strSampleTypeFieldName = dfd.dataFieldName;
            }
            if (tag.matches(Tags.STR_SAMPLESHEET_SIZE_STANDARD)) {
                strSizeStandardFieldName = dfd.dataFieldName;
            }
        }
        try {
            if (StringUtils.isBlank(strWellIdFieldName)) {
                clientCallback.displayError(String.format("Missing tag '<!-- STR SAMPLESHEET WELL ID-->' %s", activeTask.getInputDataTypeName()));
            }
            if (StringUtils.isBlank(strApplicationTypeFieldName)) {
                clientCallback.displayError(String.format("Missing tag '<!-- STR SAMPLESHEET APPLICATION TYPE-->' on %s", activeTask.getInputDataTypeName()));
            }
            if (StringUtils.isBlank(strDyeSetFieldName)) {
                clientCallback.displayError(String.format("Missing tag '<!-- STR SAMPLESHEET DYE SET-->' on %s", activeTask.getInputDataTypeName()));
            }
            if (StringUtils.isBlank(strRunModuleFieldName)) {
                clientCallback.displayError(String.format("Missing tag '<!-- STR SAMPLESHEET RUN MODULE-->' on %s", activeTask.getInputDataTypeName()));
            }
            if (StringUtils.isBlank(strSampleTypeFieldName)) {
                clientCallback.displayError(String.format("Missing tag '<!-- STR SAMPLESHEET SAMPLE TYPE-->' on %s", activeTask.getInputDataTypeName()));
            }
            if (StringUtils.isBlank(strSizeStandardFieldName)) {
                clientCallback.displayError(String.format("Missing tag '<!-- STR SAMPLESHEET SIZE STANDARD-->' on %s", activeTask.getInputDataTypeName()));
            }
        }catch (RemoteException e) {
            String errMsg = String.format("RemoteException -> Error occured while getting Input DataType name:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
        }
    }

    /**
     * Method to generate sample-sheet row data for STR Samples.
     * @param protocolRecords
     * @param plateId
     * @return List<List<String>>
     * @throws ServerException
     * @throws IOException
     * @throws NotFound
     */
    private List<List<String>> getStrSampleSheetData(List<DataRecord> protocolRecords, String plateId) {
        List <List<String>> csvData = new ArrayList<>();
        List<String> headerRow1 = Arrays.asList("#Version", "1");
        List<String> headerRow2 = Arrays.asList("#Plate Name", "#Plate Application Type", "#Barcode",	"#Owner", "#SangerAnalysisSoftware");
        List<String> headerRow3 = Arrays.asList(plateId, "Fragment Analysis","","","FALSE");
        List<String> headerRow4 = Arrays.asList("#Well ID", "#Sample Name", "#Application Type", "#Sample Type", "#Size Standard", "#Dye set", "#Run module");
        csvData.add(headerRow1);
        csvData.add(headerRow2);
        csvData.add(headerRow3);
        csvData.add(headerRow4);
        for(DataRecord rec : protocolRecords){
            List<String> row = new ArrayList<>();
            try {
                row.add(rec.getStringVal(strWellIdFieldName, user));
                row.add(rec.getStringVal("OtherSampleId", user));
                row.add(rec.getStringVal(strApplicationTypeFieldName, user));
                row.add(rec.getStringVal(strSampleTypeFieldName, user));
                row.add(rec.getStringVal(strSizeStandardFieldName, user));
                row.add(rec.getStringVal(strDyeSetFieldName, user));
                row.add(rec.getStringVal(strRunModuleFieldName, user));
                csvData.add(row);
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error occured while getting STR Samplesheet data for '%s' with recordid %d:\n%s", rec.getDataTypeName(), rec.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error occured while getting STR Samplesheet data for '%s' with recordid %d:\n%s", rec.getDataTypeName(), rec.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
        }
        return csvData;
    }

    /**
     * Method to write sample-sheet to a csv file.
     * @param csvData
     * @param plateId
     * @throws IOException
     * @throws ServerException
     */
    private void generateStrSampleSheet(List<List<String>>csvData, String plateId) {
        String fileName = StringUtils.join(plateId,".csv");
        try {
            byte[] sampleSheetBytes = CsvHelper.writeCSV(csvData, null);
            clientCallback.writeBytes(sampleSheetBytes, fileName);
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error occured while generating STR Samplesheet:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (IOException e) {
            logError(String.format("IOException -> Error occured while generating STR Samplesheet:\n%s", ExceptionUtils.getStackTrace(e)));
        }
    }
}
