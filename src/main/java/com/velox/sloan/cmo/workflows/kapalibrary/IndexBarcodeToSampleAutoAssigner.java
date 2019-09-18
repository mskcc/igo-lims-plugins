package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;

import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
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
 * @author sharmaa1
 */
public class IndexBarcodeToSampleAutoAssigner extends DefaultGenericPlugin{

    private final List<String> RECIPES_TO_USE_SPECIAL_ADAPTERS = Arrays.asList("CRISPRSeq", "AmpliconSeq");
    private final String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    AutoIndexAssignmentHelper autoHelper;

    public IndexBarcodeToSampleAutoAssigner() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder() + 2);
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().containsKey("AUTOASSIGN INDEX BARCODES") && !activeTask.getTask().getTaskOptions().containsKey("_INDEXES_AUTO_ASIGNED");
    }

    public PluginResult run() throws ServerException {
        autoHelper = new AutoIndexAssignmentHelper(managerContext);
        try{
            List<DataRecord> attachedSamplesList = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IndexBarcode", user);
            if(attachedIndexBarcodeRecords.isEmpty()){
                clientCallback.displayError(String.format("Could not find any IndexBarcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                logError(String.format("Could not find any IndexBarcode records attached to the TASK '%s'", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            if (attachedSamplesList.isEmpty()){
                clientCallback.displayError("No Samples found attached to this task.");
                logError("No Samples found attached to this task.");
                return new PluginResult(false);
            }
            if (StringUtils.isBlank(activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES")) &&
                    ! activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES").contains("INDEX TYPE")){
                clientCallback.displayError("Task Option 'VALIDATE UNIQUE SAMPLE RECIPE' is missing valid value. Please double check. Valid values should be in format 'INDEX TYPE (IDT | TruSeq)'");
                logError("Task Option 'VALIDATE UNIQUE SAMPLE RECIPE' is missing valid value. Please double check. Valid values should be in format 'INDEX TYPE (IDT | TruSeq)'");
                return new PluginResult(false);
            }

            String taskOptionValueForIndexAssignment = activeTask.getTask().getTaskOptions().get("AUTOASSIGN INDEX BARCODES");
            List<String> recipes = getUniqueSampleRecipes(attachedSamplesList);
            String indexTypeToProcess = getIndexTypesToUse(taskOptionValueForIndexAssignment);
            List<DataRecord> indexConfigsToUse = getIndexAssignmentConfigsForIndexType(indexTypeToProcess, recipes);
            if(indexConfigsToUse.isEmpty()){
                clientCallback.displayError(String.format("Could not find 'AutoIndexAssignmentConfig' for Recipes/IndexTypes values '%s/%s' given to plugin 'AUTOASSIGN INDEX BARCODES", utils.convertListToString(recipes), indexTypeToProcess));
                logError(String.format("Could not find 'AutoIndexAssignmentConfig' for Recipes '%s' for samples and TASK OPTION VALUE '%s' for Index Types given to Option 'AUTOASSIGN INDEX BARCODES", utils.convertListToString(recipes), indexTypeToProcess));
                return new PluginResult(false);
            }
            List<DataRecord> sortedProtocolRecords = getSampleProtocolRecordsSortedByWellPositionColumnWise(attachedIndexBarcodeRecords);
            Integer plateSize = autoHelper.getPlateSize(attachedSamplesList);
            Double minAdapterVol = autoHelper.getMinAdapterVolumeRequired(plateSize);
            if(plateSize==96) {
                assignIndicesToSamplesOn96WellPlate(sortedProtocolRecords, indexConfigsToUse, minAdapterVol, plateSize);
            }
            else{
                assignIndicesToSamplesOn384WellPlate(sortedProtocolRecords, indexConfigsToUse, minAdapterVol, plateSize);
            }
            checkIndexAssignmentsForDepletedAdapters(indexConfigsToUse);
        }catch (Exception e){
            clientCallback.displayError(String.format("Error while Auto Index assignment to samples:\n%s", ExceptionUtils.getStackTrace(e)));
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to get the IndexType to use for the autoassignment based on the value for Task Option 'AUTOASSIGN INDEX BARCODES' on the task.
     * @param taskOptionValueForIndexAssignment
     * @return String
     */
    private String getIndexTypesToUse( String taskOptionValueForIndexAssignment){
        List<String> indexTypes = new ArrayList<>();
        Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(taskOptionValueForIndexAssignment);
        if (m.find()){
            String [] values = m.group(1).split("\\|");
            for (String val: values){
                indexTypes.add(val.trim());
            }
        }
        return "('" + StringUtils.join(indexTypes,"','") + "')";
    }

    /**
     * Method to get the unique Recipe values associated with Sample DataRecords.
     * @param attachedSamples
     * @return List<String>
     */
    private List<String> getUniqueSampleRecipes(List<DataRecord> attachedSamples){
        List<String> recipes = attachedSamples.stream().map(s -> {
            try {
                return s.getStringVal("Recipe", user);
            } catch (NotFound | RemoteException notFound){
                notFound.printStackTrace();
                return "";
            }
        }).collect(Collectors.toList());
        Set<String> uniqueRecipes = new HashSet<>(recipes);
        return new ArrayList<String>(uniqueRecipes);
    }

    /**
     * Method to get DataRecords for 'AutoIndexAssignmentConfig' DataType that are marked as Active.
     * @param indexTypes
     * @param recipes
     * @return List<DataRecord>
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private List<DataRecord> getIndexAssignmentConfigsForIndexType(String indexTypes, List<String> recipes) throws IoError, RemoteException, NotFound, ServerException {
        Boolean isCrisprOrAmpliconSeq = recipes.stream().filter(RECIPES_TO_USE_SPECIAL_ADAPTERS :: contains).collect(Collectors.toList()).size()>0;
        if (!isCrisprOrAmpliconSeq) {
            logInfo("Library samples do not have recipe values Crispr or AmpliconSeq, reserved indexes in set5 will not be used.");
            return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType IN " + indexTypes + "AND IsActive=1 AND SetId!=5", user);
        }
        else {
            logInfo("Recipe on Library samples is Crispr or AmpliconSeq, reserved indexes in set5 will be used.");
            return dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IndexType='DUAL_IDT_LIB' AND SetId=5 and IsActive=1", user);
        }
    }

    /**
     * Method to get the position of last 'AutoIndexAssignmentConfig' DataRecord that was used for Auto Index Assignment process.
     * @param indexAssignmentConfigs
     * @return Integer
     * @throws NotFound
     * @throws RemoteException
     */
    private Integer getPositionOfLastUsedIndex(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException {
        for (int i=0; i< indexAssignmentConfigs.size(); i ++){
            if (indexAssignmentConfigs.get(i).getBooleanVal("LastUsed", user)){
                return i;
            }
        }
        return -1;
    }

    /**
     * Method to sort the DataRecords, First by 'SampleColumn' and then by 'SampleRow' field values in Ascending order.
     * @param protocolRecords
     * @return List<DataRecord>
     */
    private List<DataRecord> getSampleProtocolRecordsSortedByWellPositionColumnWise(List<DataRecord> protocolRecords){
        protocolRecords.sort(Comparator.comparing(s -> {
            try {
                return s.getSelectionVal("SampleColumn", user) + s.getSelectionVal("SampleRow", user);
            } catch (NotFound | RemoteException notFound) {
                notFound.printStackTrace();
                return "";
            }
         }));
        return protocolRecords;
    }

    /**
     * Method to populate field values of 'IndexBarcode' DataRecord.
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
    private Map<String, Object> setAssignedIndicesDataRecordFieldValues(DataRecord indexBarcode, DataRecord indexAssignmentConfig, Double minVolInAdapterPlate, Double maxPlateVolume) throws NotFound, RemoteException, InvalidValue, IoError, ServerException {
        Double dnaInputAmount = 0.0;
        if (indexBarcode.getValue("InitialInput", user)!=null){
            dnaInputAmount = Double.parseDouble(indexBarcode.getStringVal("InitialInput", user));
        }
        else{
            clientCallback.displayError(String.format("Dna Input for Sample '%s' cannot be null. Please correct the values", indexBarcode.getStringVal("SampleId", user)));
            logError(String.format("Dna Input for Sample '%s' cannot be null. Please correct the values", indexBarcode.getStringVal("SampleId", user)));
        }
        String indexId = indexAssignmentConfig.getStringVal("IndexId", user);
        String indexTag = indexAssignmentConfig.getStringVal("IndexTag", user);
        String adapterPlate = indexAssignmentConfig.getStringVal("AdapterPlateId", user);
        String wellPosition = indexAssignmentConfig.getStringVal("WellId", user);
        String adapterSourceRow = autoHelper.getAdapterRowPosition(wellPosition);
        String adapterSourceCol = autoHelper.getAdapterColPosition(wellPosition);
        Double adapterStartConc = indexAssignmentConfig.getDoubleVal("AdapterConcentration", user);
        Double targetAdapterConc = autoHelper.getCalculatedTargetAdapterConcentration(dnaInputAmount);
        Double adapterVolume = autoHelper.getAdapterInputVolume(adapterStartConc, minVolInAdapterPlate, targetAdapterConc);
        Double waterVolume = autoHelper.getVolumeOfWater(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, maxPlateVolume);
        Double actualTargetAdapterConc = adapterStartConc/((waterVolume + adapterVolume)/adapterVolume);
        autoHelper.setUpdatedIndexAssignmentConfigVol(indexAssignmentConfig, adapterVolume);
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
     * Set the 'LastUsed' value of 'AutoIndexAssignmentConfig' DataRecord that was last used in the iteration to true.
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
        if (oldLastIndexUsed >=0) {
            indexAssignmentConfigs.get(oldLastIndexUsed).setDataField("LastUsed", false, user);
        }
        indexAssignmentConfigs.get(newLastIndexUsed).setDataField("LastUsed", true, user);
    }

    /**
     * Split the List of DataRecords by alternate Well ID's. This is useful autoassignment when samples are on 384 well plates.
     * @param protocolRecords
     * @return List<List<DataRecord>>
     * @throws ServerException
     * @throws NotFound
     * @throws RemoteException
     */
    private List<List<DataRecord>> splitListByAlternatePosition(List<DataRecord> protocolRecords) throws ServerException, NotFound, RemoteException {
        List<List<DataRecord>> splitList = new ArrayList<>();
        List<DataRecord> listA = new ArrayList<>();
        List<DataRecord> listB = new ArrayList<>();
        for (DataRecord protocolRecord : protocolRecords) {
            if ((int)protocolRecord.getStringVal("SampleRow", user).charAt(0) % 2 != 0) {
                listA.add(protocolRecord);
            } else {
                listB.add(protocolRecord);
            }
        }
        if(!listA.isEmpty()) {
            splitList.add(getSampleProtocolRecordsSortedByWellPositionColumnWise(listA));
        }
        if(!listB.isEmpty()) {
            splitList.add(getSampleProtocolRecordsSortedByWellPositionColumnWise(listB));
        }
        return splitList;
    }

    /**
     * Method to update 'IndexBarcode' records when samples are present on 96 well plates.
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
    private void assignIndicesToSamplesOn96WellPlate(List<DataRecord> indexAssignmentProtocolRecordsSortedColumnWise, List<DataRecord> indexAssignmentConfigs, Double minAdapterVol, Integer plateSize) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        Integer positionOfLastUsedIndex = getPositionOfLastUsedIndex(indexAssignmentConfigs);
        Double maxPlateVolume = autoHelper.getMaxVolumeLimit(plateSize);
        Integer updatedLastIndexUsed = getStartIndexAssignmentConfigPosition(positionOfLastUsedIndex, indexAssignmentConfigs);
        Set<String> indexAssignmentConfigPlatesToUse = new HashSet<>();
        for (int i= updatedLastIndexUsed, j=0; i < indexAssignmentConfigs.size() &&  j < indexAssignmentProtocolRecordsSortedColumnWise.size(); i++, j++) {
            DataRecord indexBarcodeProtocolRecord = indexAssignmentProtocolRecordsSortedColumnWise.get(j);
            DataRecord indexAssignmentConfig = indexAssignmentConfigs.get(i);
            Map<String, Object> indexAssignmentValues = setAssignedIndicesDataRecordFieldValues(indexBarcodeProtocolRecord, indexAssignmentConfig, minAdapterVol, maxPlateVolume);
            indexBarcodeProtocolRecord.setFields(indexAssignmentValues, user);
            indexAssignmentConfigPlatesToUse.add(indexAssignmentConfig.getStringVal("AdapterPlateId", user));
            if (i >= indexAssignmentConfigs.size() && j < indexAssignmentProtocolRecordsSortedColumnWise.size()) {
                i = 0;
            }
            updatedLastIndexUsed = i;
        }
        setLastIndexUsed(indexAssignmentConfigs, positionOfLastUsedIndex, updatedLastIndexUsed);
        clientCallback.displayInfo(String.format("You need to use following adapter plates for this experiment:\n%s", StringUtils.join(indexAssignmentConfigPlatesToUse, "\n")));
        activeTask.getTask().getTaskOptions().put("_INDEXES_AUTO_ASIGNED","");
    }

    /**
     * Method to get the position from the List of 'AutoIndexAssignmentConfig' to use as a start point based on position of last used record.
     * @param lastUsedIndexPosition
     * @param indexAssignmentConfigs
     * @return int
     */
    private int getStartIndexAssignmentConfigPosition(int lastUsedIndexPosition, List<DataRecord> indexAssignmentConfigs){
        if (lastUsedIndexPosition == indexAssignmentConfigs.size()-1){
            return 0;
        }
        else{
            return lastUsedIndexPosition +1;
        }
    }

    /**
     * Method to update 'IndexBarcode' records when samples are present on 384 well plates.
     * @param indexAssignmentProtocolRecordsSortedColumnWise
     * @param indexAssignmentConfigs
     * @param minAdapterVol
     * @param plateSize
     * @throws NotFound
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws ServerException
     */
    private void assignIndicesToSamplesOn384WellPlate(List<DataRecord> indexAssignmentProtocolRecordsSortedColumnWise, List<DataRecord> indexAssignmentConfigs, Double minAdapterVol, Integer plateSize) throws NotFound, RemoteException, InvalidValue, IoError, ServerException {
        Integer positionOfLastUsedIndex = getPositionOfLastUsedIndex(indexAssignmentConfigs);
        Double maxPlateVolume = autoHelper.getMaxVolumeLimit(plateSize);
        int updatedLastIndexUsed = positionOfLastUsedIndex;
        Set<String> indexAssignmentConfigPlatesToUse = new HashSet<>();
        List<List<DataRecord>> protocolsSplitByAlternateWell = splitListByAlternatePosition(indexAssignmentProtocolRecordsSortedColumnWise);
        for(List<DataRecord> list : protocolsSplitByAlternateWell){
            int nextIndexToUse = getStartIndexAssignmentConfigPosition(updatedLastIndexUsed, indexAssignmentConfigs);
            for(int i = nextIndexToUse, j = 0; i < indexAssignmentConfigs.size() && j < list.size(); i++, j++){
                clientCallback.displayInfo("index position:" + i);
                clientCallback.displayInfo("next index " + nextIndexToUse);
                DataRecord indexBarcodeProtocolRecord = list.get(j);
                DataRecord indexAssignmentConfig = indexAssignmentConfigs.get(i);
                Map<String, Object> indexAssignmentValues = setAssignedIndicesDataRecordFieldValues(indexBarcodeProtocolRecord, indexAssignmentConfig, minAdapterVol, maxPlateVolume);
                indexBarcodeProtocolRecord.setFields(indexAssignmentValues, user);
                indexAssignmentConfigPlatesToUse.add(indexAssignmentConfig.getStringVal("AdapterPlateId", user));
                if (i >= indexAssignmentConfigs.size() && j < list.size()){
                    i = 0;
                }
                updatedLastIndexUsed = i;
            }
        }
        setLastIndexUsed(indexAssignmentConfigs, positionOfLastUsedIndex, updatedLastIndexUsed);
        clientCallback.displayInfo(String.format("You need to use following adapter plates for this experiment:\n%s", StringUtils.join(indexAssignmentConfigPlatesToUse, "\n")));
        activeTask.getTask().getTaskOptions().put("_INDEXES_AUTO_ASIGNED","");
    }

    /**
     * Method to check for the DataRecords in 'AutoIndexAssignmentConfig' for which the value of 'IsDepelted' should to marked to true.
     * @param indexAssignmentConfigs
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    private void checkIndexAssignmentsForDepletedAdapters(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        for (DataRecord rec: indexAssignmentConfigs){
            if (rec.getDoubleVal("AdapterVolume", user) < 10.00){
                rec.setDataField("IsDepelted", true, user);
                rec.setDataField("IsActive", false, user);
                clientCallback.displayWarning(String.format("AutoIndexAssignmentConfig with Index ID '%s' on Adapter Plate '%s' has volume less than 10ul. It is now marked as Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
                logInfo(String.format("AutoIndexAssignmentConfig with Index ID '%s' on Adapter Plate '%s' has volume less than 10ul. It is now marked as Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
            }
        }
    }
}
