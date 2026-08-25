package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.plugin.PluginResult;
import com.velox.api.servermanager.PickListConfig;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.recmodels.BankedSampleModel;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Plugin to populate Requested Reads for samples that require Coverage
 * using the 'Recipe, CapturePanel, RunType, TumorOrNormal, Species, Coverage' combinations defined in
 * 'ApplicationReadCoverageRef' table in LIMS. The plugin parses data from Samples and Banked Samples
 * to find values for above mentioned fields. If a match is found, the plugin will update the Sequencing Requirements
 * or Will display an error message.
 *
 * @author sharmaa1, Fahimeh Mirhaj
 */
public class SequencingRequirementsHandler extends DefaultGenericPlugin {
    IgoLimsPluginUtils util = new IgoLimsPluginUtils();
    private Object runType = null;
    private Object panelName = null;
    private Object recipe = null;

    public SequencingRequirementsHandler() {
        this.setTaskEntry(true);
        this.setOrder(PluginOrder.LAST.getOrder());
    }

    public boolean shouldRun() throws RemoteException {
        this.logInfo("Checking run status");
        return this.activeTask.getTask().getTaskOptions().containsKey("UPDATE SEQUENCING REQUIREMENTS FROM REFERENCE TABLE") && !this.activeTask.getTask().getTaskOptions().containsKey("SEQUENCING REQUIREMENTS UPDATED");
    }

