package com.velox.sloan.cmo.workflows;

import com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc.SampleQcResult;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {

    public static void main(String[] args) {
        Pattern quotedStringPattern = Pattern.compile("\"([^\"]*)\"");
        System.out.println((char)34+"Hello"+(char)34);
        try(BufferedReader reader = new BufferedReader(new FileReader("Test.csv"))) {
            String line = reader.readLine();
            while (line != null) {
                System.out.println(removeThousandSeparator(line, quotedStringPattern));
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
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

