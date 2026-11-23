package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineAiClientResponseTest {
    @Test public void tokenTruncationIsRejectedForChatAndAgentPlanning() {
        String error = OnlineAiClient.completionErrorForFinishReason("length");
        assertNotNull(error);
        assertTrue(error.startsWith("ERROR:"));
        assertTrue(error.contains("truncated"));
    }

    @Test public void contentFilteringIsRejectedForChatAndAgentPlanning() {
        String error = OnlineAiClient.completionErrorForFinishReason("content_filter");
        assertNotNull(error);
        assertTrue(error.startsWith("ERROR:"));
        assertTrue(error.contains("filtered"));
    }

    @Test public void normalStopAndUnknownFinishReasonsRemainValid() {
        assertNull(OnlineAiClient.completionErrorForFinishReason("stop"));
        assertNull(OnlineAiClient.completionErrorForFinishReason(""));
        assertNull(OnlineAiClient.completionErrorForFinishReason(null));
    }

    @Test public void astraIncompleteResponsesTriggerFallback() {
        String error = OpenAiResponsesClient.incompleteResponseError("incomplete", "max_output_tokens");
        assertNotNull(error);
        assertTrue(error.startsWith("ERROR:"));
        assertTrue(error.contains("truncated"));
    }

    @Test public void astraFailedResponsesAreRejected() {
        assertNotNull(OpenAiResponsesClient.incompleteResponseError("failed", ""));
    }

    @Test public void completedAstraResponsesRemainValid() {
        assertNull(OpenAiResponsesClient.incompleteResponseError("completed", ""));
        assertNull(OpenAiResponsesClient.incompleteResponseError("", ""));
    }

    @Test public void finishReasonMatchingIsCaseInsensitive() {
        assertNotNull(OnlineAiClient.completionErrorForFinishReason("LENGTH"));
        assertNotNull(OnlineAiClient.completionErrorForFinishReason("CONTENT_FILTER"));
    }
}
