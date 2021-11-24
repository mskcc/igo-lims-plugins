package com.velox.sloan.cmo.workflows.TCRseq;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.DefaultServerPlugin;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlphaBetaBarcodeParser extends DefaultGenericPlugin {
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

            //Parse every field of every row and populate the map


            Map<String, List<DataRecord>> uploadedTcrSeqBarcodes = new HashMap<>();


            //populate a new table with map values
        }
        catch (ServerException se) {
            String errMsg = String.format("");
            clientCallback.displayError(errMsg);
            logError(errMsg);
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
