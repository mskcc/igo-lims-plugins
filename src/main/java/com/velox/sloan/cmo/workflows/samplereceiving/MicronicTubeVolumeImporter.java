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
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;

public class MicronicTubeVolumeImporter extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin"};
    private MicronicTubeVolumeDataReader volumeDataReader = new MicronicTubeVolumeDataReader();

    public MicronicTubeVolumeImporter() {
        setTaskTableToolbar(true);
        setTaskFormToolbar(true);
        setLine1Text("Calculate Volumes");
        setLine2Text("for micronics");
        setDescription("Upload file to calculate volumes using micronic tube tare weights.");
        setUserGroupList(permittedUsers);
    }

    public boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().keySet().contains("UPDATE MICRONIC VOLUMES USING TARE WEIGHT");
    }

    @Override
    public boolean onTaskFormToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            logInfo("creating form button");
            if (activeTask.getTask().getTaskOptions().keySet().contains("UPDATE MICRONIC VOLUMES USING TARE WEIGHT")) {
                return true;
            }
        } catch (Exception e) {
            logInfo(Arrays.toString(e.getStackTrace()));
        }
        return false;
    }

    @Override
    public boolean onTaskTableToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            if (activeTask.getTask().getTaskOptions().keySet().contains("UPDATE MICRONIC VOLUMES USING TARE WEIGHT")) {
                return true;
            }
        } catch (Exception e) {
            logInfo(Arrays.toString(e.getStackTrace()));
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
            logError(String.format("cannot read data from file."), e);
            clientCallback.displayWarning(String.format("%s", e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private boolean isValidCsvFile(String fileName) throws ServerException {
        if (!volumeDataReader.isValidFile(fileName)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            logError(String.format("Uploaded file '%s' is not a '.csv' file", fileName));
            return false;
        }
        return true;
    }

    private boolean fileHasData(String[] fileData, String fileName) throws ServerException {
        if (!volumeDataReader.fileHasData(fileData)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is empty. Please check the file.", fileName));
            logError(String.format("Uploaded file '%s' is empty. Please check the file.", fileName));
            return false;
        }
        return true;
    }

    private boolean fileHasValidHeader(String[] fileData, String fileName) throws ServerException {
        if (!volumeDataReader.isValidHeader(fileData)) {
            clientCallback.displayError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            logError(String.format("Uploaded file '%s' has incorrect header. Please check the file", fileName));
            return false;
        }
        return true;
    }

    private boolean rowHasAllValues(String rowData) {
        return volumeDataReader.rowInFileHasValues(rowData);
    }

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

    private boolean isValidFile(String[] fileData, String fileName) throws ServerException {
        return isValidCsvFile(fileName) && fileHasData(fileData, fileName) && fileHasValidHeader(fileData, fileName) && allRowsHaveValidValues(fileData, fileName);
    }

    private Map<String, Integer> parseFileHeader(String[] fileData) {
        return volumeDataReader.getHeaderValues(fileData);
    }

    private List<String> getMicronicBarcodesFromRecordsInLims(List<DataRecord> recordsInLims) throws NotFound, RemoteException {
        List<String> tubeBarcodes = new ArrayList<>();
        for (DataRecord record : recordsInLims) {
            String barcode = record.getStringVal(("MicronicTubeBarcode"), user);
            if (!StringUtils.isEmpty(barcode)) {
                tubeBarcodes.add(barcode);
            }
        }
        return tubeBarcodes;
    }

    private String getMicronicTubeColumnPosition(String row, Map<String, Integer> header) {
        return volumeDataReader.getColumnPosition(row, header);
    }

    private double getTubeTareWeight(String tubeBarcode, List<DataRecord> micronicTubeRecordsInLims) throws NotFound, RemoteException, ServerException {
        double tareWeight = 0.0;
        boolean barcodeFound = false;
        for (DataRecord record : micronicTubeRecordsInLims) {
            if (Objects.equals(record.getStringVal("MicronicTubeBarcode", user), tubeBarcode)) {
                tareWeight = record.getDoubleVal("MicronicTubeWeight", user);
                barcodeFound = true;
            }
        }
        if (!barcodeFound) {
            logError(String.format("TARE WEIGHT not found for sample with following Micronic Tube Barcode: %s", tubeBarcode));
            clientCallback.displayWarning(String.format("TARE WEIGHT not found for sample with Micronic Tube Barcode: %s", tubeBarcode));
        }
        return tareWeight;
    }

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

    private Map<String, Object> getTubeValueMapFromFileRowData(String row, Map<String, Integer> header, List<DataRecord> micronicTubeRecordsInLims) throws ServerException, NotFound, RemoteException {
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

    private List<Map<String, Object>> readMicronicInfoFromFileData(String[] fileData, List<DataRecord> micronicTubeRecordsInLims, List<DataRecord> Samples) throws ServerException, NotFound, RemoteException {
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
        }
        if (micronicTubeData.isEmpty()) {
            logError("Uploaded file does not contain micronic barcodes matching attached samples. Please check the file.");
            clientCallback.displayError("Uploaded file does not contain micronic barcodes matching attached samples. Please check the file.");
        }
        return micronicTubeData;
    }

    private boolean isMicronicTubeAssignedToSample(String barcode, List<DataRecord> micronicTubeRecordsInLims) throws NotFound, RemoteException, ServerException {
        for (DataRecord micronicRecord : micronicTubeRecordsInLims) {
            if (micronicRecord.getStringVal(("MicronicTubeBarcode"), user).equals(barcode) && micronicRecord.getBooleanVal("AssignedToSample", user)) {
                logError(String.format("Micronic tube '%s' in file is assigned to another sample. It will be ignored.", barcode));
                clientCallback.displayError(String.format("Micronic tube '%s' in file is assigned to another sample. It will be ignored.", barcode));
                return true;
            }
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

    private void assignNewVolumeAndStorageToSamples(List<DataRecord> samples, List<Map<String, Object>> recordsReadFromFile, List<DataRecord> micronicTubeRecordsInLims) throws NotFound, RemoteException, ServerException, IoError, InvalidValue {
        String sampleTubeBarcodeInLims;
        for (DataRecord sampleRecord : samples) {
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
        }
    }
}

