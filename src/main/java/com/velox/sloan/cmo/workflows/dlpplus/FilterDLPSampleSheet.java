package com.velox.sloan.cmo.workflows.dlpplus;

import java.io.*;

public class FilterDLPSampleSheet {
/**

@author mirhajf
*/
    private void filterDLPSampleSheet(File inputSampleSheet, File fld) {
        /*
         * 5th field in the sample sheet contains the chip location
         * Lane,Sample_ID,Sample_Plate,Sample_Well,I7_Index_ID,index,index2,Sample_Project,Description
         * 1,Lee_DLP_BC_02_Breast_130092A_1_1_IGO_15607_B_1,Human,DLP,DLPi7_01-i5_01,ACAGTGAT,ACCGTGAT,Project_15607_B,shahs3@mskcc.org
         * DLPi7_01-i5_01: split by "_", take indexes 1 (split by "-" and take index 0) and 3
         * on the fld file find 1/1. On the sample sheet only copy the lines with chip locations that has a value on its corresponding
         **/
        try {
            BufferedReader sampleSheetReader = new BufferedReader(new FileReader(inputSampleSheet));
            BufferedReader fieldFileReader = new BufferedReader(new FileReader(fld));
            BufferedWriter writer = new BufferedWriter(new FileWriter("filteredDLPSampleSheet.csv"));
            String fieldLine, sampleSheetLine;
            // Skipping the field and the sample sheet header lines
            int sampleSheetHeaderLinesToCopy = 18;
            for (int i = 0; i < sampleSheetHeaderLinesToCopy; i++) {
                writer.write(sampleSheetReader.readLine());
            }
            int fieldFileHeaderLinesToSkip = 24;
            for (int i = 0; i < fieldFileHeaderLinesToSkip; i++) {
                fieldFileReader.readLine();
            }

            // Filtering the sample sheet lines based on filed information
            while ((fieldLine = fieldFileReader.readLine()) != null) {
                String fieldRow = fieldLine.split("/")[0];
                String fieldCol = fieldLine.split("/")[1];
                if (fieldLine.split(",").length > 0) {
                    while ((sampleSheetLine = sampleSheetReader.readLine()) != null) {
                        String[] sampleFields = sampleSheetLine.split(",");
                        String fieldWithChipLocInfo = sampleFields[4];
                        String row = fieldWithChipLocInfo.split("_")[1].split("-")[0];
                        String col = fieldWithChipLocInfo.split("_")[2];
                        if (fieldRow.equals(row) && fieldCol.equals(col)) {
                            // keep this line
                            writer.write(sampleSheetLine);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
