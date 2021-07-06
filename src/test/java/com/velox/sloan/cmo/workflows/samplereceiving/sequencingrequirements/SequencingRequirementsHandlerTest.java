package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxTask;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqRequirementModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;


public class SequencingRequirementsHandlerTest {
    User user;
    DataRecordManager dataRecordManager;
    IgoLimsPluginUtils util = new IgoLimsPluginUtils();
    DataMgmtServer dataMgmtServer;
    @Test
    public void setUpTestTearUp() {
        VeloxConnection connection = null;
        try {
            try {
                connection = new VeloxConnection("/Users/mirhajf/igo-lims-plugins/Connection.txt");
                System.out.println("Connection start");
                connection.open();
                user = connection.getUser();
                dataRecordManager = connection.getDataRecordManager();
                System.out.println("Connected successfully.");
                dataMgmtServer = connection.getDataMgmtServer();
                VeloxStandalone.run(connection, new VeloxTask<Object>() {
                    @Override
                    public Object performTask() {
                        sequencingRequirementsTest(user, dataRecordManager);
                        return new Object();
                    }
                });

            } catch (Throwable e) {
                e.printStackTrace();
            }
            finally {
                connection.close();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void sequencingRequirementsTest(User user, DataRecordManager dataRecordManager) {
        SequencingRequirementsHandler seqReqHandler = new SequencingRequirementsHandler();

        try {
            String igoId = "('07340_B_53', '12123_1', '05463_BA_1', '11856_B_1', '11113_I_1', '05538_AD_2', " +
                    "'06000_IW_3', '04553_L_1', '05257_CT_1', '04553_M_1', '05240_AH_1')";

            List<DataRecord> coverageReqRefs = dataRecordManager.queryDataRecords("ApplicationReadCoverageRef",
                    "ReferenceOnly != 1", user);
            List<DataRecord> attachedSamples = dataRecordManager.queryDataRecords(SampleModel.DATA_TYPE_NAME,
                    SampleModel.SAMPLE_ID + " IN " + igoId, user);

            List<DataRecord> seqRequirements = dataRecordManager.queryDataRecords(SeqRequirementModel.DATA_TYPE_NAME,
                    SeqRequirementModel.SAMPLE_ID + " IN " + igoId, user);
            Object requestId = ((DataRecord)attachedSamples.get(0)).getValue("RequestId", user);
            List<Object> sampleIds = attachedSamples.stream().map((s) -> {
                try {
                    return s.getValue("OtherSampleId", user);
                } catch (RemoteException | NotFound var3) {
                    var3.printStackTrace();
                    return null;
                }
            }).collect(Collectors.toList());

            String whereClause = String.format("%s IN %s AND %s='%s'", "UserSampleID", this.util.listToSqlInClauseVal(sampleIds), "RequestId", requestId);
            //System.out.println(whereClause);
            List<DataRecord> relatedBankedSampleInfo = dataRecordManager.queryDataRecords("BankedSample", whereClause, user);
            System.out.println(relatedBankedSampleInfo.size());
            System.out.println(relatedBankedSampleInfo.get(0).getValue("RunType", user));


            // NonSequencingRequirement recipes before values
            String sample1ReqReads = null, sample1Coverage = null, sample2ReqReads = null, sample2Coverage = null,
                    sample3ReqReads = null, sample3Coverage = null, sample4ReqReads = null, sample4Coverage = null;

            if(attachedSamples.get(1).getValue("SampleId", user).toString().equals("12123_1")) {
                sample1ReqReads = seqRequirements.get(1).getValue("RequestedReads", user).toString();
                sample1Coverage = seqRequirements.get(1).getValue("CoverageTarget", user).toString();
            }

            if(attachedSamples.get(2).getValue("SampleId", user).toString().equals("05463_BA_1")) {
                sample2ReqReads = seqRequirements.get(2).getValue("RequestedReads", user).toString();
                sample2Coverage = seqRequirements.get(2).getValue("CoverageTarget", user).toString();
            }

            if(attachedSamples.get(3).getValue("SampleId", user).toString().equals("11856_B_1")) {
                sample3ReqReads = seqRequirements.get(3).getValue("RequestedReads", user).toString();
                sample3Coverage = seqRequirements.get(3).getValue("CoverageTarget", user).toString();
            }

            if(attachedSamples.get(4).getValue("SampleId", user).toString().equals("11113_I_1")) {
                sample4ReqReads = seqRequirements.get(4).getValue("RequestedReads", user).toString();
                sample4Coverage = seqRequirements.get(4).getValue("CoverageTarget", user).toString();
            }

            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);

            //ImmunoSeq
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("07340_B_53")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "156/0/0/12");
            }
            //ddPCR, DLP, CellLineAuthentication, COVID19
            if (attachedSamples.get(1).getValue("SampleId", user).toString().equals("12123_1")
                    || attachedSamples.get(2).getValue("SampleId", user).toString().equals("05463_BA_1")
                    || attachedSamples.get(3).getValue("SampleId", user).toString().equals("11856_B_1")
                    || attachedSamples.get(4).getValue("SampleId", user).toString().equals("11113_I_1")) {

                assertEquals(seqRequirements.get(1).getValue("RequestedReads", user).toString(), sample1ReqReads);
                assertEquals(seqRequirements.get(1).getValue("CoverageTarget", user).toString(), sample1Coverage);

                assertEquals(seqRequirements.get(2).getValue("RequestedReads", user).toString(), sample2ReqReads);
                assertEquals(seqRequirements.get(2).getValue("CoverageTarget", user).toString(), sample2Coverage);

                assertEquals(seqRequirements.get(3).getValue("RequestedReads", user).toString(), sample3ReqReads);
                assertEquals(seqRequirements.get(3).getValue("CoverageTarget", user).toString(), sample3Coverage);

                assertEquals(seqRequirements.get(4).getValue("RequestedReads", user).toString(), sample4ReqReads);
                assertEquals(seqRequirements.get(4).getValue("CoverageTarget", user).toString(), sample4Coverage);
            }

            //
            if (attachedSamples.get(5).getValue("SampleId", user).toString().equals("05538_AD_2")) {
                System.out.println(attachedSamples.get(5).getValue("Recipe", user).toString());
                assertEquals(seqRequirements.get(5).getValue("SequencingRunType", user).toString(), "PE100");
//                assertEquals(seqRequirements.get(5).getValue("RequestedReads", user).toString(), "40");
//                assertEquals(seqRequirements.get(5).getValue("MinimumReads", user).toString(), "30");
            }

            if (attachedSamples.get(6).getValue("SampleId", user).toString().equals("06000_IW_3")) {
                //assertEquals(seqRequirements.get(6).getValue("SequencingRunType", user).toString(), "PE50");
//                assertEquals(seqRequirements.get(6).getValue("RequestedReads", user).toString(), "10");
//                assertEquals(seqRequirements.get(6).getValue("MinimumReads", user).toString(), "5");
            }


            if (attachedSamples.get(7).getValue("SampleId", user).toString().equals("04553_L_1")) {
                assertEquals(seqRequirements.get(7).getValue("SequencingRunType", user).toString(), "PE100");
                assertEquals(seqRequirements.get(7).getValue("CoverageTarget", user).toString(), "500");
                assertEquals(seqRequirements.get(7).getValue("RequestedReads", user).toString(), "14.0");
            }


            if (attachedSamples.get(8).getValue("SampleId", user).toString().equals("05257_CT_1")) {
                assertEquals(seqRequirements.get(8).getValue("SequencingRunType", user).toString(), "PE100");
                assertEquals(seqRequirements.get(8).getValue("CoverageTarget", user).toString(), "1000");
                assertEquals(seqRequirements.get(8).getValue("RequestedReads", user).toString(), "60.0");
            }


            if (attachedSamples.get(9).getValue("SampleId", user).toString().equals("04553_M_1")) {
                assertEquals(seqRequirements.get(9).getValue("SequencingRunType", user).toString(), "PE100");
                assertEquals(seqRequirements.get(9).getValue("CoverageTarget", user).toString(), "150");
                assertEquals(seqRequirements.get(9).getValue("RequestedReads", user).toString(), "95.0");
            }


            if (attachedSamples.get(10).getValue("SampleId", user).toString().equals("05240_AH_1")) {
                assertEquals(seqRequirements.get(10).getValue("SequencingRunType", user).toString(), "PE150");
                assertEquals(seqRequirements.get(10).getValue("CoverageTarget", user).toString(), "40");
                assertEquals(seqRequirements.get(10).getValue("RequestedReads", user).toString(), "600.0");
            }
        }
        catch (NotFound | ServerException | IoError | InvalidValue | RemoteException ex) {
            ex.printStackTrace();
        }
    }
}
