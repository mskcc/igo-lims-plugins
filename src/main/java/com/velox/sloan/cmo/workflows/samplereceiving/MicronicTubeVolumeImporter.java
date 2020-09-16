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
import com.velox.sloan.cmo.workflows.micronics.NewMicronicTubeTareWeightImporter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to import Sample Volume values using a file upload. The plugin calculates the volume using
 * MicronicTube info in 'MicronicTubesTareWeight' table and values present in the uploaded file. The calculated volume
 * is then saved on the corresponding samples.
 */

public class MicronicTubeVolumeImporter extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin"};
    private MicronicTubeVolumeDataReader volumeDataReader = new MicronicTubeVolumeDataReader();
    private NewMicronicTubeTareWeightImporter excelFileValidator = new NewMicronicTubeTareWeightImporter();

    public MicronicTubeVolumeImporter() {
        setTaskToolbar(true);
        setLine1Text("Calculate Volumes");
        setLine2Text("for micronics");
        setDescription("Upload file to calculate volumes using micronic tube tare weights.");
        setUserGroupList(permittedUsers);
    }

    public boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().containsKey("UPDATE MICRONIC VOLUMES USING TARE WEIGHT");
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("UPDATE MICRONIC VOLUMES USING TARE WEIGHT");
        } catch (RemoteException e) {
            logError(String.format("Error while setting task form toolbar button for 'MicronicTubeVolumeImporter' plugin:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    public PluginResult run() throws ServerException {
        try {
            String fileWithMicronicTubeData = clientCallback.showFileDialog("Please upload micronic file", null);
            if (StringUtils.isEmpty(fileWithMicronicTubeData)) {
                return new PluginResult(false);
            }
            byte[] byteData = clientCallback.readBytes(fileWithMicronicTubeData);
            String[] tubeDataInFile = new String(byteData).split("\r\n|\r|\n");
            if (!isValidFile(tubeDataInFile, fileWithMicronicTubeData)) {
                return new PluginResult(false);
            }
            List<DataRecord> samples = activeTask.getAttachedDataRecords(user);
            if (samples.isEmpty()) {
                clientCallback.displayError("There are no samples attached to this task.");
                return new PluginResult(false);
            }
            List<DataRecord> existingMicronicTubes = dataRecordManager.queryDataRecords("MicronicTubesTareWeight", null, user);
            List<Map<String, Object>> micronicTubeDataReadFromFile = readMicronicInfoFromFileData(tubeDataInFile, existingMicronicTubes, samples);
            if (micronicTubeDataReadFromFile.size() > 0 && existingMicronicTubes.size() > 0) {
                assignNewVolumeAndStorageToSamples(samples, micronicTubeDataReadFromFile, existingMicronicTubes);
            }
        } catch (Exception e) {
            logError(e);
            clientCallback.displayWarning(String.format("%s", e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate CSV file type.
     *
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean isValidCsvFile(String fileName) throws ServerException {
        if (!excelFileValidator.isCsvFile(fileName)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            logError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            return false;
        }
        return true;
    }

    /**
     * Method to check if file has data.
     *
     * @param fileData
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean fileHasData(String[] fileData, String fileName) throws ServerException {
        if (!excelFileValidator.dataFileHasValidData(fileData)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is empty. Please check the file.", fileName));
            logError(String.format("Uploaded file '%s' is empty. Please check the file.", fileName));
            return false;
        }
        return true;
    }

    /**
     * Method to check if file data has valid header values.
     *
     * @param fileData
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean fileHasValidHeader(String[] fileData, String fileName) throws ServerException {
        if (!excelFileValidator.dataFileHasValidHeader(fileData)) {
            clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            return false;
        }
        return true;
    }

    /**
     * Method to check if given file row data has all the values.
     *
     * @param rowData
     * @return
     */
    private boolean rowHasAllValues(String rowData) {
        return volumeDataReader.rowInFileHasValues(rowData);
    }

    /**
     * Method to check if all file rows have values.
     *
     * @param fileData
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean allRowsHaveValidValues(String[] fileData, String fileName) throws ServerException {
        if (!volumeDataReader.allRowsHaveValidData(fileData)) {
            clientCallback.displayError(String.format("Invalid row values in '%s'. Please make sure that all rows have values" +
                    "and Weight values are > 0.0.", fileName));
            logError(String.format("Invalid row values in '%s'. Please make sure that all rows have values" +
                    "and Weight values are > 0.0.", fileName));
            return false;
        }
        return true;
    }

    /**
     * Method to check if file is valid file type with valid data for processing.
     *
     * @param fileData
     * @param fileName
     * @return
     * @throws ServerException
     */
    private boolean isValidFile(String[] fileData, String fileName) throws ServerException {
        return isValidCsvFile(fileName) && fileHasData(fileData, fileName) && fileHasValidHeader(fileData, fileName) && allRowsHaveValidValues(fileData, fileName);
    }

    /**
     * Method to parse header values from file data.
     *
     * @param fileData
     * @return
     */
    private Map<String, Integer> parseFileHeader(String[] fileData) {
        return volumeDataReader.getHeaderValues(fileData);
    }

    /**
     * Method to get micronic records from passed LIMS DataRecords.
     *
     * @param recordsInLims
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getMicronicBarcodesFromRecordsInLims(List<DataRecord> recordsInLims) {
        List<String> tubeBarcodes = new ArrayList<>();
        for (DataRecord record : recordsInLims) {
            String barcode = null;
            try {
                barcode = record.getStringVal(("MicronicTubeBarcode"), user);
            } catch (NotFound notFound) {
                logError(String.format("NotFound -> Error reading 'MicronicTubeBarcode' field value for Sample with recordId %d:\n%s", record.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error reading 'MicronicTubeBarcode' field value for Sample with recordId %d:\n%s", record.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
            if (!StringUtils.isEmpty(barcode)) {
                tubeBarcodes.add(barcode);
            }
        }
        return tubeBarcodes;
    }

    /**
     * Method to Column position from data row from file.
     *
     * @param row
     * @param header
     * @return
     */
    private String getMicronicTubeColumnPosition(String row, Map<String, Integer> header) {
        return volumeDataReader.getColumnPosition(row, header);
    }

    /**
     * Method to get MicronicTubeWeight from LIMS.
     *
     * @param tubeBarcode
     * @param micronicTubeRecordsInLims
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private double getTubeTareWeight(String tubeBarcode, List<DataRecord> micronicTubeRecordsInLims){
        double tareWeight = 0.0;
        boolean barcodeFound = false;
        try {
            for (DataRecord record : micronicTubeRecordsInLims) {
                if (Objects.equals(record.getStringVal("MicronicTubeBarcode", user), tubeBarcode)) {
                    tareWeight = record.getDoubleVal("MicronicTubeWeight", user);
                    barcodeFound = true;
                }
            }
            if (!barcodeFound) {
                String errMsg = String.format("TARE WEIGHT not found for sample with Micronic Tube Barcode: %s", tubeBarcode);
                logError(errMsg);
                clientCallback.displayError(errMsg);
                throw new NotFound(errMsg);
            }
        } catch (RemoteException re) {
            logError(String.format("RemoteException -> Error reading 'MicronicTubeWeight' field value for MicronicTube with barcode %s:\n%s", tubeBarcode, ExceptionUtils.getStackTrace(re)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error reading 'MicronicTubeWeight' field value for MicronicTube with barcode %s:\n%s", tubeBarcode, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error reading 'MicronicTubeWeight' field value for MicronicTube with barcode %s:\n%s", tubeBarcode, ExceptionUtils.getStackTrace(e)));
        }
        return tareWeight;
    }

    /**
     * Method to get calculated volume for micronic tube.
     *
     * @param weightWithSampleVolume
     * @param tubeTareWeight
     * @param tubeBarcode
     * @return
     * @throws ServerException
     */
    private double getNewVolume(double weightWithSampleVolume, double tubeTareWeight, String tubeBarcode) throws ServerException {
        if (tubeTareWeight < 0) {
            weightWithSampleVolume = 0;
        }
        if (weightWithSampleVolume - tubeTareWeight <= 0) {
            logError(String.format("Calculated volume for tube '%s' is less than or equal to 0. Volume will be set to 0.", tubeBarcode));
            clientCallback.displayWarning(String.format("Calculated volume for tube '%s' is less than or equal to 0. Volume will be set to 0.", tubeBarcode));
            return 0.0;
        } else {
            return weightWithSampleVolume - tubeTareWeight;
        }
    }

    /**
     * Method to get values from cells of row in file.
     *
     * @param row
     * @param header
     * @param micronicTubeRecordsInLims
     * @return
     * @throws ServerException
     * @throws NotFound
     * @throws RemoteException
     */
    private Map<String, Object> getTubeValueMapFromFileRowData(String row, Map<String, Integer> header, List<DataRecord> micronicTubeRecordsInLims) throws ServerException {
        Map<String, Object> tubeValuesMap = new HashMap<>();
        String storageLocationBarcode = row.split(",")[header.get("Rack")];
        char tubeRowPosition = row.split(",")[header.get("Tube")].charAt(0);
        String tubeColumnPosition = getMicronicTubeColumnPosition(row, header);
        String micronicTubeBarcode = row.split(",")[header.get("Barcode")];
        String containerType = "Micronic Rack";
        double tubeTareWeight = getTubeTareWeight(micronicTubeBarcode, micronicTubeRecordsInLims);
        double tubeWeightWithSampleVolume = Double.parseDouble(row.split(",")[header.get("Weight")]);
        double newSampleVolumeInTube = getNewVolume(tubeWeightWithSampleVolume, tubeTareWeight, micronicTubeBarcode);
        tubeValuesMap.put("StorageLocationBarcode", storageLocationBarcode);
        tubeValuesMap.put("RowPosition", tubeRowPosition);
        tubeValuesMap.put("ColPosition", tubeColumnPosition);
        tubeValuesMap.put("MicronicTubeBarcode", micronicTubeBarcode);
        tubeValuesMap.put("ContainerType", containerType);
        tubeValuesMap.put("Volume", newSampleVolumeInTube);
        return tubeValuesMap;
    }

    /**
     * Method to read MicronicTube volume data from file data.
     *
     * @param fileData
     * @param micronicTubeRecordsInLims
     * @param Samples
     * @return
     * @throws ServerException
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Map<String, Object>> readMicronicInfoFromFileData(String[] fileData, List<DataRecord> micronicTubeRecordsInLims, List<DataRecord> Samples) throws ServerException {
        List<String> sampleMicronicBarcodes = getMicronicBarcodesFromRecordsInLims(Samples);
        Map<String, Integer> header = parseFileHeader(fileData);
        List<Map<String, Object>> micronicTubeData = new ArrayList<>();
        for (int i = 1; i < fileData.length; i++) {

            Map<String, Object> newTube;
            String row = fileData[i];
            String tubeBarcodeInRow = row.split(",")[header.get("Barcode")];
                if (rowHasAllValues(row) && sampleMicronicBarcodes.contains(tubeBarcodeInRow) && !isMicronicTubeAssignedToSample(tubeBarcodeInRow, micronicTubeRecordsInLims)) {
                    newTube = getTubeValueMapFromFileRowData(row, header, micronicTubeRecordsInLims);
                    micronicTubeData.add(newTube);
                } else {
                    logError(String.format("Micronic barcode %s in file is not present on any of attached samples. It will be ignored.", tubeBarcodeInRow));
                    clientCallback.displayWarning(String.format("Micronic barcode %s in file is not present on any of attached samples. It will be ignored.", tubeBarcodeInRow));
                }
            if (micronicTubeData.isEmpty()) {
                logError("Uploaded file does not contain micronic barcodes matching attached samples. Please check the file.");
                clientCallback.displayError("Uploaded file does not contain micronic barcodes matching attached samples. Please check the file.");
            }
        }
        return micronicTubeData;
    }

    /**
     * Method to check if MicronicTubeBarcode is already assigned to another sample.
     * @param barcode
     * @param micronicTubeRecordsInLims
     * @return
     */
    private boolean isMicronicTubeAssignedToSample(String barcode, List<DataRecord> micronicTubeRecordsInLims){
        try {
            for (DataRecord micronicRecord : micronicTubeRecordsInLims) {
                if (micronicRecord.getStringVal(("MicronicTubeBarcode"), user).equals(barcode) && micronicRecord.getBooleanVal("AssignedToSample", user)) {
                    logError(String.format("Micronic tube '%s' in file is assigned to another sample. It will be ignored.", barcode));
                    clientCallback.displayError(String.format("Micronic tube '%s' in file is assigned to another sample. It will be ignored.", barcode));
                    return true;
                }
            }
        } catch (RemoteException re) {
            logError(String.format("RemoteException -> Error validating if MicronicTube with barcode %s is linked to another sample:\n%s", barcode, ExceptionUtils.getStackTrace(re)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound ->Error validating if MicronicTube with barcode %s is linked to another sample:\n%s", barcode, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException ->Error validating if MicronicTube with barcode %s is linked to another sample:\n%s", barcode, ExceptionUtils.getStackTrace(se)));
        }
        return false;
    }

    private void setMicronicTubeAsAssignedInLims(String tubeBarcode, List<DataRecord> micronicTubeRecordsInLims) throws NotFound, RemoteException, IoError, InvalidValue {
        for (DataRecord micronicRecord : micronicTubeRecordsInLims) {
            if (Objects.equals(micronicRecord.getStringVal("MicronicTubeBarcode", user), tubeBarcode)) {
                micronicRecord.setDataField("AssignedToSample", true, user);
            }
        }
    }

    /**
     * Method to set Volume and Storage information on Samples.
     * @param samples
     * @param recordsReadFromFile
     * @param micronicTubeRecordsInLims
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws IoError
     * @throws InvalidValue
     */
    private void assignNewVolumeAndStorageToSamples(List<DataRecord> samples, List<Map<String, Object>> recordsReadFromFile, List<DataRecord> micronicTubeRecordsInLims) {
        String sampleTubeBarcodeInLims;
        for (DataRecord sampleRecord : samples) {
            try {
                sampleTubeBarcodeInLims = sampleRecord.getStringVal("MicronicTubeBarcode", user);
                boolean sampleBarcodeFoundInFile = false;
                if (!StringUtils.isEmpty(sampleTubeBarcodeInLims)) {
                    for (Map<String, Object> fileRecord : recordsReadFromFile) {
                        String tubeBarcodeInFileRecord = fileRecord.get("MicronicTubeBarcode").toString();
                        if (tubeBarcodeInFileRecord.equals(sampleTubeBarcodeInLims) && getTubeTareWeight(tubeBarcodeInFileRecord, micronicTubeRecordsInLims) > 0) {
                            sampleRecord.setFields(fileRecord, user);
                            setMicronicTubeAsAssignedInLims(sampleTubeBarcodeInLims, micronicTubeRecordsInLims);
                            sampleBarcodeFoundInFile = true;
                        }
                    }
                }
                if (!sampleBarcodeFoundInFile && !StringUtils.isEmpty(sampleTubeBarcodeInLims)) {
                    logError(String.format("File records does not contain Sample tube barcode: %s", sampleTubeBarcodeInLims));
                    clientCallback.displayWarning(String.format("File records does not contain Sample tube barcode: %s", sampleTubeBarcodeInLims));
                }
            } catch (InvalidValue invalidValue) {
                logError(String.format("InvalidValue Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sampleRecord.getRecordId(), ExceptionUtils.getStackTrace(invalidValue)));
            } catch (IoError ioError) {
                logError(String.format("IoError Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sampleRecord.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
            } catch (ServerException e) {
                logError(String.format("ServerException -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sampleRecord.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sampleRecord.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while setting Volume and Storage value for sample with recordId %d:\n%s", sampleRecord.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
        }
    }
}

