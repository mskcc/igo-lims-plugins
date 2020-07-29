package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

import static com.velox.sloan.cmo.workflows.utils.DataAccessUtils.*;

/**
 * This plugin is solely designed to create new Request and new Samples under a project 10858 for COVID Testing.
 * The CSV file that will be used to import the samples into LIMS must have information in following format:
 * Accession Number, Well ID.
 *
 * @author sharmaa1
 */
public class Covid19SampleImporter extends DefaultGenericPlugin {

    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private final List<String> REQUIRED_FILE_HEADERS = Arrays.asList("Accession Number", "Well ID");
    private final String COVID_REQUEST_ID = "10858";

    public Covid19SampleImporter() throws ServerException {
       setActionMenu(true);
       setLine1Text("Import COVID19 Samples");
       setDescription("Plugin to import new COVID-19 Samples into LIMS.");
       String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin", "Group Leaders", "Lab Managers", "Lab Techs", "NA Team", "Path Extraction Techs", "Sequencing Techs"};
       setUserGroupList(permittedUsers);
   }

    @Override
    public PluginResult run() throws ServerException {
        try {
            String csvFilePath = clientCallback.showFileDialog("Upload file with Sample Information", null);
            if (csvFilePath == null) {
                logInfo("Path to csv file is empty or file not uploaded and process cancelled by the user.");
                return new PluginResult(false);
            }

            if (!utils.isCsvFile(csvFilePath)) {
                clientCallback.displayError(String.format("Not a valid csv file %s.", csvFilePath));
                logError(String.format("Not a valid csv file %s.", csvFilePath));
                return new PluginResult(false);
            }

            List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(csvFilePath));
            if (!utils.csvFileHasData(fileData)){
                clientCallback.displayError(String.format("The uploaded file %s does not have data. Please check the file.", csvFilePath));
                logError(String.format("The uploaded file %s does not have data. Please check the file.", csvFilePath));
                return new PluginResult(false);
            }

            if (!utils.csvFileHasValidHeader(fileData, REQUIRED_FILE_HEADERS)){
                clientCallback.displayError(String.format("The uploaded file %s does not have valid headers. The required headers are:\n%s",
                        csvFilePath, utils.convertListToString(REQUIRED_FILE_HEADERS)));
                logError(String.format("The uploaded file %s does not have valid headers. The required headers are:\n%s",
                        csvFilePath, utils.convertListToString(REQUIRED_FILE_HEADERS)));
                return new PluginResult(false);
            }
            Map<String, Integer> headerValuesMap = utils.getCsvHeaderValueMap(fileData);
            List<Map<String,Object>> parsedSampleData = parseFileDataToSampleData(fileData, headerValuesMap);
            List<DataRecord> requests = dataRecordManager.queryDataRecords("Request", "RequestId = '" + COVID_REQUEST_ID + "'" , user);

            if (requests.size() == 1 && !hasDuplicateAccessionNumber(parsedSampleData) && isValidFileData(fileData, headerValuesMap)){
                requests.get(0).addChildren("Sample", parsedSampleData, user);
                clientCallback.displayInfo("Adding new Sample to request 10858........");
                dataRecordManager.storeAndCommit(String.format("Added %d new Samples in LIMS.", parsedSampleData.size()), null, user);
                clientCallback.displayInfo(String.format("Process complete.\n\nAdded %d new Sample to request 10858.", parsedSampleData.size()));
            }
            if (requests.size() != 1){
                clientCallback.displayError("Error: Either the Request 10858 does not exist, or there are more than one Requests with same RequestId 10858.");
                logError("Error: Either the Request 10858 does not exist, or there are more than one Requests with same RequestId 10858.");
                return new PluginResult(false);
            }

        } catch (ServerException e){
            String message = String.format("Server Exception while importing new COVID-19 Samples into LIMS with message: %s", e.getMessage());
            logError(message, e);
            return new PluginResult(false);
        } catch (IOException e) {
            String message = String.format("IOException while importing new COVID-19 Samples into LIMS with message: %s", e.getMessage());
            logError(message, e);
            return new PluginResult(false);
        } catch (IoError | NotFound | AlreadyExists | InvalidValue e) {
            String message = String.format("Data Record Access exception while importing new COVID-19 Samples into LIMS with message: %s", e.getMessage());
            logError(message, e);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to validate file data.
     * @param fileData
     * @param headerValuesMap
     * @return
     * @throws ServerException
     */
    private boolean isValidFileData(List<String> fileData, Map<String, Integer> headerValuesMap) throws ServerException {
        Set<String> accessionNums = new HashSet<>();
        for (int i=1; i<fileData.size(); i++){
            List<String> rowVals = Arrays.asList(fileData.get(i).trim().split(","));
            String accessNm = rowVals.get(headerValuesMap.get("Accession Number"));
            String well = rowVals.get(headerValuesMap.get("Well ID"));
            if (rowVals.size() < 2 || StringUtils.isBlank(accessNm) || StringUtils.isBlank(well)){
                clientCallback.displayError(String.format("Invalid Row values in uploaded file: %s.\nRow values must have Accession Number and Well ID values.", fileData.get(i)));
                return false;
            }
            if (!accessionNums.add(accessNm)){
                clientCallback.displayWarning(String.format("Found duplicate Accession Number in uploaded file: %s.", accessNm));
            }
            if(well.length() < 2 || well.length() > 3){
                clientCallback.displayError(String.format("Invalid well position in uploaded file: %s.", well));
                return false;
            }
        }
        return true;
    }

    /**
     * Method to parse
     * @param fileData
     * @param headrValuesMap
     * @return List<Map<String, Object>>
     */
   private List<Map<String, Object>> parseFileDataToSampleData(List<String> fileData, Map<String, Integer> headrValuesMap){
        List<Map<String, Object>> parsedData = new ArrayList<>();
        Integer nextSampleNumer = getNextSampleNumber();
        for (int i = 1; i< fileData.size(); i++){
            List<String> rowValues = Arrays.asList(fileData.get(i).split(","));
            if (rowValues.size()>0){
                Map<String, Object> sampleVals = new HashMap<>();
                String sampleId = COVID_REQUEST_ID + "_" + nextSampleNumer;
                String sampleName = rowValues.get(headrValuesMap.get("Accession Number"));
                String row = rowValues.get(headrValuesMap.get("Well ID")).substring(0,1);
                String column = rowValues.get(headrValuesMap.get("Well ID")).substring(1);
                sampleVals.put("SampleId", sampleId);
                sampleVals.put("OtherSampleId", sampleName);
                sampleVals.put("AltId", sampleId);
                sampleVals.put("Volume", 3000);
                sampleVals.put("ExemplarSampleType", "other");
                sampleVals.put("ExemplarSampleStatus", "Awaiting Processing");
                sampleVals.put("ContainerType", "Tube");
                sampleVals.put("IsPooled", false);
                sampleVals.put("RowPosition", row);
                sampleVals.put("ColPosition", column);
                parsedData.add(sampleVals);
                nextSampleNumer += 1;
            }
        }
        return parsedData;
   }

    /**
     * Method to get the start incremental count value for new Sample ID's in LIMS.
     * @return Integer
     */
    private Integer getNextSampleNumber() {
        Integer lastSampleNumber = 0;
        try {
            List<DataRecord> samples = dataRecordManager.queryDataRecords("Sample", "RequestId = '" + COVID_REQUEST_ID + "'", user);
            if (samples.size() > 0) {
                // traverse all samples saving the highest sample number
                for (DataRecord rec : samples) {
                    Integer sampleNumber = Integer.parseInt(rec.getStringVal("SampleId", user).split("_")[1]);
                    if (sampleNumber > lastSampleNumber) {
                        lastSampleNumber = sampleNumber;
                    }
                }
            }
        } catch (Exception e) {
            logError(e.getMessage(), e);
            return null;
        }
        return lastSampleNumber + 1;
    }

    /**
     * Method to check if any Accession Number being imported is already associated with other Sample in the same request 10858.
     * @param parsedSampleData
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private boolean hasDuplicateAccessionNumber(List<Map<String, Object>> parsedSampleData) {
        List<DataRecord> samplesInRequest = new ArrayList<>();
        try{
            samplesInRequest = dataRecordManager.queryDataRecords("Sample", "RequestId = '" + COVID_REQUEST_ID + "' AND ExemplarSampleType = 'other'", user);
        } catch (Exception e){
            logError(String.format("Couldn't query sample: %s", COVID_REQUEST_ID), e);
        }

        for (DataRecord sample: samplesInRequest){
            String sampleName = getRecordStringValue(sample, "OtherSampleId", user);
            if (sampleName != ""){
                String sampleId = getRecordStringValue(sample, "SampleId", user);
                for (Map<String, Object> data : parsedSampleData){
                    if(data.get("OtherSampleId").toString().equalsIgnoreCase(sampleName)){
                        String warning = String.format("Duplicate Accession No '%s' in uploaded file. Sample %s already has this Accession No.", sampleName, sampleId);
                        try {
                            clientCallback.displayWarning(warning);
                        } catch (ServerException e){
                            logError(warning, e);
                        }
                    }
                }
            }
        }
        return false;
    }
}
