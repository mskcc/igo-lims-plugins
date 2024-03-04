package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to assign samples to plate for ddPCR Assay using file upload.
 * User can choose to upload the file with sample to plate assignment information when prompted,
 * or user can cancel the prompt to manually assign samples to plate. This plugin will be useful when
 * user has many samples to run as duplicates using different assays across multiple plates. Drag and drop
 * method is not efficient for large sample group and can lead to manual errors.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */

public class DdPcrSampleToPlateAssigner extends DefaultGenericPlugin {

    private final List<String> DDPCR_PLATE_ASSIGNMENT_SHEET_EXPECTED_HEADERS = Arrays.asList("IGO ID", "Sample Name", "AltId", "Assay", "Well", "Plate ID");
    private final String TASK_OPTION = "UPLOAD_DDPCR_PLATE_ASSIGNMENT_SHEET";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public DdPcrSampleToPlateAssigner() {
        setTaskSubmit(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(TASK_OPTION) && activeTask.getStatus() != activeTask.COMPLETE;
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            String uploadedFile = clientCallback.showFileDialog("Upload Excel Sheet with ddPCR plate assignment values", null);
            if (StringUtils.isBlank(uploadedFile)) {
                return new PluginResult(true);
            }
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            String destinationDataTypeFields = activeTask.getTask().getTaskOptions().get(TASK_OPTION);
            if (StringUtils.isBlank(destinationDataTypeFields) || destinationDataTypeFields.split("|").length < 3) {
                clientCallback.displayError("Invalid Values for Task Option 'UPLOAD_DDPCR_PLATE_ASSIGNMENT_SHEET':\n" +
                        "Valid values should be 'AliquotProtocolRecordName | DestinationPlateIdFieldName | DestinationWellFieldName | ddPcrAssayFieldName' as in the attached dataType.");
                logError("Invalid Values for Task Option 'UPLOAD_DDPCR_PLATE_ASSIGNMENT_SHEET':\n" +
                        "Valid values should be 'AliquotProtocolRecordName | DestinationPlateIdFieldName | DestinationWellFieldName | ddPcrAssayFieldName' as in the attached dataType.");
            }
            if (StringUtils.isBlank(uploadedFile) || !isValidExcelFile(uploadedFile)) {
                return new PluginResult(false);
            }
            byte[] excelFileData = clientCallback.readBytes(uploadedFile);
            List<Row> fileData = utils.getExcelSheetDataRows(excelFileData);
            if (!fileHasData(fileData, uploadedFile) || !hasValidHeader(fileData, DDPCR_PLATE_ASSIGNMENT_SHEET_EXPECTED_HEADERS, uploadedFile)) {
                return new PluginResult(false);
            }
            logInfo("File validation passed.");
            if (attachedSamples.isEmpty()) {
                clientCallback.displayError("Could not find any Samples attached to this Task.");
                logError("Could not find any Samples attached to this Task.");
                return new PluginResult(false);
            }
            HashMap<String, Integer> headerValuesMap = utils.getHeaderValuesMapFromExcelRowData(fileData);
            List<Map<String, Object>> dataFieldValuesMap = getDataFieldValueMaps(fileData, headerValuesMap);
            String targetDataTypeName = activeTask.getTask().getTaskOptions().get(TASK_OPTION).split("\\|")[0].trim();
            setValuesOnDataRecord(attachedSamples, dataFieldValuesMap, targetDataTypeName);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while parsing the ddPCR Sample to plate assignment sheet:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidFormatException e) {
            String errMsg = String.format("InvalidFormat Exception Error while parsing the ddPCR Sample to plate assignment sheet:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IOException e) {
            String errMsg = String.format("IO Exception Error while parsing the ddPCR Sample to plate assignment sheet:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate excel file has valid header.
     *
     * @param fileName
     * @return true/false
     * @throws ServerException
     */
    private boolean isValidExcelFile(String fileName) throws ServerException, RemoteException {
        boolean isValid = utils.isValidExcelFile(fileName);
        if (!isValid) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a valid excel file", fileName));
            return false;
        }
        return true;
    }

    /**
     * Method to validate file has data.
     *
     * @param fileData
     * @param fileName
     * @return true/false
     * @throws ServerException
     */
    private boolean fileHasData(List<Row> fileData, String fileName) throws ServerException, RemoteException {
        boolean hasData = utils.excelFileHasData(fileData);
        if (!hasData) {
            clientCallback.displayError(String.format("Uploaded file '%s' is Empty", fileName));
            logError(String.format("Uploaded file '%s' is Empty", fileName));
            return false;
        }
        return true;
    }


    /**
     * Method to validate file has valid header values.
     *
     * @param dataRows
     * @param expectedHeaderValues
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean hasValidHeader(List<Row> dataRows, List<String> expectedHeaderValues, String fileName) throws ServerException, RemoteException {
        boolean isValidHeader = utils.excelFileHasValidHeader(dataRows, expectedHeaderValues);
        if (!isValidHeader) {
            clientCallback.displayError(String.format("Uploaded file '%s' does not have a valid header. Valid file Headers are\n'%s'", fileName, utils.convertListToString(expectedHeaderValues)));
            logError(String.format("Uploaded file '%s' does not have a valid header.Valid file Headers are\n'%s'", fileName, utils.convertListToString(expectedHeaderValues)));
            return false;
        }
        return true;
    }

    /**
     * Method to create Map of Fields and Values from row data.
     *
     * @param row
     * @param headerValueMap
     * @return Map of Fields and Values
     * @throws RemoteException
     */
    private Map<String, Object> getDataFieldsValueMapFromRowData(Row row, Map<String, Integer> headerValueMap) {
        Map<String, Object> dataFieldValueMap = new HashMap<>();
        List<String> dataTypeFieldNames = new ArrayList<>();
        try {
            dataTypeFieldNames = Arrays.asList(activeTask.getTask().getTaskOptions().get(TASK_OPTION).split("\\|"));
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error reading task option values for active task:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        logInfo("Tag values: " + dataTypeFieldNames.toString());
        String plateIdFieldName = dataTypeFieldNames.get(1).trim();
        String destinationWellFieldName = dataTypeFieldNames.get(2).trim();
        String ddPcrAssayFieldName = dataTypeFieldNames.get(3).trim(); // "TargetName"
        dataFieldValueMap.put("SampleId", row.getCell(headerValueMap.get("IGO ID")).getStringCellValue());
        dataFieldValueMap.put("OtherSampleId", row.getCell(headerValueMap.get("Sample Name")).getStringCellValue());
        if (row.getCell(headerValueMap.get("AltId")) != null)
        //&& row.getCell(headerValueMap.get("AltId")).getStringCellValue() != null)
        {
            dataFieldValueMap.put("AltId", row.getCell(headerValueMap.get("AltId")).getStringCellValue());
        }
        dataFieldValueMap.put(ddPcrAssayFieldName, row.getCell(headerValueMap.get("Assay")).getStringCellValue());
        dataFieldValueMap.put(destinationWellFieldName, row.getCell(headerValueMap.get("Well")).getStringCellValue());
        dataFieldValueMap.put(plateIdFieldName, row.getCell(headerValueMap.get("Plate ID")).getStringCellValue());
        logInfo(dataFieldValueMap.toString());
        return dataFieldValueMap;
    }

    /**
     * Method to generate Maps for DataField values for "dataType/Protocol" record attached to the active task.
     *
     * @param fileData
     * @param headerValueMap
     * @return List of Maps with DataField values.
     * @throws RemoteException
     */
    private List<Map<String, Object>> getDataFieldValueMaps(List<Row> fileData, Map<String, Integer> headerValueMap){
        List<Map<String, Object>> dataFieldValuesMaps = new ArrayList<>();
        for (Row row : fileData) {
            Map<String, Object> dataFieldValueMap = getDataFieldsValueMapFromRowData(row, headerValueMap);
            if (dataFieldValueMap.get("TargetName").toString().trim().equalsIgnoreCase("Mdm2_Mm_Neo")) {
                dataFieldValueMap.put("SignalCh1", "FAM");
            }
            if (dataFieldValueMap.get("TargetName").toString().trim().equalsIgnoreCase("mPTGER2")) {
                dataFieldValueMap.put("SignalCh2", "HEX");
            }
            dataFieldValuesMaps.add(dataFieldValueMap);
        }
        return dataFieldValuesMaps;
    }

    /**
     * Method to get RecordId values.
     * @param records
     * @return
     */
    private List<Long> getRecotdIds(List<DataRecord> records) {
        List<Long> recordIds = new ArrayList<>();
        for (DataRecord rec : records) {
            recordIds.add(rec.getRecordId());
        }
        return recordIds;
    }

    /**
     * Method to update the target DataRecord with values read from file, and attach updated records to active task.
     *
     * @param attachedSamples
     * @param dataFieldValuesMaps
     * @param targetDataTypeName
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private void setValuesOnDataRecord(List<DataRecord> attachedSamples, List<Map<String, Object>> dataFieldValuesMaps, String targetDataTypeName){
        logInfo("Adding records to " + targetDataTypeName);
        List<DataRecord> attachedProtocolRecords = new ArrayList<>();
        try {
            attachedProtocolRecords = activeTask.getAttachedDataRecords(targetDataTypeName, user);
            activeTask.removeTaskAttachments(getRecotdIds(attachedProtocolRecords));
            activeWorkflow.getNext(activeTask).removeTaskAttachments(getRecotdIds(attachedProtocolRecords));
            dataRecordManager.deleteDataRecords(attachedProtocolRecords, null, false, user);
            List<DataRecord> newDataRecords = new ArrayList<>();
            for (DataRecord sample : attachedSamples) {
                String sampleId = sample.getStringVal("SampleId", user);
                String otherSampleId = sample.getStringVal("OtherSampleId", user);
                for (Map<String, Object> map : dataFieldValuesMaps) {
                    logInfo("map plate id is: " + map.get("Aliq1TargetPlateId"));
                    if (map.get("SampleId").toString().equals(sampleId) && map.get("OtherSampleId").toString().equals(otherSampleId)) {
                        logInfo("sample.addChild(targetDataTypeName, map, user)");
                        newDataRecords.add(sample.addChild(targetDataTypeName, map, user));
                    }
                }
            }
            activeTask.addAttachedDataRecords(newDataRecords);
            activeWorkflow.getNext(activeTask).addAttachedDataRecords(newDataRecords);
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error setting DdPcr result values for active task:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error setting DdPcr result values for active task:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error setting DdPcr result values for active task:\n%s", ExceptionUtils.getStackTrace(e)));
        }
    }
}