package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisHybridBrainTest {
    @Test public void routesCodingRequests() {
        assertEquals(JarvisHybridBrain.Specialist.CODING, JarvisHybridBrain.classify("Debug my Kotlin app and build APK"));
    }
    @Test public void routesDataAnalysisRequests() {
        assertEquals(JarvisHybridBrain.Specialist.DATA_ANALYSIS, JarvisHybridBrain.classify("Analyse this Excel and forecast profit"));
    }
    @Test public void routesResearchRequests() {
        assertEquals(JarvisHybridBrain.Specialist.RESEARCH, JarvisHybridBrain.classify("Research latest Android APIs"));
    }
    @Test public void defaultsToGeneral() {
        assertEquals(JarvisHybridBrain.Specialist.GENERAL, JarvisHybridBrain.classify("Hello Jarvis"));
    }
    @Test public void specialistDirectiveDoesNotClaimTools() {
        assertTrue(JarvisHybridBrain.systemDirective("build my app").contains("Never invent tool results"));
    }
}
