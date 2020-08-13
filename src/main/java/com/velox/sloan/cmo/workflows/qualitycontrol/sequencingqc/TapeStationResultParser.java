package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.shared.managers.ManagerBase;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mockito.internal.matchers.Not;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

public class TapeStationResultParser extends ManagerBase {
    private final double ADAPTER_FROM_BP = 0.0;
    private final double ADAPTER_TO_BP = 180.0;
    private final String SAMPLE_DESCRIPTION = "Sample Description";
    private final String FROM_BP = "From [bp]";
    private final String TO_BP = "To [bp]";
    private final String AVERAGE_SIZE = "Average Size [bp]";
    private final String CONCENTRATION = "Conc. [ng/µl]";
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private Map<String, Integer> headerMapValues;
    private List<String> fileData;
    private String fileName;


    public TapeStationResultParser(List<String> data, String fileName) {
        this.fileData = data;
        this.fileName = fileName;
    }

//    /**
//     * Method to get header values and column positons in file.
//     */
//    private Map<String, Integer> getHeaderMapValues() {
//        return this.headerMapValues;
//    }

    /**
     * Method to set header values and column position of each value.
     *
     * @param fileData
     */
//    private void setHeaderMapValues(List<String> fileData) {
//        this.headerMapValues = utils.getCsvHeaderValueMap(fileData);
//    }



