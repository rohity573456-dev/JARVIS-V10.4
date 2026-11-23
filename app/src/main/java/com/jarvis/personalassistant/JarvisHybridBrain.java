package com.jarvis.personalassistant;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hybrid reasoning router: Gemini primary, Groq fallback, local LiteRT-LM last.
 * It never executes Android actions; action execution stays behind the app's
 * explicit command handlers and platform permissions.
 */
public final class JarvisHybridBrain {
    public interface Callback { void onResult(String result); }
    public enum Specialist { RESEARCH, CODING, DATA_ANALYSIS, PLANNING, GENERAL }

    private static final ExecutorService ROUTER = Executors.newFixedThreadPool(2);
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1";

    private JarvisHybridBrain() {}

    public static Specialist classify(String text) {
        String q = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(q, "github", "code", "coding", "debug", "compile", "build app", "program", "java", "kotlin", "python"))
            return Specialist.CODING;
        if (containsAny(q, "excel", "csv", "spreadsheet", "data analysis", "analyse data", "analyze data", "chart", "forecast", "statistics", "profit margin"))
            return Specialist.DATA_ANALYSIS;
        if (containsAny(q, "search web", "research", "latest", "sources", "compare online", "find information"))
            return Specialist.RESEARCH;
        if (containsAny(q, "plan", "strategy", "steps", "roadmap", "schedule", "organize"))
            return Specialist.PLANNING;
        return Specialist.GENERAL;
    }

    public static String systemDirective(String userText) {
        Specialist specialist = classify(userText);
        String mode;
        switch (specialist) {
            case RESEARCH: mode = "RESEARCH SPECIALIST: separate verified facts from uncertainty, cite source names/URLs only when actually supplied by an enabled search tool, and never invent live research."; break;
            case CODING: mode = "SOFTWARE ENGINEERING SPECIALIST: inspect requirements, propose safe minimal changes, explain files and tests, and never claim code was committed or built without tool confirmation."; break;
            case DATA_ANALYSIS: mode = "DATA ANALYST: state assumptions, validate units and missing values, calculate transparently, and distinguish observed data from estimates."; break;
            case PLANNING: mode = "MISSION PLANNER: decompose the objective into ordered steps, dependencies, risks, and verifiable completion criteria."; break;
            default: mode = "GENERAL ASSISTANT: answer directly, reason carefully, and adapt depth to the task.";
        }
        return "You are JARVIS, a high-capability hybrid personal AI assistant. Address the user as Boss when natural. " +
            "Respond in the user's language, including natural Hindi-English code switching. " +
            mode + " " +
            "For complex requests, give a concise plan, state important assumptions, then provide the result. " +
            "Before answering, silently check that the response addresses every part, follows supplied memory, " +
            "has consistent dates, units and calculations, and does not contradict earlier turns. Correct obvious errors. " +
            "Do not reveal private chain-of-thought; provide conclusions and a brief rationale instead. " +
            "Never invent tool results, current facts, files, API access, phone actions, or completed work. " +
            "If a task requires a tool that is unavailable, say so and give the next actionable step. " +
            "Respect privacy: do not ask to expose API keys, passwords, OTPs, or government identifiers. " +
            "Do not execute or recommend irreversible actions without clear safeguards.";
    }

    public static void ask(String geminiKey, String geminiModel, String groqKey, String groqModel,
            String localModelPath, String contextPrompt, String userText, Callback callback) {
        ask(geminiKey, geminiModel, groqKey, groqModel, localModelPath,
                contextPrompt, userText, "", callback);
    }

    /** Astra is an optional paid reasoning tier; free providers remain the normal fallback. */
    public static void ask(String geminiKey, String geminiModel, String groqKey, String groqModel,
            String localModelPath, String contextPrompt, String userText,
            String openAiKey, Callback callback) {
        ROUTER.execute(() -> {
            String directive = systemDirective(userText);
            String result = "ERROR: No AI provider configured.";
            Specialist specialist = classify(userText);
            boolean useAstra = shouldUseAstra(userText, !empty(openAiKey));
            if (useAstra) {
                String effort = specialist == Specialist.CODING || specialist == Specialist.RESEARCH
                        || specialist == Specialist.DATA_ANALYSIS ? "high" : "medium";
                result = OpenAiResponsesClient.ask(openAiKey, OpenAiResponsesClient.DEFAULT_MODEL,
                        directive, contextPrompt, effort);
            }
            String preferred = preferredCloudProvider(specialist, !empty(geminiKey), !empty(groqKey));
            if (isError(result) && "GROQ".equals(preferred)) {
                result = OnlineAiClient.ask(GROQ_ENDPOINT, groqKey,
                    empty(groqModel) ? "openai/gpt-oss-120b" : groqModel, contextPrompt, directive);
            } else if (isError(result) && "GEMINI".equals(preferred)) {
                result = OnlineAiClient.ask(GEMINI_ENDPOINT, geminiKey,
                    empty(geminiModel) ? "gemini-2.5-flash" : geminiModel, contextPrompt, directive);
            }
            if (isError(result) && "GROQ".equals(preferred) && !empty(geminiKey)) {
                result = OnlineAiClient.ask(GEMINI_ENDPOINT, geminiKey,
                    empty(geminiModel) ? "gemini-2.5-flash" : geminiModel, contextPrompt, directive);
            } else if (isError(result) && "GEMINI".equals(preferred) && !empty(groqKey)) {
                result = OnlineAiClient.ask(GROQ_ENDPOINT, groqKey,
                    empty(groqModel) ? "openai/gpt-oss-120b" : groqModel, contextPrompt, directive);
            }
            if (!isError(result)) {
                if (useAstra && shouldVerifyAstraAnswer(userText, true)) {
                    String audited = OpenAiResponsesClient.verifyAnswer(openAiKey, contextPrompt, result,
                        specialist == Specialist.CODING || specialist == Specialist.RESEARCH || specialist == Specialist.DATA_ANALYSIS ? "high" : "medium");
                    if (!isError(audited)) result = audited;
                }
                callback.onResult(result);
                return;
            }
            if (!empty(localModelPath) && new File(localModelPath).isFile()) {
                final String cloudFailure = result;
                JarvisPromptBudget.Prompt localBudget = JarvisPromptBudget.fit(
                    directive, contextPrompt, JarvisPromptBudget.STANDARD_MAX_CHARS);
                LocalAiEngine.ask(localModelPath, localBudget.system + "\n\n" + localBudget.input, localResult -> {
                    String answer = localResult == null || localResult.trim().isEmpty()
                        ? "Cloud AI unavailable (" + cloudFailure + "). Offline model returned no answer."
                        : "Cloud AI unavailable; using the offline brain.\n" + localResult;
                    callback.onResult(answer);
                    return kotlin.Unit.INSTANCE;
                });
                return;
            }
            callback.onResult("JARVIS AI providers unavailable. " + result
                    + ". Configure Gemini/Groq, optional GPT-6.2 Astra, or load a compatible offline model.");
        });
    }

    /** Groq is preferred for code/analysis/planning; Gemini for general and research. */
    static String preferredCloudProvider(Specialist specialist, boolean geminiConfigured, boolean groqConfigured) {
        if (specialist == Specialist.CODING || specialist == Specialist.DATA_ANALYSIS
                || specialist == Specialist.PLANNING) {
            if (groqConfigured) return "GROQ";
        }
        if (geminiConfigured) return "GEMINI";
        if (groqConfigured) return "GROQ";
        return "NONE";
    }
    static boolean shouldUseAstra(String userText, boolean configured) {
        if (!configured || userText == null) return false;
        // Premium Astra is opt-in only. Specialist classification changes the
        // free Gemini/Groq route; it must never silently trigger paid requests.
        return userText.trim().toLowerCase(Locale.ROOT).startsWith("astra ");
    }

    static boolean shouldVerifyAstraAnswer(String userText, boolean configured) {
        if (!configured) return false;
        return classify(userText) != Specialist.GENERAL;
    }

    private static boolean isError(String value) {
        return value == null || value.trim().isEmpty() || value.startsWith("ERROR");
    }
    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
}
