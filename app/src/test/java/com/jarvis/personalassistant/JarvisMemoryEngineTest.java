package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisMemoryEngineTest {
    @Test public void localVectorIsDeterministic(){
        float[] a=JarvisLocalVectorEngine.embed("I live in Deoria");
        float[] b=JarvisLocalVectorEngine.embed("I live in Deoria");
        assertEquals(JarvisLocalVectorEngine.DIMENSIONS,a.length);
        assertTrue(JarvisLocalVectorEngine.similarity(a,b)>0.99);
    }
    @Test public void relatedConceptsShareVectorSpace(){
        float[] a=JarvisLocalVectorEngine.embed("my job and office");
        float[] b=JarvisLocalVectorEngine.embed("my work and career");
        assertTrue(JarvisLocalVectorEngine.similarity(a,b)>0.10);
    }
    @Test public void explicitYearIsRecognized(){
        JarvisTimeQuery.Window w=JarvisTimeQuery.parse("what was my job in 2024");
        assertNotNull(w);assertEquals("year 2024",w.label);assertTrue(w.endAt>w.startAt);
    }
    @Test public void relativeYearIsRecognized(){
        JarvisTimeQuery.Window w=JarvisTimeQuery.parse("what did I do last year");
        assertNotNull(w);assertTrue(w.endAt>w.startAt);
    }
    @Test public void ordinaryQuestionHasNoTimeScope(){
        assertNull(JarvisTimeQuery.parse("what is my favourite food"));
    }
}