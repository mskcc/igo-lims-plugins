package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.datatype.fielddefinition.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.api.workflow.ActiveWorkflow;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

/**
 * Sequencing team QC the library/pool samples before sequencing. Team checks a lot of QC parameters manually to check if samples
 * qualify for Sequencing or not. Manual process can be time consuming, so this plugin is designed to automated it. User
 * can upload the Tapestation and Bioanalyzer CSV files to automatically annotate the samples based on their QC results.
 * This plugin will be used in Library/Pool Quality Control workflow.
 *
 * @author sharmaa1
 */

public class LibraryQcResultsAnnotator extends DefaultGenericPlugin {
    private final List<String> BIOA_IDENTIFIERS = Arrays.asList("Data File Path", "Date Created", "Date Last Modified",
            "Version Created", "Assay Name", "Assay Path", "Assay Title", "Assay Version", "Number of Samples Run",
            "Sample Name");
    private final List<String> EXPECTED_BIOA_HEADER_VALS = Arrays.asList("Size [bp]", "Conc. [pg/�l]", "Molarity [pmol/l]",
            "Observations", "Area", "Aligned Migration Time [s]", "Peak Height", "Peak Width", "% of Total", "Time corrected area");
    private final String BIOA_HEADER_IDENTIFIER = "Size [bp]";
    private final List<String> QC_TYPES = Arrays.asList("BioAnalyzer", "Tapestation");
    private final String TASK_INPUT_DATATYPE = "QcReportLibrary";
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private List<String> EXPECTED_TAPESTATION_HEADER_VALS = Arrays.asList("FileName", "WellId", "Sample Description", "From [bp]",
            "To [bp]", "Average Size [bp]", "Conc. [ng/�l]", "Region Molarity [nmol/l]", "% of Total");

    public LibraryQcResultsAnnotator() {
        setTaskToolbar(true);
        setLine1Text("Add QC");
        setLine2Text(" Recommendations");
        setDescription("Upload BioAnalyzer and/or Tapestation results to mark IGO Recommendations using IGO defined rules.");
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("ANNOTATE SAMPLE QC RECOMMENDATIONS") && activeTask.getInputDataTypeName().equalsIgnoreCase(TASK_INPUT_DATATYPE);
    }

