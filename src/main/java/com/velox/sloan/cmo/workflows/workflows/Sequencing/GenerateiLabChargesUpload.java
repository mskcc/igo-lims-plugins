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

import javax.xml.crypto.Data;
import java.io.*;
import java.rmi.RemoteException;
import java.util.*;

public class GenerateiLabChargesUpload extends DefaultGenericPlugin {

    // SampleReceving Request Type pick list ID

    private static final Map<String, String> serviceInfoMap = new HashMap<>();
    static { // Make the map of Service Name -> Service ID
        //QC
        serviceInfoMap.put("QC - Agilent", "256044"); // DNA? RNA? Library?
        serviceInfoMap.put("QC - Quant-it", "259492"); // DNA? RNA? Library?
        serviceInfoMap.put("QC - Quantity + Quality", "256029"); // DNA? RNA? Library?
        // DDPCR Non-Sequencing
        serviceInfoMap.put("ddPCR (1 reaction)", "256041"); // Request name: DDPCR
        serviceInfoMap.put("ddPCR Assay Design", "288524"); // Where do this property
        serviceInfoMap.put("ddPCR Assay Order - CNV", "290735"); // Pick list ID: ddPCR Assay Type
        serviceInfoMap.put("ddPCR Assay Order - Mutation/GEX", "288525"); // Pick list ID: ddPCR Assay Type
        serviceInfoMap.put("ddPCR Human % Assay", "490143"); // Pick list ID: ddPCR Species
        serviceInfoMap.put("ddPCR KRAS Multiplexing", "337962"); // What is "KRAS Multiplexing"?
        // CellLine Aut (STR) Non-Sequencing
        serviceInfoMap.put("Cell Line Authentication", "490142"); // Request name: CellLineAuthentication
        serviceInfoMap.put("Fingerprinting - STR", "302835"); // None Sequencing, Cell line Auth?
        // Data Analysis
        serviceInfoMap.put("Data Analysis - ACCESS (N)", "495935"); // Request name: MSK-ACCESS_v1?
        serviceInfoMap.put("Data Analysis - ACCESS (T)", "495936"); // Request name: MSK-ACCESS_v1?
        serviceInfoMap.put("Data Analysis - CMO-CH", "495937"); // analysis Pipelinable or BIC AutoRunnable? (request table)
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

        serviceInfoMap.put("ATAC Library Prep", "490513"); // IGO Internal, Request name: ATACSeq, Do we charge for IGO internals?
        serviceInfoMap.put("ATAC-Seq", "483257"); // Request name: ATACSeq

        serviceInfoMap.put("cfDNA Extraction - Plasma", "261860"); // What is the request name?

        serviceInfoMap.put("ChIP-Seq/CUT&RUN", "483258"); // Request name: ChIPSeq

        serviceInfoMap.put("CMO-CH", "492855"); // Request name: CMO-CH

        serviceInfoMap.put("CRISPR-Seq", "308754"); // Request name: CRISPRSeq

        serviceInfoMap.put("DLP Library - 800 cells", "490187"); // DLP Lib Prep
        serviceInfoMap.put("DLP Sequencing - 1 quadrant", "490188"); // DLP Sequencing

        serviceInfoMap.put("DNA Extraction - Blood", "256034"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample origin: Blood?
        serviceInfoMap.put("DNA Extraction - FFPE", "256048"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample Preservation: FFPE?
        serviceInfoMap.put("DNA Extraction - Fresh/Frozen", "256043"); // Available request names: DNAExtraction and PATH-DNAExtraction, Sample preservation: Fresh/Frozen
        serviceInfoMap.put("DNA Extraction - Nails", "288528"); // Available request names: DNAExtraction and PATH-DNAExtraction, What property is Nails?
        serviceInfoMap.put("DNA Extraction - Viably Frozen", "490136"); // Available request names: DNAExtraction and PATH-DNAExtraction, What is Viably Frozen?
        serviceInfoMap.put("DNA/RNA Dual Extraction", "256092"); // Request name: PATH-DNA/RNASimultaneous??

        serviceInfoMap.put("Double Capture", "497933"); // IGO Internal, iLab charge? Request name: CustomCapture???

        serviceInfoMap.put("EPIC Methyl Capture", "483259"); // request name: MethylSeq???

        serviceInfoMap.put("FFPE Sectioning - Curls", "260306"); // what is it?
        serviceInfoMap.put("FFPE Sectioning - Slides", "260305"); // what is it?

        serviceInfoMap.put("H&E Stain", "260304"); // Is it a request name? What property is it?

        serviceInfoMap.put("HemePACT - Normal", "259603"); // Tumor or normal Sample level info
        serviceInfoMap.put("HemePACT - Tumor", "406819"); // Tumor or normal Sample level info

        serviceInfoMap.put("IMPACT - Mouse", "331388"); // Tumor or normal/ Species Sample level info
        serviceInfoMap.put("IMPACT - Normal", "256124");
        serviceInfoMap.put("IMPACT - Tumor", "406813");

        serviceInfoMap.put("KAPA HT Library Prep", "256127");
        serviceInfoMap.put("KAPA Hyper Library Prep", "351941"); // Available request name: WholeExome-KAPALib and RNASeq-KAPAmRNAStranded
        serviceInfoMap.put("KAPA WGS Library Prep - PCR+", "490516"); // Available request name: WholeExome-KAPALib and RNASeq-KAPAmRNAStranded
        serviceInfoMap.put("KAPA WGS Library Prep - PCR-free", "490515"); // Available request name: WholeExome-KAPALib and RNASeq-KAPAmRNAStranded

        serviceInfoMap.put("Micronic Tube", "308755"); // Where is it included?

        serviceInfoMap.put("PlateSeq Library Prep", "490185");
        serviceInfoMap.put("PlateSeq Sequencing - 1 column", "490186");

        serviceInfoMap.put("PolyA Library Prep", "490511"); // Lib prep

        serviceInfoMap.put("RiboDepletion Library Prep", "490512"); // Lib prep
        //serviceInfoMap.put("RNA Extraction + COVID19 Testing", "490141"); // Request name: RNAExtraction-COVIDScreen

        serviceInfoMap.put("RNA Extraction - FFPE", "256100"); // Where does FFPE come from? Sample preservation?
        serviceInfoMap.put("RNA Extraction - Fresh/Frozen", "256097"); // Fresh/ Frozen -> sample properties: Sample preservation?
        serviceInfoMap.put("RNA Extraction - Viably Frozen", "490137"); // Viably Frozen -> sample property: Sample preservation?

        serviceInfoMap.put("RNASeq - polyA - 10-20M", "490506"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 100M+", "490507"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 20-30M", "404330"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 30-40M", "404331"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 40-50M", "487566"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 50-60M", "404332"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 60-80M", "490144"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - polyA - 80-100M", "404334"); // Sequencing, Request name: RNASeq-TruSeqPolyA +  requested reads
        serviceInfoMap.put("RNASeq - Ribodeplete - 10-20M", "490508"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 100M+", "490509"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 20-30M", "490145"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 30-40M", "404335"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 40-50M", "490146"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 50-60M", "404336"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 60-80M", "404337"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?
        serviceInfoMap.put("RNASeq - Ribodeplete - 80-100M", "404338"); // Sequencing, Request name: RNASeq-TruSeqRiboDeplete?

        serviceInfoMap.put("Sample Capture + Library", "490514"); // Where is it included?

        serviceInfoMap.put("Sample Pooling", "491619"); // Is it included in all services going into sequencing?

        // Sequencing Only
        serviceInfoMap.put("Sequencing  - 100M Reads - 150c", "490157");
        serviceInfoMap.put("Sequencing - 100M Reads - 300c", "490158");
        serviceInfoMap.put("Sequencing - 10M Reads - 10X Standard", "490149");
        serviceInfoMap.put("Sequencing - 10M Reads - PE100", "490147");
        serviceInfoMap.put("Sequencing - 10M Reads - PE150", "490148");
        serviceInfoMap.put("Sequencing - 11000M Reads - 200c", "490173");
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

        serviceInfoMap.put("Slide Dissection", "260643"); // Where is it included?
        serviceInfoMap.put("Slide Scraping", "296697"); // Where is it included?

        serviceInfoMap.put("SMARTer Amplification", "261859"); // Request name: RNASeq-SMARTerAmp

        serviceInfoMap.put("Special Processing -- Extraction", "487571"); // ?

        serviceInfoMap.put("TCRSeq-IGO", "498671");

        serviceInfoMap.put("UMI Library Prep", "351940"); // ?

        serviceInfoMap.put("WES - 100X", "289981"); // WholeExomeKapaLib + coverage
        serviceInfoMap.put("WES - 150X", "289982");
        serviceInfoMap.put("WES - 200X", "289983");
        serviceInfoMap.put("WES - 250X", "289984");
        serviceInfoMap.put("WES - 30X", "289979");
        serviceInfoMap.put("WES - 70X", "289980");

        serviceInfoMap.put("WGS - PCR+ - 100X", "490204"); // PCR information?
        serviceInfoMap.put("WGS - PCR+ - 10X", "495934");
        serviceInfoMap.put("WGS - PCR+ - 150X", "490205");
        serviceInfoMap.put("WGS - PCR+ - 30X", "490199");
        serviceInfoMap.put("WGS - PCR+ - 40X", "490200");
        serviceInfoMap.put("WGS - PCR+ - 50X", "490201");
        serviceInfoMap.put("WGS - PCR+ - 60X", "490202");
        serviceInfoMap.put("WGS - PCR+ - 80X", "490203");
        serviceInfoMap.put("WGS - PCR-free - 100X", "490197");
        serviceInfoMap.put("WGS - PCR-free - 10X", "495933");
        serviceInfoMap.put("WGS - PCR-free - 150X", "490198");
        serviceInfoMap.put("WGS - PCR-free - 30X", "490192");
        serviceInfoMap.put("WGS - PCR-free - 40X", "490193");
        serviceInfoMap.put("WGS - PCR-free - 50X", "490194");
        serviceInfoMap.put("WGS - PCR-free - 60X", "490195");
        serviceInfoMap.put("WGS - PCR-free - 80X", "490196");

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
        DataRecord firstSample = flowCellSamples.get(0).getParentsOfType("Sample", user).get(0);
        List<DataRecord> chargesInfo = outputChargesInfo(firstSample);
        setFieldsForReport(chargesInfo);
        generateiLabChargeSheet();
        return new PluginResult(true);
    }

    /**
     * Logic for charges corresponding to different service types
     * @param firstSample
     * @return all iLab template sheet information for the bulk charge upload
     * */
    private List<DataRecord> outputChargesInfo(DataRecord firstSample) {

        List<DataRecord> chargeInfoRecords = new LinkedList<>();
        try {
            String serviceType = firstSample.getParentsOfType("Request", user).get(0)
                    .getStringVal("RequestName", user);
            String species = firstSample.getStringVal("Species", user);
            String preservation = firstSample.getStringVal("Preservation", user);
            String tumorOrNormal = firstSample.getStringVal("TumorOrNormal", user);
            String assay = firstSample.getStringVal("Assay", user);
            String origin = firstSample.getStringVal("SampleOrigin", user);


            DataRecord [] seqRequeirements = firstSample.getChildrenOfType("SeqRequirement", user);
            String maxNumOfReads = seqRequeirements[0].getStringVal("RequestedReads", user);
            String covrage = seqRequeirements[0].getStringVal("CoverageTarget", user);
            String runLength = seqRequeirements[0].getStringVal("SequencingRunType", user);

            // Request name in Request table is a drop down menu with certain options
            Object serviceId;
            // Adding rows of charges information for each service
            if(serviceType.contains("DNA") && serviceType.contains("Extraction")) {

            }
            if(serviceType.contains("RNA") && serviceType.contains("Extraction")) {

            }
//            if(serviceType.equals("RNAExtraction-COVIDScreen")) {
//
//            }
            if(serviceType.equals("DNA Cleanup")) {

            }
            if(serviceType.equals("RNA Cleanup")) {

            }
            if(serviceType.equals("DNA/RNASimultaneous")) {

            }
            if(serviceType.equals("PATH-DNAExtraction")) {

            }
            if(serviceType.equals("PATH-RNAExtraction")) {

            }
            if(serviceType.equals("PATH-DNA/RNASimultaneous")) {

            }
            if(serviceType.equals("BloodExtraction")) {

            }
            if(serviceType.equals("DNA-QC")) {

            }
            if(serviceType.equals("RNA-QC")) {

            }
            if(serviceType.equals("Library-QC")) {

            }
            if(serviceType.equals("IMPACT341")) {

            }
            if(serviceType.equals("IMPACT341+")) {

            }
            if(serviceType.equals("IMPACT410")) {

            }
            if(serviceType.equals("IMPACT410+")) {

            }
            if(serviceType.equals("IMPACT468")) {

            }
            if(serviceType.equals("IMPACT505")) {

            }
            if(serviceType.equals("PM-IMPACT")) {

            }
            if(serviceType.equals("M-IMPACT")) {

            }
            if(serviceType.equals("HemePACT_v3")) {

            }
            if(serviceType.equals("HemePACT_v3+")) {

            }
            if(serviceType.equals("HemePACT_v4")) {

            }
            if(serviceType.equals("CustomCapture")) {

            }
            if(serviceType.equals("MSK-ACCESS_v1")) {

            }
            if(serviceType.equals("MissionBio")) {

            }
            if(serviceType.equals("RNASeq-TruSeqPolyA")) {

            }
            if(serviceType.equals("RNASeq-KAPAmRNAStranded")) {

            }
            if(serviceType.equals("RNASeq-TruSeqFusion")) {

            }
            if(serviceType.equals("RNASeq-TruSeqRiboDeplete")) {

            }
            if(serviceType.equals("RNASeq-SMARTerAmp")) {

            }
            if(serviceType.equals("Rapid-RCC")) {

            }
            if(serviceType.equals("Archer")) {

            }
            if(serviceType.equals("NanoString")) {

            }
            if(serviceType.equals("10XGenomics_GeneExpression")) {

            }
            if(serviceType.equals("10XGenomics_VDJ")) {

            }
            if(serviceType.equals("10XGenomics_CNV")) {

            }
            if(serviceType.equals("10XGenomics_FeatureBarcoding")) {

            }
            if(serviceType.equals("10XGenomics_Multiome")) {

            }
            if(serviceType.equals("10XGenomics_Visium")) {

            }
            if(serviceType.equals("96Well_SmartSeq2")) {

            }
            if(serviceType.equals("WholeExome + IMPACT")) {

            }
            if(serviceType.equals("WholeExome-KAPALib")) {

            }
            if(serviceType.equals("HumanWholeGenome")) {

            }
            if(serviceType.equals("MouseWholeGenome")) {

            }
            if(serviceType.equals("WholeGenome")) {

            }
            if(serviceType.equals("sWGS")) {

            }
            if(serviceType.equals("ChIPSeq")) {

            }
            if(serviceType.equals("MethylSeq")) {

            }
            if(serviceType.equals("CRISPRSeq")) {

            }
            if(serviceType.equals("shRNAScreen")) {

            }
            if(serviceType.equals("RiboProfileSeq")) {

            }
            if(serviceType.equals("ATACSeq")) {

            }
            if(serviceType.equals("AmpliSeq")) {

            }
            if(serviceType.equals("AmpliconSeq")) {

            }
            if(serviceType.equals("AdaptiveImmunoSeq")) {

            }
            if(serviceType.equals("CLIPSeq")) {

            }
            if(serviceType.equals("HiSeq-Other")) {

            }
            if(serviceType.equals("MiSeq-Other")) {

            }
            if(serviceType.equals("NextSeq-Other")) {

            }
            if(serviceType.equals("NovaSeq-Other")) {

            }
            if(serviceType.equals("Investigator Prepared Libraries")) {
                // sequencing only
            }
            if(serviceType.equals("Investigator Prepared Pools")) {
                // sequencing only
            }
            if(serviceType.equals("ddPCR")) {

            }
            if(serviceType.equals("DLP")) {

            }
            if(serviceType.equals("PED-PEG")) {

            }
            if(serviceType.equals("IGO-Test")) {

            }
            if(serviceType.equals("FragmentAnalysis")) {

            }
            if(serviceType.equals("CellLineAuthentication")) {

            }
            if(serviceType.equals("SingleCellCNV")) {

            }
            if(serviceType.equals("CMO-CH")) {

            }
            if(serviceType.equals("TCRSeq-IGO")) {

            }
            serviceId = serviceInfoMap.get(serviceType);
            // Adding recognized serviceId to the last element of the returned list
            chargeInfoRecords.add(chargeInfoRecords.size(), (DataRecord) serviceId);
        } catch (IoError | RemoteException | NotFound e) {
            logError("An exception occurred while  retrieving first sample's request info");
        }

        return chargeInfoRecords;
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

    /**
     * Setting the iLab template sheet fields
     * @param chargesInformation
     * @return List of map of iLab template fields and their values
     * */
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

                reportFieldValues.put("serviceId", chargesInformation.get(chargesInformation.size() - 1).toString());
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
