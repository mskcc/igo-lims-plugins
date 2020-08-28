package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import org.junit.Test;

import static org.junit.Assert.*;

public class SampleQcResultTest {

    SampleQcResult qcResult = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    SampleQcResult qcResult0 = new SampleQcResult("123456", 15, 0.1, 2.0, false);
    SampleQcResult qcResult1 = new SampleQcResult("123456", 8, 0.1, 2.0, false);
    SampleQcResult qcResult2 = new SampleQcResult("123456", 600.0, 0.1, 1.0, true);
    SampleQcResult qcResult3 = new SampleQcResult("123456", 60.0, 0.1, 1.0, true);
    SampleQcResult qcResult4 = new SampleQcResult("123456", 19.0, 0.1, 1.0, true);

    SampleQcResult qcResult5 = new SampleQcResult("123456", 600.0, 0.1, 1.0, false);
    SampleQcResult qcResult6 = new SampleQcResult("123456", 600.0, 1.0, 1.0, false);
    SampleQcResult qcResult7 = new SampleQcResult("123456", 600.0, 2.1, 1.0, false);
    SampleQcResult qcResult8 = new SampleQcResult("123456", 600.0, 0.4, 1.0, true);
    SampleQcResult qcResult9 = new SampleQcResult("123456", 600.0, 0.5, 1.0, true);
    SampleQcResult qcResult10 = new SampleQcResult("123456", 600.0, 10.1, 1.0, true);


    SampleQcResult qcResult11 = new SampleQcResult("123456", 600.0, 0.1, 0.9, false);
    SampleQcResult qcResult12 = new SampleQcResult("123456", 600.0, 0.1, 10.0, false);
    SampleQcResult qcResult13 = new SampleQcResult("123456", 600.0, 0.1, 20.1, false);
    SampleQcResult qcResult14 = new SampleQcResult("123456", 600.0, 0.1, 1.99, true);
    SampleQcResult qcResult15 = new SampleQcResult("123456", 600.0, 0.1, 2.0, true);
    SampleQcResult qcResult16 = new SampleQcResult("123456", 600.0, 0.1, 50.1, true);

    SampleQcResult qcResult17 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    SampleQcResult qcResult18 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    SampleQcResult qcResult19 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    SampleQcResult qcResult20 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    SampleQcResult qcResult21 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    SampleQcResult qcResult22 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);

    //SampleQcResult qcResult11 = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);

    @Test
    public void getIgoRecommendationAnnotation() {
        //Annotation tests based on Quantity and IGO library.
        assertTrue(qcResult.getIgoRecommendationAnnotation().equals("Passed"));
        assertTrue(qcResult0.getIgoRecommendationAnnotation().equals("Try"));
        assertTrue(qcResult1.getIgoRecommendationAnnotation().equals("Failed"));
        //Annotation tests based on Quantity and User submitted library.
        assertTrue(qcResult2.getIgoRecommendationAnnotation().equals("Passed"));
        assertTrue(qcResult3.getIgoRecommendationAnnotation().equals("Try"));
        assertTrue(qcResult4.getIgoRecommendationAnnotation().equals("Failed"));


        //Annotation tests based on Adapter Percentage and IGO library.
        assertTrue(qcResult5.getIgoRecommendationAnnotation().equals("Passed"));
        assertTrue(qcResult6.getIgoRecommendationAnnotation().equals("Try"));
        assertTrue(qcResult7.getIgoRecommendationAnnotation().equals("Failed"));
        //Annotation tests based on Adapter Percentage and User submitted library.
        assertTrue(qcResult8.getIgoRecommendationAnnotation().equals("Passed"));
        assertTrue(qcResult9.getIgoRecommendationAnnotation().equals("Try"));
        assertTrue(qcResult10.getIgoRecommendationAnnotation().equals("Failed"));


        //Annotation tests based on Percent Fragment greater than 1kbp and IGO library.
        assertTrue(qcResult11.getIgoRecommendationAnnotation().equals("Passed"));
        assertTrue(qcResult12.getIgoRecommendationAnnotation().equals("Try"));
        assertTrue(qcResult13.getIgoRecommendationAnnotation().equals("Failed"));
        //Annotation tests based on Percent Fragment greater than 1kbp and User submitted library.
        assertTrue(qcResult14.getIgoRecommendationAnnotation().equals("Passed"));
        assertTrue(qcResult15.getIgoRecommendationAnnotation().equals("Try"));
        assertTrue(qcResult16.getIgoRecommendationAnnotation().equals("Failed"));
    }
}