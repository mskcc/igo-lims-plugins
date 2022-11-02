package com.velox.sloan.cmo.workflows.workflows.Sequencing;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.text.SimpleDateFormat;
import java.util.*;

public class GenerateiLabChargesUpload extends DefaultGenericPlugin {
    private List<String> headerValues = Arrays.asList("service_id", "note", "service_quantity", "purchased_on",
            "service_request_id", "owner_email", "pi_email_or_group_id", "payment_number");
    public List<Map<String, String>> dataValues = new LinkedList<>();

    private static final Map<String, String> serviceInfoMap = new HashMap<>(); // or reading from the file on iLabs in case of any update
    static { // Make the map of Service Name -> Service ID
        //QC
        serviceInfoMap.put("QC - Agilent", "256044");
        serviceInfoMap.put("QC - Quant-it", "259492");
        serviceInfoMap.put("QC - Quantity + Quality", "256029");
        // DDPCR Non-Sequencing
        serviceInfoMap.put("ddPCR (1 reaction)", "256041");
        serviceInfoMap.put("ddPCR Assay Design", "288524");
        serviceInfoMap.put("ddPCR Assay Order - CNV/GEX", "290735");
        serviceInfoMap.put("ddPCR Assay Order - Mutation", "288525");
        serviceInfoMap.put("ddPCR Human % Assay", "490143");
        serviceInfoMap.put("ddPCR KRAS Multiplexing", "337962");
        // CellLine Aut (STR) Non-Sequencing
        serviceInfoMap.put("Cell Line Authentication", "490142");
        serviceInfoMap.put("Fingerprinting - STR", "302835");

        serviceInfoMap.put("GeoMx Experiment Optimization", "504495");
        serviceInfoMap.put("GeoMx Library", "504496");
        serviceInfoMap.put("GeoMx Slide Prep", "504497");
        // Data Analysis
        serviceInfoMap.put("Data Analysis - ACCESS (N)", "495935");
        serviceInfoMap.put("Data Analysis - ACCESS (T)", "495936");
        serviceInfoMap.put("Data Analysis - CMO-CH", "495937");

        // Library Prep
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
        serviceInfoMap.put("10X Visium Optimization", "490189"); // What property is "Optimization"?
        serviceInfoMap.put("10X Visium Sequencing (Frozen)", "490191");
        serviceInfoMap.put("10X Visium Sequencing (FFPE)", "504489");


        serviceInfoMap.put("ACCESS - Normal", "406820");
        serviceInfoMap.put("ACCESS - Tumor", "406821");
        serviceInfoMap.put("AmpliconSeq", "490510");

        serviceInfoMap.put("Archer Fusion - Heme Panel", "334267");
        serviceInfoMap.put("Archer Fusion - Solid Tumor (MSK) Panel", "334266");
        serviceInfoMap.put("Archer Immunoverse", "490140");

        serviceInfoMap.put("ATAC-Seq", "483257");

        serviceInfoMap.put("cfDNA Extraction", "261860");

        serviceInfoMap.put("ChIP-Seq/CUT&RUN", "483258");

        serviceInfoMap.put("CMO-CH", "492855");

        serviceInfoMap.put("CRISPR-Seq", "308754");
        serviceInfoMap.put("Custom Fragment Analysis", "504490");

        serviceInfoMap.put("DLP Library - 800 cells", "490187");
        serviceInfoMap.put("DLP Sequencing - 800 cells", "504524");

        serviceInfoMap.put("DNA Extraction - Oxford Nanopore", "504492");
        serviceInfoMap.put("DNA Extraction", "504491");
        serviceInfoMap.put("DNA Extraction - FFPE", "256048");
        serviceInfoMap.put("DNA/RNA Dual Extraction", "256092");
        serviceInfoMap.put("DNA/RNA Dual Extraction - FFPE", "504493");

        serviceInfoMap.put("EPIC Methyl Capture", "483259"); // request name: MethylSeq?

        serviceInfoMap.put("FFPE Sectioning", "260306");

        serviceInfoMap.put("H&E Stain", "260304");

        serviceInfoMap.put("HemePACT - Normal", "259603");
        serviceInfoMap.put("HemePACT - Tumor", "406819");

        serviceInfoMap.put("IMPACT - Mouse", "331388");
        serviceInfoMap.put("IMPACT - Normal", "256124");
        serviceInfoMap.put("IMPACT - Tumor", "406813");

        serviceInfoMap.put("Micronic Tube", "308755"); // Where is it included? could be ignored

        serviceInfoMap.put("PlateSeq Library Prep", "490185");
        serviceInfoMap.put("PlateSeq Sequencing - 1 column", "490186");

        serviceInfoMap.put("PolyA Library Prep", "490511");

        serviceInfoMap.put("RiboDepletion Library Prep", "490512");

        serviceInfoMap.put("RNA Extraction", "504499");
        serviceInfoMap.put("RNA Extraction - FFPE", "256100");

        serviceInfoMap.put("RNASeq - polyA - 10-20M", "490506");
        serviceInfoMap.put("RNASeq - polyA - 100M+", "490507");
        serviceInfoMap.put("RNASeq - polyA - 20-30M", "404330");
        serviceInfoMap.put("RNASeq - polyA - 30-40M", "404331");
        serviceInfoMap.put("RNASeq - polyA - 40-50M", "487566");
        serviceInfoMap.put("RNASeq - polyA - 50-60M", "404332");
        serviceInfoMap.put("RNASeq - polyA - 60-80M", "490144");
        serviceInfoMap.put("RNASeq - polyA - 80-100M", "404334");
        serviceInfoMap.put("RNASeq - Ribodeplete - 10-20M", "490508");
        serviceInfoMap.put("RNASeq - Ribodeplete - 100M+", "490509");
        serviceInfoMap.put("RNASeq - Ribodeplete - 20-30M", "490145");
        serviceInfoMap.put("RNASeq - Ribodeplete - 30-40M", "404335");
        serviceInfoMap.put("RNASeq - Ribodeplete - 40-50M", "490146");
        serviceInfoMap.put("RNASeq - Ribodeplete - 50-60M", "404336");
        serviceInfoMap.put("RNASeq - Ribodeplete - 60-80M", "404337");
        serviceInfoMap.put("RNASeq - Ribodeplete - 80-100M", "404338");

        serviceInfoMap.put("RNASeq - SMARTer - 10-20M", "504501");
        serviceInfoMap.put("RNASeq - SMARTer - 100M+", "504500");
        serviceInfoMap.put("RNASeq - SMARTer - 20-30M", "504502");
        serviceInfoMap.put("RNASeq - SMARTer - 30-40M", "504503");
        serviceInfoMap.put("RNASeq - SMARTer - 40-50M", "504504");
        serviceInfoMap.put("RNASeq - SMARTer - 50-60M", "504506");
        serviceInfoMap.put("RNASeq - SMARTer - 60-80M", "504507");
        serviceInfoMap.put("RNASeq - SMARTer - 80-100M", "504509");


        // Sequencing Only
        serviceInfoMap.put("Sequencing  - 100M Reads - 150c", "490157");
        serviceInfoMap.put("Sequencing - 100M Reads - 300c", "490158");
        serviceInfoMap.put("Sequencing - 10M Reads - 10X Standard", "490149");
        serviceInfoMap.put("Sequencing - 10M Reads - PE100", "490147");
        serviceInfoMap.put("Sequencing - 10M Reads - PE150", "490148");
        serviceInfoMap.put("Sequencing - 11000M Reads - 200c", "490173"); // What is 200c? cycle, seq req: read length
        serviceInfoMap.put("Sequencing - 11000M Reads - 300c", "490174");
        serviceInfoMap.put("Sequencing - 1800M Reads - 100c", "490165");
        serviceInfoMap.put("Sequencing - 1800M Reads - 200c", "490166");
        serviceInfoMap.put("Sequencing - 1800M Reads - 300c", "490167");
        serviceInfoMap.put("Sequencing - 3600M Reads - 100c", "490170");
        serviceInfoMap.put("Sequencing - 3600M Reads - 200c", "490171");
        serviceInfoMap.put("Sequencing - 3600M Reads - 300c", "490172");
        serviceInfoMap.put("Sequencing - 400M Reads - 100c", "490159");
        serviceInfoMap.put("Sequencing - 400M Reads - 200c", "490160");
        serviceInfoMap.put("Sequencing - 400M Reads - 300c", "490161");
        serviceInfoMap.put("Sequencing - 800M Reads - 100c", "490162");
        serviceInfoMap.put("Sequencing - 800M Reads - 200c", "490163");
        serviceInfoMap.put("Sequencing - 800M Reads - 300c", "490164");
        serviceInfoMap.put("Sequencing - 800M Reads - 500c", "494610");
        serviceInfoMap.put("Sequencing - MiSeq 150c", "490154");
        serviceInfoMap.put("Sequencing - MiSeq 300c", "490155");
        serviceInfoMap.put("Sequencing - MiSeq 50c", "490153");
        serviceInfoMap.put("Sequencing - MiSeq 600c", "490156");
        serviceInfoMap.put("Sequencing - MiSeq Micro 300c", "490152");
        serviceInfoMap.put("Sequencing - MiSeq Nano 300c", "490150");
        serviceInfoMap.put("Sequencing - MiSeq Nano 500c", "490151");
        // End of Sequencing Only

        serviceInfoMap.put("Shallow WGS", "341254");

        serviceInfoMap.put("Slide Dissection", "260643");
        serviceInfoMap.put("Slide Scraping", "296697");

        serviceInfoMap.put("SMARTer Amplification", "261859");

        serviceInfoMap.put("TCRSeq-IGO", "498671");

        serviceInfoMap.put("WES - FFPE - 100X", "289981");
        serviceInfoMap.put("WES - FFPE - 150X", "289982");
        serviceInfoMap.put("WES - FFPE - 200X", "289983");
        serviceInfoMap.put("WES - FFPE - 250X", "289984");
        serviceInfoMap.put("WES - FFPE - 30X", "289979");
        serviceInfoMap.put("WES - FFPE - 70X", "289980");

        serviceInfoMap.put("WES - Frozen - 100X", "504515");
        serviceInfoMap.put("WES - Frozen - 150X", "504516");
        serviceInfoMap.put("WES - Frozen - 200X", "504517");
        serviceInfoMap.put("WES - Frozen - 250X", "504518");
        serviceInfoMap.put("WES - Frozen - 30X", "504519");
        serviceInfoMap.put("WES - Frozen - 70X", "504520");

        serviceInfoMap.put("WGS - PCR+ - 100X", "490204");
        serviceInfoMap.put("WGS - PCR+ - 10X", "495934");
        serviceInfoMap.put("WGS - PCR+ - 120X", "490205");
        serviceInfoMap.put("WGS - PCR+ - 30X", "490199");
        serviceInfoMap.put("WGS - PCR+ - 40X", "490200");
        serviceInfoMap.put("WGS - PCR+ - 50X", "490201");
        serviceInfoMap.put("WGS - PCR+ - 60X", "490202");
        serviceInfoMap.put("WGS - PCR+ - 70X", "504521");
        serviceInfoMap.put("WGS - PCR+ - 80X", "490203");
        serviceInfoMap.put("WGS - PCR-free - 100X", "490197");
        serviceInfoMap.put("WGS - PCR-free - 10X", "495933");
        serviceInfoMap.put("WGS - PCR-free - 120X", "490198");
        serviceInfoMap.put("WGS - PCR-free - 30X", "490192");
        serviceInfoMap.put("WGS - PCR-free - 40X", "490193");
        serviceInfoMap.put("WGS - PCR-free - 50X", "490194");
        serviceInfoMap.put("WGS - PCR-free - 60X", "490195");
        serviceInfoMap.put("WGS - PCR-free - 70X", "504522");
        serviceInfoMap.put("WGS - PCR-free - 80X", "490196");

    }

