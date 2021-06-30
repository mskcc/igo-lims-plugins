package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqRequirementModel;
import com.velox.sloan.cmo.workflows.TestUtils;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class SequencingRequirementsHandlerTest extends SequencingRequirementsHandler {
    User user;
    DataRecordManager dataRecordManager;
    VeloxConnection connection = new VeloxConnection("/Users/mirhajf/igo-lims-plugins/Connection.txt");
    TestUtils testUtils = new TestUtils();
    @Test
    public void setUp() {
        try {
            try {
                if (connection.isConnected()) {
                    connection = testUtils.connectServer();
                    user = connection.getUser();
                    dataRecordManager = connection.getDataRecordManager();
                }

                VeloxStandalone.run(connection, new VeloxTask<Object>() {
                    @Override
                    public Object performTask() throws VeloxStandaloneException {
                        sequencingRequirementsTest(user, dataRecordManager);
                        return new Object();
                    }
                });
            } catch (Throwable e) {

            }
            finally {
                connection.close();
            }
        } catch (Throwable t) {

        }
    }

    private void sequencingRequirementsTest(User user, DataRecordManager dataRecordManager) {
        SequencingRequirementsHandler seqReqHandler = new SequencingRequirementsHandler();

        try {
            String igoId = "('07340_B_53_2_1_1_1', '12123_1', '05463_BA_1', '11856_B_1', '11113_I_1', '05538_AD_2', " +
                    "'06000_IW_3', '04553_L_1')";

            List<DataRecord> coverageReqRefs = dataRecordManager.queryDataRecords("ApplicationReadCoverageRef",
                    "ReferenceOnly != 1", user);
            List<DataRecord> attachedSamples = dataRecordManager.queryDataRecords(SampleModel.DATA_TYPE_NAME,
                    SampleModel.SAMPLE_ID + " IN " + igoId, user);
            List<DataRecord> seqRequirements = dataRecordManager.queryDataRecords(SeqRequirementModel.DATA_TYPE_NAME,
                    SeqRequirementModel.SAMPLE_ID + " IN " + igoId, user);
            List<DataRecord> relatedBankedSampleInfo = this.getBankedSamples(attachedSamples);


            // NonSequencingRequirement recipes before velues
            String sample1ReqReads = seqRequirements.get(1).getValue("RequestedReads", user).toString();
            String sample1Coverage = seqRequirements.get(1).getValue("CoverageTarget", user).toString();

            String sample2ReqReads = seqRequirements.get(2).getValue("RequestedReads", user).toString();
            String sample2Coverage = seqRequirements.get(2).getValue("CoverageTarget", user).toString();

            String sample3ReqReads = seqRequirements.get(3).getValue("RequestedReads", user).toString();
            String sample3Coverage = seqRequirements.get(3).getValue("CoverageTarget", user).toString();

            String sample4ReqReads = seqRequirements.get(4).getValue("RequestedReads", user).toString();
            String sample4Coverage = seqRequirements.get(4).getValue("CoverageTarget", user).toString();

            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs);

            //ImmunoSeq
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("07340_B_53_2_1_1_1")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "");
            }
            //ddPCR, DLP, CellLineAuthentication, COVID19
            if (attachedSamples.get(1).getValue("SampleId", this.user).toString().equals("12123_1")
                    || attachedSamples.get(2).getValue("SampleId", this.user).toString().equals("05463_BA_1")
                    || attachedSamples.get(3).getValue("SampleId", this.user).toString().equals("11856_B_1")
                    || attachedSamples.get(4).getValue("SampleId", this.user).toString().equals("11113_I_1")) {
                assertEquals(seqRequirements.get(1).getValue("RequestedReads", user).toString(), sample1ReqReads);
                assertEquals(seqRequirements.get(1).getValue("CoverageTarget", user).toString(), sample1Coverage);

                assertEquals(seqRequirements.get(2).getValue("RequestedReads", user).toString(), sample2ReqReads);
                assertEquals(seqRequirements.get(2).getValue("CoverageTarget", user).toString(), sample2Coverage);

                assertEquals(seqRequirements.get(3).getValue("RequestedReads", user).toString(), sample3ReqReads);
                assertEquals(seqRequirements.get(3).getValue("CoverageTarget", user).toString(), sample3Coverage);

                assertEquals(seqRequirements.get(4).getValue("RequestedReads", user).toString(), sample4ReqReads);
                assertEquals(seqRequirements.get(4).getValue("CoverageTarget", user).toString(), sample4Coverage);
            }

            // requested reads empty
            // coverage is empty for the recipe
            if (attachedSamples.get(7).getValue("SampleId", this.user).toString().equals("")) {
                assertEquals(seqRequirements.get(7).getValue("MaxReads", this.user).toString(), "10.0");
            }

            //banked sample coverage is empty & sequencing requirements requested reads is empty
            // if banked sample capture panel is empty

            //EXPECTED: ask LIMS user to select capture panel
            // check max reads & coverage values
            if (attachedSamples.get(8).getValue("SampleId", user).toString().equals("07555_1")) {
                assertEquals(seqRequirements.get(8).getValue("RequestedReads", user).toString(), "");
                assertEquals(seqRequirements.get(8).getValue("CoverageTarget", user).toString(), "");
            }

            // if banked sample capture panel has a value
            // check max reads & coverage values
            if (attachedSamples.get(9).getValue("SampleId", user).toString().equals("")) {
                assertEquals(seqRequirements.get(9).getValue("RequestedReads", user).toString(), "");
                assertEquals(seqRequirements.get(9).getValue("CoverageTarget", user).toString(), "");
            }

            //banked sample coverage has a value and more than 1 capture panel mapped to banked sample recipe
            //EXPECTED: ask LIMS user to select capture panel
            // check max reads & coverage values
            if (attachedSamples.get(10).getValue("SampleId", user).toString().equals("")) {
                assertEquals(seqRequirements.get(10).getValue("RequestedReads", user).toString(), "");
                assertEquals(seqRequirements.get(10).getValue("CoverageTarget", user).toString(), "");
            }

            // if banked sample coverage has a value and banked sample capture panel has a value
            // check max reads & coverage values
            if (attachedSamples.get(11).getValue("SampleId", user).toString().equals("")) {
                assertEquals(seqRequirements.get(11).getValue("RequestedReads", user).toString(), "");
                assertEquals(seqRequirements.get(11).getValue("CoverageTarget", user).toString(), "");
            }
        }
        catch (NotFound | ServerException | IoError | InvalidValue | RemoteException ex) {
            System.out.println(ex.getStackTrace());
        }
    }
}
