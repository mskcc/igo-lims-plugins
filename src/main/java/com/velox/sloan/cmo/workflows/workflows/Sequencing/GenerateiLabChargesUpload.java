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

//**************Can I upload a dummy charge upload to actual IGO core room? Yes, under IGO-Test



public class GenerateiLabChargesUpload extends DefaultGenericPlugin {
    private List<String> headerValues = Arrays.asList("service_id", "note", "service_quantity", "purchased_on",
            "service_request_id", "owner_email", "pi_email_or_group_id");
    public List<Map<String, String>> dataValues = new LinkedList<>();
    // SampleReceving Request Type pick list ID

    private static final Map<String, String> serviceInfoMap = new HashMap<>(); // or reading from the file on iLabs in case of any update
    static { // Make the map of Service Name -> Service ID
        //QC
        serviceInfoMap.put("QC - Agilent", "256044");
        serviceInfoMap.put("QC - Quant-it", "259492");
        serviceInfoMap.put("QC - Quantity + Quality", "256029");
        // DDPCR Non-Sequencing
        serviceInfoMap.put("ddPCR (1 reaction)", "256041"); // Request name: DDPCR
        serviceInfoMap.put("ddPCR Assay Design", "288524"); // assay ignore
        serviceInfoMap.put("ddPCR Assay Order - CNV/GEX", "290735"); // Pick list ID: ddPCR Assay Type
        serviceInfoMap.put("ddPCR Assay Order - Mutation", "288525"); // Pick list ID: ddPCR Assay Type
        serviceInfoMap.put("ddPCR Human % Assay", "490143"); // Pick list ID: ddPCR Species
        serviceInfoMap.put("ddPCR KRAS Multiplexing", "337962"); // What is "KRAS Multiplexing"?
        // CellLine Aut (STR) Non-Sequencing
        serviceInfoMap.put("Cell Line Authentication", "490142"); // Request name: CellLineAuthentication
        serviceInfoMap.put("Fingerprinting - STR", "302835"); // None Sequencing, Cell line Auth?

        serviceInfoMap.put("GeoMx Experiment Optimization", "504495");
        serviceInfoMap.put("GeoMx Library", "504496");
        serviceInfoMap.put("GeoMx Slide Prep", "504497");
        // Data Analysis
        serviceInfoMap.put("Data Analysis - ACCESS (N)", "495935"); // Request name: MSK-ACCESS_v1?
        serviceInfoMap.put("Data Analysis - ACCESS (T)", "495936"); // Request name: MSK-ACCESS_v1?
        serviceInfoMap.put("Data Analysis - CMO-CH", "495937");
        serviceInfoMap.put("Data Handling", "491618"); // IGO Internal, Any expense entered in the iLab?

        // Library Prep
        serviceInfoMap.put("10X FB Library", "490181"); //Feature Barcoding

        serviceInfoMap.put("10X GEX Library", "490175"); // Gene Expression Library Prep
        serviceInfoMap.put("10X GEX Sequencing - 10K cells", "490177"); // Gene Expression Sequencing + cell count (sample table)
        serviceInfoMap.put("10X GEX Sequencing - 1K cells", "490176"); // Gene Expression Sequencing + cell count (sample table)

        serviceInfoMap.put("10X Multiome Library", "490182"); // Multiome Lib Prep
        serviceInfoMap.put("10X Multiome Sequencing - 10K nuclei", "490184"); // Where to find Nuclei info? which table?
        serviceInfoMap.put("10X Multiome Sequencing - 1K nuclei", "490183");

        serviceInfoMap.put("10X VDJ Library", "490178"); // Lib Prep?
        serviceInfoMap.put("10X VDJ/FB Sequencing - 10K cells", "490180");
        serviceInfoMap.put("10X VDJ/FB Sequencing - 1K cells", "490179");

        serviceInfoMap.put("10X Visium Library", "490190"); // Lib Prep?
        serviceInfoMap.put("10X Visium Optimization", "490189"); // What property is "Optimization"?
        serviceInfoMap.put("10X Visium Sequencing (Frozen)", "490191"); // Sequencing
        serviceInfoMap.put("10X Visium Sequencing (FFPE)", "504489");


        serviceInfoMap.put("ACCESS - Normal", "406820"); //Tumor or normal in sample
        serviceInfoMap.put("ACCESS - Tumor", "406821");
        serviceInfoMap.put("AmpliconSeq", "490510"); // Request name: AmpliconSeq

        serviceInfoMap.put("Archer Fusion - Heme Panel", "334267");
        serviceInfoMap.put("Archer Fusion - Solid Tumor (MSK) Panel", "334266");
        serviceInfoMap.put("Archer Immunoverse", "490140");

        serviceInfoMap.put("ATAC Library Prep", "490513"); // IGO Internal
        serviceInfoMap.put("ATAC-Seq", "483257"); // Request name: ATACSeq

        serviceInfoMap.put("cfDNA Extraction", "261860");

        serviceInfoMap.put("ChIP-Seq/CUT&RUN", "483258"); // Request name: ChIPSeq

        serviceInfoMap.put("CMO-CH", "492855");

        serviceInfoMap.put("CRISPR-Seq", "308754"); // Request name: CRISPRSeq
        serviceInfoMap.put("Custom Fragment Analysis", "504490");

        serviceInfoMap.put("DLP Library - 800 cells", "490187"); // DLP Lib Prep
        serviceInfoMap.put("DLP Sequencing - 800 cells", "504524"); // DLP Sequencing

        serviceInfoMap.put("DNA Extraction - Oxford Nanopore", "504492");
        serviceInfoMap.put("DNA Extraction", "504491");
        serviceInfoMap.put("DNA Extraction - FFPE", "256048"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample origin: FFPE?
        serviceInfoMap.put("DNA/RNA Dual Extraction", "256092"); // Request name: PATH-DNA/RNASimultaneous??
        serviceInfoMap.put("DNA/RNA Dual Extraction - FFPE", "504493");

        serviceInfoMap.put("EPIC Methyl Capture", "483259"); // request name: MethylSeq???

        serviceInfoMap.put("FFPE Sectioning", "260306"); // what is it?

        serviceInfoMap.put("H&E Stain", "260304"); // Is it a request name? What property is it?

        serviceInfoMap.put("HemePACT - Normal", "259603"); // Tumor or normal Sample level info
        serviceInfoMap.put("HemePACT - Tumor", "406819"); // Tumor or normal Sample level info

        serviceInfoMap.put("IMPACT - Mouse", "331388"); // Tumor or normal/ Species Sample level info
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

        serviceInfoMap.put("Shallow WGS", "341254"); // Human Whole Genome/ Mouse Whole Genome/ Whole Genome?

        serviceInfoMap.put("Slide Dissection", "260643"); // in Pathology protocol 1: MicrodissectionRequired, PATH-DNA/RNA/simultaious Extraction
        serviceInfoMap.put("Slide Scraping", "296697"); // Where is it included? Pathology protocol 1: ScrapingCurlsRequired, PATH-DNA/RNA/simultaious Extraction

        serviceInfoMap.put("SMARTer Amplification", "261859");

        serviceInfoMap.put("TCRSeq-IGO", "498671");

        serviceInfoMap.put("WES - FFPE - 100X", "289981"); // WholeExomeKapaLib + coverage
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

        serviceInfoMap.put("WGS - PCR+ - 100X", "490204"); // PCR information? [WholeGenomeLibProtocol3]: PCR cycles
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
        List<DataRecord> flowCellSamples = activeTask.getAttachedDataRecords("NormalizationPooledLibProtocol", user);
        // On igo-lims04 I checked sample for normalization of pooled libraries datatype allowable parent
        Set<String> uniqueRequestsOnTheFlowCell = new HashSet<>();
        for(DataRecord eachPool : flowCellSamples) {
            DataRecord firstSampleOfEachRequest = eachPool.getParentsOfType("Sample", user).get(0);
            String requestId = firstSampleOfEachRequest.getAncestorsOfType("Request", user).get(0)
                    .getStringVal("RequestId", user);
            if (uniqueRequestsOnTheFlowCell.add(requestId)) {
                dataValues = outputChargesInfo(firstSampleOfEachRequest);
                generateiLabChargeSheet();
            }
            dataValues.clear();
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
            DataRecord requestRecord = firstSample.getAncestorsOfType("Request", user).get(0);
            String serviceType = requestRecord.getStringVal("RequestName", user);

            String ownerEmail = requestRecord.getStringVal("ProjectOwner", user);
            String piEmail = requestRecord.getStringVal("PIemail", user);
            String requestId = requestRecord.getStringVal("RequestId", user);
            String purchaseDate = requestRecord.getDataField("RequestDate", user).toString();
            String serviceQuantity = requestRecord.getDataField("SampleNumber", user).toString();
            // Sample level information
            String species = firstSample.getStringVal("Species", user);
            String preservation = firstSample.getStringVal("Preservation", user);
            String tumorOrNormal = firstSample.getStringVal("TumorOrNormal", user);
            String assay = firstSample.getStringVal("Assay", user);
            String origin = firstSample.getStringVal("SampleOrigin", user);
            String sampleType = firstSample.getStringVal("SampleType", user);
            String recipe = firstSample.getStringVal("Recipe", user);

            // Sequencing Requirements: on igo-lims04 I checked sample as allowable parent for sequencing requirement datatype
            DataRecord [] seqRequeirements = firstSample.getChildrenOfType("SeqRequirementPooled", user);
            String numOfReads = seqRequeirements[0].getDataField("RequestedReads", user).toString();
            String maxNumOfReads = numOfReads.substring(0, numOfReads.length() - 2);
            //String covrage = seqRequeirements[0].getDataField("CoverageTarget", user).toString();
            String SequencingRunType = seqRequeirements[0].getDataField("SequencingRunType", user).toString();

            //DDPCR: DdPcrProtocol2 is a potential child of sample: to be marked in LIMS
            DataRecord[] ddpcrProtocol2s = firstSample.getChildrenOfType("DdPcrProtocol2", user);
            int numOfReplicates = 0;
            for (DataRecord ddpcrProtocol2 : ddpcrProtocol2s) {
                numOfReplicates += ddpcrProtocol2.getIntegerVal("NumberOfReplicates", user);
            }


            // Pathology info
            // PathologyProtocol1 is a potential child of sample: to be marked in LIMS
            DataRecord[] pathRecords = firstSample.getChildrenOfType("PathologyProtocol1", user);
            Boolean pathScrapping = false;
            Boolean pathMicrodissection = false;
            if (pathRecords.length > 0) {
                pathScrapping = pathRecords[0].getBooleanVal("ScrapingCurlsRequired", user);
                pathMicrodissection = pathRecords[0].getBooleanVal("MicrodissectionRequired", user);
            }


            // Request name in Request table is a drop down menu with certain options
            String serviceId;
            Map<String, String> chargesFieldValues;
            Set<String> requestsSeviceIds = new HashSet<>();

            requestsSeviceIds.add(serviceInfoMap.get("QC - Quantity + Quality"));

            if(serviceType.equals("DNAExtraction")) {
                if (sampleType.toLowerCase().contains("cfdna")) {
                    requestsSeviceIds.add(serviceInfoMap.get("cfDNA Extraction - Plasma"));
                } else if (preservation.toLowerCase().contains("ffpe")) {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - FFPE"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - Viably Frozen"));
                }
            }
            if(serviceType.equals("RNAExtraction")) {
                requestsSeviceIds.add(serviceInfoMap.get("RNA Extraction - FFPE"));
            }

            if(serviceType.equals("DNA/RNASimultaneous")) {
                requestsSeviceIds.add(serviceInfoMap.get("DNA/RNA Dual Extraction"));

            }
            if(serviceType.equals("PATH-DNAExtraction")) {
                if (sampleType.toLowerCase().contains("cfdna")) {
                    requestsSeviceIds.add(serviceInfoMap.get("cfDNA Extraction - Plasma"));
                } else if (preservation.toLowerCase().contains("ffpe")) {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - FFPE"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("DNA Extraction - Viably Frozen"));
                }

                if (pathScrapping) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Scraping"));
                }
                else if (pathMicrodissection) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Dissection"));
                }

            }
            if(serviceType.equals("PATH-RNAExtraction")) {
                requestsSeviceIds.add(serviceInfoMap.get("RNA Extraction - FFPE"));
                if (pathScrapping) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Scraping"));
                }
                else if (pathMicrodissection) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Dissection"));
                }
            }
            if(serviceType.equals("PATH-DNA/RNASimultaneous")) {
                requestsSeviceIds.add(serviceInfoMap.get("DNA/RNA Dual Extraction"));
                if (pathScrapping) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Scraping"));
                }
                else if (pathMicrodissection) {
                    requestsSeviceIds.add(serviceInfoMap.get("Slide Dissection"));
                }
            }
            if(serviceType.equals("IMPACT505")) {
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
            }
            if(serviceType.equals("HemePACT_v4")) {
                if (tumorOrNormal.toLowerCase().contains("tumor")) {
                    requestsSeviceIds.add(serviceInfoMap.get("HemePACT - Tumor"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("HemePACT - Normal"));
                }
            }
            if(serviceType.equals("MSK-ACCESS_v1")) {
                if (tumorOrNormal.toLowerCase().contains("tumor")) {
                    requestsSeviceIds.add(serviceInfoMap.get("ACCESS - Tumor"));
                }
                else {
                    requestsSeviceIds.add(serviceInfoMap.get("ACCESS - Normal"));
                }
            }
            if(serviceType.equals("RNASeq-TruSeqPolyA")) { // seq req might be under source sample id
                requestsSeviceIds.add(serviceInfoMap.get("PolyA Library Prep"));

                if (Integer.parseInt(maxNumOfReads) < 20) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 10-20M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 30) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 20-30M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 40) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 30-40M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 50) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 40-50M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 60) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 50-60M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 80) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 60-80M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 100) {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 80-100M");
                }
                else {
                    serviceId = serviceInfoMap.get("RNASeq - polyA - 100M+");
                }
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("RNASeq-TruSeqRiboDeplete")) {
                requestsSeviceIds.add(serviceInfoMap.get("RiboDepletion Library Prep"));

                if (Integer.parseInt(maxNumOfReads) < 20) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 10-20M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 30) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 20-30M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 40) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 30-40M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 50) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 40-50M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 60) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 50-60M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 80) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 60-80M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 100) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 80-100M");
                }
                else {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 100M+");
                }
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("RNASeq-SMARTerAmp")) {
                requestsSeviceIds.add(serviceInfoMap.get("SMARTer Amplification"));

            }