    public GenerateiLabChargesUpload() {
        setTaskEntry(true);
        setOrder(PluginOrder.LAST.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("GENERATE ILAB CHARGES SHEET") &&
                !this.activeTask.getTask().getTaskOptions().containsKey("ILAB CHARGES SHEET GENERATED");
    }

    /**
     * At any run we are processing charges for ALL requests for which their samples are present in the flowcell we are
     * looking at, at the Illumina sequencing workflow
     * */
    public PluginResult run() throws Throwable {
        // Illumina Sequencing Workflow last step has FlowCellSamples attached to it, which are pools
        List<DataRecord> flowCellPools = activeTask.getAttachedDataRecords("NormalizationPooledLibProtocol", user);
        logInfo("Step attachments found.");
        // On igo-lims04 I checked sample for normalization of pooled libraries datatype allowable parent
        Set<String> uniqueRequestsOnTheFlowCell = new HashSet<>();
        if (flowCellPools != null && flowCellPools.size() > 0) {
            for (DataRecord eachPool : flowCellPools) {
                List<DataRecord> samplesOfEachPool = eachPool.getAncestorsOfType("Sample", user);
                for (DataRecord sampleOfAPool : samplesOfEachPool) {
                    List<DataRecord> requests = sampleOfAPool.getParentsOfType("Request", user);
                    if(requests != null && requests.size() > 0) {
                        for (DataRecord request : requests) {
                        // skipping the loop, assuming every sample is in only 1 request
                            DataRecord firstSampleOfEachRequest = request.getChildrenOfType("Sample", user)[0];
                            String requestId = request.getStringVal("RequestId", user);
                            logInfo("request id is: " + requestId);
                            String iLabServiceRequestId = request.getStringVal("iLabServiceRequestId", user);
                            logInfo("iLabServiceRequestId is: " + iLabServiceRequestId);
                            // Below: checking for processing every request only once and if whether there is an iLab form filled out for the request
                            if (uniqueRequestsOnTheFlowCell.add(requestId) && iLabServiceRequestId != null &&
                                    !iLabServiceRequestId.trim().equals("")) {
                                dataValues = outputChargesInfo(firstSampleOfEachRequest);
                                if(dataValues.size() > 0) {
                                    generateiLabChargeSheet();
                                }
                            }
                            dataValues.clear();
                        }
                    }
                }
            }
        }
        this.activeTask.getTask().getTaskOptions().put("ILAB CHARGES SHEET GENERATED", "");
        return new PluginResult(true);
    }

    /**
     * Logic for charges corresponding to different service types
     * @param firstSample
     * @return all iLab template sheet information for the bulk charge upload
     * */
    private List<Map<String, String>> outputChargesInfo(DataRecord firstSample) {

        List<Map<String, String>> chargeInfoRecords = new LinkedList<>();
        try {
            // Request level information
            logInfo("First sample igo id is: " + firstSample.getDataField("SampleId", user));
            DataRecord requestRecord = firstSample.getParentsOfType("Request", user).get(0);
            logInfo("request project id is: " + requestRecord.getDataField("RequestId", user));
            String serviceType = requestRecord.getStringVal("RequestName", user);
            logInfo("service type is: " + serviceType);
            String requestName = requestRecord.getStringVal("RequestName", user);
            String ownerEmail = requestRecord.getStringVal("Investigatoremail", user);
            String piEmail = requestRecord.getStringVal("LabHeadEmail", user);
            String requestId = requestRecord.getStringVal("iLabServiceRequestId", user);
            logInfo("ilab service req id = " + requestId);
            Long purchaseDate = requestRecord.getDateVal("RequestDate", user);
            String serviceQuantity = requestRecord.getDataField("SampleNumber", user).toString();
            // Sample level information
            String species = firstSample.getStringVal("Species", user);
            String preservation = firstSample.getStringVal("Preservation", user);
            String tumorOrNormal = firstSample.getStringVal("TumorOrNormal", user);
            String assay = firstSample.getStringVal("Assay", user);
            String origin = firstSample.getStringVal("SampleOrigin", user);
            String sampleType = firstSample.getStringVal("ExemplarSampleType", user);
            String recipe = firstSample.getStringVal("Recipe", user);

            // Sequencing Requirements: on igo-lims04 I checked sample as allowable parent for sequencing requirement datatype
            DataRecord [] seqRequeirements = firstSample.getChildrenOfType("SeqRequirementPooled", user);
            logInfo("seqRequeirements size is: " + seqRequeirements.length);
            String numOfReads = "";
            String maxNumOfReads = "";
            String SequencingRunType = "";
            int coverage = 0;
            if (seqRequeirements.length > 0) {
                numOfReads = seqRequeirements[0].getDataField("RequestedReads", user).toString();
                maxNumOfReads = numOfReads.substring(0, numOfReads.length() - 2);
                logInfo("maxNumOfReads = " + maxNumOfReads);
                logInfo("seqRequeirements length is: " + seqRequeirements.length);
                if (seqRequeirements[0].getDataField("CoverageTarget", user) != null) {
                    logInfo("seqRequeirements[0] CoverageTarget is: " + seqRequeirements[0].getDataField(
                            "CoverageTarget", user).toString());
                    coverage = seqRequeirements[0].getIntegerVal("CoverageTarget", user);
                    SequencingRunType = seqRequeirements[0].getDataField("SequencingRunType", user).toString();
                }
            }
            //DDPCR: DdPcrProtocol2 is a potential child of sample: to be marked in LIMS
            DataRecord[] ddpcrProtocol2s = firstSample.getChildrenOfType("DdPcrProtocol2", user);
            int numOfReplicates = 0;
            for (DataRecord ddpcrProtocol2 : ddpcrProtocol2s) {
                numOfReplicates += ddpcrProtocol2.getIntegerVal("NumberOfReplicates", user);
            }
            DataRecord[] ddpcrProtocol1s = firstSample.getChildrenOfType("DdPcrProtocol1", user);
            String Ch1Target = "";
            if (ddpcrProtocol1s.length > 0) {
                Ch1Target = ddpcrProtocol1s[0].getStringVal("Ch1Target", user);
            }

            // Pathology info
            // PathologyProtocol1 is a potential child of sample: to be marked in LIMS
            DataRecord[] pathRecords = firstSample.getChildrenOfType("PathologyProtocol1", user);
            Boolean scrappingCurls = false;
            Boolean microdissection = false;
            Boolean HERequired = false;
            Boolean SectioningRequired = false;
            if (pathRecords.length > 0) {
                scrappingCurls = pathRecords[0].getBooleanVal("ScrapingCurlsRequired", user);
                microdissection = pathRecords[0].getBooleanVal("MicrodissectionRequired", user);
                HERequired = pathRecords[0].getBooleanVal("HERequired", user);
                SectioningRequired = pathRecords[0].getBooleanVal("SectioningRequired", user);
            }

            DataRecord[] wholeGenomeLib = firstSample.getChildrenOfType("WholeGenomeLibProtocol3", user);
            String pcrCycles = "";
            if (wholeGenomeLib.length > 0) {
                pcrCycles = wholeGenomeLib[0].getStringVal("NumberPCRCycles", user);
            }

            // Request name in Request table is a drop down menu with certain options
            String serviceId;
            Map<String, String> chargesFieldValues;
            List<String> requestsSeviceIds = new LinkedList<>();


            if(serviceType.equals("DNAExtraction")) {
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                if (sampleType.toLowerCase().contains("cfdna")) {
                    requestsSeviceIds.add(serviceInfoMap.get("cfDNA Extraction - Plasma"));
                } else if (preservation.toLowerCase().contains("ffpe")) {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - FFPE"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction"));
                }
            }
            if(serviceType.equals("RNAExtraction")) {
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                requestsSeviceIds.add(serviceInfoMap.get("RNA Extraction - FFPE"));
            }

            if(serviceType.equals("DNA/RNASimultaneous")) {
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                if (preservation.toLowerCase().contains("ffpe")) {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA/RNA Dual Extraction - FFPE"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA/RNA Dual Extraction"));
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }

            }
            if(serviceType.equals("PATH-DNAExtraction")) {
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                if (HERequired) {
                    requestsSeviceIds.add(serviceInfoMap.get("H&E Stain"));
                }
                if (SectioningRequired) {
                    requestsSeviceIds.add(serviceInfoMap.get("FFPE Sectioning"));
                }
                if (scrappingCurls) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Scraping"));
                }
                if (microdissection) {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - FFPE"));
                }

