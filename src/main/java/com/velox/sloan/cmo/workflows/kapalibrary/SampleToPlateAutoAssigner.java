package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.datatype.datatypelayout.DataFormComponent;
import com.velox.api.datatype.datatypelayout.DataTypeLayout;
import com.velox.api.datatype.datatypelayout.DataTypeTabDefinition;
import com.velox.api.datatype.fielddefinition.FieldDefinitionPosition;
import com.velox.api.datatype.fielddefinition.VeloxFieldDefinition;
import com.velox.api.datatype.fielddefinition.VeloxIntegerFieldDefinition;
import com.velox.api.datatype.fielddefinition.VeloxStringFieldDefinition;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import org.apache.commons.lang3.StringUtils;
import org.mockito.internal.matchers.Not;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * For the Kapa Library Preparation workflow write the well locations to DNALibraryPrepProtocols
 * sorted based on IGO ID.  (User doesn't need to drag and drop).
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
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
        return activeTask.getStatus() != activeTask.COMPLETE && activeTask.getTask().getTaskOptions().keySet().contains("SORT AND ASSIGN SAMPLES TO PLATE");
    }

    @Override
    public PluginResult run() throws com.velox.api.util.ServerException {
        try {
            List<DataRecord> attachedSampleRecords = activeTask.getAttachedDataRecords("Sample", user);
            String taskOptionValue = activeTask.getTask().getTaskOptions().get("SORT AND ASSIGN SAMPLES TO PLATE");
            if (!hasValidTaskOptionValueForPlugin(taskOptionValue)) {
                return new PluginResult(false);
            }
            String newPlateProtocolRecordName = getNewPlateProtocolRecordName(taskOptionValue);
            List<DataRecord> attachedRecords = activeTask.getAttachedDataRecords(newPlateProtocolRecordName, user);
            if (!hasProtocolRecords(attachedRecords, newPlateProtocolRecordName)) {
                return new PluginResult(false);
            }
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords(newPlateProtocolRecordName, user);
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

        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while sample assignment to plates. CAUSE:\n%s", e));
            logError(e);
            return new PluginResult(false);

        }
        return new PluginResult(true);

    }

    /**
     * To confirm that a valid protocol Datatype is attached to the task.
     *
     * @param attachedRecords
     * @return boolean
     * @throws RemoteException
     * @throws com.velox.api.util.ServerException
     */
    private boolean hasProtocolRecords(List<DataRecord> attachedRecords, String newPlateProtocolRecordName) throws RemoteException, com.velox.api.util.ServerException {
        if (attachedRecords.isEmpty()) {
            clientCallback.displayError(String.format("Cannot find a valid '%s' record attached to this task : %s", newPlateProtocolRecordName, activeTask.getTask().getTaskName()));
            return false;
        }
        return true;
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
    private List<String> getSampleIdsSortedSeparatedByRecipe(List<DataRecord> attachedSamples, List<String> recipes) throws NotFound, RemoteException {
        List<String> sampleIds = new ArrayList<>();
        List<String> controlSampleIds = new ArrayList<>();
        for (String recipe : recipes) {
            List<String> sampleIdsForRecipe = new ArrayList<>();
            for (DataRecord sample : attachedSamples) {
                String sampleId = sample.getStringVal("SampleId", user);
                String sampleRecipe = sample.getStringVal("Recipe", user);
                if (!sampleId.toLowerCase().contains("poolednormal") && sampleRecipe.toLowerCase().equals(recipe.toLowerCase())) {
                    if (!sampleIdsForRecipe.contains(sampleId)) {
                        sampleIdsForRecipe.add(sampleId);
                    }
                }
                if (sampleId.toLowerCase().contains("poolednormal")) {
                    if (!controlSampleIds.contains(sampleId)) {
                        controlSampleIds.add(sampleId);
                    }
                }
            }
            List<String> sortedSampleIdsForRecipe = sampleIdsForRecipe.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
            sampleIds.addAll(sortedSampleIdsForRecipe);
        }
        List<String> sortedControlSampleIds = controlSampleIds.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
        sampleIds.addAll(sortedControlSampleIds);
        return sampleIds;
    }

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
        for (DataRecord sample : attachedSamples) {
            String containerType = sample.getStringVal("ContainerType", user);
            if (containerType.toLowerCase().equals("plate")) {
                String plateName = sample.getParentsOfType("Plate", user).get(0).getStringVal("PlateId", user);
                plateSize = sample.getParentsOfType("Plate", user).get(0).getShortVal("PlateWellCnt", user);
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
            if (plateId != null && !uniquePlateIds.contains(plateId)) {
                uniquePlateIds.add(plateId);
            }
        }
        return uniquePlateIds;
    }

    /**
     * Get the quadrant values that each source plate must be assigned to.
     *
     * @param uniquePlateIds
     * @return List<Map       <       String       ,       Object>>
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
        return (int) Math.ceil((double) sampleSize / (double) plateSize);
    }

    /**
     * Method to create DataForm to show table entry to get user input.
     * @param tempPlate
     * @param fieldDefList
     * @return TemporaryDatatype
     */
    private TemporaryDataType createTempDataForm(TemporaryDataType tempPlate, List<VeloxFieldDefinition<?>> fieldDefList) {
        String formName = "NewPlateForm";
        // Create form
        DataFormComponent form = new DataFormComponent(formName, "New Plate Form");
        form.setCollapsed(false);
        form.setColumn(0);
        form.setColumnSpan(4);
        form.setOrder(0);
        form.setHeight(10);
        // Add fields to the form
        for (int i = 0; i < fieldDefList.size(); i++) {
            VeloxFieldDefinition<?> fieldDef = fieldDefList.get(i);
            FieldDefinitionPosition pos = new FieldDefinitionPosition(fieldDef.getDataFieldName());
            pos.setFormColumn(0);
            pos.setFormColumnSpan(4);
            pos.setOrder(i);
            pos.setFormName(formName);
            form.setFieldDefinitionPosition(pos);
        }
        // Create a tab with the form on it
        DataTypeTabDefinition tabDef = new DataTypeTabDefinition("Tab1", "Tab 1");
        tabDef.setDataTypeLayoutComponent(form);
        tabDef.setTabOrder(0);
        // Create a layout with the tab on it
        DataTypeLayout layout = new DataTypeLayout("Default", "Default", "Default layout for temp fields");
        layout.setDataTypeTabDefinition(tabDef);
        // Add the layout to the TDT
        tempPlate.setDataTypeLayout(layout);
        return tempPlate;
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
        TemporaryDataType tempPlate = new TemporaryDataType("NewPlate", "New Plate");
        List<VeloxFieldDefinition<?>> fieldDefList = new ArrayList<VeloxFieldDefinition<?>>();
        VeloxStringFieldDefinition idField = VeloxFieldDefinition.stringFieldBuilder().displayName("Plate Id").dataFieldName("PlateId").visible(true).editable(true).htmlEditor(false).required(true).htmlEditor(true).isRestricted(false).build();
        VeloxIntegerFieldDefinition plateNumField = VeloxFieldDefinition.integerFieldBuilder().displayName("Plate Number").dataFieldName("PlateNumber").visible(true).editable(false).required(true).isRestricted(false).build();
        fieldDefList.add(idField);
        fieldDefList.add(plateNumField);
        tempPlate.setVeloxFieldDefinitionList(fieldDefList);
        tempPlate = createTempDataForm(tempPlate, fieldDefList);
        int numberOfNewPlates = getNumberOfNewPlatesFor96To96Transfer(attachedSamples.size(), plateSize);
        List<Map<String, Object>> defaultValuesList = new ArrayList<>();
        for (int i = 1; i <= numberOfNewPlates; i++) {
            Map<String, Object> samples = new HashMap<>();
            samples.put("PlateNumber", i);
            samples.put("PlateId", "");
            defaultValuesList.add(samples);
        }

        List<Map<String, Object>> plateIdsFromUser = clientCallback.showTableEntryDialog("Enter the New Plate Ids",
                "Enter new Plate ID's and their order for sample assignment. eg: enter 1, 2 ,3 and so on.", tempPlate, defaultValuesList);

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
    private void assignWellPositionsFor96WellPlate(List<String> sortedSampleIds, int plateSize, List<String> plateIds, String targetPlateIdFieldName, String targetWellIdFieldName, List<DataRecord> newPlateProtocolRecordName) throws com.velox.api.util.ServerException, RemoteException, NotFound {
        List<String> plateWellIds = getPlateWells(plateSize);
        int i = 0;
        int numberOfNewPlates = getNumberOfNewPlatesFor96To96Transfer(sortedSampleIds.size(), plateSize);
        for (int j = 0; j < numberOfNewPlates; j++) {
            int k;
            for (k = 0; k < plateSize && i < sortedSampleIds.size(); k++, i++) {
                for (DataRecord rec : newPlateProtocolRecordName) {
                    String sampleId = sortedSampleIds.get(i);
                    if (rec.getStringVal("SampleId", user).equals(sampleId)) {
                        Map<String, Object> sampleToPlatePositionValue = new HashMap<>();
                        sampleToPlatePositionValue.put(targetPlateIdFieldName, plateIds.get(j));
                        sampleToPlatePositionValue.put(targetWellIdFieldName, plateWellIds.get(k));
                        rec.setFields(sampleToPlatePositionValue, user);
                    }
                }
            }
        }
        addPlateDimensionsToTaskOptions(plateSize);
    }

    /**
     * Get PlateId and corresponding quadrant number from user via table input dialog box.
     *
     * @param attachedSamples
     * @return List<Map       <       String       ,               Object>>
     * @throws com.velox.api.util.ServerException
     * @throws RemoteException
     * @throws NotFound
     */
    private List<Map<String, Object>> getPlateIdToQuadrantValues(List<DataRecord> attachedSamples) throws com.velox.api.util.ServerException, RemoteException, NotFound {
        List<Map<String, Object>> plateToQuadrantValuesFromUser;
        List<String> plateIds = getPlateIdsForAttachedSamples(attachedSamples);
        List<Map<String, Object>> plateToQuadrantValuesForTableDisplay = getValueMapForPlateQuadrant(plateIds);
        TemporaryDataType quadrantValues = new TemporaryDataType("QuadrantValues", "QuadrantValues");
        List<VeloxFieldDefinition<?>> fieldDefList = new ArrayList<VeloxFieldDefinition<?>>();
        VeloxStringFieldDefinition plateIdField = VeloxFieldDefinition.stringFieldBuilder().displayName("Plate Id").dataFieldName("PlateId").visible(true).editable(false).required(true).htmlEditor(true).isRestricted(false).build();
        VeloxStringFieldDefinition quadrantNumField = VeloxFieldDefinition.stringFieldBuilder().displayName("Quadrant Number").dataFieldName("QuadrantNumber").visible(true).editable(true).required(true).isRestricted(false).build();
        fieldDefList.add(plateIdField);
        fieldDefList.add(quadrantNumField);
        quadrantValues.setVeloxFieldDefinitionList(fieldDefList);
        quadrantValues = createTempDataForm(quadrantValues, fieldDefList);
        plateToQuadrantValuesFromUser = clientCallback.showTableEntryDialog("Please enter quadrant values for plates", "Please enter the 384 well plate quadrant values " +
                "for each 96 well plate in the cell next to it.", quadrantValues, plateToQuadrantValuesForTableDisplay);
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
        if (quadrantValues == null || quadrantValues.isEmpty()) {
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
    private void assignPositionsMapFor96To384WellPlate(List<DataRecord> attachedSamples, String destination384PlateId, List<Map<String, Object>> quadrantValues, String targetPlateIdFieldName, String targetWellIdFieldName, List<DataRecord> newPlateProtocolRecordName) throws RemoteException, com.velox.api.util.ServerException, NotFound {
        for (Map quadrantMap : quadrantValues) {
            String plateId = quadrantMap.get("PlateId").toString();
            String quadrantId = quadrantMap.get("QuadrantNumber").toString();
            for (DataRecord sample : attachedSamples) {
                String sampleId = sample.getStringVal("SampleId", user);
                if (sample.getValue("RelatedRecord23", user) == null) {
                    clientCallback.displayError(String.format("Invalid Plate ID '%s' for sample '%s'.\nAll samples must have a Plate ID value for 96 to 384 assignment.",
                            sample.getValue("RelatedRecord23", user), sampleId));
                    throw new NotFound(String.format("Invalid Plate ID '%s' for sample '%s'. All samples must have a Plate ID value for 96 to 384 assignment.",
                            sample.getValue("RelatedRecord23", user), sampleId));
                }
                String samplePlate = sample.getStringVal("RelatedRecord23", user);
                String sampleWell = sample.getStringVal("RowPosition", user).replaceAll("\\s+", "") +
                        sample.getStringVal("ColPosition", user).replaceAll("\\s+", "");
                if (samplePlate.equals(plateId)) {
                    for (DataRecord rec : newPlateProtocolRecordName) {
                        if (rec.getStringVal("SampleId", user).equals(sampleId)) {
                            Map<String, Object> newValues = new HashMap<>();
                            newValues.put(targetPlateIdFieldName, destination384PlateId);
                            newValues.put(targetWellIdFieldName, to384(Integer.parseInt(quadrantId), sampleWell));
                            rec.setFields(newValues, user);
                        }
                    }
                }
            }
        }
        addPlateDimensionsToTaskOptions(384);
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
        for (DataRecord sample : attachedSamples) {
            String sampleId = sample.getStringVal("SampleId", user);
            String wellId = sample.getStringVal("RowPosition", user).replaceAll("\\s+", "") +
                    sample.getStringVal("ColPosition", user).replaceAll("\\s+", "");
            for (DataRecord rec : newPlateProtocolRecordName) {
                if (rec.getStringVal("SampleId", user).equals(sampleId)) {
                    Map<String, Object> newValues = new HashMap<>();
                    newValues.put(targetPlateIdFieldName, destination384PlateId);
                    newValues.put(targetWellIdFieldName, wellId);
                    rec.setFields(newValues, user);
                }
            }
        }
        addPlateDimensionsToTaskOptions(384);
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
}




