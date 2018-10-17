package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datafielddefinition.DataFieldDefinitions;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sapioutils.shared.utilities.FormBuilder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Ajay Sharma
 * For the Kapa Library Preparation workflow write the well locations to DNALibraryPrepProtocols
 * sorted based on IGO ID.  (User doesn't need to drag and drop).
 */

public class SampleToPlateAutoAssigner extends DefaultGenericPlugin {

    private final int MIN_PLATE_ROWS = 8;
    private final int MIN_PLATE_COLUMNS = 12;
    private final List<String> PLATE_SIZES = Arrays.asList("96", "384");

    public SampleToPlateAutoAssigner() {
        setTaskEntry(true);
        setOrder(PluginOrder.LATE.getOrder());
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().keySet().contains("SORT AND ASSIGN SAMPLES TO PLATE");
    }

    @Override
    public PluginResult run() throws com.velox.api.util.ServerException {
        try {
            List<DataRecord> attachedRecords = activeTask.getAttachedDataRecords(user);
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            String taskOptionValue = activeTask.getTask().getTaskOptions().get("SORT AND ASSIGN SAMPLES TO PLATE");
            if (!hasValidTaskOptionValueForPlugin(taskOptionValue)) {
                return new PluginResult(false);
            }
            String newPlateProtocolRecord = getNewPlateProtocolRecordName(taskOptionValue);
            if (!hasProtocolRecords(attachedRecords, newPlateProtocolRecord)) {
                return new PluginResult(false);
            }
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords(newPlateProtocolRecord, user);
            String destinationPlateFieldName = getDestinationPlateIdFieldName(taskOptionValue);
            String destinationWellFieldName = getDestinationWellFieldName(taskOptionValue);
            String destinationPlateSize = getPlateSizeFromUser(PLATE_SIZES);
            if (StringUtils.isBlank(destinationPlateSize)) {
                logError("Process canceled by user.");
                return new PluginResult(false);
            }
            List<String> recipes = getRecipesForAttachedSamples(attachedSampleRecords);
            if (destinationPlateSize.equals(PLATE_SIZES.get(0))) {
                int plateSizeDestination = Integer.parseInt(destinationPlateSize);
                List<String> sortedSampleIds = getSampleIdsSortedSeparatedByRecipe(attachedSampleRecords, recipes);
                List<String> newPlateIds = getNewPlateIdsFromUserFor96To96Transfer(attachedSampleRecords, plateSizeDestination);
                assignWellPositionsFor96WellPlate(sortedSampleIds, plateSizeDestination, newPlateIds, destinationPlateFieldName, destinationWellFieldName, attachedProtocolRecords);
            }
            String sourcePlateSize = getSourcePlateSize(attachedSampleRecords);

            if (sourcePlateSize.equals(PLATE_SIZES.get(0)) && destinationPlateSize.equals(PLATE_SIZES.get(1))) {
                String destinationPlateId384 = getNewPlateIdsFromUserFor96To384Transfer();
                List<Map<String, Object>> quadrantValues = getPlateIdToQuadrantValues(attachedSampleRecords);
                if (!isValidQuadrantValues(quadrantValues)) {
                    return new PluginResult(false);
                }
                assignPositionsMapFor96To384WellPlate(attachedSampleRecords, destinationPlateId384, quadrantValues, destinationPlateFieldName, destinationWellFieldName, attachedProtocolRecords);
            }

            if (sourcePlateSize.equals(PLATE_SIZES.get(1)) && destinationPlateSize.equals(PLATE_SIZES.get(1))) {
                String destinationPlateId384 = getNewPlateIdsFromUserFor96To384Transfer();
                assignPositionsMapFor384To384WellPlate(attachedSampleRecords, destinationPlateId384, destinationPlateFieldName, destinationWellFieldName, attachedProtocolRecords);
            }
            if (sourcePlateSize.equals(PLATE_SIZES.get(1)) && destinationPlateSize.equals(PLATE_SIZES.get(0))) {
                clientCallback.displayError("Incompatible Source plate size: 384, to destination plate size: 96");
                return new PluginResult(false);
            }

            setIsControlTrueForNewControls(attachedProtocolRecords);
        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while sample assignment to plates. CAUSE:\n%s", e));
            logError(e);
        }
        return new PluginResult(true);

    }

    /**
     * To confirm that a valid protocol Datatype is attached to the task.
     *
     * @param attachedRecords
     * @param newPlateProtocolName
     * @return boolean
     * @throws RemoteException
     * @throws com.velox.api.util.ServerException
     */
    private boolean hasProtocolRecords(List<DataRecord> attachedRecords, String newPlateProtocolName) throws RemoteException, com.velox.api.util.ServerException {
        boolean found = false;
        if (attachedRecords.isEmpty()) {
            clientCallback.displayError(String.format("No DataRecord was found attached to this task : %s", activeTask.getTask().getTaskName()));
            return false;
        }
        for (DataRecord record : attachedRecords) {
            if (record.getDataTypeName().toLowerCase().equals(newPlateProtocolName.toLowerCase())) {
                found = true;
            }
        }
        if (!found) {
            clientCallback.displayError(String.format("Cannot find a valid 'protocol' record attached to this task : %s", activeTask.getTask().getTaskName()));
            found = false;
        }
        return found;
    }

    /**
     * Validate that correct task option value is set for tag that will invoke the plugin.
     *
     * @param taskOption
     * @return boolean
     * @throws com.velox.api.util.ServerException
     */
    private boolean hasValidTaskOptionValueForPlugin(String taskOption) throws com.velox.api.util.ServerException {
        if (StringUtils.isEmpty(taskOption) || taskOption.split("\\|").length != 3) {
            clientCallback.displayError("Invalid task option values for 'SORT AND ASSIGN SAMPLES TO PLATE' option." +
                    "\nValid values should be 'AliquotProtocolRecordName | DestinationPlateIdFieldName | DestinationWellFieldName' as in the attached dataType");
            return false;
        }
        return true;
    }

    /**
     * Get the name of the Protocol Datatype from the task option tag value.
     *
     * @param taskOptionValue
     * @return String
     */
    private String getNewPlateProtocolRecordName(String taskOptionValue) {
        return taskOptionValue.split("\\|")[0].replaceAll("\\s+", "");
    }

    /**
     * Get the name of the Protocol DataField that will hold PlateId, from the task option tag value.
     *
     * @param taskOptionValue
     * @return String
     */
    private String getDestinationPlateIdFieldName(String taskOptionValue) {
        return taskOptionValue.split("\\|")[1].replaceAll("\\s+", "");
    }

    /**
     * Get the name of Protocol DataField that will hold the destintionWellId, from the task option tag value.
     *
     * @param taskOptionValue
     * @return String
     */
    private String getDestinationWellFieldName(String taskOptionValue) {
        return taskOptionValue.split("\\|")[2].replaceAll("\\s+", "");
    }

    /**
     * Get the number of plate rows based on plateSize.
     *
     * @param plateSize
     * @return int
     */
    private int getNumberOfPlateRows(int plateSize) {
        int numOfRows = 0;
        if (plateSize == (MIN_PLATE_ROWS * MIN_PLATE_COLUMNS)) {
            numOfRows = MIN_PLATE_ROWS;
        }
        if (plateSize == (MIN_PLATE_ROWS * MIN_PLATE_COLUMNS) * 4) {
            numOfRows = MIN_PLATE_ROWS * 2;
        }
        if (plateSize == (MIN_PLATE_ROWS * MIN_PLATE_COLUMNS) * 16) {
            numOfRows = MIN_PLATE_ROWS * 4;
        }
        return numOfRows;
    }

    /**
     * Get the number of plate columns based on plateSize.
     *
     * @param plateSize
     * @return int
     */
    private int getNumberOfPlateColumns(int plateSize) {
        int numOfColumns = 0;
        if (plateSize == (MIN_PLATE_ROWS * MIN_PLATE_COLUMNS)) {
            numOfColumns = MIN_PLATE_COLUMNS;
        }
        if (plateSize == (MIN_PLATE_ROWS * MIN_PLATE_COLUMNS) * 4) {
            numOfColumns = MIN_PLATE_COLUMNS * 2;
        }
        if (plateSize == (MIN_PLATE_ROWS * MIN_PLATE_COLUMNS) * 8) {
            numOfColumns = MIN_PLATE_ROWS * 4;
        }
        return numOfColumns;
    }

    /**
     * Get the well ID's for plate wells using plate size, A1,B1,C1....H12
     *
     * @param plateSize
     * @return List<String>
     */
    private List<String> getPlateWells(int plateSize) {
        int numberOfRows = getNumberOfPlateRows(plateSize);
        int numberOfColumns = getNumberOfPlateColumns(plateSize);
        List<String> plateWells = new ArrayList<>();
        for (int i = 1; i <= numberOfColumns; i++) {
            for (int j = 65; j < 65 + numberOfRows; j++) {
                plateWells.add((char) j + Integer.toString(i));
            }
        }
        return plateWells;
    }

    /**
     * Get unique recipe value list for attached samples
     *
     * @param attachedSamples
     * @return List<String>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getRecipesForAttachedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> recipes = new ArrayList<>();
        for (DataRecord sample : attachedSamples) {
            String recipe = sample.getStringVal("Recipe", user);
            if (!recipes.contains(recipe)) {
                recipes.add(recipe);
            }
        }
        Collections.sort(recipes);
        return recipes;
    }

    /**
     * Sort sample Ids using alphanumeric sorting methods.
     *
     * @param attachedSamples
     * @return List<String>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getSampleIdsSortedSeparatedByRecipe(List<DataRecord> attachedSamples, List<String>recipes ) throws NotFound, RemoteException {
        List<String> sampleIds = new ArrayList<>();
        for(String recipe : recipes){
            List<String> sampleIdsForRecipe = new ArrayList<>();
            for (DataRecord sample: attachedSamples){
                String sampleId = sample.getStringVal("SampleId", user);
                String sampleRecipe = sample.getStringVal("Recipe",user);
                if (sampleRecipe.toLowerCase().equals(recipe.toLowerCase())){
                    sampleIdsForRecipe.add(sampleId);
                }
            }
            List<String> sortedSampleIdsForRecipe = sampleIdsForRecipe.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
            sampleIds.addAll(sortedSampleIdsForRecipe);
            logInfo(sampleIds.toString());
        }
        return sampleIds;
    }

    /**
     * Sort sample Ids using alphanumeric sorting methods.
     *
     * @param dataRecords
     * @return List<String>
     * @throws NotFound
     * @throws RemoteException
     */
//    private List<String> getSortedSampleIds(List<DataRecord> dataRecords) throws NotFound, RemoteException {
//        List<String> sampleIds = new ArrayList<>();
//        for (DataRecord sample : dataRecords) {
//            sampleIds.add(sample.getStringVal("SampleId", user));
//        }
//        return sampleIds.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
//    }

    /**
     * Get the destination plate size from user to which samples will be assigned.
     *
     * @param platesizes
     * @return String
     */
    private String getPlateSizeFromUser(List<String> platesizes) {
        List plateDim = clientCallback.showListDialog("Please Select the Destination Plate size", platesizes, false, user);
        if (plateDim == null) {
            return "";
        }
        return plateDim.get(0).toString();
    }

    /**
     * Get the size of the plate which holds the samples before transfer.
     *
     * @param attachedSamples
     * @return String
     * @throws IoError
     * @throws RemoteException
     * @throws NotFound
     * @throws ServerException
     */
    private String getSourcePlateSize(List<DataRecord> attachedSamples) throws IoError, RemoteException, NotFound, ServerException {
        Object plateSize = null;
        for(DataRecord sample : attachedSamples){
            String containerType = sample.getStringVal("ContainerType",user);
            if (containerType.toLowerCase().equals("plate")){
                String plateName = sample.getParentsOfType("Plate", user).get(0).getStringVal("PlateId", user);
                plateSize = sample.getParentsOfType("Plate",user).get(0).getShortVal("PlateWellCnt", user);
                if (plateSize == null) {
                    clientCallback.displayError(String.format("Plate well count not defined for plate '%s'\nSetting plate size to 96.", plateName));
                    return "96";
                }
            }
        }
        return PLATE_SIZES.get((short) plateSize);
    }

    /**
     * Get the unique source plate Ids for the attached samples.
     *
     * @param attachedSamples
     * @return List<String>
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getPlateIdsForAttachedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> uniquePlateIds = new ArrayList<>();
        for (DataRecord sample : attachedSamples) {
            String plateId = sample.getStringVal("RelatedRecord23", user);
            if (!uniquePlateIds.contains(plateId)) {
                uniquePlateIds.add(plateId);
            }
        }
        logInfo("Number of Plates : " + uniquePlateIds.size());
        return uniquePlateIds;
    }

    /**
     * Get the quadrant values that each source plate must be assigned to.
     *
     * @param uniquePlateIds
     * @return List<Map   <   String   ,   Object>>
     */
    private List<Map<String, Object>> getValueMapForPlateQuadrant(List<String> uniquePlateIds) {
        List<Map<String, Object>> uniquePlates = new ArrayList<>();
        for (String plateId : uniquePlateIds) {
            Map<String, Object> plateValuesMap = new HashMap<>();
            plateValuesMap.put("PlateId", plateId);
            plateValuesMap.put("QuadrantNumber", "");
            uniquePlates.add(plateValuesMap);
        }
        return uniquePlates;
    }

    /**
     * Get the number of new plates needed to make transfer from 96 well plates to 96 well plates.
     *
     * @param sampleSize
     * @param plateSize
     * @return int
     */
    private int getNumberOfNewPlatesFor96To96Transfer(int sampleSize, int plateSize) {
        logInfo("Number of plates needed : " + Integer.toString((int) Math.ceil((double) sampleSize / (double) plateSize)));
        return (int) Math.ceil((double) sampleSize / (double) plateSize);
    }

    /**
     * Get the Plate Ids to which the samples will be transferred to.
     *
     * @param attachedSamples
     * @param plateSize
     * @return List<String>
     * @throws com.velox.api.util.ServerException
     * @throws RemoteException
     */
    private List<String> getNewPlateIdsFromUserFor96To96Transfer(List<DataRecord> attachedSamples, int plateSize) throws com.velox.api.util.ServerException, RemoteException {
        List<String> plateDisplayFields = Arrays.asList("PlateNumber", "PlateId");
        FormBuilder formBuilder = new FormBuilder(managerContext);
        formBuilder.addFieldDefinitions(new HashMap<String, FormBuilder.FieldDefinitionValues>(), plateDisplayFields, "PlateIdForm", "PlateEntries96to96");
        DataFieldDefinitions plateIdFieldDefinitions = formBuilder.getDataFieldDefinitions();
        int numberOfNewPlates = getNumberOfNewPlatesFor96To96Transfer(attachedSamples.size(), plateSize);
        logInfo("NumberOfNewPlates needed: " + numberOfNewPlates);
        List<Map<String, Object>> plateIdValues = new ArrayList<>();
        for (int i = 1; i <= numberOfNewPlates; i++) {
            Map<String, Object> plateIdValue = new HashMap<>();
            plateIdValue.put("PlateNumber", i);
            plateIdValue.put("PlateId", "");
            plateIdValues.add(plateIdValue);
        }
        List<Map<String, Object>> plateIdsFromUser = clientCallback.showTableEntryDialog("Enter the New Plate Ids",
                "Enter new Plate ID's and their order for sample assignment. eg: enter 1, 2 ,3 and so on.", plateIdFieldDefinitions, plateIdValues);
        return extractPlateIdsFromUserInputFor96To96Transfer(plateIdsFromUser, numberOfNewPlates);
    }

    /**
     * Get the plate IDs from values entered by user for new plates.
     *
     * @param plateIdValues
     * @param numberOfNewPlates
     * @return List<String>
     * @throws com.velox.api.util.ServerException
     */
    private List<String> extractPlateIdsFromUserInputFor96To96Transfer(List<Map<String, Object>> plateIdValues, int numberOfNewPlates) throws com.velox.api.util.ServerException {
        List<String> plateIds = new ArrayList<>();
        for (int i = 1; i <= numberOfNewPlates; i++) {
            for (Map map : plateIdValues) {
                String plateId = map.get("PlateId").toString();
                int plateNum = Integer.parseInt(map.get("PlateNumber").toString());
                if (plateNum == i && !plateIds.contains(plateId)) {
                    plateIds.add(plateId);
                }
            }
        }
        if (plateIds.size() != numberOfNewPlates) {
            clientCallback.displayError(String.format("Number of new plates '%d' not same as number of unique new Plate Barcodes %s entered.", numberOfNewPlates, plateIds.toString()));
        }
        return plateIds;
    }

    /**
     * Assign well positions to samples when transferring between 96 well plates.
     *
     * @param sortedSampleIds
     * @param plateSize
     * @param plateIds
     * @param targetPlateIdFieldName
     * @param targetWellIdFieldName
     * @param newPlateProtocolRecordName
     * @throws com.velox.api.util.ServerException
     * @throws RemoteException
     */
    private void assignWellPositionsFor96WellPlate(List<String> sortedSampleIds, int plateSize, List<String> plateIds, String targetPlateIdFieldName, String targetWellIdFieldName, List<DataRecord> newPlateProtocolRecordName) throws com.velox.api.util.ServerException, RemoteException {
        List<String> plateWellIds = getPlateWells(plateSize);
        int i = 0;
        List<Map<String, Object>> sampleToPlatePositionValues = new ArrayList<>();
        int numberOfNewPlates = getNumberOfNewPlatesFor96To96Transfer(sortedSampleIds.size(), plateSize);
        for (int j = 0; j < numberOfNewPlates; j++) {
            int k;
            for (k = 0; k < plateSize && i < sortedSampleIds.size(); k++, i++) {
                Map<String, Object> sampleToPlatePositionValue = new HashMap<>();
                sampleToPlatePositionValue.put("SampleId", sortedSampleIds.get(i));
                sampleToPlatePositionValue.put(targetPlateIdFieldName, plateIds.get(j));
                sampleToPlatePositionValue.put(targetWellIdFieldName, plateWellIds.get(k));
                sampleToPlatePositionValues.add(sampleToPlatePositionValue);
            }
        }
        addPlateDimensionsToTaskOptions(plateSize);
        dataRecordManager.setFieldsForRecords(newPlateProtocolRecordName, sampleToPlatePositionValues, user);
    }

    /**
     * Get PlateId and corresponding quadrant number from user via table input dialog box.
     *
     * @param attachedSamples
     * @return List<Map   <   String   ,       Object>>
     * @throws com.velox.api.util.ServerException
     * @throws RemoteException
     * @throws NotFound
     */
    private List<Map<String, Object>> getPlateIdToQuadrantValues(List<DataRecord> attachedSamples) throws com.velox.api.util.ServerException, RemoteException, NotFound {
        List<Map<String, Object>> plateToQuadrantValuesFromUser;
        List<String> plateIds = getPlateIdsForAttachedSamples(attachedSamples);
        List<Map<String, Object>> plateToQuadrantValuesForTableDisplay = getValueMapForPlateQuadrant(plateIds);
        DataFieldDefinitions definitions = dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions("PlateToQuadrantEntries");
        plateToQuadrantValuesFromUser = clientCallback.showTableEntryDialog("Please enter quadrant values for plates", "Please enter the 384 well plate quadrant values " +
                "for each 96 well plate in the cell next to it.", definitions, plateToQuadrantValuesForTableDisplay);
        return plateToQuadrantValuesFromUser;
    }

    /**
     * Confirm that entered quadrant values are valid.
     *
     * @param quadrantValues
     * @return boolean
     * @throws com.velox.api.util.ServerException
     */
    private boolean isValidQuadrantValues(List<Map<String, Object>> quadrantValues) throws com.velox.api.util.ServerException {
        Set<String> quadrantVal = new HashSet<>();
        if (quadrantValues.isEmpty()) {
            clientCallback.displayError("Process canceled by the user");
            return false;
        }
        for (Map map : quadrantValues) {
            String plateId = map.get("PlateId").toString();
            String quadrantId = map.get("QuadrantNumber").toString();
            if (StringUtils.isBlank(quadrantId)) {
                clientCallback.displayError(String.format("Quadrant ID value not entered for PlateId '%s'", plateId));
                return false;
            }
            if (!quadrantVal.add(quadrantId)) {
                clientCallback.displayError(String.format("Plate to quadrant value is not unique for PlateId '%s'", plateId));
                return false;
            }
        }
        return true;
    }

    /**
     * Get new plateId for target 384 well plate.
     *
     * @return String
     * @throws InvalidValue
     * @throws com.velox.api.util.ServerException
     */
    private String getNewPlateIdsFromUserFor96To384Transfer() throws InvalidValue, com.velox.api.util.ServerException {
        String plateId384 = clientCallback.showInputDialog("Enter the Plate ID for new 384 well Plate");
        if (StringUtils.isBlank(plateId384)) {
            throw new InvalidValue("Invalid value: " + plateId384);
        }
        return plateId384;
    }

    /**
     * Add plate dimensions tag value to appropriate task option on the active task.
     *
     * @param plateSize
     * @throws RemoteException
     */
    private void addPlateDimensionsToTaskOptions(int plateSize) throws RemoteException {
        String numRows = Integer.toString(getNumberOfPlateRows(plateSize));
        String numCols = Integer.toString(getNumberOfPlateColumns(plateSize));
        String taskOptionValue = String.format("ROWS(%s), COLUMNS(%s)", numRows, numCols);
        activeTask.getTask().getTaskOptions().put("ALIQUOT MAKER PLATE SIZE", taskOptionValue);
    }

    /**
     * Assign well positions to samples when transferring from 96 well to 384 well plates.
     *
     * @param attachedSamples
     * @param destination384PlateId
     * @param quadrantValues
     * @param targetPlateIdFieldName
     * @param targetWellIdFieldName
     * @param newPlateProtocolRecordName
     * @throws NotFound
     * @throws RemoteException
     * @throws com.velox.api.util.ServerException
     */
    private void assignPositionsMapFor96To384WellPlate(List<DataRecord> attachedSamples, String destination384PlateId, List<Map<String, Object>> quadrantValues, String targetPlateIdFieldName, String targetWellIdFieldName, List<DataRecord> newPlateProtocolRecordName) throws NotFound, RemoteException, com.velox.api.util.ServerException {
        List<Map<String, Object>> newProtocolValues = new ArrayList<>();
        for (Map quadrantMap : quadrantValues) {
            String plateId = quadrantMap.get("PlateId").toString();
            String quadrantId = quadrantMap.get("QuadrantNumber").toString();
            for (DataRecord sample : attachedSamples) {
                String sampleId = sample.getStringVal("SampleId", user);
                String samplePlate = sample.getStringVal("RelatedRecord23", user);
                String sampleWell = sample.getStringVal("RowPosition", user).replaceAll("\\s+", "") +
                        sample.getStringVal("ColPosition", user).replaceAll("\\s+", "");
                if (samplePlate.equals(plateId)) {
                    Map<String, Object> newValues = new HashMap<>();
                    newValues.put("SampleId", sampleId);
                    newValues.put(targetPlateIdFieldName, destination384PlateId);
                    newValues.put(targetWellIdFieldName, to384(Integer.parseInt(quadrantId), sampleWell));
                    newProtocolValues.add(newValues);
                }
            }
        }
        addPlateDimensionsToTaskOptions(384);
        dataRecordManager.setFieldsForRecords(newPlateProtocolRecordName, newProtocolValues, user);
    }

    /**
     * Assign well positions to samples when transferring from 384 well to 384 well plates.
     *
     * @param attachedSamples
     * @param destination384PlateId
     * @param targetPlateIdFieldName
     * @param targetWellIdFieldName
     * @param newPlateProtocolRecordName
     * @throws NotFound
     * @throws RemoteException
     * @throws com.velox.api.util.ServerException
     */
    private void assignPositionsMapFor384To384WellPlate(List<DataRecord> attachedSamples, String destination384PlateId, String targetPlateIdFieldName, String targetWellIdFieldName, List<DataRecord> newPlateProtocolRecordName) throws NotFound, RemoteException, com.velox.api.util.ServerException {
        List<Map<String, Object>> newProtocolValues = new ArrayList<>();
        for (DataRecord sample : attachedSamples) {
            String sampleId = sample.getStringVal("SampleId", user);
            String wellId = sample.getStringVal("RowPosition", user).replaceAll("\\s+", "") +
                    sample.getStringVal("ColPosition", user).replaceAll("\\s+", "");
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("SampleId", sampleId);
            newValues.put(targetPlateIdFieldName, destination384PlateId);
            newValues.put(targetWellIdFieldName, wellId);
            newProtocolValues.add(newValues);
        }
        addPlateDimensionsToTaskOptions(384);
        dataRecordManager.setFieldsForRecords(newPlateProtocolRecordName, newProtocolValues, user);
    }

    /**
     * Get the destination Well ID for a sample when transferred from 96well to 384 well plate.
     * This requires the quadrant number and source well ID of the sample being transferred.
     *
     * @param plateNumber
     * @param rowAndColumn
     * @return String
     */
    private String to384(int plateNumber, String rowAndColumn) {
        char row = rowAndColumn.substring(0, 1).toUpperCase().charAt(0);
        Integer rowOffSet = (row - 64);
        rowOffSet = rowOffSet * 2;
        if (plateNumber == 1 || plateNumber == 2)
            rowOffSet--;
        char resultRow = (char) (64 + rowOffSet);
        Integer column = Integer.parseInt(rowAndColumn.substring(1, rowAndColumn.length()));
        Integer toColumn = column * 2;
        if (plateNumber == 1 || plateNumber == 3)
            toColumn--;
        return resultRow + toColumn.toString();
    }

    /**
     * On the attached DataType, set the {IsNewControl = true} for the control samples.
     *
     * @param attachedDataType
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     */
    private void setIsControlTrueForNewControls(List<DataRecord> attachedDataType) throws NotFound, RemoteException, IoError, InvalidValue {
        for (DataRecord r : attachedDataType) {
            String sampleId = r.getStringVal("SampleId", user);
            String otherSampleId = r.getStringVal("OtherSampleId", user);
            if (sampleId.toLowerCase().contains("POOLEDNORMAL")) {
                r.setDataField("IsNewControl", "true", user);
            }
        }
    }
}