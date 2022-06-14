package com.velox.sloan.cmo.workflows.dmpbankedsample;

import com.velox.api.plugin.PluginLogger;
import com.velox.api.util.ClientCallbackOperations;
import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.workflows.Test;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.*;
import java.security.cert.X509Certificate;


public class DmpToBankedSampleDataReader {

    public boolean excelFileHasData(Sheet sheet) throws ServerException {
        return sheet.getLastRowNum() >= 1;
    }

    //    check excel against min. required headers
    public boolean excelFileHasValidHeader(Sheet sheet, ArrayList<String> expectedHeaderValues) {
        Row row = sheet.getRow(0);
        ArrayList<String> headerValuesInFile = new ArrayList<>();
        for (String value : expectedHeaderValues) {
            for (Cell cell : row) {
                if (value.equals(cell.getStringCellValue())) {
                    headerValuesInFile.add(cell.getStringCellValue());
                }
            }
        }
        return headerValuesInFile.containsAll(expectedHeaderValues);
    }

    public Map<String, Integer> parseExcelFileHeader(Sheet sheet) {
        Map<String, Integer> headerNames = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        for (Cell cell : headerRow) {
            headerNames.put(cell.getStringCellValue(), cell.getColumnIndex());
        }
        return headerNames;
    }

    public ArrayList<Map<String, Object>> readDmpBankedSampleRecordsFromFile(Sheet sheet, Map<String, Integer> fileHeader, String iLabsId, String username, Map<String, String>dualIdtIndices, ClientCallbackOperations clientCallback) throws IOException, ServerException {
        ArrayList<Map<String, Object>> dmpBankedSampleRecords = new ArrayList<>();
        int firstRowAfterHeaderRowWithData = 1;
        // every banked sample needs a transaction id
        long transactionId = Instant.now().toEpochMilli() / 1000L;

        for (int rowNum = firstRowAfterHeaderRowWithData; rowNum < sheet.getPhysicalNumberOfRows(); rowNum++) {
            Map<String, Object> newDmpSampleRecord = new HashMap<>();
            Row row = sheet.getRow(rowNum);

            // skip rows without well positions
            if (row.getCell(fileHeader.get("Well Position")).getStringCellValue().equals("")) {
                continue;
            }

            // fields required in Banked but not present in DMP excel. DMP only deals with Humans
            newDmpSampleRecord.put("Organism", "Human");
            newDmpSampleRecord.put("Species", "Human");
            newDmpSampleRecord.put("TransactionId", transactionId);
            // Investigator/PM will be the currently logged in user (for sample-submission we will get this with get_jwt_identity()
            newDmpSampleRecord.put("Investigator", username);

            // straightforward fields
            newDmpSampleRecord.put("PlateId", row.getCell(fileHeader.get("Barcode/Plate ID")).getStringCellValue());
            newDmpSampleRecord.put("UserSampleID", row.getCell(fileHeader.get("Investigator Sample ID")).getStringCellValue());
            newDmpSampleRecord.put("OtherSampleId", row.getCell(fileHeader.get("Investigator Sample ID")).getStringCellValue());
            // these two will not be returned from DMP
            // PM's will put recipe in when the Sample Submission grid is pre-filled with the DMP data
            newDmpSampleRecord.put("Recipe", row.getCell(fileHeader.get("Recipe")).getStringCellValue());
            // PM's will put serviceId in when the Sample Submission grid is pre-filled with the DMP data, currently the LIMS shows a prompt for them to enter on excel upload
            newDmpSampleRecord.put("ServiceId", iLabsId);

            // optional fields
            if (optionalExists("Collection Year", fileHeader)) {
                newDmpSampleRecord.put("CollectionYear", row.getCell(fileHeader.get("Collection Year")).getStringCellValue());
            }
            if (optionalExists("Sex", fileHeader)) {
                newDmpSampleRecord.put("Gender", row.getCell(fileHeader.get("Sex")).getStringCellValue());
            }


            // might be string or numeric
            try {
                newDmpSampleRecord.put("Volume", row.getCell(fileHeader.get("Volume (ul)")).getNumericCellValue());
            } catch (IllegalStateException e) {
                newDmpSampleRecord.put("Volume", row.getCell(fileHeader.get("Volume (ul)")).getStringCellValue());
            }
            // might be string or numeric
            try {
                newDmpSampleRecord.put("Concentration", row.getCell(fileHeader.get("Concentration (ng/ul)")).getNumericCellValue());
            } catch (IllegalStateException e) {
                newDmpSampleRecord.put("Concentration", row.getCell(fileHeader.get("Concentration (ng/ul)")).getStringCellValue());
            }


            // fields with needed translationlogic
            String wellPos = String.valueOf(row.getCell(fileHeader.get("Well Position")).getStringCellValue());
            newDmpSampleRecord.put("RowPosition", String.valueOf(wellPos.charAt(0)));
            newDmpSampleRecord.put("ColPosition", wellPos.substring(1));

            // comes from DMP as either Library or gDNA
            String nucleicAcidType = row.getCell(fileHeader.get("Nucleic Acid Type (Library or DNA)")).getStringCellValue();
            if (nucleicAcidType.equals("gDNA")) {
                nucleicAcidType = "DNA";
            } else nucleicAcidType = "DNA Library";
            newDmpSampleRecord.put("SampleType", nucleicAcidType);


            // only Libraries come with Indexes. If the Index is named like DMP0xyz we have to remove the 0
            if (nucleicAcidType.equals("DNA Library") || nucleicAcidType.equals("Library")) {
                String indexId = row.getCell(fileHeader.get("Index")).getStringCellValue();
                String dmpIndexI7 = row.getCell(fileHeader.get("Index Sequence")).getStringCellValue();
                String dmpIndexI5 = "";
                Object i5ValFromFile = row.getCell(fileHeader.get("Index Sequence I5"));
                if (!Objects.isNull(i5ValFromFile)){
                    dmpIndexI5 = i5ValFromFile.toString();
                }
                if (indexId.startsWith("DMP0")) {
                    indexId = indexId.replaceFirst("0", "");
                }
                if (!(StringUtils.isBlank(dmpIndexI5)) && !(StringUtils.isBlank(dmpIndexI7))){
                    String dualSequence = dmpIndexI7 + "-" + dmpIndexI5;
                    String dualIndexId = dualIdtIndices.get(dualSequence);
                    if(StringUtils.isBlank(dualIndexId)){
                        clientCallback.displayError(String.format("Dual Index Barcode Sequence %s parsed from the sheet is not found in LIMS Index Assignment records.", dualSequence));
                    }
                    newDmpSampleRecord.put("BarcodeId", dualIndexId);
                }
                else{
                    newDmpSampleRecord.put("BarcodeId", indexId);
                }
            }

            // fields that need to be translated using other APIs
            // http://oncotree.mskcc.org/#/home
            String cancerType = getOncoCode(row.getCell(fileHeader.get("Tumor Type")).getStringCellValue());
            // if Metastastic then Metastasis else DMP value
            String sampleClass = row.getCell(fileHeader.get("Sample Class (Primary, Met or Normal)")).getStringCellValue();
            sampleClass = sampleClass.equals("Metastatic") ? "Metastasis" : sampleClass;
            newDmpSampleRecord.put("SampleClass", sampleClass);
            // TumorOrNormal is Normal if DMP is Normal, Tumor for everything else
            if (cancerType.equals("") && sampleClass.equals("Normal")) {
                cancerType = "Normal";
                newDmpSampleRecord.put("TumorOrNormal", "Normal");
            } else {
                newDmpSampleRecord.put("TumorOrNormal", "Tumor");
            }
            newDmpSampleRecord.put("TumorType", cancerType);

            // PatientId redaction
            // CMO Patient ID is important for PMs
            String patientId = "";
            patientId = String.valueOf(row.getCell(fileHeader.get("MRN")).getStringCellValue());
            newDmpSampleRecord.put("CMOPatientId", "C-" + crdb(patientId, clientCallback));
            newDmpSampleRecord.put("PatientId", "MRN_REDACTED");

            // preservation and sampleOrigin can only be filled after cancerType was found because they all depend on each other
            // specimenType impacts preservation AND depends on sampleClass
            String preservation = row.getCell(fileHeader.get("Preservation (FFPE or Blood)")).getStringCellValue();
            String sampleOrigin = preservation.equals("FFPE") ? "Tissue" : "Whole Blood";
            newDmpSampleRecord.put("SampleOrigin", sampleOrigin);

            String specimenType = row.getCell(fileHeader.get("Specimen Type (Resection, Biopsy or Blood)")).getStringCellValue();
            if (!specimenType.equals("")) {
                if (specimenType.equals("N/A")) {
                    specimenType = "Biopsy";
                }
                if (specimenType.toLowerCase().equals("cytology")) {
                    preservation = "Frozen";
                    specimenType = "other";
                } else if (sampleClass.equals("Normal")) {
                    specimenType = "Blood";
                } else {
                    specimenType = specimenType.substring(0, 1).toUpperCase() + specimenType.substring(1);
                }
            }
            newDmpSampleRecord.put("SpecimenType", specimenType);
            String samplePreservation = preservation.equals("Blood") ? "EDTA-Streck" : preservation;
            newDmpSampleRecord.put("Preservation", samplePreservation);
            newDmpSampleRecord.put("RequestedCoverage", row.getCell(fileHeader.get("Requested Coverage")).getStringCellValue());
            // add to list
            dmpBankedSampleRecords.add(newDmpSampleRecord);
        }
        //sort list!
        // Samples need to be sorted by Well Position. Row first, then column
        ArrayList<Map<String, Object>> dmpBankedSampleRecordsSorted = sortByWellPosition(dmpBankedSampleRecords);
        // After sorting, RowIndex is set to the sorted row index. starting at 1 because non-developers use this.
        for (int counter = 0; counter < dmpBankedSampleRecordsSorted.size(); counter++) {
            Map<String, Object> record = dmpBankedSampleRecordsSorted.get(counter);
            record.put("RowIndex", counter + 1);
            dmpBankedSampleRecordsSorted.set(counter, record);
        }
        return dmpBankedSampleRecordsSorted;
    }


