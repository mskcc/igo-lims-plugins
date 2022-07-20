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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            Set<String> setOfProjects = new HashSet<>();
//            List<DataRecord> attachedAlphaSamples = new LinkedList<>();
//            List<DataRecord> attachedBetaSamples = new LinkedList<>();

            for (DataRecord samples : attachedSamples) {
                String projectId = getBaseProjectId(samples.getStringVal("SampleId", user));
                setOfProjects.add(projectId);
            }

            List<DataRecord> eachProjectsAttachedSample = new LinkedList<>();
            for(String project: setOfProjects) {
                logInfo("Project id is:" + project);
                for (DataRecord samples : attachedSamples) {
                    String sampleName = samples.getStringVal("OtherSampleId", user);
                    String projectId = getBaseProjectId(samples.getStringVal("SampleId", user));
                    if(project.equals(projectId)) {
                        eachProjectsAttachedSample.add(samples);
                        logInfo("attached " + sampleName + " to " + project + " list.");
//                        try {
//                            if (samples.getStringVal("Recipe", user).toLowerCase().contains("alpha")
//                                    && !samples.getStringVal("OtherSampleId", user).toLowerCase().contains("_alpha")) {
//                                samples.setDataField("OtherSampleId", sampleName + "_alpha", user);
//                                logInfo("_alpha appended to the alpha sample name.");
//                                attachedAlphaSamples.add(samples);
//
//                            } else if (samples.getStringVal("Recipe", user).toLowerCase().contains("beta")
//                                    && !samples.getStringVal("OtherSampleId", user).toLowerCase().contains("_beta")) {
//                                samples.setDataField("OtherSampleId", sampleName + "_beta", user);
//                                logInfo("_beta appended to the beta sample name.");
//                                attachedBetaSamples.add(samples);
//                            }
//                        } catch (NotFound | RemoteException e) {
//
//                        }
                    }
                }
                if (assignedIndices.isEmpty()) {
                    clientCallback.displayError("No '' records found attached to this task.");
                    logError("No attached 'IGO TCRseq assigned indices' records found attached to this task.");
                    return new PluginResult(false);
                }
                if (attachedSamples.isEmpty()) {
                    clientCallback.displayError("No 'Sample' records found attached to this task.");
                    logError("No sample records found attached to this task.");
                    return new PluginResult(false);
                }

                List<String> headerForReport = manifestHeaders;
                List<DataRecord> alphaIndicesInfo = new LinkedList<>();
                List<DataRecord> betaIndicesInfo = new LinkedList<>();

                for (DataRecord attachedSample : attachedSamples) {
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

                XSSFWorkbook alphaWorkbook = new XSSFWorkbook();
                logInfo("Generating alpha workbook..");
                List<Map<String, Object>> alphaValuesForReport = setFieldsForReport(alphaIndicesInfo);
                generateExcelDataWorkbook(headerForReport, alphaValuesForReport, alphaWorkbook);
                exportReport(true, alphaWorkbook, fileName);

                XSSFWorkbook betaWorkbook = new XSSFWorkbook();
                logInfo("Generating beta workbook..");
                List<Map<String, Object>> betaValuesForReport = setFieldsForReport(betaIndicesInfo);
                generateExcelDataWorkbook(headerForReport, betaValuesForReport, betaWorkbook);
                exportReport(false, betaWorkbook, fileName);
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
    private List<Map<String, Object>> setFieldsForReport(List<DataRecord> manifestInfo){
        List<Map<String, Object>> reportFieldValueMaps = new ArrayList<>();
        for (DataRecord record : manifestInfo) {
            Map<String, Object> reportFieldValues = new HashMap<>();
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
                Object[] indexTag = record.getValue("IndexTag", user).toString().split("-");
                reportFieldValues.put("ParentBarcodeSequence", indexTag[0]);
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
    private void sortMapBySampleId(List<Map<String, Object>> data) {
        data.sort(Comparator.comparing(o -> o.get("SampleName").toString()));
    }
    /**
     * Add data values to the excel workbook.
     *
     * @param headerValues
     * @param dataValues
     * @param workbook
     */
    private void generateExcelDataWorkbook(List<String> headerValues, List<Map<String, Object>> dataValues, XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("TCRseq Manifest");
        int rowId = 0;
        XSSFRow row = sheet.createRow(rowId);
        rowId++;
        int cellId = 0;
        for (String headerValue : headerValues) {
            Cell cell = row.createCell(cellId);
            cell.setCellValue(headerValue);
            sheet.autoSizeColumn(cellId);
            cellId++;
        }
        for (Map<String, Object> data : dataValues) {
            row = sheet.createRow(rowId);
            row.createCell(0).setCellValue(data.get("SampleName").toString());
            row.createCell(1).setCellValue(data.get("ParentBarcodeSequence").toString());
            row.createCell(2).setCellValue(data.get("ChildBarcodeSequence").toString());
            rowId++;
        }
        autoSizeColumns(sheet, headerValues);

    }
    /**
     * Auto-size columns to show complete test in the cells.
     *
     * @param sheet
     * @param header
     */
    private void autoSizeColumns(XSSFSheet sheet, List<String> header) {
        for (int i = 0; i < header.size(); i++) {
            sheet.autoSizeColumn(i);
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
     * This method is designed to find and return the Sample in hierarchy with a desired child DataType
     *
     * @param sample
     * @return Sample DataRecord
     * @throws IoError
     * @throws RemoteException
     */
    private String getParentRequestId(DataRecord sample){
        DataRecord record = null;
        Stack<DataRecord> samplePile = new Stack<>();
        samplePile.push(sample);
        try {
            do {
                DataRecord startSample = samplePile.pop();
                List<DataRecord> parentRecords = startSample.getParentsOfType("Sample", user);
                if (!parentRecords.isEmpty() && parentRecords.get(0).getParentsOfType("Request", user).size() > 0) {
                    record = parentRecords.get(0);
                }
                if (!parentRecords.isEmpty() && record == null) {
                    samplePile.push(parentRecords.get(0));
                }
            } while (!samplePile.empty());

            if ((record != null) && (record.getValue("RequestId", user) != null)) {
                return (String) record.getValue("RequestId", user);
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error getting Parent RequestId for Sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error getting Parent RequestId for Sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ioError)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error getting Parent RequestId for Sample with recordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
        }
        return "";
    }

    /**
     * Export the manifest as Excel file.
     *
     * @param workbook
     * @param outFileName
     * @throws IOException
     * @throws ServerException
     */
    private void exportReport(boolean isAlpha, XSSFWorkbook workbook, String outFileName) {
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
            workbook.write(byteStream);
            byteStream.close();
            byte[] bytes = byteStream.toByteArray();
            ExemplarConfig exemplarConfig = new ExemplarConfig(managerContext);
            String tcrseqManifestPath = exemplarConfig.getExemplarConfigValues().get("TCRseqManifestPath").toString();
                    //"/pskis34/vialelab/LIMS/TCRseqManifest"

            File outFile = null;
            if (isAlpha) {
                outFile = new File(tcrseqManifestPath + "/" + outFileName + "_TCRseq_Manifest_Alpha.csv");
                clientCallback.writeBytes(bytes, outFileName + "_TCRseq_Manifest_Alpha.csv");
            }
            else {
                outFile = new File(tcrseqManifestPath + "/" + outFileName + "_TCRseq_Manifest_Beta.csv");
                clientCallback.writeBytes(bytes, outFileName + "_TCRseq_Manifest_Beta.csv");
            }


            try (FileOutputStream fos = new FileOutputStream(outFile)){
                fos.write(bytes);
                outFile.setReadOnly();
            } catch (Exception e) {
                logInfo(e.getMessage());
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
