package com.velox.sloan.cmo.workflows.samplereceiving.sequencingrequirements;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.*;
import com.velox.api.plugin.DefaultServerPlugin;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.user.User;
import com.velox.api.util.ClientCallbackOperations;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqRequirementModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;


import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Fahimeh Mirhaj
 */
@Ignore
public class SequencingRequirementsHandlerTest {
    User user;
    DataRecordManager dataRecordManager;
    DataMgmtServer dataMgmtServer;
    PluginLogger logger;
    SequencingRequirementsHandler seqReqHandler = new SequencingRequirementsHandler();
    List<DataRecord> coverageReqRefs;
    List<DataRecord> seqRequirements;
    List<Object> sampleIds;
    List<DataRecord> attachedSamples;
    List<DataRecord> relatedBankedSampleInfo;
    VeloxConnection connection = null;

    @Before
    public void setUp() {
        try {
            connection = new VeloxConnection("/Users/mirhajf/igo-lims-plugins/Connection.txt");
            System.out.println("Connection start");
            connection.open();
            user = connection.getUser();
            dataRecordManager = connection.getDataRecordManager();
            System.out.println("Connected successfully.");
            dataMgmtServer = connection.getDataMgmtServer();

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @After
    public void tearUp() {
        try {
            connection.close();
        } catch (Throwable t) {

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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    // NonSequencingRequirement recipes
    @Test
    public void Covid19RecipeTest() {
        String sample1ReqReads = null, sample1Coverage = null;
        try {
            readData(user, dataRecordManager, "('12123_1')");
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("12123_1")) {
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    sample1ReqReads = seqRequirements.get(0).getValue("RequestedReads", user).toString();
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    sample1Coverage = seqRequirements.get(0).getValue("CoverageTarget", user).toString();
                }
            }
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            

            if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                assertEquals(sample1ReqReads, seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
            if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                assertEquals(sample1Coverage, seqRequirements.get(0).getValue("CoverageTarget", user).toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void ddPCRRecipeTest() {
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
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            

            if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                assertEquals(sample2ReqReads, seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
            if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                assertEquals(sample2Coverage, seqRequirements.get(0).getValue("CoverageTarget", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    @Test
    public void cellLineAuthRecipeTest() {
        String sample3ReqReads = null, sample3Coverage = null;
        try {
            readData(user, dataRecordManager, "('11851_1')");
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("11851_1")) {
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    sample3ReqReads = seqRequirements.get(0).getValue("RequestedReads", user).toString();
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    sample3Coverage = seqRequirements.get(0).getValue("CoverageTarget", user).toString();
                }
            }
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            

            if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                assertEquals(sample3ReqReads, seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
            if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                assertEquals(sample3Coverage, seqRequirements.get(0).getValue("CoverageTarget", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    @Test
    public void dlpRecipeTest() {
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

                seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
                
                if (!Objects.isNull(seqRequirements.get(0).getValue("RequestedReads", user))) {
                    System.out.println("sample4ReqReads: " + sample4ReqReads);
                    System.out.println("req reads: " + seqRequirements.get(0).getValue("RequestedReads", user).toString());
                    assertEquals(sample4ReqReads, seqRequirements.get(0).getValue("RequestedReads", user).toString());
                }
                if (!Objects.isNull(seqRequirements.get(0).getValue("CoverageTarget", user))) {
                    assertEquals(sample4Coverage, seqRequirements.get(0).getValue("CoverageTarget", user).toString());
                }
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    //ImmunoSeq
    @Test
    public void ImmunoSeqTest() {
        try {
            readData(user, dataRecordManager, "('07340_B_53')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("07340_B_53")) {
                assertEquals("156/0/0/12", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

   @Test
    public void problem1Test() {
        try {
            readData(user, dataRecordManager, "('05538_AD_2')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05538_AD_2")) {
                assertEquals("PE100", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("30.0", seqRequirements.get(0).getValue("MinimumReads", user).toString());
                assertEquals("40.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    @Test
    public void problem2Test() {
        try {
            readData(user, dataRecordManager, "('06000_IW_3')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("06000_IW_3")) {
                assertEquals("PE50", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("10.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
                assertEquals("5.0", seqRequirements.get(0).getValue("MinimumReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sample04553_L_1() {
        try {
            readData(user, dataRecordManager, "('04553_L_1')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("04553_L_1")) {
                assertEquals("PE100", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("500", seqRequirements.get(0).getValue("CoverageTarget", user).toString());
                assertEquals("14.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sample05257_CT_1() {
        try {
            readData(user, dataRecordManager, "('05257_CT_1')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05257_CT_1")) {
                assertEquals("PE100", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("1000", seqRequirements.get(0).getValue("CoverageTarget", user).toString());
                assertEquals("60.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    //LIMS user selects the capture panel
//    @Test
//    public void sample04553_M_1() {
//        try {
//            readData(user, dataRecordManager, "('04553_M_1')");
//            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer);
//            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("04553_M_1")) {
//                assertEquals("PE100", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
//                assertEquals("150", seqRequirements.get(0).getValue("CoverageTarget", user).toString());
//                assertEquals("95.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
//            }
//        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
//            e.printStackTrace();
//        }
//    }

    //ShallowWGS
    @Test
    public void sample09025_E_1() {
        try {
            readData(user, dataRecordManager, "('09025_E_1')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("09025_E_1")) {
                assertEquals("PE100", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("10.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    //CRISPRseq
    @Test
    public void sample09037_B_1() {
        try {
            readData(user, dataRecordManager, "('09037_B_1')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("09037_B_1")) {
                assertEquals("PE100", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("0.1", seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sample05240_AH_1() {
        try {
            readData(user, dataRecordManager, "('05240_AH_1')");
            seqReqHandler.updateSeqReq(attachedSamples, relatedBankedSampleInfo, seqRequirements, coverageReqRefs, user, dataMgmtServer, logger);
            if (attachedSamples.get(0).getValue("SampleId", user).toString().equals("05240_AH_1")) {
                assertEquals("PE150", seqRequirements.get(0).getValue("SequencingRunType", user).toString());
                assertEquals("40", seqRequirements.get(0).getValue("CoverageTarget", user).toString());
                assertEquals("600.0", seqRequirements.get(0).getValue("RequestedReads", user).toString());
            }
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }
    }
}

