package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import java.util.List;
import java.util.Arrays;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.api.workflow.ActiveTask;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import javax.xml.crypto.Data;
import java.rmi.RemoteException;
import java.util.*;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class FixAvgLibSizeTest {
    private FixAvgLibSize fixAvgLibSize;
    User user;
    DataRecordManager dataRecordManager;
    DataMgmtServer dataMgmtServer;
    PluginLogger logger;
    VeloxConnection connection = null;

    @Before
    public void setUp() {
        fixAvgLibSize = new FixAvgLibSize();
        user = connection.getUser();
        dataRecordManager = connection.getDataRecordManager();
        dataMgmtServer = connection.getDataMgmtServer();
        connection = new VeloxConnection("/Users/lambed/igo-lims-plugins/Connection.txt");
        System.out.println("Connection start");
        connection.open();
        System.out.println("Connected successfully.");
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
        List<Object> testSamples = Arrays.asList("sample1", "sample2", "sample3");

        // Get data records
        try {
            List<DataRecord> QCDatum = FixAvgLibSize.getQcRecordsForSamples(testSamples, "QCDatum");
            List<DataRecord> MCA = FixAvgLibSize.getQcRecordsForSamples(testSamples, "MolarConcentrationAssignment");
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
            FixAvgLibSizeTest.updateAvgSize(QCDatum, MCA);
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