//                if (sampleType.toLowerCase().contains("cfdna")) {
//                    requestsSeviceIds.add(serviceInfoMap.get("cfDNA Extraction - Plasma"));
//                } else if (preservation.toLowerCase().contains("ffpe")) {
//                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - FFPE"));
//                }
//                else {
//                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - Viably Frozen"));
//                }
            }
            if(serviceType.equals("PATH-RNAExtraction")) {
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                if (HERequired) {
                    requestsSeviceIds.add(serviceInfoMap.get("H&E Stain"));
                }
                if (SectioningRequired) {
                    requestsSeviceIds.add(serviceInfoMap.get("FFPE Sectioning"));
                }
                if (scrappingCurls) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Scraping"));
                }
                if (microdissection) {
                    requestsSeviceIds.add(serviceInfoMap.get("RNA Extraction - FFPE"));
                }
            }
            if(serviceType.equals("PATH-DNA/RNASimultaneous")) {
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                if (HERequired) {
                    requestsSeviceIds.add(serviceInfoMap.get("H&E Stain"));
                }
                if (SectioningRequired) {
                    requestsSeviceIds.add(serviceInfoMap.get("FFPE Sectioning"));
                }
                if (scrappingCurls) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Scraping"));
                }
                if (microdissection) {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA/RNA Extraction - FFPE"));
                }
            }
            if(serviceType.equals("IMPACT505")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));
                }
                if (firstSample.getChildrenOfType("QcReportLibrary", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                if (tumorOrNormal.toLowerCase().contains("normal")) {
                    requestsSeviceIds.add(serviceInfoMap.get("IMPACT - Normal"));
                }
                else {
                    serviceId = serviceInfoMap.get("IMPACT - Tumor");
                    requestsSeviceIds.add(serviceId);
                }
            }
            if(serviceType.equals("M-IMPACT")) {
                requestsSeviceIds.add(serviceInfoMap.get("IMPACT - Mouse"));
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
            }
            if(serviceType.equals("HemePACT_v4")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));
                }
                else if (firstSample.getChildrenOfType("QcReportLibrary", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                if (tumorOrNormal.toLowerCase().contains("tumor")) {
                    requestsSeviceIds.add(serviceInfoMap.get("HemePACT - Tumor"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("HemePACT - Normal"));
                }
            }
            if(serviceType.equals("MSK-ACCESS_v1")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));
                }
                if (firstSample.getChildrenOfType("QcReportLibrary", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                if (tumorOrNormal.toLowerCase().contains("tumor")) {
                    requestsSeviceIds.add(serviceInfoMap.get("ACCESS - Tumor"));
                    requestsSeviceIds.add(serviceInfoMap.get("Data Analysis - ACCESS (T)"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("ACCESS - Normal"));
                    requestsSeviceIds.add(serviceInfoMap.get("Data Analysis - ACCESS (N)"));
                }
            }
            if(serviceType.equals("RNASeq-TruSeqPolyA")) { // seq req might be under source sample id
                if (firstSample.getChildrenOfType("QcReportRna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                requestsSeviceIds.add(serviceInfoMap.get("PolyA Library Prep"));
                if (!maxNumOfReads.equals("")) {
                    if (Integer.parseInt(maxNumOfReads) <= 20) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 10-20M");
                    }
                    else if (Integer.parseInt(maxNumOfReads) <= 30) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 20-30M");
                    }
                    else if (Integer.parseInt(maxNumOfReads) <= 40) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 30-40M");
                    }
                    else if (Integer.parseInt(maxNumOfReads) <= 50) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 40-50M");
                    }
                    else if (Integer.parseInt(maxNumOfReads) <= 60) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 50-60M");
                    }
                    else if (Integer.parseInt(maxNumOfReads) <= 80) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 60-80M");
                    }
                    else if (Integer.parseInt(maxNumOfReads) <= 100) {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 80-100M");
                    }
                    else {
                        serviceId = serviceInfoMap.get("RNASeq - polyA - 100M+");
                    }
                    requestsSeviceIds.add(serviceId);
                }
            }
            if(serviceType.equals("RNASeq-TruSeqRiboDeplete")) {
                if (firstSample.getChildrenOfType("QcReportRna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                requestsSeviceIds.add(serviceInfoMap.get("RiboDepletion Library Prep"));
                if (!maxNumOfReads.equals("")) {
                    if (Integer.parseInt(maxNumOfReads) <= 20) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 10-20M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 30) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 20-30M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 40) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 30-40M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 50) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 40-50M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 60) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 50-60M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 80) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 60-80M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 100) {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 80-100M");
                    } else {
                        serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 100M+");
                    }
                    requestsSeviceIds.add(serviceId);
                }
            }
            if(serviceType.equals("RNASeq-SMARTerAmp")) {
                if (firstSample.getChildrenOfType("QcReportRna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                requestsSeviceIds.add(serviceInfoMap.get("SMARTer Amplification"));
                if (!maxNumOfReads.equals("")) {
                    if (Integer.parseInt(maxNumOfReads) <= 20) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 10-20M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 30) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 20-30M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 40) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 30-40M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 50) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 40-50M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 60) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 50-60M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 80) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 60-80M");
                    } else if (Integer.parseInt(maxNumOfReads) <= 100) {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 80-100M");
                    } else {
                        serviceId = serviceInfoMap.get("RNASeq - SMARTer - 100M+");
                    }
                    requestsSeviceIds.add(serviceId);
                }
            }
