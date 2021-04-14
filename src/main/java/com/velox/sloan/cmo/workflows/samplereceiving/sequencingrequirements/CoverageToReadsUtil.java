package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.user.User;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

class CoverageToReadsUtil {
    CoverageToReadsUtil() {
    }

    public static Object getCoverageReadsFromRefs(Object recipe, Object panel, Object species, Object runType, Object tumorOrNormal, Object coverage, List<DataRecord> seqReqReferences, User user, PluginLogger logger) throws NotFound, RemoteException {
        if (Objects.isNull(recipe)) {
            recipe = "";
        }
        if (Objects.isNull(panel)) {
            panel = "";
        }
        if (Objects.isNull(runType)) {
            runType = "";
        }
        if (Objects.isNull(tumorOrNormal)) {
            tumorOrNormal = "";
        }
        if (Objects.isNull(coverage)) {
            coverage = "";
        }
        Iterator var9 = seqReqReferences.iterator();
        while(var9.hasNext()) {
            DataRecord d = (DataRecord)var9.next();
            Object dRecipe = d.getValue("PlatformApplication", user);
            Object dPanel = d.getValue("CapturePanel", user);
            Object dRunType = d.getValue("SequencingRunType", user);
            Object dTumorOrNormal = d.getValue("TumorNormal", user);
            Object dCoverage = d.getValue("Coverage", user);
            logger.logInfo(String.format("Given values: Recipe: %s, Panel: %s, RunType: %s, TumorOrNormal: %s, RequestedCoverage: %s", recipe, panel, runType, tumorOrNormal, coverage));
            logger.logInfo(String.format("Reference values: Recipe: %s, Panel: %s, RunType: %s, TumorOrNormal: %s, RequestedCoverage: %s", dRecipe, dPanel, dRunType, dTumorOrNormal, dCoverage));
            logger.logInfo("dRecipe = recipe: " + Objects.equals(dRecipe, recipe));
            logger.logInfo("dPanel = panel: " + Objects.equals(dPanel, panel));
            logger.logInfo("dRunType = runType: " + Objects.equals(dRunType, runType));
            logger.logInfo("dTumorOrNormal = tumorOrNormal: " + Objects.equals(dTumorOrNormal, tumorOrNormal));
            if (Objects.equals(dRecipe, recipe) && Objects.equals(dPanel, panel) && Objects.equals(dRunType, runType) && Objects.equals(dTumorOrNormal, tumorOrNormal) && Objects.nonNull(dCoverage) && Objects.nonNull(coverage) && dCoverage.toString().trim().equals(coverage.toString().trim())) {
                if (species.toString().equalsIgnoreCase("Human")) {
                    return d.getValue("MillionReadsHuman", user);
                }

                if (species.toString().equalsIgnoreCase("Mouse")) {
                    return d.getValue("MillionReadsMouse", user);
                }
            }
        }
        return null;
    }
}
