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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class QcPassFailCriteria extends DefaultGenericPlugin {
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
            List<Object> sampleObjects = new LinkedList<>();
            for (DataRecord sample : attachedSamples) {
                sampleObjects.add(sample);
            }
            List<DataRecord> qcReports = getQcReportRecordsForSamples(sampleObjects, isDNAQcReportStep);
            for (DataRecord sample : attachedSamples) {
                String recipe = sample.getStringVal("Recipe", user);
                int mass = Integer.parseInt(sample.getStringVal("TotalMass", user));
                String igoId = sample.getStringVal("SampleId", user);
                String preservation = sample.getStringVal("Preservation", user);
                String sampleType = sample.getStringVal("ExemplarSampleType", user);


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
                                    if (true) { // all samples passed
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
                                    if (true) { // all samples passed
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
                                    if (true) { // all samples passed
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
            //clientCallback.displayError(errMsg);
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
}