//            if(serviceType.equals("Rapid-RCC")) {
//
//            }
            if(serviceType.equals("Archer")) {
                if (firstSample.getChildrenOfType("QcReportRna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                if (recipe.toLowerCase().contains("Archer-HemePanel")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Archer Fusion - Heme Panel"));
                }
                else if (recipe.toLowerCase().contains("Archer-SolidTumorPanel")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Archer Fusion - Solid Tumor (MSK) Panel"));
                }
                else if (recipe.toLowerCase().contains("Archer-Immunoverse")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Archer Immunoverse"));
                }
            }
            if(serviceType.equals("10XGenomics_GeneExpression")) {
                requestsSeviceIds.add((serviceInfoMap.get("10X GEX Library")));
                if (!maxNumOfReads.equals("") && Integer.parseInt(maxNumOfReads) >= 200) {
                    requestsSeviceIds.add((serviceInfoMap.get("10X GEX Sequencing - 10K cells")));
                    if (Integer.parseInt(maxNumOfReads) > 200) {
                        int remainingReadsToCharge = (Integer.parseInt(maxNumOfReads) - 200) / 10;
                        for (int i = 0; i < remainingReadsToCharge; i++) {
                            requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - 10X Standard"));
                        }
                    }
                }
                else {
                    int countOfAdditions = Integer.parseInt(maxNumOfReads) / 20;
                    for (int i = 0; i < countOfAdditions; i++) {
                        requestsSeviceIds.add((serviceInfoMap.get("10X GEX Sequencing - 1K cells")));
                    }
                }
            }
            if(serviceType.equals("10XGenomics_VDJ")) {
                requestsSeviceIds.add(serviceInfoMap.get("10X VDJ Library"));
                if (!maxNumOfReads.equals("")) {
                    if (Integer.parseInt(maxNumOfReads) >= 50) {
                        requestsSeviceIds.add(serviceInfoMap.get("10X VDJ/FB Sequencing - 10K cells"));
                        if (Integer.parseInt(maxNumOfReads) > 50) {
                            int remainingReadsToCharge = (Integer.parseInt(maxNumOfReads) - 50) / 10;
                            for (int i = 0; i < remainingReadsToCharge; i++) {
                                requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - 10X Standard"));
                            }
                        }
                    } else {
                        int numOfAdditions = Integer.parseInt(maxNumOfReads) / 5;
                        for (int i = 0; i < numOfAdditions; i++) {
                            requestsSeviceIds.add(serviceInfoMap.get("10X VDJ/FB Sequencing - 1K cells"));
                        }
                    }
                }
            }
            if(serviceType.equals("10XGenomics_FeatureBarcoding")) {
                requestsSeviceIds.add(serviceInfoMap.get("10X FB Library"));
                if (!maxNumOfReads.equals("")) {
                    if (Integer.parseInt(maxNumOfReads) >= 50) {
                        requestsSeviceIds.add(serviceInfoMap.get("10X VDJ/FB Sequencing - 10K cells"));
                        if (Integer.parseInt(maxNumOfReads) > 50) {
                            int remainingReadsToCharge = (Integer.parseInt(maxNumOfReads) - 50) / 10;
                            for (int i = 0; i < remainingReadsToCharge; i++) {
                                requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - 10X Standard"));
                            }
                        }
                    } else {
                        int countOfAdditions = Integer.parseInt(maxNumOfReads) / 5;
                        for (int i = 0; i < countOfAdditions; i++) {
                            requestsSeviceIds.add(serviceInfoMap.get("10X VDJ/FB Sequencing - 1K cells"));
                        }
                    }
                }
            }
            if(serviceType.equals("10XGenomics_Multiome")) {
                requestsSeviceIds.add(serviceInfoMap.get("10X Multiome Library"));
                if (!maxNumOfReads.equals("")) {
                    if (Integer.parseInt(maxNumOfReads) >= 200) {
                        requestsSeviceIds.add(serviceInfoMap.get("10X Multiome Sequencing - 10K nuclei"));
                    } else {
                        int numberOfAdditions = Integer.parseInt(maxNumOfReads) / 20;
                        for (int i = 0; i < numberOfAdditions; i++) {
                            requestsSeviceIds.add(serviceInfoMap.get("10X Multiome Sequencing - 1K nuclei"));
                        }
                    }
                }
            }
            if(serviceType.equals("WholeExome-KAPALib")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0 ||
                        firstSample.getChildrenOfType("QcReportLibrary", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                if (preservation.toLowerCase().contains("ffpe")) {
                    if (coverage == 30 || coverage == 70) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - FFPE - 70X"));
                    }
                    else if (coverage == 100) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - FFPE - 100X"));
                    }
                    else if (coverage == 150) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - FFPE - 150X"));
                    }
                    else if (coverage == 200) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - FFPE - 200X"));
                    }
                    else if (coverage == 250) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - FFPE - 250X"));
                    }
                }
                else if (preservation.toLowerCase().contains("frozen")) {
                    if (coverage == 30 || coverage == 70) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - Frozen - 70X"));
                    }
                    else if (coverage== 100) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - Frozen - 100X"));
                    }
                    else if (coverage == 150) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - Frozen - 150X"));
                    }
                    else if (coverage == 200) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - Frozen - 200X"));
                    }
                    else if (coverage == 250) {
                        requestsSeviceIds.add(serviceInfoMap.get("WES - Frozen - 250X"));
                    }
                }
            }
            if(serviceType.equals("HumanWholeGenome") || serviceType.equals("MouseWholeGenome")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                if (Integer.parseInt(pcrCycles) > 0) {
                    if (coverage== 10) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 10X"));
                    }
                    else if (coverage == 30) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 30X"));
                    }
                    else if (coverage == 40) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 40X"));
                    }
                    else if (coverage == 50) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 50X"));
                    }
                    else if (coverage == 60) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 60X"));
                    }
                    else if (coverage == 70) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 70X"));
                    }
                    else if (coverage == 80) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 80X"));
                    }
                    else if (coverage == 100) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 100X"));
                    }
                    else if (coverage == 120) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR+ - 120X"));
                    }
                }
                else { // PCR Free
                    if (coverage == 10) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 10X"));
                    }
                    else if (coverage == 30) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 30X"));
                    }
                    else if (coverage == 40) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 40X"));
                    }
                    else if (coverage == 50) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 50X"));
                    }
                    else if (coverage == 60) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 60X"));
                    }
                    else if (coverage == 70) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 70X"));
                    }
                    else if (coverage == 80) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 80X"));
                    }
                    else if (coverage == 100) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 100X"));
                    }
                    else if (coverage == 120) {
                        requestsSeviceIds.add(serviceInfoMap.get("WGS - PCR-free - 120X"));
                    }
                }
            }