    public PluginResult run() {
        try {
            this.logInfo("Running sequencing requirements handler plugin");
            // 'ReferenceOnly IS NULL' is required: a newly added reference row often has no value for
            // ReferenceOnly, and in SQL 'NULL != 1' evaluates to UNKNOWN, which silently drops the row.
            List<DataRecord> coverageReqRefs = this.dataRecordManager.queryDataRecords("ApplicationReadCoverageRef",
                    "ReferenceOnly IS NULL OR ReferenceOnly != 1", this.user);
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
            if (Objects.isNull(recipe) || StringUtils.isBlank(recipe.toString())) {
                String msg = "Recipe value missing on the samples";
                clientCallback.displayError(msg);
                logError(msg);
                return new PluginResult(false);
            }
            Object sampleType = attachedSamples.get(0).getValue(SampleModel.EXEMPLAR_SAMPLE_TYPE, user);
            if (Objects.isNull(sampleType) || StringUtils.isBlank(sampleType.toString())) {
                String msg = "SampleType value missing on the samples";
                clientCallback.displayError(msg);
                logError(msg);
                return new PluginResult(false);
            }

            if (seqRequirements.isEmpty()) {
                this.clientCallback.displayError("Sample 'SequencingRequirements' not attached to this task.");
                return new PluginResult(false);
            }
            PluginLogger logger = this.pluginLogger;
            List<DataRecord> relatedBankedSampleInfo = this.getBankedSamples(attachedSamples);
            this.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, this.user, this.dataMgmtServer, logger);
            this.activeTask.getTask().getTaskOptions().put("SEQUENCING REQUIREMENTS UPDATED", "");
        } catch (NotFound | ServerException | IoError | InvalidValue | RemoteException e) {
            // Previously this logged 'String.valueOf(e.getStackTrace())' (an array identity hash, not a trace)
            // and returned PluginResult(true), so a failed update looked like a successful one.
            String errMsg = String.format("Failed to update sequencing requirements:\n%s", ExceptionUtils.getStackTrace(e));
            this.logError(errMsg);
            try {
                this.clientCallback.displayError(errMsg);
            } catch (ServerException | RemoteException ex) {
                this.logError(ExceptionUtils.getStackTrace(ex));
            }
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to get banked samples related to samples attached to the task.
     *
     * @param attachedSamples
     * @return
     * @throws NotFound
     * @throws RemoteException
     * @throws IoError
     */
    public List<DataRecord> getBankedSamples(List<DataRecord> attachedSamples) throws NotFound, ServerException, RemoteException, IoError {
        List<DataRecord> bankedSamples = new LinkedList<>();
        this.logInfo("attached samples size: " + attachedSamples.size());
        for (int i = 0; i < attachedSamples.size(); i++) {
            Object requestId = ((DataRecord) attachedSamples.get(i)).getValue("RequestId", this.user);
            Object userSampleId = ((DataRecord) attachedSamples.get(i)).getValue("UserSampleID", this.user);
            //this.logInfo("RequestId: " + requestId);
            String whereClause = String.format("%s='%s' AND %s='%s'", "UserSampleID", userSampleId, "RequestId", requestId);
//            this.logInfo("WHERE CLAUSE: " + whereClause);
//            this.logInfo("I am in get banked sample query");
            List<DataRecord> matchingBankedSamples = this.dataRecordManager.queryDataRecords("BankedSample", whereClause, this.user);
            if (matchingBankedSamples.isEmpty()) {
                // Previously an unmatched sample threw IndexOutOfBoundsException here, which is not in the
                // catch list of run() and therefore killed the plugin before anything was updated.
                this.logInfo(String.format("No 'BankedSample' record found for UserSampleID '%s' and RequestId '%s'. " +
                        "Reference table values will be used for this sample.", userSampleId, requestId));
                continue;
            }
            bankedSamples.add(matchingBankedSamples.get(0));
        }
        return bankedSamples;

    }

    /**
     * Method to update SequencingRequirements from 'ApplicationReadCoverageRef' values.
     *
     * @param samples
     * @param bankedSamples
     * @param seqRequirements
     * @param coverageReqRefs
     * @throws NotFound
     * @throws RemoteException
     * @throws ServerException
     * @throws IoError
     * @throws InvalidValue
     */
    public void updateSeqReq(List<DataRecord> samples, List<DataRecord> bankedSamples, List<DataRecord> seqRequirements,
                             List<DataRecord> coverageReqRefs, User user, DataMgmtServer dataMgmtServer, PluginLogger logger) throws NotFound,
            RemoteException, ServerException, IoError, InvalidValue {

        // Fetching the NonSequencingRecipes from its pick list
        PickListConfig nonSeqRecipes = null;
        try {
            nonSeqRecipes = dataMgmtServer.getPickListManager(user).getPickListConfig("NonSequencingRecipes");

        } catch (RemoteException re) {
            re.printStackTrace();
        }
        //******************Create the required mappings from Ref table**************
        Iterator refIter = coverageReqRefs.iterator();
        Map<String, Set<Object>> refRecipeToCoverageMap = new HashMap<String, Set<Object>>();
        Map<String, Object> refRecipeToTranslatedReadsHumanMap = new HashMap<String, Object>();
        Map<String, Set<Object>> recipeToCapturePanelMap = new HashMap<String, Set<Object>>();
        Map<String, Object> recipeToSequencingRunTypeMap = new HashMap<String, Object>();
        // First reference row seen per recipe, used for recipes that carry no Coverage/CapturePanel to match on.
        Map<String, DataRecord> refRecipeToRecord = new HashMap<String, DataRecord>();
        while (refIter.hasNext()) {
            DataRecord ref = (DataRecord) refIter.next();
            Object refRecipeValue = ref.getValue("PlatformApplication", user);
            if (Objects.isNull(refRecipeValue) || StringUtils.isBlank(refRecipeValue.toString())) {
                logger.logInfo("Skipping an 'ApplicationReadCoverageRef' row with a blank PlatformApplication.");
                continue;
            }
            String refRecipe = refRecipeValue.toString();
            Object refCoverage = ref.getValue("Coverage", user);
            Object refCapturePanel = ref.getValue("CapturePanel", user);
            Object refSeqRunType = ref.getValue("SequencingRunType", user);
            Object refHumanTranslatedReadsHuman = ref.getValue("MillionReadsHuman", user);
            Set<Object> coverageSet = null;
            Set<Object> capturePanelSet = null;
            if (!refRecipeToCoverageMap.containsKey(refRecipe)) {
                coverageSet = new HashSet<>();
                if (!Objects.isNull(refCoverage) && !refCoverage.toString().trim().isEmpty()) {
                    coverageSet.add(refCoverage);
                    refRecipeToCoverageMap.put(refRecipe, coverageSet);
                }

            } else {
                if (!Objects.isNull(refCoverage) && !refCoverage.toString().trim().isEmpty()) {
                    refRecipeToCoverageMap.get(refRecipe).add(refCoverage);
                }
            }


            if (!refRecipeToTranslatedReadsHumanMap.containsKey(refRecipe)) {
                refRecipeToTranslatedReadsHumanMap.put(refRecipe, refHumanTranslatedReadsHuman);
            }
            // it should be unique association of recipe to reads in ref table.

            if (!recipeToCapturePanelMap.containsKey(refRecipe)) {
                capturePanelSet = new LinkedHashSet<>();
                if (!Objects.isNull(refCapturePanel) && !refCapturePanel.toString().trim().isEmpty()) {
                    capturePanelSet.add(refCapturePanel);
                    recipeToCapturePanelMap.put(refRecipe, capturePanelSet);
                }

            } else {
                if (!Objects.isNull(refCapturePanel) && !refCapturePanel.toString().trim().isEmpty()) {
                    recipeToCapturePanelMap.get(refRecipe).add(refCapturePanel);
                }
            }
            if (!recipeToSequencingRunTypeMap.containsKey(refRecipe)) {
                recipeToSequencingRunTypeMap.put(refRecipe, refSeqRunType);
            }
            if (!refRecipeToRecord.containsKey(refRecipe)) {
                refRecipeToRecord.put(refRecipe, ref);
            }
        }

        //******************Map of recipe to capture panel(s) from banked sample**************
        Iterator bankedSampleIter = bankedSamples.iterator();
        Map<String, TreeSet<Object>> bankedSampleRecipeToCapturePanelMap = new HashMap<>();
        while(bankedSampleIter.hasNext()) {
            DataRecord bs = (DataRecord) bankedSampleIter.next();
            panelName = bs.getValue("CapturePanel", user);
            recipe = bs.getValue(BankedSampleModel.RECIPE, user);
            if(!bankedSampleRecipeToCapturePanelMap.containsKey(recipe)) {
                TreeSet<Object> capturePanelSet = new TreeSet<>();
                if (!Objects.isNull(panelName) && !panelName.toString().trim().isEmpty()) {
                    capturePanelSet.add(panelName);
                    bankedSampleRecipeToCapturePanelMap.put(recipe.toString(), capturePanelSet);
                }

            } else {
                if (!Objects.isNull(panelName) && !panelName.toString().trim().isEmpty()) {
                    bankedSampleRecipeToCapturePanelMap.get(recipe.toString()).add(panelName);
                }
            }
        }
        //*********************************************************************************


        Iterator sampleIter = samples.iterator();
        Set<String> batchRecipes = new HashSet<>();
        while(sampleIter.hasNext()) {
            DataRecord s = (DataRecord) sampleIter.next();
            recipe = s.getValue(SampleModel.RECIPE, user);
            batchRecipes.add(recipe.toString());
        }
        Map<String, Object> recipeToSelectedCapturePanel = new HashMap<>();
        for(String recipe : batchRecipes) {

            if(bankedSampleRecipeToCapturePanelMap.get(recipe) != null && bankedSampleRecipeToCapturePanelMap.get(recipe)
                    .size() == 1) {
                recipeToSelectedCapturePanel.put(recipe, bankedSampleRecipeToCapturePanelMap.get(recipe).first());
                break;
            }


            if (!Objects.isNull(recipeToCapturePanelMap.get(recipe))) {
                if (recipeToCapturePanelMap.get(recipe).size() > 1) {
                    try {
                        Object[] listOfCapturePanels = recipeToCapturePanelMap.get(recipe).toArray(
                                new String[recipeToCapturePanelMap.get(recipe).size()]);
                        String[] stringListOfCapturePanels = new String[listOfCapturePanels.length];
                        for (int i = 0; i < listOfCapturePanels.length; i++) {
                            stringListOfCapturePanels[i] = listOfCapturePanels[i].toString();
                        }
                        int selectedCapturePanelIndex = clientCallback.showOptionDialog("Selecting Capture Panel",
                                "Please select a capture panel for recipe: " + recipe, stringListOfCapturePanels, 0);
                        recipeToSelectedCapturePanel.put(recipe, (Object) stringListOfCapturePanels[selectedCapturePanelIndex]);
                    } catch (ServerException se) {
                        this.logError(String.valueOf(se.getStackTrace()));
                    }
                }
            }
        }

        sampleIter = samples.iterator();
        while(true) {
            while (true) {
                while (sampleIter.hasNext()) {
                    DataRecord s = (DataRecord) sampleIter.next();
                    recipe = s.getValue(SampleModel.RECIPE, user);
                    if (nonSeqRecipes.getEntryList().contains(recipe.toString())) {
                    }

                    else {
                        logger.logInfo("at sample: " + s.getValue("SampleId", user));
                        // panelName and runType are instance fields; without this reset a sample inherits the
                        // previous sample's panel/run type when its own banked sample does not supply one.
                        this.panelName = null;
                        this.runType = null;
                        Object igoId = s.getValue("SampleId", user);
                        Object sampleId = s.getValue("OtherSampleId", user);
                        Object species = s.getValue("Species", user);
                        Object tumorOrNormal = s.getValue("TumorOrNormal", user);
                        Object reads = null;
                        Object coverage = null;
                        Iterator banked = bankedSamples.iterator();
                        Iterator seqReqs1 = seqRequirements.iterator();
                        DataRecord d = null;
                        DataRecord seqReq;
                        Object igoIdSr;
                        boolean seqReqMatched = false;
                        while (banked.hasNext()) {
                            d = (DataRecord) banked.next();
                            igoIdSr = d.getValue("UserSampleID", user);
                            if (Objects.equals(sampleId, igoIdSr)) {
                                reads = d.getValue("RequestedReads", user);
                                coverage = d.getValue("RequestedCoverage", user);
                                runType = d.getValue("RunType", user);
                                if (!Objects.isNull(coverage)) {
                                    coverage = coverage.toString().replace("X", "").replace("x",
                                            "").trim();
                                }

                                if (Objects.isNull(coverage) && reads != null && reads.toString().endsWith("X")) {
                                    coverage = reads.toString().replace("X", "").replace("x",
                                            "").trim();
                                }

                                if (Objects.isNull(this.panelName)) {
                                    this.panelName = d.getValue("CapturePanel", user);
                                    this.logInfo("Panel: " + this.panelName);
                                }
                            }
                        }


                        while (seqReqs1.hasNext()) {
                            seqReq = (DataRecord) seqReqs1.next();
                            igoIdSr = seqReq.getValue("SampleId", user);
                            if (Objects.equals(igoIdSr, igoId)) {
                                seqReqMatched = true;
                                if (recipe.toString().equals("ImmunoSeq")) {
                                    if (Objects.nonNull(runType) && !runType.toString().trim().isEmpty()) {
                                        seqReq.setDataField("SequencingRunType", runType, user);
                                    } else {
                                        seqReq.setDataField("SequencingRunType",
                                                recipeToSequencingRunTypeMap.get(recipe.toString()), user);
                                    }
                                    continue;
                                }
                                if (!Objects.isNull(runType) && !runType.toString().trim().isEmpty()) {
                                    seqReq.setDataField("SequencingRunType", runType, user);
                                } else {
                                    // Never write a null from a map miss: that clears the field and looks
                                    // identical to 'the plugin did not populate anything'.
                                    Object refRunType = recipeToSequencingRunTypeMap.get(recipe.toString());
                                    if (Objects.nonNull(refRunType) && !refRunType.toString().trim().isEmpty()) {
                                        seqReq.setDataField("SequencingRunType", refRunType, user);
                                    } else {
                                        logger.logInfo(String.format("No SequencingRunType in 'ApplicationReadCoverageRef' " +
                                                "for recipe '%s'; leaving the existing value untouched.", recipe));
                                    }
                                }

                                if (Objects.nonNull(reads) && !reads.toString().trim().isEmpty()) {
                                    Set<Object> refCoveragesForReads = refRecipeToCoverageMap.get(recipe.toString());
                                    boolean useSubmittedReads = (Objects.nonNull(runType) && !runType.toString().trim().isEmpty())
                                            || Objects.isNull(refCoveragesForReads) || refCoveragesForReads.isEmpty();
                                    if (useSubmittedReads) {
                                        Double[] minMax = parseRequestedReads(reads);
                                        if (Objects.isNull(minMax)) {
                                            String errMsg = String.format("Sample %s: could not read a numeric value " +
                                                    "from the submitted Requested Reads '%s'. Please correct it on the " +
                                                    "Banked Sample record.", sampleId, reads);
                                            this.clientCallback.displayError(errMsg);
                                            this.logError(errMsg);
                                            continue;
                                        }
                                        seqReq.setDataField("RequestedReads", minMax[1], user);
                                        if (Objects.nonNull(minMax[0])) {
                                            seqReq.setDataField("MinimumReads", minMax[0], user);
                                        }
                                    }
                                }

                                // ShallowWGS, CRISPR, TCR_AIR: the reference row has no Coverage to match on,
                                // so the row itself is the answer. Copy every populated value from it.
                                else if ((Objects.isNull(refRecipeToCoverageMap.get(recipe.toString())) ||
                                        refRecipeToCoverageMap.get(recipe.toString()).size() == 0) &&
                                        (Objects.isNull(reads) || reads.toString().trim().isEmpty())) {
                                    DataRecord directRef = refRecipeToRecord.get(recipe.toString());
                                    if (Objects.isNull(directRef)) {
                                        String errMsg = String.format("No 'ApplicationReadCoverageRef' row found for " +
                                                        "recipe '%s'. Sequencing requirements for sample %s were left " +
                                                        "unchanged. Check that the reference row exists and that its " +
                                                        "ReferenceOnly flag is not set.", recipe, sampleId);
                                        this.clientCallback.displayError(errMsg);
                                        this.logError(errMsg);
                                        continue;
                                    }
                                    this.applyRefRecordDirectly(seqReq, directRef, species, user, logger);
                                } else if ((Objects.isNull(coverage) || coverage.toString().trim().isEmpty() ||
                                        coverage.toString().trim().equals("")) &&
                                        (Objects.isNull(seqReq.getValue("RequestedReads", user)) ||
                                                seqReq.getValue("RequestedReads", user).toString().trim().isEmpty() ||
                                                seqReq.getValue("RequestedReads", user).toString().equals(""))) {
                                    this.logInfo("Coverage is null..");
                                    if (Objects.isNull(this.panelName) || this.panelName.toString().trim().isEmpty()) {
                                        if (!Objects.isNull(recipeToCapturePanelMap.get(recipe.toString()))) {
                                            if (recipeToCapturePanelMap.get(recipe.toString()).size() > 1) {
                                                this.panelName = recipeToSelectedCapturePanel.get(recipe.toString());
                                            }
                                            else if (recipeToCapturePanelMap.get(recipe.toString()).size() == 1) {
                                                this.panelName = recipeToCapturePanelMap.get(recipe.toString()).toArray()[0];
                                            }
                                        }
                                    }
                                    DataRecord refRecord = CoverageToReadsUtil.getRefRecordFromRecipeAndCapturePanel
                                            (recipe, this.panelName, tumorOrNormal, coverage, coverageReqRefs,
                                                    user, this.pluginLogger);
                                    if (Objects.isNull(refRecord)) {
                                        String errMsg = String.format("Could not find read requirements for Sample %s based " +
                                                        "on metadata Recipe: %s, Species: %s, Panel: %s, RunType: %s, TumorOrNormal: " +
                                                        "%s, RequestedCoverage: %s", sampleId, recipe, species, this.panelName,
                                                this.runType, tumorOrNormal, coverage);
                                        this.clientCallback.displayError(errMsg);
                                        this.logError(errMsg);
                                        continue;
                                    }
                                    else {
                                        if (species.toString().equalsIgnoreCase("Human")) {
                                            seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                    "MillionReadsHuman", user), user);
                                        } else if (species.toString().equalsIgnoreCase("Mouse")) {
                                            if (Objects.nonNull(refRecord.getValue(
                                                    "MillionReadsMouse", user)) && !refRecord.getValue(
                                                    "MillionReadsMouse", user).toString().trim().isEmpty()) {
                                                seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                        "MillionReadsMouse", user), user);
                                            } else {
                                                seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                        "MillionReadsHuman", user), user);
                                            }

                                        }
                                        // Null check has to come first: recipes with no Coverage in the reference
                                        // table have no key in this map at all, so .size() threw NPE here.
                                        Set<Object> refCoverages = refRecipeToCoverageMap.get(recipe.toString());
                                        if (Objects.nonNull(refCoverages) && !refCoverages.isEmpty()) {
                                            seqReq.setDataField("CoverageTarget", refRecord.getValue(
                                                    "Coverage", user), user);
                                        }
                                    }

                                } else {
                                    // requested coverage has a value
                                    if (Objects.nonNull(recipeToCapturePanelMap.get(recipe.toString()))) {
                                        this.logInfo("recipe to capture panel was NOT null!");
                                        if (recipeToCapturePanelMap.get(recipe.toString()).size() > 1) {
                                            this.panelName = recipeToSelectedCapturePanel.get(recipe.toString());
                                          }
                                        else if (recipeToCapturePanelMap.get(recipe.toString()).size() == 1) {
                                            this.panelName = recipeToCapturePanelMap.get(recipe.toString()).toArray()[0];
                                        }
                                    }
                                   
                                    DataRecord refRecord = CoverageToReadsUtil.getRefRecordFromRecipeAndCapturePanel(
                                            recipe, this.panelName, tumorOrNormal, coverage, coverageReqRefs, user, this.pluginLogger);
                                    if (Objects.isNull(refRecord)) {
                                        String errMsg = String.format("Could not find read requirements for Sample %s based " +
                                                        "on metadata Recipe: %s, Species: %s, Panel: %s, RunType: %s, TumorOrNormal: " +
                                                        "%s, RequestedCoverage: %s", sampleId, recipe, species, this.panelName,
                                                this.runType, tumorOrNormal, coverage);
                                        this.clientCallback.displayError(errMsg);
                                        this.logError(errMsg);
                                        continue;
                                    }
                                    else {
                                        if (species.toString().equalsIgnoreCase("Human")) {
                                            seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                    "MillionReadsHuman", user), user);
                                        } else if (species.toString().equalsIgnoreCase("Mouse")) {
                                            if (Objects.nonNull(refRecord.getValue(
                                                    "MillionReadsMouse", user)) && !refRecord.getValue(
                                                    "MillionReadsMouse", user).toString().trim().isEmpty()) {
                                                seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                        "MillionReadsMouse", user), user);
                                            } else {
                                                seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                        "MillionReadsHuman", user), user);
                                            }

                                        }
                                        Set<Object> refCoveragesForRecipe = refRecipeToCoverageMap.get(recipe.toString());
                                        if (Objects.nonNull(refCoveragesForRecipe) && !refCoveragesForRecipe.isEmpty()) {
                                            seqReq.setDataField("CoverageTarget", refRecord.getValue(
                                                    "Coverage", user), user);
                                        }
                                    }
                                }
                            }
                        }
                        if (!seqReqMatched) {
                            String errMsg = String.format("No attached 'SeqRequirement' record matches SampleId '%s' " +
                                    "(sample %s), so no sequencing requirements were written for it.", igoId, sampleId);
                            this.clientCallback.displayError(errMsg);
                            this.logError(errMsg);
                        }
                    }
                }
                return;
            }
        }
    }

    /**
     * Parses the free-text 'RequestedReads' a submitter enters on a Banked Sample into millions of reads.
     * Handles '200', '>200M', '>=200 M', '1,000', '200 million', '40-50' and '40-50M'.
     * Returns {minimum, requested}; minimum is null unless the value is a range. Returns null if unparseable.
     */
    static Double[] parseRequestedReads(Object reads) {
        if (Objects.isNull(reads)) {
            return null;
        }
        String value = reads.toString().toUpperCase()
                .replace(",", "")
                .replace("MILLION", "")
                .replace("READS", "")
                .replaceAll("^[<>~=≤≥]+", "")
                .trim()
                .split("\\s+")[0]
                .replaceAll("M+$", "");
        String[] parts = value.split("-");
        try {
            if (parts.length == 2) {
                return new Double[]{Double.valueOf(parts[0]), Double.valueOf(parts[1])};
            }
            return new Double[]{null, Double.valueOf(parts[0])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Copies every populated value of an 'ApplicationReadCoverageRef' row onto a SequencingRequirements record.
     * Used for recipes whose reference row carries no Coverage and no CapturePanel to match on (e.g. TCR_AIR,
     * ShallowWGS, CRISPR): there is nothing to disambiguate, so the row itself is the answer.
     * Blank reference values are never written, so an existing value is preserved rather than cleared.
     *
     * @param seqReq    SequencingRequirements record to update
     * @param refRecord reference row for this recipe
     * @param species   Species of the sample, used to pick the human or mouse read column
     */
    private void applyRefRecordDirectly(DataRecord seqReq, DataRecord refRecord, Object species, User user,
                                        PluginLogger logger) throws NotFound, RemoteException, ServerException,
            IoError, InvalidValue {
        Object refReads = refRecord.getValue("MillionReadsHuman", user);
        if (Objects.nonNull(species) && species.toString().equalsIgnoreCase("Mouse")) {
            Object mouseReads = refRecord.getValue("MillionReadsMouse", user);
            if (Objects.nonNull(mouseReads) && !mouseReads.toString().trim().isEmpty()) {
                refReads = mouseReads;
            }
        }
        if (Objects.nonNull(refReads) && !refReads.toString().trim().isEmpty()) {
            seqReq.setDataField("RequestedReads", refReads, user);
        } else {
            logger.logInfo("Reference row carries no MillionReads value; RequestedReads left unchanged.");
        }

        Object refCoverage = refRecord.getValue("Coverage", user);
        if (Objects.nonNull(refCoverage) && !refCoverage.toString().trim().isEmpty()) {
            seqReq.setDataField("CoverageTarget", refCoverage, user);
        }
    }
}
