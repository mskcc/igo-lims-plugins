package com.velox.sloan.cmo.workflows.strauthentication;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.utilities.CsvHelper;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to process the STR cell line authentication results using an external API from Cellosaurus company.
 * The user will upload raw results file generated by STR raw results analysis tool and upload it to LIMS when prompted. The
 * plugin returns a formatted csv file with results from API.
 *
 * @author sharmaa1
 */
public class StrReportGenerator extends DefaultGenericPlugin {
    private final List<String> STR_MARKERS_HUMAN = Arrays.asList("Amelogenin", "CSF1PO", "D2S1338", "D3S1358", "D5S818", "D7S820", "D8S1179",
            "D13S317", "D16S539", "D18S51", "D19S433", "D21S11", "FGA", "TH01", "TPOX", "vWA");
    private final List<String> STR_MARKERS_MOUSE = Arrays.asList("Mouse STR 1-1", "Mouse STR 1-2", "Mouse STR 11-1", "Mouse STR 11-2", "Mouse STR 12-1", "Mouse STR 13-1",
            "Mouse STR 15-3", "Mouse STR 17-2", "Mouse STR 18-3", "Mouse STR 19-2", "Mouse STR 2-1", "Mouse STR 3-2",
            "Mouse STR 4-2", "Mouse STR 5-5", "Mouse STR 6-4", "Mouse STR 6-7", "Mouse STR 7-1", "Mouse STR 8-1", "Mouse STR X-1");
    private final List<String> STR_REPORT_HEADERS_HUMAN = Arrays.asList("IGO ID", "Accession", "Name", "Score", "AMEL", "CSF1PO", "D2S1338", "D3S1358",
            "D5S818", "D7S820", "D8S1179", "D13S317", "D16S539", "D18S51", "D19S433", "D21S11", "FGA", "TH01", "TPOX", "vWA");
    private final List<String> STR_REPORT_HEADERS_MOUSE = Arrays.asList("IGO ID", "Accession", "Name", "Score",
            "Mouse STR 1-1", "Mouse STR 1-2", "Mouse STR 11-1", "Mouse STR 11-2", "Mouse STR 12-1", "Mouse STR 13-1",
            "Mouse STR 15-3", "Mouse STR 17-2", "Mouse STR 18-3", "Mouse STR 19-2", "Mouse STR 2-1", "Mouse STR 3-2",
            "Mouse STR 4-2", "Mouse STR 5-5", "Mouse STR 6-4", "Mouse STR 6-7", "Mouse STR 7-1", "Mouse STR 8-1", "Mouse STR X-1");
    private final String STR_REPORT_TAG = "GENERATE STR REPORT";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    /*
        Note: Mouse markers are not constant in this process, for example, Markers in raw data uploaded by user does not contain any prefix (1-1, 1-2, 1-3 etc),
        Markers in API request data must have "Mouse STR " prefix (Mouse STR 1-1, Mouse STR 1-2, Mouse STR 1-3 etc.), and Markers in data returned by API contain
        prefix "STR "(STR 1-1, STR 1-2, STR 1-3 etc.). Therefore, the STR markers are modified to add/remove prefixes in different methods below to send/parse data
        successfully.
        For Human markers, it is the case with 'Amelogenin' marker.
     */
    StrHelper strHelper = new StrHelper();
    private String species = "";
    private List<String> reportHeaders;
    private List<String> markers;

