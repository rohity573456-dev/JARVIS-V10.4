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

/** Optional OpenAI Responses API adapter for GPT-6.2 Astra. */
public final class OpenAiResponsesClient {
    public static final String DEFAULT_MODEL = "gpt-6.2-astra";
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private OpenAiResponsesClient() {}

    /** Converts the app's Chat Completions function schemas to Responses API calls. */
    public static String askForToolCalls(String apiKey, String model, String instructions,
            String input, String reasoningEffort, JSONArray chatTools) {
        if (apiKey == null || apiKey.trim().isEmpty()) return "ERROR: OpenAI API key is not configured.";
        HttpURLConnection connection = null;
        try {
            JSONArray tools = new JSONArray();
            if (chatTools != null) {
                for (int i = 0; i < chatTools.length(); i++) {
                    JSONObject wrapper = chatTools.optJSONObject(i);
                    JSONObject function = wrapper == null ? null : wrapper.optJSONObject("function");
                    if (function == null) continue;
                    JSONObject tool = new JSONObject().put("type", "function")
                            .put("name", function.optString("name", ""))
                            .put("description", function.optString("description", ""))
                            .put("parameters", function.optJSONObject("parameters") == null
                                    ? new JSONObject().put("type", "object").put("properties", new JSONObject())
                                        .put("additionalProperties", false)
                                    : function.optJSONObject("parameters"))
                            .put("strict", true);
                    tools.put(tool);
                }
            }
            JSONObject body = new JSONObject();
            body.put("model", model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim());
            body.put("instructions", instructions == null ? "Use only the supplied tools." : instructions);
            body.put("input", input == null ? "" : input);
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            JSONObject reasoning = new JSONObject();
            String effort = reasoningEffort == null ? "high" : reasoningEffort.trim().toLowerCase(java.util.Locale.ROOT);
            if (!("low".equals(effort) || "medium".equals(effort) || "high".equals(effort)
                    || "xhigh".equals(effort) || "max".equals(effort))) effort = "high";
            reasoning.put("effort", effort);
            body.put("reasoning", reasoning);
            body.put("max_output_tokens", 2048);

            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
            }
            if (status < 200 || status >= 300) return "ERROR " + status + ": " + raw;
            JSONObject response = new JSONObject(raw.toString());
            JSONArray outputItems = response.optJSONArray("output");
            JSONArray normalized = new JSONArray();
            if (outputItems != null) {
                for (int i = 0; i < outputItems.length(); i++) {
                    JSONObject item = outputItems.optJSONObject(i);
                    if (item == null || !"function_call".equals(item.optString("type"))) continue;
                    String name = item.optString("name", "");
                    String arguments = item.optString("arguments", "{}");
                    if (name.isEmpty()) continue;
                    JSONObject args = new JSONObject(arguments);
                    normalized.put(new JSONObject().put("name", name).put("arguments", args));
                }
            }
            return normalized.toString();
        } catch (Exception error) {
            return "ERROR: " + error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static String ask(String apiKey, String model, String instructions,
            String input, String reasoningEffort) {
        if (apiKey == null || apiKey.trim().isEmpty()) return "ERROR: OpenAI API key is not configured.";
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim());
            body.put("instructions", instructions == null ? "You are JARVIS. Be accurate and honest." : instructions);
            body.put("input", input == null ? "" : input);
            JSONObject reasoning = new JSONObject();
            String effort = reasoningEffort == null ? "medium" : reasoningEffort.trim().toLowerCase(java.util.Locale.ROOT);
            if (!("low".equals(effort) || "medium".equals(effort) || "high".equals(effort)
                    || "xhigh".equals(effort) || "max".equals(effort))) effort = "medium";
            reasoning.put("effort", effort);
            body.put("reasoning", reasoning);
            body.put("max_output_tokens", 4096);

            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
            }
            if (status < 200 || status >= 300) return "ERROR " + status + ": " + raw;
            JSONObject response = new JSONObject(raw.toString());
            JSONArray outputItems = response.optJSONArray("output");
            StringBuilder answer = new StringBuilder();
            if (outputItems != null) {
                for (int i = 0; i < outputItems.length(); i++) {
                    JSONObject item = outputItems.optJSONObject(i);
                    if (item == null || !"message".equals(item.optString("type"))) continue;
                    JSONArray content = item.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.optJSONObject(j);
                        if (part != null && "output_text".equals(part.optString("type"))) {
                            String text = part.optString("text", "");
                            if (!text.isEmpty()) {
                                if (answer.length() > 0) answer.append("\n");
                                answer.append(text);
                            }
                        }
                    }
                }
            }
            String result = answer.toString().trim();
            return result.isEmpty() ? "ERROR: GPT-6.2 Astra returned no text output." : result;
        } catch (Exception error) {
            return "ERROR: " + error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
