package com.velox.sloan.cmo.workflows.dmpbankedsample;

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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


public class DmpToBankedSampleImporter extends DefaultGenericPlugin {
    private final ArrayList<String> excelFileHeaderValues =
            new ArrayList<>(Arrays.asList("Tracking ID", "PI Name", "Study of Title",
                    "Barcode/Plate ID", "Well Position", "DMP ID", "Investigator Sample ID",
                    "Nucleic Acid Type (Library or DNA)", "Preservation (FFPE or Blood)", "Volume (ul)",
                    "Concentration (ng/ul)", "Index", "Index Sequence", "Collection Year", "Tissue Site", "Tumor Type", "Sex"));

    private DmpToBankedSampleDataReader dataReader = new DmpToBankedSampleDataReader();

    public DmpToBankedSampleImporter() {
        setActionMenu(true);
        setDescription("Import DMP Excel to Banked Sample");
        setLine1Text("DEV: DMP to Banked Sample Import");
    }

    public PluginResult run() throws ServerException {
        try {

            String dmpExcelPath = clientCallback.showFileDialog("Please upload the DMP Excel file.", null);

            if (StringUtils.isBlank(dmpExcelPath)) {
                logInfo("DMP to Banked Sample: Path to excel file is empty. Or file not uploaded and process canceled by the user.");
                return new PluginResult(false);
            }
            Sheet sheet = getSheetFromFile(dmpExcelPath);

            if (!isValidExcelFile(sheet, excelFileHeaderValues, dmpExcelPath)) {
                return new PluginResult(false);
            } else {
                logInfo("DMP to Banked Sample: uploaded excel file valid.");
            }

            String iLabsId = clientCallback.showInputDialog("iLabs ID (optional, format: IGO-XXXXXX: ");

            Map<String, Integer> headerNames = parseHeader(sheet, excelFileHeaderValues);

            ArrayList<Map<String, Object>> dmpBankSampleRecords = getDmpBankedSampleRecordsFromFile(sheet, headerNames, iLabsId);

            dataRecordManager.addDataRecords("BankedSample", dmpBankSampleRecords, user);
            dataRecordManager.storeAndCommit(user + String.format(" added %d new Banked Sample records.", dmpBankSampleRecords.size()), null, user);
            clientCallback.displayInfo(String.format("Added %d new Banked Sample records.", dmpBankSampleRecords.size()));
            logInfo(String.format("DMP to Banked Sample: Added %d new Banked Sample records.", dmpBankSampleRecords.size()));

        } catch (Exception e) {
            clientCallback.displayError(String.format("Error reading DMP Information", e));
            logError(String.format("DMP to Banked Sample: Error reading DMP Information"), e);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    private Sheet getSheetFromFile(String excelFilePath) throws IOException, InvalidFormatException, ServerException {
        InputStream input = new ByteArrayInputStream(clientCallback.readBytes(excelFilePath));
        Workbook workbook = WorkbookFactory.create(input);
        return workbook.getSheetAt(0);
    }

    private boolean isValidExcelFile(Sheet sheet, ArrayList<String> expectedHeaderValues, String filePath) throws ServerException {

        if (!dataReader.excelFileHasData(sheet)) {
            logError(String.format("File '%s' is invalid file type. Only excel file with '.xls' or '.xlsx' extensions are acceptable.", filePath));
            clientCallback.displayError(String.format("File '%s' is invalid file type. Only excel file with '.xls' or '.xlsx' extensions are acceptable.", filePath));
            return false;
        }

        if (!dataReader.excelFileHasValidHeader(sheet, expectedHeaderValues)) {
            logError(String.format("File '%s' does not match minimum expected DMP column names.", filePath));
            clientCallback.displayError(String.format("File '%s' does not match expected DMP column names: '%s'", filePath,excelFileHeaderValues));
            return false;
        }
        return true;
    }

    private Map<String, Integer> parseHeader(Sheet sheet, ArrayList<String> headerValues) {
        return dataReader.parseExcelFileHeader(sheet, headerValues);
    }

    private ArrayList<Map<String, Object>> getDmpBankedSampleRecordsFromFile(Sheet sheet, Map<String, Integer> fileHeader, String iLabsId) throws IoError, IOException, NotFound, ServerException {
        ArrayList<Map<String, Object>> bankedSampleRecords = null;
        try {
            bankedSampleRecords = dataReader.readDmpBankedSampleRecordsFromFile(sheet, fileHeader, iLabsId);
        } catch (FileNotFoundException e) {
            logError("DMP to Banked Sample: Something went wrong translating '%s' using OncoTree. Please see the logs for more info.");
            clientCallback.displayError("Something went wrong translating '%s' using OncoTree. Please see the logs for more info.");
            e.printStackTrace();
        }
        return bankedSampleRecords;
    }



}