    @Override
    public boolean onTaskToolbar(ActiveWorkflow activeWorkflow, ActiveTask activeTask) {
        try {
            return activeTask.getTask().getTaskOptions().containsKey("ANNOTATE SAMPLE QC RECOMMENDATIONS") && activeTask.getInputDataTypeName().equalsIgnoreCase(TASK_INPUT_DATATYPE);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> samples = activeTask.getAttachedDataRecords(SampleModel.DATA_TYPE_NAME, user);
            if (samples.isEmpty()) {
                logError(String.format("No samples found attached to the task '%s'", activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            List<String> files = clientCallback.showMultiFileDialog("Upload QC File(s)s", "Please upload QC files. Please make sure that the file cells are formatted as Text.");
            if (files == null || files.isEmpty()) {
                clientCallback.displayInfo("Cancelled QC annotation.");
                logError("Client cancelled file upload prompt/did not upload any files.");
                return new PluginResult(true);
            }
            List<SampleQcResult> bioaResults = new ArrayList<>();
            List<SampleQcResult> tapeStationResults = new ArrayList<>();
            for (String file : files) {
                logInfo(String.format("Reading Quality control data from file %s", file));
                if (!isValidFileType(file)) {
                    return new PluginResult(true);
                }
                List<String> fileData = utils.readDataFromCsvFile(clientCallback.readBytes(file));
                boolean isBioanalyzerFile = utils.isBioanalyzerFile(fileData, BIOA_IDENTIFIERS, clientCallback, pluginLogger);
                if (isBioanalyzerFile && isValidBioanalyzerData(fileData, file)) {
                    List<SampleQcResult> bioaData = new ArrayList<>(getBioAnalyzerData(fileData, file, samples));
                    bioaResults.addAll(bioaData);
                } else if (isValidTapestationData(fileData, file)) {
                    List<SampleQcResult> tapestationData = new ArrayList<>(getTapeStationData(fileData, file, samples));
                    tapeStationResults.addAll(tapestationData);
                } else {
                    String errMsg = String.format("Cannot identify file %s as Bioanalyzer or Tapestation data. Please check the files again.", file);
                    clientCallback.displayError(errMsg);
                    logError(errMsg);
                }
            }
            List<SampleQcResult> qcResults = new ArrayList<>(checkAndRemoveDuplicates(bioaResults, tapeStationResults));
            if (qcResults.isEmpty()) {
                clientCallback.displayError("Failed to read QC data from the uploaded QC file(s).");
                logError("Failed to read QC data from the uploaded QC file(s).");
                return new PluginResult(true);
            }
            List<Map<String, Object>> qcResultsToMap = getHashMapValues(qcResults);
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords(activeTask.getInputDataTypeName(), user);
            if (attachedProtocolRecords.isEmpty()) {
                logError(String.format("No %s found attached to the task '%s'. Plugin cannot update QC Results.", activeTask.getInputDataTypeName(), activeTask.getTask().getTaskName()));
                return new PluginResult(false);
            }
            List<Map<String, Object>> userReviewedValues = getUserQcAnnotation(qcResultsToMap);
            setQcReportValues(userReviewedValues, attachedProtocolRecords);
        } catch (Exception e) {
            String errMsg = String.format("%s Error while running QC Results annotation plugin.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }


    /**
     * Method to parse Quality control data from Bioanalyzer file.
     *
     * @param fileData
     * @param fileName
     * @return
     */
    private List<SampleQcResult> getBioAnalyzerData(List<String> fileData, String fileName, List<DataRecord> attachedSamples) throws ServerException, RemoteException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        try {
            Map<String, Integer> headerValueMap = utils.getBioanalyzerFileHeaderMap(fileData, fileName, BIOA_HEADER_IDENTIFIER, pluginLogger);
            BioAnalyzerResultsParser parser = new BioAnalyzerResultsParser(fileData, fileName, headerValueMap, clientCallback, pluginLogger, user);
            qcResults = parser.parseData(attachedSamples);
        } catch (Exception e) {
            String errMsg = String.format("%s Error while parsing tapestation data from uploaded file %s .\n%s", ExceptionUtils.getRootCauseMessage(e), fileName, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return qcResults;
    }


    /**
     * Method to parse Quality control data from Tapestation file.
     *
     * @param fileData
     * @param fileName
     * @return
     */
    private List<SampleQcResult> getTapeStationData(List<String> fileData, String fileName, List<DataRecord> attachedSamples) throws ServerException, RemoteException {
        List<SampleQcResult> qcResults = new ArrayList<>();
        try {
            Map<String, Integer> headerValueMap = utils.getCsvHeaderValueMap(fileData);
            TapeStationResultParser parser = new TapeStationResultParser(fileData, fileName, headerValueMap, clientCallback, pluginLogger, user);
            qcResults = parser.parseData(attachedSamples);
        } catch (Exception e) {
            String errMsg = String.format("%s Error while parsing tapestation data from uploaded file %s .\n%s", ExceptionUtils.getRootCauseMessage(e), fileName, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return qcResults;
    }

    /**
     * Method to check if valid fileType.
     *
     * @param file
     * @return
     * @throws ServerException
     */
    private boolean isValidFileType(String file) throws ServerException, RemoteException {
        if (!utils.isCsvFile(file)) {
            String errmsg = String.format("'%s' is not a valid CSV file.", file);
            clientCallback.displayError(errmsg);
            logError(errmsg);
            return false;
        }
        return true;
    }

    /**
     * Method to validate if the file is valid Tapestation file with correct data and headers.
     *
     * @param fileData
     * @return
     * @throws ServerException
     */
    public boolean isValidTapestationData(List<String> fileData, String file) throws ServerException {
        try {
            logInfo(String.format("Header line from file %s %s", fileData.get(0), file));
            if (fileData.isEmpty() || !utils.csvFileContainsRequiredHeaders(fileData, EXPECTED_TAPESTATION_HEADER_VALS)) {
                String errMsg = String.format("Uploaded file %s is missing valid header values. Expected header values are %s or \n%s.", file, EXPECTED_TAPESTATION_HEADER_VALS, EXPECTED_BIOA_HEADER_VALS);
                clientCallback.displayError(errMsg);
                logError(errMsg);
                return false;
            }
            if (!utils.csvFileHasData(fileData)) {
                String errMsg = String.format("Uploaded file %s does not have parsable data rows.", file);
                clientCallback.displayError(errMsg);
                logError(errMsg);
                return false;
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while parsing tapestation data from uploaded file %s .\n%s", file, ExceptionUtils.getStackTrace(e));
            System.out.println(errMsg);
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return false;
        }
        return true;
    }

    /**
     * Method to validate if the file is valid Bioanalyzer file with correct data and headers.
     *
     * @param fileData
     * @return
     * @throws ServerException
     */
    public boolean isValidBioanalyzerData(List<String> fileData, String file) throws ServerException {
        try {
            if (fileData.isEmpty() || !utils.hasValidBioanalyzerHeader(fileData, file, EXPECTED_BIOA_HEADER_VALS, pluginLogger)) {
                String errMsg = String.format("Uploaded file %s is missing valid header values. Expected header values are %s.", file, EXPECTED_BIOA_HEADER_VALS);
                clientCallback.displayError(errMsg);
                logError(errMsg);
                return false;
            }
            if (!utils.csvFileHasData(fileData)) {
                String errMsg = String.format("Uploaded file %s does not have parsable data rows.", file);
                clientCallback.displayError(errMsg);
                logError(errMsg);
                return false;
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while parsing tapestation data from uploaded file %s .\n%s", file, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return false;
        }
        return true;
    }

    /**
     * Method to check if duplicate entries are found in uploaded QC files. If found, prompt user to select QCType values
     * to keep and remove duplicates accordingly. Eg. A user might upload BioA and Tapestation data, and there is Sample
     * 12345_1 in both files. User should decide which results to use for annotation.
     *
     * @param bioaResults
     * @param tapestationResults
     */
    private List<SampleQcResult> checkAndRemoveDuplicates(List<SampleQcResult> bioaResults, List<SampleQcResult> tapestationResults) throws ServerException {
        List<SampleQcResult> dedupedResults = new ArrayList<>();
        List<SampleQcResult> tapeToEliminate = new ArrayList<>();
        try {
            if (bioaResults.size() == 0) {
                return tapestationResults;
            }
            if (tapestationResults.size() == 0) {
                return bioaResults;
            }
            for (SampleQcResult bioa : bioaResults) {
                String bioId = bioa.getSampleDescription();
                boolean matchFound = false;
                for (SampleQcResult tape : tapestationResults) {
                    String tapeId = tape.getSampleDescription();
                    if (bioId.equalsIgnoreCase(tapeId)) {
                        matchFound = true;
                        String message = String.format("Found Multiple QC Type values for sample %s. Please select the QC Type values to use for annonation.", bioId);
                        logInfo(message);
                        List qcTypeSelected = clientCallback.showListDialog(message, QC_TYPES, false, user);
                        logInfo("qc type selected: " + qcTypeSelected.toString());
                        if (qcTypeSelected.size() == 0) {
                            String msg = String.format("User did not select the value for QC Type values to keep for Sample %s. Cannot filter result without valid user input.", bioId);
                            logError(msg);
                            throw new InvalidValue(msg);
                        }
                        if (qcTypeSelected.get(0).equals(QC_TYPES.get(1))) {
                            dedupedResults.add(tape);
                            logInfo(String.format("Added %s tapestation results to final QC Results: ", bioId ));
                            tapeToEliminate.add(tape);
                        }
                        if (qcTypeSelected.get(0).equals(QC_TYPES.get(0))) {
                            dedupedResults.add(bioa);
                            tapeToEliminate.add(tape);
                            logInfo(String.format("Added %s BioA results to final QC Results: ", bioId ));
                        }
                    }
                }
                if (!matchFound) {
                    dedupedResults.add(bioa);
                    logInfo(String.format("No duplicate values found for Sample %s. Added to final QC Results: ", bioId ));
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("%s error occured while checking for duplicate values in QC data.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            logError(errMsg);
        }
        if (tapestationResults.size() > tapeToEliminate.size()) {
            for (SampleQcResult tr : tapestationResults) {
                String trId = tr.getSampleDescription();
                boolean isCommon = false;
                for (SampleQcResult se : tapeToEliminate) {
                    String seId = se.getSampleDescription();
                    if (trId.equalsIgnoreCase(seId)) {
                        isCommon = true;
                    }
                }
                if (!isCommon) {
                    dedupedResults.add(tr);
                }
            }
            logInfo("tapestation results after eliminating selected: " + dedupedResults.toString());
        }
        return dedupedResults;
    }

    /**
     * Method to create a TemporaryDataType and layout to display as pop up table for the user to enter Quality Assurance Measurement values.
     *
     * @param qcResults
     * @return List<Map < String, Object>>
     */
    private List<Map<String, Object>> getUserQcAnnotation(List<Map<String, Object>> qcResults) {
        List<Map<String, Object>> userInputData = new ArrayList<>();
        try {
            String formName = "QC Recommendations info";
            TemporaryDataType tempDataType = new TemporaryDataType("QCRecommendations", "QC Recommendations");
            List<VeloxFieldDefinition<?>> fieldDefList = new ArrayList<VeloxFieldDefinition<?>>();
            VeloxStringFieldDefinition sampleId = VeloxFieldDefinition.stringFieldBuilder().displayName("IGO ID").dataFieldName("SampleId").visible(true).editable(false).maxLength(100000000).numLines(1).sortDirection(SortDirection.Ascending).build();
            VeloxDoubleFieldDefinition quantity = VeloxFieldDefinition.doubleFieldBuilder().displayName("Total Mass").dataFieldName("Quantity").visible(true).editable(false).maxValue(100000000).minValue(0.0).precision((short) 3).build();
            VeloxDoubleFieldDefinition adapterPercentage = VeloxFieldDefinition.doubleFieldBuilder().displayName("Adapters (%)").dataFieldName("AdapterPercentage").visible(true).editable(false).maxValue(100000000).minValue(0.0).precision((short) 3).build();
            VeloxDoubleFieldDefinition fragmentsUpto1kb = VeloxFieldDefinition.doubleFieldBuilder().displayName("Fragments Upto 1 kb (%)").dataFieldName("PercentUpto1kb").visible(true).editable(false).maxValue(100000000).minValue(0.0).precision((short) 3).build();
            VeloxDoubleFieldDefinition fragmentsLargerThan1kb = VeloxFieldDefinition.doubleFieldBuilder().displayName("Fragments Larger Than 1kb (%)").dataFieldName("PercentGreaterThan1kb").visible(true).editable(false).maxValue(100000000).minValue(0.0).precision((short) 3).build();
            VeloxBooleanFieldDefinition isUserLibrary = VeloxFieldDefinition.booleanFieldBuilder().displayName("Is User Library").dataFieldName("IsUserLibrary").visible(true).editable(false).build();
            VeloxPickListFieldDefinition igoRecommendation = VeloxFieldDefinition.pickListFieldBuilder().displayName("IGO Recommendation").dataFieldName("IgoRecommendation").visible(true).pickListName("QC Statuses").build();
            fieldDefList.add(sampleId);
            fieldDefList.add(quantity);
            fieldDefList.add(adapterPercentage);
            fieldDefList.add(fragmentsUpto1kb);
            fieldDefList.add(fragmentsLargerThan1kb);
            fieldDefList.add(isUserLibrary);
            fieldDefList.add(igoRecommendation);
            tempDataType.setVeloxFieldDefinitionList(fieldDefList);
            List<Map<String, Object>> defaultValues = new ArrayList<>();
            logInfo("Creating table dialog");
            tempDataType = utils.setTempDataTypeLayout(tempDataType, fieldDefList, formName, pluginLogger);
            for (Map<String, Object> qcResult : qcResults) {
                Map<String, Object> values = new HashMap<>();
                values.put("SampleId", qcResult.get("SampleId"));
                values.put("Quantity", qcResult.get("Quantity"));
                values.put("AdapterPercentage", qcResult.get("AdapterPercentage"));
                values.put("PercentUpto1kb", qcResult.get("PercentUpto1kb"));
                values.put("PercentGreaterThan1kb", qcResult.get("PercentGreaterThan1kb"));
                values.put("IsUserLibrary", qcResult.get("IsUserLibrary"));
                values.put("IgoRecommendation", qcResult.get("IgoRecommendation"));
                defaultValues.add(values);
            }
            userInputData = clientCallback.showTableEntryDialog("IGO QC Recommendations", "Please review IGO QC Recommendations", tempDataType, defaultValues);
            if (userInputData == null){
                String errMsg = "User canceled the QC recommendation review dialog. Qc Recommendation will not update.";
                logError(errMsg);
                clientCallback.displayError(errMsg);
            }
        } catch (ServerException se) {
            logError(String.format("ServerException while creating popup table prompt to show 'IGO QC Recommendation' and get input from user:\n%s", ExceptionUtils.getStackTrace(se)));
        }
        return userInputData;
    }

    /**
     * Method to get HashMap values from QcResults.
     *
     * @param qcResults
     * @return
     * @throws ServerException
     */
    private List<Map<String, Object>> getHashMapValues(List<SampleQcResult> qcResults) throws ServerException {
        List<Map<String, Object>> hashmapVals = new ArrayList<>();
        try {
            for (SampleQcResult result : qcResults) {
                hashmapVals.add(result.getHashMap());
            }
        } catch (Exception e) {
            String errMsg = String.format("%s error while creating HashMap from qcResults.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            logError(errMsg);
            clientCallback.displayError(errMsg);
            return hashmapVals;
        }
        return hashmapVals;
    }


    /**
     * Method to finally set the IGO QC Recommendations on attached protocol records for each sample.
     *
     * @param userReviewedValues
     * @param attachedProtocolRecords
     * @throws ServerException
     */
    private void setQcReportValues(List<Map<String, Object>> userReviewedValues, List<DataRecord> attachedProtocolRecords) throws ServerException {
        try {
            if(userReviewedValues!=null) {
                for (DataRecord rec : attachedProtocolRecords) {
                    Object recSampleId = rec.getValue(SampleModel.SAMPLE_ID, user);
                    boolean sampleMappingFound = false;
                    for (Map<String, Object> val : userReviewedValues) {
                        Object sampleId = val.get("SampleId");
                        if (sampleId != null && recSampleId != null && sampleId.toString().equalsIgnoreCase(recSampleId.toString())) {
                            rec.setDataField("IgoQcRecommendation", val.get("IgoRecommendation"), user);
                            dataRecordManager.storeDataFieldChanges(null, user);
                            sampleMappingFound = true;
                        }
                    }
                    if (!sampleMappingFound) {
                        clientCallback.displayWarning(String.format("Cannot find mapping for Sample %s in QC data for 'IGO QC Recommendation' annotations.", recSampleId));
                    }
                }
            }
        } catch (RemoteException e) {
            String errMsg = String.format("RemoteException -> while saving IGO QC Recommendation values.\n%s", ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        } catch (NotFound notFound) {
            String errMsg = String.format("NotFound -> while saving IGO QC Recommendation values.\n%s", ExceptionUtils.getStackTrace(notFound));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        } catch (InvalidValue invalidValue) {
            String errMsg = String.format("InvalidValue -> while saving IGO QC Recommendation values.\n%s", ExceptionUtils.getStackTrace(invalidValue));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        } catch (IoError ioError) {
            String errMsg = String.format("IoError -> while saving IGO QC Recommendation values.\n%s", ExceptionUtils.getStackTrace(ioError));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
    }
}
