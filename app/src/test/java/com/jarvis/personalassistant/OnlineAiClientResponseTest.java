package com.jarvis.personalassistant;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineAiClientResponseTest {
    @Test public void acceptsCompleteAssistantAnswer() throws Exception {
        JSONObject response = new JSONObject("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Hello Boss.\"}}]}");
        assertEquals("Hello Boss.", OnlineAiClient.extractAssistantAnswer(response));
    }

    @Test public void rejectsTokenTruncatedAnswerSoRouterCanFallback() throws Exception {
        JSONObject response = new JSONObject("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"This answer is incomplete\"}}]}");
        assertTrue(OnlineAiClient.extractAssistantAnswer(response).startsWith("ERROR:"));
        assertTrue(OnlineAiClient.extractAssistantAnswer(response).contains("truncated"));
    }

    @Test public void rejectsFilteredAnswerSoRouterCanFallback() throws Exception {
        JSONObject response = new JSONObject("{\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":null}}]}");
        assertTrue(OnlineAiClient.extractAssistantAnswer(response).startsWith("ERROR:"));
        assertTrue(OnlineAiClient.extractAssistantAnswer(response).contains("filtered"));
    }

    @Test public void rejectsTruncatedToolPlanInsteadOfTreatingItAsNoAction() throws Exception {
        JSONObject response = new JSONObject("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}]}");
        assertTrue(OnlineAiClient.extractToolCalls(response).startsWith("ERROR:"));
        assertTrue(OnlineAiClient.extractToolCalls(response).contains("truncated"));
    }

    @Test public void acceptsACompleteNoToolDecision() throws Exception {
        JSONObject response = new JSONObject("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"No action needed.\"}}]}");
        assertEquals("[]", OnlineAiClient.extractToolCalls(response));
    }

    @Test public void rejectsMissingAndEmptyProviderContent() throws Exception {
        assertTrue(OnlineAiClient.extractAssistantAnswer(new JSONObject("{}")).startsWith("ERROR:"));
        JSONObject response = new JSONObject("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"  \"}}]}");
        assertTrue(OnlineAiClient.extractAssistantAnswer(response).startsWith("ERROR:"));
    }
}