//            if(serviceType.equals("Rapid-RCC")) { // Single price, ask Neeman
//
//            }
            if(serviceType.equals("Archer")) {
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
            if(serviceType.equals("10XGenomics_GeneExpression")) { // Cassidy will provide

            }
            if(serviceType.equals("10XGenomics_VDJ")) { // same as Feature barcoding

            }
            if(serviceType.equals("10XGenomics_FeatureBarcoding")) {
                requestsSeviceIds.add(serviceInfoMap.get("10X FB Library"));
                if (Integer.parseInt(maxNumOfReads) >= 50) {
                    serviceId = serviceInfoMap.get("10X VDJ/FB Sequencing - 10K cells");
                }
                else {
                    serviceId = serviceInfoMap.get("10X VDJ/FB Sequencing - 1K cells");
                }
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("10XGenomics_Multiome")) { // use seq req

            }
            if(serviceType.equals("WholeExome-KAPALib")) { // preservation: FFPE or non FFPE + seqreq: coverage
                if (preservation.toLowerCase().contains("ffpe")) {

                }
                else {

                }

            }
            if(serviceType.equals("HumanWholeGenome")) { // preservation: FFPE or non FFPE + seqreq: coverage
                if (preservation.toLowerCase().contains("ffpe")) {

                }
                else {

                }
            }
            if(serviceType.equals("MouseWholeGenome")) { // preservation: FFPE or non FFPE + seqreq: coverage
                if (preservation.toLowerCase().contains("ffpe")) {

                }
                else {

                }
            }
            if(serviceType.equals("WholeGenome")) { // seqr > 10 M + add sequencing charge
                int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 10;

                if (remainigReadsRequestedToCharge < 20) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 10-20M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 30) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 20-30M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 40) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 30-40M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 50) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 40-50M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 60) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 50-60M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 80) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 60-80M");
                }
                else if (Integer.parseInt(maxNumOfReads) < 100) {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 80-100M");
                }
                else {
                    serviceId = serviceInfoMap.get("RNASeq - Ribodeplete - 100M+");
                }
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("sWGS")) { // QC + one price
                requestsSeviceIds.add(serviceInfoMap.get("Shallow WGS"));
            }
            if(serviceType.equals("ChIPSeq")) { // similar to wholegenome
                requestsSeviceIds.add(serviceInfoMap.get("ChIP-Seq/CUT&RUN"));
                int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 10;

            }
            if(serviceType.equals("MethylSeq")) {
                requestsSeviceIds.add(serviceInfoMap.get("EPIC Methyl Capture"));
            }
            if(serviceType.equals("CRISPRSeq")) {
                requestsSeviceIds.add(serviceInfoMap.get("CRISPR-Seq"));
            }
            if(serviceType.equals("ATACSeq")) { // seq req <= 50M reads no addional seq charge
                requestsSeviceIds.add(serviceInfoMap.get("ATAC-Seq"));
                if (Integer.parseInt(maxNumOfReads) > 50) {
                    int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 50;
                    int seqReadsService = remainigReadsRequestedToCharge / 10;
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));

                    if (seqReadsService < 20) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else if (seqReadsService < 30) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else if (seqReadsService < 40) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else if (seqReadsService < 50) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else if (seqReadsService < 60) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else if (seqReadsService < 80) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else if (seqReadsService < 100) {
                        serviceId = serviceInfoMap.get("");
                    }
                    else {
                        serviceId = serviceInfoMap.get("");
                    }
                    requestsSeviceIds.add(serviceId);

                }
            }
            if(serviceType.equals("AmpliconSeq")) { // seq req: requested read length, only if PE100, else do it manually
                requestsSeviceIds.add(serviceInfoMap.get("AmpliconSeq"));
                if (SequencingRunType.equals("PE100")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE100"));

                } else if (SequencingRunType.equals("PE150")) {
                    requestsSeviceIds.add(serviceInfoMap.get("Sequencing - 10M Reads - PE150"));
                }
            }
            if(serviceType.equals("ddPCR")) {
                // Assays picklist
                //QC- Quant-it
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));
                //ddPCR (1 reaction)
                serviceId = serviceInfoMap.get("ddPCR (1 reaction)");
                for (int i  = 0; i < numOfReplicates; i++) {
                    requestsSeviceIds.add(serviceId);
                }
                //ddPCR Human % Assay
                if(assay.toLowerCase().contains("")) {
                    serviceId = serviceInfoMap.get("ddPCR Human % Assay");
                }
                //ddPCR KRAS Multiplexing
                else if (assay.toLowerCase().contains("")) {
                    serviceId = serviceInfoMap.get("ddPCR KRAS Multiplexing");
                }
                requestsSeviceIds.add(serviceId);

            }
            if(serviceType.equals("DLP")) {
                requestsSeviceIds.add(serviceInfoMap.get("DLP Library - 800 cells"));
                requestsSeviceIds.add(serviceInfoMap.get("DLP Sequencing - 1 quadrant"));
            }
