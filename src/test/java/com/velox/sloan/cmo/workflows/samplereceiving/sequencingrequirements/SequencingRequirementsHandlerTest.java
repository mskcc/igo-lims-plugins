package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class SequencingRequirementsHandlerTest extends SequencingRequirementsHandler {
    @Test
    public void sequencingRequirementsTest() {
        SequencingRequirementsHandler seqReqHandler = new SequencingRequirementsHandler();
        PluginResult pr = seqReqHandler.run();
        try {
            List<DataRecord> attachedSamples = this.activeTask.getAttachedDataRecords("Sample", this.user);
            List<DataRecord> seqRequirements = this.activeTask.getAttachedDataRecords("SeqRequirement", this.user);
            //ImmunoSeq
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("07340_B_53_2_1_1_1")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", this.user).toString(), "");
            }
            //ddPCR, DLP, CellLineAuthentication, COVID19
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("05816_CM_1_1")
                    || attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("09875_1")
                    || attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("05134_H_1")
                    || attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("06000_FZ_1")) {
                assertEquals(pr, new PluginResult(true));
            }

            // requested reads empty
            // coverage is empty for the recipe
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("UPS_PBMC_02")) {
                assertEquals(seqRequirements.get(0).getValue("MaxReads", this.user).toString(), "10.0");
            }

            //banked sample coverage is empty & sequencing requirements requested reads is empty
            // if banked sample capture panel is empty

            //EXPECTED: ask LIMS user to select capture panel
            // check max reads & coverage values
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("07555_1")) {
                assertEquals(seqRequirements.get(0).getValue("MaxReads", this.user).toString(), "");
                assertEquals(seqRequirements.get(0).getValue("Coverage", this.user).toString(), "");
            }

            // if banked sample capture panel has a value
            // check max reads & coverage values
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("")) {
                assertEquals(seqRequirements.get(0).getValue("MaxReads", this.user).toString(), "");
                assertEquals(seqRequirements.get(0).getValue("Coverage", this.user).toString(), "");
            }

            //banked sample coverage has a value and more than 1 capture panel mapped to banked sample recipe
            //EXPECTED: ask LIMS user to select capture panel
            // check max reads & coverage values
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("")) {
                assertEquals(seqRequirements.get(0).getValue("MaxReads", this.user).toString(), "");
                assertEquals(seqRequirements.get(0).getValue("Coverage", this.user).toString(), "");
            }

            // if banked sample capture panel has a value
            // check max reads & coverage values
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("")) {
                assertEquals(seqRequirements.get(0).getValue("MaxReads", this.user).toString(), "");
                assertEquals(seqRequirements.get(0).getValue("Coverage", this.user).toString(), "");
            }
        }
        catch (NotFound | ServerException | RemoteException ex) {
            this.logError(String.valueOf(ex.getStackTrace()));
        }
    }
}
