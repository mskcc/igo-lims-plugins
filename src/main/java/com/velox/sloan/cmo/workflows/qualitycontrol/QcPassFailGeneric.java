package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.management.NotificationFilter;
import javax.xml.crypto.Data;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Pattern;

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
            List<Object> sampleObjects = new LinkedList<>(attachedSamples);
            List<DataRecord> qcReports = new LinkedList<>();//getQcReportRecordsForSamples(sampleObjects, isDNAQcReportStep);
            Map<String, List<Object>> requestIdToSampleMap = new HashMap<>();
            Map<String, Boolean> requestToAllSamplesQcStatus = new HashMap<>();

            for (DataRecord sample : attachedSamples) {
                String sampleId = sample.getStringVal("SampleId", user);
                String requestId = getRequestId(sampleId);
                logInfo("Retrieved request id  for sample: " + sample.getStringVal("SampleId", user) + " = " + requestId);
                if(!requestIdToSampleMap.containsKey(requestId)) {
                    requestIdToSampleMap.put(requestId, new LinkedList<>());
                }
                requestIdToSampleMap.get(requestId).add(sampleId);
                logInfo("Added " + sampleId + " for request " + requestId + " to the request to samples map");
            }

            if (isDNAQcReportStep) {
                if(attachedQcDNAReports.isEmpty()) {
                    this.clientCallback.displayError("No DNA QC report attached to this task.");
                    return new PluginResult(false);
                }
                qcReports.addAll(attachedQcDNAReports);
                logInfo("qcReports populated for DNA");

            } else if (isRNAQcReportStep) {
                if(attachedQcRNAReports.isEmpty()) {
                    this.clientCallback.displayError("No RNA QC report attached to this task.");
                    return new PluginResult(false);
                }
                qcReports.addAll(attachedQcRNAReports);
                logInfo("qcReports populated for RNA");
            }
            List<DataRecord> qcReportRecords = getQcReportRecordsForSamples(sampleObjects, isDNAQcReportStep);
            for (DataRecord sample : attachedSamples) {
                String recipe = sample.getStringVal("Recipe", user);
                double mass = sample.getDoubleVal("TotalMass", user);
                String igoId = sample.getStringVal("SampleId", user);
                String preservation = sample.getStringVal("Preservation", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);
                String requestId = getRequestId(igoId);
                DataRecord currentSampleQcReportRec = null;
                for (DataRecord qcReport : qcReportRecords) {
                    if (qcReport.getStringVal("SampleId", user).trim().equalsIgnoreCase(igoId)) {
                        currentSampleQcReportRec = qcReport;
                    }
                }
                DataRecord relatedPassFailCriteriaRecords = getPassFailCriteria(sample, currentSampleQcReportRec);
            }

        } catch (Exception e) {

        }
        this.activeTask.getTask().getTaskOptions().put("_Autofilled", "");
        return new PluginResult(true);
    }


    public DataRecord getPassFailCriteria(DataRecord sample, DataRecord qcReportRecord) {
        List<DataRecord> qcRecords = new LinkedList<>();
        try {
            String recipe = sample.getStringVal("Recipe", user);
            double mass = sample.getDoubleVal("TotalMass", user);
            String preservation = sample.getStringVal("Preservation", user);
            String sampleType = sample.getStringVal("ExemplarSampleType", user);
            String A260230 = qcReportRecord.getStringVal("A260230", user);
            String A260280 = qcReportRecord.getStringVal("A260280", user);
            String DV200 = qcReportRecord.getStringVal("DV200", user);
            String RIN = qcReportRecord.getStringVal("RIN", user);
            String DIN = qcReportRecord.getStringVal("DIN", user);

            String whereClause = String.format("%s='%s'", "Recipe", recipe);
            qcRecords = dataRecordManager.queryDataRecords("QCPassFailCriteria", whereClause , user);
            List<DataRecord> shrinkedList = new LinkedList<>();
            for (DataRecord passFailCriteria : qcRecords) { // Or checking all criteria for a sample in a row
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("ExemplarSampleType", user)) &&
                    !passFailCriteria.getStringVal("ExemplarSampleType", user).isEmpty()) {
                    if (sampleType.trim().equalsIgnoreCase(passFailCriteria.getStringVal("ExemplarSampleType", user))) {
                        shrinkedList.add(passFailCriteria);
                    }
                }
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("Preservation", user)) &&
                        !passFailCriteria.getStringVal("Preservation", user).isEmpty()) {
                    if (preservation.trim().equalsIgnoreCase(passFailCriteria.getStringVal("Preservation", user))) {
                        shrinkedList.add(passFailCriteria);
                    }
                }
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("A260230", user)) &&
                        !passFailCriteria.getStringVal("A260230", user).isEmpty()) {
                    String[] A260230Criteria = passFailCriteria.getStringVal("A260230", user).split("A260/230 >=");
                    if (A260230Criteria.length == 0) {
                        A260230Criteria = passFailCriteria.getStringVal("A260230", user).split("A260/230 <");
                        if (Double.parseDouble(A260230) < Double.parseDouble(A260230Criteria[1])) {

                        }
                    }
                    else {
                        if (Double.parseDouble(A260230) >= Double.parseDouble(A260230Criteria[1])) {

                        }
                    }
                }
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("A260280", user)) &&
                        !passFailCriteria.getStringVal("A260280", user).isEmpty()) {
                    if (A260280.trim().equalsIgnoreCase(passFailCriteria.getStringVal("A260280", user))) {

                    }
                }
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("DV200", user)) &&
                        !passFailCriteria.getStringVal("DV200", user).isEmpty()) {
                    if (DV200.trim().equalsIgnoreCase(passFailCriteria.getStringVal("DV200", user))) {

                    }
                }
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("RIN", user)) &&
                        !passFailCriteria.getStringVal("RIN", user).isEmpty()) {
                    if (RIN.trim().equalsIgnoreCase(passFailCriteria.getStringVal("RIN", user))) {

                    }
                }
                if (!StringUtils.isBlank(passFailCriteria.getStringVal("DIN", user)) &&
                        !passFailCriteria.getStringVal("DIN", user).isEmpty()) {
                    if (DIN.trim().equalsIgnoreCase(passFailCriteria.getStringVal("DIN", user))) {

                    }
                }

            }

        } catch (NotFound | IoError | ServerException | RemoteException e) {
            logError(String.format("Exception while querying QCPassFailCriteria table for sample: %s" + e.getMessage()));
        }

    }

    /**
     * get QCDNAReport/QCRNAReport DataRecords for a sample.
     *
     * @param sampleIdList
     * @return List<DataRecord>
     * @throws RemoteException
     * @throws IoError
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getQcReportRecordsForSamples(List<Object> sampleIdList, boolean isDNAQcReportStep) {
        List<DataRecord> qcReportRecords = new ArrayList<>();
        try {
            if (isDNAQcReportStep) {
                qcReportRecords = dataRecordManager.queryDataRecords("QcReportDna", "SampleId", sampleIdList, user);
            }
            else if (isRNAQcReportStep) {
                qcReportRecords = dataRecordManager.queryDataRecords("QcReportRna", "SampleId", sampleIdList, user);
            }
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception while getting QC report records for attached Samples:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception while getting QC report records for attached Samples:\n%s", ExceptionUtils.getStackTrace(ioError)));
        } catch (ServerException e) {
            logError(String.format("ServerException while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting QC report records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return qcReportRecords;
    }
    /**
     * Method to get base Sample ID when aliquot annotation is present.
     * Example: for sample id 012345_1_1_2, base sample id is 012345
     * Example2: for sample id 012345_B_1_1_2, base sample id is 012345_B
     * @param sampleId
     * @return requestId
     */
    public static String getRequestId(String sampleId){
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