    private ArrayList<Map<String, Object>> sortByWellPosition(ArrayList<Map<String, Object>> dmpBankedSampleRecords) {
        // Sort by Row Position and Col Position (A1 < B1, A2 > B1) I compare the numbers/cols first, then letters/rows
        // so if 3 < 4 then sorting is correct. if 4 > 3, then look at letters to determine how to sort
        Collections.sort(dmpBankedSampleRecords, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                if (Integer.valueOf((String) o1.get("ColPosition")) < Integer.valueOf((String) o2.get("ColPosition"))) {
                    return -1;
                }
                if (Integer.valueOf((String) o1.get("ColPosition")) == Integer.valueOf((String) o2.get("ColPosition"))) {
                    return String.CASE_INSENSITIVE_ORDER.compare((String) o1.get("RowPosition"), (String) o2.get("RowPosition"));
                }
                return 1;
            }
        });


        return dmpBankedSampleRecords;
    }

    // OncoTree could be in there as full name or just ID, we send it as a name first, if OncoTree can't find it, we try it as ID
    // if it is found, we put the ID in BankedSample. If we don't find it, we put an error message
    public String getOncoCode(String oncoName) throws IOException, FileNotFoundException {
        if (oncoName.equals("") || oncoName.toLowerCase().equals("n/a")) {
            return "";
        }
        StringBuffer response = new StringBuffer();
        // if onco name search leads to FileNotFound, DMP might have exported code instead
        try {
            URL url = new URL("http://oncotree.mskcc.org/api/tumorTypes/search/name/" + oncoName.replace(" ", "%20"));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            con.disconnect();
        } catch (FileNotFoundException e) {
            try {
                URL url = new URL("http://oncotree.mskcc.org/api/tumorTypes/search/code/" + oncoName.replace(" ", "%20"));
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                con.disconnect();
            } catch (FileNotFoundException f) {
                return "Neither OncoTree code nor name found for: " + oncoName;
            }
        }

        JSONParser parser = new JSONParser();
        String code = "";
        // onco tree returns array, has to be parsed to object
        JSONArray oncoArray = new JSONArray();
        try {
            oncoArray = (JSONArray) parser.parse(response.toString());
            Iterator i = oncoArray.iterator();
            while (i.hasNext()) {
                JSONObject oncoObject = (JSONObject) i.next();
                code = (String) oncoObject.get("code");
            }
        } catch (
                ParseException e) {
            e.printStackTrace();
        }
        return code;
    }

    private boolean optionalExists(String column, Map<String, Integer> fileHeader) {
        return fileHeader.get(column) != null;
    }

    // Patient ID redaction using CRDB, for a Python example check sample-submission-backend/views/upload: /patientIdConverter
    public String crdb(String patientId, ClientCallbackOperations cb) throws IOException, FileNotFoundException, ServerException {

        // crdb endpoint for this service was updated on February 19 2021
        // curl  -u cmoint:cmointp "https://plcrdbapp1.mskcc.org:7002/rest/cmo/getDataAUTH?mrn=00300678&sid=P1"
        StringBuilder response = new StringBuilder();
        String resourceFile = Objects.requireNonNull(Test.class.getClassLoader().getResource("properties")).getPath();
        try (InputStream input = new FileInputStream(resourceFile)) {
            //java.lang.System.setProperty("appdynamics.force.default.ssl.certificate.validation", "false");
            disable();
            URL url = new URL("https://plcrdbapp1.mskcc.org:7002/rest/cmo/getDataAuthV2");
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            con.setDoInput(true);
            Properties prop = new Properties();
            prop.load(input);
            String encodedUn = new String (Base64.encodeBase64(prop.get("crdbUn").toString().getBytes()));
            String encodedPw = new String (Base64.encodeBase64(prop.get("crdbPw").toString().getBytes()));
            String body = String.format("{\"username\": \"%s\" , \"password\": \"%s\", \"mrn\": \"%s\", \"sid\": \"%s\"}", encodedUn, encodedPw, patientId, prop.get("crdbSid"));
            OutputStream os = con.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            con.disconnect();
        } catch (FileNotFoundException e) {
            cb.displayError(String.format("CRDB connection error: %s", ExceptionUtils.getStackTrace(e)));
            return "CRDB Connection Error";
        }
        JSONParser patient_parser = new JSONParser();
        try {
            JSONObject json_response = (JSONObject) patient_parser.parse(response.toString());
            if (json_response.get("PRM_JOB_STATUS").equals("0")) {
                return (String) json_response.get("PRM_PT_ID");
            } else return (String) json_response.get("PRM_ERR_MSG");
        } catch (ParseException e) {
            cb.displayError(String.format("Error while getting scrambled 'PATIENT ID' value from CRDB: %s", ExceptionUtils.getStackTrace(e)));
            return "CRDB Error";
        }
    }

    public static void disable() {
        try {
            SSLContext sslc = SSLContext.getInstance("TLS");
            TrustManager[] trustManagerArray = { new NullX509TrustManager() };
            sslc.init(null, trustManagerArray, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new NullHostnameVerifier());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static class NullX509TrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            System.out.println();
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            System.out.println();
        }
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static class NullHostnameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
