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

import java.rmi.RemoteException;
import java.util.*;

public class PlateSampleVolumeImporter extends DefaultGenericPlugin {
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin"};
    private List<String> expectedFileHeaderValues = Arrays.asList("RACKID", "TUBE", "SAMPLES", "STATUS", "VOLMED", "VOLAVG", "VOLSTDEV");
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
        } catch (Exception e) {
            logInfo(Arrays.toString(e.getStackTrace()));
        }
        return false;
    }

    @Override
    public boolean onTaskTableToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("UPDATE VOLUME FOR SAMPLES ON PLATE");
        } catch (Exception e) {
            logInfo(Arrays.toString(e.getStackTrace()));
        }
        return false;
    }

    public PluginResult run() throws ServerException {
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

            Map<String, Integer> headerValuesMap = commonMethods.getCsvHeaderValueMap(fileData);
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
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while parsing the plate volume data file:\n%s", e));
            logError(Arrays.toString(e.getStackTrace()));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private boolean isValidCsvFile(String fileName) throws ServerException {
        if (!commonMethods.isCsvFile(fileName)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a .csv file.", fileName));
            return false;
        }
        return true;
    }

    private boolean csvFileHasValidData(List<String> fileData, String fileName) throws ServerException {
        if (!commonMethods.csvFileHasData(fileData)) {
            clientCallback.displayError(String.format("Uploaded file '%s' is empty.", fileName));
            return false;
        }
        if (!commonMethods.csvFileHasValidHeader(fileData, expectedFileHeaderValues)) {
            clientCallback.displayError(String.format("File '%s' has invalid header values. File header should have '%s'", fileName, expectedFileHeaderValues.toString()));
            return false;
        }
        if (!commonMethods.allRowsInCsvFileHasValidData(fileData, headerWithMustHaveValuesInRow)) {
            clientCallback.displayError(String.format("Some of the rows in file '%s' have missing data under column %s.\n" +
                    "All rows in file must have values under these columns.", fileName, headerWithMustHaveValuesInRow.toString()));
            return false;
        }
        return true;
    }

    private double getVolumeFromRowData(String[] rowData, Map<String, Integer> header) throws ServerException {
        double volume = Double.parseDouble(rowData[header.get("VOLMED")]);
        String plateId = rowData[header.get("RACKID")].trim();
        String wellPosition = rowData[header.get("TUBE")].trim();
        if (volume < 0) {
            clientCallback.displayWarning(String.format("Volume for plate %s, well %s is less than 0.0. It will be set to 0.0 ", plateId, wellPosition));
            return 0.0;
        }
        return volume;
    }

    private List<Map<String, Object>> getRecordsFromFile(List<String> fileData, Map<String, Integer> header) throws ServerException {
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

    private boolean fileDataHasDuplicateRecords(List<String> fileData, Map<String, Integer> header, String filename) throws ServerException {
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

    private void updateSampleVolumes(List<DataRecord> samples, List<Map<String, Object>> volumeRecordsFromFile) throws NotFound, RemoteException, ServerException, IoError, InvalidValue {
        for (DataRecord sample : samples) {
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
        }
    }
}