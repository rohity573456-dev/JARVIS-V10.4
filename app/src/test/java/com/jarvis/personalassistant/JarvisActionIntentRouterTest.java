package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisActionIntentRouterTest {
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

    @Test public void doesNotRouteGeneralQuestionsAsActions() {
        assertFalse(JarvisActionIntentRouter.isExplicitAction("How do I open WhatsApp?"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Why should I launch Instagram?"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("Tell me about Android settings"));
        assertFalse(JarvisActionIntentRouter.isExplicitAction(null));
        assertFalse(JarvisActionIntentRouter.isExplicitAction("  "));
    }
}