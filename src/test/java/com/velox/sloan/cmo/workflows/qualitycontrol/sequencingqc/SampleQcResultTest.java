package com.velox.sloan.cmo.workflows.qualitycontrol.sequencingqc;

import org.junit.Test;

import static org.junit.Assert.*;

public class SampleQcResultTest {

    private SampleQcResult qcResult = new SampleQcResult("123456", 600.0, 0.1, 2.0, false);
    private SampleQcResult qcResult0 = new SampleQcResult("123456", 15, 0.1, 2.0, false);
    private SampleQcResult qcResult1 = new SampleQcResult("123456", 8, 0.1, 2.0, false);
    private SampleQcResult qcResult2 = new SampleQcResult("123456", 600.0, 0.1, 1.0, true);
    private SampleQcResult qcResult3 = new SampleQcResult("123456", 60.0, 0.1, 1.0, true);
    private SampleQcResult qcResult4 = new SampleQcResult("123456", 19.0, 0.1, 1.0, true);

    private SampleQcResult qcResult5 = new SampleQcResult("123456", 600.0, 0.1, 1.0, false);
    private SampleQcResult qcResult6 = new SampleQcResult("123456", 600.0, 1.0, 1.0, false);
    private SampleQcResult qcResult7 = new SampleQcResult("123456", 600.0, 2.1, 1.0, false);
    private SampleQcResult qcResult8 = new SampleQcResult("123456", 600.0, 0.4, 1.0, true);
    private SampleQcResult qcResult9 = new SampleQcResult("123456", 600.0, 0.5, 1.0, true);
    private SampleQcResult qcResult10 = new SampleQcResult("123456", 600.0, 10.1, 1.0, true);


    private SampleQcResult qcResult11 = new SampleQcResult("123456", 600.0, 0.1, 0.9, false);
    private SampleQcResult qcResult12 = new SampleQcResult("123456", 600.0, 0.1, 10.0, false);
    private SampleQcResult qcResult13 = new SampleQcResult("123456", 600.0, 0.1, 20.1, false);
    private SampleQcResult qcResult14 = new SampleQcResult("123456", 600.0, 0.1, 1.99, true);
    private SampleQcResult qcResult15 = new SampleQcResult("123456", 600.0, 0.1, 2.0, true);
    private SampleQcResult qcResult16 = new SampleQcResult("123456", 600.0, 0.1, 50.1, true);

    @Test
    public void getIgoRecommendationAnnotation() {
        //Annotation tests based on Quantity and IGO library.
        assertEquals("Passed", qcResult.getIgoRecommendationAnnotation());
        assertEquals("Try", qcResult0.getIgoRecommendationAnnotation());
        assertEquals("Failed", qcResult1.getIgoRecommendationAnnotation());
        //Annotation tests based on Quantity and User submitted library.
        assertEquals("Passed", qcResult2.getIgoRecommendationAnnotation());
        assertEquals("Try", qcResult3.getIgoRecommendationAnnotation());
        assertEquals("Failed", qcResult4.getIgoRecommendationAnnotation());


        //Annotation tests based on Adapter Percentage and IGO library.
        assertEquals("Passed", qcResult5.getIgoRecommendationAnnotation());
        assertEquals("Try", qcResult6.getIgoRecommendationAnnotation());
        assertEquals("Failed", qcResult7.getIgoRecommendationAnnotation());
        //Annotation tests based on Adapter Percentage and User submitted library.
        assertEquals("Passed", qcResult8.getIgoRecommendationAnnotation());
        assertEquals("Try", qcResult9.getIgoRecommendationAnnotation());
        assertEquals("Failed", qcResult10.getIgoRecommendationAnnotation());


        //Annotation tests based on Percent Fragment greater than 1kbp and IGO library.
        assertEquals("Passed", qcResult11.getIgoRecommendationAnnotation());
        assertEquals("Try", qcResult12.getIgoRecommendationAnnotation());
        assertEquals("Failed", qcResult13.getIgoRecommendationAnnotation());
        //Annotation tests based on Percent Fragment greater than 1kbp and User submitted library.
        assertEquals("Passed", qcResult14.getIgoRecommendationAnnotation());
        assertEquals("Try", qcResult15.getIgoRecommendationAnnotation());
        assertEquals("Failed", qcResult16.getIgoRecommendationAnnotation());
    }
}