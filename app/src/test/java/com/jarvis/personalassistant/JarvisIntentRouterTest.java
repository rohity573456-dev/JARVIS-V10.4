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

    @Test public void backgroundVoiceUsesStrictLocalActionIntents() {
        assertFalse(JarvisIntentRouter.isBatteryStatusQuery("Explain battery health"));
        assertFalse(JarvisIntentRouter.isBatteryStatusQuery("How does a battery management system work?"));
        assertFalse(JarvisIntentRouter.isTimeQuery("Explain time complexity"));
        assertFalse(JarvisIntentRouter.isDateQuery("Explain date parsing"));
        assertFalse(JarvisIntentRouter.isFlashlightCommand("How does a flashlight circuit work?"));
        assertFalse(JarvisIntentRouter.isVolumeUpCommand("Explain volume normalization"));
        assertTrue(JarvisIntentRouter.isFlashlightCommand("torch chalu karo"));
        assertTrue(JarvisIntentRouter.isVolumeDownCommand("volume down"));
    }

    @Test public void privateModeTransitionsAreExplicitLocalCommands() {
        assertTrue(JarvisIntentRouter.isPrivateModeEnableCommand("memory privacy on"));
        assertTrue(JarvisIntentRouter.isPrivateModeEnableCommand("Private mode ON"));
        assertFalse(JarvisIntentRouter.isPrivateModeEnableCommand("Explain private mode"));
        assertTrue(JarvisIntentRouter.isPrivateModeDisableCommand("memory privacy off"));
        assertTrue(JarvisIntentRouter.isPrivateModeDisableCommand("memory chalu"));
        assertFalse(JarvisIntentRouter.isPrivateModeDisableCommand("How does memory privacy work?"));
    }

    @Test public void memorySaveRequiresAnExplicitInstruction() {
        assertTrue(JarvisIntentRouter.isRememberCommand("Remember that my project budget is 2 crore"));
        assertTrue(JarvisIntentRouter.isRememberCommand("Please remember my preferred reply style"));
        assertTrue(JarvisIntentRouter.isRememberCommand("Yaad rakhna mujhe short answers pasand hain"));
        assertFalse(JarvisIntentRouter.isRememberCommand("Do you remember what I said yesterday?"));
        assertFalse(JarvisIntentRouter.isRememberCommand("Can you remember our last chat?"));
        assertFalse(JarvisIntentRouter.isRememberCommand("I don't remember the exact date"));
    }

    @Test public void commandsAreNormalizedAcrossCaseAndPunctuation() {
        assertTrue(JarvisIntentRouter.isBatteryStatusQuery("BATTERY STATUS!"));
        assertTrue(JarvisIntentRouter.isFlashlightCommand("Turn ON flashlight!"));
        assertTrue(JarvisIntentRouter.isWebSearchCommand("Web search quantum physics"));
    }
}
