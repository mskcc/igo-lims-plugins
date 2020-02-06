package com.velox.sloan.cmo.workflows.workflows;

import com.velox.api.datafielddefinition.DataFieldDefinition;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AliquotingTags;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;


/**
 * This plugin is designed to assign samples to plate using file upload. The plugin reads the Destination Plate ID, Destination Well, Destination Vol and other Aliquoting values from file and assigns
 * these values to Aliquoting/Plate assignment DataType. The plugin is meant to work for most of the Sample to Plate assignment tasks including Pooling.
 *
 * @author sharmaa1
 */
public class SampleToPlateAssignmentViaFileUplaod extends DefaultGenericPlugin {
    private enum acceptableHeaderValuesEnum {SAMPLE_ID, SAMPLE_NAME, SOURCE_MASS_TO_USE, SOURCE_VOLUME_TO_USE, TARGET_MASS, TARGET_VOLUME, TARGET_CONCENTRATION, DESTINATION_PLATE_ID, DESTINATION_WELL}
    private final List<String> acceptableHeaderList = Arrays.asList("SAMPLE_ID", "SAMPLE_NAME", "SOURCE_MASS_TO_USE", "SOURCE_VOLUME_TO_USE", "TARGET_MASS", "TARGET_VOLUME", "TARGET_CONCENTRATION", "DESTINATION_PLATE_ID", "DESTINATION_WELL");
    private final List<String> requiredHeaderValues = Arrays.asList("SAMPLE_ID", "SAMPLE_NAME", "DESTINATION_PLATE_ID", "DESTINATION_WELL");
    private String destinationPlateIdFieldName = "";
    private String destinationWellFieldName = "";
    private String sourceMassToUseFieldName = "";
    private String sourceVolumeToUseFieldName = "";
    private String targetMassFieldName = "";
    private String targetVolFieldName = "";
    private String targetConcFieldName = "";
    private Map<String, String> fileHeaderToDataTypeFieldNameValueMap = new HashMap<>();
    private IgoLimsPluginUtils util = new IgoLimsPluginUtils();

