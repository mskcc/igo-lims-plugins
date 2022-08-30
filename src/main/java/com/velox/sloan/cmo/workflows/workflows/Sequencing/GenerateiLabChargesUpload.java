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

    private static final Map<String, String> serviceInfoMap = new HashMap<>();
    static { // Make the map of Service Name -> Service ID
        serviceInfoMap.put("10X FB Library", "490181");
        serviceInfoMap.put("10X GEX Library", "490175");
        serviceInfoMap.put("10X GEX Sequencing - 10K cells", "490177");
        serviceInfoMap.put("10X GEX Sequencing - 1K cells", "490176");
        serviceInfoMap.put("10X Multiome Library", "490182");
        serviceInfoMap.put("10X Multiome Sequencing - 10K nuclei", "490184");
        serviceInfoMap.put("10X Multiome Sequencing - 1K nuclei", "490183");
        serviceInfoMap.put("10X VDJ Library", "490178");
        serviceInfoMap.put("10X VDJ/FB Sequencing - 10K cells", "490180");
        serviceInfoMap.put("10X VDJ/FB Sequencing - 1K cells", "490179");
        serviceInfoMap.put("10X Visium Library", "490190");
        serviceInfoMap.put("10X Visium Optimization", "490189");
        serviceInfoMap.put("10X Visium Sequencing (25%)", "490191");
        serviceInfoMap.put("ACCESS - Normal", "406820");
        serviceInfoMap.put("ACCESS - Tumor", "406821");
        serviceInfoMap.put("Adaptive immunoSEQ - Deep", "490139");
        serviceInfoMap.put("Adaptive immunoSEQ - Survey", "490138");
        serviceInfoMap.put("Adaptive immunoSEQ - Ultradeep", "490504");
        serviceInfoMap.put("AmpliconSeq", "490510");
        serviceInfoMap.put("Archer Fusion - Heme Panel", "334267");
        serviceInfoMap.put("Archer Fusion - Solid Tumor (MSK) Panel", "334266");
        serviceInfoMap.put("Archer Immunoverse", "490140");
        serviceInfoMap.put("ATAC Library Prep", "490513"); // IGO Internal
        serviceInfoMap.put("ATAC-Seq", "483257");
        serviceInfoMap.put("Cell Line Authentication", "490142");
        serviceInfoMap.put("cfDNA Extraction - Plasma", "261860");
        serviceInfoMap.put("ChIP-Seq/CUT&RUN", "483258");
        serviceInfoMap.put("CMO-CH", "492855");
        serviceInfoMap.put("CRISPR-Seq", "308754");
        serviceInfoMap.put("Data Analysis - ACCESS (N)", "495935");
        serviceInfoMap.put("Data Analysis - ACCESS (T)", "495936");
        serviceInfoMap.put("Data Analysis - CMO-CH", "495937");
        serviceInfoMap.put("Data Handling", "491618"); // IGO Internal
        serviceInfoMap.put("ddPCR (1 reaction)", "256041");
        serviceInfoMap.put("ddPCR Assay Design", "288524");
        serviceInfoMap.put("ddPCR Assay Order - CNV", "290735");
        serviceInfoMap.put("ddPCR Assay Order - Mutation/GEX", "288525");
        serviceInfoMap.put("ddPCR Human % Assay", "490143");
        serviceInfoMap.put("ddPCR KRAS Multiplexing", "337962");
        serviceInfoMap.put("DLP Library - 800 cells", "490187");
        serviceInfoMap.put("DLP Sequencing - 1 quadrant", "490188");
        serviceInfoMap.put("DNA Extraction - Blood", "256034");
        serviceInfoMap.put("DNA Extraction - FFPE", "256048");
        serviceInfoMap.put("DNA Extraction - Fresh/Frozen", "256043");
        serviceInfoMap.put("DNA Extraction - Nails", "288528");
        serviceInfoMap.put("DNA Extraction - Viably Frozen", "490136");
        serviceInfoMap.put("DNA/RNA Dual Extraction", "256092");
        serviceInfoMap.put("Double Capture", "497933"); // IGO Internal
        serviceInfoMap.put("EPIC Methyl Capture", "483259");
        serviceInfoMap.put("FFPE Sectioning - Curls", "260306");
        serviceInfoMap.put("FFPE Sectioning - Slides", "260305");
        serviceInfoMap.put("Fingerprinting - STR", "302835");
        serviceInfoMap.put("H&E Stain", "260304");
        serviceInfoMap.put("HemePACT - Normal", "259603");
        serviceInfoMap.put("HemePACT - Tumor", "406819");
        serviceInfoMap.put("IMPACT - Mouse", "331388");
        serviceInfoMap.put("IMPACT - Normal", "256124");
        serviceInfoMap.put("IMPACT - Tumor", "406813");
        serviceInfoMap.put("KAPA HT Library Prep", "256127");
        serviceInfoMap.put("KAPA Hyper Library Prep", "351941");
        serviceInfoMap.put("KAPA WGS Library Prep - PCR+", "490516");
        serviceInfoMap.put("KAPA WGS Library Prep - PCR-free", "490515");
        serviceInfoMap.put("Micronic Tube", "308755");
        serviceInfoMap.put("PlateSeq Library Prep", "490185");
        serviceInfoMap.put("PlateSeq Sequencing - 1 column", "490186");
        serviceInfoMap.put("PolyA Library Prep", "490511");
        serviceInfoMap.put("QC - Agilent", "256044");
        serviceInfoMap.put("QC - Quant-it", "259492");
        serviceInfoMap.put("QC - Quantity + Quality", "256029");
        serviceInfoMap.put("RiboDepletion Library Prep", "490512");
        serviceInfoMap.put("490141", "RNA Extraction + COVID19 Testing");
        serviceInfoMap.put("256100", "RNA Extraction - FFPE");
        serviceInfoMap.put("256097", "RNA Extraction - Fresh/Frozen");
        serviceInfoMap.put("490137", "RNA Extraction - Viably Frozen");
        serviceInfoMap.put("490506", "RNASeq - polyA - 10-20M");
        serviceInfoMap.put("490507", "RNASeq - polyA - 100M+");
        serviceInfoMap.put("404330", "RNASeq - polyA - 20-30M");
        serviceInfoMap.put("404331", "RNASeq - polyA - 30-40M");
        serviceInfoMap.put("487566", "RNASeq - polyA - 40-50M");
        serviceInfoMap.put("404332", "RNASeq - polyA - 50-60M");
        serviceInfoMap.put("490144", "RNASeq - polyA - 60-80M");
        serviceInfoMap.put("404334", "RNASeq - polyA - 80-100M");
        serviceInfoMap.put("490508", "RNASeq - Ribodeplete - 10-20M");
        serviceInfoMap.put("490509", "RNASeq - Ribodeplete - 100M+");
        serviceInfoMap.put("490145", "RNASeq - Ribodeplete - 20-30M");
        serviceInfoMap.put("404335", "RNASeq - Ribodeplete - 30-40M");
        serviceInfoMap.put("490146", "RNASeq - Ribodeplete - 40-50M");
        serviceInfoMap.put("404336", "RNASeq - Ribodeplete - 50-60M");
        serviceInfoMap.put("404337", "RNASeq - Ribodeplete - 60-80M");
        serviceInfoMap.put("404338", "RNASeq - Ribodeplete - 80-100M");
        serviceInfoMap.put("490514", "Sample Capture + Library");
        serviceInfoMap.put("491619", "Sample Pooling");
        serviceInfoMap.put("490157", "Sequencing  - 100M Reads - 150c");
        serviceInfoMap.put("490158", "Sequencing - 100M Reads - 300c");
        serviceInfoMap.put("490149", "Sequencing - 10M Reads - 10X Standard");
        serviceInfoMap.put("490147", "Sequencing - 10M Reads - PE100");
        serviceInfoMap.put("490148", "Sequencing - 10M Reads - PE150");
        serviceInfoMap.put("490173", "Sequencing - 11000M Reads - 200c");
        serviceInfoMap.put("490174", "Sequencing - 11000M Reads - 300c");
        serviceInfoMap.put("490165", "Sequencing - 1800M Reads - 100c");
        serviceInfoMap.put("490166", "Sequencing - 1800M Reads - 200c");
        serviceInfoMap.put("490167", "Sequencing - 1800M Reads - 300c");
        serviceInfoMap.put("490170", "Sequencing - 3600M Reads - 100c");
        serviceInfoMap.put("490171", "Sequencing - 3600M Reads - 200c");
        serviceInfoMap.put("490172", "Sequencing - 3600M Reads - 300c");
        serviceInfoMap.put("490159", "Sequencing - 400M Reads - 100c");
        serviceInfoMap.put("490160", "Sequencing - 400M Reads - 200c");
        serviceInfoMap.put("490161", "Sequencing - 400M Reads - 300c");
        serviceInfoMap.put("490162", "Sequencing - 800M Reads - 100c");
        serviceInfoMap.put("490163", "Sequencing - 800M Reads - 200c");
        serviceInfoMap.put("490164", "Sequencing - 800M Reads - 300c");
        serviceInfoMap.put("494610", "Sequencing - 800M Reads - 500c");
        serviceInfoMap.put("490154", "Sequencing - MiSeq 150c");
        serviceInfoMap.put("490155", "Sequencing - MiSeq 300c");
        serviceInfoMap.put("490153", "Sequencing - MiSeq 50c");
        serviceInfoMap.put("490156", "Sequencing - MiSeq 600c");
        serviceInfoMap.put("490152", "Sequencing - MiSeq Micro 300c");
        serviceInfoMap.put("490150", "Sequencing - MiSeq Nano 300c");
        serviceInfoMap.put("490151", "Sequencing - MiSeq Nano 500c");
        serviceInfoMap.put("341254", "Shallow WGS");
        serviceInfoMap.put("260643", "Slide Dissection");
        serviceInfoMap.put("296697", "Slide Scraping");
        serviceInfoMap.put("261859", "SMARTer Amplification");
        serviceInfoMap.put("487571", "Special Processing -- Extraction");
        serviceInfoMap.put("498671", "TCRSeq-IGO");
        serviceInfoMap.put("351940", "UMI Library Prep");
        serviceInfoMap.put("289981", "WES - 100X");
        serviceInfoMap.put("289982", "WES - 150X");
        serviceInfoMap.put("289983", "WES - 200X");
        serviceInfoMap.put("289984", "WES - 250X");
        serviceInfoMap.put("289979", "WES - 30X");
        serviceInfoMap.put("289980", "WES - 70X");
        serviceInfoMap.put("490204", "WGS - PCR+ - 100X");
        serviceInfoMap.put("495934", "WGS - PCR+ - 10X");
        serviceInfoMap.put("490205", "WGS - PCR+ - 150X");
        serviceInfoMap.put("490199", "WGS - PCR+ - 30X");
        serviceInfoMap.put("490200", "WGS - PCR+ - 40X");
        serviceInfoMap.put("490201", "WGS - PCR+ - 50X");
        serviceInfoMap.put("490202", "WGS - PCR+ - 60X");
        serviceInfoMap.put("490203", "WGS - PCR+ - 80X");
        serviceInfoMap.put("490197", "WGS - PCR-free - 100X");
        serviceInfoMap.put("495933", "WGS - PCR-free - 10X");
        serviceInfoMap.put("490198", "WGS - PCR-free - 150X");
        serviceInfoMap.put("490192", "WGS - PCR-free - 30X");
        serviceInfoMap.put("490193", "WGS - PCR-free - 40X");
        serviceInfoMap.put("490194", "WGS - PCR-free - 50X");
        serviceInfoMap.put("490195", "WGS - PCR-free - 60X");
        serviceInfoMap.put("490196", "WGS - PCR-free - 80X");

    }

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
}
