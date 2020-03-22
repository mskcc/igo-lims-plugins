package com.velox.sloan.cmo.workflows.labmedicine;

import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.util.*;

/**
 * This plugin is designed to read the 9 columns of interest in the csv file from the output of the qPCR machine.
 */
public class PCRSamplesImporter extends DefaultGenericPlugin {
    private final String LAB_MEDICINE_TRANSFER = "LabMedicineTransfer"; // TODO

    public PCRSamplesImporter() {
        setTableToolbar(true);
        setLine1Text("Upload qPCR CSV File");
        setLine2Text("Samples");
        setDescription("Use this button to import qPCR results from a .csv file.");
    }

    public boolean onTableToolbar(String dataTypeName) {
        return LAB_MEDICINE_TRANSFER.equals(dataTypeName);
    }

    public static class ResultFromQPCR {
        public String sample; // IGO ID
        public String target; // N1, N2, RP
        public String cq; // "Undetermined" or float value like 26.385

        public ResultFromQPCR(String sample, String target, String cq) {
            this.sample = sample;
            this.target = target;
            this.cq = cq;
        }
    }

    public PluginResult run() throws ServerException {
        try {
            String csvFilePath = clientCallback.showFileDialog("Upload the csv file with qPCR results.", null);
            if (csvFilePath == null) {
                logInfo("Path to csv file is empty or file not uploaded and process cancelled by the user.");
                return new PluginResult(false);
            }
            if (!isValidCsvFile(csvFilePath)) {
                return new PluginResult(false);
            }
            byte [] csvBytes = clientCallback.readBytes(csvFilePath);
            String entireFile = new String(csvBytes);
            String [] allRows = entireFile.split("\n");

            List<ResultFromQPCR> qPCRRecordsList = parseAllRows(allRows);

            List<Map<String, Object>> qPCRRecords = new ArrayList<>();
//            List<Map<String, Object>> thoracicBankSampleRecords = getThoracicBankedSampleRecordsFromFile(sheet, headerNames);
//            long mostRecentRecordId = getMostRecentlyAddedLabMedicineRecordId();
//            List<DataRecord> labMedicineRecords = dataRecordManager.queryDataRecords(LAB_MEDICINE_TRANSFER, "RecordId= '" + mostRecentRecordId + "'", user);
//            if (labMedicineRecords.isEmpty()) {
//                clientCallback.displayError("There are no records under '" + LAB_MEDICINE_TRANSFER + "'. Please create a record under '" + LAB_MEDICINE_TRANSFER + "' and then try again.");
//                return new PluginResult(false);
//            }

            clientCallback.displayInfo(String.format("Added %d new ThoracicBankTransfer sample records.", qPCRRecords.size()));
        } catch (Exception e) {
            String errMsg = String.format("Error reading qPCR Sample Information", e);
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    protected static List<ResultFromQPCR> parseAllRows(String[] allRows) {
        List<ResultFromQPCR> resultFromQPCRList = new ArrayList<>();
        // skip the first row with the csv headers
        for (int i = 1; i < allRows.length; i++) {
            String row = allRows[i];
            String[] rowValues = row.split(",");

            // if the CSV format changes from the example, these column numbers should be updated
            String sample1 = rowValues[1];
            String target1 = rowValues[2];
            String cq1 = rowValues[4];
            resultFromQPCRList.add(new ResultFromQPCR(sample1, target1, cq1));

            String sample2 = rowValues[9];
            String target2 = rowValues[10];
            String cq2 = rowValues[12];
            resultFromQPCRList.add(new ResultFromQPCR(sample2, target2, cq2));

            String sample3 = rowValues[17];
            String target3 = rowValues[18];
            String cq3 = rowValues[20];
            resultFromQPCRList.add(new ResultFromQPCR(sample3, target3, cq3));
        }
        return resultFromQPCRList;
    }

    /**
     * Method to check if csv file has the valid extension.
     *
     * @param fileName
     * @return true/false
     * @throws ServerException
     */
    private boolean isValidCsvFile(String fileName) throws ServerException {
        if (!fileName.toLowerCase().endsWith(".csv")) {
            String errMsg = String.format("File '%s' is invalid file type. Only csv files with the extension .csv are accepted.", fileName);
            logError(errMsg);
            clientCallback.displayError(errMsg);
            return false;
        }
        return true;
    }
}