package com.velox.sloan.cmo.workflows.labmedicine;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.*;

/*
 @description This plugin is designed to read Thoracic Banked Sample information from an excel file and assign unique Id's to the samples.
 */
public class ThoracicBankedSamplesImporter extends DefaultGenericPlugin {
    private final List<String> excelFileHeaderValues = Arrays.asList("Accession#", "DrawDate", "DrawTime", "Pi", "TubeType", "#ofTubes", "BoxDate", "SpecimenType", "Aliquot#", "Comments");
    private final String LAB_MEDICINE_TRANSFER = "LabMedicineTransfer";
    private ThoracicBankedSampleDataReader dataReader = new ThoracicBankedSampleDataReader();

    public ThoracicBankedSamplesImporter() {
        setTableToolbar(true);
        setLine1Text("Upload Thoracic Bank");
        setLine2Text("Samples");
        setDescription("Use this button to import Thoracic Bank Samples from an excel file.");
    }

    public boolean onTableToolbar(String dataTypeName) {
        return LAB_MEDICINE_TRANSFER.equals(dataTypeName);
    }

    public PluginResult run() {
        try {
            String excelFilePath = clientCallback.showFileDialog("Upload File with Thoracic bank sample information.", null);
            if (StringUtils.isBlank(excelFilePath)) {
                logInfo("Path to excel file is empty. Or file not uploaded and process canceled by the usr.");
                return new PluginResult(false);
            }
            Sheet sheet = getSheetFromFile(excelFilePath);
            if (!isValidFile(sheet, excelFileHeaderValues, excelFilePath)) {
                return new PluginResult(false);
            }
            Map<String, Integer> headerNames = parseHeader(sheet, excelFileHeaderValues);
            List<Map<String, Object>> thoracicBankSampleRecords = getThoracicBankedSampleRecordsFromFile(sheet, headerNames);
            long mostRecentRecordId = getMostRecentlyAddedLabMedicineRecordId();
            List<DataRecord> labMedicineRecords = dataRecordManager.queryDataRecords(LAB_MEDICINE_TRANSFER, "RecordId= '" + mostRecentRecordId + "'", user);
            if (labMedicineRecords.isEmpty()) {
                clientCallback.displayError("There are no records under '" + LAB_MEDICINE_TRANSFER + "'. Please create a record under '" + LAB_MEDICINE_TRANSFER + "' and then try again.");
                return new PluginResult(false);
            }
            if (!shouldAddChildRecordsToLabMedicineRecord(labMedicineRecords.get(0))) {
                return new PluginResult(false);
            } else {
                labMedicineRecords.get(0).addChildren("ThoracicBankTransfer", thoracicBankSampleRecords, user);
                dataRecordManager.storeAndCommit(String.format("Added %d ThoracicBankTransfer samples", thoracicBankSampleRecords.size()), user);
                clientCallback.displayInfo(String.format("Added %d new ThoracicBankTransfer sample records.", thoracicBankSampleRecords.size()));
            }
        } catch (Exception e) {
            logError(String.format("Error reading Thoracic Sample Information\n"), e);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private Sheet getSheetFromFile(String excelFilePath) throws IOException, InvalidFormatException, ServerException {
        InputStream input = new ByteArrayInputStream(clientCallback.readBytes(excelFilePath));
        Workbook workbook = WorkbookFactory.create(input);
        return workbook.getSheetAt(0);
    }

    private boolean isValidFile(Sheet sheet, List<String> headerValues, String fileName) throws ServerException {
        if (!dataReader.excelFileHasData(sheet)) {
            logError(String.format("uploaded File '%s' is Empty. File must have more than 1 rows with data.", fileName));
            clientCallback.displayError(String.format("uploaded File '%s' is Empty. File must have more than 1 rows with data.", fileName));
            return false;
        }
        if (!dataReader.excelFileHasValidHeader(sheet, headerValues, fileName)) {
            logError(String.format("Uploaded file '%s' Has invalid header row.", fileName));
            clientCallback.displayError(String.format("Uploaded file '%s' Has invalid header row.", fileName));
            return false;
        }
        if (!dataReader.isValidExcelFile(fileName)) {
            logError(String.format("File '%s' is invalid file type. Only excel file with '.xls' or '.xlsx' extensions are acceptable.", fileName));
            clientCallback.displayError(String.format("File '%s' is invalid file type. Only excel file with '.xls' or '.xlsx' extensions are acceptable.", fileName));
            return false;
        }
        return true;
    }

    private Map<String, Integer> parseHeader(Sheet sheet, List<String> headerValues) {
        return dataReader.parseExcelFileHeader(sheet, headerValues);
    }

    private List<Map<String, Object>> getThoracicBankedSampleRecordsFromFile(Sheet sheet, Map<String, Integer> fileHeader) throws IoError, RemoteException, NotFound {
        List<String> existingUuids = getExistingUuids();
        return dataReader.readThoracicBankedSampleRecordsFromFile(sheet, fileHeader, existingUuids);
    }

    private long getMostRecentlyAddedLabMedicineRecordId() throws ServerException, NotFound, RemoteException, IoError {
        List<DataRecord> listOfRecords = dataRecordManager.queryDataRecords(LAB_MEDICINE_TRANSFER, null, user);
        List<Long> listOfLabMedicineRecordIds = new ArrayList<>();
        for (DataRecord record : listOfRecords) {
            Long recordId = record.getLongVal("RecordId", user);
            listOfLabMedicineRecordIds.add(recordId);
        }
        String errorMessage = "There are no records under " + LAB_MEDICINE_TRANSFER + ". Please create a record under '" + LAB_MEDICINE_TRANSFER + "' and then try again.";
        if (listOfLabMedicineRecordIds.isEmpty()) {
            clientCallback.displayError(errorMessage);
            throw new NotFound(errorMessage);
        }
        return Collections.max(listOfLabMedicineRecordIds);
    }

    private List<String> getExistingUuids() throws RemoteException, NotFound, IoError {
        List<String> existingUuids = new ArrayList<>();
        List<DataRecord> thoracicBankedSamples = dataRecordManager.queryDataRecords("ThoracicBankTransfer", null, user);
        for (DataRecord record : thoracicBankedSamples) {
            existingUuids.add(record.getStringVal("Uuid", user));
        }
        return existingUuids;
    }

    private String getUuidForExistingChildRecords(List<DataRecord> existingRecord) {
        StringBuilder existingRecordsUuids = new StringBuilder();
        for (DataRecord record : existingRecord) {
            try {
                existingRecordsUuids.append(record.getStringVal("Uuid", user)).append("\n");
            } catch (NotFound | RemoteException e) {
                logError(String.format("Error retrieving UUID for existing records."),e);
            }
        }
        return existingRecordsUuids.toString();
    }

    private boolean shouldAddChildRecordsToLabMedicineRecord(DataRecord mostRecentLabMedicineRecord) throws IoError, RemoteException, NotFound, ServerException {
        boolean addChildRecords = true;
        if (mostRecentLabMedicineRecord.hasChildren(user)) {
            Long recordId = mostRecentLabMedicineRecord.getLongVal("RecordId", user);
            logInfo(String.format("Found child samples on record Id '%d", recordId));
            List<DataRecord> childRecords = mostRecentLabMedicineRecord.getDescendantsOfType("ThoracicBankTransfer", user);
            addChildRecords = clientCallback.showOkCancelDialog(String.format("The LabMedicine DataRecordId '%d' has child samples", recordId)
                    , getUuidForExistingChildRecords(childRecords) + "\nDo you want to continue adding samples to the same Datarecord?");
        }
        return addChildRecords;
    }

}