package com.velox.sloan.cmo.workflows.micronics;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImportNewMicronicTubes extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin"};
    private NewMicronicTubeTareWeightImporter fileDataReader = new NewMicronicTubeTareWeightImporter();

    public ImportNewMicronicTubes() {
        setActionMenu(true);
        setLine1Text("Import New");
        setLine2Text("Micronic Tubes");
        setDescription("To import empty micronic tubes information along with 'tare weight' to store in LIMS. This information" +
                " will be used to automatically calculate the volume of the sample when samples are added to these tubes.");
        setUserGroupList(permittedUsers);
    }

    @Override
    public PluginResult run() throws ServerException {
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
        } catch (Exception e) {
            clientCallback.displayError(String.format("cannot read information from file:\n%s", e));
            logError(String.format("cannot read information from file:"), e);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private boolean isValidCsvFile(String file) {
        return fileDataReader.isCsvFile(file);
    }

    private boolean fileHasData(String[] fileData) {
        return fileDataReader.dataFileHasValidData(fileData);
    }

    private boolean hasValidMicronicFileHeader(String[] fileData) {
        return fileDataReader.dataFileHasValidHeader(fileData);
    }

    private boolean allRowsContainValidData(String[] fileData) {
        return fileDataReader.rowsInFileHasValidData(fileData);
    }

    private boolean isValidFileWithValidData(String filePath, byte[] fileDataToBytes) throws ServerException {
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

    private List<Map<String, Object>> getMicronicTubeRecordsFromFile(String[] fileData) {
        return fileDataReader.readNewTubeRecordsFromFileData(fileData);
    }

    private List<String> getExistingMicronicBarcodes(List<DataRecord> existingMicronicTubes) throws NotFound, RemoteException {
        List<String> existingMicronicBarcodes = new ArrayList<>();
        for (DataRecord record : existingMicronicTubes) {
            existingMicronicBarcodes.add(record.getStringVal("MicronicTubeBarcode", user));
        }
        return existingMicronicBarcodes;
    }

    private List<String> getMicronicTubeBarcodes(List<Map<String, Object>> micronicTubes) {
        return fileDataReader.getMicronicTubeBarcodesFromTubeRecords(micronicTubes);
    }

    private boolean hasDuplicateBarcodesInData(List<String> micronicTubeBarcodesAlreadyInLims, List<String> newMicronicTubeBarcodes) throws ServerException {
        List<String> duplicatesWithExistingBarcodes = fileDataReader.getDuplicateBarcodesInExistingBarcodes(micronicTubeBarcodesAlreadyInLims, newMicronicTubeBarcodes);
        List<String> duplicatesInNewBarcodes = fileDataReader.getDuplicateValuesInNewBarcodesList(newMicronicTubeBarcodes);
        if (duplicatesWithExistingBarcodes.size() > 0) {
            clientCallback.displayError(String.format("Following barcodes already exist:\n%s", convertListToString(duplicatesWithExistingBarcodes)));
            logError(String.format("Following barcodes already exist:\n%s", convertListToString(duplicatesWithExistingBarcodes)));
            return true;
        }
        if (duplicatesInNewBarcodes.size() > 0) {
            clientCallback.displayError(String.format("Following duplicate barcodes found in the uploaded file\n%s", convertListToString(duplicatesInNewBarcodes)));
            logError(String.format("Folloeing duplicate barcodes found in the uploaded file\n%s", convertListToString(duplicatesInNewBarcodes)));
            return true;
        }
        return false;
    }

    private String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }

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

