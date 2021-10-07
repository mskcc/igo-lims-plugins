package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;


import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.user.User;
import com.velox.api.util.ClientCallbackOperations;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import com.velox.sloan.cmo.workflows.TestUtils;
import com.velox.util.LogWriter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class BioAnalyzerResultsParserTest {
    ClientCallbackOperations clientCallback;
    PluginLogger logger;
    private IgoLimsPluginUtils utils = new IgoLimsPluginUtils();
    private byte[] byteData;
    private List<String> dataFromFile = new ArrayList<>();
    private BioAnalyzerResultsParser parser;
    private User user;
    private DataRecordManager dataRecordManager;
    private VeloxConnection connection;
    private List<DataRecord> attachedSamples;
    private TestUtils testUtils = new TestUtils();

    @Before
    public void setUp() {
        connection = testUtils.connectServer();
        user = connection.getUser();
        dataRecordManager = connection.getDataRecordManager();
        String sampleIds = "('07566_12_1_1', '07566_13_1_1', '09687_AO_1_1')";
        try {
            attachedSamples = dataRecordManager.queryDataRecords(SampleModel.DATA_TYPE_NAME, SampleModel.SAMPLE_ID + " IN " + sampleIds, user);
        } catch (NotFound | IoError | RemoteException | ServerException notFound) {
            notFound.printStackTrace();
        }
        String fileName = "BioAnalyzer_Test_File_QCResultAnnotation.csv";
        byteData = utils.readCsvFileToBytes(fileName);
        try {
            dataFromFile = utils.readDataFromCsvFile(byteData);
            logger = Mockito.mock(PluginLogger.class);
            clientCallback = Mockito.mock(ClientCallbackOperations.class);
            //private List<String> headerValues = Arrays.asList("Size [bp]","Conc. [pg/�l]","Molarity [pmol/l]",
            //        "Observations","Area","Aligned Migration Time [s]","Peak Height","Peak Width","% of Total","Time corrected area");
            String BIOA_HEADER_IDENTIFIER = "Size [bp]";
            Map<String, Integer> headerValueMap = utils.getBioanalyzerFileHeaderMap(dataFromFile, fileName, BIOA_HEADER_IDENTIFIER, logger);
            parser = new BioAnalyzerResultsParser(dataFromFile, fileName, headerValueMap, clientCallback, logger, user);
        } catch (IOException e) {
            String message = ExceptionUtils.getMessage(e);
            System.out.println(message);
            System.out.println(dataFromFile);
        }
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

//    @Test
//    public void parseData() throws ServerException {
//        assertEquals(parser.parseData(attachedSamples).size(),3 );
//    }
}