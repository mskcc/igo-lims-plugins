package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import javax.xml.crypto.Data;
import java.rmi.RemoteException;
import java.util.*;

/**
 * This plugin is designed to repopulate QCDatum with the Average Library Size value from
 * Molar Concentration Assignment to update Lib QC Reports per the Calculate Molarity
 * step of the Lib/Pool QC workflow
 */
public class FixAvgLibSize extends DefaultGenericPlugin {

    public FixAvgLibSize() {
        setTaskSubmit(true);
        setOrder(PluginOrder.LAST.getOrder());
        setIcon("com/velox/sloan/cmo/resources/import_32.gif");
    }

    @Override
    public boolean shouldRun() throws RemoteException, ServerException, NotFound {
        if (activeTask.getTaskName().equals("Calculate Molarity") && activeTask.getTask().getTaskOptions().containsKey("FixAvgLibSize")) {
            return activeTask.getStatus() != ActiveTask.COMPLETE;
        }
        return false;
    }

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            if (samples.size() == 0) {
                clientCallback.displayError(String.format("Sample attachments not found on task: %s", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            List<Object> sampleIds = getSampleIds(samples);
            List<DataRecord> qcRecords = getQcRecordsForSamples(sampleIds, "QCDatum");
            List<DataRecord> qcRecordsMCA = getQcRecordsForSamples(sampleIds, "MolarConcentrationAssignment");

            if (qcRecords.size() < sampleIds.size()) {
                clientCallback.displayWarning(String.format("Number of QC Records found: %d are LESS than number of samples attached %d." +
                        "\nPlease make sure all the samples have at least one QC record.", qcRecords.size(), sampleIds.size()));
            }

            //for sample in qcrecords, for sample1 in qcrecordsMCA, if getAverageLibrarySizeValue(sample, qcrecords) =! getAverageLibrarySizeValue(sample1, qcrecordsMCA), sample.avgsize = sample1.avgsize
            updateAvgSize(qcRecords, qcRecordsMCA);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception while assigning Lib Avg Size:\n%s", ExceptionUtils.getStackTrace(e));
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    public List<DataRecord> getQcRecordsForSamples(List<Object> sampleIdList, String table) {
        List<DataRecord> qcRecords = new ArrayList<>();
        try {
            qcRecords = dataRecordManager.queryDataRecords(table, "SampleId", sampleIdList, user);
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

    public List<Object> getSampleIds(List<DataRecord> samples) {
        List<Object> sampleIds = new ArrayList<>();
        for (DataRecord sample : samples) {
            String sampleId = null;
            try {
                sampleId = sample.getStringVal("SampleId", user);
                logInfo("getSampleIds returns: " + sampleId);
            } catch (RemoteException e) {
                logError(String.format("Remote Exception while reading SampleId for sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound e) {
                logError(String.format("SampleId missing for sample with recordid %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            }
            if (!StringUtils.isBlank(sampleId)) {
                sampleIds.add(sampleId);
            }
        }
        return sampleIds;
    }

    public void updateAvgSize(List<DataRecord> QCDatum, List<DataRecord> MCA){
        for (DataRecord rectoupdate : QCDatum) {
            for (DataRecord rec : MCA) {
            try {
                String rectoupdateId = null;
                String recId = null;
                rectoupdateId = rectoupdate.getStringVal("SampleId", user).toLowerCase();//rectoupdate sample ID
                recId = rec.getStringVal("SampleId", user).toLowerCase();//rec sample ID
                Double rectoupdateavgsize = 0.0;
                Double recavgsize = 0.0;
                rectoupdateavgsize = rectoupdate.getDoubleVal("AvgSize", user);//rectoupdate avgsize
                recavgsize = rec.getDoubleVal("AvgSize", user);//rec avgsize
                if (rectoupdateId == recId && rectoupdateavgsize != recavgsize) {
                    rectoupdate.setDataField("AvgSize", recavgsize, user);
                }
                }catch (NotFound notFound) {
                    logError(String.format("NotFound Exception while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(notFound)));
                } catch (IoError ioError) {
                    logError(String.format("IoError Exception while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(ioError)));
                } catch (ServerException e) {
                    logError(String.format("ServerException while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
                } catch (RemoteException e) {
                    logError(String.format("RemoteException while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
                } catch (InvalidValue e) {
                    logError(String.format("RemoteException while getting QC records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
                }
            }
        }
    }
}





