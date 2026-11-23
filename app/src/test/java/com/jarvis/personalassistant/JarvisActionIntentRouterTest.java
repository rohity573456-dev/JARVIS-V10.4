package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisActionIntentRouterTest {
    @Test public void explicitAstraPrefixDoesNotHideAndroidActions() {
        assertTrue(JarvisActionIntentRouter.isExplicitAction("Astra open YouTube"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("ASTRA remind me in 5 minutes to call home"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Astra explain battery health"));
    }

    @Test public void recognizesEnglishAppAndSearchCommands() {
        assertTrue(JarvisActionIntentRouter.isExplicitAction("open WhatsApp"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("Please launch Instagram"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("start app Spotify"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("search the web for weather"));
    }

    @Test public void recognizesObjectFirstHinglishCommands() {
        assertTrue(JarvisActionIntentRouter.isExplicitAction("WhatsApp kholo"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("Instagram chala do"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("Chrome khol ke do"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("internet pe search karo latest news"));
    }

    @Test public void technicalOpenPhrasesAreNotTreatedAsAppLaunches() {
        assertFalse(JarvisActionIntentRouter.isExplicitAction("open source licensing"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("open AI architecture"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Explain open source licensing"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("What is open AI architecture?"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Compare open source Android launchers"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("Can you open WhatsApp?"));
    }

    @Test public void routesExplicitRelativeReminderCommandsButNotQuestions() {
        assertTrue(JarvisActionIntentRouter.isExplicitAction("remind me in 10 minutes to call home"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("set a reminder in 2 hours to take the parcel"));
        assertTrue(JarvisActionIntentRouter.isExplicitAction("mujhe 15 minute baad yaad dilana ki call karna hai"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("How do reminders work in JARVIS?"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("What is a reminder in 10 minutes?"));
    }

    @Test public void doesNotRouteGeneralQuestionsAsActions() {
        assertFalse(JarvisActionIntentRouter.isExplicitAction("How do I open WhatsApp?"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Why should I launch Instagram?"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Tell me about Android settings"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction(null));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("  "));
    }
}