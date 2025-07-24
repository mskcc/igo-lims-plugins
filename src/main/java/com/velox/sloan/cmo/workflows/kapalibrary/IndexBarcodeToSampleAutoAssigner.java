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

import javax.xml.crypto.Data;
import java.io.IOError;
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

    private final List<String> RECIPES_TO_USE_SPECIAL_ADAPTERS = Arrays.asList("DNA_CRISPR", "DNA_Amplicon", "DNA_SingleCellCNV");
    private boolean isTCRseq = false;
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

    public PluginResult run() throws ServerException, RemoteException{
        autoHelper = new AutoIndexAssignmentHelper();
        try {
            List<DataRecord> attachedSamplesList = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedIndexBarcodeRecords = new LinkedList<>();
            Set<DataRecord> uniquePlates = new HashSet<>();
            for(DataRecord sample: attachedSamplesList) {
                List<DataRecord> listOfParentPlates = sample.getParentsOfType("Plate", user);
                uniquePlates.add(listOfParentPlates.get(listOfParentPlates.size() - 1));
            }

            if (activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES").toLowerCase().contains("tcr")) {
                isTCRseq = true;
                attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IgoTcrSeqIndexBarcode", user);
            } else {
                attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IndexBarcode", user);
            }

            if(isTCRseq && attachedIndexBarcodeRecords.isEmpty()) {
                clientCallback.displayError(String.format("Could not find any TCRseq Barcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                logError(String.format("Could not find any TCRseq Barcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }

            if (attachedIndexBarcodeRecords.isEmpty() || attachedIndexBarcodeRecords.size() == 0) {
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

            for (DataRecord plate : uniquePlates) {
                List<DataRecord> IndexBarcodeRecordsForThisPlate = new LinkedList<>();
                logInfo("plate is: " + plate.getDataField("PlateId", user));
                DataRecord samplesInThePlate[] = plate.getChildrenOfType("Sample", user);
                for(DataRecord barcode: attachedIndexBarcodeRecords) {
                    for(DataRecord currentSample: samplesInThePlate) {
                        if(currentSample.getDataField("SampleId", user).toString().equals(barcode.getDataField("SampleId", user))) {
                            logInfo("current sample is:" + currentSample.getDataField("SampleId", user).toString());
                            IndexBarcodeRecordsForThisPlate.add(barcode);
                        }
                    }
                }

                String taskOptionValueForIndexAssignment = activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES");
                List<String> recipes = getUniqueSampleRecipes(Arrays.asList(samplesInThePlate));

                String indexTypeToProcess = getIndexTypesToUse(taskOptionValueForIndexAssignment);
                List<DataRecord> indexConfigsToUse = getIndexAssignmentConfigsForIndexType(indexTypeToProcess, recipes, Arrays.asList(samplesInThePlate));

                if (indexConfigsToUse.isEmpty()) {
                    clientCallback.displayError(String.format("Could not find 'AutoIndexAssignmentConfig' for Recipes/IndexTypes values '%s/%s' given to plugin 'AUTOASSIGN INDEX BARCODES", utils.convertListToString(recipes), indexTypeToProcess));
                    logError(String.format("Could not find 'AutoIndexAssignmentConfig' for Recipes '%s' for samples and TASK OPTION VALUE '%s' for Index Types given to Option 'AUTOASSIGN INDEX BARCODES", utils.convertListToString(recipes), indexTypeToProcess));
                    return new PluginResult(false);
                }
                List<DataRecord> sortedProtocolRecords = getSampleProtocolRecordsSortedByWellPositionColumnWise(IndexBarcodeRecordsForThisPlate);
                for(DataRecord sorted: sortedProtocolRecords) {
                    logInfo("sample id of sorted protocol records: " + sorted.getStringVal("SampleId", user));
                }
                Integer plateSize = getPlateSize(attachedSamplesList);
                Double minAdapterVol = autoHelper.getMinAdapterVolumeRequired(plateSize, isTCRseq);
                String sampleType = attachedSamplesList.get(0).getStringVal("ExemplarSampleType", user);
                if (plateSize == 96) {
                    assignIndicesToSamples(sortedProtocolRecords, indexConfigsToUse, minAdapterVol, plateSize, sampleType, isTCRseq, recipes);
                } else if (plateSize == 384) {
                    List<List<DataRecord>> protocolsSplitByAlternateWell = getQuadrantsFromProtocols(sortedProtocolRecords);
                    for (List<DataRecord> protocolsList : protocolsSplitByAlternateWell) {
                        assignIndicesToSamples(protocolsList, indexConfigsToUse, minAdapterVol, plateSize, sampleType, isTCRseq, recipes);
                    }
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
    private List<DataRecord> getIndexAssignmentConfigsForIndexType(String indexTypes, List<String> recipes, List<DataRecord> attachedSamplesList) throws IoError, RemoteException, NotFound, ServerException {
        boolean isCrisprOrAmpliconSeq = recipes.stream().anyMatch(RECIPES_TO_USE_SPECIAL_ADAPTERS::contains);
        String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";
        String species = attachedSamplesList.get(0).getStringVal("Species", user);

        if (indexTypes.toLowerCase().contains("tcrseq-igo")) {
            logInfo("Library samples have recipe values TCRseq-IGO, reserved indexes in set5 will not be used.");
            boolean isAlpha = recipes.get(0).toLowerCase().contains("alpha");
            boolean isBeta = recipes.get(0).toLowerCase().contains("beta");

            if (species.compareToIgnoreCase("mouse") == 0) {
                if (isAlpha) {
                    return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + " AND IsActive=1 AND SetId!=5 AND IndexId like 'M%' AND IndexId like '%acj%'", user);
                }
                else if (isBeta) {
                    return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + " AND IsActive=1 AND SetId!=5 AND IndexId like 'M%' AND IndexId like '%bcj%'", user);
                }
            }
            else { // species: human
                if (isAlpha) {
                    return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + " AND IsActive=1 AND SetId!=5 AND IndexId like 'H%' AND IndexId like '%acj%'", user);
                }
                else if (isBeta) {
                    return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + " AND IsActive=1 AND SetId!=5 AND IndexId like 'H%' AND IndexId like '%bcj%'", user);
                }
            }
        } else if (isCrisprOrAmpliconSeq) {
            logInfo("Recipe on Library samples is Crispr or AmpliconSeq, reserved indexes in plate5 will be used.");
            return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType='DUAL_IDT_LIB' AND AdapterPlateId LIKE 'Set%Plate5' and IsActive=1", user);

        } else {
            logInfo("Library samples do not have recipe values Crispr or AmpliconSeq, reserved indexes in plate5 will not be used.");
            return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + "AND IsActive=1 AND AdapterPlateId NOT LIKE 'Set%Plate5'", user);

        }
        return new LinkedList<DataRecord>();
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
    private Map<String, Object> setAssignedIndicesDataRecordFieldValues(DataRecord indexBarcode, DataRecord indexAssignmentConfig,
                                                                        Double minVolInAdapterPlate, Double maxPlateVolume,
                                                                        Integer plateSize, String sampleType, boolean isTCRseq, boolean isCrisprOrAmpliconSeq, String recipe) throws NotFound,
            RemoteException, InvalidValue, IoError, ServerException {
        Double sampleInputAmount = 0.0;
        if (indexBarcode.getValue("InitialInput", user) != null && indexBarcode.getStringVal("InitialInput", user).length() > 0) {
            logInfo("Parsing InitialInput value: " + indexBarcode.getStringVal("InitialInput", user));
            sampleInputAmount = Double.parseDouble(indexBarcode.getStringVal("InitialInput", user));
        } else {
            clientCallback.displayError(String.format("Sample Input for Sample '%s' cannot be null. Please correct the values", indexBarcode.getStringVal("SampleId", user)));
            logError(String.format("Sample Input for Sample '%s' cannot be null. Please correct the values", indexBarcode.getStringVal("SampleId", user)));
        }
        Double targetAdapterConc = autoHelper.getCalculatedTargetAdapterConcentration(sampleInputAmount, plateSize, sampleType);

        String indexId = indexAssignmentConfig.getStringVal("IndexId", user);
        String indexTag = indexAssignmentConfig.getStringVal("IndexTag", user);
        String adapterPlate = indexAssignmentConfig.getStringVal("AdapterPlateId", user);
        String wellPosition = indexAssignmentConfig.getStringVal("WellId", user);
        String adapterSourceRow = autoHelper.getAdapterRowPosition(wellPosition);
        String adapterSourceCol = autoHelper.getAdapterColPosition(wellPosition);
        Double adapterStartConc = indexAssignmentConfig.getDoubleVal("AdapterConcentration", user);
        Double adapterVolume = 0.0;
        Double waterVolume = 0.0;
        logInfo("recipe is = " + recipe);
        if (recipe.toLowerCase().contains("atac")) {
            logInfo("atac recipe sample gets 4ul adapter vol and 0 water.");
            adapterVolume = 4.0;
            waterVolume = 0.0;
        }
        else {
            adapterVolume = autoHelper.getAdapterInputVolume(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, sampleType, isTCRseq, isCrisprOrAmpliconSeq);
            waterVolume = autoHelper.getVolumeOfWater(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, maxPlateVolume, sampleType, isCrisprOrAmpliconSeq);
        }
        Double actualTargetAdapterConc = adapterStartConc / ((waterVolume + adapterVolume) / adapterVolume);
        setUpdatedIndexAssignmentConfigVol(indexAssignmentConfig, adapterVolume);
        Map<String, Object> indexAssignmentValues = new HashMap<>();

        indexAssignmentValues.put("IndexTag", indexTag);
        indexAssignmentValues.put("IndexId", indexId);
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

        if (newVolume <= 20) {
            indexAssignmentConfig.setDataField("IsDepelted", true, user);
            indexAssignmentConfig.setDataField("IsActive", false, user);
            clientCallback.displayWarning(String.format("The Volume for adapter '%s'on Adapter Plate with ID '%s' is below 20ul.\nThis adapter will be marked as depleted and will be ignored for future assignments.",
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
            if (autoHelper.isOddValue(rowValue) && !autoHelper.isOddValue(colValue)) {
                quad2.add(protocolRecord);
            }
            if (!autoHelper.isOddValue(rowValue) && autoHelper.isOddValue(colValue)) {
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
    private void assignIndicesToSamples(List<DataRecord> indexAssignmentProtocolRecordsSortedColumnWise, List<DataRecord>
            indexAssignmentConfigs, Double minAdapterVol, Integer plateSize, String sampleType, boolean isTCRseq, List<String> recipes) throws NotFound,
            RemoteException, IoError, InvalidValue, ServerException {
        boolean isCrisprOrAmpliconSeq = recipes.stream().anyMatch(RECIPES_TO_USE_SPECIAL_ADAPTERS::contains);
        Integer positionOfLastUsedIndex = getPositionOfLastUsedIndex(indexAssignmentConfigs);
        Double maxPlateVolume = autoHelper.getMaxVolumeLimit(plateSize);
        Integer updatedLastIndexUsed = getStartIndexAssignmentConfigPosition(positionOfLastUsedIndex, indexAssignmentConfigs);
        Set<String> indexAssignmentConfigPlatesToUse = new HashSet<>();
        for (int i = updatedLastIndexUsed, j = 0; i < indexAssignmentConfigs.size() && j < indexAssignmentProtocolRecordsSortedColumnWise.size(); i++, j++) {
            DataRecord indexAssignmentConfig = indexAssignmentConfigs.get(i);
            DataRecord indexBarcodeProtocolRecord = indexAssignmentProtocolRecordsSortedColumnWise.get(j);
            Map<String, Object> indexAssignmentValues = setAssignedIndicesDataRecordFieldValues(indexBarcodeProtocolRecord,
                    indexAssignmentConfig, minAdapterVol, maxPlateVolume, plateSize, sampleType, isTCRseq, isCrisprOrAmpliconSeq, recipes.get(0));
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
            if (rec.getDoubleVal("AdapterVolume", user) < 20.00 && !rec.getBooleanVal("IsDepelted", user) && rec.getBooleanVal("IsActive", user)) {
                String wellId = rec.getStringVal("WellId", user);
                String column = wellId.substring(1);
                String indexPlateId = rec.getStringVal("AdapterPlateId", user);
                int setId = rec.getIntegerVal("SetId", user);
                clientCallback.displayWarning(String.format("Index ID '%s' on Adapter Plate '%s' has volume less than 20ul. Entire column will be marked Inactive and depleted.",
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


    /**
     * Method to get the unique plates associated with Sample DataRecords.
     *
     * @param attachedSamples
     * @return List<String>
     */
    private List<Object> getAllUniquesPlates(List<DataRecord> attachedSamples) {
        List<Object> plates = new LinkedList<>();
        for (DataRecord s : attachedSamples) {
            try {
                plates.add(s.getAncestorsOfType("Plate", user));
            } catch (RemoteException | IoError | ServerException re) {
                logError("Exception happened while finding all unique plates for the attached samples", re);
            }
        }

        Set<Object> uniquePlates = new HashSet<>(plates);
        return new ArrayList<Object>(uniquePlates);
    }
}