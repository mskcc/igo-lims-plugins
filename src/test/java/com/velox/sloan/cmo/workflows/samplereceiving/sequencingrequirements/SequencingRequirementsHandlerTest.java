package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datarecord.*;
import com.velox.api.plugin.PluginResult;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sloan.cmo.recmodels.BankedSampleModel;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqRequirementModel;
import com.velox.sloan.cmo.workflows.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class SequencingRequirementsHandlerTest extends SequencingRequirementsHandler {
    User user;
    DataRecordManager dataRecordManager;
    VeloxConnection connection = new VeloxConnection("/Users/mirhajf/igo-lims-plugins/Connection.txt");
    TestUtils testUtils = new TestUtils();
    @Before
    public void setUp() {
        connection = testUtils.connectServer();
        user = connection.getUser();
        dataRecordManager = connection.getDataRecordManager();
    }

    @Test
    public void sequencingRequirementsTest() {
        SequencingRequirementsHandler seqReqHandler = new SequencingRequirementsHandler();

        try {
            String sampleIds = "('07340_B_53_2_1_1_1', 'UPS_PBMC_02', '07555_1')";
            String igoId = "('07566_12_1_1', '07566_13_1_1', '09687_AO_1_1')";
            List<DataRecord> coverageReqRefs = dataRecordManager.queryDataRecords("ApplicationReadCoverageRef", "ReferenceOnly != 1", user);
            List<DataRecord> attachedSamples = dataRecordManager.queryDataRecords("Sample",  "SampleId IN" + sampleIds, user);
            List<DataRecord> seqRequirements = dataRecordManager.queryDataRecords("SeqRequirement", "SampleId IN " + igoId, user);
            List<DataRecord> relatedBankedSampleInfo = this.getBankedSamples(attachedSamples);

            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs);
            //ImmunoSeq
            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("07340_B_53_2_1_1_1")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", this.user).toString(), "");
            }
            //ddPCR, DLP, CellLineAuthentication, COVID19
//            if (attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("05816_CM_1_1")
//                    || attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("09875_1")
//                    || attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("05134_H_1")
//                    || attachedSamples.get(0).getValue("SampleId", this.user).toString().equals("06000_FZ_1")) {
//                assertEquals();
//            }

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
        catch (NotFound | ServerException | IoError | InvalidValue | RemoteException ex) {
            this.logError(String.valueOf(ex.getStackTrace()));
        }
    }
    @After
    public void tearDown() throws Exception {
        connection.close();
    }
}
