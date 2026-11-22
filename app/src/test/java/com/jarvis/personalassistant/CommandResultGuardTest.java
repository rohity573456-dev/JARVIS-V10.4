package com.jarvis.personalassistant;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class CommandResultGuardTest {
    @Test public void rejectsEmptyAndWakeOnlyResults() {
        assertFalse(CommandResultGuard.isUsable("", Float.NaN));
        assertFalse(CommandResultGuard.isUsable("Hey Jarvis", Float.NaN));
        assertFalse(CommandResultGuard.isUsable("Okay, Jarvis!", Float.NaN));
    }
    @Test public void stripsWakePrefixFromFullUtterance() {
        assertEquals("what time is it", CommandResultGuard.cleanDirectedCommand("Hey Jarvis, what time is it?"));
        assertEquals("battery status", CommandResultGuard.cleanDirectedCommand("Jarvis battery status"));
    }
    @Test public void preservesJarvisMentionInsideARealQuestion() {
        assertEquals("who is jarvis", CommandResultGuard.cleanDirectedCommand("Who is Jarvis?"));
    }
    @Test public void rejectsLowConfidenceButAllowsMissingConfidence() {
        assertFalse(CommandResultGuard.isUsable("Turn on the lights", 0.12f));
        assertTrue(CommandResultGuard.isUsable("Turn on the lights", Float.NaN));
    }
    @Test public void selectsHighestConfidenceUsableAlternative() {
        ArrayList<String> candidates = new ArrayList<>(Arrays.asList("Hey Jarvis", "Jarvis what is the weather", "Turn on the lights"));
        assertEquals("turn on the lights", CommandResultGuard.selectBest(candidates, new float[]{0.99f, 0.62f, 0.91f}));
    }
    @Test public void returnsEmptyWhenEveryAlternativeIsWakeOnlyOrLowConfidence() {
        ArrayList<String> candidates = new ArrayList<>(Arrays.asList("Jarvis", "Hey Jarvis"));
        assertEquals("", CommandResultGuard.selectBest(candidates, new float[]{0.9f, 0.8f}));
    }
}
