package com.velox.sloan.cmo.workflows.kapalibrary;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;

/**
 * This is the plugin class is designed to update the values for 'AutoIndexAssignmentConfig' records based on the 'IndexBarcode' DataRecord values manually assigned by the Users. This will help to track the
 * Volumes for 'AutoIndexAssignmentConfig' DataRecords.
 * 'Index Barcode and Adapter' terms are used interchangeably and have the same meaning.
 * 'AutoIndexAssignmentConfig' is the DataType which holds the Index Barcode metadata that is used for Auto Index Assignment to the samples.
 *
 * @author sharmaa1
 */
public class ManualIndexAssignmentHandler extends DefaultGenericPlugin {
    private final List<String> RECIPES_TO_USE_SPECIAL_ADAPTERS = Arrays.asList("DNA_CRISPR", "DNA_Amplicon", "DNA_SingleCellCNV", "User_SingleCellCNV");
    private final String INDEX_ASSIGNMENT_CONFIG_DATATYPE = "AutoIndexAssignmentConfig";
    AutoIndexAssignmentHelper autohelper;
    private boolean isTCRseq = false;

    public ManualIndexAssignmentHandler() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }

    @Override
    protected boolean shouldRun() throws Throwable {
        return activeTask.getTask().getTaskOptions().containsKey("UPDATE INDEX VOLUMES POST MANUAL INDEX ASSIGNMENT") && !activeTask.getTask().getTaskOptions().containsKey("_UPDATE_INDEX_VOLUMES_POST_MANUAL_INDEX_ASSIGNMENT");
    }

    public PluginResult run() throws ServerException, RemoteException {
        autohelper = new AutoIndexAssignmentHelper();
        try {
            List<DataRecord> attachedSamplesList = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedIndexBarcodeRecords = new LinkedList<>();
            String recipe = attachedSamplesList.get(0).getStringVal("Recipe", user);
            if(activeWorkflow.getWorkflow().getFullName().toLowerCase().contains("tcrseq") && recipe.toLowerCase().contains("tcr")) {
                isTCRseq = true;
                attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IgoTcrSeqIndexBarcode", user);
            }
            else {
                attachedIndexBarcodeRecords = activeTask.getAttachedDataRecords("IndexBarcode", user);
            }

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
            List<DataRecord> activeIndexAssignmentConfigs = dataRecordManager.queryDataRecords(INDEX_ASSIGNMENT_CONFIG_DATATYPE, "IsActive=1", user);
            if (activeIndexAssignmentConfigs.isEmpty()) {
                clientCallback.displayError("Could not find any active 'AutoIndexAssignmentConfig'");
                logError("Could not find any active 'AutoIndexAssignmentConfig'");
                return new PluginResult(false);
            }
            Set<Object> uniquePlates = new HashSet<>();
            for (DataRecord sample: attachedSamplesList) {
                uniquePlates.add(sample.getParentsOfType("Plate", user));
            }
            for (Object plate: uniquePlates) {
                Integer plateSize = getPlateSize(attachedSamplesList);
                String sampleType = attachedSamplesList.get(0).getStringVal("ExemplarSampleType", user);
                String species = attachedSamplesList.get(0).getStringVal("Species", user);
                String aliquotRecipe = attachedSamplesList.get(0).getStringVal("Recipe", user);

                Double minAdapterVolInPlate = autohelper.getMinAdapterVolumeRequired(plateSize, isTCRseq);
                Double maxPlateVolume = autohelper.getMaxVolumeLimit(plateSize);
                List<DataRecord> alphaAttachedIndexBarcodeRecords = new LinkedList<>();
                List<DataRecord> betaAttachedIndexBarcodeRecords = new LinkedList<>();

                // Splitting the attached alpha and beta barcodes
                for (DataRecord attachedBarcode : attachedIndexBarcodeRecords) {
                    if(attachedBarcode.getStringVal("Recipe", user).trim().toLowerCase().contains("alpha")) {
                        alphaAttachedIndexBarcodeRecords.add(attachedBarcode);
                    }
                    else {
                        betaAttachedIndexBarcodeRecords.add(attachedBarcode);
                    }
                }

                if (aliquotRecipe.trim().toLowerCase().contains("alpha")) {
                    boolean setUpdatedIndexAssignmentStatus = setUpdatedIndexAssignmentValues(activeIndexAssignmentConfigs, alphaAttachedIndexBarcodeRecords,
                            minAdapterVolInPlate, maxPlateVolume, plateSize, sampleType, isTCRseq, species, aliquotRecipe);

                    logInfo("Value of setUpdatedIndexAssignmentStatus is: " + setUpdatedIndexAssignmentStatus);
                    checkIndexAssignmentsForDepletedAdapters(activeIndexAssignmentConfigs);
                    if (!setUpdatedIndexAssignmentStatus) {
                        String errMsg = String.format("The manual adapter assignment went wrong, 3 possible scenarios (if TCRseq application 2 & 3 apply):\n" +
                                "1) No Active record found for Index ID. Please double check to avoid discrepancies.\n" +
                                "2) A human adapter been assigned to a mouse sample or vice versa.\n" +
                                "3) An alpha adapter been assigned to a beta aliquot or vice vera");
                        logError(errMsg);
                        return new PluginResult(false);
                    }
                }
                else {
                    boolean setUpdatedIndexAssignmentStatus = setUpdatedIndexAssignmentValues(activeIndexAssignmentConfigs, betaAttachedIndexBarcodeRecords,
                            minAdapterVolInPlate, maxPlateVolume, plateSize, sampleType, isTCRseq, species, aliquotRecipe);

                    logInfo("Value of setUpdatedIndexAssignmentStatus is: " + setUpdatedIndexAssignmentStatus);
                    checkIndexAssignmentsForDepletedAdapters(activeIndexAssignmentConfigs);
                    if (!setUpdatedIndexAssignmentStatus) {
                        String errMsg = String.format("The manual adapter assignment went wrong, 3 possible scenarios (if TCRseq application 2 & 3 apply):\n" +
                                "1) No Active record found for Index ID. Please double check to avoid discrepancies.\n" +
                                "2) A human adapter been assigned to a mouse sample or vice versa.\n" +
                                "3) An alpha adapter been assigned to a beta aliquot or vice vera");
                        logError(errMsg);
                        return new PluginResult(false);
                    }
                }
            }

        } catch (NotFound e) {
            String errMsg = String.format("NotFound Exception while manual index assignment:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (RemoteException e) {
            String errMsg = String.format("Remote Exception while manual index assignment:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (InvalidValue e) {
            String errMsg = String.format("InvalidValue Exception while manual index assignment:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        } catch (IoError e) {
            String errMsg = String.format("IoError Exception while manual index assignment:\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
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
    public Integer getPlateSize(List<DataRecord> samples) throws IoError, RemoteException, NotFound, ServerException {
        DataRecord plate = samples.get(0).getParentsOfType("Plate", user).get(0);
        int plateSizeIndex = Integer.parseInt(plate.getValue("PlateWellCnt", user).toString());
        String plateSize = dataMgmtServer.getPickListManager(user).getPickListConfig("Plate Sizes").getEntryList().get(plateSizeIndex);
        return Integer.parseInt(plateSize.split("-")[0]);
    }

    /**
     * Method to add the metadata for 'IndexBarcode' records, that is not added during Manual Index Assignment process.
     *
     * @param indexAssignmentConfigs
     * @param indexBarcodeRecords
     * @param minVolInAdapterPlate
     * @param maxPlateVolume
     * @param plateSize
     * @param sampleType
     * @param isTCRseq
     * @param species
     * @param aliquotRecipe
     * @throws NotFound
     * @throws RemoteException
     * @throws InvalidValue
     * @throws IoError
     * @throws ServerException
     */
    private boolean setUpdatedIndexAssignmentValues(List<DataRecord> indexAssignmentConfigs, List<DataRecord> indexBarcodeRecords,
                                                 Double minVolInAdapterPlate, Double maxPlateVolume, Integer plateSize,
                                                 String sampleType, boolean isTCRseq, String species, String aliquotRecipe) throws NotFound, RemoteException,
            InvalidValue, IoError, ServerException {
        boolean isCrisprOrAmpliconSeq = RECIPES_TO_USE_SPECIAL_ADAPTERS.contains(aliquotRecipe);
        for (DataRecord indexBarcodeRec : indexBarcodeRecords) {
            boolean found = false;
            for (DataRecord indexConfig : indexAssignmentConfigs) {
                if (indexBarcodeRec.getStringVal("IndexId", user).equals(indexConfig.getStringVal("IndexId", user))
                        && indexBarcodeRec.getStringVal("IndexTag", user).equals(indexConfig.getStringVal("IndexTag", user))) {
                    found = true;
                    logInfo("found is now true");
                    Object inputAmount = indexBarcodeRec.getStringVal("InitialInput", user);
                    Double dnaInputAmount = inputAmount != null ? Double.parseDouble(inputAmount.toString()) : 50.00;
                    String wellPosition = indexConfig.getStringVal("WellId", user);
                    String adapterSourceRow = autohelper.getAdapterRowPosition(wellPosition);
                    String adapterSourceCol = autohelper.getAdapterColPosition(wellPosition);
                    Double adapterStartConc = indexConfig.getDoubleVal("AdapterConcentration", user);
                    Double targetAdapterConc = autohelper.getCalculatedTargetAdapterConcentration(dnaInputAmount, plateSize, sampleType);
                    Double adapterVolume = autohelper.getAdapterInputVolume(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, sampleType, isTCRseq, isCrisprOrAmpliconSeq);
                    Double waterVolume = autohelper.getVolumeOfWater(adapterStartConc, minVolInAdapterPlate, targetAdapterConc, maxPlateVolume, sampleType, isCrisprOrAmpliconSeq);
                    Double actualTargetAdapterConc = adapterStartConc / ((waterVolume + adapterVolume) / adapterVolume);
                    //Double adapterConcentration = autohelper.getAdapterConcentration(indexConfig, adapterVolume, waterVolume);
                    setUpdatedIndexAssignmentConfigVol(indexConfig, adapterVolume);
                    indexBarcodeRec.setDataField("BarcodePlateID", indexConfig.getStringVal("AdapterPlateId", user), user);
                    indexBarcodeRec.setDataField("IndexRow", adapterSourceRow, user);
                    indexBarcodeRec.setDataField("IndexCol", adapterSourceCol, user);
                    indexBarcodeRec.setDataField("BarcodeVolume", adapterVolume, user);
                    indexBarcodeRec.setDataField("Aliq1WaterVolume", waterVolume, user);
                    indexBarcodeRec.setDataField("IndexBarcodeConcentration", actualTargetAdapterConc, user);

                    if (isTCRseq) {
                        if ((indexConfig.getStringVal("IndexId", user).toLowerCase().startsWith("h") &&
                                species.trim().toLowerCase().equals("mouse")) || (indexConfig.getStringVal("IndexId", user)
                                .toLowerCase().startsWith("m") && species.trim().toLowerCase().equals("human"))) {
                            logInfo("human -> mouse || mouse -> human happened!!");
                            clientCallback.displayError("You've set a human adapter to a mouse sample or a mouse adapter to a human sample.");
                            return false;

                        }
                        if ((indexConfig.getStringVal("IndexId", user).toLowerCase().contains("acj") &&
                                aliquotRecipe.toLowerCase().contains("beta")) || (indexConfig.getStringVal("IndexId", user)
                                .toLowerCase().contains("bcj") && aliquotRecipe.toLowerCase().contains("alpha"))) {
                            logInfo("alpha -> beta || beta -> alpha happened!!");
                            clientCallback.displayError("You've set an alpha adapter to a beta aliquot or a beta adapter to an alpha aliquot.");
                            return false;
                        }

                    }
                }
            }
            if (!found) {
                clientCallback.displayError(String.format("No Active '%s' record found for Index ID '%s'. Please double check to avoid discrepancies in '%s' record volumes.",
                        INDEX_ASSIGNMENT_CONFIG_DATATYPE, indexBarcodeRec.getStringVal("IndexId", user), INDEX_ASSIGNMENT_CONFIG_DATATYPE));
                return false;
            }
            activeTask.getTask().getTaskOptions().put("_UPDATE_INDEX_VOLUMES_POST_MANUAL_INDEX_ASSIGNMENT", "");
        }
        logInfo("Returning true!!!!");
        return true;
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
            if (rec.getDoubleVal("AdapterVolume", user) < 20.00) {
                rec.setDataField("IsDepelted", true, user);
                rec.setDataField("IsActive", false, user);
                clientCallback.displayWarning(String.format("AutoIndexAssignmentConfig with Index ID '%s' on Adapter Plate '%s' has volume less than 20ul. It is now marked as Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
                logInfo(String.format("AutoIndexAssignmentConfig with Index ID '%s' on Adapter Plate '%s' has volume less than 10ul. It is now marked as Inactive and depleted.",
                        rec.getStringVal("IndexId", user), rec.getStringVal("AdapterPlateId", user)));
            }
        }
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
    public void setUpdatedIndexAssignmentConfigVol(DataRecord indexAssignmentConfig, Double adapterVolumeUsed) throws NotFound, RemoteException, IoError, InvalidValue, ServerException {
        Double previousVol = indexAssignmentConfig.getDoubleVal("AdapterVolume", user);
        double newVolume = previousVol - adapterVolumeUsed;
        indexAssignmentConfig.setDataField("AdapterVolume", newVolume, user);

        if (newVolume <= 20) {
            indexAssignmentConfig.setDataField("IsDepelted", true, user);
            indexAssignmentConfig.setDataField("IsActive", false, user);
            clientCallback.displayWarning(String.format("The Volume for adapter '%s'on Adapter Plate with ID '%s' is below 20ul.\nThis adapter will be marked as depleted and will be ignored for future assignments.",
                    indexAssignmentConfig.getStringVal("IndexId", user), indexAssignmentConfig.getStringVal("AdapterPlateId", user)));
        }
    }
}