//            if(serviceType.equals("PED-PEG")) { // Single price, ask Neeman
//
//            }
            if(serviceType.equals("FragmentAnalysis")) {
                //Custom Fragment Analysis
                requestsSeviceIds.add(serviceInfoMap.get("Custom Fragment Analysis"));

            }
            if(serviceType.equals("CellLineAuthentication")) {
                requestsSeviceIds.add(serviceInfoMap.get("Cell Line Authentication"));
                requestsSeviceIds.add(serviceInfoMap.get("QC - Quant-it"));

            }
            if(serviceType.equals("CMO-CH")) {
                requestsSeviceIds.add(serviceInfoMap.get("CMO-CH"));
                requestsSeviceIds.add(serviceInfoMap.get("Data Analysis - CMO-CH"));
            }
            if(serviceType.equals("TCRSeq-IGO")) {
                requestsSeviceIds.add(serviceInfoMap.get("TCRSeq-IGO"));
            }

            for(String eachServiceId : requestsSeviceIds) {
                chargesFieldValues = new HashMap<>();
                chargesFieldValues.put("serviceId", eachServiceId);
                chargesFieldValues.put("note", requestId);
                chargesFieldValues.put("serviceQuantity", serviceQuantity);
                chargesFieldValues.put("purchasedOn", purchaseDate);
                chargesFieldValues.put("serviceRequestId", requestId); // from iLab!
                chargesFieldValues.put("ownerEmail", ownerEmail);
                chargesFieldValues.put("pIEmail", piEmail);
                chargeInfoRecords.add(chargesFieldValues);
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
            String iLabChargeUpload = exemplarConfig.getExemplarConfigValues().get("iLabBulkUploadChargesPath").toString();
            //"/pskis34/vialelab/LIMS/iLabBulkUploadCharges"

            // Check for permission to write into shared drive

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
}
