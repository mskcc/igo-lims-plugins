package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.user.User;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.api.datarecord.*;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.util.ServerException;
import java.rmi.RemoteException;
import java.util.*;

import java.util.List;
import java.util.Arrays;
import org.junit.Ignore;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

@Ignore
public class FixAvgLibSizeTest {
    //FixAvgLibSize fixAvgLibSize = new FixAvgLibSize();
    User user;
    DataRecordManager dataRecordManager;
    DataMgmtServer dataMgmtServer;
    VeloxConnection connection = null;

    @Before
    public void setUp() {
        try {
            FixAvgLibSize fixAvgLibSize = new FixAvgLibSize();
            connection = new VeloxConnection("/Users/lambed/igo-lims-plugins/Connection.txt");
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

    @Test
    public void testUpdateAvgSize() {
        // Create sample DataRecords for testing
        List<Object> testSamples = new ArrayList<>();
        testSamples.add("sample1");
        testSamples.add("sample2");
        testSamples.add("sample3");

        // Get data records
        try {
            List<DataRecord> QCDatum = fixAvgLibSize.getQcRecordsForSamples(testSamples, "QCDatum");
            List<DataRecord> MCA = fixAvgLibSize.getQcRecordsForSamples(testSamples, "MolarConcentrationAssignment");
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }

        // Modify MCA
        for (DataRecord sampleMCA : MCA) {
            try {
                sampleMCA.setDataField("AvgSize", "500", user);
            } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
                e.printStackTrace();
            }
        }

        //call updateAvgSize
        try {
            fixAvgLibSizeTest.updateAvgSize(QCDatum, MCA);
        } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
            e.printStackTrace();
        }

        // Verify that the AvgSize field is updated as expected
        for (DataRecord sampleQCDatum : QCDatum) {
            try {
                double QCDatumavgsize = sampleQCDatum.getDoubleVal("AvgSize", user);
                assertEquals(500, QCDatumavgsize, 0.001);
            } catch (NotFound | RemoteException | ServerException | IoError | InvalidValue e) {
                e.printStackTrace();
            }
        }
    }
}




