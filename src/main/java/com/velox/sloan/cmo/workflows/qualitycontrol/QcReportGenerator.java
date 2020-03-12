package com.velox.sloan.cmo.workflows.qualitycontrol;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.managers.TaskUtilManager;
import org.apache.commons.lang3.StringUtils;

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
    private final String QC_TYPE_FOR_DV200 = "fragment analyzer smear table, bioanalyzer rna pico, bioanalyzer rna nano";
    private final String QC_TYPE_FOR_RIN = "bioanalyzer rna pico, bioanalyzer rna nano";
    private final String QC_TYPE_FOR_AVERAGE_BP_SIZE = "TapeStation Compact Peak Table, TapeStation Compact Pico Region Table, " +
            "TapeStation D1000 Compact Region Table, TapeStation D1000 HiSense Compact Region Table, Bioanalyzer DNA High Sens Region Table";
    private final List<String> DNA_SAMPLE_TYPES = Arrays.asList("cdna", "cfdna", "dna");
    private final List<String> RNA_SAMPLE_TYPES = Arrays.asList("rna");
    private final List<String> LIBRARY_SAMPLE_TYPES = Arrays.asList("dna library", "pooled library", "cdna library");
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

    public PluginResult run() throws ServerException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords("Sample", user);
            if (samples.size() == 0) {
                clientCallback.displayError(String.format("Sample attachments not found on task: %s", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            List<Object> sampleIds = getSampleIds(samples);
            String reportName = getReportName(samples.get(0));
            List<DataRecord> attachedReportData = activeTask.getAttachedDataRecords(reportName, user);

            if (attachedReportData.size() > 0){
                activeTask.removeTaskAttachments(getAttachedRecordIds(attachedReportData));
                dataRecordManager.deleteDataRecords(attachedReportData, null, false, user);
//                List<List<DataRecord>> sampleReportsToRemove = new ArrayList<>();
//                sampleReportsToRemove.add(attachedReportData);
//                dataRecordManager.removeChildren(samples, sampleReportsToRemove , user);
            }
            List<DataRecord> qcRecords = getQcRecordsForSamples(sampleIds);
            List<DataRecord> qcProtocolRecords = getQcProtocolRecordsForSamples(sampleIds);
            if (qcRecords.size() < sampleIds.size()) {
                clientCallback.displayWarning(String.format("Number of QC Records found: %d are LESS than number of samples attached %d." +
                        "\nPlease make sure all the samples have at least one QC record.", qcRecords.size(), sampleIds.size()));
            }
            generateQcReport(samples, qcRecords, qcProtocolRecords);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error in QC report generation. CAUSE: %s", e));
            logError(e);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * get QcReport DataType name based on sample type attached to the workflow task.
     * @param sample
     * @return String
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getReportName(DataRecord sample) throws NotFound, RemoteException, ServerException {
        String sampleType = null;
        if (sample.getValue("ExemplarSampleType", user) !=null){
            sampleType = sample.getStringVal("ExemplarSampleType", user).toLowerCase();
        }
        if (StringUtils.isBlank(sampleType)){
            clientCallback.displayError("ExemplarSampleType value missing on at least one Samples attached to this Task.");
        }
        if (RNA_SAMPLE_TYPES.contains(sampleType)){
            return "QcReportRna";
        }
        if (DNA_SAMPLE_TYPES.contains(sampleType)){
            return "QcReportDna";
        }
        if (LIBRARY_SAMPLE_TYPES.contains(sampleType)){
            return "QcReportLibrary";
        }
        return "";
    }

    /**
     * get RecordId values for DataRecords.
     * @param records
     * @return List<long>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Long> getAttachedRecordIds(List<DataRecord> records) throws NotFound, RemoteException {
        List<Long> ids = new ArrayList<>();
        for(DataRecord rec : records){
            ids.add(rec.getLongVal("RecordId", user));
        }
        return ids;
    }


    /**
     * Get SampleId values for the DataRecords.
     * @param samples
     * @return List<Object>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Object> getSampleIds(List<DataRecord> samples) throws NotFound, RemoteException {
        List<Object> sampleIds = new ArrayList<>();
        for (DataRecord sample : samples) {
            String sampleId = sample.getStringVal("SampleId", user);
            if (!StringUtils.isBlank(sampleId)) {
                sampleIds.add(sampleId);
            } else {
                throw new NotFound("SampleId missing on one of the attached sample records");
            }
        }
        return sampleIds;
    }

    /**
     * get QCDatum DataRecords for a sample.
     * @param sampleIdList
     * @return List<DataRecord>
     * @throws RemoteException
     * @throws IoError
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getQcRecordsForSamples(List<Object> sampleIdList) throws RemoteException, IoError, NotFound, ServerException {
        return dataRecordManager.queryDataRecords("QCDatum", "SampleId", sampleIdList, user);
    }

    /**
     * get QCDatum records based on type of QC.
     * @param sampleId
     * @param qcDataRecords
     * @param QcType
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<DataRecord> getQcRecordsByQcType(String sampleId, List<DataRecord> qcDataRecords, String QcType) throws NotFound, RemoteException {
        List<DataRecord> sampleQcRecordsByQcType = new ArrayList<>();
        for (DataRecord qcRecord : qcDataRecords) {
            if (qcRecord.getStringVal("SampleId", user).equals(sampleId) &&
                    QcType.toLowerCase().contains(qcRecord.getStringVal("DatumType", user).toLowerCase())) {
                sampleQcRecordsByQcType.add(qcRecord);
            }
        }
        return sampleQcRecordsByQcType;
    }

    /**
     * get most recently added QCDarum record for a sample
     * @param qcRecords
     * @return DataRecord
     * @throws NotFound
     * @throws RemoteException
     */
    private DataRecord getMostRecentQcRecord(List<DataRecord> qcRecords) throws NotFound, RemoteException {
        if (qcRecords.size() == 1) {
            return qcRecords.get(0);
        }
        DataRecord mostRecentQcRecord = qcRecords.get(0);
        for (DataRecord record : qcRecords) {
            if (record.getLongVal("RecordId", user) > mostRecentQcRecord.getLongVal("RecordId", user)) {
                mostRecentQcRecord = record;
            }
        }
        return mostRecentQcRecord;
    }

    /**
     * get DIN value from QCDatum records for sample.
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getDinValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> qcRecordsForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_DIN);
        Double dinValue = 0.00;
        if (!qcRecordsForSample.isEmpty() && getMostRecentQcRecord(qcRecordsForSample).getValue("DIN", user) != null) {
            dinValue = getMostRecentQcRecord(qcRecordsForSample).getDoubleVal("DIN", user);
        }
        if (qcRecordsForSample.isEmpty() || getMostRecentQcRecord(qcRecordsForSample).getValue("DIN", user) == null || dinValue <= 0) {
            clientCallback.displayWarning(String.format("DIN value not found for '%s'.", sampleId));
            logInfo(String.format("WARNING: DIN value not found for '%s'.", sampleId));
            return 0.00;
        }
        return dinValue;
    }

    /**
     * get RQN value from QCDatum records for sample.
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getRqnValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> qcRecordsWithRqnForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_RQN);
        Double rqnValue = 0.00;
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
        return rqnValue;
    }

    /**
     * get RIN value from QCDatum records for sample.
     * @param sampleId
     * @param qcRecords
     * @return String
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getRinValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> qcRecordsWithRinForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_RIN);
        String rinValue = "";
        if (!qcRecordsWithRinForSample.isEmpty()) {
            for (DataRecord qcRecord : qcRecordsWithRinForSample) {
                if (qcRecord.getValue("RIN", user) != null) {
                    rinValue = qcRecord.getStringVal("RIN", user);
                }
            }
        }
        if (qcRecordsWithRinForSample.isEmpty() || StringUtils.isBlank(rinValue)) {
            clientCallback.displayWarning(String.format("RIN value not found for '%s'.", sampleId));
            logInfo(String.format("WARNING: RIN value not found for '%s'.", sampleId));
            return "";
        }
        return rinValue;
    }

    /**
     * get DV200 value from QCDatum records for sample.
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getDv200ValueFromQcRecord(String sampleId, List<DataRecord> qcRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> qcRecordswithDv200ForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_DV200);
        Double dv200Value = 0.00;
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
        return dv200Value;
    }

    /**
     * get QcProtocol records for samples.
     * @param sampleIdList
     * @return List<DataRecord>
     * @throws RemoteException
     * @throws IoError
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getQcProtocolRecordsForSamples(List<Object> sampleIdList) throws RemoteException, IoError, NotFound, ServerException {
        return dataRecordManager.queryDataRecords("QCProtocol", "SampleId", sampleIdList, user);
    }

    /**
     * get IgoRecommendation value from QcProtocol records for sample
     * @param sampleId
     * @param qcProtocolRecords
     * @return String
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private String getIgoRecommendationValue(String sampleId, List<DataRecord> qcProtocolRecords) throws NotFound, RemoteException, ServerException {
        String igoRecommendationValue = "";
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
        return igoRecommendationValue;
    }

    /**
     * get QcComments value from QcProtocol records for sample.
     * @param sampleId
     * @param qcDataRecords
     * @return String
     * @throws NotFound
     * @throws RemoteException
     */
    private String getQcCommentsValue(String sampleId, List<DataRecord> qcDataRecords) throws NotFound, RemoteException {
        String igoQcCommentsValue = "";
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
        return igoQcCommentsValue;
    }

    /**
     * get AverageSize of Library for a Sample from QcProtocol records.
     * @param sampleId
     * @param qcRecords
     * @return Double
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Double getAverageLibrarySizeValue(String sampleId, List<DataRecord> qcRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> qcRecordsWithAvgBpSizeForSample = getQcRecordsByQcType(sampleId, qcRecords, QC_TYPE_FOR_AVERAGE_BP_SIZE);
        Double averageBasePairSize = 0.0;
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
        return averageBasePairSize;
    }

    /**
     * Create DNA QC REPORT DataRecords for Samples.
     * @param samples
     * @param qcDataRecords
     * @param qcProtocolRecords
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private List<DataRecord> generateDnaQcReportFieldValuesMap(List<DataRecord> samples, List<DataRecord> qcDataRecords, List<DataRecord> qcProtocolRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> dnaQcRecords = new ArrayList<>();
        for (DataRecord sample : samples) {
            Map<String, Object> qcRecord = new HashMap<>();
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
            Double dinValue = getDinValueFromQcRecord(sampleId, qcDataRecords);
            String igoRecommendation = getIgoRecommendationValue(sampleId, qcProtocolRecords);
            String comments = getQcCommentsValue(sampleId, qcProtocolRecords);
            if (dinValue > 0) {
                qcRecord.put("DIN", dinValue);
            }
            if (!StringUtils.isBlank(igoRecommendation)) {
                qcRecord.put("IgoQcRecommendation", igoRecommendation);
            }
            if (!StringUtils.isBlank(comments)) {
                qcRecord.put("Comments", comments);
            }
            dnaQcRecords.add(sample.addChild("QcReportDna", qcRecord, user));
        }
        return dnaQcRecords;
    }

    /**
     * generate RNA QC REPORT DataRecords for Samples.
     * @param samples
     * @param qcRecords
     * @param qcProtocolRecords
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private List<DataRecord> generateRnaQcReportFieldValuesMap(List<DataRecord> samples, List<DataRecord> qcRecords, List<DataRecord> qcProtocolRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> rnaQcRecords = new ArrayList<>();
        for (DataRecord sample : samples) {
            Map<String, Object> qcRecord = new HashMap<>();
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
            String rinValue = getRinValueFromQcRecord(sampleId, qcRecords);
            Double dv200Value = getDv200ValueFromQcRecord(sampleId, qcRecords);
            Double rqnValue = getRqnValueFromQcRecord(sampleId, qcRecords);
            String igoRecommendation = getIgoRecommendationValue(sampleId, qcProtocolRecords);
            String comments = getQcCommentsValue(sampleId, qcProtocolRecords);
            if (!StringUtils.isBlank(rinValue)) {
                qcRecord.put("RIN", rinValue.split("\\(")[0]);
            }
            if (dv200Value > 0) {
                qcRecord.put("DV200", dv200Value);
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
        }
        return rnaQcRecords;
    }

    /**
     * Generate LIBRARY QC REPORT DataRecords for Samples.
     * @param samples
     * @param qcRecords
     * @param qcProtocolRecords
     * @return List<DataRecord>
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private List<DataRecord> generateLibraryQcReportFieldValuesMap(List<DataRecord> samples, List<DataRecord> qcRecords, List<DataRecord> qcProtocolRecords) throws NotFound, RemoteException, ServerException {
        List<DataRecord> libraryQcRecords = new ArrayList<>();
        for (DataRecord sample : samples) {
            Map<String, Object> qcRecord = new HashMap<>();
            String sampleId = sample.getStringVal("SampleId", user);
            qcRecord.put("SampleId", sampleId);
            qcRecord.put("OtherSampleId", sample.getStringVal("OtherSampleId", user));
            qcRecord.put("UserSampleID", sample.getStringVal("UserSampleID", user));
            qcRecord.put("AltId", sample.getStringVal("AltId", user));
            qcRecord.put("RequestId", sample.getStringVal("RequestId", user));
            qcRecord.put("Concentration", sample.getDoubleVal("Concentration", user));
            qcRecord.put("ConcentrationUnits", sample.getStringVal("ConcentrationUnits", user));
            qcRecord.put("Volume", sample.getDoubleVal("Volume", user));
            if (sample.getStringVal("ConcentrationUnits", user).trim().equalsIgnoreCase("ng/uL")){
                qcRecord.put("TotalMass", sample.getDoubleVal("Concentration", user) * sample.getDoubleVal("Volume", user));
            }
            else {
                qcRecord.put("TotalMass", sample.getDoubleVal("TotalMass", user) * NANOMOLAR_TO_FEMTOMOLAR_CONVERSION_FACTOR); //convert nM to fM by multiplying by 1000000
            }
            qcRecord.put("TumorOrNormal", sample.getStringVal("TumorOrNormal", user));
            qcRecord.put("Recipe", sample.getStringVal("Recipe", user));
            Double averageBpSize = getAverageLibrarySizeValue(sampleId, qcRecords);
            String igoRecommendation = getIgoRecommendationValue(sampleId, qcProtocolRecords);
            String comments = getQcCommentsValue(sampleId, qcProtocolRecords);
            if (averageBpSize > 0) {
                qcRecord.put("AvgSize", averageBpSize);
            }
            if (!StringUtils.isBlank(igoRecommendation)) {
                qcRecord.put("IgoQcRecommendation", igoRecommendation);
            }
            if (!StringUtils.isBlank(comments)) {
                qcRecord.put("Comments", comments);
            }
            libraryQcRecords.add(sample.addChild("QcReportLibrary", qcRecord, user));
        }
        return libraryQcRecords;
    }

    /**
     * Generate QC report records
     * @param samples
     * @param qcRecords
     * @param qcProtocolRecords
     * @throws ServerException
     * @throws RemoteException
     * @throws NotFound
     */
    private void generateQcReport(List<DataRecord> samples, List<DataRecord> qcRecords, List<DataRecord> qcProtocolRecords) throws ServerException, RemoteException, NotFound {
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
            List<DataRecord> libraryQcRecords = generateLibraryQcReportFieldValuesMap(samples, qcRecords, qcProtocolRecords);
            activeTask.addAttachedDataRecords(libraryQcRecords);
        }
    }
}