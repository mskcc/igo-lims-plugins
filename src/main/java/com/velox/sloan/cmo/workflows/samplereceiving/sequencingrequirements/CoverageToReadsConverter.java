package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Plugin to populate Requested Reads for samples that require Coverage
 * using the 'Recipe, CapturePanel, RunType, TumorOrNormal, Species, Coverage' combinations defined in
 * 'ApplicationReadCoverageRef' table in LIMS. The plugin parses data from Samples and Banked Samples
 * to find values for above mentioned fields. If a match is found, the plugin will update the Sequencing Requirements
 * or Will display an error message.
 *
 * @author sharmaa1
 */
public class CoverageToReadsConverter extends DefaultGenericPlugin {
    IgoLimsPluginUtils util = new IgoLimsPluginUtils();
    private boolean panelSelectionOffered = false;
    private Object runType = null;
    private Object panelName = null;
    private Object recipe = null;

    public CoverageToReadsConverter() {
        this.setTaskEntry(true);
        this.setOrder(PluginOrder.LAST.getOrder());
    }

    public boolean shouldRun() throws RemoteException {
        this.logInfo("Checking run status");
        return this.activeTask.getTask().getTaskOptions().containsKey("UPDATE SEQUENCING REQUIREMENTS FROM REFERENCE TABLE") && !this.activeTask.getTask().getTaskOptions().containsKey("SEQUENCING REQUIREMENTS UPDATED");
    }

    public PluginResult run() {
        try {
            this.logInfo("Running reads converter plugin");
            List<DataRecord> coverageReqRefs = this.dataRecordManager.queryDataRecords("ApplicationReadCoverageRef", "ReferenceOnly != 1", this.user);
            List<DataRecord> attachedSamples = this.activeTask.getAttachedDataRecords("Sample", this.user);
            List<DataRecord> seqRequirements = this.activeTask.getAttachedDataRecords("SeqRequirement", this.user);
            if (coverageReqRefs.isEmpty()) {
                this.clientCallback.displayError("Could not fetch 'ApplicationReadCoverageRef' values.");
                return new PluginResult(false);
            }

            if (attachedSamples.isEmpty()) {
                this.clientCallback.displayError("Samples not attached to this task.");
                return new PluginResult(false);
            }
            recipe = attachedSamples.get(0).getValue(SampleModel.RECIPE, user);
            if (Objects.isNull(recipe) || StringUtils.isBlank(recipe.toString())){
                String msg = "Recipe value missing on the samples";
                clientCallback.displayError(msg);
                logError(msg);
                return new PluginResult(false);
            }

            Object sampleType = attachedSamples.get(0).getValue(SampleModel.EXEMPLAR_SAMPLE_TYPE, user);
            if (Objects.isNull(sampleType) || StringUtils.isBlank(sampleType.toString())){
                String msg = "SampleType value missing on the samples";
                clientCallback.displayError(msg);
                logError(msg);
                return new PluginResult(false);
            }

            // If user submitted pooled libraries, do not run the plugin.
            if (sampleType.toString().toLowerCase().equals("pooled library")){
                logInfo("Skipping coverage to reads conversion for user submitted pools");
                return new PluginResult(true);
            }

            if (seqRequirements.isEmpty()) {
                this.clientCallback.displayError("Sample 'SequencingRequirements' not attached to this task.");
                return new PluginResult(false);
            }

            List<String> coverageRecipes = this.getCoverageRecipes(coverageReqRefs);
            this.logInfo("Coverage Recipes: " + coverageRecipes);
            List<DataRecord> relatedBankedSampleInfo = this.getBankedSamples(attachedSamples);
            this.updateSeqReq(attachedSamples, relatedBankedSampleInfo, coverageRecipes, seqRequirements, coverageReqRefs);
            this.activeTask.getTask().getTaskOptions().put("SEQUENCING REQUIREMENTS UPDATED", "");
        } catch (NotFound | ServerException | IoError | InvalidValue | RemoteException var6) {
            this.logError(String.valueOf(var6.getStackTrace()));
            return new PluginResult(true);
        }
        return new PluginResult(true);
    }


