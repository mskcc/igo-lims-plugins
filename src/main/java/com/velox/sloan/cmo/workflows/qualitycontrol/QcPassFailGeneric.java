package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class QcPassFailGeneric extends DefaultGenericPlugin {
    private final static String IGO_ID_WITHOUT_ALPHABETS_PATTERN = "^[0-9]+_[0-9]+.*$";  // sample id without alphabets
    private final static String IGO_ID_WITH_ALPHABETS_PATTERN = "^[0-9]+_[A-Z]+_[0-9]+.*$";  // sample id without alphabets
    public boolean isRNAQcReportStep = false;
    public boolean isDNAQcReportStep = false;
    public QcPassFailGeneric() {
        setTaskSubmit(true);
        setOrder(PluginOrder.FIRST.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return this.activeTask.getTask().getTaskOptions().containsKey("Autofill QC DECISION COMMENT") && !this.activeTask.getTask().getTaskOptions().containsKey("_Autofilled");
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            // read the attached samples
            //Match with the generic table recipe and then sampleType, preservation, RIN, DIN, DV200 and TotalMass then update the IgoQcRecommendation, Comment and Investigator Decision
            if (this.activeTask.getTask().getTaskName().trim().equalsIgnoreCase("Generate QC report for DNA")) {
                this.isRNAQcReportStep = false;
                this.isDNAQcReportStep = true;
                logInfo("It's a DNA QC Report step!!");
            }
            if (this.activeTask.getTask().getTaskName().trim().equalsIgnoreCase("Generate QC report for RNA")) {
                this.isDNAQcReportStep = false;
                this.isRNAQcReportStep = true;
                logInfo("It's a RNA QC Report step!!");
            }
            List<DataRecord> attachedSamples = this.activeTask.getAttachedDataRecords("Sample", this.user);
            List<DataRecord>  attachedQcDNAReports = this.activeTask.getAttachedDataRecords("QcReportDna", this.user);
            List<DataRecord> attachedQcRNAReports = this.activeTask.getAttachedDataRecords("QcReportRna", this.user);
            List<DataRecord> qcReports = new LinkedList<>();//getQcReportRecordsForSamples(sampleObjects, isDNAQcReportStep);
            Map<String, List<Object>> requestIdToSampleMap = new HashMap<>();
            Map<String, Boolean> requestToAllSamplesQcStatus = new HashMap<>();
        } catch (Exception e) {

        }
    }
}