//            if(serviceType.equals("MouseWholeGenome")) { // preservation: FFPE or non FFPE + seqreq: coverage
//                if (preservation.toLowerCase().contains("ffpe")) {
//
//                }
//                else {
//
//                }
//            }
            if(serviceType.equals("WholeGenome")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                    requestsSeviceIds.add(serviceInfoMap.get("Shallow WGS"));

                }
                int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 10;
                int seqReadsService = remainigReadsRequestedToCharge / 10;

                for (int i = 0; i < seqReadsService; i++) {
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));
                }
            }
            if(serviceType.equals("sWGS")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0 ||
                        firstSample.getChildrenOfType("QcReportLibrary", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));

                }
                requestsSeviceIds.add(serviceInfoMap.get("Shallow WGS"));
            }
            if(serviceType.equals("ChIPSeq")) {
                requestsSeviceIds.add(serviceInfoMap.get("ChIP-Seq/CUT&RUN"));
                requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));
                if (!maxNumOfReads.equals("")) {
                    int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 10;
                    int seqReadsService = remainigReadsRequestedToCharge / 10;
                    for (int i = 0; i < seqReadsService; i++) {
                        requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));
                    }
                }
            }
            if(serviceType.equals("MethylSeq")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                requestsSeviceIds.add(serviceInfoMap.get("EPIC Methyl Capture"));
            }
            if(serviceType.equals("CRISPRSeq")) {
                requestsSeviceIds.add(serviceInfoMap.get("CRISPR-Seq"));
            }
            if(serviceType.equals("ATACSeq")) {
                requestsSeviceIds.add(serviceInfoMap.get("ATAC-Seq"));
                if (!maxNumOfReads.equals("") && Integer.parseInt(maxNumOfReads) > 50) {
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));
                    int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 50;
                    int seqReadsService = remainigReadsRequestedToCharge / 10;

                    for (int i = 0; i < seqReadsService; i++) {
                        requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));
                    }
                }
            }
            if(serviceType.equals("AmpliconSeq")) { // seq req: requested read length, only if PE100, else do it manually
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                requestsSeviceIds.add(serviceInfoMap.get("AmpliconSeq"));
                if (SequencingRunType.equals("PE100")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));

                } else if (SequencingRunType.equals("PE150")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE150"));
                }
            }
            if(serviceType.equals("ddPCR")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0 ||
                    firstSample.getChildrenOfType("QcReportLibrary", user).length > 0 ||
                        firstSample.getChildrenOfType("QcReportRna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));

                }
                //requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));
                //ddPCR Human % Assay
                if(Ch1Target.toLowerCase().contains("Mouse_Human_CNV_PTGER2")) {
                    serviceId = serviceInfoMap.get("ddPCR Human % Assay");
                }
                else if (numOfReplicates > 1) {
                    for (int i  = 0; i < numOfReplicates; i++) {
                        requestsSeviceIds.add(serviceInfoMap.get("ddPCR (1 reaction)"));
                    }
                }
            }
            if(serviceType.equals("DLP")) {
                requestsSeviceIds.add(serviceInfoMap.get("DLP Library - 800 cells"));
                requestsSeviceIds.add(serviceInfoMap.get("DLP Sequencing - 800 cells"));
            }
