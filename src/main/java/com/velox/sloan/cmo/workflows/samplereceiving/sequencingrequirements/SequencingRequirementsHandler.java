package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.servermanager.PickListConfig;
import com.velox.api.user.User;
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
        //this.logInfo("Checking run status");
        return this.activeTask.getTask().getTaskOptions().containsKey("UPDATE SEQUENCING REQUIREMENTS FROM REFERENCE TABLE") && !this.activeTask.getTask().getTaskOptions().containsKey("SEQUENCING REQUIREMENTS UPDATED");
    }

    public PluginResult run() {
        try {
            //this.logInfo("Running sequencing requirements handler plugin");
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

            // If user submitted pooled libraries, do not run the plugin.
            if (sampleType.toString().toLowerCase().equals("pooled library")) {
                logInfo("Skipping coverage to reads conversion for user submitted pools");
                return new PluginResult(true);
            }

            if (seqRequirements.isEmpty()) {
                this.clientCallback.displayError("Sample 'SequencingRequirements' not attached to this task.");
                return new PluginResult(false);
            }

            List<DataRecord> relatedBankedSampleInfo = this.getBankedSamples(attachedSamples);
            this.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, this.user, this.dataMgmtServer);
            this.activeTask.getTask().getTaskOptions().put("SEQUENCING REQUIREMENTS UPDATED", "");
        } catch (NotFound | ServerException | IoError | InvalidValue | RemoteException var6) {
            this.logError(String.valueOf(var6.getStackTrace()));
            return new PluginResult(true);
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
    public List<DataRecord> getBankedSamples(List<DataRecord> attachedSamples) throws NotFound, RemoteException, IoError {
        List<DataRecord> bankedSamples = new LinkedList<>();
        for (int i = 0; i < attachedSamples.size(); i++) {
            Object requestId = ((DataRecord) attachedSamples.get(i)).getValue("RequestId", this.user);
            Object userSampleId = ((DataRecord) attachedSamples.get(i)).getValue("UserSampleID", this.user);
            //this.logInfo("RequestId: " + requestId);
            String whereClause = String.format("%s=%s AND %s=%s", "UserSampleID", userSampleId, "RequestId", requestId);
            //this.logInfo("WHERE CLAUSE: " + whereClause);
            bankedSamples.add(this.dataRecordManager.queryDataRecords("BankedSample", whereClause, this.user).get(i));
        }
        return bankedSamples;

    }


    /**
     * Method to get all Recipes that require Coverage.
     *
     * @param coverageReqRefs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getCoverageRecipes(List<DataRecord> coverageReqRefs) throws NotFound, RemoteException {
        HashSet<String> recipes = new HashSet<>();
        Iterator references = coverageReqRefs.iterator();

        while (references.hasNext()) {
            DataRecord r = (DataRecord) references.next();
            Object rRecipe = r.getValue("PlatformApplication", this.user);
            Boolean isRefOnly = r.getBooleanVal("ReferenceOnly", this.user);
            if (!Objects.isNull(rRecipe) && !isRefOnly) {
                recipes.add(rRecipe.toString());
            }
        }
        return new ArrayList(recipes);
    }

    /**
     * Method to get the runTypes for a Recipe from the 'ApplicationReadCoverageRef' table in LIMS.
     *
     * @param refs
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getRunTypeValues(List<DataRecord> refs) throws NotFound, RemoteException {
        HashSet<String> runTypes = new HashSet<>();
        Iterator references = refs.iterator();
        while (references.hasNext()) {
            DataRecord d = (DataRecord) references.next();
            Object dRecipe = d.getValue("PlatformApplication", this.user);
            Object dRunType = d.getValue("SequencingRunType", this.user);
            if (Objects.equals(recipe, dRecipe) && Objects.nonNull(dRunType)) {
                runTypes.add((String) dRunType);
            }
        }
        return new ArrayList(runTypes);
    }

    /**
     * Method to get PanelNames from 'ApplicationReadCoverageRef' table in LIMS.
     *
     * @param referenceValues
     * @return
     * @throws NotFound
     * @throws RemoteException
     */
    private List<String> getCapturePanelValues(List<DataRecord> referenceValues) throws NotFound, RemoteException {
        HashSet<String> panels = new HashSet();
        Iterator references = referenceValues.iterator();
        while (references.hasNext()) {
            DataRecord d = (DataRecord) references.next();
            Object dRecipe = d.getValue("PlatformApplication", this.user);
            Object dPanel = d.getValue("CapturePanel", this.user);
            if (Objects.equals(recipe, dRecipe) && Objects.nonNull(dPanel)) {
                panels.add((String) dPanel);
            }
        }
        return new ArrayList(panels);
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
                             List<DataRecord> coverageReqRefs, User user, DataMgmtServer dataMgmtServer) throws NotFound,
            RemoteException, ServerException, IoError, InvalidValue {

        recipe = samples.get(0).getValue(SampleModel.RECIPE, user);
        Iterator sampleIter = samples.iterator();

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
        while (refIter.hasNext()) {
            DataRecord ref = (DataRecord) refIter.next();
            String refRecipe = ref.getValue("PlatformApplication", user).toString();
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
        }
        while(true) {
            while (true) {
                while (sampleIter.hasNext()) {
                    if (nonSeqRecipes.getEntryList().contains(recipe.toString())) {
                        break;
                    }

                    DataRecord s = (DataRecord) sampleIter.next();
                    ////this.logInfo("at sample: " + s.toString());
                    Object igoId = s.getValue("SampleId", user);
                    Object sampleId = s.getValue("OtherSampleId", user);
                    Object species = s.getValue("Species", user);
                    Object tumorOrNormal = s.getValue("TumorOrNormal", user);
                    Object reads = null;
                    Object coverage = null;
                    Object sequencingReadLength = null;
                    Iterator banked = bankedSamples.iterator();
                    Iterator seqReqs1 = seqRequirements.iterator();
                    DataRecord d = null;
                    DataRecord seqReq;
                    Object igoIdSr;
                    while (banked.hasNext()) {
                        d = (DataRecord) banked.next();
                        //this.logInfo("at banked sample: " + d.getValue("RecordId", user));
                        igoIdSr = d.getValue("UserSampleID", user);
                        if (Objects.equals(sampleId, igoIdSr)) {
                            reads = d.getValue("RequestedReads", user);
                            coverage = d.getValue("RequestedCoverage", user);
                            runType = d.getValue("RunType", user);
                            sequencingReadLength = d.getValue("SequencingReadLength", user);
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
                                ////this.logInfo("Panel: " + this.panelName);
                            }
                        }
                    }


                    while (seqReqs1.hasNext()) {
                        seqReq = (DataRecord) seqReqs1.next();
                        igoIdSr = seqReq.getValue("SampleId", user);
                        if (Objects.equals(igoIdSr, igoId)) {
                            //this.logInfo("seq req update is happening..");
                            if (recipe.toString().equals("ImmunoSeq")) {
                                if (!Objects.isNull(runType) && !runType.toString().trim().isEmpty()) {
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
                                seqReq.setDataField("SequencingRunType", recipeToSequencingRunTypeMap
                                        .get(recipe), user);
                            }
                            //this.logInfo(d.getDataField("RecordId", user).toString());
                            ////this.logInfo(reads.toString());
                            if (!Objects.isNull(reads) && !reads.toString().trim().isEmpty()) {
                                //this.logInfo("I am going to parse the read.");
                                if(!Objects.isNull(sequencingReadLength) && !sequencingReadLength.toString()
                                        .trim().isEmpty()) {
                                    String minMaxRead = reads.toString().split(" ")[0];
                                    Object[] minMax = new Object[2];
                                    if (minMaxRead.contains("-")) {
                                        minMax = (Object[]) minMaxRead.split("-");
                                        seqReq.setDataField("RequestedReads", minMax[1], user);
                                        seqReq.setDataField("MinimumReads", minMax[0], user);
                                    } else { // no range
                                        seqReq.setDataField("RequestedReads", minMaxRead, user);
                                    }
                                }
                                else if (Objects.isNull(refRecipeToCoverageMap.get(recipe.toString())) ||
                                        refRecipeToCoverageMap.get(recipe.toString()).size() == 0) {
                                    String minMaxRead = reads.toString().split(" ")[0];
                                    Object[] minMax = new Object[2];
                                    if (minMaxRead.contains("-")) {
                                        minMax = (Object[]) minMaxRead.split("-");
                                        seqReq.setDataField("RequestedReads", minMax[1], user);
                                        seqReq.setDataField("MinimumReads", minMax[0], user);
                                    } else { // no range
                                        seqReq.setDataField("RequestedReads", minMaxRead, user);
                                    }
                                    //this.logInfo("Here at read parsing I am..");
                                }
                            }

                            // ShallowWGS, CRISPR
                            else if ((Objects.isNull(refRecipeToCoverageMap.get(recipe.toString())) ||
                                    refRecipeToCoverageMap.get(recipe.toString()).size() == 0) &&
                                    (Objects.isNull(reads) || reads.toString().trim().isEmpty())) {
                                //this.logInfo("I thought it's ShallowWGS or CRISPR");
                                seqReq.setDataField("RequestedReads", refRecipeToTranslatedReadsHumanMap.
                                        get(recipe.toString()), user);

                            } else if ((Objects.isNull(coverage) || coverage.toString().trim().isEmpty()) &&
                                    (Objects.isNull(seqReq.getValue("RequestedReads", user)) ||
                                            seqReq.getValue("RequestedReads", user).toString().trim().isEmpty())) {
                                //this.logInfo("Coverage is null..");
                                if (Objects.isNull(this.panelName) || this.panelName.toString().trim().isEmpty()) {
                                    if (!Objects.isNull(recipeToCapturePanelMap.get(recipe.toString()))) {
                                        if (recipeToCapturePanelMap.get(recipe.toString()).size() > 1) {
                                            try {
                                                Object[] listOfCapturePanels = recipeToCapturePanelMap.get(recipe.toString())
                                                        .toArray(new String[recipeToCapturePanelMap.get(recipe.toString()).size()]);
                                                String[] stringListOfCapturePanels = new String[listOfCapturePanels.length];
                                                for (int i = 0; i < listOfCapturePanels.length; i++) {
                                                    stringListOfCapturePanels[i] = listOfCapturePanels[i].toString();
                                                }
                                                int selectedCapturePanelIndex = clientCallback.showOptionDialog("",
                                                        "Please select a capture panel", stringListOfCapturePanels, 0);
                                                this.panelName = (Object) stringListOfCapturePanels[0];
                                            } catch (ServerException se) {
                                                this.logError(String.valueOf(se.getStackTrace()));
                                                //continue;
                                            }
                                        } else {
                                            this.panelName = recipeToCapturePanelMap.get(recipe.toString()).toArray()[0];
                                        }
                                    }
                                    //this.logInfo("finding ref corresponding record..");
                                    DataRecord refRecord = CoverageToReadsUtil.getRefRecordFromRecipeAndCapturePanel
                                            (recipe, this.panelName, tumorOrNormal, coverage, coverageReqRefs,
                                                    user, this.pluginLogger);
                                    if (species.toString().equalsIgnoreCase("Human")) {
                                        seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                "MillionReadsHuman", user), user);
                                    } else if (species.toString().equalsIgnoreCase("Mouse")) {
                                        seqReq.setDataField("RequestedReads", refRecord.getValue(
                                                "MillionReadsMouse", user), user);
                                    }
                                    if (refRecipeToCoverageMap.get(recipe).size() > 0 &&
                                            !Objects.isNull(refRecipeToCoverageMap.get(recipe.toString()))) {
                                        seqReq.setDataField("CoverageTarget", refRecord.getValue(
                                                "Coverage", user), user);
                                    }
                                }

                            } else {
                                // requested coverage has a value
                                //this.logInfo("Coverage is NOT null..");
                                if (!Objects.isNull(recipeToCapturePanelMap.get(recipe.toString()))) {
                                    if (recipeToCapturePanelMap.get(recipe.toString()).size() > 1) {
                                        try {
                                            Object[] listOfCapturePanels = recipeToCapturePanelMap.get(recipe).toArray(
                                                    new String[recipeToCapturePanelMap.get(recipe.toString()).size()]);
                                            String[] stringListOfCapturePanels = new String[listOfCapturePanels.length];
                                            for (int i = 0; i < listOfCapturePanels.length; i++) {
                                                stringListOfCapturePanels[i] = listOfCapturePanels[i].toString();
                                            }
                                            int selectedCapturePanelIndex = clientCallback.showOptionDialog("",
                                                    "Please select a capture panel", stringListOfCapturePanels, 0);
                                            this.panelName = (Object) stringListOfCapturePanels[0];
                                        } catch (ServerException se) {
                                            this.logError(String.valueOf(se.getStackTrace()));
                                            //continue;
                                        }
                                    } else if (recipeToCapturePanelMap.get(recipe.toString()).size() == 1) {
                                        this.panelName = recipeToCapturePanelMap.get(recipe.toString());
                                    }
                                }
                                //this.logInfo("finding ref corresponding record..");
                                DataRecord refRecord = CoverageToReadsUtil.getRefRecordFromRecipeAndCapturePanel(
                                        recipe, this.panelName, tumorOrNormal, coverage, coverageReqRefs, user, this.pluginLogger);
                                if (species.toString().equalsIgnoreCase("Human")) {
                                    seqReq.setDataField("RequestedReads", refRecord.getValue(
                                            "MillionReadsHuman", user), user);
                                } else if (species.toString().equalsIgnoreCase("Mouse")) {
                                    seqReq.setDataField("RequestedReads", refRecord.getValue(
                                            "MillionReadsMouse", user), user);
                                }
                                if (refRecipeToCoverageMap.get(recipe).size() > 0 &&
                                        !Objects.isNull(refRecipeToCoverageMap.get(recipe))) {
                                    seqReq.setDataField("CoverageTarget", refRecord.getValue(
                                            "Coverage", user), user);
                                }
                            }
                        }
                    }
                }
                return;
            }
        }
    }
}