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
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    public boolean shouldRun() throws RemoteException {
        this.logInfo("");
        return this.activeTask.getTask().getTaskOptions().containsKey("Autofill QC DECISION COMMENT") && !this.activeTask.getTask().getTaskOptions().containsKey("_Autofilled");
    }
    public PluginResult run() throws RemoteException {
        try {
            if (this.activeTask.getTask().getTaskName().contains("Generate QC Report DNA")) {
                this.isDNAQcReportStep = true;
            }
            if (this.activeTask.getTask().getTaskName().contains("Generate QC Report RNA")) {
                this.isRNAQcReportStep = true;
            }
            List<DataRecord> attachedSamples = this.activeTask.getAttachedDataRecords("Sample", this.user);
            List<DataRecord> attachedQcDNAReports = this.activeTask.getAttachedDataRecords("QcReportDna", this.user);
            List<DataRecord> attachedQcRNAReports = this.activeTask.getAttachedDataRecords("QcReportRna", this.user);
            List<Object> sampleObjects = new LinkedList<>(attachedSamples);
            List<DataRecord> qcReports = getQcReportRecordsForSamples(sampleObjects, isDNAQcReportStep);
            Map<String, List<DataRecord>> requestIdToSampleMap = new HashMap<>();
            Map<String, Boolean> requestToAllSamplesQcStatus = new HashMap<>();

            for (DataRecord sample : attachedSamples) {
                String requestId = getRequestId(sample.getStringVal("SampleId", user));
                if(!requestIdToSampleMap.containsKey(requestId)) {
                    requestIdToSampleMap.put(requestId, new LinkedList<>());
                }
                requestIdToSampleMap.get(requestId).add(sample);
            }

            for (Map.Entry<String, List<DataRecord>> entry : requestIdToSampleMap.entrySet()) {
                boolean currentRequestSamplesQcStatus = true;
                List<Object> entryValueObjects = new LinkedList<>(entry.getValue());
                List<DataRecord> qcRecs = getQcRecordsForSamples(entryValueObjects);
                for (DataRecord qcRecords : qcRecs) {
                    if(!qcRecords.getBooleanVal("QCStatus", user)) {
                        currentRequestSamplesQcStatus = false;
                        break;
                    }
                }
                requestToAllSamplesQcStatus.put(entry.getKey(), currentRequestSamplesQcStatus);
            }



            for (DataRecord sample : attachedSamples) {
                String recipe = sample.getStringVal("Recipe", user);
                int mass = Integer.parseInt(sample.getStringVal("TotalMass", user));
                String igoId = sample.getStringVal("SampleId", user);
                String preservation = sample.getStringVal("Preservation", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);
                String requestId = getRequestId(igoId);

                if (recipe.toLowerCase().equals("ampliconseq")) {
                    if (isDNAQcReportStep) {
                        if(attachedQcDNAReports.isEmpty()) {
                            this.clientCallback.displayError("No DNA QC report attached to this task.");
                            return new PluginResult(false);
                        }

                    } else if (isRNAQcReportStep) {
                        if(attachedQcRNAReports.isEmpty()) {
                            this.clientCallback.displayError("No RNA QC report attached to this task.");
                            return new PluginResult(false);
                        }
                    }
                    if (mass > 100) {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                            }
                        }

                    } else if (10 < mass && mass < 100) {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                qcReport.setDataField("Comments", "Suboptimal quantity", user);
                            }
                        }

                    } else if (mass < 10) {
                        for (DataRecord qcReport : qcReports) {
                            if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                qcReport.setDataField("Comments", "Low quantity", user);
                            }
                        }
                    }
                } else if (recipe.toLowerCase().equals("chipseq")) {
                    if (mass >= 10) {
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

                } else if (recipe.toLowerCase().equals("impact505")) {
                    if (sampleType.equalsIgnoreCase("cfDNA")) {
                        if (mass > 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    if (requestToAllSamplesQcStatus.get(requestId)) { // all samples passed
                                        qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
                                    }
                                }
                            }

                        } else if (mass > 5 && mass < 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                    qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                }
                            }
                        } else if (mass < 5) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                    qcReport.setDataField("Comments", "Low quantity", user);
                                }
                            }
                        }
                    }
                    if (preservation.equalsIgnoreCase("FFPE")) {
                        if (mass > 200) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    if (requestToAllSamplesQcStatus.get(requestId)) { // all samples passed
                                        qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
                                    }
                                }
                            }

                        } else if (mass > 40 && mass < 200) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                    qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                }
                            }
                        } else if (mass < 40) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                    qcReport.setDataField("Comments", "Low quantity", user);
                                }
                            }
                        }
                    }
                    else { // other preservations than FFPE
                        if (mass > 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Passed", user);
                                    if (requestToAllSamplesQcStatus.get(requestId)) { // all samples passed
                                        qcReport.setDataField("InvestigatorDecision", "Already moved forward by IGO", user);
                                    }
                                }
                            }

                        } else if (mass > 20 && mass < 100) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Try", user);
                                    qcReport.setDataField("Comments", "Suboptimal quantity", user);
                                }
                            }

                        } else if (mass < 20) {
                            for (DataRecord qcReport : qcReports) {
                                if (igoId.equals(qcReport.getStringVal("SampleId", user))) {
                                    qcReport.setDataField("IgoQcRecommendation", "Failed", user);
                                    qcReport.setDataField("Comments", "Low quantity", user);
                                }
                            }
                        }

                    }
                }
            }
        } catch (NotFound | ServerException | IoError | InvalidValue | RemoteException e) {
            String errMsg = String.format("Remote Exception while QC report generation:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
            return new PluginResult(false);
        }

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
     * @return
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
