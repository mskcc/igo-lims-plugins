package com.velox.sloan.cmo.workflows.dmpbankedsample;

import com.velox.api.util.ServerException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DmpToBankedSampleDataReader {

    public boolean excelFileHasData(Sheet sheet) throws ServerException {
        return sheet.getLastRowNum() >= 1;
    }

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

    public Map<String, Integer> parseExcelFileHeader(Sheet sheet, ArrayList<String> headerValues) {
        Map<String, Integer> headerNames = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        for (String value : headerValues) {
            for (Cell cell : headerRow) {
                if (value.equals(cell.getStringCellValue().trim())) {
                    headerNames.put(cell.getStringCellValue(), cell.getColumnIndex());
                }
            }
        }
        return headerNames;
    }

    public ArrayList<Map<String, Object>> readDmpBankedSampleRecordsFromFile(Sheet sheet, Map<String, Integer> fileHeader, String iLabsId) throws IOException {
        ArrayList<Map<String, Object>> dmpBankedSampleRecords = new ArrayList<>();
        int firstRowAfterHeaderRowWithData = 1;
        for (int rowNum = firstRowAfterHeaderRowWithData; rowNum < sheet.getPhysicalNumberOfRows(); rowNum++) {
            Map<String, Object> newDmpSampleRecord = new HashMap<>();
            Row row = sheet.getRow(rowNum);

            if (row.getCell(fileHeader.get("Well Position")).getStringCellValue().equals("")) {
                continue;
            }


            //OPTIONALS, not on every DMP excel
            //tumorType depends on Sample Class
            String cancerType = "";
            if (optionalExists("Sample Class (Primary Met or Normal)", fileHeader)) {
                cancerType = getOncoCode(row.getCell(fileHeader.get("Tumor Type")).getStringCellValue());
            }
            if (optionalExists("Sample Class (Primary Met or Normal)", fileHeader)) {
                String sampleClass = getOncoCode(row.getCell(fileHeader.get("Sample Class (Primary Met or Normal)")).getStringCellValue());
                newDmpSampleRecord.put("SampleType", sampleClass);
                if (cancerType.equals("") && sampleClass.equals("Normal")) {
                    cancerType = "Normal";
                }
            }
            // Specimen type needs to be capitalized
            if (optionalExists("Specimen Type (Resection Biopsy or Blood)", fileHeader)) {
                String specimenType = row.getCell(fileHeader.get("Specimen Type (Resection Biopsy or Blood)")).getStringCellValue();
                specimenType = specimenType.substring(0, 1).toUpperCase() + specimenType.substring(1);
                newDmpSampleRecord.put("SpecimenType", specimenType);
            }
            if (optionalExists("Collection Year", fileHeader)) {
                newDmpSampleRecord.put("CollectionYear", row.getCell(fileHeader.get("Collection Year")).getStringCellValue());
            }
            if (optionalExists("Sex", fileHeader)) {
                newDmpSampleRecord.put("Gender", row.getCell(fileHeader.get("Sex")).getStringCellValue());
            }

            newDmpSampleRecord.put("TumorType", cancerType);

            String tissueSite = row.getCell(fileHeader.get("Tissue Site")).getStringCellValue();
            if (!tissueSite.toLowerCase().equals("n/a")) {
                newDmpSampleRecord.put("TissueSite", tissueSite);
            }

            // REQUIRED, minimal DMP columns needed
            String nucleicAcidType = row.getCell(fileHeader.get("Nucleic Acid Type (Library or DNA)")).getStringCellValue();
            if (nucleicAcidType.equals("gDNA")) {
                nucleicAcidType = "DNA";
            }
            newDmpSampleRecord.put("NAtoExtract", nucleicAcidType);

            if (nucleicAcidType.equals("Library")) {
                if (optionalExists("Index)", fileHeader)) {
                    newDmpSampleRecord.put("Index", row.getCell(fileHeader.get("Index")).getStringCellValue());
                }
                if (optionalExists("Index Sequence)", fileHeader)) {
                    newDmpSampleRecord.put("IndexSeq", row.getCell(fileHeader.get("Index Sequence")).getStringCellValue());
                }
            }

            // might be string or numeric
            try {
                newDmpSampleRecord.put("Concentration", row.getCell(fileHeader.get("Concentration (ng/ul)")).getNumericCellValue());
            } catch (IllegalStateException e) {
                newDmpSampleRecord.put("Concentration", row.getCell(fileHeader.get("Concentration (ng/ul)")).getStringCellValue());
            }
            // might be string or numeric
            try {
                newDmpSampleRecord.put("Volume", row.getCell(fileHeader.get("Volume (ul)")).getNumericCellValue());
            } catch (IllegalStateException e) {
                newDmpSampleRecord.put("Volume", row.getCell(fileHeader.get("Volume (ul)")).getStringCellValue());
            }
            String patientId = row.getCell(fileHeader.get("DMP ID")).getStringCellValue();

            // first 9 chars of dmpID make up patientId
            Pattern pattern = Pattern.compile("P-[0-9]{7}");
            Matcher matcher = pattern.matcher(patientId);
            if (matcher.matches()) {
                matcher.find();
                patientId = (matcher.group(1));
            }
            newDmpSampleRecord.put("PatientId", patientId);

            // preservation needed for sample origin
            String preservation = row.getCell(fileHeader.get("Preservation (FFPE or Blood)")).getStringCellValue();
            newDmpSampleRecord.put("Preservation", preservation);
            String sampleOrigin = preservation.equals("FFPE") ? "Block" : "Whole Blood";
            newDmpSampleRecord.put("SampleOrigin", sampleOrigin);

            //straightforward fields
            newDmpSampleRecord.put("UserSampleId", row.getCell(fileHeader.get("Investigator Sample ID")).getStringCellValue());
            newDmpSampleRecord.put("Investigator", row.getCell(fileHeader.get("PI Name")).getStringCellValue());
            newDmpSampleRecord.put("PlateId", row.getCell(fileHeader.get("Barcode/Plate ID")).getStringCellValue());
            newDmpSampleRecord.put("RowPosition", String.valueOf(row.getCell(fileHeader.get("Well Position")).getStringCellValue().charAt(0)));
            newDmpSampleRecord.put("ColPosition", String.valueOf(row.getCell(fileHeader.get("Well Position")).getStringCellValue().charAt(1)));
            newDmpSampleRecord.put("Organism", "Human");
            newDmpSampleRecord.put("ServiceId", iLabsId);

            dmpBankedSampleRecords.add(newDmpSampleRecord);
        }
        return sortByWellPosition(dmpBankedSampleRecords);
    }


    private ArrayList<Map<String, Object>> sortByWellPosition(ArrayList<Map<String, Object>> dmpBankedSampleRecords) {

        // Sort by Row Position and Col Position (A1 < B1, A2 > B1)
        Collections.sort(dmpBankedSampleRecords, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                if (Integer.valueOf((String)o1.get("ColPosition")) < Integer.valueOf((String)o2.get("ColPosition"))) {
                    return -1;
                }
                if (Integer.valueOf((String)o1.get("ColPosition")) == Integer.valueOf((String)o2.get("ColPosition"))) {
                    return String.CASE_INSENSITIVE_ORDER.compare((String) o1.get("RowPosition"), (String) o2.get("RowPosition"));
                }
                return 1;
            }

        });


        return dmpBankedSampleRecords;
    }

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
}
