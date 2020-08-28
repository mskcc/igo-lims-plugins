package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.user.User;
import com.velox.api.util.ClientCallbackOperations;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.context.ManagerBase;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

class TapeStationResultParser extends ManagerBase {
    private final double ADAPTER_TO_BP = 180.0;
    private final double LIB_TO_BP = 1000.0;
    private final String FROM_BP = "From [bp]";
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private Map<String, Integer> headerMapValues;
    private List<String> fileData;
    private String fileName;
    private ClientCallbackOperations clientCallback;
    private PluginLogger logger;
    private User user;

    TapeStationResultParser(List<String> data, String fileName, Map<String, Integer> headerMapValues, ClientCallbackOperations clientCallback, PluginLogger logger, User user) {
        this.fileData = data;
        this.fileName = fileName;
        this.headerMapValues = headerMapValues;
        this.clientCallback = clientCallback;
        this.user = user;
        this.logger = logger;
    }


    /**
     * Method to group data by sample.
     *
     * @param tapeStationFileData
     * @return
     * @throws ServerException
     */
    private Map<String, List<QualityControlData>> groupQualityControlDataBySampleId(List<String> tapeStationFileData) throws ServerException {
        Map<String, List<QualityControlData>> groupedData = new HashMap<>();
        String SAMPLE_DESCRIPTION = "Sample Description";
        String TO_BP = "To [bp]";
        String CONCENTRATION = "Conc. [ng/�l]";
        String PERCENT_FRACTION = "% of Total";
        try {
            for (int i = 1; i < tapeStationFileData.size(); i++) {
                String row = tapeStationFileData.get(i);
                List<String> rowDataValues = Arrays.asList(row.split(","));
                logger.logInfo(String.format("Tapestation data from file %s.\n%s", fileName, rowDataValues.toString()));
                String sampleId = rowDataValues.get(headerMapValues.get(SAMPLE_DESCRIPTION));
                int fromBp = Integer.parseInt(rowDataValues.get(headerMapValues.get(FROM_BP)));
                int toBp = Integer.parseInt(rowDataValues.get(headerMapValues.get(TO_BP)));
                double concentration = Double.parseDouble(rowDataValues.get(headerMapValues.get(CONCENTRATION)));
                double fraction = Double.parseDouble(rowDataValues.get(headerMapValues.get(PERCENT_FRACTION)));
                String observation = ""; //observation value are only present in Bioanalyzer Data.
                QualityControlData QualityControlData = new QualityControlData(sampleId, fromBp, toBp, concentration, fraction, observation);
                groupedData.putIfAbsent(sampleId, new ArrayList<>());
                groupedData.get(sampleId).add(QualityControlData);
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while grouping the data by 'SampleId'.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayInfo(errMsg);
            logger.logError(errMsg);
            return groupedData;
        }
        return groupedData;
    }


    /**
     * Method to sort Tapestation objects in Ascending order by fromBp property values.
     *
     * @param QualityControlDataVals
     * @return
     * @throws ServerException
     */
    private List<QualityControlData> getDataSortedByFromBpAsc(List<QualityControlData> QualityControlDataVals) throws ServerException {
        try {
            return QualityControlDataVals.stream()
                    .sorted(Comparator.comparing(QualityControlData::getFromBp))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            String errMsg = String.format("Error while sorting the QualityControlData by '%s' value.\n%s", FROM_BP, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
            return null;
        }
    }


    /**
     * Method to get sum of concentration values
     *
     * @param QualityControlDataVals
     * @return
     * @throws ServerException
     */
    private double getConcentrationSum(List<QualityControlData> QualityControlDataVals) throws ServerException {
        double sum = 0.0;
        try {
            for (QualityControlData data : QualityControlDataVals) {
                sum += data.getConcentration();
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while getting sum of concentration from QualityControlData.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        return sum;
    }

    /**
     * Method to get adapterPercentage
     *
     * @param QualityControlDataVals
     * @param sumConcentration
     * @return
     * @throws ServerException
     */
    private double calculateAdapterPercentage(List<QualityControlData> QualityControlDataVals, double sumConcentration) throws ServerException {
        double adapterPercentage = 0.0;
        try {
            for (QualityControlData data : QualityControlDataVals) {
                int fromBpVal = data.getFromBp();
                int toBpVal = data.getToBp();
                double ADAPTER_FROM_BP = 0.0;
                if (fromBpVal >= ADAPTER_FROM_BP && toBpVal <= ADAPTER_TO_BP) {
                    double adapterConc = data.getConcentration();
                    return (adapterConc / sumConcentration) * 100.0;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while calculating 'Adapter Percentage' from QualityControlData.\n%s02756_B_2_1_1_1\n", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        return adapterPercentage;
    }

    /**
     * Method to get percentageFragmentsLargerThan1kb
     *
     * @param QualityControlDataVals
     * @param sumConc
     * @return
     * @throws ServerException
     */
    private double calculatePercentageFragmentsUpto1kb(List<QualityControlData> QualityControlDataVals, double sumConc) throws ServerException {
        double percentageFragmentsUpto1kb = 0.0;
        try {
            for (QualityControlData data : QualityControlDataVals) {
                int toBpVal = data.getToBp();
                if (toBpVal > ADAPTER_TO_BP && toBpVal <= LIB_TO_BP) {
                    double conc = data.getConcentration();
                    return (conc/sumConc) * 100;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while calculating 'Adapter Percentage' from QualityControlData.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        return percentageFragmentsUpto1kb;
    }

    /**
     * Method to create QualityControlData objects
     */
    private List<SampleQcResult> getQualityControlData(Map<String, List<QualityControlData>> groupedData, List<DataRecord> attachedSamples) throws ServerException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        try{
            for (String key : groupedData.keySet()) {
                List<QualityControlData> QualityControlData = getDataSortedByFromBpAsc(groupedData.get(key));
                DataRecord sample = utils.getSampleWithMatchingId(key, attachedSamples, fileName, clientCallback, logger, user);
                if (sample != null) {
                    double quantity = utils.getSampleQuantity(sample, clientCallback, logger, user);
                    double sumConc = getConcentrationSum(QualityControlData);
                    double adapterPercentage = calculateAdapterPercentage(QualityControlData, sumConc);
                    double percentFragmentsUpto1kb = calculatePercentageFragmentsUpto1kb(QualityControlData, sumConc);
                    double percentFragmentLargerThan1Kb = Math.abs((adapterPercentage + percentFragmentsUpto1kb) - 100.0);
                    boolean isUserLibrary = utils.isUserLibrary(sample, user, clientCallback);
                    SampleQcResult qcResult = new SampleQcResult(key, quantity, adapterPercentage, percentFragmentLargerThan1Kb, isUserLibrary);
                    logger.logInfo("Sample ID: " + key);
                    logger.logInfo("Quantity: " + quantity);
                    logger.logInfo("Adapter percent: " + adapterPercentage);
                    logger.logInfo("Percent upto 1 kb: " + percentFragmentsUpto1kb);
                    logger.logInfo("Percent > 1kb: " + percentFragmentLargerThan1Kb);
                    logger.logInfo(String.format("Is user Lib: %s", isUserLibrary));
                    logger.logInfo("Igo recommendation: " + qcResult.getIgoRecommendationAnnotation());
                    logger.logInfo(qcResult.toString());
                    qcResults.add(qcResult);
                }
            }
        }catch (Exception e){
            String errMsg = String.format("%s -> while getting tapeStation Data.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        return qcResults;
    }

    /**
     * Method to parse tapestation data to QcResult objects.
     * @param attachedSamples
     * @return
     * @throws ServerException
     */
    List<SampleQcResult> parseData(List<DataRecord> attachedSamples) throws ServerException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        try {
            Map<String, List<QualityControlData>> groupedData = groupQualityControlDataBySampleId(this.fileData);
            qcResults = getQualityControlData(groupedData, attachedSamples);
            logger.logInfo(String.format("Parsed SampleQcResults: %s", qcResults.toString()));
        }catch (Exception e){
            String errMsg = String.format("%s -> while parsing Tapestation data.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayInfo(errMsg);
            logger.logError(errMsg);
        }
        return qcResults;
    }
}
