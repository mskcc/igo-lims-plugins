package com.velox.sloan.cmo.workflows.workflows.TCRseq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.utilities.ExemplarConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Pattern;

public class CreateTCRseqManifestSheet extends DefaultGenericPlugin {
    private List<String> manifestHeaders = Arrays.asList("SAMPLE ID", "PARENT BARCODE SEQUENCE", "CHILD BARCODE SEQUENCE");

    private final static String IGO_ID_WITHOUT_ALPHABETS_PATTERN = "^[0-9]+_[0-9]+.*$";  // sample id without alphabets
    private final static String IGO_ID_WITH_ALPHABETS_PATTERN = "^[0-9]+_[A-Z]+_[0-9]+.*$";  // sample id without alphabets

    public CreateTCRseqManifestSheet() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
        setIcon("com/velox/sloan/cmo/resources/export_32.gif");
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("GENERATE TCRSEQ MANIFEST") &&
                !this.activeTask.getTask().getTaskOptions().containsKey("TCRSEQ SAMPLE MANIFEST GENERATED");
    }

    public PluginResult run() throws Throwable {

        try {
            List<DataRecord> assignedIndices = activeTask.getAttachedDataRecords("IgoTcrSeqIndexBarcode", user);
            List<DataRecord> attachedSamplesWithControls = activeTask.getAttachedDataRecords("Sample", user);
            Set<String> setOfProjects = new HashSet<>();
            List<DataRecord> attachedSamplesWithoutControls = new LinkedList<>();

            for (DataRecord samples : attachedSamplesWithControls) {
                Object isControl = samples.getValue("IsControl", user);
                logInfo("isControl: " + isControl.toString());
                if (isControl != null && (boolean) isControl) {
                    logInfo("isControl = " + isControl.toString() +" continuing with the next sample.");
                    continue;
                }
                logInfo("Not a control, adding the sample to the list.");
                attachedSamplesWithoutControls.add(samples);
                String projectId = getBaseProjectId(samples.getStringVal("SampleId", user));
                setOfProjects.add(projectId);
            }

            List<DataRecord> eachProjectsAttachedSample = new LinkedList<>();
            for(String project: setOfProjects) {
                logInfo("Project id is:" + project);
                for (DataRecord samples : attachedSamplesWithoutControls) {
                    String sampleName = samples.getStringVal("OtherSampleId", user);
                    String projectId = getBaseProjectId(samples.getStringVal("SampleId", user));
                    if(project.equals(projectId)) {
                        eachProjectsAttachedSample.add(samples);
                        logInfo("attached " + sampleName + " to " + project + " list.");
                    }
                }
                if (assignedIndices.isEmpty()) {
                    clientCallback.displayError("No '' records found attached to this task.");
                    logError("No attached 'IGO TCRseq assigned indices' records found attached to this task.");
                    return new PluginResult(false);
                }
                if (attachedSamplesWithControls.isEmpty()) {
                    clientCallback.displayError("No 'Sample' records found attached to this task.");
                    logError("No sample records found attached to this task.");
                    return new PluginResult(false);
                }

                List<String> headerForReport = manifestHeaders;
                List<DataRecord> alphaIndicesInfo = new LinkedList<>();
                List<DataRecord> betaIndicesInfo = new LinkedList<>();

                for (DataRecord attachedSample : attachedSamplesWithoutControls) {
                    for (DataRecord assignedIndex : assignedIndices) {
                        String projectId = getBaseProjectId(attachedSample.getStringVal("SampleId", user));
                        if(project.equals(projectId)) {
                            try {
                                if (attachedSample.getStringVal("Recipe", user).toLowerCase().contains("alpha") &&
                                        assignedIndex.getStringVal("SampleId", user).equals(attachedSample.getStringVal("SampleId", user))) {
                                    logInfo(assignedIndex.getStringVal("sampleId", user) + " with recipe: "
                                            + assignedIndex.getStringVal("Recipe", user) + " added to alpha assigned indices.");
                                    alphaIndicesInfo.add(assignedIndex);
                                } else if (attachedSample.getStringVal("Recipe", user).toLowerCase().contains("beta") &&
                                        assignedIndex.getStringVal("SampleId", user).equals(attachedSample.getStringVal("SampleId", user))) {
                                    logInfo(assignedIndex.getStringVal("sampleId", user) + " with recipe: "
                                            + assignedIndex.getStringVal("Recipe", user) + " added to beta assigned indices.");
                                    betaIndicesInfo.add(assignedIndex);
                                }
                            } catch (NotFound | RemoteException e) {

                            }
                        }
                    }
                }

                String fileName = generateFileNameFromRequestIds(eachProjectsAttachedSample);
                eachProjectsAttachedSample.clear();

                List<String[]> dataLines = new LinkedList<>();
                logInfo("Generating alpha sheet..");
                List<Map<String, String>> alphaValuesForReport = setFieldsForReport(alphaIndicesInfo);
                generateCSVData(headerForReport, alphaValuesForReport, dataLines, fileName, true);

                dataLines.clear();
                logInfo("Generating beta sheet..");
                List<Map<String, String>> betaValuesForReport = setFieldsForReport(betaIndicesInfo);
                generateCSVData(headerForReport, betaValuesForReport, dataLines, fileName, false);
            }

        } catch(NotFound e) {
            String errMsg = String.format("Not Found Exception Error while assigning TCRseq Manifest File name:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while generating TCRseq Manifest File:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        this.activeTask.getTask().getTaskOptions().put("TCRSEQ SAMPLE MANIFEST GENERATED", "");
        return new PluginResult(true);
    }

    /**
     * Create a data structure to hold the values to be included in the manifest file.
     *
     * @param manifestInfo
     * @return values for the report for each sample.
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     */
    private List<Map<String, String>> setFieldsForReport(List<DataRecord> manifestInfo){
        List<Map<String, String>> reportFieldValueMaps = new ArrayList<>();
        for (DataRecord record : manifestInfo) {
            Map<String, String> reportFieldValues = new HashMap<>();
            try {
                Object[] sampleId = record.getValue("SampleId", user).toString().split("_");
                String manifestSampleName = record.getValue("OtherSampleId", user).toString();
                if (sampleId[sampleId.length - 1].toString().equals("1")) {
                    logInfo("Appending _alpha");
                    manifestSampleName += "_alpha";
                }
                else if (sampleId[sampleId.length - 1].toString().equals("2")) {
                    logInfo("Appending _beta");
                    manifestSampleName += "_beta";
                }
                logInfo("manifestSampleName = " + manifestSampleName);
                reportFieldValues.put("SampleName", manifestSampleName);
                String[] indexTag = record.getValue("IndexTag", user).toString().split("-");
                reportFieldValues.put("ParentBarcodeSequence", indexTag[0]);
                if (indexTag[1].startsWith("NNNN")) {
                    indexTag[1] = indexTag[1].substring(4, indexTag[1].length());
                }
                reportFieldValues.put("ChildBarcodeSequence", indexTag[1]);

                reportFieldValueMaps.add(reportFieldValues);
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error setting field values for report:\n%s", ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error setting field values for report:\n%s", ExceptionUtils.getStackTrace(notFound)));
            }
        }
        sortMapBySampleId(reportFieldValueMaps);
        return reportFieldValueMaps;
    }
    /**
     * Sort the data based on the SampleId value.
     *
     * @param data
     */
    private void sortMapBySampleId(List<Map<String, String>> data) {
        data.sort(Comparator.comparing(o -> o.get("SampleName")));
    }

    private void generateCSVData(List<String> headerValues, List<Map<String, String>> dataValues, List<String[]> dataLines
    , String outFileName, boolean isAlpha) {
        String[] headersArray = new String[headerValues.size()];
        int i = 0;
        for (String headerValue : headerValues) {
            headersArray[i++] = headerValue;
        }
        dataLines.add(headersArray);
        i = 0;
        String[] dataInfoArray = new String[headerValues.size()];
        for(Map<String, String> row : dataValues) {
            dataInfoArray[i++] = row.get("SampleName");
            dataInfoArray[i++] = row.get("ParentBarcodeSequence");
            dataInfoArray[i++] = row.get("ChildBarcodeSequence");
            dataLines.add(dataInfoArray);
            dataInfoArray = new String[headerValues.size()];
            i = 0;
        }

        for (String[] dl : dataLines) {
            for (String dlData : dl)
            logInfo("lines of data are: " + dlData + "\n");
        }
        if (StringUtils.isBlank(outFileName)) {
            outFileName = "Project_";
        }
        if (isAlpha) {
            logInfo("Generating TCRseq manifest " + outFileName + "_TCRseq_Manifest_Alpha.csv");
        }
        else {
            logInfo("Generating TCRseq manifest " + outFileName + "_TCRseq_Manifest_Beta.csv");
        }
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            File outFile = null;
            StringBuffer allData = new StringBuffer();
            byte[] bytes;
            for (String[] eachLine: dataLines) {
                for (String eachCell : eachLine) {
                    allData.append(eachCell + ",");
                }
                allData.append("\n");
            }
            bytes = allData.toString().getBytes();
            ExemplarConfig exemplarConfig = new ExemplarConfig(managerContext);
            String tcrseqManifestPath = exemplarConfig.getExemplarConfigValues().get("TCRseqManifestPath").toString();
            //"/pskis34/vialelab/LIMS/TCRseqManifest"
            int copyNumber = 1;
            if (isAlpha) {
                copyNumber = 1;
                outFile = new File(tcrseqManifestPath + "/LIMSTesting/" + outFileName + "_TCRseq_Manifest_Alpha.csv");

                while (outFile.exists() && !outFile.isDirectory()) {
                    logInfo("The alpha outfile compared in while loop is: " + outFile);
                    outFile =  new File(tcrseqManifestPath + "/LIMSTesting/" + outFileName + "_TCRseq_Manifest_Alpha("
                            + copyNumber + ").csv");
                    copyNumber++;
                }
                logInfo("Alpha drive filename is:" + outFile);
                clientCallback.writeBytes(bytes, outFileName + "_TCRseq_Manifest_Alpha.csv");
            }
            else {
                copyNumber = 1;
                outFile = new File(tcrseqManifestPath + "/LIMSTesting/" + outFileName + "_TCRseq_Manifest_Beta.csv");
                while (outFile.exists() && !outFile.isDirectory()) {
                    logInfo("The beta outfile compared in while loop is: " + outFile);
                    outFile =  new File(tcrseqManifestPath + "/LIMSTesting/" + outFileName + "_TCRseq_Manifest_Beta("
                            + copyNumber + ").csv");
                    copyNumber++;
                }
                logInfo("Beta drive filename is:" + outFile);
                clientCallback.writeBytes(bytes, outFileName + "_TCRseq_Manifest_Beta.csv");
            }

            try (OutputStream fos = new FileOutputStream(outFile, false)){
                //byteStream.writeTo(fos);
                fos.write(bytes);
                outFile.setReadOnly();
                byteStream.close();
            } catch (Exception e) {
                logInfo("Error in writing to shared drive: " + e.getMessage());
            }


        } catch (NotFound e) {
            logError(String.format("NotFoundException -> Error while exporting TCRseq manifest:\n%s",ExceptionUtils.getStackTrace(e)));
        } catch (IoError e) {
            logError(String.format("IoError -> Error while exporting TCRseq manifest:\n%s",ExceptionUtils.getStackTrace(e)));
        } catch (ServerException e) {
            logError(String.format("RemoteException -> Error while exporting TCRseq manifest:\n%s",ExceptionUtils.getStackTrace(e)));
        } catch (IOException e) {
            logError(String.format("IOException -> Error while exporting TCRseq manifes:\n%s", ExceptionUtils.getStackTrace(e)));
        } finally {
            try {
                byteStream.close();
            } catch (IOException e) {
                logError(String.format("IOException -> Error while closing the ByteArrayOutputStream:\n%s", ExceptionUtils.getStackTrace(e)));
            }
        }
    }
    /**
     * Generate output file name for Report
     *
     * @param attachedRecords
     * @return Project ID's separated by "_" and prefixed with "Project_"
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     */
    private String generateFileNameFromRequestIds(List<DataRecord> attachedRecords) throws RemoteException, NotFound {
        DataRecord firstSample = attachedRecords.get(0);
        String requestId = getBaseProjectId(firstSample.getStringVal("SampleId", user)); //getParentRequestId
        logInfo("sample request id is:" + requestId);
        if (!StringUtils.isBlank(requestId)) {
            return "Project_" + requestId;
        }
        return "";
    }
    /**
     * Method to get base Sample ID when aliquot annotation is present.
     * Example: for sample id 012345_1_1_2, base sample id is 012345
     * Example2: for sample id 012345_B_1_1_2, base sample id is 012345_B
     * @param sampleId
     * @return
     */
    public static String getBaseProjectId(String sampleId){
        Pattern alphabetPattern = Pattern.compile(IGO_ID_WITH_ALPHABETS_PATTERN);
        Pattern withoutAlphabetPattern = Pattern.compile(IGO_ID_WITHOUT_ALPHABETS_PATTERN);
        if (alphabetPattern.matcher(sampleId).matches()){
            String[] sampleIdValues =  sampleId.split("_");
            return String.join("_", Arrays.copyOfRange(sampleIdValues,0,2));
        }
        if(withoutAlphabetPattern.matcher(sampleId).matches()){
            String[] sampleIdValues =  sampleId.split("_");
            return String.join("_", Arrays.copyOfRange(sampleIdValues,0,1));
        }
        return sampleId;
    }
}
