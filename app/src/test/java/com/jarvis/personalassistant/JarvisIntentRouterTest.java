package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisIntentRouterTest {
    @Test public void batteryQuestionsAreNotMistakenForDeviceStatus() {
        assertTrue(JarvisIntentRouter.isBatteryStatusQuery("Battery kitni hai?"));
        assertTrue(JarvisIntentRouter.isBatteryStatusQuery("show my battery level"));
        assertFalse(JarvisIntentRouter.isBatteryStatusQuery("Explain battery health"));
        assertFalse(JarvisIntentRouter.isBatteryStatusQuery("How does a lithium battery work?"));
    }

    @Test public void technicalSystemQuestionsStayOnAiPath() {
        assertTrue(JarvisIntentRouter.isSystemReportQuery("system report"));
        assertTrue(JarvisIntentRouter.isSystemReportQuery("phone ka status batao"));
        assertTrue(JarvisIntentRouter.isSystemReportQuery("storage"));
        assertTrue(JarvisIntentRouter.isSystemReportQuery("RAM kitni hai?"));
        assertFalse(JarvisIntentRouter.isSystemReportQuery("Explain Android RAM management"));
        assertFalse(JarvisIntentRouter.isSystemReportQuery("What is storage architecture?"));
    }

    @Test public void timeAndDateNeedActualTimeOrDateIntent() {
        assertTrue(JarvisIntentRouter.isTimeQuery("Abhi kitne baje hain?"));
        assertTrue(JarvisIntentRouter.isDateQuery("Aaj ki date"));
        assertFalse(JarvisIntentRouter.isTimeQuery("Explain time complexity"));
        assertFalse(JarvisIntentRouter.isDateQuery("What is a date type in Java?"));
    }

    @Test public void explanatoryDeviceQuestionsDoNotLaunchSettings() {
        assertTrue(JarvisIntentRouter.isWifiSettingsCommand("open Wi-Fi settings"));
        assertFalse(JarvisIntentRouter.isWifiSettingsCommand("Explain how Wi-Fi works"));
        assertTrue(JarvisIntentRouter.isOpenCameraCommand("camera kholo"));
        assertFalse(JarvisIntentRouter.isOpenCameraCommand("How does a camera sensor work?"));
        assertTrue(JarvisIntentRouter.isOpenYoutubeCommand("open YouTube"));
        assertFalse(JarvisIntentRouter.isOpenYoutubeCommand("How does YouTube recommend videos?"));
    }

    @Test public void commandsAreNormalizedAcrossCaseAndPunctuation() {
        assertTrue(JarvisIntentRouter.isBatteryStatusQuery("BATTERY STATUS!"));
        assertTrue(JarvisIntentRouter.isFlashlightCommand("Turn ON flashlight!"));
        assertTrue(JarvisIntentRouter.isWebSearchCommand("Web search quantum physics"));
    }
}
