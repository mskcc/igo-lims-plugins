package com.velox.sloan.cmo.workflows;

import com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc.SampleQcResult;

public class Test {

    public static void main (String[] args){
        SampleQcResult result = new SampleQcResult("TestSample", 199.35477681428773,0.8, 29.20000000000003, true);
        System.out.println(result.toString());
    }
}
