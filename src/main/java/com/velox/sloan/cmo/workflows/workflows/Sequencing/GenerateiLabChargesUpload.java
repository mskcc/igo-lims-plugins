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
        serviceInfoMap.put("ddPCR Assay Order - CNV", "290735"); // Pick list ID: ddPCR Assay Type
        serviceInfoMap.put("ddPCR Assay Order - Mutation/GEX", "288525"); // Pick list ID: ddPCR Assay Type
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
        serviceInfoMap.put("10X VDJ/FB Sequencing - 10K cells", "490180"); // Sequencing, VDJ/FB?
        serviceInfoMap.put("10X VDJ/FB Sequencing - 1K cells", "490179"); // Sequencing, VDJ/FB?

        serviceInfoMap.put("10X Visium Library", "490190"); // Lib Prep?
        serviceInfoMap.put("10X Visium Optimization", "490189"); // What property is "Optimization"?
        serviceInfoMap.put("10X Visium Sequencing (25%)", "490191"); // Sequencing


        serviceInfoMap.put("ACCESS - Normal", "406820"); //Tumor or normal in sample
        serviceInfoMap.put("ACCESS - Tumor", "406821");
//        serviceInfoMap.put("Adaptive immunoSEQ - Deep", "490139"); // Where to find Deep/ Survey/ Ultradeep?
//        serviceInfoMap.put("Adaptive immunoSEQ - Survey", "490138");
//        serviceInfoMap.put("Adaptive immunoSEQ - Ultradeep", "490504");
        serviceInfoMap.put("AmpliconSeq", "490510"); // Request name: AmpliconSeq

        serviceInfoMap.put("Archer Fusion - Heme Panel", "334267"); // Request name: Archer
        serviceInfoMap.put("Archer Fusion - Solid Tumor (MSK) Panel", "334266"); // Request name: Archer
        serviceInfoMap.put("Archer Immunoverse", "490140"); // Request name: Archer

        serviceInfoMap.put("ATAC Library Prep", "490513"); // IGO Internal
        serviceInfoMap.put("ATAC-Seq", "483257"); // Request name: ATACSeq

        serviceInfoMap.put("cfDNA Extraction - Plasma", "261860");

        serviceInfoMap.put("ChIP-Seq/CUT&RUN", "483258"); // Request name: ChIPSeq

        serviceInfoMap.put("CMO-CH", "492855");

        serviceInfoMap.put("CRISPR-Seq", "308754"); // Request name: CRISPRSeq
        serviceInfoMap.put("Custom Fragment Analysis", "504490");

        serviceInfoMap.put("DLP Library - 800 cells", "490187"); // DLP Lib Prep
        serviceInfoMap.put("DLP Sequencing - 1 quadrant", "490188"); // DLP Sequencing

        //serviceInfoMap.put("DNA Extraction - Blood", "256034"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample origin: Blood?
        serviceInfoMap.put("DNA Extraction - FFPE", "256048"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample origin: FFPE?
        //serviceInfoMap.put("DNA Extraction - Fresh/Frozen", "256043"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample preservation: Fresh/Frozen
        //serviceInfoMap.put("DNA Extraction - Nails", "288528"); // Available request names: DNAExtraction and PATH-DNAExtraction, What property is Nails?
        serviceInfoMap.put("DNA Extraction - Viably Frozen", "490136"); // Available request names: DNAExtraction and PATH-DNAExtraction, What is Viably Frozen?
        serviceInfoMap.put("DNA/RNA Dual Extraction", "256092"); // Request name: PATH-DNA/RNASimultaneous??

        serviceInfoMap.put("EPIC Methyl Capture", "483259"); // request name: MethylSeq???

        serviceInfoMap.put("FFPE Sectioning - Curls", "260306"); // what is it?
        serviceInfoMap.put("FFPE Sectioning - Slides", "260305"); // what is it?

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

        serviceInfoMap.put("Sample Pooling", "491619"); // Is it included in all services going into sequencing?

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

        serviceInfoMap.put("WES - 100X", "289981"); // WholeExomeKapaLib + coverage
        serviceInfoMap.put("WES - 150X", "289982");
        serviceInfoMap.put("WES - 200X", "289983");
        serviceInfoMap.put("WES - 250X", "289984");
        serviceInfoMap.put("WES - 30X", "289979");
        serviceInfoMap.put("WES - 70X", "289980");

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
        for(DataRecord eachSample : flowCellSamples) {
            DataRecord firstSampleOfEachRequest = eachSample.getParentsOfType("Sample", user).get(0);
            String requestId = firstSampleOfEachRequest.getParentsOfType("Request", user).get(0).getStringVal("RequestId", user);
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
            String serviceType = firstSample.getParentsOfType("Request", user).get(0)
                    .getStringVal("RequestName", user);
            // Request level information
            DataRecord requestRecord = firstSample.getParentsOfType("Request", user).get(0);
            String ownerEmail = requestRecord.getStringVal("ProjectOwner", user);
            String piEmail = requestRecord.getStringVal("PIemail", user);
            String requestId = requestRecord.getStringVal("RequestId", user);
            String purchaseDate = requestRecord.getStringVal("RequestDate", user);
            String serviceQuantity = requestRecord.getStringVal("SampleNumber", user);
            // Sample level information
            String species = firstSample.getStringVal("Species", user);
            String preservation = firstSample.getStringVal("Preservation", user);
            String tumorOrNormal = firstSample.getStringVal("TumorOrNormal", user);
            String assay = firstSample.getStringVal("Assay", user);
            String origin = firstSample.getStringVal("SampleOrigin", user);
            String sampleType = firstSample.getStringVal("SampleType", user);

            // Sequencing Requirements: on igo-lims04 I checked sample as allowable parent for sequencing requirement datatype
            DataRecord [] seqRequeirements = firstSample.getChildrenOfType("SeqRequirement", user);
            String maxNumOfReads = seqRequeirements[0].getStringVal("RequestedReads", user);
            String covrage = seqRequeirements[0].getStringVal("CoverageTarget", user);
            String SequencingRunType = seqRequeirements[0].getStringVal("SequencingRunType", user);

            //DDPCR: DdPcrProtocol2 is a potential child of sample: to be marked in LIMS
            DataRecord[] ddpcrProtocol2s = firstSample.getChildrenOfType("DdPcrProtocol2", user);
            int numOfReplicates = 0;
            for (DataRecord ddpcrProtocol2 : ddpcrProtocol2s) {
                numOfReplicates += ddpcrProtocol2.getIntegerVal("NumberOfReplicates", user);
            }


            // Pathology info
            // PathologyProtocol1 is a potential child of sample: to be marked in LIMS
            DataRecord[] pathRecords = firstSample.getChildrenOfType("PathologyProtocol1", user);
            Boolean pathScrapping = pathRecords[0].getBooleanVal("ScrapingCurlsRequired", user);
            Boolean pathMicrodissection = pathRecords[0].getBooleanVal("MicrodissectionRequired", user);

            // Request name in Request table is a drop down menu with certain options
            String serviceId;
            Map<String, String> chargesFieldValues;
            Set<String> requestsSeviceIds = new HashSet<>();

            serviceId = serviceInfoMap.get("QC - Quantity + Quality");
            requestsSeviceIds.add(serviceId);

            if(serviceType.equals("DNAExtraction")) {
                if (sampleType.toLowerCase().contains("cfdna")) {
                    serviceId = serviceInfoMap.get("cfDNA Extraction - Plasma");
                    requestsSeviceIds.add(serviceId);
                } else if (preservation.toLowerCase().contains("ffpe")) {
                    serviceId = serviceInfoMap.get("DNA Extraction - FFPE");
                    requestsSeviceIds.add(serviceId);
                }
                else {
                    serviceId = serviceInfoMap.get("DNA Extraction - Viably Frozen");
                    requestsSeviceIds.add(serviceId);
                }
            }
            if(serviceType.equals("RNAExtraction")) {
                serviceId = serviceInfoMap.get("RNA Extraction - FFPE");
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("DNA Cleanup")) {

            }
            if(serviceType.equals("RNA Cleanup")) {

            }
            if(serviceType.equals("DNA/RNASimultaneous")) {
                serviceId = serviceInfoMap.get("DNA/RNA Dual Extraction");
                requestsSeviceIds.add(serviceId);

            }
            if(serviceType.equals("PATH-DNAExtraction")) {
                if (sampleType.toLowerCase().contains("cfdna")) {
                    serviceId = serviceInfoMap.get("cfDNA Extraction - Plasma");
                    requestsSeviceIds.add(serviceId);
                } else if (preservation.toLowerCase().contains("ffpe")) {
                    serviceId = serviceInfoMap.get("DNA Extraction - FFPE");
                    requestsSeviceIds.add(serviceId);
                }
                else {
                    serviceId = serviceInfoMap.get("DNA Extraction - Viably Frozen");
                    requestsSeviceIds.add(serviceId);
                }

                if (pathScrapping) {
                    serviceId = serviceInfoMap.get("Slide Scraping");
                    requestsSeviceIds.add(serviceId);
                }
                else if (pathMicrodissection) {
                    serviceId = serviceInfoMap.get("Slide Dissection");
                    requestsSeviceIds.add(serviceId);
                }

            }
            if(serviceType.equals("PATH-RNAExtraction")) {
                serviceId = serviceInfoMap.get("RNA Extraction - FFPE");
                requestsSeviceIds.add(serviceId);
                if (pathScrapping) {
                    serviceId = serviceInfoMap.get("Slide Scraping");
                    requestsSeviceIds.add(serviceId);
                }
                else if (pathMicrodissection) {
                    serviceId = serviceInfoMap.get("Slide Dissection");
                    requestsSeviceIds.add(serviceId);
                }
            }
            if(serviceType.equals("PATH-DNA/RNASimultaneous")) {
                serviceId = serviceInfoMap.get("DNA/RNA Dual Extraction");
                requestsSeviceIds.add(serviceId);
                if (pathScrapping) {
                    serviceId = serviceInfoMap.get("Slide Scraping");
                    requestsSeviceIds.add(serviceId);
                }
                else if (pathMicrodissection) {
                    serviceId = serviceInfoMap.get("Slide Dissection");
                    requestsSeviceIds.add(serviceId);
                }
            }
//            if(serviceType.equals("BloodExtraction")) {
//                // "DNA Extraction - Blood"?
//
//            }
//            if(serviceType.equals("DNA-QC")) {
//
//            }
//            if(serviceType.equals("RNA-QC")) {
//
//            }
//            if(serviceType.equals("Library-QC")) {
//
//            }
            // IMPACT: Spicies: Mouse? Tumor/Normal?
//            if(serviceType.equals("IMPACT341")) {
//
//            }
//            if(serviceType.equals("IMPACT341+")) {
//
//            }
//            if(serviceType.equals("IMPACT410")) {
//
//            }
//            if(serviceType.equals("IMPACT410+")) {
//
//            }
//            if(serviceType.equals("IMPACT468")) {
//
//            }
            if(serviceType.equals("IMPACT505")) {
                if (tumorOrNormal.toLowerCase().contains("normal")) {
                    serviceId = serviceInfoMap.get("IMPACT - Normal");
                    requestsSeviceIds.add(serviceId);
                }
                else {
                    serviceId = serviceInfoMap.get("IMPACT - Tumor");
                    requestsSeviceIds.add(serviceId);
                }
            }
//            if(serviceType.equals("PM-IMPACT")) {
//
//            }
            if(serviceType.equals("M-IMPACT")) {
                serviceId = serviceInfoMap.get("IMPACT - Mouse");
                requestsSeviceIds.add(serviceId);
            }
//            if(serviceType.equals("HemePACT_v3")) {
//                // Tumor/Normal
//
//            }
//            if(serviceType.equals("HemePACT_v3+")) {
//                // Tumor/Normal
//
//            }
            if(serviceType.equals("HemePACT_v4")) {
                if (tumorOrNormal.toLowerCase().contains("tumor")) {
                    serviceId = serviceInfoMap.get("HemePACT - Tumor");
                    requestsSeviceIds.add(serviceId);
                }
                else {
                    serviceId = serviceInfoMap.get("HemePACT - Normal");
                    requestsSeviceIds.add(serviceId);
                }
            }
//            if(serviceType.equals("CustomCapture")) { Complex
//
//            }
            if(serviceType.equals("MSK-ACCESS_v1")) {
                if (tumorOrNormal.toLowerCase().contains("tumor")) {
                    serviceId = serviceInfoMap.get("ACCESS - Tumor");
                    requestsSeviceIds.add(serviceId);
                }
                else {
                    serviceId = serviceInfoMap.get("ACCESS - Normal");
                    requestsSeviceIds.add(serviceId);
                }
            }
//            if(serviceType.equals("MissionBio")) { //Easy to bill manually
//                // depends on panel
//
//            }
            if(serviceType.equals("RNASeq-TruSeqPolyA")) { // seq req might be under source sample id
                serviceId = serviceInfoMap.get("PolyA Library Prep");
                requestsSeviceIds.add(serviceId);

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
//            if(serviceType.equals("RNASeq-KAPAmRNAStranded")) {
//
//            }
//            if(serviceType.equals("RNASeq-TruSeqFusion")) {
//
//            }
            if(serviceType.equals("RNASeq-TruSeqRiboDeplete")) {
                serviceId = serviceInfoMap.get("RiboDepletion Library Prep");
                requestsSeviceIds.add(serviceId);

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
                serviceId = serviceInfoMap.get("SMARTer Amplification");
                requestsSeviceIds.add(serviceId);

            }
            if(serviceType.equals("Rapid-RCC")) { // Single price, ask Neeman

            }
            if(serviceType.equals("Archer")) {


            }
//            if(serviceType.equals("NanoString")) {
//
//            }
            if(serviceType.equals("10XGenomics_GeneExpression")) { // Cassidy will provide

            }
            if(serviceType.equals("10XGenomics_VDJ")) { // same as Feature barcoding

            }
//            if(serviceType.equals("10XGenomics_CNV")) {
//
//            }
            if(serviceType.equals("10XGenomics_FeatureBarcoding")) {
                //490181

            }
            if(serviceType.equals("10XGenomics_Multiome")) { // use seq req

            }
            if(serviceType.equals("10XGenomics_Visium")) { // use seq req
                serviceId = serviceInfoMap.get("10X Visium Library");
                requestsSeviceIds.add(serviceId);
                serviceInfoMap.get("10X Visium Optimization");
                requestsSeviceIds.add(serviceId);
            }
//            if(serviceType.equals("96Well_SmartSeq2")) {
//
//            }
//            if(serviceType.equals("WholeExome + IMPACT")) { // not often
//
//            }
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
                serviceId = serviceInfoMap.get("Shallow WGS");
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("ChIPSeq")) { // similar to wholegenome
                serviceId = serviceInfoMap.get("ChIP-Seq/CUT&RUN");
                requestsSeviceIds.add(serviceId);
                int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 10;

            }
            if(serviceType.equals("MethylSeq")) {
                serviceId = serviceInfoMap.get("EPIC Methyl Capture");
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("CRISPRSeq")) {
                serviceId = serviceInfoMap.get("CRISPR-Seq");
                requestsSeviceIds.add(serviceId);
            }
//            if(serviceType.equals("shRNAScreen")) {
//                // user prepared library
//            }
//            if(serviceType.equals("RiboProfileSeq")) {
//
//            }
            if(serviceType.equals("ATACSeq")) { // seq req <= 50M reads no addional seq charge
                if (Integer.parseInt(maxNumOfReads) <= 50) {
                    serviceId = serviceInfoMap.get("ATAC-Seq");
                    requestsSeviceIds.add(serviceId);
                }
                else {
                    int remainigReadsRequestedToCharge = Integer.parseInt(maxNumOfReads) - 50;
                }

            }
//            if(serviceType.equals("AmpliSeq")) {
//
//            }
            if(serviceType.equals("AmpliconSeq")) { // seq req: requested read length, only if PE100, else do it manually
                if (SequencingRunType.equals("PE100")) {
                    serviceId = serviceInfoMap.get("AmpliconSeq");
                    requestsSeviceIds.add(serviceId);
                }
            }
//            if(serviceType.equals("AdaptiveImmunoSeq")) {
//
//            }
//            if(serviceType.equals("CLIPSeq")) {
//
//            }
//            if(serviceType.equals("HiSeq-Other")) {
//
//            }
//            if(serviceType.equals("MiSeq-Other")) {
//
//            }
//            if(serviceType.equals("NextSeq-Other")) {
//
//            }
//            if(serviceType.equals("NovaSeq-Other")) {
//
//            }
            // Dropped for now
    //            if(serviceType.equals("Investigator Prepared Libraries")) {
    //                // sequencing only
    //            }
    //            if(serviceType.equals("Investigator Prepared Pools")) { // number of micronic tubes = # pools
    //                // sequencing only
    //            }
            if(serviceType.equals("ddPCR")) {
                // Assays picklist
                //QC- Quant-it
                serviceId = serviceInfoMap.get("QC - Quant-it");
                requestsSeviceIds.add(serviceId);
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
                serviceId = serviceInfoMap.get("DLP Library - 800 cells");
                requestsSeviceIds.add(serviceId);
                serviceId = serviceInfoMap.get("DLP Sequencing - 1 quadrant");
                requestsSeviceIds.add(serviceId);
            }
            if(serviceType.equals("PED-PEG")) { // Single price, ask Neeman

            }
            if(serviceType.equals("FragmentAnalysis")) {
                //Custom Fragment Analysis
                serviceId = serviceInfoMap.get("Custom Fragment Analysis");
                requestsSeviceIds.add(serviceId);

            }
            if(serviceType.equals("CellLineAuthentication")) {
                serviceId = serviceInfoMap.get("Cell Line Authentication");
                requestsSeviceIds.add(serviceId);
                serviceId = serviceInfoMap.get("QC - Quant-it");
                requestsSeviceIds.add(serviceId);

            }
            if(serviceType.equals("CMO-CH")) {
                serviceId = serviceInfoMap.get("CMO-CH");
                requestsSeviceIds.add(serviceId);
                // "CMO-CH", "492855"
                // "Data Analysis - CMO-CH", "495937"

            }
            if(serviceType.equals("TCRSeq-IGO")) {
               serviceId = serviceInfoMap.get("TCRSeq-IGO");
               requestsSeviceIds.add(serviceId);
            }

            for(String eachServiceId : requestsSeviceIds) {
                chargesFieldValues = new HashMap<>();
                chargesFieldValues.put("serviceId", eachServiceId);
                chargesFieldValues.put("note", requestId);
                chargesFieldValues.put("serviceQuantity", serviceQuantity);
                chargesFieldValues.put("purchasedOn", purchaseDate);
                chargesFieldValues.put("serviceRequestId", ); // from iLab!
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
}
