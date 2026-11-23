package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisConversationContextTest {
    @Test public void privateModeDoesNotLeakSavedOrPreviousContext() {
        String prompt = JarvisConversationContext.compose("SECRET_MEMORY", "SECRET_NOTE",
                "SECRET_PREVIOUS", "What about this?", true);
        assertFalse(prompt.contains("SECRET_MEMORY"));
        assertFalse(prompt.contains("SECRET_NOTE"));
        assertFalse(prompt.contains("SECRET_PREVIOUS"));
        assertTrue(prompt.contains("PRIVATE MODE"));
        assertTrue(prompt.contains("Commander says: What about this?"));
    }

    @Test public void normalModeIncludesRelevantHistoryWithRealNewlines() {
        String prompt = JarvisConversationContext.compose("PAST_FACT", "A NOTE",
                "Earlier answer", "Continue", false);
        assertTrue(prompt.contains("PAST_FACT\n"));
        assertTrue(prompt.contains("Legacy saved note: A NOTE\n"));
        assertTrue(prompt.contains("Previous response: Earlier answer\n"));
        assertTrue(prompt.contains("\n\n"));
        assertFalse(prompt.contains("\\nLegacy saved note"));
    }

    @Test public void contextSectionsAreBounded() {
        String huge = new String(new char[12000]).replace('\0', 'x');
        String prompt = JarvisConversationContext.compose(huge, huge, huge, "Hi", false);
        assertTrue(prompt.length() < 14000);
    }
}
