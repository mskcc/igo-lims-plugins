package com.velox.sloan.cmo.workflows.micronics;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plugin to create new Micronic Tube records along with tare weight in LIMS.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class NewMicronicTubeRecordGenerator extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin"};
    private NewMicronicTubeTareWeightImporter fileDataReader = new NewMicronicTubeTareWeightImporter();

    public NewMicronicTubeRecordGenerator() {
        setActionMenu(true);
        setLine1Text("Import New");
        setLine2Text("Micronic Tubes");
        setDescription("To import empty micronic tubes information along with 'tare weight' to store in LIMS. This information" +
                " will be used to automatically calculate the volume of the sample when samples are added to these tubes.");
        setUserGroupList(permittedUsers);
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            String excelFilePath = clientCallback.showFileDialog("Upload File with micronic tube information.", null);
            byte[] fileToBytes = clientCallback.readBytes(excelFilePath);
            if (StringUtils.isEmpty(excelFilePath)) {
                logError("File path not provided. Task canceled by user.");
                return new PluginResult(false);
            }
            if (!isValidFileWithValidData(excelFilePath, fileToBytes)) {
                return new PluginResult(false);
            }
            String[] fileData = new String(fileToBytes).split("\r\n|\r|\n");
            List<Map<String, Object>> micronicTubes = getMicronicTubeRecordsFromFile(fileData);
            if (shouldAddNewMicronicRecords(micronicTubes)) {
                dataRecordManager.addDataRecords("MicronicTubesTareWeight", micronicTubes, user);
                dataRecordManager.storeAndCommit(String.format("Added %d new micronic tubes with weights.", micronicTubes.size()), user);
                clientCallback.displayInfo(String.format("Added %d new 'Micronic Tube Tare Weight' records", micronicTubes.size()));
            } else {
                return new PluginResult(false);
            }
        } catch (NotFound e) {
            String errMsg = String.format("NotFound Exception while reading MicronicTube information from file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception while reading MicronicTube information from file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }catch (IoError e) {
            String errMsg = String.format("IoError Exception while reading MicronicTube information from file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate file extension.
     *
     * @param file
     * @return true/false
     */
    private boolean isValidCsvFile(String file) {
        return fileDataReader.isCsvFile(file);
    }

    /**
     * Method to validate if file has data.
     *
     * @param fileData
     * @return true/false
     */
    private boolean fileHasData(String[] fileData) {
        return fileDataReader.dataFileHasValidData(fileData);
    }

    /**
     * Method to valid header row values in file.
     *
     * @param fileData
     * @return true/false
     */
    private boolean hasValidMicronicFileHeader(String[] fileData) {
        return fileDataReader.dataFileHasValidHeader(fileData);
    }

    /**
     * Method to validate that all rows in excel file has required data
     *
     * @param fileData
     * @return
     */
    private boolean allRowsContainValidData(String[] fileData) {
        return fileDataReader.rowsInFileHasValidData(fileData);
    }

    /**
     * Method to valid file and file data
     *
     * @param filePath
     * @param fileDataToBytes
     * @return true/false
     * @throws ServerException
     */
    private boolean isValidFileWithValidData(String filePath, byte[] fileDataToBytes) throws ServerException, RemoteException {
        String[] rowData = new String(fileDataToBytes).split("\r\n|\r|\n");
        if (!isValidCsvFile(filePath)) {
            logError(String.format("Invalid file format %s. The file must be a '.csv' file", filePath));
            clientCallback.displayError(String.format("Invalid file format %s. The file must be a '.csv' file", filePath));
            return false;
        }
        if (!fileHasData(rowData)) {
            logError(String.format("Uploaded file '%s' is empty. Please check the file.", filePath));
            clientCallback.displayError(String.format("Uploaded file '%s' is empty. Please check the file.", filePath));
            return false;
        }
        if (!hasValidMicronicFileHeader(rowData)) {
            logError(String.format("Invalid file format. Please make sure that the file '%s' has valid header row \n" +
                    "Expected header: 'Rack','Tube','Barcode','Weight'\n" + "followed by rows with 'Micronic Tube' information.", filePath));
            clientCallback.displayError(String.format("Invalid file format. Please make sure that the file '%s' has valid header row \n" +
                    "Expected header: 'Rack','Tube','Barcode','Weight'\n" + "followed by rows with 'Micronic Tube' information.", filePath));
            return false;
        }
        if (!allRowsContainValidData(rowData)) {
            logError(String.format("Some rows contain invalid data in file '%s'. Make sure that Micronic Barcode is present and " +
                    "weight is > 0.", filePath));
            clientCallback.displayError(String.format("Some rows contain invalid data in file '%s'. Make sure that Micronic Barcode is present and " +
                    "weight is > 0.", filePath));
            return false;
        }
        return true;
    }

    /**
     * Method to create micronic tube record values from excel data
     *
     * @param fileData
     * @return List of Maps of micronic tube values
     */
    private List<Map<String, Object>> getMicronicTubeRecordsFromFile(String[] fileData) {
        return fileDataReader.readNewTubeRecordsFromFileData(fileData);
    }

    /**
     * Method to get existing MicronicTube Barcodes stored under MicronicTubeTareWeight DataType
     *
     * @param existingMicronicTubes
     * @return List of MicronicTube Barcodes
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getExistingMicronicBarcodes(List<DataRecord> existingMicronicTubes) throws NotFound, RemoteException {
        List<String> existingMicronicBarcodes = new ArrayList<>();
        for (DataRecord record : existingMicronicTubes) {
            existingMicronicBarcodes.add(record.getStringVal("MicronicTubeBarcode", user));
        }
        return existingMicronicBarcodes;
    }

    /**
     * Method to get MicronicTube Barcodes from Data
     *
     * @param micronicTubes
     * @return List of MicronicTube Barcodes
     */
    private List<String> getMicronicTubeBarcodes(List<Map<String, Object>> micronicTubes) {
        return fileDataReader.getMicronicTubeBarcodesFromTubeRecords(micronicTubes);
    }

    /**
     * Method to check if duplicate MicronicTube Barcodes exist in the data read from the file.
     *
     * @param micronicTubeBarcodesAlreadyInLims
     * @param newMicronicTubeBarcodes
     * @return true/false
     * @throws ServerException
     */
    private boolean hasDuplicateBarcodesInData(List<String> micronicTubeBarcodesAlreadyInLims, List<String> newMicronicTubeBarcodes) throws RemoteException, ServerException {
        List<String> duplicatesWithExistingBarcodes = fileDataReader.getDuplicateBarcodesInExistingBarcodes(micronicTubeBarcodesAlreadyInLims, newMicronicTubeBarcodes);
        List<String> duplicatesInNewBarcodes = fileDataReader.getDuplicateValuesInNewBarcodesList(newMicronicTubeBarcodes);
        if (duplicatesWithExistingBarcodes.size() > 0) {
            clientCallback.displayError(String.format("Following barcodes already exist: %s", convertListToString(duplicatesWithExistingBarcodes)));
            logError(String.format("Following barcodes already exist: %s", convertListToString(duplicatesWithExistingBarcodes)));
            return true;
        }
        if (duplicatesInNewBarcodes.size() > 0) {
            clientCallback.displayError(String.format("Following duplicate barcodes found in the uploaded file %s", convertListToString(duplicatesInNewBarcodes)));
            logError(String.format("Folloeing duplicate barcodes found in the uploaded file %s", convertListToString(duplicatesInNewBarcodes)));
            return true;
        }
        return false;
    }

    /**
     * Method to concatenate list separated by new line character '\n'.
     *
     * @param listWithValues
     * @return Strings separated by new line
     */
    private String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }

    /**
     * Method to validate no conflict state to add new MicronicTube records.
     *
     * @param micronicTubes
     * @return true/false
     * @throws ServerException
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private boolean shouldAddNewMicronicRecords(List<Map<String, Object>> micronicTubes) throws ServerException, IoError, RemoteException, NotFound {
        if (micronicTubes.isEmpty()) {
            clientCallback.displayError("Cannot construct any micronic tube records from the file. Please make sure you have uploaded "
                    + "\ncorrect file with correct formatting.");
            return false;
        }
        List<DataRecord> existingMicronicTubes = dataRecordManager.queryDataRecords("MicronicTubesTareWeight", null, user);
        List<String> existingMicronicTubeBarcodes = new ArrayList<>();
        if (existingMicronicTubes.isEmpty()) {
            logInfo("No 'MicronicTubesTareWeight' datatype records exist yet. Probably because this is the first import of New Micronic Tube Records.");
        } else {
            existingMicronicTubeBarcodes = getExistingMicronicBarcodes(existingMicronicTubes);
        }
        List<String> newMicronicBarcodes = getMicronicTubeBarcodes(micronicTubes);
        return !hasDuplicateBarcodesInData(existingMicronicTubeBarcodes, newMicronicBarcodes);
    }
}

