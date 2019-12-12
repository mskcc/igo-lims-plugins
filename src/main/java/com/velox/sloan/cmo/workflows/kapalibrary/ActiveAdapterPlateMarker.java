package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This Plugin is designed in an effort to Automate Index Assignment to samples in Kapa Library Preparation workflow. This plugin provides a toolbar button on 'AutoIndexAssignmentConfig' Datatype
 * to activate Index barcode plates stored under 'AutoIndexAssignmentConfig' Datatype to use for Index Assignments.
 * 'Index Barcode and Adapter' terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 *
 * @author sharmaa1
 */
public class ActiveAdapterPlateMarker extends DefaultGenericPlugin {

    private final String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";

    public ActiveAdapterPlateMarker() {
        setTableToolbar(true);
        setLine1Text("Activate Adapter Plate");
        setLine2Text(" for use");
        setDescription("Use this button to mark an Adapter plate/set as active for use for Auto Indes Assignment plugin.");
    }

    @Override
    public boolean onTableToolbar() {
        return dataTypeName.equals(INDEX_ASSIGNMENT_CONFIG_DATATYPE);
    }


    public PluginResult run() throws ServerException {
        try {
            String plateBarcode = clientCallback.showInputDialog("Enter Barcode for Adapter Plate to activate.");
            if (StringUtils.isBlank(plateBarcode)) {
                clientCallback.displayError(String.format("Entered plate barcode value of '%s' is invalid.", plateBarcode));
                logError(String.format("Entered plate barcode value of '%s' is invalid.", plateBarcode));
                return new PluginResult(false);
            }
            List<DataRecord> indexAssignmentConfigs = dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, null, user);
            Integer setIdToActivate = getSetIdToActivate(plateBarcode, indexAssignmentConfigs);
            String indexTypeToActivate = getIndexTypeToActivate(plateBarcode, indexAssignmentConfigs);
            setAdapterSetAsUsedAndDepleted(indexTypeToActivate, setIdToActivate, plateBarcode, indexAssignmentConfigs);
            setAdapterPlateAsActive(plateBarcode, indexAssignmentConfigs);

            if (isActiveAdapterCountCorrect(indexAssignmentConfigs)) {
                dataRecordManager.commitChanges(String.format("Setting '%s' records with Adapter Plate ID '%s' as active", INDEX_ASSIGNMENT_CONFIG_DATATYPE, plateBarcode), false, user);
            }


        } catch (Exception e) {
            clientCallback.displayError(String.format("Error while activating Adapter Plate/Set for use:\n%s", ExceptionUtils.getStackTrace(e)));
            logError(ExceptionUtils.getStackTrace(e));
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to return the Set ID that should be activated
     *
     * @param plateBarcode
     * @param indexAssignmentConfigs
     * @return Integer Set ID
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Integer getSetIdToActivate(String plateBarcode, List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, ServerException {
        for (DataRecord record : indexAssignmentConfigs) {
            if (record.getValue("AdapterPlateId", user) != null && record.getStringVal("AdapterPlateId", user).equals(plateBarcode)) {
                return record.getIntegerVal("SetId", user);
            }
        }
        clientCallback.displayError(String.format("Could not find Adapter Plate Barcode '%s' in the '%s' records.", plateBarcode, INDEX_ASSIGNMENT_CONFIG_DATATYPE));
        logError(String.format("Could not find Adapter Plate Barcode '%s' in the '%s' records.", plateBarcode, INDEX_ASSIGNMENT_CONFIG_DATATYPE));
        return null;
    }

    /**
     * Method to return the type of Index Barcodes to activate.
     *
     * @param plateBarcode
     * @param indexAssignmentConfigs
     * @return
     * @throws ServerException
     * @throws NotFound
     * @throws RemoteException
     */
    private String getIndexTypeToActivate(String plateBarcode, List<DataRecord> indexAssignmentConfigs) throws ServerException, NotFound, RemoteException {
        for (DataRecord record : indexAssignmentConfigs) {
            if (record.getValue("IndexType", user) != null && record.getStringVal("AdapterPlateId", user).equals(plateBarcode)) {
                return record.getStringVal("IndexType", user);
            }
        }
        clientCallback.displayError(String.format("Could not find Adapter Plate Barcode '%s' in the '%s' records.", plateBarcode, INDEX_ASSIGNMENT_CONFIG_DATATYPE));
        logError(String.format("Could not find Adapter Plate Barcode '%s' in the '%s' records.", plateBarcode, INDEX_ASSIGNMENT_CONFIG_DATATYPE));
        return null;
    }

    /**
     * Method to set the Boolean value of 'IsDepelted' to true for datarecords under 'AutoIndexAssignmentConfig' Datatype.
     *
     * @param indexType
     * @param setId
     * @param plateBarcode
     * @param indexAssignmentConfigs
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     */
    private void setAdapterSetAsUsedAndDepleted(String indexType, Integer setId, String plateBarcode, List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, IoError, InvalidValue {
        for (DataRecord record : indexAssignmentConfigs) {
            if (indexType.equals(record.getStringVal("IndexType", user)) && record.getIntegerVal("SetId", user) == setId &&
                    !plateBarcode.equals(record.getStringVal("AdapterPlateId", user))) {
                record.setDataField("IsDepelted", true, user);
                record.setDataField("IsActive", false, user);
                logInfo(String.format("Setting Adapter Plate '%s' as Inactive and Depleted", plateBarcode));
            }
        }
    }

    /**
     * Method to set the Boolean value of 'IsActive' to true for datarecords under 'AutoIndexAssignmentConfig' Datatype.
     *
     * @param barcode
     * @param indexAssignmentConfigs
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     * @throws InvalidValue
     * @throws ServerException
     */
    private void setAdapterPlateAsActive(String barcode, List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        int count = 0;
        for (DataRecord rec : indexAssignmentConfigs) {
            if (barcode.equals(rec.getStringVal("AdapterPlateId", user))) {
                if (rec.getBooleanVal("IsDepelted", user)) {
                    logError(String.format("You are trying to activate a previously used and marked as depleted Adapter Plate '%s'. Used a different plate barcode.", barcode));
                    throw new InvalidValue(String.format("You are trying to activate a previously used and marked as depleted Adapter Plate '%s'. Used a different plate barcode.", barcode));
                }
                if (rec.getBooleanVal("IsActive", user)) {
                    logError(String.format("The Adapter Plate '%s' is already active for use.", barcode));
                    throw new InvalidValue(String.format("The Adapter Plate plate '%s' is already active for use.", barcode));
                }
                rec.setDataField("IsActive", true, user);
                count += 1;
            }
        }
        if (count > 96) {
            logError(String.format("Found '%d' Index records associate with Adapter Plate Barcode '%s'. The number should be <=96 per Adapter Plate. Please double check", count, barcode));
            throw new InvalidValue(String.format("Found '%d' Index records associate with Adapter Plate Barcode '%s'. The number should be <=96 per Adapter Plate. Please double check", count, barcode));
        }
        logInfo(String.format("Setting Adapter Plate with ID '%s' as active.", barcode));
    }

    /**
     * Method to return Unique 'IndexType' values from a given set of AutoIndexAssignmentConfig' DataRecords.
     *
     * @param indexAssignmentConfigs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getUniqueIndexTypes(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException {
        Set<String> uniqueIndexTypes = new HashSet<>();
        for (DataRecord rec : indexAssignmentConfigs) {
            if (rec.getValue("IndexType", user) != null) {
                uniqueIndexTypes.add(rec.getStringVal("IndexType", user));
            }
        }
        return new ArrayList<>(uniqueIndexTypes);
    }

    /**
     * Method to return Unique 'SetId' values from a given set of AutoIndexAssignmentConfig' DataRecords.
     *
     * @param indexType
     * @param indexAssignmentConfigs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<Integer> getUniqueSetIdForIndexType(String indexType, List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException {
        Set<Integer> uniqueSetIds = new HashSet<>();
        for (DataRecord rec : indexAssignmentConfigs) {
            if (rec.getValue("IndexType", user) != null && indexType.equals(rec.getValue("IndexType", user))) {
                uniqueSetIds.add(rec.getIntegerVal("SetId", user));
            }
        }
        return new ArrayList<>(uniqueSetIds);
    }

    /**
     * Method to get the size of adapter set from a given set of 'AutoIndexAssignmentConfig' DataRecords.
     *
     * @param indexType
     * @param setId
     * @param indexAssignmentConfigs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private int getSizeOfAdapterSet(String indexType, int setId, List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException {
        int count = 0;
        for (DataRecord rec : indexAssignmentConfigs) {
            if (indexType.equals(rec.getStringVal("IndexType", user)) && rec.getIntegerVal("SetId", user) == setId && rec.getBooleanVal("IsActive", user)) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * Method to check that the size of any Index Barcode records under 'AutoIndexAssignmentConfig' DataType for a given set is not greater that 96.
     *
     * @param indexAssignmentConfigs
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private boolean isActiveAdapterCountCorrect(List<DataRecord> indexAssignmentConfigs) throws NotFound, RemoteException, ServerException {
        List<String> uniqueIndexTypes = getUniqueIndexTypes(indexAssignmentConfigs);
        for (String ind : uniqueIndexTypes) {
            List<Integer> uniqeSetIds = getUniqueSetIdForIndexType(ind, indexAssignmentConfigs);
            for (Integer setId : uniqeSetIds) {
                int setSize = getSizeOfAdapterSet(ind, setId, indexAssignmentConfigs);
                if (setSize > 96) {
                    clientCallback.displayError(String.format("Size of set '%d' for Index Type '%s' is '%d', which is greater than 96." +
                            " This is incorrect. The valid size of Active Set for any Index Type is between 1 to 96", setId, ind, setSize));
                    logError(String.format("Size of set '%d' for Index Type '%s' is '%d', which is greater than 96." +
                            " This is incorrect. The valid size of Active Set for any Index Type is between 1 to 96", setId, ind, setSize));
                    return false;
                }
            }
        }
        return true;
    }
}
