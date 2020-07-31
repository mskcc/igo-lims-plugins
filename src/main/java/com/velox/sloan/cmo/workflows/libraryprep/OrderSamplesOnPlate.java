package com.velox.sloan.cmo.workflows.libraryprep;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.util.List;

public class OrderSamplesOnPlate extends DefaultGenericPlugin {

    /**
     * This a test plugin.
     */
    public OrderSamplesOnPlate() {
        setActionMenu(true);
        setLine1Text("Here We Go!");
        setLine2Text("More Text");
        setDescription("Move Samples onto Plate Based on IGO ID");
    }

    @Override
    public PluginResult run() throws ServerException {
        ActiveWorkflow activeWebFormReceivingWorkflow;
        try {
            List<DataRecord> bankedSamples = dataRecordManager.queryDataRecords("BankedSample", "ServiceId = ", user);

            return new PluginResult(true);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error:", e.getMessage(), e));
            logError(e);
            return new PluginResult(false);
        }
    }
}