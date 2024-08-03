package com.velox.sloan.cmo.workflows.samplereceiving;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;


/**
 * This plugin is designed to import Volume information from samples on plate using a file upload.
 */
public class PlateSampleVolumeImporter extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin"};
    private List<String> expectedFileHeaderValues = Arrays.asList("RACKID", "TUBE", "SAMPLES", "STATUS", "VOLMED", "VOLAVG", "VOLSTDEV", "DISMED");
    private List<String> headerWithMustHaveValuesInRow = Arrays.asList("RACKID", "TUBE");
    private IgoLimsPluginUtils commonMethods = new IgoLimsPluginUtils();

    public PlateSampleVolumeImporter() {
        setTaskTableToolbar(true);
        setTaskFormToolbar(true);
        setLine1Text("Update Vol for");
        setLine2Text("samples on plate");
        setDescription("Upload file with volumes for samples on plate.");
        setUserGroupList(permittedUsers);
    }

    public boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().containsKey("UPDATE VOLUME FOR SAMPLES ON PLATE");
    }

    @Override
    public boolean onTaskFormToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("UPDATE VOLUME FOR SAMPLES ON PLATE");
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while setting Task Form toolbar button for plugin PlateSampleVolumeImporter.\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    @Override
    public boolean onTaskTableToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("UPDATE VOLUME FOR SAMPLES ON PLATE");
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while setting Task Table toolbar button for plugin PlateSampleVolumeImporter.\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            String plateVolumeFile = clientCallback.showFileDialog("Upload file with plate volume data", null);
            if (StringUtils.isEmpty(plateVolumeFile)) {
                logInfo("Method canceled by user or file not uploaded");
                return new PluginResult(false);
            }
            if (!isValidCsvFile(plateVolumeFile)) {
                return new PluginResult(false);
            }

            List<String> fileData = commonMethods.readDataFromCsvFile(clientCallback.readBytes(plateVolumeFile));
            if (!csvFileHasValidData(fileData, plateVolumeFile)) {
                return new PluginResult(false);
            }

            Map<String, Integer> headerValuesMap = commonMethods.getCsvHeaderValueMap(fileData, pluginLogger);
            if (fileDataHasDuplicateRecords(fileData, headerValuesMap, plateVolumeFile)) {
                return new PluginResult(false);
            }

            List<DataRecord> samples = activeTask.getAttachedDataRecords(user);
            if (samples.isEmpty()) {
                clientCallback.displayError("There are no samples attached to this task.");
                return new PluginResult(false);
            }

            List<Map<String, Object>> volumeRecordsFromfile = getRecordsFromFile(fileData, headerValuesMap);
            if (volumeRecordsFromfile.isEmpty()) {
                clientCallback.displayError(String.format("Cannot read volume records from file '%s'.", plateVolumeFile));
                return new PluginResult(false);
            }
            updateSampleVolumes(samples, volumeRecordsFromfile);
        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException -> Error while parsing the plate volume data file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IOException e) {
            String errMsg = String.format("IOException -> Error while parsing the plate volume data file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate file type.
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean isValidCsvFile(String fileName) throws ServerException, RemoteException {
        if (!commonMethods.isCsvFile(fileName)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a .csv file.", fileName));
            return false;
        }
        return true;
    }

    /**
     * Method to validate file type and if the file has valid data.
     * @param fileData
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean csvFileHasValidData(List<String> fileData, String fileName) throws ServerException, RemoteException {
        if (!commonMethods.csvFileHasData(fileData)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is empty.", fileName));
            return false;
        }
        if (!commonMethods.csvFileHasValidHeader(fileData, expectedFileHeaderValues)) {
            clientCallback.displayError(String.format("File '%s' has invalid header values. File header should have '%s'", fileName, expectedFileHeaderValues.toString()));
            return false;
        }
        if (!commonMethods.allRowsInCsvFileHasValidData(fileData, headerWithMustHaveValuesInRow, pluginLogger)) {
            clientCallback.displayError(String.format("Some of the rows in file '%s' have missing data under column %s.\n" +
                    "All rows in file must have values under these columns.", fileName, headerWithMustHaveValuesInRow.toString()));
            return false;
        }
        return true;
    }

    /**
     * Method to read Volume values from the file.
     * @param rowData
     * @param header
     * @return
     * @throws ServerException
     */
    private double getVolumeFromRowData(String[] rowData, Map<String, Integer> header) throws ServerException, RemoteException {
        double volume = Double.parseDouble(rowData[header.get("VOLMED")]);
        String plateId = rowData[header.get("RACKID")].trim();
        String wellPosition = rowData[header.get("TUBE")].trim();
        if (volume < 0) {
            clientCallback.displayWarning(String.format("Volume for plate %s, well %s is less than 0.0. It will be set to 0.0 ", plateId, wellPosition));
            return 0.0;
        }
        return volume;
    }

    /**
     * Method to read metadata from file.
     * @param fileData
     * @param header
     * @return
     * @throws ServerException
     */
    private List<Map<String, Object>> getRecordsFromFile(List<String> fileData, Map<String, Integer> header) throws ServerException, RemoteException {
        List<Map<String, Object>> volumeDataRecords = new ArrayList<>();
        int minimumValuesInRow = 5;
        int rowNumAfterHeader = 1;
        for (int i = rowNumAfterHeader; i <= fileData.size() - 1; i++) {
            Map<String, Object> volumeData = new HashMap<>();
            String[] rowValues = fileData.get(i).split(",");
            if (rowValues.length >= minimumValuesInRow) {
                volumeData.put("RelatedRecord23", String.valueOf(rowValues[header.get("RACKID")]).trim());
                volumeData.put("RowPosition", commonMethods.getPlateWellRowPosition(rowValues[header.get("TUBE")]).trim());
                volumeData.put("ColPosition", commonMethods.getPlateWellColumnPosition(rowValues[header.get("TUBE")]).trim());
                volumeData.put("Volume", getVolumeFromRowData(rowValues, header));
                volumeDataRecords.add(volumeData);
            }
        }
        return volumeDataRecords;
    }

    /**
     * Method to check if file data has duplicate row values.
     * @param fileData
     * @param header
     * @param filename
     * @return
     * @throws ServerException
     */
    private boolean fileDataHasDuplicateRecords(List<String> fileData, Map<String, Integer> header, String filename) throws ServerException, RemoteException {
        Set<String> uniqueRecords = new HashSet<>();
        List<String> duplicateRecords = new ArrayList<>();
        int firstRowNumAfterHeader = 1;
        for (int i = firstRowNumAfterHeader; i <= fileData.size() - 1; i++) {
            String[] rowData = fileData.get(i).split(",");
            String data = rowData[header.get("RACKID")].trim() + " " + rowData[header.get("TUBE")].trim();
            if (!uniqueRecords.add(data)) {
                duplicateRecords.add(fileData.get(i).trim());
            }
        }
        if (duplicateRecords.size() > 0) {
            clientCallback.displayError(String.format("Duplicate row values found in file '%s':\n%s", filename, commonMethods.convertListToString(duplicateRecords)));
            return true;
        }
        return false;
    }

    /**
     * Method to update volume and plate information on samples.
     * @param samples
     * @param volumeRecordsFromFile
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws IoError
     * @throws InvalidValue
     */
    private void updateSampleVolumes(List<DataRecord> samples, List<Map<String, Object>> volumeRecordsFromFile) {
        for (DataRecord sample : samples) {
            try {
                String sampleId = sample.getStringVal("SampleId", user).trim();
                String samplePlateId = sample.getStringVal("RelatedRecord23", user);
                String sampleRowPosition = sample.getSelectionVal("RowPosition", user).trim();
                String sampleColumnPosition = sample.getSelectionVal("ColPosition", user).trim();
                boolean found = false;
                for (Map<String, Object> volumeData : volumeRecordsFromFile) {
                    if (!(StringUtils.isBlank(samplePlateId)) && samplePlateId.equals(String.valueOf(volumeData.get("RelatedRecord23")))
                            && sampleRowPosition.equals(String.valueOf(volumeData.get("RowPosition"))) && sampleColumnPosition.equals(String.valueOf(volumeData.get("ColPosition")))) {
                        sample.setDataField("Volume", volumeData.get("Volume"), user);
                        found = true;
                    }
                }
                if (!found) {
                    clientCallback.displayWarning(String.format("Volume data not found in file records for sample: %s", sampleId));
                }
            } catch (InvalidValue invalidValue) {
                logError(String.format("InvalidValue Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(invalidValue)));
            } catch (IoError ioError) {
                logError(String.format("IoError Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
            } catch (ServerException e) {
                logError(String.format("ServerException -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
        }
    }
}