package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandResultGuardTest {
    @Test public void rejectsWakePhraseOnly() {
        assertFalse(CommandResultGuard.isUsable("Hey Jarvis", Float.NaN));
        assertFalse(CommandResultGuard.isUsable("JARVIS", Float.NaN));
    }
    @Test public void acceptsARealCommandWhenConfidenceIsUnavailable() {
        assertTrue(CommandResultGuard.isUsable("Open settings", Float.NaN));
    }
    @Test public void rejectsLowConfidenceTranscript() {
        assertFalse(CommandResultGuard.isUsable("Open settings", 0.12f));
    }
    @Test public void acceptsConfidentTranscript() {
        assertTrue(CommandResultGuard.isUsable("Open settings", 0.82f));
    }
    @Test public void rejectsEmptyTranscript() {
        assertFalse(CommandResultGuard.isUsable("   ", Float.NaN));
    }
}
