package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
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
 * This plugin is designed to generate a QC report for samples based on the sample types attached to the task where the report is generated.
 * Plugin will be used in "Quality control" and "Library/Pool Quality control" workflow. It generates three kind of dataRecords, 1. "QcReportForDna" when
 * ExemplarSampleType is "DNA, cDNA or cfDNA", 2. "QcReportForRna" when ExemplarSampleType is "RNA", and 3. "QcReportForLibrary" when ExemplarSampleType
 * is "DNA Library, cDNA Library or Pooled Library".
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class QcReportGenerator extends DefaultGenericPlugin {

    private final String QC_TYPE_FOR_DIN = "tapestation sampletable";
    private final String QC_TYPE_FOR_RQN = "fragment analyzer rna quality, fragment analyzer peak table";
    private final String QC_TYPE_FOR_DV200 = "fragment analyzer smear table, bioanalyzer rna pico, bioanalyzer rna nano, tapestation rna screentape hisense compactregion table, tapestation rna screentape compactregion table";
    private final String QC_TYPE_FOR_RIN = "bioanalyzer rna pico, bioanalyzer rna nano, tapestation rna screentape hisense sample table, tapestation rna screentape sample table";
    private final String QC_TYPE_FOR_A260280 = "nanodrop nano";
    private final String QC_TYPE_FOR_A260230 = "nanodrop nano";
    private final String QC_TYPE_FOR_AVERAGE_BP_SIZE = "TapeStation Compact Peak Table, TapeStation Compact Pico Region Table, " +
            "TapeStation D1000 Compact Region Table, TapeStation D1000 HiSense Compact Region Table, Bioanalyzer DNA High Sens Region Table";
    private final String TAPESTATION_QC_FOR_AVERAGE_BP_SIZE = "TapeStation Compact Peak Table, TapeStation Compact Pico Region Table, " +
            "TapeStation D1000 Compact Region Table, TapeStation D1000 HiSense Compact Region Table";
    private final String BIOA_QC_FOR_AVERAGE_BP_SIZE = "Bioanalyzer DNA High Sens Region Table";
    private final List<String> DNA_SAMPLE_TYPES = Arrays.asList("cdna", "cfdna", "dna", "hmwdna", "uhmwdna");
    private final List<String> RNA_SAMPLE_TYPES = Arrays.asList("rna");
    private final List<String> LIBRARY_SAMPLE_TYPES = Arrays.asList("dna library", "cdna library", "dna/cdna library", "pooled library", "protein library");
    private final double NANOMOLAR_TO_FEMTOMOLAR_CONVERSION_FACTOR = 1000000.00;

    public QcReportGenerator() {
        setTaskEntry(true);
        setLine1Text("Generate");
        setLine2Text("BioMek File");
        setOrder(PluginOrder.EARLY.getOrder());
        setIcon("com/velox/sloan/cmo/resources/import_32.gif");
    }

    @Override
    public boolean shouldRun() throws RemoteException, ServerException, NotFound {
        if (activeTask.getTaskName().equals("Generate QC report for DNA") && activeTask.getTask().getTaskOptions().containsKey("GENERATE QC REPORT")) {
            return DNA_SAMPLE_TYPES.contains(activeTask.getAttachedDataRecords("Sample", user).get(0).getStringVal("ExemplarSampleType", user).toLowerCase()) && activeTask.getStatus() != ActiveTask.COMPLETE;
        }

        if (activeTask.getTaskName().equals("Generate QC report for RNA") && activeTask.getTask().getTaskOptions().containsKey("GENERATE QC REPORT")) {
            return RNA_SAMPLE_TYPES.contains(activeTask.getAttachedDataRecords("Sample", user).get(0).getStringVal("ExemplarSampleType", user).toLowerCase()) && activeTask.getStatus() != ActiveTask.COMPLETE;
        }

        if (activeTask.getTaskName().equals("Generate QC report for Libraries") && activeTask.getTask().getTaskOptions().containsKey("GENERATE QC REPORT")) {
            return LIBRARY_SAMPLE_TYPES.contains(activeTask.getAttachedDataRecords("Sample", user).get(0).getStringVal("ExemplarSampleType", user).toLowerCase()) && activeTask.getStatus() != ActiveTask.COMPLETE;
        }
        return false;
    }

    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            if (samples.size() == 0) {
                clientCallback.displayError(String.format("Sample attachments not found on task: %s", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            boolean hasPool = false;
            String poolsSampleNamesStr = "(";
            // Pools
            for (int i = 0; i < samples.size(); i++) {
                if (samples.get(i).getStringVal("SampleId", user).toLowerCase().startsWith("pool-")) {
                    hasPool = true;
                    if (!StringUtils.isBlank(samples.get(i).getStringVal("OtherSampleId", user))) {
                        if (i < samples.size() - 1) {
                            poolsSampleNamesStr += "'" + samples.get(i).getStringVal("OtherSampleId", user) + "',";
                        } else {
                            poolsSampleNamesStr += "'" + samples.get(i).getStringVal("OtherSampleId", user);
                        }
                    }
                }
            }
            poolsSampleNamesStr += "')";
            logInfo("poolsSampleNames is: " + poolsSampleNamesStr);

            List<Object> listOfPoolSampleNames = new LinkedList<>();
            for (DataRecord s : samples) {
                if (s.getStringVal("SampleId", user).toLowerCase().startsWith("pool-")) {
                    String [] arrayOfNames = s.getDataField("OtherSampleId", user).toString().split(",");
                    for (int i = 0; i < arrayOfNames.length; i++) {
                        listOfPoolSampleNames.add(arrayOfNames[i]);
                    }

                }
            }
            logInfo("listOfPoolSampleNames size = " + listOfPoolSampleNames.size());
            String strOfPoolSampleIds = "";
            List<Object> sampleIds = getSampleIds(samples);
            for (Object s : sampleIds) {
                if (s.toString().toLowerCase().startsWith("pool-")) {
                    strOfPoolSampleIds += s.toString() + ",";
                }
            }
            String reportName = getReportName(samples.get(0));
            List<DataRecord> attachedReportData = activeTask.getAttachedDataRecords(reportName, user);

            if (attachedReportData.size() > 0) {
                activeTask.removeTaskAttachments(getAttachedRecordIds(attachedReportData));
                dataRecordManager.deleteDataRecords(attachedReportData, null, false, user);
            }
            List<DataRecord> qcRecords = getQcRecordsForSamples(sampleIds);
            List<DataRecord> seqReqRecords = getSequencingRequirementsForSamples(sampleIds, strOfPoolSampleIds, hasPool, listOfPoolSampleNames);
            List<DataRecord> qcProtocolRecords = getQcProtocolRecordsForSamples(sampleIds);
            if (qcRecords.size() < sampleIds.size()) {
                clientCallback.displayWarning(String.format("Number of QC Records found: %d are LESS than number of samples attached %d." +
                        "\nPlease make sure all the samples have at least one QC record.", qcRecords.size(), sampleIds.size()));
            }
            generateQcReport(samples, qcRecords, qcProtocolRecords, seqReqRecords);
        } catch (NotFound | RemoteException e) {
            String errMsg = String.format("Remote Exception while QC report generation:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * get QcReport DataType name based on sample type attached to the workflow task.
     *
     * @param sample
     * @return String
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getReportName(DataRecord sample) {
        try {
            String sampleType = null;
            if (sample.getValue("ExemplarSampleType", user) != null) {
                sampleType = sample.getStringVal("ExemplarSampleType", user).toLowerCase();
            }
            if (StringUtils.isBlank(sampleType)) {
                clientCallback.displayError("ExemplarSampleType value missing on at least one Samples attached to this Task.");
            }
            if (RNA_SAMPLE_TYPES.contains(sampleType)) {
                return "QcReportRna";
            }
            if (DNA_SAMPLE_TYPES.contains(sampleType)) {
                return "QcReportDna";
            }
            if (LIBRARY_SAMPLE_TYPES.contains(sampleType)) {
                return "QcReportLibrary";
            }
        } catch (NotFound notFound) {
            logError(String.format("Failed to get report name/type:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (RemoteException re) {
            logError(String.format("RemoteException while getting report name/type:\n%s", ExceptionUtils.getStackTrace(re)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting report name/type:\n%s", ExceptionUtils.getStackTrace(se)));
        }
        return "";
    }

    /**
     * get RecordId values for DataRecords.
     *
     * @param records
     * @return List<long>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Long> getAttachedRecordIds(List<DataRecord> records) {

        List<Long> ids = new ArrayList<>();
        for (DataRecord rec : records) {
            long recId = rec.getRecordId();
            ids.add(recId);
        }
        return ids;
    }


    /**
     * Get SampleId values for the DataRecords.
     *
     * @param samples
     * @return List<Object>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Object> getSampleIds(List<DataRecord> samples) {
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

    private List<DataRecord> getSequencingRequirementsForSamples(List<Object> sampleIdList, String strOfPoolSampleIds, boolean hasPool, List<Object> poolsSampleNames) {
        List<DataRecord> seqReqRecords = new ArrayList<>();
        try {
            if (hasPool) {
                List<DataRecord> poolSeqReqs = dataRecordManager.queryDataRecords("SeqRequirement", "OtherSampleId", poolsSampleNames, user);
                logInfo("poolSeqReqs.size() = " + poolSeqReqs.size());
                List<DataRecord> poolSeqReqExact = new LinkedList<>();


                for(DataRecord psr : poolSeqReqs) {
                    logInfo("igo id: " + psr.getStringVal("SampleId", user));
                    String idOfSample = psr.getStringVal("SampleId", user).split("_")[0] + "_" + psr.getStringVal("SampleId", user).split("_")[1];
                    logInfo("id of the sample = " + idOfSample);
                    if (strOfPoolSampleIds.contains(idOfSample)) {
                        poolSeqReqExact.add(psr);
                    }
                }

                logInfo("poolSeqReqExact size = "  + poolSeqReqExact.size());
                for (DataRecord d : poolSeqReqExact) {
                    seqReqRecords.add(d);
                }
            }

            List<DataRecord> libSeqReqs = dataRecordManager.queryDataRecords("SeqRequirement", "SampleId", sampleIdList, user);
            for (DataRecord d : libSeqReqs) {
                seqReqRecords.add(d);
            }

            logInfo("seqReqRecords size is: " + seqReqRecords.size());
        } catch (NotFound | IoError | ServerException | RemoteException e) {
            logError(String.format("An Exception occurred while getting sequencing requirements records for attached Samples:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return seqReqRecords;
    }

    /**
     * get QCDatum records based on type of QC.
     *
     * @param sampleId
     * @param qcDataRecords
     * @param QcType
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<DataRecord> getQcRecordsByQcType(String sampleId, List<DataRecord> qcDataRecords, String QcType) {
        List<DataRecord> sampleQcRecordsByQcType = new ArrayList<>();
        for (DataRecord qcRecord : qcDataRecords) {
            try {
                String qcRecordSampleId = qcRecord.getStringVal("SampleId", user);
                String qcDatumType = qcRecord.getStringVal("DatumType", user).toLowerCase();
                if (qcRecordSampleId.equals(sampleId) && QcType.toLowerCase().contains(qcDatumType)) {
                    sampleQcRecordsByQcType.add(qcRecord);
                }
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception while finding QcRecord field(s) SampleId/DatumType values for Sample with recordid %d:\n%s", ExceptionUtils.getStackTrace(notFound)));
            } catch (RemoteException e) {
                logError(String.format("Error while finding QcRecord field(s) SampleId/DatumType values for Sample with recordid %d:\n%s", ExceptionUtils.getStackTrace(e)));
            }

        }
        return sampleQcRecordsByQcType;
    }

    /**
     * get most recently added QCDatum record for a sample
     *
     * @param qcRecords
     * @return DataRecord
     * @throws NotFound
     * @throws RemoteException
     */
    private DataRecord getMostRecentQcRecord(List<DataRecord> qcRecords) {
        if (qcRecords.size() == 1) {
            return qcRecords.get(0);
        }
        DataRecord mostRecentQcRecord = qcRecords.get(0);
        for (DataRecord record : qcRecords) {
            long recordId = record.getRecordId();
            if (recordId > mostRecentQcRecord.getRecordId()) {
                mostRecentQcRecord = record;
            }
        }
        return mostRecentQcRecord;
    }

    /**
     * get DIN value from QCDatum records for sample.
     *
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getDinValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) {
        List<DataRecord> qcRecordsForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_DIN);
        Double dinValue = 0.00;
        try {
            if (!qcRecordsForSample.isEmpty() && getMostRecentQcRecord(qcRecordsForSample).getValue("DIN", user) != null) {
                dinValue = getMostRecentQcRecord(qcRecordsForSample).getDoubleVal("DIN", user);
            }
            if (qcRecordsForSample.isEmpty() || getMostRecentQcRecord(qcRecordsForSample).getValue("DIN", user) == null || dinValue <= 0) {
                clientCallback.displayWarning(String.format("DIN value not found for '%s'.", sampleId));
                logInfo(String.format("WARNING: DIN value not found for '%s'.", sampleId));
                return 0.00;
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting DIN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing DIN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting DIN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return dinValue;
    }

    /**
     * get RQN value from QCDatum records for sample.
     *
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getRqnValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) {
        List<DataRecord> qcRecordsWithRqnForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_RQN);
        Double rqnValue = 0.00;
        try {
            if (!qcRecordsWithRqnForSample.isEmpty()) {
                for (DataRecord qcRecord : qcRecordsWithRqnForSample) {
                    if (qcRecord.getValue("RQN", user) != null) {
                        rqnValue = qcRecord.getDoubleVal("RQN", user);
                    }
                }
            }
            if (qcRecordsWithRqnForSample.isEmpty() || rqnValue.isNaN() || rqnValue <= 0) {
                clientCallback.displayWarning(String.format("RQN value not found for '%s'.", sampleId));
                logInfo(String.format("WARNING: RQN value not found for '%s'.", sampleId));
                return 0.00;
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting RQN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing RQN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting RQN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return rqnValue;
    }

    /**
     * get RIN value from QCDatum records for sample.
     *
     * @param sampleId
     * @param qcRecords
     * @return String
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getRinValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) {
        List<DataRecord> qcRecordsWithRinForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_RIN);
        String rinValue = "";
        try {
            if (!qcRecordsWithRinForSample.isEmpty()) {
                for (DataRecord qcRecord : qcRecordsWithRinForSample) {
                    if (qcRecord.getValue("RIN", user) != null && !StringUtils.isBlank(qcRecord.getValue("RIN", user).toString())) {
                        rinValue = qcRecord.getStringVal("RIN", user);
                    }
                }
            }
            if (qcRecordsWithRinForSample.isEmpty() || StringUtils.isBlank(rinValue)) {
                clientCallback.displayWarning(String.format("RIN value not found for '%s'.", sampleId));
                logInfo(String.format("WARNING: RIN value not found for '%s'.", sampleId));
                return "";
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting RIN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing RIN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting RIN Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return rinValue;
    }

    /**
     * get DV200 value from QCDatum records for sample.
     *
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getDv200ValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) {
        List<DataRecord> qcRecordswithDv200ForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_DV200);
        Double dv200Value = 0.00;
        try {
            if (!qcRecordswithDv200ForSample.isEmpty()) {
                for (DataRecord qcRecord : qcRecordswithDv200ForSample) {
                    if (qcRecord.getValue("DV200", user) != null) {
                        dv200Value = qcRecord.getDoubleVal("DV200", user);
                    }
                }
            }
            if (qcRecordswithDv200ForSample.isEmpty() || dv200Value.isNaN() || dv200Value <= 0) {
                clientCallback.displayWarning(String.format("DV200 value not found for '%s'.", sampleId));
                logError(String.format("WARNING: DV200 value not found for '%s'.", sampleId));
                return 0.00;
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting DV200 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing DV200 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting DV200 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return dv200Value;
    }

    private Double getA260280FromQcRecord(String sampleId, List<DataRecord> qcRecords) {
        List<DataRecord> qcRecordswithA260280ForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_A260280);
        Double A260280 = 0.00;
        try {
            if (!qcRecordswithA260280ForSample.isEmpty()) {
                for (DataRecord qcRecord : qcRecordswithA260280ForSample) {
                    if (qcRecord.getValue("A260280", user) != null) {
                        A260280 = qcRecord.getDoubleVal("A260280", user);
                    }
                }
            }
            if (qcRecordswithA260280ForSample.isEmpty() || A260280.isNaN() || A260280 <= 0) {
                clientCallback.displayWarning(String.format("A260280 value not found for '%s'.", sampleId));
                logError(String.format("WARNING: A260280 value not found for '%s'.", sampleId));
                return 0.00;
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting A260280 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing A260280 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting A260280 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return A260280;
    }

    private Double getA260230FromQcRecord(String sampleId, List<DataRecord> qcRecords) {
        List<DataRecord> qcRecordswithA260230ForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_A260230);
        Double A260230 = 0.00;
        try {
            if (!qcRecordswithA260230ForSample.isEmpty()) {
                for (DataRecord qcRecord : qcRecordswithA260230ForSample) {
                    if (qcRecord.getValue("A260230", user) != null) {
                        A260230 = qcRecord.getDoubleVal("A260230", user);
                    }
                }
            }
            if (qcRecordswithA260230ForSample.isEmpty() || A260230.isNaN() || A260230 <= 0) {
                clientCallback.displayWarning(String.format("A260230 value not found for '%s'.", sampleId));
                logError(String.format("WARNING: A260280 value not found for '%s'.", sampleId));
                return 0.00;
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting A260230 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing A260230 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting A260230 Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return A260230;
    }

    private String getNumOfReadsFromSeqReqRecord(String sampleId, String sampleName, List<DataRecord> seqReqRecords, boolean isPool) {
        String numOfReads = "";
        double poolNumOfReads = 0.0;

        for (DataRecord eachSeqRec : seqReqRecords) {
            try {
                if (eachSeqRec.getDataField("RequestedReads", user) != null) {
                    if (isPool && eachSeqRec != null) {
                        String SampleRequest = sampleId.split("-")[1]; // input: Pool-05500_IY-Tube1, output: 05500_IY
                        if (eachSeqRec.getStringVal("SampleId", user).contains(SampleRequest) &&
                                sampleName.contains(eachSeqRec.getStringVal("OtherSampleId", user))) {

                            poolNumOfReads += Double.parseDouble(eachSeqRec.getDataField("RequestedReads", user).toString());
                            logInfo("eachSeqRec igo id = " + eachSeqRec.getStringVal("SampleId", user) +
                                    " eachSeqRec sample name = " + eachSeqRec.getStringVal("OtherSampleId", user));
                            logInfo("poolNumOfReads = " + poolNumOfReads);
                            numOfReads = String.valueOf(poolNumOfReads);
                        }

                    }
                    else if (sampleId.equals(eachSeqRec.getStringVal("SampleId", user))) {
                        numOfReads = eachSeqRec.getDataField("RequestedReads", user).toString();
                    }
                }

            } catch (NotFound e) {
                logError("NotFound while getting requested reads from sequencing requirement record of the sample.");
            } catch (RemoteException e) {
                logError("RemoteException while getting requested reads from sequencing requirement record of the sample.");
            }
        }
        return numOfReads;
    }
    /**
     * get QcProtocol records for samples.
     *
     * @param sampleIdList
     * @return List<DataRecord>
     * @throws RemoteException
     * @throws IoError
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getQcProtocolRecordsForSamples(List<Object> sampleIdList) {
        List<DataRecord> protocolRecords = new ArrayList<>();
        try {
            protocolRecords = dataRecordManager.queryDataRecords("QCProtocol", "SampleId", sampleIdList, user);
        } catch (NotFound notFound) {
            logError(String.format("NotFound Error while getting QcProtocol Records:\n%s", ExceptionUtils.getStackTrace(notFound)));
        } catch (IoError ioError) {
            logError(String.format("IoError while getting QcProtocol Records:\n%s", ExceptionUtils.getStackTrace(ioError)));
        } catch (ServerException e) {
            logError(String.format("ServerException while getting QcProtocol Records:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting QcProtocol Records:\n%s", ExceptionUtils.getStackTrace(e)));
        }
        return protocolRecords;
    }

    /**
     * get IgoRecommendation value from QcProtocol records for sample
     *
     * @param sampleId
     * @param qcProtocolRecords
     * @return String
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getIgoRecommendationValue(String sampleId, List<DataRecord> qcProtocolRecords) {
        String igoRecommendationValue = "";
        try {
            if (!qcProtocolRecords.isEmpty()) {
                for (DataRecord qcRecord : qcProtocolRecords) {
                    if (qcRecord.getValue("SampleId", user).equals(sampleId) && qcRecord.getValue("IGOQC", user) != null) {
                        igoRecommendationValue = qcRecord.getStringVal("IGOQC", user);
                    }
                }
            }
            if (qcProtocolRecords.isEmpty() || StringUtils.isBlank(igoRecommendationValue)) {
                clientCallback.displayWarning(String.format("IGO Recommendation value not found for '%s'.", sampleId));
                logError(String.format("WARNING: IGO Recommendation value not found for '%s'.", sampleId));
                return "";
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting IGOQC Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing IGOQC Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting IGOQC Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return igoRecommendationValue;
    }

    /**
     * get QcComments value from QcProtocol records for sample.
     *
     * @param sampleId
     * @param qcDataRecords
     * @return String
     * @throws NotFound
     * @throws RemoteException
     */
    private String getQcCommentsValue(String sampleId, List<DataRecord> qcDataRecords) {
        String igoQcCommentsValue = "";
        try {
            if (!qcDataRecords.isEmpty()) {
                for (DataRecord qcRecord : qcDataRecords) {
                    if (qcRecord.getValue("SampleId", user).equals(sampleId) && qcRecord.getValue("Comments_Field", user) != null) {
                        igoQcCommentsValue = qcRecord.getStringVal("Comments_Field", user);
                    }
                }
            }
            if (qcDataRecords.isEmpty() || StringUtils.isBlank(igoQcCommentsValue)) {
                logInfo(String.format("WARNING: IGO QC comments not found for '%s'.", sampleId));
                return "";
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting Comments_Field Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing Comments_Field Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        }
        return igoQcCommentsValue;
    }

    /**
     * get AverageSize of Library for a Sample from QcProtocol records.
     *
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getAverageLibrarySizeValue(String sampleId, List<DataRecord> qcRecords/*, int selectedQcFile*/) {
        List<DataRecord> qcRecordsWithAvgBpSizeForSample = new LinkedList<>();
        //if (selectedQcFile == 0) {
            qcRecordsWithAvgBpSizeForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_AVERAGE_BP_SIZE);
        //}
//        else {
//            qcRecordsWithAvgBpSizeForSample = getQcRecordsByQcType(sampleId, qcRecords, TAPESTATION_QC_FOR_AVERAGE_BP_SIZE);
//        }

        Double averageBasePairSize = 0.0;
        try {
            if (!qcRecordsWithAvgBpSizeForSample.isEmpty()) {
                for (DataRecord qcRecord : qcRecordsWithAvgBpSizeForSample) {
                    if (qcRecord.getValue("AvgSize", user) != null && qcRecord.getValue("SampleId", user).equals(sampleId)) {
                        averageBasePairSize = qcRecord.getDoubleVal("AvgSize", user);
                    }
                }
            }
            if (qcRecordsWithAvgBpSizeForSample.isEmpty() || averageBasePairSize <= 0) {
                clientCallback.displayWarning(String.format("Average Size value not found for '%s'.", sampleId));
                logInfo(String.format("WARNING: Average Size value not found for '%s'.", sampleId));
                return 0.0;
            }
        } catch (RemoteException e) {
            logError(String.format("RemoteException while getting 'AvgSize' Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound -> Missing 'AvgSize' Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(notFound)));
        } catch (ServerException se) {
            logError(String.format("ServerException while getting 'AvgSize' Value for sample with Sample ID %s:\n%s", sampleId, ExceptionUtils.getStackTrace(se)));
        }
        return averageBasePairSize;
    }

    /**
     * Create DNA QC REPORT DataRecords for Samples.
     *
     * @param samples
     * @param qcDataRecords
     * @param qcProtocolRecords
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private List<DataRecord> generateDnaQcReportFieldValuesMap(List<DataRecord> samples, List<DataRecord> qcDataRecords, List<DataRecord> qcProtocolRecords) {
        List<DataRecord> dnaQcRecords = new ArrayList<>();
        for (DataRecord sample : samples) {
            Map<String, Object> qcRecord = new HashMap<>();
            try {
                String sampleId = sample.getStringVal("SampleId", user);
                qcRecord.put("SampleId", sampleId);
                qcRecord.put("OtherSampleId", sample.getStringVal("OtherSampleId", user));
                qcRecord.put("UserSampleID", sample.getStringVal("UserSampleID", user));
                qcRecord.put("AltId", sample.getStringVal("AltId", user));
                qcRecord.put("RequestId", sample.getStringVal("RequestId", user));
                qcRecord.put("Concentration", sample.getDoubleVal("Concentration", user));
                qcRecord.put("ConcentrationUnits", sample.getStringVal("ConcentrationUnits", user));
                qcRecord.put("Volume", sample.getDoubleVal("Volume", user));
                qcRecord.put("TotalMass", sample.getDoubleVal("TotalMass", user));
                qcRecord.put("SpecimenType", sample.getStringVal("SpecimenType", user));
                qcRecord.put("TumorOrNormal", sample.getStringVal("TumorOrNormal", user));
                qcRecord.put("Preservation", sample.getStringVal("Preservation", user));
                qcRecord.put("Recipe", sample.getStringVal("Recipe", user));
                List<DataRecord> listOfSamplesAncestors = sample.getAncestorsOfType("Sample", user);
                if(listOfSamplesAncestors != null && listOfSamplesAncestors.size() > 0) {
                    qcRecord.put("SourceSampleId", listOfSamplesAncestors.get(0).getValue("SampleId", user));
                }
                Double dinValue = getDinValueFromQcRecord(sampleId, qcDataRecords);
                Double A260280 = getA260280FromQcRecord(sampleId, qcDataRecords);
                Double A260230 = getA260230FromQcRecord(sampleId, qcDataRecords);
                String igoRecommendation = getIgoRecommendationValue(sampleId, qcProtocolRecords);
                String comments = getQcCommentsValue(sampleId, qcProtocolRecords);
                if (dinValue > 0) {
                    qcRecord.put("DIN", dinValue);
                }
                if (A260230 > 0) {
                    qcRecord.put("A260230", A260230);
                    logInfo("A260230 is assigned to " + A260230);
                }
                if (A260280 > 0) {
                    qcRecord.put("A260280", A260280);
                    logInfo("A260280 is assigned to " + A260280);
                }
                if (!StringUtils.isBlank(igoRecommendation)) {
                    qcRecord.put("IgoQcRecommendation", igoRecommendation);
                }
                if (!StringUtils.isBlank(comments)) {
                    qcRecord.put("Comments", comments);
                }
            } catch (RemoteException | IoError | ServerException e) {
                logError(String.format("Exception while setting QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception while setting QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }
            try {
                dnaQcRecords.add(sample.addChild("QcReportDna", qcRecord, user));
            } catch (RemoteException e) {
                logError(String.format("RemoteException while setting child record of type 'QcReportDna' on sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (ServerException se) {
                logError(String.format("ServerException while setting child record of type 'QcReportDna' on sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(se)));
            }
        }
        return dnaQcRecords;
    }

    /**
     * generate RNA QC REPORT DataRecords for Samples.
     *
     * @param samples
     * @param qcRecords
     * @param qcProtocolRecords
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private List<DataRecord> generateRnaQcReportFieldValuesMap(List<DataRecord> samples, List<DataRecord> qcRecords, List<DataRecord> qcProtocolRecords){
        List<DataRecord> rnaQcRecords = new ArrayList<>();
        for (DataRecord sample : samples) {
            Map<String, Object> qcRecord = new HashMap<>();
            try {
                String sampleId = sample.getStringVal("SampleId", user);
                qcRecord.put("SampleId", sampleId);
                qcRecord.put("OtherSampleId", sample.getStringVal("OtherSampleId", user));
                qcRecord.put("UserSampleID", sample.getStringVal("UserSampleID", user));
                qcRecord.put("AltId", sample.getStringVal("AltId", user));
                qcRecord.put("RequestId", sample.getStringVal("RequestId", user));
                qcRecord.put("Concentration", sample.getDoubleVal("Concentration", user));
                qcRecord.put("ConcentrationUnits", sample.getStringVal("ConcentrationUnits", user));
                qcRecord.put("Volume", sample.getDoubleVal("Volume", user));
                qcRecord.put("TotalMass", sample.getDoubleVal("TotalMass", user));
                qcRecord.put("Preservation", sample.getStringVal("Preservation", user));
                qcRecord.put("Recipe", sample.getStringVal("Recipe", user));
                List<DataRecord> listOfSamplesAncestors = sample.getAncestorsOfType("Sample", user);
                if(listOfSamplesAncestors != null && listOfSamplesAncestors.size() > 0) {
                    qcRecord.put("SourceSampleId", listOfSamplesAncestors.get(0).getValue("SampleId", user));
                }
                String rinValue = getRinValueFromQcRecord(sampleId, qcRecords);
                Double dv200Value = getDv200ValueFromQcRecord(sampleId, qcRecords);
                Double A260280 = getA260280FromQcRecord(sampleId, qcRecords);
                Double A260230 = getA260230FromQcRecord(sampleId, qcRecords);
                Double rqnValue = getRqnValueFromQcRecord(sampleId, qcRecords);
                String igoRecommendation = getIgoRecommendationValue(sampleId, qcProtocolRecords);
                String comments = getQcCommentsValue(sampleId, qcProtocolRecords);
                if (!StringUtils.isBlank(rinValue)) {
                    qcRecord.put("RIN", rinValue.split("\\(")[0]);
                }
                if (dv200Value > 0) {
                    qcRecord.put("DV200", dv200Value);
                }
                if (A260230 > 0) {
                    qcRecord.put("A260230", A260230);
                }
                if (A260280 > 0) {
                    qcRecord.put("A260280", A260280);
                }
                if (rqnValue > 0) {
                    qcRecord.put("RQN", rqnValue);
                }
                if (!StringUtils.isBlank(igoRecommendation)) {
                    qcRecord.put("IgoQcRecommendation", igoRecommendation);
                }
                if (!StringUtils.isBlank(comments)) {
                    qcRecord.put("Comments", comments);
                }
                rnaQcRecords.add(sample.addChild("QcReportRna", qcRecord, user));
            }catch (NotFound | IoError notFound) {
                logError(String.format("Exception while generating RNA QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }catch (RemoteException e) {
                logError(String.format("RemoteException while generating RNA QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (ServerException se) {
                logError(String.format("ServerException while generating RNA QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(se)));
            }
        }
        return rnaQcRecords;
    }

    /**
     * Generate LIBRARY QC REPORT DataRecords for Samples.
     *
     * @param samples
     * @param qcRecords
     * @param qcProtocolRecords
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private List<DataRecord> generateLibraryQcReportFieldValuesMap(List<DataRecord> samples, List<DataRecord> qcRecords, List<DataRecord> qcProtocolRecords, List<DataRecord> seqReqRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> libraryQcRecords = new ArrayList<>();
        //String[] stringListOfQcFiles = {"BioAnalyzer", "TapeStation"};
        //int selectedQcFile = 0;
//        try {
//            selectedQcFile = clientCallback.showOptionDialog("Selecting QC Average Size Bp",
//                    "Which QC file average size bp would you like to use: ", stringListOfQcFiles, 0);
//        }
//        catch (ServerException se) {
//            this.logError(String.valueOf(se.getStackTrace()));
//        }
        for (DataRecord sample : samples) {
            Map<String, Object> qcRecord = new HashMap<>();
            try {
                String sampleId = sample.getStringVal("SampleId", user);
                String sampleName = sample.getStringVal("OtherSampleId", user);
                qcRecord.put("SampleId", sampleId);
                qcRecord.put("OtherSampleId", sample.getStringVal("OtherSampleId", user));
                qcRecord.put("UserSampleID", sample.getStringVal("UserSampleID", user));
                qcRecord.put("AltId", sample.getStringVal("AltId", user));
                qcRecord.put("RequestId", sample.getStringVal("RequestId", user));
                qcRecord.put("Concentration", sample.getDoubleVal("Concentration", user));
                qcRecord.put("ConcentrationUnits", sample.getStringVal("ConcentrationUnits", user));
                qcRecord.put("Volume", sample.getDoubleVal("Volume", user));
                String attachedSampleTypes = samples.get(0).getStringVal("ExemplarSampleType", user);
                List<DataRecord> listOfSamplesAncestors = sample.getAncestorsOfType("Sample", user);
                if(listOfSamplesAncestors != null && listOfSamplesAncestors.size() > 0 && !attachedSampleTypes.toLowerCase().equals("pooled library")) {
                    qcRecord.put("SourceSampleId", listOfSamplesAncestors.get(0).getValue("SampleId", user));
                }
                if (sample.getStringVal("ConcentrationUnits", user).trim().equalsIgnoreCase("ng/uL")) {
                    qcRecord.put("TotalMass", sample.getDoubleVal("Concentration", user) * sample.getDoubleVal("Volume", user));
                } else {
                    qcRecord.put("TotalMass", sample.getDoubleVal("TotalMass", user) * NANOMOLAR_TO_FEMTOMOLAR_CONVERSION_FACTOR); //convert nM to fM by multiplying by 1000000
                }
                qcRecord.put("TumorOrNormal", sample.getStringVal("TumorOrNormal", user));
                qcRecord.put("Recipe", sample.getStringVal("Recipe", user));
                boolean isPool = sampleId.toLowerCase().startsWith("pool-");
                String numOfReads = getNumOfReadsFromSeqReqRecord(sampleId, sampleName, seqReqRecords, isPool);
                logInfo("num of reads = " + numOfReads);
                Double averageBpSize = getAverageLibrarySizeValue(sampleId, qcRecords/*, selectedQcFile*/);
                String igoRecommendation = getIgoRecommendationValue(sampleId, qcProtocolRecords);
                String comments = getQcCommentsValue(sampleId, qcProtocolRecords);
                if (averageBpSize > 0) {
                    qcRecord.put("AvgSize", averageBpSize);
                }
                if(!StringUtils.isBlank(numOfReads)) {
                    qcRecord.put("NumOfReads", numOfReads);
                }
                if (!StringUtils.isBlank(igoRecommendation)) {
                    qcRecord.put("IgoQcRecommendation", igoRecommendation);
                }
                if (!StringUtils.isBlank(comments)) {
                    qcRecord.put("Comments", comments);
                }
                libraryQcRecords.add(sample.addChild("QcReportLibrary", qcRecord, user));
            }catch (NotFound | IoError notFound) {
                logError(String.format("Exception while generating Library QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound)));
            }catch (RemoteException e) {
                logError(String.format("RemoteException while generating Library QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
            } catch (ServerException se) {
                logError(String.format("ServerException while generating Library QC Report values for sample with RecordId %d:\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(se)));
            }
        }
        return libraryQcRecords;
    }

    /**
     * Generate QC report records
     *
     * @param samples
     * @param qcRecords
     * @param qcProtocolRecords
     * @throws ServerException
     * @throws RemoteException
     * @throws NotFound
     */
    private void generateQcReport(List<DataRecord> samples, List<DataRecord> qcRecords, List<DataRecord> qcProtocolRecords, List<DataRecord> seqReqRecords) {
        try {
            String attachedSampleTypes = samples.get(0).getStringVal("ExemplarSampleType", user);
            if (DNA_SAMPLE_TYPES.contains(attachedSampleTypes.toLowerCase())) {
                List<DataRecord> dnaQcRecords = generateDnaQcReportFieldValuesMap(samples, qcRecords, qcProtocolRecords);
                activeTask.addAttachedDataRecords(dnaQcRecords);
            }
            if (RNA_SAMPLE_TYPES.contains(attachedSampleTypes.toLowerCase())) {
                List<DataRecord> rnaQcRecords = generateRnaQcReportFieldValuesMap(samples, qcRecords, qcProtocolRecords);
                activeTask.addAttachedDataRecords(rnaQcRecords);
            }
            if (LIBRARY_SAMPLE_TYPES.contains(attachedSampleTypes.toLowerCase())) {
                List<DataRecord> libraryQcRecords = generateLibraryQcReportFieldValuesMap(samples, qcRecords, qcProtocolRecords, seqReqRecords);
                activeTask.addAttachedDataRecords(libraryQcRecords);
            }
        }catch (NotFound notFound) {
            logError(String.format("NotFound Exception while generating QC Report:\n%s", ExceptionUtils.getStackTrace(notFound)));
        }catch (RemoteException e) {
            logError(String.format("RemoteException while generating QC Report:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (ServerException se) {
            logError(String.format("ServerException while generating QC Report:\n%s", ExceptionUtils.getStackTrace(se)));
        }
    }
}