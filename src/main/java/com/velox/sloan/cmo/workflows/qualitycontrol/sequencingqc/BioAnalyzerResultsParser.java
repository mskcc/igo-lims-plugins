package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.user.User;
import com.velox.api.util.ClientCallbackOperations;
import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

public class BioAnalyzerResultsParser {
    private final double ADAPTER_FROM_BP = 0.0;
    private final double ADAPTER_TO_BP = 150.0;
    private final double TO_BP_1KB = 1000.0;
    private final String LOWER_MARKER = "Lower Marker";
    private final String UPPER_MARKER = "Upper Marker";
    private Map<String, Integer> headerMapValues;
    private List<String> fileData;
    private String fileName;
    private ClientCallbackOperations clientCallback;
    private PluginLogger logger;
    private User user;
    private final List<String> IDENTIFIER_TO_SKIP_LINE = Arrays.asList("Data File Path", "Date Created", "Date Last Modified",
            "Version Created", "Assay Name", "Assay Path", "Assay Title", "Assay Version", "Number of Samples Run", "Peak Table",
            "Size [bp]", "Region Table", "Name", "Region 1");
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public BioAnalyzerResultsParser(List<String> fileData, String fileName, Map<String, Integer> headerMapValues, ClientCallbackOperations clientCallback, PluginLogger logger, User user) {
        this.fileData = fileData;
        this.fileName = fileName;
        this.headerMapValues = headerMapValues;
        this.clientCallback = clientCallback;
        this.logger = logger;
        this.user = user;
    }


    /**
     * Method to group data by sample.
     *
     * @return
     * @throws ServerException
     */
    private Map<String, List<QualityControlData>> groupQualityControlDataBySampleId() throws ServerException, RemoteException {
        Map<String, List<QualityControlData>> groupedData = new HashMap<>();
        String SAMPLE_BEGIN_IDENTIFIER = "Sample Name";
        String SAMPLE_DATA_BEGIN_IDENTIFIER = "Size [bp]";
        String SIZE_BP = "Size [bp]";
        String CONCENTRATION = "Conc. [pg/�l]";
        String OBSERVATIONS = "Observations";
        String PERCENT_FRACTION = "% of Total";
        try {
            String sampleId = null;
            int rowFromBp = 0;
            logger.logInfo("Header value map: " + headerMapValues.toString());
            for (String fileDatum : fileData) {
                logger.logInfo("Line before comma removal from numeric values: " + fileDatum);
                String line = utils.removeThousandSeparator(fileDatum); //If numeric values have 1000 separator, remove 1000 separator which is "comma" from such values.
                logger.logInfo("Line after comma removal from numeric values: " + line);
                List<String> lineValues = Arrays.asList(line.split(","));
                logger.logInfo("Line values" + lineValues.toString());
                logger.logInfo("Line values size: " + lineValues.size());
                if (line.contains(SAMPLE_BEGIN_IDENTIFIER) && lineValues.size()>1) {
                    logger.logInfo("Sample Name value: " + lineValues.get(1));
                    sampleId = lineValues.get(1);
                    continue;
                }
                // Set sampleid to null if the Sample Name is missing in Bioanalyzer file. It happens samples fewer than
                // limit of Bioanalyzer chip are run. This will prevent the reading of Sample data blocks that are empty
                // on the chip and are not required to be processed.
                if (line.contains(SAMPLE_BEGIN_IDENTIFIER) && lineValues.size()<2){
                    sampleId = null;
                }
                String lineStartValue = lineValues.size() > 0 ? lineValues.get(0) : null;
                // Skip lines that does not contain miscellaneous data not required for sample QC annotation.
                if (lineValues.contains(SAMPLE_DATA_BEGIN_IDENTIFIER) || StringUtils.isBlank(lineStartValue) || IDENTIFIER_TO_SKIP_LINE.contains(lineStartValue)) {
                    continue;
                }
                // Start processing data for sample. This will continue until code finds a new line with Sample Name
                // block with new Sample Name.
                if (sampleId != null) {
                    logger.logInfo(String.format("Bioanalyzer data from file %s.\n%s", fileName, lineValues.toString()));
                    int startBp = rowFromBp;
                    int toBp = Integer.parseInt(lineValues.get(headerMapValues.get(SIZE_BP)).replace(",", "")); // replace any comma separators in numbers.
                    rowFromBp = toBp;
                    double concentration = Double.parseDouble(lineValues.get(headerMapValues.get(CONCENTRATION)).replace(",", ""));
                    double fraction = Double.parseDouble(lineValues.get(headerMapValues.get(PERCENT_FRACTION)).replace(",", ""));
                    String observation = lineValues.get(headerMapValues.get(OBSERVATIONS));
                    QualityControlData QualityControlData = new QualityControlData(sampleId, startBp, toBp, concentration, fraction, observation);
                    groupedData.putIfAbsent(sampleId, new ArrayList<>());
                    groupedData.get(sampleId).add(QualityControlData);
                }
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
     * Method to get adapterPercentage
     *
     * @param QualityControlDataVals
     * @return
     * @throws ServerException
     */
    private double calculateAdapterPercentage(List<QualityControlData> QualityControlDataVals) throws ServerException
    , RemoteException {
        double adapterPercentage = 0.0;
        try {
            for (QualityControlData data : QualityControlDataVals) {
                logger.logInfo(data.toString());
                int fromBpVal = data.getFromBp();
                int toBpVal = data.getToBp();
                String lowerMarker = data.getObservation();
                if (StringUtils.isBlank(lowerMarker) && toBpVal >= ADAPTER_FROM_BP && toBpVal <= ADAPTER_TO_BP) {
                    adapterPercentage += data.getFractionVal();
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
     * @return
     * @throws ServerException
     */
    private double calculatePercentageFragmentsUpto1kb(List<QualityControlData> QualityControlDataVals) throws
            ServerException, RemoteException {
        double percentageFragmentsUpto1kb = 0.0;
        try {
            for (QualityControlData data : QualityControlDataVals) {
                logger.logInfo("Bioanalyzer sample data row: " + data.toString());
                int fromBpVal = data.getFromBp();
                int toBp = data.getToBp();
                String observation = data.getObservation();
                logger.logInfo(String.format("From BP: %d, To BP: %d", fromBpVal, toBp));
                if (StringUtils.isBlank(observation) && toBp > ADAPTER_TO_BP && toBp <= TO_BP_1KB ) {
                    logger.logInfo(String.format(" Adding row with From BP: %d, To BP: %d",  fromBpVal, toBp));
                    percentageFragmentsUpto1kb += data.getFractionVal();
                    logger.logInfo(String.format("Percentage upto 1 kb: %f", percentageFragmentsUpto1kb));
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
    private List<SampleQcResult> getQualityControlData(Map<String, List<QualityControlData>> groupedData, List<DataRecord>
            attachedSamples) throws ServerException, RemoteException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        try{
            for (String key : groupedData.keySet()) {
                List<QualityControlData> QualityControlData = groupedData.get(key);
                DataRecord sample = utils.getSampleWithMatchingId(key, attachedSamples, fileName, clientCallback, logger, user);
                if (sample != null) {
                    double quantity = utils.getSampleQuantity(sample, clientCallback, logger, user);
                    double adapterPercentage = calculateAdapterPercentage(QualityControlData);
                    double percentFragmentsUpto1kb = calculatePercentageFragmentsUpto1kb(QualityControlData);
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
    List<SampleQcResult> parseData(List<DataRecord> attachedSamples) throws ServerException, RemoteException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        try {
            Map<String, List<QualityControlData>> groupedData = groupQualityControlDataBySampleId();
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
