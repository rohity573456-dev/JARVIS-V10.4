package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisStructuredPlannerRoutingTest {
    @Test public void paidAstraPlannerIsOnlySelectedByExplicitPrefix() {
        assertFalse(JarvisStructuredPlanner.shouldUseAstraForPlanning("remind me tomorrow", true));
        assertFalse(JarvisStructuredPlanner.shouldUseAstraForPlanning("Astra", true));
        assertTrue(JarvisStructuredPlanner.shouldUseAstraForPlanning("Astra create a reminder", true));
        assertTrue(JarvisStructuredPlanner.shouldUseAstraForPlanning("  ASTRA analyse this plan", true));
        assertFalse(JarvisStructuredPlanner.shouldUseAstraForPlanning("Astra create a reminder", false));
        assertFalse(JarvisStructuredPlanner.shouldUseAstraForPlanning(null, true));
    }
}
