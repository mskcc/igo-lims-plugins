package com.velox.sloan.cmo.workflows.General;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sloan.cmo.workflows.micronics.NewMicronicTubeTareWeightImporter;

import java.rmi.RemoteException;
import java.util.*;

/**
 * A plugin to add IndexBarcode as child record to samples using file upload. This will be used in rare circumstances where DNA Libraries are added
 * to LIMS without creating child record of IndexBarcode type.
 * Created by sharmaa1 on 7/15/19.
 */
public class IndexBarcodeChildRecordGenerator extends DefaultGenericPlugin{
    private String[] permittedUsers = {"Sample Receiving", "Sapio Admin", "Admin"};
    private NewMicronicTubeTareWeightImporter excelFileValidator = new NewMicronicTubeTareWeightImporter();
    public IndexBarcodeChildRecordGenerator() {
        setActionMenu(true);
        setLine1Text("Add Index Barcode");
        setLine2Text("child records");
        setDescription("Add child Index Barcodes via file upload. File should have 'SampleId', IndexId','IndexTag' fields added to the sheet");
        setUserGroupList(permittedUsers);
    }

    @Override
    public PluginResult run() throws ServerException, IoError, RemoteException, NotFound {
        String dataFile = clientCallback.showFileDialog("Upload csv file with Index Barcode Information", null);
        try {
            byte[] byteData = clientCallback.readBytes(dataFile);
            String[] dataInFile = new String(byteData).split("\r\n|\r|\n");
            if (!excelFileValidator.isCsvFile(dataFile)) {
                clientCallback.displayError(String.format("Uploaded file '%s' is not a '.csv' file", dataFile));
                logError(String.format("Uploaded file '%s' is not a '.csv' file", dataFile));
                return new PluginResult(false);
            }

            if (dataInFile.length <= 1) {
                clientCallback.displayError(String.format("Uploaded file '%s' is empty. Please check the file.", dataFile));
                logError(String.format("Uploaded file '%s' is empty. Please check the file.", dataFile));
                return new PluginResult(false);
            }

            List<Map<String, Object>> barcodeRecords = new ArrayList<>();

            for (String row : dataInFile) {
                Map<String, Object> barcodeInfo = new HashMap<>();
                String [] values = row.split(",");
                barcodeInfo.put("SampleId", values[0]);
                barcodeInfo.put("OtherSampleId", values[1]);
                barcodeInfo.put("IndexId", values[2]);
                barcodeInfo.put("IndexTag", values[3]);
                barcodeRecords.add(barcodeInfo);
            }

            List<DataRecord> samples = dataRecordManager.queryDataRecords("Sample", "SampleId", getSampleIds(dataInFile), user);
            logInfo("Total samples: " + samples.toString());
            if (!samples.isEmpty()){
                for (DataRecord sample : samples){
                    String sampleId = sample.getStringVal("SampleId", user);
                    String otherSampleId = sample.getStringVal("OtherSampleId", user);
                    logInfo(sampleId);
                    for (Map<String, Object> barcodeInfo : barcodeRecords){
                        if (sampleId.equals(barcodeInfo.get("SampleId")) && otherSampleId.equals(barcodeInfo.get("OtherSampleId"))){
                            sample.addChild("IndexBarcode", barcodeInfo, user);
                        }
                    }
                }
            }
            dataRecordManager.storeAndCommit("Added child records to samples %s " + getSampleIds(dataInFile).toString(), null, user);

        }catch (Exception e){
            clientCallback.displayError(String.format("Error while while creating Index Barcode records.\n" +
                    "Cause: \n%s", e));
            logError(e);
        }
        return new PluginResult(true);
    }

    private List<Object> getSampleIds(String [] dataInFile){
        List<Object> sampleIds = new ArrayList<>();
        for (String row : dataInFile){
            sampleIds.add(row.split(",")[0]);
        }
        logInfo("Found SampleIds: " + sampleIds.toString());
        return sampleIds;
    }

}
