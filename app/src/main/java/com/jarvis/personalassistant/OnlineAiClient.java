package com.jarvis.personalassistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** OpenAI-compatible provider client for Gemini, Groq and compatible endpoints. */
public final class OnlineAiClient {
    private OnlineAiClient() {}

    public static String ask(String endpoint, String apiKey, String model, String prompt) {
        return ask(endpoint, apiKey, model, prompt,
            "You are JARVIS, a capable personal AI assistant. Address the user as Boss when natural. Reply naturally in Hindi/Hinglish or the language used by the user. Be accurate, concise, thoughtful, and honest. Never claim you performed a phone action unless the app confirms it.");
    }

    public static String ask(String endpoint, String apiKey, String model, String prompt, String systemPrompt) {
        HttpURLConnection c = null;
        try {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length()-1) : endpoint;
            String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(90000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
            String safeSystem = systemPrompt == null || systemPrompt.trim().isEmpty()
                    ? "You are JARVIS. Be accurate and honest." : systemPrompt;
            JarvisPromptBudget.Prompt budget = JarvisPromptBudget.fit(
                    safeSystem, prompt, JarvisPromptBudget.STANDARD_MAX_CHARS);
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.35);
            body.put("max_tokens", 1536);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", budget.system));
            messages.put(new JSONObject().put("role", "user").put("content", budget.input));
            body.put("messages", messages);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) raw.append(line);
            }
            if (code < 200 || code >= 300) return "ERROR " + code + ": " + raw;
            JSONObject response = new JSONObject(raw.toString());
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "ERROR: Provider returned no choices.";
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) return "ERROR: Provider response format was unexpected.";
            Object content = message.opt("content");
            if (content instanceof String && !((String) content).trim().isEmpty()) return ((String) content).trim();
            return "ERROR: Provider returned an empty answer.";
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }
    /**
     * Calls an OpenAI-compatible model with native tool schemas. Returns only
     * normalized tool-call requests; this method never executes a tool.
     */
    public static String askForToolCalls(String endpoint, String apiKey, String model,
            String prompt, String systemPrompt, JSONArray tools) {
        HttpURLConnection c = null;
        try {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length()-1) : endpoint;
            String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(90000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("max_tokens", 2048);
            body.put("tool_choice", "auto");
            body.put("tools", tools == null ? new JSONArray() : tools);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content",
                systemPrompt == null ? "Use only supplied tools. Never invent tool results." : systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", messages);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) raw.append(line);
            }
            if (code < 200 || code >= 300) return "ERROR " + code + ": " + raw;
            JSONObject response = new JSONObject(raw.toString());
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "ERROR: Provider returned no choices.";
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) return "ERROR: Provider response format was unexpected.";
            JSONArray calls = message.optJSONArray("tool_calls");
            JSONArray normalized = new JSONArray();
            if (calls == null) return normalized.toString();
            for (int i = 0; i < calls.length() && i < JarvisAgentEngine.MAX_STEPS; i++) {
                JSONObject call = calls.optJSONObject(i);
                JSONObject function = call == null ? null : call.optJSONObject("function");
                if (function == null) continue;
                String name = function.optString("name", "");
                Object rawArgs = function.opt("arguments");
                JSONObject args;
                if (rawArgs instanceof JSONObject) args = (JSONObject) rawArgs;
                else if (rawArgs instanceof String) args = new JSONObject((String) rawArgs);
                else continue;
                normalized.put(new JSONObject().put("name", name).put("arguments", args));
            }
            return normalized.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }

}
