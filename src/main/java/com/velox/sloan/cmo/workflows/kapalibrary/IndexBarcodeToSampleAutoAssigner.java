package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is the plugin class designed to automatically populate 'IndexBarcode' records for each sample on the task. This plugin uses DataRecords under 'AutoIndexAssignmentConfig' DataType
 * to populate IndexBarcode values. The the metadata for 'AutoIndexAssignmentConfig' DataRecords is also updated if they are used in the process.
 * 'Index Barcode and Adapter' terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 *
 * @author sharmaa1
 */
public class IndexBarcodeToSampleAutoAssigner extends DefaultGenericPlugin {

    private final List<String> RECIPES_TO_USE_SPECIAL_ADAPTERS = Arrays.asList("CRISPRSeq", "AmpliconSeq");
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private AutoIndexAssignmentHelper autoHelper;

    public IndexBarcodeToSampleAutoAssigner() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder() + 2);
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().containsKey("AUTOASSIGN INDEX BARCODES") && !activeTask.getTask().getTaskOptions().containsKey("_INDEXES_AUTO_ASIGNED");
    }

    public PluginResult run() throws ServerException {
        autoHelper = new AutoIndexAssignmentHelper();
        try {
            List<DataRecord> attachedSamplesList = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IndexBarcode", user);
            if (attachedIndexBarcodeRecords.isEmpty()) {
                clientCallback.displayError(String.format("Could not find any IndexBarcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                logError(String.format("Could not find any IndexBarcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            if (attachedSamplesList.isEmpty()) {
                clientCallback.displayError("No Samples found attached to this task.");
                logError("No Samples found attached to this task.");
                return new PluginResult(false);
            }
            if (StringUtils.isBlank(activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES")) &&
                    !activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES").contains("INDEX TYPE")) {
                clientCallback.displayError("Task Option 'VALIDATE UNIQUE SAMPLE RECIPE' is missing valid value. Please double check. Valid values should be in format 'INDEX TYPE (IDT | TruSeq)'");
                logError("Task Option 'VALIDATE UNIQUE SAMPLE RECIPE' is missing valid value. Please double check. Valid values should be in format 'INDEX TYPE (IDT | TruSeq)'");
                return new PluginResult(false);
            }

            String taskOptionValueForIndexAssignment = activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES");
            List<String> recipes = getUniqueSampleRecipes(attachedSamplesList);
            String indexTypeToProcess = getIndexTypesToUse(taskOptionValueForIndexAssignment);
            List<DataRecord> indexConfigsToUse = getIndexAssignmentConfigsForIndexType(indexTypeToProcess, recipes);
            if (indexConfigsToUse.isEmpty()) {
                clientCallback.displayError(String.format("Could not find 'AutoIndexAssignmentConfig' for Recipes/IndexTypes values '%s/%s' given to plugin 'AUTOASSIGN INDEX BARCODES", utils.convertListToString(recipes), indexTypeToProcess));
                logError(String.format("Could not find 'AutoIndexAssignmentConfig' for Recipes '%s' for samples and TASK OPTION VALUE '%s' for Index Types given to Option 'AUTOASSIGN INDEX BARCODES", utils.convertListToString(recipes), indexTypeToProcess));
                return new PluginResult(false);
            }
            List<DataRecord> sortedProtocolRecords = getSampleProtocolRecordsSortedByWellPositionColumnWise(attachedIndexBarcodeRecords);
            Integer plateSize = getPlateSize(attachedSamplesList);
            Double minAdapterVol = autoHelper.getMinAdapterVolumeRequired(plateSize);
            String sampleType = attachedSamplesList.get(0).getStringVal("ExemplarSampleType", user);
            if (plateSize == 96) {
                assignIndicesToSamples(sortedProtocolRecords, indexConfigsToUse, minAdapterVol, plateSize, sampleType);
            } else if (plateSize == 384) {
                List<List<DataRecord>> protocolsSplitByAlternateWell = getQuadrantsFromProtocols(sortedProtocolRecords);
                for (List<DataRecord> protocolsList : protocolsSplitByAlternateWell) {
                    assignIndicesToSamples(protocolsList, indexConfigsToUse, minAdapterVol, plateSize, sampleType);
                }
            }
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while Auto Index assignment to samples:\n%s", ExceptionUtils.getStackTrace(e)));
            clientCallback.displayError(e.toString());
            logError(String.format("Error: %s", Arrays.toString(e.getStackTrace())));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to get the IndexType to use for the autoassignment based on the value for Task Option 'AUTOASSIGN INDEX BARCODES' on the task.
     *
     * @param taskOptionValueForIndexAssignment
     * @return String
     */
    private String getIndexTypesToUse(String taskOptionValueForIndexAssignment) {
        List<String> indexTypes = new ArrayList<>();
        Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(taskOptionValueForIndexAssignment);
        if (m.find()) {
            String[] values = m.group(1).split("\\|");
            for (String val : values) {
                indexTypes.add(val.trim());
            }
        }
        return "('" + StringUtils.join(indexTypes, "','") + "')";
    }

    /**
     * Method to get the unique Recipe values associated with Sample DataRecords.
     *
     * @param attachedSamples
     * @return List<String>
     */
    private List<String> getUniqueSampleRecipes(List<DataRecord> attachedSamples) {
        List<String> recipes = attachedSamples.stream().map(s -> {
            try {
                return s.getStringVal("Recipe", user);
            } catch (Exception e) {
                logInfo(ExceptionUtils.getStackTrace(e));
                return "";
            }
        }).collect(Collectors.toList());
        Set<String> uniqueRecipes = new HashSet<>(recipes);
        return new ArrayList<String>(uniqueRecipes);
    }

    /**
     * Method to get DataRecords for 'AutoIndexAssignmentConfig' DataType that are marked as Active.
     *
     * @param indexTypes
     * @param recipes
     * @return List<DataRecord>
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getIndexAssignmentConfigsForIndexType(String indexTypes, List<String> recipes) throws IoError, RemoteException, NotFound, ServerException {
        boolean isCrisprOrAmpliconSeq = recipes.stream().anyMatch(RECIPES_TO_USE_SPECIAL_ADAPTERS::contains);
        String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";
        if (!isCrisprOrAmpliconSeq) {
            logInfo("Library samples do not have recipe values Crispr or AmpliconSeq, reserved indexes in set5 will not be used.");
            return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + "AND IsActive=1 AND SetId!=5", user);
        } else {
            logInfo("Recipe on Library samples is Crispr or AmpliconSeq, reserved indexes in set5 will be used.");
            return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType='DUAL_IDT_LIB' AND SetId=5 and IsActive=1", user);
        }
    }

    /**
     * Method to get the position of last 'AutoIndexAssignmentConfig' DataRecord that was used for Auto Index Assignment process.
     *
     * @param indexAssignmentConfigs
     * @return Integer
     * @throws NotFound
     * @throws RemoteException
     */
    private Integer getPositionOfLastUsedIndex(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException {
        for (int i = 0; i < indexAssignmentConfigs.size(); i++) {
            if (indexAssignmentConfigs.get(i).getBooleanVal("LastUsed", user)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Set the 'LastUsed' value of 'AutoIndexAssignmentConfig' DataRecord that was last used in the iteration to true.
     *
     * @param indexAssignmentConfigs
     * @param oldLastIndexUsed
     * @param newLastIndexUsed
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws NotFound
     * @throws ServerException
     */
    private void setLastIndexUsed(List<DataRecord> indexAssignmentConfigs, Integer oldLastIndexUsed, int newLastIndexUsed) throws RemoteException, InvalidValue, IoError, NotFound, ServerException {
        if (oldLastIndexUsed != null && oldLastIndexUsed >= 0) {
            indexAssignmentConfigs.get(oldLastIndexUsed).setDataField("LastUsed", false, user);
        }
        indexAssignmentConfigs.get(newLastIndexUsed).setDataField("LastUsed", true, user);
    }

    /**
     * Method to get the position from the List of 'AutoIndexAssignmentConfig' to use as a start point based on position of last used record.
     *
     * @param lastUsedIndexPosition
     * @param indexAssignmentConfigs
     * @return int
     */
    private int getStartIndexAssignmentConfigPosition(int lastUsedIndexPosition, List<DataRecord> indexAssignmentConfigs) throws ServerException, NotFound, RemoteException {
        if (lastUsedIndexPosition >= indexAssignmentConfigs.size() - 1) {
            logInfo("Reached last Index, will start from first index position.");
            return 0;
        }
        int nextIndexToUse = lastUsedIndexPosition + 1; //start with one index position after last index used.
        for (int i = nextIndexToUse; i < indexAssignmentConfigs.size(); i++)
            if (indexAssignmentConfigs.get(i).getStringVal("WellId", user).toUpperCase().contains("A")) {
                return i;
            }
        return 0;
    }

    /**
     * Method to sort the DataRecords, First by 'SampleColumn' and then by 'SampleRow' field values in Ascending order.
     *
     * @param protocolRecords
     * @return List<DataRecord>
     */
    private List<DataRecord> getSampleProtocolRecordsSortedByWellPositionColumnWise(List<DataRecord> protocolRecords) throws RemoteException, NotFound, ServerException {
        List<String> sampleWells = new ArrayList<>();

        for (DataRecord rec : protocolRecords) {
            sampleWells.add(rec.getSelectionVal("SampleColumn", user) + rec.getSelectionVal("SampleRow", user));
        }
        List<String> sortedSampleWells = sampleWells.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
        List<DataRecord> sortedProtocolRecords = new ArrayList<>();
        for (String item : sortedSampleWells) {
            for (DataRecord rec : protocolRecords) {
                String wellId = rec.getSelectionVal("SampleColumn", user) + rec.getSelectionVal("SampleRow", user);
                if (wellId.equals(item)) {
                    sortedProtocolRecords.add(rec);
                    break;
                }
            }
        }
        return sortedProtocolRecords;
    }

    /**
     * Get the plate size given the Sample DataRecord(s) that is on a plate.
     *
     * @param samples
     * @return Integer plate size.
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private Integer getPlateSize(List<DataRecord> samples) throws IoError, RemoteException, NotFound, ServerException {
        DataRecord plate = samples.get(0).getParentsOfType("Plate", user).get(0);
        Integer plateSizeIndex = Integer.parseInt(plate.getValue("PlateWellCnt", user).toString());
        String plateSize = dataMgmtServer.getPickListManager(user).getPickListConfig("Plate Sizes").getEntryList().get(plateSizeIndex);
        return Integer.parseInt(plateSize.split("-")[0]);
    }

    /**
     * Method to populate field values of 'IndexBarcode' DataRecord.
     *
     * @param indexBarcode
     * @param indexAssignmentConfig
     * @param minVolInAdapterPlate
     * @param maxPlateVolume
     * @return Map<String, Object>
     * @throws NotFound
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws ServerException
     */
    private Map<String, Object> setAssignedIndicesDataRecordFieldValues(DataRecord indexBarcode, DataRecord indexAssignmentConfig, Double minVolInAdapterPlate, Double maxPlateVolume, Integer plateSize, String sampleType) throws NotFound, RemoteException, InvalidValue, IoError, ServerException {
        Double sampleInputAmount = 0.0;
        if (indexBarcode.getValue("InitialInput", user) != null) {
            sampleInputAmount = Double.parseDouble(indexBarcode.getStringVal("InitialInput", user));
        } else {
            clientCallback.displayError(String.format("Sample Input for Sample '%s' cannot be null. Please correct the values", indexBarcode.getStringVal("SampleId", user)));
            logError(String.format("Sample Input for Sample '%s' cannot be null. Please correct the values", indexBarcode.getStringVal("SampleId", user)));
        }
        String indexId = indexAssignmentConfig.getStringVal("IndexId", user);
        String indexTag = indexAssignmentConfig.getStringVal("IndexTag", user);
        String adapterPlate = indexAssignmentConfig.getStringVal("AdapterPlateId", user);
        String wellPosition = indexAssignmentConfig.getStringVal("WellId", user);
        String adapterSourceRow = autoHelper.getAdapterRowPosition(wellPosition);
        String adapterSourceCol = autoHelper.getAdapterColPosition(wellPosition);
        Double adapterStartConc = indexAssignmentConfig.getDoubleVal("AdapterConcentration", user);
        Double targetAdapterConc = autoHelper.getCalculatedTargetAdapterConcentration(sampleInputAmount, plateSize, sampleType);
        Double adapterVolume = autoHelper.getAdapterInputVolume(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, sampleType);
        Double waterVolume = autoHelper.getVolumeOfWater(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, maxPlateVolume, sampleType);
        Double actualTargetAdapterConc = adapterStartConc / ((waterVolume + adapterVolume) / adapterVolume);
        setUpdatedIndexAssignmentConfigVol(indexAssignmentConfig, adapterVolume);
        Map<String, Object> indexAssignmentValues = new HashMap<>();
        indexAssignmentValues.put("IndexId", indexId);
        indexAssignmentValues.put("IndexTag", indexTag);
        indexAssignmentValues.put("BarcodePlateID", adapterPlate);
        indexAssignmentValues.put("IndexRow", adapterSourceRow);
        indexAssignmentValues.put("IndexCol", adapterSourceCol);
        indexAssignmentValues.put("BarcodeVolume", adapterVolume);
        indexAssignmentValues.put("Aliq1WaterVolume", waterVolume);
        indexAssignmentValues.put("IndexBarcodeConcentration", actualTargetAdapterConc);
        return indexAssignmentValues;
    }

    /**
     * To set the value of Volume field on the records under 'AutoIndexAssignmentConfig' DataType.
     *
     * @param indexAssignmentConfig
     * @param adapterVolumeUsed
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    private void setUpdatedIndexAssignmentConfigVol(DataRecord indexAssignmentConfig, Double adapterVolumeUsed) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        Double previousVol = indexAssignmentConfig.getDoubleVal("AdapterVolume", user);
        Double newVolume = previousVol - adapterVolumeUsed;
        indexAssignmentConfig.setDataField("AdapterVolume", newVolume, user);

        if (newVolume <= 10) {
            indexAssignmentConfig.setDataField("IsDepelted", true, user);
            indexAssignmentConfig.setDataField("IsActive", false, user);
            clientCallback.displayWarning(String.format("The Volume for adapter '%s'on Adapter Plate with ID '%s' is below 10ul.\nThis adapter will be marked as depleted and will be ignored for future assignments.",
                    indexAssignmentConfig.getStringVal("IndexId", user), indexAssignmentConfig.getStringVal("AdapterPlateId", user)));
        }
    }

    /**
     * Split the List of DataRecords by alternate Well ID's. This is useful autoassignment when samples are on 384 well plates.
     *
     * @param protocolRecords
     * @return List<List < DataRecord>>
     * @throws ServerException
     * @throws NotFound
     * @throws RemoteException
     */
    private List<List<DataRecord>> getQuadrantsFromProtocols(List<DataRecord> protocolRecords) throws ServerException, NotFound, RemoteException {
        List<List<DataRecord>> protocolsByQuadrant = new ArrayList<>();
        List<DataRecord> quad1 = new ArrayList<>();
        List<DataRecord> quad2 = new ArrayList<>();
        List<DataRecord> quad3 = new ArrayList<>();
        List<DataRecord> quad4 = new ArrayList<>();
        for (DataRecord protocolRecord : protocolRecords) {
            int rowValue = protocolRecord.getStringVal("SampleRow", user).charAt(0);
            int colValue = Integer.parseInt(protocolRecord.getStringVal("SampleColumn", user));

            if (autoHelper.isOddValue(rowValue) && autoHelper.isOddValue(colValue)) {
                quad1.add(protocolRecord);
            }
            if (!autoHelper.isOddValue(rowValue) && autoHelper.isOddValue(colValue)) {
                quad2.add(protocolRecord);
            }
            if (autoHelper.isOddValue(rowValue) && !autoHelper.isOddValue(colValue)) {
                quad3.add(protocolRecord);
            }
            if (!autoHelper.isOddValue(rowValue) && !autoHelper.isOddValue(colValue)) {
                quad4.add(protocolRecord);
            }
        }
        if (!quad1.isEmpty()) {
            protocolsByQuadrant.add(getSampleProtocolRecordsSortedByWellPositionColumnWise(quad1));
        }
        if (!quad2.isEmpty()) {
            protocolsByQuadrant.add(getSampleProtocolRecordsSortedByWellPositionColumnWise(quad2));
        }
        if (!quad3.isEmpty()) {
            protocolsByQuadrant.add(getSampleProtocolRecordsSortedByWellPositionColumnWise(quad3));
        }
        if (!quad4.isEmpty()) {
            protocolsByQuadrant.add(getSampleProtocolRecordsSortedByWellPositionColumnWise(quad4));
        }
        return protocolsByQuadrant;
    }


    /**
     * Method to update 'IndexBarcode' records when samples are present on 96 well plates.
     *
     * @param indexAssignmentProtocolRecordsSortedColumnWise
     * @param indexAssignmentConfigs
     * @param minAdapterVol
     * @param plateSize
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    private void assignIndicesToSamples(List<DataRecord> indexAssignmentProtocolRecordsSortedColumnWise, List<DataRecord> indexAssignmentConfigs, Double minAdapterVol, Integer plateSize, String sampleType) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        Integer positionOfLastUsedIndex = getPositionOfLastUsedIndex(indexAssignmentConfigs);
        Double maxPlateVolume = autoHelper.getMaxVolumeLimit(plateSize);
        Integer updatedLastIndexUsed = getStartIndexAssignmentConfigPosition(positionOfLastUsedIndex, indexAssignmentConfigs);
        Set<String> indexAssignmentConfigPlatesToUse = new HashSet<>();
        for (int i = updatedLastIndexUsed, j = 0; i < indexAssignmentConfigs.size() && j < indexAssignmentProtocolRecordsSortedColumnWise.size(); i++, j++) {
            DataRecord indexAssignmentConfig = indexAssignmentConfigs.get(i);
            DataRecord indexBarcodeProtocolRecord = indexAssignmentProtocolRecordsSortedColumnWise.get(j);
            Map<String, Object> indexAssignmentValues = setAssignedIndicesDataRecordFieldValues(indexBarcodeProtocolRecord, indexAssignmentConfig, minAdapterVol, maxPlateVolume, plateSize, sampleType);
            indexBarcodeProtocolRecord.setFields(indexAssignmentValues, user);
            indexAssignmentConfigPlatesToUse.add(indexAssignmentConfig.getStringVal("AdapterPlateId", user));
            if (i == indexAssignmentConfigs.size() - 1 && j <= indexAssignmentProtocolRecordsSortedColumnWise.size()) {
                i = -1; //setting to -1 because at end of this body it will increment by 1
            }
            if (i < 0) {
                updatedLastIndexUsed = i + 1;
            } else {
                updatedLastIndexUsed = i;
            }
        }
        setLastIndexUsed(indexAssignmentConfigs, positionOfLastUsedIndex, updatedLastIndexUsed);
        checkIndexAssignmentsForDepletedAdapters(indexAssignmentConfigs);
        clientCallback.displayInfo(String.format("You need to use following adapter plates for this experiment:\n%s", StringUtils.join(indexAssignmentConfigPlatesToUse, "\n")));
        activeTask.getTask().getTaskOptions().put("_INDEXES_AUTO_ASIGNED", "");
    }

    /**
     * Method to check for the DataRecords in 'AutoIndexAssignmentConfig' for which the value of 'IsDepelted' should to marked to true.
     *
     * @param indexAssignmentConfigs
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    private void checkIndexAssignmentsForDepletedAdapters(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        for (DataRecord rec : indexAssignmentConfigs) {
            if (rec.getDoubleVal("AdapterVolume", user) < 10.00 && !rec.getBooleanVal("IsDepelted", user) && rec.getBooleanVal("IsActive", user)) {
                String wellId = rec.getStringVal("WellId", user);
                String column = wellId.substring(1);
                String indexPlateId = rec.getStringVal("AdapterPlateId", user);
                int setId = rec.getIntegerVal("SetId", user);
                clientCallback.displayWarning(String.format("Index ID '%s' on Adapter Plate '%s' has volume less than 10ul. Entire column will be marked Inactive and depleted.",
                        rec.getStringVal("IndexId", user), indexPlateId));
                logInfo(String.format("Index ID '%s' on Adapter Plate '%s' has volume less than 10ul.Entire column will be marked Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
                for (DataRecord record : indexAssignmentConfigs) {
                    String recPlateId = record.getStringVal("AdapterPlateId", user);
                    int set = record.getIntegerVal("SetId", user);
                    if (indexPlateId.equals(recPlateId) && set == setId) {
                        String col = record.getStringVal("WellId", user).substring(1);
                        if (col.equals(column)) {
                            record.setDataField("IsDepelted", true, user);
                            record.setDataField("IsActive", false, user);
                        }
                    }
                }
            }
        }
    }
}