    /**
     * Method to get SampleIds.
     * @param attachedSamples
     * @return
     */
    private List<Object> getSampleIds(List<DataRecord> attachedSamples) {
        return attachedSamples.stream().map((s) -> {
            try {
                return s.getValue("OtherSampleId", this.user);
            } catch (RemoteException | NotFound var3) {
                var3.printStackTrace();
                return null;
            }
        }).collect(Collectors.toList());
    }


    /**
     * Method to get banked samples related to samples attatched to the task.
     * @param attachedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     */
    private List<DataRecord> getBankedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException, IoError {
        Object requestId = ((DataRecord)attachedSamples.get(0)).getValue("RequestId", this.user);
        List<Object> sampleIds = this.getSampleIds(attachedSamples);
        this.logInfo("RequestId: " + requestId);
        String whereClause = String.format("%s IN %s AND %s='%s'", "UserSampleID", this.util.listToSqlInClauseVal(sampleIds), "RequestId", requestId);
        this.logInfo("WHERE CLAUSE: " + whereClause);
        return this.dataRecordManager.queryDataRecords("BankedSample", whereClause, this.user);
    }


    /**
     * Method to get all Recipes that require Coverage.
     * @param coverageReqRefs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getCoverageRecipes(List<DataRecord> coverageReqRefs) throws NotFound, RemoteException {
        HashSet<String> recipes = new HashSet<>();
        Iterator references = coverageReqRefs.iterator();

        while(references.hasNext()) {
            DataRecord r = (DataRecord)references.next();
            Object rRecipe = r.getValue("PlatformApplication", this.user);
            Boolean isRefOnly = r.getBooleanVal("ReferenceOnly", this.user);
            if (!Objects.isNull(rRecipe) && !isRefOnly) {
                recipes.add(rRecipe.toString());
            }
        }
        return new ArrayList(recipes);
    }


    /**
     * Method to check if a Recipe requires Sequencing Coverage.
     * @param coverageRecipes
     * @return
     */
    private boolean isCoverageBasedApplication(List<String> coverageRecipes) {
        return coverageRecipes.contains(recipe);
    }


