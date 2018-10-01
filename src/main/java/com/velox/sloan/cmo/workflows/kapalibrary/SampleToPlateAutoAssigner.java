package com.velox.sloan.cmo.workflows.kapalibrary;


import com.velox.api.datafielddefinition.DataFieldDefinitions;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.AlphaNumericComparator;
import org.apache.commons.lang3.StringUtils;

import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleToPlateAutoAssigner extends DefaultGenericPlugin {

    private final int MIN_PLATE_ROWS = 8;
    private final int MIN_PLATE_COLUMNS = 12;
    private final List<String> PLATE_SIZES = Arrays.asList("96", "384");

    public SampleToPlateAutoAssigner() {
        setLine1Text("Auto-assign Samples to Plate");
        setDescription("Sorts samples based on IGO ID and assign to plate.");
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder());

    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().keySet().contains("SORT AND ASSIGN SAMPLES TO PLATE");
    }


    /**
     * For the Kapa Library Preparation workflow write the well locations to DNALibraryPrepProtocols
     * sorted based on IGO ID.  (User doesn't need to drag and drop).
     *
     * @return
     * @throws ServerException
     * @throws RemoteException
     */
    @Override
    public PluginResult run() throws RemoteException, NotFound, com.velox.api.util.ServerException {
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
        DataRecord attachedProtocolRecord = activeTask.getAttachedDataRecords(newPlateProtocolRecord, user).get(0);
        String destinationPlateFieldName = getDestinationPlateIdFieldName(taskOptionValue);
        String destinationWellFieldName = getDestinationWellFieldName(taskOptionValue);
        String destinationPlateSize = getPlateSizeFromUser(PLATE_SIZES);
        if (StringUtils.isBlank(destinationPlateSize)){
            logError("Process canceled by user.");
            return new PluginResult(false);
        }

        List<Map<String,Object>> quadrantValues = getPlateIdToQuadrantValues(attachedSampleRecords);

        logInfo("Sample Id's sorted: " + getSortedSampleIds(attachedSampleRecords).toString());


        //logInfo("Quadrant Values: " + quadrantValues.toString());
        return new PluginResult(true);
    }

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

    private boolean hasValidTaskOptionValueForPlugin(String taskOption) throws com.velox.api.util.ServerException {
        if (StringUtils.isEmpty(taskOption) || taskOption.split("\\|").length != 3) {
            clientCallback.displayError("Invalid task option values for 'SORT AND ASSIGN SAMPLES TO PLATE' option." +
                    "\nValid values should be 'protocolRecord | destinationPlateIdFieldName | destinationWellFieldName' as in the attached dataType");
            return false;
        }
        return true;
    }

    private String getNewPlateProtocolRecordName(String taskOptionValue) {
        return taskOptionValue.split("\\|")[0].replaceAll("\\s+", "");
    }

    private String getDestinationPlateIdFieldName(String taskOptionValue) {
        return taskOptionValue.split("\\|")[1].replaceAll("\\s+", "");
    }

    private String getDestinationWellFieldName(String taskOptionValue) {
        return taskOptionValue.split("\\|")[2].replaceAll("\\s+", "");
    }

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

    private List<String> getSortedSampleIds(List<DataRecord> dataRecords) throws NotFound, RemoteException {
        List<String> sampleIds = new ArrayList<>();
        for (DataRecord sample : dataRecords) {
            sampleIds.add(sample.getStringVal("SampleId", user));
        }
        return sampleIds.stream().sorted(new AlphaNumericComparator()).collect(Collectors.toList());
    }

    private String getPlateSizeFromUser(List<String> platesizes) {
        List<String> plateSizes = clientCallback.showListDialog("Please Select the Destination Plate type", platesizes, false, user);
        logInfo("Plate Size by user: "+ Integer.toString(plateSizes.size()));
        if(plateSizes.isEmpty()){
            return "";
        }
        return plateSizes.get(0);
    }

    private List<String> getPlateIdsForAttachedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException {
        List<String> uniquePlateIds = new ArrayList<>();

        for (DataRecord sample : attachedSamples) {
            String plateId = sample.getStringVal("RelatedRecord23", user);
            if (!uniquePlateIds.contains(plateId)) {
                uniquePlateIds.add(plateId);
            }
        }
        return uniquePlateIds;
    }

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

    private List<Map<String, Object>> getPlateIdToQuadrantValues(List<DataRecord> attachedSamples) throws com.velox.api.util.ServerException, RemoteException, NotFound {
        List<String> plateIds = getPlateIdsForAttachedSamples(attachedSamples);
        List<Map<String, Object>> plateToQuadrantValues = getValueMapForPlateQuadrant(plateIds);
        DataFieldDefinitions definitions = dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions("PlateToQuadrantEntries");
        logInfo(definitions.toString());
        List<Map<String,Object>> userInputForQuadrantValues = clientCallback.showTableEntryDialog("Please enter quadrant values for plates", "Please enter the 384 well plate quadrant values " +
                "for each 96 well plate in the cell next to it.", definitions, plateToQuadrantValues);
        return userInputForQuadrantValues;
    }

    private void assignPositionsMapFor96WellPlate(List<String> sortedSampleIds, int plateSize, List<String> plateIds, String targetPlateIdFieldName, String targetWellIdFieldName, String newPlateProtocolRecordName ) throws com.velox.api.util.ServerException, RemoteException {
        List<String> plateWellIds = getPlateWells(plateSize);
        int i = 0;
        List<Map<String, Object>> sampleToPlatePositionValues = new ArrayList<>();
        Map<String, Object> sampleToPlatePositionValue = new HashMap<>();
        for (int j = 0; j < plateIds.size(); j++) {
            int k;
            int l;
            for (k = 0, l = i; k < plateSize && l < sortedSampleIds.size(); k++, i++) {
                sampleToPlatePositionValue.put("SampleId", sortedSampleIds.get(i));
                sampleToPlatePositionValue.put(targetPlateIdFieldName, plateIds.get(j));
                sampleToPlatePositionValue.put(targetWellIdFieldName, plateWellIds.get(k));
                sampleToPlatePositionValues.add(sampleToPlatePositionValue);
            }
            j++;
        }
        dataRecordManager.addDataRecords(newPlateProtocolRecordName, sampleToPlatePositionValues, user);
    }

    public boolean isValidQuadrantValues(List<Map<String,Object>> quadrantValues) throws com.velox.api.util.ServerException {
        Set <String> quadrantVal = new HashSet<>();
        for (Map map: quadrantValues){
            String plateId = map.get("PlateId").toString();
            String quadrantId = map.get("QuadrantNumber").toString();
            if(!quadrantVal.add(quadrantId)){
                clientCallback.displayError(String.format("Plate to quadrant value is not unique for PlateId '%s'", plateId));
                return false;
            }
        }
        return true;
    }

    private void assignPositionsMapFor384WellPlate(List<DataRecord> attachedSamples, String destination384PlateId, List<Map<String,Object>> quadrantValues, String targetPlateIdFieldName, String targetWellIdFieldName, String newPlateProtocolRecordName) throws NotFound, RemoteException, com.velox.api.util.ServerException {
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
        dataRecordManager.addDataRecords(newPlateProtocolRecordName, newProtocolValues, user);
    }

    private String to384(int plateNumber, String rowAndColumn) {
        char row = rowAndColumn.substring(0,1).toUpperCase().charAt(0);
        Integer rowOffSet = (row - 64);
        rowOffSet = rowOffSet * 2;
        if (plateNumber == 1 || plateNumber == 2)
            rowOffSet--;
        char resultRow = (char) (64 + rowOffSet);

        Integer column = Integer.parseInt(rowAndColumn.substring(1,rowAndColumn.length()));
        Integer toColumn = column * 2;
        if (plateNumber == 1 || plateNumber == 3)
            toColumn--;
        return resultRow +  toColumn.toString();
    }

}




