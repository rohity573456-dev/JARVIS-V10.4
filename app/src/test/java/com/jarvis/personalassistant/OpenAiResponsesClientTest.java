package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenAiResponsesClientTest {
    @Test public void acceptsSupportedReasoningEffortLevels() {
        assertEquals("low", OpenAiResponsesClient.normalizeReasoningEffort("LOW", "medium"));
        assertEquals("medium", OpenAiResponsesClient.normalizeReasoningEffort("medium", "high"));
        assertEquals("high", OpenAiResponsesClient.normalizeReasoningEffort("high", "medium"));
        assertEquals("xhigh", OpenAiResponsesClient.normalizeReasoningEffort("xhigh", "medium"));
    }

    @Test public void unknownOrUnsupportedEffortFallsBackSafely() {
        assertEquals("high", OpenAiResponsesClient.normalizeReasoningEffort("max", "high"));
        assertEquals("medium", OpenAiResponsesClient.normalizeReasoningEffort("invalid", "medium"));
        assertEquals("medium", OpenAiResponsesClient.normalizeReasoningEffort(null, "medium"));
    }
}