    /**
     * Method to get the runTypes for a Recipe from the 'ApplicationReadCoverageRef' table in LIMS.
     * @param refs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getRunTypeValues(List<DataRecord> refs) throws NotFound, RemoteException {
        HashSet<String> runTypes = new HashSet<>();
        Iterator references = refs.iterator();
        while(references.hasNext()) {
            DataRecord d = (DataRecord)references.next();
            Object dRecipe = d.getValue("PlatformApplication", this.user);
            Object dRunType = d.getValue("SequencingRunType", this.user);
            if (Objects.equals(recipe, dRecipe) && Objects.nonNull(dRunType)) {
                runTypes.add((String)dRunType);
            }
        }
        return new ArrayList(runTypes);
    }


    /**
     * Method to get RunType
     * @param bankedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Object getRunType(List<DataRecord> bankedSamples, List<DataRecord> refs) throws NotFound, RemoteException, ServerException {
        Object runTypeVal = null;
        Iterator banked = bankedSamples.iterator();
        do {
            if (!banked.hasNext()) {
                this.logInfo("Run type after checking on banked samples: " + runTypeVal);
                if (Objects.isNull(runTypeVal) || StringUtils.isBlank((String)runTypeVal)) {
                    this.logInfo("Prompting user for run type.");
                    List<String> sequencingRunTypes = this.getRunTypeValues(refs);
                    // if only one RunType value for Recipe, return the RunType and skip the user prompt.
                    if (sequencingRunTypes.size()==1){
                        return sequencingRunTypes.get(0);
                    }
                    List runTypes = this.clientCallback.showListDialog("Select Sequencing Run Type", sequencingRunTypes, false, this.user);
                    this.logInfo("user selected run type: " + runTypes.toString());
                    if (runTypes.size() > 0) {
                        return runTypes.get(0);
                    }
                    this.clientCallback.displayError("Run Type is a required field. User did not select a valid Run Type.");
                    throw new NullPointerException("Run Type is a required field. User did not select a valid Run Type.");
                }
                return null;
            }
            DataRecord d = (DataRecord)banked.next();
            runTypeVal = d.getValue("RunType", this.user);
            this.logInfo("Run Type on banked Sample: " + runTypeVal);
        } while(Objects.isNull(runTypeVal) || !StringUtils.isNotBlank((String)runTypeVal));
        this.logInfo("Banked Sample run type: " + runTypeVal);
        return runTypeVal;
    }


    /**
     * Method to get PanelNames from 'ApplicationReadCoverageRef' table in LIMS.
     * @param referenceValues
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getCapturePanelValues(List<DataRecord> referenceValues) throws NotFound, RemoteException {
        HashSet<String> panels = new HashSet();
        Iterator references = referenceValues.iterator();
        while(references.hasNext()) {
            DataRecord d = (DataRecord)references.next();
            Object dRecipe = d.getValue("PlatformApplication", this.user);
            Object dPanel = d.getValue("CapturePanel", this.user);
            if (Objects.equals(recipe, dRecipe) && Objects.nonNull(dPanel)) {
                panels.add((String)dPanel);
            }
        }
        return new ArrayList(panels);
    }


    /**
     * Method to get PanelName value from user
     * @param referenceValues
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     */
    private Object getPanelName(List<DataRecord> referenceValues) throws NotFound, RemoteException, ServerException {
        Iterator references = referenceValues.iterator();
        while(true) {
            Object dRecipe;
            Object dPanel;
            do {
                if (!references.hasNext()) {
                    return "";
                }
                DataRecord d = (DataRecord)references.next();
                dRecipe = d.getValue("PlatformApplication", this.user);
                dPanel = d.getValue("CapturePanel", this.user);
            } while(!Objects.isNull(this.panelName) && !StringUtils.isBlank(this.panelName.toString().trim()));

            if (Objects.nonNull(dPanel) && !StringUtils.isBlank(dPanel.toString()) && Objects.equals(recipe, dRecipe)) {
                List<String> capturePanels = this.getCapturePanelValues(referenceValues);
                // if there is only one capture panel for recipe then return the capture panel and skip user prompt.
                if (capturePanels.size()==1){
                    return capturePanels.get(0);
                }
                String msg = String.format("Recipe '%s' should have a 'CapturePanel' value. Missing 'CapturePanel' value may result in failure to update RequestedReads/RequestedCoverage. Select 'OK' to pick CapturePanel value.", recipe);
                boolean selectPanel = this.clientCallback.showYesNoDialog(String.format("Missing 'Capture Panel' value for Recipe '%s'.", recipe), msg);
                if (selectPanel) {
                    List panelSelected = this.clientCallback.showListDialog("Select CapturePanel/Baitset from the list", capturePanels, false, this.user);
                    if (panelSelected.size() > 0) {
                        return panelSelected.get(0);
                    }
                }
            }
        }
    }


