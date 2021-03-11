package com.velox.sloan.cmo.workflows;

import com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc.SampleQcResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {

    public static void main(String[] args) {
//        Pattern quotedStringPattern = Pattern.compile("\"([^\"]*)\"");
//        System.out.println((char)34+"Hello"+(char)34);
//        try(BufferedReader reader = new BufferedReader(new FileReader("Test.csv"))) {
//            String line = reader.readLine();
//            while (line != null) {
//                System.out.println(removeThousandSeparator(line, quotedStringPattern));
//                line = reader.readLine();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }






        StringBuffer response = new StringBuffer();
        String patientId_scrambled = "";
        String mrn = "00300678";
        String resourceFile = Objects.requireNonNull(Test.class.getClassLoader().getResource("properties")).getPath();
        try (InputStream input = new FileInputStream(resourceFile)) {
            URL url = new URL("https://plcrdbapp1.mskcc.org:7002/rest/cmo/getDataAuthV2");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoOutput(true);
            con.setDoInput(true);
            Properties prop = new Properties();
            prop.load(input);
            String encodedUn = new String (Base64.encodeBase64(prop.get("crdbUn").toString().getBytes()));
            String encodedPw = new String (Base64.encodeBase64(prop.get("crdbPw").toString().getBytes()));
            String body = String.format("{\"username\": \"%s\" , \"password\": \"%s\", \"mrn\": \"%s\", \"sid\": \"%s\"}", encodedUn, encodedPw, mrn, prop.get("crdbSid"));
            System.out.println(body);
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
        } catch (Exception e) {
            System.out.print(e);
        }
        JSONParser patient_parser = new JSONParser();
        try {
            JSONObject json_response = (JSONObject) patient_parser.parse(response.toString());
            System.out.println(json_response);
            if (json_response.get("PRM_JOB_STATUS").equals("0")) {
                System.out.println((String) json_response.get("PRM_PT_ID"));
            } else System.out.println((String) json_response.get("PRM_ERR_MSG"));
        } catch (ParseException e) {
            e.printStackTrace();
            System.out.println("CRDB Error");
        }
    }


    private static String removeThousandSeparator(String line, Pattern pattern) {
        String updatedLine = line;
         Matcher m = pattern.matcher(line);
            while (m.find()) {
                String val = m.group(1);
                updatedLine = updatedLine.replace(val, val.replace(",", "")).replace("\"", "");
            }
        return updatedLine;
    }

//    private static String removeQuotesFromString(String val){
//        String updatedVal="";
//        for (char ch : val.toCharArray()){
//            updatedVal = updatedVal.concat(ch!=(char)34 ? String.valueOf(ch) : "");
//        }
//        return updatedVal;
//    }
}

