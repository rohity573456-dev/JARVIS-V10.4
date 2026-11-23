package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisMemoryPrivacyTest {
    @Test public void sensitiveCategoriesAreRejectedDeterministically() {
        assertTrue(JarvisMemoryAiWorker.isSensitiveCategory("health"));
        assertTrue(JarvisMemoryAiWorker.isSensitiveCategory("religion"));
        assertTrue(JarvisMemoryAiWorker.isSensitiveCategory("financial_identifier"));
        assertFalse(JarvisMemoryAiWorker.isSensitiveCategory("preference"));
        assertFalse(JarvisMemoryAiWorker.isSensitiveCategory("project"));
    }

    @Test public void sensitiveIdentifiersAndCredentialsAreNeverEligibleForMemory() {
        assertTrue(JarvisMemoryAiWorker.isSensitiveText("My Aadhaar number is 123456789012"));
        assertTrue(JarvisMemoryAiWorker.isSensitiveText("PAN ABCDE1234F"));
        assertTrue(JarvisMemoryAiWorker.isSensitiveText("My password is secret"));
        assertTrue(JarvisMemoryAiWorker.isSensitiveText("I have diabetes"));
        assertFalse(JarvisMemoryAiWorker.isSensitiveText("I prefer concise answers"));
    }

    @Test public void backgroundMemoryLearningNeverSelectsPaidAstra() {
        assertEquals("GEMINI", JarvisMemoryAiWorker.preferredMemoryProvider(true, true, true));
        assertEquals("GROQ", JarvisMemoryAiWorker.preferredMemoryProvider(false, true, true));
        assertEquals("NONE", JarvisMemoryAiWorker.preferredMemoryProvider(false, false, true));
        assertEquals("NONE", JarvisMemoryAiWorker.preferredMemoryProvider(false, false, false));
    }

    @Test public void modelCannotPinArbitraryOrSensitiveFacts() {
        assertTrue(JarvisMemoryAiWorker.isSafePinnedKey("profile.name", "profile"));
        assertTrue(JarvisMemoryAiWorker.isSafePinnedKey("instruction.response_style", "preference"));
        assertFalse(JarvisMemoryAiWorker.isSafePinnedKey("health.diagnosis", "health"));
        assertFalse(JarvisMemoryAiWorker.isSafePinnedKey("general.random_fact", "general"));
    }
}
