package com.jarvis.personalassistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Three-provider cloud router for JARVIS.
 * It selects a task-aware preference order, then performs bounded sequential
 * failover. It never executes Android tools or treats provider output as proof
 * that an action happened.
 */
public final class MultiBrainRouter {
    private static final String NEBIUS_ENDPOINT = "https://api.tokenfactory.nebius.com/v1";
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1";

    public enum Provider { NEBIUS, GEMINI, GROQ }

    private MultiBrainRouter() {}

    public static List<Provider> route(String userText, boolean hasNebius,
            boolean hasGemini, boolean hasGroq) {
        String q = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        List<Provider> preferred = new ArrayList<>();
        if (containsAny(q, "code", "coding", "debug", "compile", "github", "java",
                "kotlin", "python", "programming", "architecture", "technical design")) {
            add(preferred, Provider.NEBIUS, hasNebius);
            add(preferred, Provider.GROQ, hasGroq);
            add(preferred, Provider.GEMINI, hasGemini);
        } else if (containsAny(q, "research", "latest", "compare", "sources", "fact check",
                "analyse", "analyze", "data", "statistics", "calculate")) {
            add(preferred, Provider.GEMINI, hasGemini);
            add(preferred, Provider.NEBIUS, hasNebius);
            add(preferred, Provider.GROQ, hasGroq);
        } else if (containsAny(q, "plan", "planning", "strategy", "steps", "roadmap",
                "task", "mission")) {
            add(preferred, Provider.GROQ, hasGroq);
            add(preferred, Provider.NEBIUS, hasNebius);
            add(preferred, Provider.GEMINI, hasGemini);
        } else {
            add(preferred, Provider.GEMINI, hasGemini);
            add(preferred, Provider.GROQ, hasGroq);
            add(preferred, Provider.NEBIUS, hasNebius);
        }
        return preferred;
    }

    public static String ask(String nebiusKey, String geminiKey, String geminiModel,
            String groqKey, String groqModel, String prompt, String userText,
            String systemPrompt) {
        List<Provider> order = route(userText, !empty(nebiusKey), !empty(geminiKey), !empty(groqKey));
        if (order.isEmpty()) return "ERROR: No cloud AI provider configured.";
        String lastError = "ERROR: No provider attempted.";
        for (Provider provider : order) {
            String result;
            switch (provider) {
                case NEBIUS:
                    result = OnlineAiClient.ask(NEBIUS_ENDPOINT, nebiusKey,
                            "openai/gpt-oss-120b", prompt, systemPrompt);
                    break;
                case GEMINI:
                    result = OnlineAiClient.ask(GEMINI_ENDPOINT, geminiKey,
                            empty(geminiModel) ? "gemini-2.5-flash" : geminiModel,
                            prompt, systemPrompt);
                    break;
                default:
                    result = OnlineAiClient.ask(GROQ_ENDPOINT, groqKey,
                            empty(groqModel) ? "openai/gpt-oss-120b" : groqModel,
                            prompt, systemPrompt);
                    break;
            }
            if (!isError(result)) return result;
            lastError = provider.name() + " failed: " + result;
        }
        return "ERROR: All configured AI providers failed. " + lastError;
    }

    public static String askForToolCalls(String nebiusKey, String geminiKey, String geminiModel,
            String groqKey, String groqModel, String prompt, String userText,
            String systemPrompt, org.json.JSONArray tools) {
        List<Provider> order = route(userText, !empty(nebiusKey), !empty(geminiKey), !empty(groqKey));
        if (order.isEmpty()) return "ERROR: No cloud AI provider configured.";
        String lastError = "ERROR: No provider attempted.";
        for (Provider provider : order) {
            String result;
            switch (provider) {
                case NEBIUS:
                    result = OnlineAiClient.askForToolCalls(NEBIUS_ENDPOINT, nebiusKey,
                            "openai/gpt-oss-120b", prompt, systemPrompt, tools);
                    break;
                case GEMINI:
                    result = OnlineAiClient.askForToolCalls(GEMINI_ENDPOINT, geminiKey,
                            empty(geminiModel) ? "gemini-2.5-flash" : geminiModel,
                            prompt, systemPrompt, tools);
                    break;
                default:
                    result = OnlineAiClient.askForToolCalls(GROQ_ENDPOINT, groqKey,
                            empty(groqModel) ? "openai/gpt-oss-120b" : groqModel,
                            prompt, systemPrompt, tools);
                    break;
            }
            if (!isError(result)) return result;
            lastError = provider.name() + " failed: " + result;
        }
        return "ERROR: All configured AI planners failed. " + lastError;
    }

    private static void add(List<Provider> list, Provider provider, boolean enabled) {
        if (enabled && !list.contains(provider)) list.add(provider);
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            int from = 0;
            while (true) {
                int index = text.indexOf(term, from);
                if (index < 0) break;
                int end = index + term.length();
                boolean left = index == 0 || !Character.isLetterOrDigit(text.codePointBefore(index));
                boolean right = end == text.length() || !Character.isLetterOrDigit(text.codePointAt(end));
                if (left && right) return true;
                from = index + 1;
            }
        }
        return false;
    }

    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean isError(String value) {
        return value == null || value.trim().isEmpty() || value.startsWith("ERROR");
    }
}
