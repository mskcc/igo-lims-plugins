package com.velox.sloan.cmo.workflows.workflows.TCRseq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

public class CreateTCRseqManifestSheet extends DefaultGenericPlugin {
    private List<String> manifestHeaders = Arrays.asList("SAMPLE ID", "PARENT BARCODE SEQUENCE", "CHILD BARCODE SEQUENCE");

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("GENERATE TCRSEQ MANIFEST");
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("GENERATE TCRSEQ MANIFEST");
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while setting toolbar button:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
        }
        return false;
    }

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> assignedIndices = activeTask.getAttachedDataRecords("IgoTcrSeqIndexBarcode", user);
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedAlphaSamples = new LinkedList<>();
            List<DataRecord> attachedBetaSamples = new LinkedList<>();
            for(DataRecord samples : attachedSamples) {
                try {
                    if(samples.getStringVal("Recipe", user).toLowerCase().contains("alpha")) {
                        attachedAlphaSamples.add(samples);
                    }
                    else if(samples.getStringVal("Recipe", user).toLowerCase().contains("beta")) {
                        attachedBetaSamples.add(samples);
                    }
                }
                catch(NotFound | RemoteException e) {

                }
            }


            if (assignedIndices.isEmpty()) {
                clientCallback.displayError("No '' records found attached to this task.");
                logError("No attached 'IGO TCRseq assigned indices' records found attached to this task.");
                return new PluginResult(false);
            }
            if (attachedAlphaSamples.isEmpty()) {
                clientCallback.displayError("No 'Alpha Sample' records found attached to this task.");
                logError("No attached 'alpha' records found attached to this task.");
                return new PluginResult(false);
            }
            if (attachedBetaSamples.isEmpty()) {
                clientCallback.displayError("No 'Beta Sample' records found attached to this task.");
                logError("No attached 'beta' records found attached to this task.");
                return new PluginResult(false);
            }


            List<String> headerForReport = manifestHeaders;
            List<DataRecord> alphaIndicesInfo = new LinkedList<>();
            List<DataRecord> betaIndicesInfo = new LinkedList<>();

            for (DataRecord attachedSample : attachedSamples) {
                for (DataRecord assignedIndex : assignedIndices) {
                    try {
                        if (attachedSample.getStringVal("Recipe", user).toLowerCase().contains("alpha") &&
                        assignedIndex.getStringVal("SampleId", user).equals(attachedSample.getStringVal("SampleId", user))) {
                            alphaIndicesInfo.add(assignedIndex);
                        }
                        else if (attachedSample.getStringVal("Recipe", user).toLowerCase().contains("beta") &&
                                assignedIndex.getStringVal("SampleId", user).equals(attachedSample.getStringVal("SampleId", user))) {
                            betaIndicesInfo.add(assignedIndex);
                        }
                    } catch (NotFound | RemoteException e) {

                    }
                }
            }


            XSSFWorkbook alphaWorkbook = new XSSFWorkbook();
            List<Map<String, Object>> alphaValuesForReport = setFieldsForReport(alphaIndicesInfo);
            generateExcelDataWorkbook(headerForReport, alphaValuesForReport, alphaWorkbook);
            String alphaFileName = generateFileNameFromRequestIds(attachedSamples);
            exportReport(alphaWorkbook, alphaFileName);

            XSSFWorkbook betaWorkbook = new XSSFWorkbook();
            List<Map<String, Object>> betaValuesForReport = setFieldsForReport(betaIndicesInfo);
            generateExcelDataWorkbook(headerForReport, betaValuesForReport, betaWorkbook);
            String betaFileName = generateFileNameFromRequestIds(attachedSamples);
            exportReport(alphaWorkbook, betaFileName);

        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception Error while generating TCRseq Manifest File:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
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
                reportFieldValues.put("SampleId", record.getValue("SampleId", user));
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
        data.sort(Comparator.comparing(o -> o.get("SampleId").toString()));
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
    private String generateFileNameFromRequestIds(List<DataRecord> attachedRecords) {
        Set<String> requestIds = new HashSet<>();
        for (DataRecord sample : attachedRecords) {
            String requestId = getParentRequestId(sample);
            if (!StringUtils.isBlank(requestId)) {
                requestIds.add(requestId);
            }
        }
        return "Project_" + StringUtils.join(requestIds, "_");
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
    private void exportReport(XSSFWorkbook workbook, String outFileName) {
        if (StringUtils.isBlank(outFileName)) {
            outFileName = "Project_";
        }
        logInfo("Generating TCRseq manifest " + outFileName + "_TCRseq_Manifest.xlsx");
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            workbook.write(byteStream);
            byteStream.close();
            byte[] bytes = byteStream.toByteArray();
            clientCallback.writeBytes(bytes, outFileName + "_TCRseq_Manifest.xlsx");
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
}