    /**
     * Method to group data by sample.
     *
     * @param tapeStationFileData
     * @return
     * @throws ServerException
     */
    private Map<String, List<TapeStationData>> groupTapestationDataBySampleId(List<String> tapeStationFileData) throws ServerException {
        Map<String, List<TapeStationData>> groupedData = new HashMap<>();
        try {
            for (int i = 1; i < tapeStationFileData.size(); i++) {
                String row = tapeStationFileData.get(i);
                List<String> rowDataValues = Arrays.asList(row.split(","));
                String sampleId = rowDataValues.get(headerMapValues.get(SAMPLE_DESCRIPTION));
                int fromBp = Integer.parseInt(rowDataValues.get(headerMapValues.get(FROM_BP)));
                int toBp = Integer.parseInt(rowDataValues.get(headerMapValues.get(TO_BP)));
                double avgSize = Double.parseDouble(rowDataValues.get(headerMapValues.get(AVERAGE_SIZE)));
                double concentration = Double.parseDouble(rowDataValues.get(headerMapValues.get(CONCENTRATION)));
                TapeStationData tapeStationData = new TapeStationData(sampleId, fromBp, toBp, avgSize, concentration);
                groupedData.putIfAbsent(sampleId, new ArrayList<>());
                groupedData.get(sampleId).add(tapeStationData);
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while grouping the data by 'SampleId'.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return groupedData;
        }
        return groupedData;
    }


    /**
     * Method to sort Tapestation objects in Ascending order by fromBp property values.
     *
     * @param tapeStationDataVals
     * @return
     * @throws ServerException
     */
    private List<TapeStationData> getDataSortedByFromBpAsc(List<TapeStationData> tapeStationDataVals) throws ServerException {
        try {
            return tapeStationDataVals.stream()
                    .sorted(Comparator.comparing(TapeStationData::getFromBp))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            String errMsg = String.format("Error while sorting the TapestationData by '%s' value.\n%s", FROM_BP, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return null;
        }
    }


    /**
     * Method to get sum of concentration values
     *
     * @param tapeStationDataVals
     * @return
     * @throws ServerException
     */
    private double getConcentrationSum(List<TapeStationData> tapeStationDataVals) throws ServerException {
        double sum = 0.0;
        try {
            for (TapeStationData data : tapeStationDataVals) {
                sum += data.getConcentration();
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while getting sum of concentration from TapestationData.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return sum;
    }

    /**
     * Method to get adapterPercentage
     *
     * @param tapeStationDataVals
     * @return
     * @throws ServerException
     */
    private double calculateAdapterPercentage(List<TapeStationData> tapeStationDataVals) throws ServerException {
        double adapterPercentage = 0.0;
        try {
            for (TapeStationData data : tapeStationDataVals) {
                int fromBpVal = data.getFromBp();
                if (fromBpVal >= ADAPTER_FROM_BP && fromBpVal <= ADAPTER_TO_BP) {
                    double sumConcentration = getConcentrationSum(tapeStationDataVals);
                    return (fromBpVal / sumConcentration) * 100.0;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while calculating 'Adapter Percentage' from TapestationData.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return adapterPercentage;
    }

    /**
     * Method to get percentageFragmentsLargerThan1kb
     *
     * @param tapeStationDataVals
     * @return
     * @throws ServerException
     */
    private double calculatePercentageFragmentsLargerThan1kb(List<TapeStationData> tapeStationDataVals) throws ServerException {
        double percentageFragmentsLargerThan1kb = 0.0;
        try {
            for (TapeStationData data : tapeStationDataVals) {
                int fromBpVal = data.getFromBp();
                double concentrationSum = getConcentrationSum(tapeStationDataVals);
                if (fromBpVal >= ADAPTER_TO_BP) {
                    double sumConcentration = getConcentrationSum(tapeStationDataVals);
                    return (1.0 - concentrationSum) * 100.0;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while calculating 'Adapter Percentage' from TapestationData.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return percentageFragmentsLargerThan1kb;
    }

    /**
     * Method to get Sample matching with passed SampleId from attached Samples.
     */
    private DataRecord getSampleWithMatchingId(String sampleId, List<DataRecord> attachedSamples) throws ServerException {
        DataRecord matchingSample = null;
        try {
            for (DataRecord sample : attachedSamples) {
                Object sampId = sample.getValue(SampleModel.SAMPLE_ID, user);
                if (sampId != null && sampleId.equals(sampId.toString())) {
                    matchingSample = sample;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while searching for Sample with ID '%s' in attached Samples.\n%s", sampleId, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        if (matchingSample == null) {
            String errMsg = String.format("Could not find Sample with ID '%s' in attached Samples.\nPlease double check the Sample ID's in the uploaded Tapestation file(s).", sampleId);
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return matchingSample;
        }
        return matchingSample;
    }

    /**
     * Method to get Sample Quantity value.
     *
     * @param sample
     * @return
     * @throws ServerException
     */
    private double getSampleQuantity(DataRecord sample) throws ServerException {
        double sampleQuantity = 0.0;
        try {
            Object concentration = sample.getValue(SampleModel.CONCENTRATION, user);
            Object volume = sample.getValue(SampleModel.VOLUME, user);
            if (concentration == null){
                String errMsg = String.format("Error while reading 'Concentration' from Sample.\n%s", sample.getStringVal(SampleModel.SAMPLE_ID, user));
                clientCallback.displayError(errMsg);
                throw new NotFound(errMsg);
            }
            else if (volume == null){
                String errMsg = String.format("Error while reading 'Volume' from Sample.\n%s", sample.getStringVal(SampleModel.SAMPLE_ID, user));
                clientCallback.displayError(errMsg);
                throw new NotFound(errMsg);
            }
            else {
                return (double) concentration * (double) volume;
            }
        }catch (RemoteException | NotFound ex) {
            String errMsg = String.format("Error while reading 'quantity' from Sample with recordId '%d'.\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(ex));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return sampleQuantity;
    }

    /**
     * Method to create TapeStationData objects
     */
    private List<SampleQcResult> getTapeStationData(Map<String, List<TapeStationData>> groupedData, List<DataRecord> attachedSamples) throws ServerException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        for (String key : groupedData.keySet()) {
            List<TapeStationData> tapeStationData = groupedData.get(key);
            String sampleDescription = key;
            DataRecord sample = getSampleWithMatchingId(sampleDescription, attachedSamples);
            double quantity = getSampleQuantity(sample);
            double adapterPercentage = calculateAdapterPercentage(tapeStationData);
            double percentFragmentLargerThan1Kb = calculatePercentageFragmentsLargerThan1kb(tapeStationData);
            boolean isUserLibrary = utils.isUserLibrary(sample);
            SampleQcResult qcResult = new SampleQcResult(sampleDescription, quantity, adapterPercentage, percentFragmentLargerThan1Kb, isUserLibrary);
            qcResults.add(qcResult);
        }
        return qcResults;
    }
}
