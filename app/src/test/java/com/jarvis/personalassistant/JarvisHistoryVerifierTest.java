package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisHistoryVerifierTest {
    @Test public void attachesSpeakerAttributedEvidence() {
        String result = JarvisHistoryVerifier.attachEvidence("BASE", "[E1] JARVIS | 2026-11-23 10:00 | I said 8 batteries.");
        assertTrue(result.contains("VERIFIABLE CONVERSATION EVIDENCE"));
        assertTrue(result.contains("[E1] JARVIS"));
        assertTrue(result.contains("I said 8 batteries."));
    }

    @Test public void emptyEvidenceDoesNotChangeContext() {
        assertEquals("BASE", JarvisHistoryVerifier.attachEvidence("BASE", " "));
        assertFalse(JarvisHistoryVerifier.hasEvidence(""));
    }

    @Test public void evidenceIsBounded() {
        String huge = new String(new char[9000]).replace('\0', 'x');
        String result = JarvisHistoryVerifier.attachEvidence("BASE", huge);
        assertTrue(result.length() <= 5700);
    }

    @Test public void nullContextAndEvidenceAreSafe() {
        assertEquals("", JarvisHistoryVerifier.attachEvidence(null, null));
        assertFalse(JarvisHistoryVerifier.hasEvidence(null));
    }
}
