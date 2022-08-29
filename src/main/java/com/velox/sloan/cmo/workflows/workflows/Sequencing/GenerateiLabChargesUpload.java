package com.velox.sloan.cmo.workflows.workflows.Sequencing;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.utilities.ExemplarConfig;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.rmi.RemoteException;
import java.util.*;

public class GenerateiLabChargesUpload extends DefaultGenericPlugin {

    public Map<String, String> serviceInfoMap = new HashMap<>();

    public GenerateiLabChargesUpload() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("GENERATE ILAB CHARGES SHEET") &&
                !this.activeTask.getTask().getTaskOptions().containsKey("GENERATE ILAB CHARGES SHEET GENERATED");
    }

    public PluginResult run() throws Throwable {
        // Illumina Sequencing Workflow last step has FlowCellSamples attached to it, which are pools
        // need to access initial samples and their parent the request to publish: project_id, number of samples, investigator email
        // address, PI email address, Date of request, service_request_id?
        populateServiceInfoMap();
        List<DataRecord> flowCellSamples = activeTask.getAttachedDataRecords("NormalizationPooledLibProtocol", user);
        String serviceType = flowCellSamples.get(0).getParentsOfType("Sample", user).get(0)
                .getStringVal("Recipe", user);
        List<DataRecord> chargesInfo = outputChargesInfo(serviceType);
        setFieldsForReport(chargesInfo);
        generateiLabChargeSheet();
        // Populate different services sheets
        return new PluginResult(true);
    }

    private List<DataRecord> outputChargesInfo(String serviceType) {
        // Logic for charges corresponding to different services

    }
    private void generateiLabChargeSheet() {
        // Make the sheet with 7 columns
        List<String> headerValues;
        List<Map<String, String>> dataValues;
        List<String[]> dataLines = new LinkedList<>();
        String[] headersArray = new String[headerValues.size()];
        int i = 0;
        for (String headerValue : headerValues) {
            headersArray[i++] = headerValue;
        }
        dataLines.add(headersArray);
        i = 0;
        String[] dataInfoArray = new String[headerValues.size()];
        for(Map<String, String> row : dataValues) {
            dataInfoArray[i++] = row.get("note");
            dataInfoArray[i++] = row.get("serviceQuantity");
            dataInfoArray[i++] = row.get("purchasedOn");
            dataInfoArray[i++] = row.get("serviceRequestId");
            dataInfoArray[i++] = row.get("ownerEmail");
            dataInfoArray[i++] = row.get("pIEmail");
            dataLines.add(dataInfoArray);
            dataInfoArray = new String[headerValues.size()];
            i = 0;
        }


        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            File outFile = null;
            StringBuffer allData = new StringBuffer();
            byte[] bytes;
            for (String[] eachLine: dataLines) {
                for (String eachCell : eachLine) {
                    allData.append(eachCell + ",");
                }
                allData.append("\n");
            }
            bytes = allData.toString().getBytes();
            ExemplarConfig exemplarConfig = new ExemplarConfig(managerContext);
            String iLabChargeUpload = exemplarConfig.getExemplarConfigValues().get("").toString();
            //"/pskis34/vialelab/LIMS/iLabBulkUploadCharges"


            try (OutputStream fos = new FileOutputStream(outFile, false)){
                fos.write(bytes);
                outFile.setReadOnly();
                byteStream.close();
            } catch (Exception e) {
                logInfo("Error in writing to shared drive: " + e.getMessage());
            }


        } catch (NotFound e) {
            logError(String.format("NotFoundException -> Error while exporting iLab bulk charge sheet:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (IoError e) {
            logError(String.format("IoError -> Error while exporting iLab bulk charge sheet:\n%s",ExceptionUtils.getStackTrace(e)));
        } catch (ServerException e) {
            logError(String.format("RemoteException -> Error while exporting iLab bulk charge sheet:\n%s",ExceptionUtils.getStackTrace(e)));
        } catch (IOException e) {
            logError(String.format("IOException -> Error while exporting iLab bulk charge sheet:\n%s", ExceptionUtils.getStackTrace(e)));
        } finally {
            try {
                byteStream.close();
            } catch (IOException e) {
                logError(String.format("IOException -> Error while closing the ByteArrayOutputStream:\n%s", ExceptionUtils.getStackTrace(e)));
            }
        }
    }

    private List<Map<String, String>> setFieldsForReport(List<DataRecord> chargesInformation) {
        List<Map<String, String>> reportFieldValueMaps = new ArrayList<>();
        for (DataRecord record : chargesInformation) {
            Map<String, String> reportFieldValues = new HashMap<>();
            try {
                DataRecord requestRecord = record.getParentsOfType("Request", user).get(0);
                String ownerEmail = requestRecord.getStringVal("ProjectOwner", user);
                String piEmail = requestRecord.getStringVal("PIemail", user);
                String requestId = requestRecord.getStringVal("RequestId", user);
                String purchaseDate = requestRecord.getStringVal("RequestDate", user);
                String serviceQuantity = requestRecord.getStringVal("SampleNumber", user);

                reportFieldValues.put("serviceId", );
                reportFieldValues.put("note", requestId);
                reportFieldValues.put("serviceQuantity", serviceQuantity);
                reportFieldValues.put("purchasedOn", purchaseDate);
                reportFieldValues.put("serviceRequestId", );
                reportFieldValues.put("ownerEmail", ownerEmail);
                reportFieldValues.put("pIEmail", piEmail);

                reportFieldValueMaps.add(reportFieldValues);
            } catch (IoError e) {
                logError(String.format("IOError -> Error setting field values for charges sheet:\n%s", ExceptionUtils.getStackTrace(e)));
            } catch (RemoteException e) {
                logError(String.format("RemoteException -> Error setting field values for charges sheet:\n%s", ExceptionUtils.getStackTrace(e)));
            } catch (NotFound notFound) {
                logError(String.format("NotFound Exception -> Error setting field values for charges sheet:\n%s", ExceptionUtils.getStackTrace(notFound)));
            }
        }
        return reportFieldValueMaps;
    }

    // Make the map of Service ID -> Service Name
    public Map<String, String> populateServiceInfoMap() {
        serviceInfoMap.put("490181", "10X FB Library");
        serviceInfoMap.put("490175", "10X GEX Library");

        return serviceInfoMap;
    }

}