    public StrReportGenerator() {
        setTaskEntry(true);
        setTaskToolbar(true);
        setLine1Text("Generate STR");
        setLine2Text("Report");
        setOrder(PluginOrder.EARLY.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey(STR_REPORT_TAG);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey(STR_REPORT_TAG);
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while setting Task Toolbar button for plugin StrReportGenerator.\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            String uploadedFile = clientCallback.showFileDialog("Upload Raw data file", ".csv");
            if (StringUtils.isBlank(uploadedFile)) {
                logInfo("User did not upload the file.");
                return new PluginResult(true);
            }
            //validate the uploaded file
            if (!isValidFile(uploadedFile)) {
                return new PluginResult(false);
            }
            //read data from uploaded file
            List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(uploadedFile));
            if (!fileHasData(fileData, uploadedFile)) {
                return new PluginResult(false);
            }
            Map<String, Integer> headerValueMap = utils.getCsvHeaderValueMap(fileData, pluginLogger);
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            //set prerequisites for report generation
            species = getSpecies(fileData, headerValueMap);
            reportHeaders = getReportHeaders();
            markers = getStrMarkers();
            //validate prerequisite values
            if (!hasValidPrerequisites()) {
                clientCallback.displayError(String.format("Invalid prerequisites for report generation.\nSpecies:\n%s\nReport Headers:\n%s\nMarkers:\n%s\n", species, reportHeaders, markers));
            }
            //validate raw data file against species, markers.
            if (!isValidRawDataFile(fileData, headerValueMap, species)) {
                return new PluginResult(false);
            }
            //read and clean data for posting to api
            Map<String, Map<String, Object>> data = strHelper.aggregateDataBySample(fileData, headerValueMap, species);
            List<String> jsonData = strHelper.convertSampleDataToJson(data);
            //call method to get data from api
            JSONArray results = getStrResultsFromApi(jsonData.toString());
            //process data coming from api
            if (results == null) throw new AssertionError();
            Map<String, Map<String, Map<String, Object>>> sampleData = strHelper.getSampleDataFromApiData(results);
            if (attachedSamples.size() == 0) {
                clientCallback.displayError("Samples not found attached to this task.");
                return new PluginResult(false);
            }
            //process further to format for csv export
            parseDataForCsvFile(sampleData, attachedSamples, data);
        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException -> Error occured while running StrReportGenerator plugin:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (ParseException e) {
            String errMsg = String.format("ParseException -> Error occured while running StrReportGenerator plugin:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IOException e) {
            String errMsg = String.format("IOException -> Error occured while running StrReportGenerator plugin:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to check if uploaded excel file is valid csv file.
     *
     * @param uploadedFile
     * @return true/false
     * @throws ServerException
     * @throws IOException
     */
    private boolean isValidFile(String uploadedFile) throws ServerException, RemoteException {
        if (!utils.isCsvFile(uploadedFile)) {
            clientCallback.displayError(String.format("Not a valid csv file\n%s", uploadedFile));
            return false;
        }
        return true;
    }

    /**
     * Method to check if the uplaoded file has data.
     *
     * @param fileData
     * @param uploadedFile
     * @return true/false
     * @throws ServerException
     */
    private boolean fileHasData(List<String> fileData, String uploadedFile) throws ServerException, RemoteException {
        if (!utils.csvFileHasData(fileData)) {
            clientCallback.displayError(String.format("The uploaded file does not contain data\n%s", uploadedFile));
            return false;
        }
        return true;
    }

    /**
     * Method to get species value present on samples attached to the task.
     *
     * @param fileData
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getSpecies(List<String> fileData, Map<String, Integer> headerValueMap) {
        for (String row : fileData) {
            List<String> rowValues = Arrays.asList(row.split(","));
            Object marker = rowValues.get(headerValueMap.get("Marker"));
            if (marker != null && !StringUtils.isBlank(marker.toString().trim())) {
                String markerVal = marker.toString().trim();
                if (markerVal.equalsIgnoreCase("1-1") || markerVal.equalsIgnoreCase("5-5") || markerVal.equalsIgnoreCase("6-4") || markerVal.equalsIgnoreCase("7-1")) {
                    return "mouse";
                }
            }
        }
        return "human";
    }

    /**
     * Method to get markers from raw data file.
     *
     * @param uploadedData
     * @param headerValueMap
     * @return
     */
    private List<String> getMarkersFromRawDataFile(List<String> uploadedData, Map<String, Integer> headerValueMap) {
        List<String> markers = new ArrayList<>();
        for (String d : uploadedData) {
            List<String> rowData = Arrays.asList(d.split(","));
            markers.add(rowData.get(headerValueMap.get("Marker")).trim());
        }
        return markers;
    }

    private boolean isValidRawDataFile(List<String> fileData, Map<String, Integer> headerValueMap, String species) throws ServerException, RemoteException {
        List<String> markersInFile = getMarkersFromRawDataFile(fileData, headerValueMap);
        if (species.equalsIgnoreCase("mouse")) {
            assert markers != null;
            for (String m : markers) {
                String marker = m.replace("Mouse STR ", "").trim(); //header in raw data file does not contain prefix 'Mouse STR '. This prefix is necessary for api calls.
                if (!markersInFile.contains(marker)) {
                    clientCallback.displayError(String.format("Markers in uploaded raw data file are not valid for '%s' species.", species));
                    return false;
                }
            }
        }
        if (species.equalsIgnoreCase("human")) {
            assert markers != null;
            for (String marker : markers) {
                // marker 'Amelogenin' is named 'AMEL' in valid markers. The nomenclature is not constant across processes from file upload, Sending request to API,
                // and receiving data from API. This has to be handled in the code.
                String updatedMarker = marker.replace("Amelogenin", "AMEL");
                if (!markersInFile.contains(updatedMarker)) {
                    clientCallback.displayError(String.format("Markers in uploaded raw data file are not valid for '%s' species.", species));
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Method to get STR report headers based on species.
     *
     * @return
     */
    private List<String> getReportHeaders() {
        if (species.equalsIgnoreCase("mouse")) {
            return STR_REPORT_HEADERS_MOUSE;

        } else if (species.equalsIgnoreCase("human")) {
            return STR_REPORT_HEADERS_HUMAN;
        }
        return null;
    }

    /**
     * Method to get STR report markers based on species.
     *
     * @return
     */
    private List<String> getStrMarkers() {
        if (species.equalsIgnoreCase("mouse")) {
            return STR_MARKERS_MOUSE;
        } else if (species.equalsIgnoreCase("human")) {
            return STR_MARKERS_HUMAN;
        }
        return null;
    }

    /**
     * Method to validate species, reportHeaders and markers that are prerequisites for the plugin to work correctly.
     *
     * @return
     */
    private Boolean hasValidPrerequisites() {
        return !StringUtils.isBlank(species) && reportHeaders.size() != 0 && markers.size() != 0;
    }

    /**
     * Method to send HTTP request to the API and receive results back from API.
     *
     * @param sampleData
     * @return JSONArray
     * @throws ServerException
     * @throws ParseException
     */
    private JSONArray getStrResultsFromApi(String sampleData) {
        StringBuilder response = new StringBuilder();
        JSONParser parser = new JSONParser();
        JSONArray finalResponse = null;
        try {
            URL obj = new URL("https://web.expasy.org/cellosaurus-str-search/api/batch");
            HttpURLConnection postConnection = (HttpURLConnection) obj.openConnection();
            postConnection.setRequestMethod("POST");
            postConnection.setRequestProperty("Content-Type", "application/json");
            postConnection.setDoOutput(true);
            OutputStream os = postConnection.getOutputStream();
            os.write(sampleData.getBytes());
            os.flush();
            os.close();
            int responseCode = postConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { //success
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        postConnection.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine.trim());
                }
                in.close();
            } else {
                clientCallback.displayInfo(String.format("API REQUEST DID NOT WORK: %s", postConnection.getResponseMessage()));
            }

            if (response.toString().length() == 0) {
                clientCallback.displayInfo(String.format("API returned 0 result hits.\nData sent to server is:\n%s",
                        sampleData));
                return null;
            }
            finalResponse = (JSONArray) parser.parse(response.toString());
        } catch (ProtocolException e) {
            logError(String.format("ProtocolException -> Failed to get response from API:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (MalformedURLException e) {
            logError(String.format("MalformedURLException -> Failed to get response from API:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (IOException e) {
            logError(String.format("IOException -> Failed to get response from API:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Failed to get response from API:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (ParseException e) {
            logError(String.format("ParseException -> Failed to parse response from API:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return finalResponse;
    }

    /**
     * Method to get IGO ID for samples in the report.
     *
     * @param dataRecords
     * @param sampleName
     * @return String
     * @throws NotFound
     * @throws RemoteException
     */
    private String getIgoId(List<DataRecord> dataRecords, String sampleName){
        for (DataRecord r : dataRecords) {
            try {
                if (r.getValue("OtherSampleId", user) != null) {
                    String otherSampleId = r.getStringVal("OtherSampleId", user);
                    if (otherSampleId.equals(sampleName.trim())) {
                        return r.getStringVal("SampleId", user);
                    }
                }
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error while reading FieldValue from Sample with OtherSampleId %s:\n%s", sampleName, ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error while reading FieldValue from Sample with OtherSampleId %s:\n%s", sampleName, ExceptionUtils.getStackTrace(notFound)));
            }
        }
        return "";
    }

    /**
     * Method to parse and filter data received from API for CSV report. And then generate CSV report.
     *
     * @param extractedData
     * @param attachedSamples
     * @param sampleParamsData
     * @throws NotFound
     * @throws IOException
     * @throws ServerException
     */
    private void parseDataForCsvFile(Map<String, Map<String, Map<String, Object>>> extractedData, List<DataRecord> attachedSamples, Map<String, Map<String, Object>> sampleParamsData) {
        List<List<String>> data = new ArrayList<>();
        data.add(reportHeaders);
        Set<String> keySet = extractedData.keySet();
        try {
            for (String sampleName : keySet) {
                List<String> sampleParams = strHelper.getSampleParamsSentToApi(sampleParamsData, sampleName, markers); // first add line containing sample data sent to api
                sampleParams.set(0, getIgoId(attachedSamples, sampleName)); // add IGO ID to sample
                data.add(sampleParams);
                Map<String, Map<String, Object>> valuesByAccession = extractedData.get(sampleName);
                Set<String> accessionKeySet = valuesByAccession.keySet();
                for (String accession : accessionKeySet) {
                    //add one row for each result returned by the api. API could return multiple results per sample if there is at least 75% match.
                    List<String> rowValues = new ArrayList<>();
                    Map<String, Object> alleleData = valuesByAccession.get(accession);
                    rowValues.add(getIgoId(attachedSamples, sampleName)); // add IGO ID's to row data
                    rowValues.add(accession); //add accession value to row data
                    rowValues.add(strHelper.getValueFromMap(alleleData, "name"));
                    String scoreValue = strHelper.getValueFromMap(alleleData, "score");
                    if (!StringUtils.isBlank(scoreValue) && scoreValue.length() >= (scoreValue.indexOf(".") + 3)) {
                        rowValues.add(strHelper.getValueFromMap(alleleData, "score").substring(0, scoreValue.indexOf(".") + 3));
                    } else {
                        rowValues.add(scoreValue);
                    }
                    for (String value : markers) {
                        //markers in mouse data returned by api does not contain prefix "Mouse ", therefore it should be removed to parse data successfully for report.
                        String markerValueFromApi = value.replace("Mouse ", "").replace("Amelogenin", "AMEL");
                        rowValues.add(strHelper.getValueFromMap(alleleData, markerValueFromApi));
                    }
                    data.add(rowValues);
                }
            }
            strHelper.sortData(data);
            byte[] bytes = CsvHelper.writeCSV(data, null);
            clientCallback.writeBytes(bytes, "STR Report.csv");
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error while parsing API data and generating STR report:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (IOException e) {
            logError(String.format("IOException -> Error while parsing API data and generating STR report:\n%s", ExceptionUtils.getStackTrace(e)));
        }
    }
}
