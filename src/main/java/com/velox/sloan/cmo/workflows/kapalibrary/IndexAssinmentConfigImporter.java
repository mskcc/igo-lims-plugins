package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This is the plugin class designed to import the data for 'AutoIndexAssignmentConfig' DataType via file upload.
 * 'Index Barcode and Adapter' terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 *
 * @author sharmaa1
 */
public class IndexAssinmentConfigImporter extends DefaultGenericPlugin {
    private final List<String> VALID_HEADER_VALUES = Arrays.asList("Adapter Plate Barcode", "Index ID", "Index Tag", "Well ID", "Concentration", "Volume", "Index Type", "Set ID"); //Header values that are expected to be present in the uploaded file.
    private final String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public IndexAssinmentConfigImporter() {
        setTableToolbar(true);
        setLine1Text("Upload Index Assignment");
        setLine2Text("Configurations");
        setDescription("Use this button to import Index Assignment configurations that will be used to auto assign Index barcodes to samples in Library prep workflow.");
    }

    @Override
    public boolean onTableToolbar() {
        return dataTypeName.equals(INDEX_ASSIGNMENT_CONFIG_DATATYPE);
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            String csvFilePath = clientCallback.showFileDialog("Upload File with Index Assignment configurations", null);
            if (StringUtils.isBlank(csvFilePath)) {
                logInfo("Path to Index Assignment configurations file is empty. Or file not uploaded and process canceled by the user.");
                return new PluginResult(false);
            }

            byte[] fileToBytes = clientCallback.readBytes(csvFilePath);
            List<String> fileDataRows = utils.readDataFromCsvFile(fileToBytes);
            if (!(isValidCsvFile(csvFilePath) && fileHasData(fileDataRows, csvFilePath) && fileHasValidHeader(fileDataRows, VALID_HEADER_VALUES, csvFilePath) && allRowsHaveData(fileDataRows, VALID_HEADER_VALUES, csvFilePath))) {
                return new PluginResult(false);
            }

            Map<String, Integer> headerValueMap = utils.getCsvHeaderValueMap(fileDataRows, pluginLogger);
            List<Map<String, Object>> indexAssignmentConfigurations = parseIndexAssignmentConfigurations(fileDataRows, headerValueMap);
            List<String> uniqueAdapterPlateBarcodes = getUniueAdapterPlateBarcodes(fileDataRows, headerValueMap);
            List<DataRecord> indexAssignmentsInLims = dataRecordManager.queryDataRecords("IndexAssignment", null, user);
            if (!adapterPlateBarcodeAlreadyExists(uniqueAdapterPlateBarcodes) && isValidIndexAssignmentRecords(indexAssignmentsInLims, fileDataRows, headerValueMap)) {
                List newIndexAssignmentConfigurations = dataRecordManager.addDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, indexAssignmentConfigurations, user);
                dataRecordManager.commitChanges(String.format("Added '%d' new Index Assignment Configurations records.", newIndexAssignmentConfigurations.size()), false, user);
                clientCallback.displayInfo(String.format("Added '%d' new Index Assignment Configurations records.", newIndexAssignmentConfigurations.size()));
                logInfo(String.format("Added '%d' new Index Assignment Configurations records.", newIndexAssignmentConfigurations.size()));
                return new PluginResult(true);
            }
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while importing Adapter Plate/Set:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IoError | IOException ioError) {
            String errMsg = String.format("IoError Exception Error while importing Adapter Plate/Set:\n%s", ExceptionUtils.getStackTrace(ioError));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidValue invalidValue) {
            String errMsg = String.format("InvalidValue Exception Error while importing Adapter Plate/Set:\n%s", ExceptionUtils.getStackTrace(invalidValue));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (NotFound notFound) {
            String errMsg = String.format("NotFound Exception Error while importing Adapter Plate/Set:\n%s", ExceptionUtils.getStackTrace(notFound));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate csv file type.
     *
     * @param filepath
     * @return Boolean
     * @throws ServerException
     */
    private boolean isValidCsvFile(String filepath) throws ServerException, RemoteException {
        if (!utils.isCsvFile(filepath)) {
            clientCallback.displayError(String.format("File '%s' is not a valid csv file. File must be csv with valid .csv extension", filepath));
            logError(String.format("File '%s' is not a valid csv file. File must be csv with valid .csv extension", filepath));
            return false;
        }
        return true;
    }

    /**
     * Method to check if file has data.
     *
     * @param fileRowData
     * @param filePath
     * @return Boolean
     * @throws ServerException
     */
    private boolean fileHasData(List<String> fileRowData, String filePath) throws ServerException, RemoteException {
        if (!utils.csvFileHasData(fileRowData)) {
            clientCallback.displayError(String.format("File '%s' is empty. Please load a csv file with data.", filePath));
            logError(String.format("File '%s' is empty. Please load a csv file with data.", filePath));
            return false;
        }
        return true;
    }

    /**
     * Method to validate Header values in uploaded file.
     *
     * @param fileRowData
     * @param expectedHeader
     * @param filePath
     * @return Boolean
     * @throws ServerException
     */
    private boolean fileHasValidHeader(List<String> fileRowData, List<String> expectedHeader, String filePath) throws ServerException, RemoteException {
        if (!utils.csvFileHasValidHeader(fileRowData, expectedHeader)) {
            clientCallback.displayError(String.format("File '%s' does not contain valid headers. File must have following header values: %s.", filePath, utils.convertListToString(expectedHeader)));
            logError(String.format("File '%s' does not contain valid headers. File must have following header values: %s.", filePath, utils.convertListToString(expectedHeader)));
            return false;
        }
        return true;
    }

    /**
     * Method to validate data for all rows in uploaded file.
     *
     * @param fileRowData
     * @param expectedHeader
     * @param filePath
     * @return Boolean
     * @throws ServerException
     */
    private boolean allRowsHaveData(List<String> fileRowData, List<String> expectedHeader, String filePath) throws ServerException, RemoteException {
        if (!utils.allRowsInCsvFileHasValidData(fileRowData, expectedHeader, pluginLogger)) {
            clientCallback.displayError(String.format("Some rows in File '%s' has missing data. Please make sure all rows has data for all columns", filePath));
            logError(String.format("Some rows in File '%s' has missing data. Please make sure all rows has data for all columns", filePath));
            return false;
        }
        return true;
    }

    /**
     * Method to check if the Index record vales in uploaded file match the existing values under 'IndexBarcode' DataType. 'IndexBarcode' datatype holds the values for all indexes to be used in LIMS.
     *
     * @param indexAssignmentRecords
     * @param dataRows
     * @param headerValuesMap
     * @return Boolean
     * @throws NotFound
     * @throws RemoteException
     * @throws InvalidValue
     */
    private boolean isValidIndexAssignmentRecords(List<DataRecord> indexAssignmentRecords, List<String> dataRows, Map<String, Integer> headerValuesMap) throws NotFound, RemoteException, InvalidValue {
        for (int i = 1; i < dataRows.size(); i++) {
            boolean valid = false;
            List<String> newIndexConfigValues = Arrays.asList(dataRows.get(i).split(","));
            String indexId = Arrays.asList(dataRows.get(i).split(",")).get(headerValuesMap.get("Index ID"));
            String indexTag = Arrays.asList(dataRows.get(i).split(",")).get(headerValuesMap.get("Index Tag"));
            for (DataRecord rec : indexAssignmentRecords) {
                if (indexId.equals(rec.getStringVal("IndexId", user)) && indexTag.equals(rec.getStringVal("IndexTag", user))) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                logInfo(String.format("IndexID '%s' and IndexTag '%s' in the uploaded file does not match any Defined IndexAssignment records in the file." +
                        "\nPlease make sure that IndexID and IndexTag values are correct and match one of the defined IndexAssignment values for the IndexType", indexId, indexTag));
                throw new InvalidValue(String.format("IndexID '%s' and IndexTag '%s' in the uploaded file does not match any Defined IndexAssignment records in the file." +
                        "\nPlease make sure that IndexID and IndexTag values are correct and match one of the defined IndexAssignment values for the IndexType", indexId, indexTag));

            }
        }
        return true;
    }

    /**
     * Method to parse file rows into 'AutoIndexAssignmentConfig' datarecord.
     *
     * @param fileRowData
     * @param headerValuesMap
     * @return List<Map < String, Object>>
     * @throws ServerException
     */
    private List<Map<String, Object>> parseIndexAssignmentConfigurations(List<String> fileRowData, Map<String, Integer> headerValuesMap) throws ServerException {
        List<Map<String, Object>> indexAssignmentConfigurations = new ArrayList<>();
        for (int i = 1; i < fileRowData.size(); i++) {
            Map<String, Object> indexConfiguration = new HashMap<>();
            List<String> rowValues = Arrays.asList(fileRowData.get(i).split(","));
            indexConfiguration.put("AdapterPlateId", rowValues.get(headerValuesMap.get("Adapter Plate Barcode")));
            indexConfiguration.put("IndexId", rowValues.get(headerValuesMap.get("Index ID")));
            indexConfiguration.put("IndexTag", rowValues.get(headerValuesMap.get("Index Tag")));
            indexConfiguration.put("AdapterConcentration", Double.parseDouble(rowValues.get(headerValuesMap.get("Concentration"))));
            indexConfiguration.put("AdapterVolume", Double.parseDouble(rowValues.get(headerValuesMap.get("Volume"))));
            indexConfiguration.put("WellId", rowValues.get(headerValuesMap.get("Well ID")));
            indexConfiguration.put("IndexType", rowValues.get(headerValuesMap.get("Index Type")));
            indexConfiguration.put("SetId", Integer.parseInt(rowValues.get(headerValuesMap.get("Set ID"))));
            indexConfiguration.put("IsActive", false);
            indexConfiguration.put("IsDepelted", false);
            indexConfiguration.put("LastUsed", false);
            indexAssignmentConfigurations.add(indexConfiguration);
        }
        return indexAssignmentConfigurations;
    }

    /**
     * Method to get unique Adapter Plate barcodes from rows in uploaded file.
     *
     * @param fileRowData
     * @param headerValuesMap
     * @return List<String>
     */
    private List<String> getUniueAdapterPlateBarcodes(List<String> fileRowData, Map<String, Integer> headerValuesMap) {
        Set<String> adaptePlateBarcodes = new HashSet<>();
        for (String row : fileRowData) {
            List<String> rowValues = Arrays.asList(row.split(","));
            adaptePlateBarcodes.add(rowValues.get(headerValuesMap.get("Adapter Plate Barcode")));
        }
        return new ArrayList<>(adaptePlateBarcodes);
    }

    /**
     * @param adapterPlateBarcodes
     * @return Boolean
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private boolean adapterPlateBarcodeAlreadyExists(List<String> adapterPlateBarcodes) throws IoError, RemoteException, NotFound, ServerException {
        for (String barcode : adapterPlateBarcodes) {
            if (dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "AdapterPlateId = '" + barcode + "'", user).size() > 0) {
                clientCallback.displayError(String.format("Adapter Plate Barcode '%s' is already assigned to previous '%s' records in LIMS.", barcode, INDEX_ASSIGNMENT_CONFIG_DATATYPE));
                logError(String.format("Adapter Plate Barcode '%s' is already assigned to previous '%s' records in LIMS.", barcode, INDEX_ASSIGNMENT_CONFIG_DATATYPE));
                return true;
            }
        }
        return false;
    }
}
