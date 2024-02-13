package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOError;
import java.io.IOException;
import java.rmi.RemoteException;
import com.velox.api.util.ServerException;

import javax.xml.crypto.Data;
import java.util.*;
import java.util.regex.Pattern;

public class QcPassFailCriteria extends DefaultGenericPlugin {
    private final static String IGO_ID_WITHOUT_ALPHABETS_PATTERN = "^[0-9]+_[0-9]+.*$";  // sample id without alphabets
    private final static String IGO_ID_WITH_ALPHABETS_PATTERN = "^[0-9]+_[A-Z]+_[0-9]+.*$";  // sample id without alphabets
    public boolean isRNAQcReportStep = false;
    public boolean isDNAQcReportStep = false;
    public QcPassFailCriteria() {
        setTaskSubmit(true);
        setOrder(PluginOrder.FIRST.getOrder());
    }

    public boolean shouldRun() throws RemoteException {
        this.logInfo("");
        return this.activeTask.getTask().getTaskOptions().containsKey("Autofill QC DECISION COMMENT") && !this.activeTask.getTask().getTaskOptions().containsKey("_Autofilled");
    }
    public PluginResult run() throws RemoteException {
        try {
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

            Map<String, Integer> recipeToVolCoefficient = new HashMap<>()
            {{
                put("ampliconseq", 50);
                put("chipseq", 50);
                put("impact505", 50);
            }};
            for (DataRecord sample : attachedSamples) {
                String recipe = sample.getStringVal("Recipe", user);
                double mass = sample.getDoubleVal("TotalMass", user);
                double concentration = sample.getDoubleVal("Concentration", user);
                String igoId = sample.getStringVal("SampleId", user);
                String preservation = sample.getStringVal("Preservation", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);
                String requestId = getRequestId(igoId);

                logInfo("Logging sample info: \nIGO ID = " + igoId + "\nrecipe = " + recipe + "\ntotal mass = " + mass
                 + "\npreservation = " + preservation + "\nsample type = " + sampleType);

                double calculatedMass = concentration * recipeToVolCoefficient.get(recipe);
                if (recipe.trim().equalsIgnoreCase("ampliconseq")) {
                    logInfo("ampliconseq logic!");
                    if (calculatedMass >= 100) {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                            }
                        }

                    } else if (10 <= calculatedMass && calculatedMass < 100) {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                qcReport.setDataField("Comments", "Suboptimal quantity", user);
                            }
                        }

                    } else if (calculatedMass < 10) {
                        logInfo("mass is < 10");
                        for (DataRecord qcReport : qcReports) {
                            logInfo("amliconseq sample with mass < 10. Qc reports are getting modified!");
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                qcReport.setDataField("Comments", "Low quantity", user);
                            }
                        }
                    }
                } else if (recipe.trim().equalsIgnoreCase("chipseq")) {
                    if (calculatedMass >= 10) {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                            }
                        }
                    }
                    else {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                qcReport.setDataField("Comments", "Suboptimal quantity", user);
                            }
                        }
                    }

                } else if (recipe.trim().equalsIgnoreCase("impact505")) {
                    if (sampleType.equalsIgnoreCase("cfDNA")) {
                        logInfo("recipe impact 505, sample type cfdna");
                        if (calculatedMass >= 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    logInfo("requestToAllSamplesQcStatus.get(requestId) for requestId" + requestId + " is: " + requestToAllSamplesQcStatus.get(requestId));
                                }
                            }

                        } else if (calculatedMass >= 5 && calculatedMass < 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                    qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                }
                            }
                        } else if (calculatedMass < 5) {
                            logInfo("mass is < 10");
                            logInfo("qcReports size  = " + qcReports.size());
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    logInfo("SampleId matched!!");
                                    qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                    qcReport.setDataField("Comments", "Low quantity", user);
                                }
                            }
                        }
                    }
                    if (preservation.trim().equalsIgnoreCase("FFPE")) {
                        if (calculatedMass >= 200) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    logInfo("requestToAllSamplesQcStatus.get(requestId) = " + requestToAllSamplesQcStatus.get(requestId));
                                }
                            }

                        } else if (calculatedMass >= 40 && calculatedMass < 200) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                    qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                }
                            }
                        } else if (calculatedMass < 40) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                    qcReport.setDataField("Comments", "Low quantity", user);
                                }
                            }
                        }
                    }
                    else { // other preservations than FFPE
                        if (calculatedMass >= 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                }
                            }

                        } else if (calculatedMass >= 20 && calculatedMass < 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                    qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                }
                            }

                        } else if (calculatedMass < 20) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                    qcReport.setDataField("Comments", "Low quantity", user);
                                }
                            }
                        }
                    }
                }
                else if (recipe.trim().equalsIgnoreCase("ddpcr")) {
                    String[] assays = sample.getStringVal("Assay", user).split(";");
                    int minimumMassRequired = assays.length * 1;
                    if(isDNAQcReportStep) {
                        if (sampleType.equalsIgnoreCase("cDNA")) {
                            minimumMassRequired *= 9;
                            if (mass >= minimumMassRequired) {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    }
                                }
                            } else {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                        qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                    }
                                }
                            }
                        }
                        else { // Other sample types than cDNA
                            minimumMassRequired = assays.length * 20;
                            if (mass >= minimumMassRequired) {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    }
                                }
                            } else {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                        qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                    }
                                }
                            }
                        }
                    }
                    else { //RNA QC step
                        if (preservation.equalsIgnoreCase("FFPE")) {
                            minimumMassRequired = assays.length * 2;
                            if (mass >= minimumMassRequired) {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                        if (Integer.parseInt(qcReport.getStringVal("DV200", user)) >= 50) {
                                            qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                        }
                                        else {
                                            qcReport.setDataField("Comments", "Suboptimal quality", user);
                                        }
                                    }
                                }
                            }
                            else {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                        if (Integer.parseInt(qcReport.getStringVal("DV200", user)) >= 50) {
                                            qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                        }
                                        else {
                                            qcReport.setDataField("Comments", "Suboptimal quantity and suboptimal quality", user);
                                        }
                                    }
                                }
                            }
                        }
                        else { //preservation other than FFPE
                            if (mass >= minimumMassRequired) {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        if (Integer.parseInt(qcReport.getStringVal("RIN", user)) >= 6) {
                                            qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                        }
                                        else {
                                            qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                            qcReport.setDataField("Comments", "Suboptimal quality", user);
                                        }
                                    }
                                }
                            }
                            else {
                                for (DataRecord qcReport : qcReports) {
                                    if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                        qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                        if (Integer.parseInt(qcReport.getStringVal("RIN", user)) < 6) {
                                            qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                        }
                                        else {
                                            qcReport.setDataField("Comments", "Suboptimal quantity and suboptimal quality", user);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else if (recipe.trim().equalsIgnoreCase("")) {

                }
            }
            // Map of request ID to boolean value indicating if all samples under that request have igo QC recommendation value of "passed"
            for (Map.Entry<String, List<Object>> entry : requestIdToSampleMap.entrySet()) {
                boolean currentRequestSamplesQcStatus = true;
                //List<Object> entryValueObjects = new LinkedList<>(entry.getValue());
                List<DataRecord> qcRecs = getQcReportRecordsForSamples(entry.getValue(), isDNAQcReportStep);
                for (DataRecord qcRecords : qcRecs) {
                    if(!qcRecords.getStringVal("IgoQcRecommendation", user).trim().equalsIgnoreCase("passed")) {
                        currentRequestSamplesQcStatus = false;
                        logInfo("QC report of the project has a failed value.");
                        break;
                    }
                }
                requestToAllSamplesQcStatus.put(entry.getKey(), currentRequestSamplesQcStatus);
                logInfo("Value " + currentRequestSamplesQcStatus + " has been initialized for request " + entry.getKey() + " in requestToAllSamplesQcStatus map.");
            }

            // Setting the investigator decision for each sample based on the all samples in a request igo QC recommendation field value
            for (DataRecord sample : attachedSamples) {
                String recipe = sample.getStringVal("Recipe", user);
                double mass = sample.getDoubleVal("TotalMass", user);
                String igoId = sample.getStringVal("SampleId", user);
                String preservation = sample.getStringVal("Preservation", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);
                String requestId = getRequestId(igoId);

//                if (recipe.trim().equalsIgnoreCase("impact505")) {
//                    if (sampleType.equalsIgnoreCase("cfDNA")) {
//                        logInfo("recipe impact 505, sample type cfdna");
//                        if (mass >= 100) {
//                            for (DataRecord qcReport : qcReports) {
//                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
//                                    if (requestToAllSamplesQcStatus.get(requestId)) {
//                                        qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    if (preservation.trim().equalsIgnoreCase("FFPE")) {
//                        if (mass >= 200) {
//                            for (DataRecord qcReport : qcReports) {
//                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
//                                    if (requestToAllSamplesQcStatus.get(requestId)) {
//                                        qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    else { // other preservations than FFPE
//                        if (mass >= 100) {
//                            for (DataRecord qcReport : qcReports) {
//                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
//                                    if (requestToAllSamplesQcStatus.get(requestId)) {
//                                        qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
                // Below should work for all recipes. No need to check conditions again when the igo recommendation is passed!
                for (DataRecord qcReport : qcReports) {
                    if (igoId.equals(qcReport.getStringVal("SampleId", user)) &&
                            qcReport.getStringVal("IgoQcRecommendation", user).trim().equalsIgnoreCase("passed")) {
                        if (requestToAllSamplesQcStatus.get(requestId)) {
                            qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
                        }
                    }
                }
            }
        } catch (NotFound | ServerException | IoError | InvalidValue | RemoteException e) {
            String errMsg = String.format("Remote Exception while QC report generation:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
            return new PluginResult(false);
        }
        this.activeTask.getTask().getTaskOptions().put("_Autofilled", "");
        return new PluginResult(true);
    }

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
     * get QCDatum DataRecords for a sample.
     *
     * @param sampleIdList
     * @return List<DataRecord>
     * @throws RemoteException
     * @throws IoError
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getQcRecordsForSamples(List<Object> sampleIdList) {
        List<DataRecord> qcRecords = new ArrayList<>();
        try {
            qcRecords = dataRecordManager.queryDataRecords("QCDatum", "SampleId", sampleIdList, user);
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(ioError)));
        } catch (ServerException e) {
            logError(String.format("ServerException while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return qcRecords;
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
