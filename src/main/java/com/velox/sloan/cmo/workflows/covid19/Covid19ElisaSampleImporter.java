package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

public class Covid19ElisaSampleImporter extends DefaultGenericPlugin {
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private final List<String> REQUIRED_FILE_HEADERS = Arrays.asList("SAMPLE_NAME", "DESTINATION_WELL");
    private final String COVID_ELISA_REQUEST_ID = "10859";

    public Covid19ElisaSampleImporter() {
        setActionMenu(true);
        setLine1Text("Import COVID19 ELISA Samples");
        setDescription("Plugin to import new COVID-19 ELISA Samples into LIMS.");
        String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin", "Group Leaders", "Lab Managers", "Lab Techs", "NA Team", "Path Extraction Techs", "Sequencing Techs"};
        setUserGroupList(permittedUsers);
    }

    @Override
    public PluginResult run() throws ServerException {
        try {
            String csvFilePath = clientCallback.showFileDialog("Upload file with ELISA Sample Information", null);
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

            if (!csvFileContainsRequiredHeaders(fileData, REQUIRED_FILE_HEADERS)){
                clientCallback.displayError(String.format("The uploaded file %s does not have valid headers. The required headers are:\n%s",
                        csvFilePath, utils.convertListToString(REQUIRED_FILE_HEADERS)));
                logError(String.format("The uploaded file %s does not have valid headers. The required headers are:\n%s",
                        csvFilePath, utils.convertListToString(REQUIRED_FILE_HEADERS)));
                return new PluginResult(false);
            }
            Map<String, Integer> headerValuesMap = utils.getCsvHeaderValueMap(fileData);
            List<Map<String,Object>> parsedSampleData = parseFileDataToSampleData(fileData, headerValuesMap);
            //create new request for project if not exists
            createRequestIfNotExist();
            List<DataRecord> requests = dataRecordManager.queryDataRecords("Request", "RequestId = '10859'" , user);

            if (requests.size() == 1 && !hasDuplicateSampleName(parsedSampleData) && isValidFileData(fileData, headerValuesMap)){
                requests.get(0).addChildren("Sample", parsedSampleData, user);
                clientCallback.displayInfo("Adding new Sample to request 10859........");
                dataRecordManager.storeAndCommit(String.format("Added %d new Samples in LIMS.", parsedSampleData.size()), null, user);
                clientCallback.displayInfo(String.format("Process complete.\n\nAdded %d new Sample to request 10859.", parsedSampleData.size()));
            }
            if (requests.size() != 1){
                clientCallback.displayError("Error: Either the Request 10859 does not exist, or there are more than one Requests with same RequestId 10859.");
                logError("Error: Either the Request 10859 does not exist, or there are more than one Requests with same RequestId 10859.");
                return new PluginResult(false);
            }

        } catch (Exception e){
            String message = String.format("Error while importing new COVID-19 ELISA Samples into LIMS\n%s", ExceptionUtils.getMessage(e));
            logError(message);
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
        Set<String> sampleNames = new HashSet<>();
        for (int i=1; i<fileData.size(); i++){
            List<String> rowVals = Arrays.asList(fileData.get(i).trim().split(","));
            String sampleName = rowVals.get(headerValuesMap.get("SAMPLE_NAME"));
            //String plateId = rowVals.get(headerValuesMap.get("DESTINATION_PLATE_ID"));
            String well = rowVals.get(headerValuesMap.get("DESTINATION_WELL"));
            if (rowVals.size()< 3 || StringUtils.isBlank(sampleName) || StringUtils.isBlank(well)){
                clientCallback.displayError(String.format("Invalid Row values in uploaded file: %s.\nRow values must have SAMPLE_NAME and DESTINATION_WELL values.", fileData.get(i)));
                return false;
            }
            if (!sampleNames.add(sampleName)){
                clientCallback.displayWarning(String.format("Found duplicate SAMPLE_NAME in uploaded file: %s.", sampleName));
            }
            if(well.length() < 2 || well.length() > 3){
                clientCallback.displayError(String.format("Invalid well position in uploaded file: %s.", well));
                return false;
            }
        }
        return true;
    }

    /**
     * Method to check if csv file header contains the values that are required.
     *
     * @param fileData
     * @param expectedHeaderValues
     * @return true/false
     */
    private boolean csvFileContainsRequiredHeaders(List<String> fileData, List<String> expectedHeaderValues) {
        List<String> headerValInFile = Arrays.asList(fileData.get(0).split(","));
        for (String val: expectedHeaderValues){
            if (!headerValInFile.contains(val)){
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
    private List<Map<String, Object>> parseFileDataToSampleData(List<String> fileData, Map<String, Integer> headrValuesMap) throws ServerException {
        List<Map<String, Object>> parsedData = new ArrayList<>();
        Integer nextSampleNumer = getNextSampleNumber();
        for (int i = 1; i< fileData.size(); i++){
            List<String> rowValues = Arrays.asList(fileData.get(i).split(","));
            if (rowValues.size()>0){
                Map<String, Object> sampleVals = new HashMap<>();
                String sampleId = "10859" + "_" + nextSampleNumer;
                clientCallback.displayInfo(sampleId);
                String sampleName = rowValues.get(headrValuesMap.get("SAMPLE_NAME"));
                String row = rowValues.get(headrValuesMap.get("DESTINATION_WELL")).substring(0,1);
                String column = rowValues.get(headrValuesMap.get("DESTINATION_WELL")).substring(1);
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
     * Method to create new Request if not exists.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private void createRequestIfNotExist() throws IoError, RemoteException, NotFound, ServerException {
        List<DataRecord> request = dataRecordManager.queryDataRecords("Request", "RequestId = '10859'", user);
        List<DataRecord> project = dataRecordManager.queryDataRecords("Project", "ProjectId = '10859'", user);
        clientCallback.displayInfo("" + request.size());
        clientCallback.displayInfo("" + project.size());
        if (request.size() == 0) {
            Map<String, Object> requestValues = new HashMap<>();
            requestValues.put("RequestId", "10859");
            requestValues.put("ArePoolsIncluded", false);
            requestValues.put("AreSamplesLibraries", false);
            requestValues.put("HighPriority", true);
            requestValues.put("NumberOfSamples", 1000);
            requestValues.put("AddSamplesMethod", "Manually Add New Samples");
            requestValues.put("ProjectId", "10859");
            if(project.size()==1){
                clientCallback.displayInfo("adding request as child.");
                DataRecord newRequest = project.get(0).addChild("Request", requestValues, user);
                dataRecordManager.storeAndCommit("Added new request '10859' for COVID 19 ELISA Project.", null, user);
                clientCallback.displayInfo(newRequest.getStringVal("RequestId", user));
            }
        }
    }

    /**
     * Method to get the start incremental count value for new Sample ID's in LIMS.
     * @return Integer
     */
    private Integer getNextSampleNumber() {
        Integer lastSampleNumber = 0;
        try {
            List<DataRecord> samples = dataRecordManager.queryDataRecords("Sample", "RequestId = '10859'", user);
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
            logError(e.getMessage());
            return null;
        }
        return lastSampleNumber + 1;
    }

    /**
     * Method to check if any Sample Name being imported is already associated with other Sample in the same request 10859.
     * @param parsedSampleData
     * @return
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private boolean hasDuplicateSampleName(List<Map<String, Object>> parsedSampleData) throws IoError, RemoteException, NotFound, ServerException {
        List<DataRecord> samplesInRequest = dataRecordManager.queryDataRecords("Sample", "RequestId = '10859' AND ExemplarSampleType = 'other'", user);
        for (DataRecord sample: samplesInRequest){
            if (sample.getValue("OtherSampleId", user) != null){
                String sampleId = sample.getStringVal("SampleId", user);
                String sampleName = sample.getStringVal("OtherSampleId", user);
                for (Map<String, Object> data : parsedSampleData){
                    if(data.get("OtherSampleId").toString().equalsIgnoreCase(sampleName)){
                        clientCallback.displayWarning(String.format("Duplicate SAMPLE_NAME '%s' in uploaded file. Sample %s already has this Sample Name", sampleName, sampleId));
                    }
                }
            }
        }
        return false;
    }
}
