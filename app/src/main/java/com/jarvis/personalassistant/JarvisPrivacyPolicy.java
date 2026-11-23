package com.jarvis.personalassistant;

/** Central persistence policy for data that must stay ephemeral in Private Mode. */
public final class JarvisPrivacyPolicy {
    private JarvisPrivacyPolicy() {}

    public static boolean shouldPersistConversation(boolean privateMode) {
        return !privateMode;
    }

    public static boolean shouldPersistLastResponse(boolean privateMode) {
        return !privateMode;
    }
}