package com.velox.sloan.cmo.workflows.TCRseq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.plugin.DefaultServerPlugin;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlphaBetaBarcodeParser extends DefaultGenericPlugin {

    private final List<String> TCRSEQ_BARCODE_SHEET_EXPECTED_HEADERS = Arrays.asList("");
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getStatus() != ActiveTask.COMPLETE && activeTask.getTask().getTaskOptions().containsKey("PARSING ALPHA BETA BARCODE")
                && !activeTask.getTask().getTaskOptions().containsKey("ALPHA BETA BARCODE PARSED");
    }

    public PluginResult run() throws ServerException {
        try {
            String tcrseqBarcodesFile = clientCallback.showFileDialog("Please upload TCRseq barcode file", null);
            if (StringUtils.isBlank(tcrseqBarcodesFile)) {
                return new PluginResult(false);
            }
            if (!isValidExcelFile(tcrseqBarcodesFile)) {
                return new PluginResult(false);
            }
            //Check the headers of the file
            byte[] excelFileData = clientCallback.readBytes(tcrseqBarcodesFile);
            List<Row> rowData = utils.getExcelSheetDataRows(excelFileData);
            if (!utils.fileHasData(rowData, tcrseqBarcodesFile) || !utils.hasValidHeader(rowData, TCRSEQ_BARCODE_SHEET_EXPECTED_HEADERS, tcrseqBarcodesFile)) {
                return new PluginResult(false);
            }

            //Parse every field of every row and populate the map
            List<DataRecord> samplesAttachedToTask = activeTask.getAttachedDataRecords("Sample", user);
            if (samplesAttachedToTask.isEmpty()) {
                clientCallback.displayError("No samples found attached to this task.");
                return new PluginResult(false);
            }
            HashMap<String, Integer> headerValuesMap = utils.getHeaderValuesMapFromExcelRowData(rowData);
            Map<String, List<DataRecord>> uploadedTcrSeqBarcodes = new HashMap<>();


            //populate a new table with map values
            Object recipe = samplesAttachedToTask.get(0).getValue(SampleModel.RECIPE, user);
            Object dlpRequestedReads = getDlpRequestedReads(recipe);
            Map<String, List<Row>> rowsSeparatedBySampleMap = utils.getRowsBySample(samplesAttachedToTask, rowData, headerValuesMap, user);
            Map<String, List<DataRecord>> newAlphaBetaBarcodes = createDlpSamplesAndProtocolRecords(rowsSeparatedBySampleMap, headerValuesMap, samplesAttachedToTask, cellTypeToProcess);
            createPools(newAlphaBetaBarcodes, (Double) dlpRequestedReads);
        }
        catch (IoError e) {
            String errMsg = String.format("IoError Exception while parsing the TCRseq spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidValue e) {
            String errMsg = String.format("IoError Exception while parsing the TCRseq spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidFormatException e) {
            String errMsg = String.format("InvalidFormat Exception while parsing the TCRseq spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (ServerException se) {
            String errMsg = String.format("");
            clientCallback.displayError(errMsg);
            logError(errMsg);
        } catch (IOException e) {
            String errMsg = String.format("IOException while parsing the TCRseq spotting file:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    public boolean isValidExcelFile(String excelFileName) throws ServerException {
        boolean isValid =  excelFileName.toLowerCase().endsWith("xlsx") || excelFileName.toLowerCase().endsWith("xls");
        if(!isValid) {
            clientCallback.displayError(String.format("Uploaded file '%s' is not a valid excel file", excelFileName));
            return false;
        }
        return true;
    }
}
