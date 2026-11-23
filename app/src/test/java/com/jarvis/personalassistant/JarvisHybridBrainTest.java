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
    @Test public void specialistClassificationUsesWholeWordsNotAccidentalSubstrings() {
        assertEquals(JarvisHybridBrain.Specialist.GENERAL, JarvisHybridBrain.classify("Explain how planets form"));
        assertEquals(JarvisHybridBrain.Specialist.GENERAL, JarvisHybridBrain.classify("How do I decode this message?"));
        assertEquals(JarvisHybridBrain.Specialist.CODING, JarvisHybridBrain.classify("Help me with JavaScript code"));
        assertEquals(JarvisHybridBrain.Specialist.PLANNING, JarvisHybridBrain.classify("Help me with planning"));
    }

    @Test public void specialistDirectiveDoesNotClaimTools() {
        assertTrue(JarvisHybridBrain.systemDirective("build my app").contains("Never invent tool results"));
    }
    @Test public void specialistTasksNeverSilentlyTriggerPaidAstra() {
        assertFalse(JarvisHybridBrain.shouldUseAstra("Help me debug this Java compile error", true));
        assertFalse(JarvisHybridBrain.shouldUseAstra("Research the latest battery technology", true));
        assertFalse(JarvisHybridBrain.shouldUseAstra("Hello JARVIS", true));
        assertFalse(JarvisHybridBrain.shouldUseAstra("Help me debug this Java compile error", false));
    }

    @Test public void explicitAstraPrefixRequestsAstraForGeneralQuestions() {
        assertTrue(JarvisHybridBrain.shouldUseAstra("Astra explain relativity simply", true));
        assertTrue(JarvisHybridBrain.shouldUseAstra("  ASTRA debug this code", true));
        assertFalse(JarvisHybridBrain.shouldUseAstra("Explain relativity simply", true));
        assertFalse(JarvisHybridBrain.shouldUseAstra("Astra explain relativity simply", false));
    }

    @Test public void onlyComplexAstraTasksTriggerSecondPassVerification() {
        assertTrue(JarvisHybridBrain.shouldVerifyAstraAnswer("Debug this Java crash", true));
        assertTrue(JarvisHybridBrain.shouldVerifyAstraAnswer("Research current battery chemistry", true));
        assertFalse(JarvisHybridBrain.shouldVerifyAstraAnswer("Hello", true));
        assertFalse(JarvisHybridBrain.shouldVerifyAstraAnswer("Debug this Java crash", false));
    }

    @Test public void codingAndPlanningPreferGroqWhenBothCloudProvidersExist() {
        assertEquals("GROQ", JarvisHybridBrain.preferredCloudProvider(JarvisHybridBrain.Specialist.CODING, true, true));
        assertEquals("GROQ", JarvisHybridBrain.preferredCloudProvider(JarvisHybridBrain.Specialist.PLANNING, true, true));
    }

    @Test public void generalAndResearchPreferGeminiAndFallbackToGroq() {
        assertEquals("GEMINI", JarvisHybridBrain.preferredCloudProvider(JarvisHybridBrain.Specialist.GENERAL, true, true));
        assertEquals("GEMINI", JarvisHybridBrain.preferredCloudProvider(JarvisHybridBrain.Specialist.RESEARCH, true, true));
        assertEquals("GROQ", JarvisHybridBrain.preferredCloudProvider(JarvisHybridBrain.Specialist.GENERAL, false, true));
        assertEquals("NONE", JarvisHybridBrain.preferredCloudProvider(JarvisHybridBrain.Specialist.GENERAL, false, false));
    }
}