    public SampleToPlateAssignmentViaFileUplaod() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("SAMPLE ASSIGNMENT USING FILE");
    }

    @Override
    public PluginResult run() throws ServerException {
        try {
            String poolingFileName = clientCallback.showFileDialog("Upload Pooling Sheet", ".csv");
            if (StringUtils.isBlank(poolingFileName)) {
                logInfo("User did not load pooling sheet. Plate assignment GUI will be rendered instead for manual assignment.");
                return new PluginResult(true);
            }
            List<String> fileData = util.readDataFromCsvFile(clientCallback.readBytes(poolingFileName));
            Map<String, Integer> headerColumnLocationMap = util.getCsvHeaderValueMap(fileData);
            if (!isValidCsvFile(poolingFileName, fileData, requiredHeaderValues)) {
                return new PluginResult(false);
            }
            setRelevantDataTypeFieldNames();
            fileHeaderToDataTypeFieldNameValueMap.put("SAMPLE_ID", "SampleId");
            fileHeaderToDataTypeFieldNameValueMap.put("SAMPLE_NAME", "OtherSampleId");
            fileHeaderToDataTypeFieldNameValueMap.put("SOURCE_MASS_TO_USE", sourceMassToUseFieldName);
            fileHeaderToDataTypeFieldNameValueMap.put("SOURCE_VOLUME_TO_USE", sourceVolumeToUseFieldName);
            fileHeaderToDataTypeFieldNameValueMap.put("TARGET_MASS", targetMassFieldName);
            fileHeaderToDataTypeFieldNameValueMap.put("TARGET_VOLUME", targetVolFieldName);
            fileHeaderToDataTypeFieldNameValueMap.put("TARGET_CONCENTRATION", targetConcFieldName);
            fileHeaderToDataTypeFieldNameValueMap.put("DESTINATION_PLATE_ID", destinationPlateIdFieldName);
            fileHeaderToDataTypeFieldNameValueMap.put("DESTINATION_WELL", destinationWellFieldName);
            List<DataRecord> protocolRecords = activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user);
            if (protocolRecords.isEmpty()) {
                clientCallback.displayError(String.format("Did not find %s protocol records attached to this task.", activeTask.getInputDataTypeName()));
                return new PluginResult(false);
            }
            getUpdatedFieldValueList(fileData, headerColumnLocationMap, protocolRecords);
            //clientCallback.displayInfo(String.format("%s, %s, %s, %s, %s, %s, %s", destinationPlateIdFieldName, destinationWellFieldName, sourceMassToUseFieldName, sourceVolumeToUseFieldName, targetConcFieldName, targetMassFieldName, targetVolFieldName));

        } catch (Exception e) {
            logInfo(Arrays.toString(e.getStackTrace()));
            clientCallback.displayError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate excel file uploaded for plate assignment
     *
     * @param filePath
     * @param fileData
     * @param requiredHeaderVals
     * @return true/false
     * @throws ServerException
     */
    private Boolean isValidCsvFile(String filePath, List<String> fileData, List<String> requiredHeaderVals) throws ServerException {
        if (!util.isCsvFile(filePath)) {
            clientCallback.displayError(String.format("The uploaded file %s is not a valid .csv file", filePath));
            return false;
        }
        if (!util.csvFileContainsRequiredHeaders(fileData, requiredHeaderVals)) {
            clientCallback.displayError(String.format("File is missing some of required Header values \n%s", util.convertListToString(requiredHeaderVals)));
            return false;
        }
        if (!util.csvFileHasData(fileData)) {
            clientCallback.displayError(String.format("The uploaded file %s does not appear to have any data rows. Please double check.", filePath));
            return false;
        }
        return true;
    }

    /**
     * Method to update aliquoting DataFieldNames based on tags present on Aliquoting DataType.
     *
     * @throws ServerException
     * @throws RemoteException
     */
    private void setRelevantDataTypeFieldNames() throws ServerException, RemoteException {
        List<DataFieldDefinition> dataFieldDefinitions = dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions(activeTask.getInputDataTypeName()).getDataFieldDefinitionList();
        for (DataFieldDefinition dfd : dataFieldDefinitions) {
            String tag = dfd.tag;
            if (tag.matches(AliquotingTags.ALIQUOT_DESTINATION_PLATE_ID)) {
                destinationPlateIdFieldName = dfd.dataFieldName;
            }
            if (tag.matches(AliquotingTags.ALIQUOT_DESTINATION_WELL_POS)) {
                destinationWellFieldName = dfd.dataFieldName;
            } else if (tag.matches(AliquotingTags.ALIQUOT_DESTINATION_POOL)) {
                destinationWellFieldName = dfd.dataFieldName;
            }
            if (tag.matches(AliquotingTags.ALIQUOT_SOURCE_VOL_TO_USE)) {
                sourceVolumeToUseFieldName = dfd.dataFieldName;
            }
            if (tag.matches(AliquotingTags.ALIQUOT_SOURCE_MASS_TO_USE)) {
                sourceMassToUseFieldName = dfd.dataFieldName;
            }
            if (tag.matches(AliquotingTags.ALIQUOT_TARGET_MASS)) {
                targetMassFieldName = dfd.dataFieldName;
            }
            if (tag.matches(AliquotingTags.ALIQUOT_TARGET_VOL)) {
                targetVolFieldName = dfd.dataFieldName;
            }
            if (tag.matches(AliquotingTags.ALIQUOT_TARGET_CONC)) {
                targetConcFieldName = dfd.dataFieldName;
            }
        }
    }

    /**
     * Validation method to check if the header in the file has a field with valid tag to identify the DataFieldName on the DataType
     *
     * @param headerName
     * @return true/false
     * @throws ServerException
     */
    private Boolean isValidColumnNameForHeader(String headerName) throws ServerException {
        if (headerName.equals(String.valueOf(acceptableHeaderValuesEnum.SOURCE_MASS_TO_USE)) && StringUtils.isBlank(sourceMassToUseFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_TARGET_MASS));
            return false;
        }
        if (headerName.equals(String.valueOf(acceptableHeaderValuesEnum.SOURCE_VOLUME_TO_USE)) && StringUtils.isBlank(sourceVolumeToUseFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_SOURCE_VOL_TO_USE));
            return false;
        }
        if (headerName.equals(String.valueOf(acceptableHeaderValuesEnum.TARGET_MASS)) && StringUtils.isBlank(targetMassFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_TARGET_MASS));
            return false;
        }
        if (headerName.equals(String.valueOf(acceptableHeaderValuesEnum.TARGET_VOLUME)) && StringUtils.isBlank(targetVolFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_TARGET_VOL));
            return false;
        }
        if (headerName.equals(String.valueOf(acceptableHeaderValuesEnum.TARGET_CONCENTRATION)) && StringUtils.isBlank(targetConcFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_TARGET_CONC));
            return false;
        }
        if (headerName.equals(String.valueOf(acceptableHeaderValuesEnum.DESTINATION_PLATE_ID)) && StringUtils.isBlank(destinationPlateIdFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_DESTINATION_PLATE_ID));
            return false;
        }
        if (headerName.equals(acceptableHeaderValuesEnum.DESTINATION_WELL) && StringUtils.isBlank(destinationWellFieldName)) {
            clientCallback.displayError(String.format("Missing tag '%s' on DataType attached to this step.", AliquotingTags.ALIQUOT_DESTINATION_WELL_POS));
            return false;
        }
        return true;
    }

    /**
     * Method to get the a column value from a row using headerName
     *
     * @param row
     * @param headerValueMap
     * @param headerName
     * @return Object
     * @throws ServerException
     */
    private Object getFieldValueFromRowValues(String row, Map<String, Integer> headerValueMap, String headerName) throws ServerException {
        List<String> values = Arrays.asList(row.split(","));
        Integer headerPosition = headerValueMap.get(headerName);
        return values.get(headerPosition);
    }

    /**
     * Method to get the FieldValues Map from uploaded file for DataRecord
     *
     * @param row
     * @param rec
     * @param headerValueMap
     * @return
     * @throws ServerException
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws NotFound
     */
    private Map<String, Object> getUpdatedFieldValues(String row, DataRecord rec, Map<String, Integer> headerValueMap) throws ServerException, RemoteException, InvalidValue, IoError, NotFound {
        List<String> rowValues = Arrays.asList(row.split(",|\n"));
        List<String> fileHeaders = new ArrayList<>(headerValueMap.keySet());
        Integer headerSize = headerValueMap.size();
        Map<String, Object> updatedValues = new HashMap<>();
        for (String entry : fileHeaders) {
            logInfo(Boolean.toString(acceptableHeaderList.contains(entry) && !StringUtils.isBlank(fileHeaderToDataTypeFieldNameValueMap.get(entry)) && isValidColumnNameForHeader(entry)));
            if (acceptableHeaderList.contains(entry) && !StringUtils.isBlank(fileHeaderToDataTypeFieldNameValueMap.get(entry)) && isValidColumnNameForHeader(entry)) {
                if (headerValueMap.get(entry) < rowValues.size()){
                    Object value = rowValues.get(headerValueMap.get(entry));
                    logInfo(String.valueOf(value));
                    updatedValues.put(fileHeaderToDataTypeFieldNameValueMap.get(entry), value);
                }
            }
        }
        return updatedValues;
    }

    /**
     * Method to update the FieldValues on DataType with values from uploaded file.
     *
     * @param fileData
     * @param headerValueMap
     * @param protocolRecords
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws IoError
     * @throws InvalidValue
     */
    private void getUpdatedFieldValueList(List<String> fileData, Map<String, Integer> headerValueMap, List<DataRecord> protocolRecords) throws NotFound, RemoteException, ServerException, IoError, InvalidValue {
        for (int i = 1; i < fileData.size(); i++) {
            String row = fileData.get(i);
            Object sampleId = getFieldValueFromRowValues(row, headerValueMap, String.valueOf(acceptableHeaderValuesEnum.SAMPLE_ID));
            for (DataRecord proc : protocolRecords) {
                if (String.valueOf(sampleId).equals(proc.getStringVal("SampleId", user))) {
//                    clientCallback.displayInfo(sampleId + "     " + i);
                    proc.setFields(getUpdatedFieldValues(row, proc, headerValueMap), user);
                }
            }
        }
    }
}
