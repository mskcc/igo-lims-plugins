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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;


public class SequencingRequirementsHandlerTest {
    User user;
    DataRecordManager dataRecordManager;
    IgoLimsPluginUtils util = new IgoLimsPluginUtils();
    DataMgmtServer dataMgmtServer;
    SequencingRequirementsHandler seqReqHandler = new SequencingRequirementsHandler();
    List<DataRecord> coverageReqRefs;
    List<DataRecord> seqRequirements;
    List<Object> sampleIds;
    List<DataRecord> attachedSamples;
    List<DataRecord> relatedBankedSampleInfo;

    @Test
    public void setUpRunTearUp() {
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
                        Covid19RecipeTest(user);
//                        dlpRecipeTest(user);
//                        cellLineAuthRecipeTest(user);
                        //ddPCRRecipeTest(user);
                        //ImmunoSeqTest(user);
                        //problem1Test(user);
                       //problem2Test(user);
                        //sample04553_L_1(user);
                        //sample05257_CT_1(user);
                        //sample04553_M_1(user);
                        //sample05240_AH_1(user);
                        return new Object();
                    }
                });

            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                connection.close();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void readData(User user, DataRecordManager dataRecordManager, String igoId) {
        try {
            coverageReqRefs = dataRecordManager.queryDataRecords("ApplicationReadCoverageRef",
                    "ReferenceOnly != 1", user);
            attachedSamples = dataRecordManager.queryDataRecords(SampleModel.DATA_TYPE_NAME,
                    SampleModel.SAMPLE_ID + " IN " + igoId, user);

            seqRequirements = dataRecordManager.queryDataRecords(SeqRequirementModel.DATA_TYPE_NAME,
                    SeqRequirementModel.SAMPLE_ID + " IN " + igoId, user);
            sampleIds = attachedSamples.stream().map((s) -> {
                try {
                    return s.getValue("OtherSampleId", user);
                } catch (RemoteException | NotFound var3) {
                    var3.printStackTrace();
                    return null;
                }
            }).collect(Collectors.toList());


            relatedBankedSampleInfo = new LinkedList<>();
            String whereClause = "";
            for (int i = 0; i < attachedSamples.size(); i++) {
                Object requestId = ((DataRecord) attachedSamples.get(i)).getValue("RequestId", this.user);
                Object userSampleId = ((DataRecord) attachedSamples.get(i)).getValue("UserSampleID", this.user);
                whereClause = String.format("%s='%s' AND %s='%s'", "UserSampleID", userSampleId, "RequestId", requestId);
                relatedBankedSampleInfo.add(this.dataRecordManager.queryDataRecords("BankedSample", whereClause, this.user).get(0));
            }
//            for(int i = 0; i < relatedBankedSampleInfo.size(); i++) {
//                System.out.println(relatedBankedSampleInfo.get(i).getValue("RequestedReads", user).toString()
//                        + " : " + relatedBankedSampleInfo.get(i).getValue("SequencingReadLength", user).toString());
//            }
        } catch (NotFound | IoError | RemoteException ex) {
            ex.printStackTrace();
        }
    }


    // NonSequencingRequirement recipes
    private void Covid19RecipeTest(User user) {
        String sample1ReqReads = null, sample1Coverage = null;
        try {
            readData(user, dataRecordManager, "('12123_1')");
            System.out.println("attachedSamples size:" + attachedSamples.size());
            System.out.println("bankedSamples size:" + relatedBankedSampleInfo.size());
            System.out.println("seq req size:" + seqRequirements.size());
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("12123_1")) {
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    sample1ReqReads = seqRequirements.get(0).getValue("RequestedReads", user).toString();
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    sample1Coverage = seqRequirements.get(0).getValue("CoverageTarget", user).toString();
                }
            }
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);

            if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), sample1ReqReads);
            }
            if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), sample1Coverage);
            }

        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    private void ddPCRRecipeTest(User user) {
        String sample2ReqReads = null, sample2Coverage = null;
        try {
            readData(user, dataRecordManager, "('05463_BA_1')");
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05463_BA_1")) {
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    sample2ReqReads = seqRequirements.get(0).getValue("RequestedReads", user).toString();
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    sample2Coverage = seqRequirements.get(0).getValue("CoverageTarget", user).toString();
                }
            }
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);

            if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), sample2ReqReads);
            }
            if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), sample2Coverage);
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    public void cellLineAuthRecipeTest(User user) {
        String sample3ReqReads = null, sample3Coverage = null;
        try {
            readData(user, dataRecordManager, "('11856_B_1')");
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("11856_B_1")) {
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    sample3ReqReads = seqRequirements.get(0).getValue("RequestedReads", user).toString();
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    sample3Coverage = seqRequirements.get(0).getValue("CoverageTarget", user).toString();
                }
            }
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);

            if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), sample3ReqReads);
            }
            if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), sample3Coverage);
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    public void dlpRecipeTest(User user) {
        String sample4ReqReads = null, sample4Coverage = null;
        try {
            readData(user, dataRecordManager, "('11113_I_1')");
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("11113_I_1")) {
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    sample4ReqReads = seqRequirements.get(0).getValue("RequestedReads", user).toString();
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    sample4Coverage = seqRequirements.get(0).getValue("CoverageTarget", user).toString();
                }
            }
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
            if(!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), sample4ReqReads);
                }
                if(!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), sample4Coverage);
                }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    //ImmunoSeq
    public void ImmunoSeqTest(User user) {
        try {
            readData(user, dataRecordManager, "('07340_B_53')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("07340_B_53")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "156/0/0/12");
            }
        }
        catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }


    public void problem1Test(User user) {
        try{
            readData(user, dataRecordManager, "('05538_AD_2')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05538_AD_2")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "PE100");
                //System.out.println("This sample seq req: " + seqRequirements.get(0).getValue("RequestedReads", user).toString());
                assertEquals(seqRequirements.get(0).getValue("MinimumReads", user).toString(), "30.0");
                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), "40.0");

            }
        }
        catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }


    public void problem2Test(User user) {
        try {
            readData(user, dataRecordManager, "('06000_IW_3')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("06000_IW_3")) {
                System.out.println(attachedSamples.get(0).getValue("Recipe", user).toString());
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "PE50");
                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), "10.0");
                assertEquals(seqRequirements.get(0).getValue("MinimumReads", user).toString(), "5.0");
            }
        }
        catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    public void sample04553_L_1(User user) {
        try {
            //'04553_L_1', '05257_CT_1', '04553_M_1', '05240_AH_1'
            readData(user, dataRecordManager, "('04553_L_1')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("04553_L_1")) {
                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "PE100");
                assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), "500");
                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), "14.0");
            }
        }
        catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

        public void sample05257_CT_1(User user){
            try {
                readData(user, dataRecordManager, "('05257_CT_1')");
                seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
                if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05257_CT_1")) {
                    assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "PE100");
                    assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), "1000");
                    assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), "60.0");
                }
            }
            catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
                e.printStackTrace();
            }
        }
        public void sample04553_M_1(User user) {
            try {
                readData(user, dataRecordManager, "('04553_M_1')");
                seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
                if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("04553_M_1")) {
                    assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "PE100");
                    assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), "150");
                    assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), "95.0");
                }
            } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
                e.printStackTrace();
            }
        }
//
//
//    public void sample05240_AH_1(User user) {
//        try {
//            readData(user, dataRecordManager, "('05240_AH_1')");
//            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
//            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05240_AH_1")) {
//                assertEquals(seqRequirements.get(0).getValue("SequencingRunType", user).toString(), "PE150");
//                assertEquals(seqRequirements.get(0).getValue("CoverageTarget", user).toString(), "40");
//                assertEquals(seqRequirements.get(0).getValue("RequestedReads", user).toString(), "600.0");
//            }
//        }
//        catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
//            e.printStackTrace();
//        }
//    }
}