//            if(serviceType.equals("PED-PEG")) {
//
//            }
            if(serviceType.equals("FragmentAnalysis")) {
                requestsSeviceIds.add(serviceInfoMap.get("Custom Fragment Analysis"));

            }
            if(serviceType.equals("CellLineAuthentication")) {
                requestsSeviceIds.add(serviceInfoMap.get("Cell Line Authentication"));
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));

            }
            if(serviceType.equals("CMO-CH")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                requestsSeviceIds.add(serviceInfoMap.get("CMO-CH"));
                requestsSeviceIds.add(serviceInfoMap.get("Data Analysis - CMO-CH"));
            }
            if(serviceType.equals("TCRSeq-IGO")) {
                if (firstSample.getChildrenOfType("QcReportDna", user).length > 0) {
                    requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));
                }
                requestsSeviceIds.add(serviceInfoMap.get("TCRSeq-IGO"));
            }

            if(requestsSeviceIds.size() > 0) {
                for (String eachServiceId : requestsSeviceIds) {
                    chargesFieldValues = new HashMap<>();
                    chargesFieldValues.put("serviceId", eachServiceId);
                    chargesFieldValues.put("note", requestName);
                    chargesFieldValues.put("serviceQuantity", serviceQuantity);

                    Date date = new Date();
                    date.setTime(purchaseDate);
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-hh.mm.ss");
                    String formattedDate = formatter.format(date);
                    chargesFieldValues.put("purchasedOn", formattedDate);
                    chargesFieldValues.put("serviceRequestId", requestId);
                    chargesFieldValues.put("ownerEmail", ownerEmail);
                    chargesFieldValues.put("pIEmail", piEmail);
                    chargesFieldValues.put("paymentNumber", "");
                    chargeInfoRecords.add(chargesFieldValues);
                }
            }
        } catch (IoError | RemoteException | NotFound e) {
            logError("An exception occurred while  retrieving first sample's request info");
        }
        return chargeInfoRecords;
    }

    /**
     * Generating the iLab bulk charge upload CSV sheet
     * */
    private void generateiLabChargeSheet() {
        // Make the sheet with 7 columns
        List<String[]> dataLines = new LinkedList<>();
        String[] headersArray = new String[headerValues.size()];
        int i = 0;
        for (String headerValue : headerValues) {
            headersArray[i++] = headerValue;
        }
        dataLines.add(headersArray);
        i = 0;
        String request = "";
        String[] dataInfoArray = new String[headerValues.size()];
        for(Map<String, String> row : dataValues) {
            dataInfoArray[i++] = row.get("serviceId");
            dataInfoArray[i++] = row.get("note");
            dataInfoArray[i++] = row.get("serviceQuantity");
            dataInfoArray[i++] = row.get("purchasedOn");
            dataInfoArray[i++] = row.get("serviceRequestId");
            request = row.get("serviceRequestId");
            dataInfoArray[i++] = row.get("ownerEmail");
            dataInfoArray[i++] = row.get("pIEmail");
            dataInfoArray[i++] = row.get("paymentNumber");
            dataLines.add(dataInfoArray);
            dataInfoArray = new String[headerValues.size()];
            i = 0;
        }

        logInfo("Inside generateiLabChargeSheet method");
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            File outFile;
            StringBuffer allData = new StringBuffer();
            byte[] bytes;
            for (String[] eachLine: dataLines) {
                for (String eachCell : eachLine) {
                    logInfo("each cell is: " + eachCell);
                    allData.append(eachCell + ",");
                }
                allData.append("\n");
            }
            bytes = allData.toString().getBytes();
            ExemplarConfig exemplarConfig = new ExemplarConfig(managerContext);
            String iLabChargeUpload = exemplarConfig.getExemplarConfigValues().get("iLabBulkUploadChargesPath").toString();
            //"/pskis34/vialelab/LIMS/iLabBulkUploadCharges"
            outFile = new File(iLabChargeUpload + "/" + request + "_ilabcharge.csv");
            clientCallback.writeBytes(bytes, outFile.getName());
            // Check for permission to write into shared drive

            try (OutputStream fos = new FileOutputStream(outFile, false)){
                fos.write(bytes);
                //outFile.setReadOnly();
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
}
