package com.velox.sloan.cmo.workflows.kapalibrary;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SampleControlMakerTest {

    @Test
    public void getNextPooledNormalSampleIdsFirstTime() {
        List<String> l = SampleControlMaker.getNextPooledNormalSampleIds("xyz");
        assertEquals("FROZENPOOLEDNORMAL-1", l.get(0));
        assertEquals("FFPEPOOLEDNORMAL-1", l.get(1));
    }

    @Test
    public void getNextPooledNormalSampleIdsIncrementByOne() {
        List<String> l = SampleControlMaker.getNextPooledNormalSampleIds("FFPEPOOLEDNORMAL-1");
        String one = l.get(0);
        String two = l.get(1);
        System.out.println("NEXT CONTROL ID:" + one);
        assertEquals("FROZENPOOLEDNORMAL-2", one);
        assertEquals("FFPEPOOLEDNORMAL-2", two);
    }
}