    /**
     * Method to update SequencingRequirements from 'ApplicationReadCoverageRef' values.
     * @param samples
     * @param bankedSamples
     * @param coverageRecipes
     * @param seqRequirements
     * @param coverageReqRefs
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws IoError
     * @throws InvalidValue
     */
    private void updateSeqReq(List<DataRecord> samples, List<DataRecord> bankedSamples, List<String> coverageRecipes, List<DataRecord> seqRequirements, List<DataRecord> coverageReqRefs) throws NotFound, RemoteException, ServerException, IoError, InvalidValue {
        this.logInfo(bankedSamples.toString());
        if (Objects.isNull(this.runType) || this.runType == "") {
            this.runType = this.getRunType(bankedSamples, coverageReqRefs);
        }
        this.logInfo("Run Type after prompt : " + this.runType);
        Iterator sampleIter = samples.iterator();
        while(true) {
            while(true) {
                while(sampleIter.hasNext()) {
                    DataRecord s = (DataRecord)sampleIter.next();
                    Object igoId = s.getValue("SampleId", this.user);
                    Object sampleId = s.getValue("OtherSampleId", this.user);
                    Object altId = s.getValue("AltId", this.user);
                    this.logInfo("Recipe: " + recipe);
                    Object species = s.getValue("Species", this.user);
                    Object tumorOrNormal = s.getValue("TumorOrNormal", this.user);
                    Object reads = null;
                    Object coverage = null;
                    boolean isCoverageRecipe = this.isCoverageBasedApplication(coverageRecipes);
                    this.logInfo("Is coverage Recipe: " + isCoverageRecipe);
                    this.logInfo("Sample ID: " + sampleId);
                    Iterator banked = bankedSamples.iterator();
                    DataRecord d;
                    Object igoIdSr;
                    while(banked.hasNext()) {
                        d = (DataRecord)banked.next();
                        igoIdSr = d.getValue("UserSampleID", this.user);
                        this.logInfo("bSample ID: " + igoIdSr);
                        if (Objects.equals(sampleId, igoIdSr)) {
                            this.logInfo("Matching bSample ID: " + igoIdSr);
                            reads = d.getValue("RequestedReads", this.user);
                            coverage = d.getValue("RequestedCoverage", this.user);
                            if (!Objects.isNull(coverage)) {
                                coverage = coverage.toString().replace("X", "").replace("x", "").trim();
                            }

                            this.logInfo("Coverage: " + coverage);
                            if (Objects.isNull(coverage) && reads != null && reads.toString().endsWith("X")) {
                                coverage = reads.toString().replace("X", "").replace("x", "").trim();
                            }

                            if (Objects.isNull(this.panelName)) {
                                this.panelName = d.getValue("CapturePanel", this.user);
                                this.logInfo("Panel: " + this.panelName);
                            }
                        }
                    }
                    if (isCoverageRecipe) {
                        if (!this.panelSelectionOffered && (Objects.isNull(this.panelName) || StringUtils.isBlank(this.panelName.toString().trim()))) {
                            this.panelName = this.getPanelName(coverageReqRefs);
                            this.panelSelectionOffered = true;
                        }
                        reads = CoverageToReadsUtil.getCoverageReadsFromRefs(recipe, this.panelName, species, this.runType, tumorOrNormal, coverage, coverageReqRefs, this.user, this.pluginLogger);
                        this.logInfo("Reads: " + reads);
                        if (Objects.isNull(reads)) {
                            String errMsg = String.format("Could not find read requirements for Sample %s based on metadata Recipe: %s, Species: %s, Panel: %s, RunType: %s, TumorOrNormal: %s, RequestedCoverage: %s", sampleId, recipe, species, this.panelName, this.runType, tumorOrNormal, coverage);
                            this.clientCallback.displayError(errMsg);
                            this.logError(errMsg);
                        } else {
                            Iterator seqReqs1 = seqRequirements.iterator();

                            while(seqReqs1.hasNext()) {
                                d = (DataRecord)seqReqs1.next();
                                igoIdSr = d.getValue("SampleId", this.user);
                                if (Objects.equals(igoIdSr, igoId)) {
                                    d.setDataField("SequencingRunType", this.runType, this.user);
                                    d.setDataField("RequestedReads", reads, this.user);
                                    d.setDataField("CoverageTarget", coverage, this.user);
                                    d.setDataField("MinimumReads", reads, this.user);
                                    d.setDataField("AltId", altId, this.user);
                                    String msg = String.format("Updating sequencing requirements for Sample %s with valuesRunType: %s, RequestedCoverage: %s, RequestedReads: %s, MinimumReads: %s", sampleId, this.runType, coverage, reads, reads);
                                    this.logInfo(msg);
                                }
                            }
                        }
                    } else {
                        Iterator seqReqs2 = seqRequirements.iterator();
                        while(seqReqs2.hasNext()) {
                            d = (DataRecord)seqReqs2.next();
                            igoIdSr = d.getValue("SampleId", this.user);
                            if (Objects.equals(igoIdSr, igoId)) {
                                d.setDataField("SequencingRunType", this.runType, this.user);
                                d.setDataField("RequestedReads", reads, this.user);
                                d.setDataField("CoverageTarget", coverage, this.user);
                                d.setDataField("MinimumReads", reads, this.user);
                                d.setDataField("AltId", altId, this.user);
                            }
                        }
                    }
                }
                return;
            }
        }
    }
}
