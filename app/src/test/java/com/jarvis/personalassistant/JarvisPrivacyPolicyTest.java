package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisPrivacyPolicyTest {
    @Test public void privateModeBlocksConversationAndLastResponsePersistence() {
        assertFalse(JarvisPrivacyPolicy.shouldPersistConversation(true));
        assertFalse(JarvisPrivacyPolicy.shouldPersistLastResponse(true));
    }

    @Test public void normalModeAllowsConversationAndLastResponsePersistence() {
        assertTrue(JarvisPrivacyPolicy.shouldPersistConversation(false));
        assertTrue(JarvisPrivacyPolicy.shouldPersistLastResponse(false));
    }
}