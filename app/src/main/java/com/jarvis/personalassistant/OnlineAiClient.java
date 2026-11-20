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

/** OpenAI-compatible provider client for Gemini and Groq. */
public final class OnlineAiClient {
    private OnlineAiClient() {}

    public static String ask(String endpoint, String apiKey, String model, String prompt) {
        HttpURLConnection c = null;
        try {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length()-1) : endpoint;
            String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
            JSONObject body = new JSONObject(); body.put("model", model);
            body.put("temperature", 0.7); body.put("max_tokens", 2048);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", "You are JARVIS, a capable personal AI assistant. Address the user as Commander when natural. Reply naturally in the language used by the user, especially Hindi/Hinglish. Be accurate, concise, thoughtful, and honest. Never claim you performed a phone action unless the app confirms it."));
            messages.put(new JSONObject().put("role", "user").put("content", prompt)); body.put("messages", messages);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line = r.readLine()) != null) raw.append(line); }
            if (code < 200 || code >= 300) return "ERROR " + code + ": " + raw;
            JSONObject response = new JSONObject(raw.toString());
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "ERROR: Provider returned no choices.";
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) return "ERROR: Provider response format was unexpected.";
            Object content = message.opt("content");
            if (content instanceof String && !((String) content).trim().isEmpty()) return (String) content;
            return "ERROR: Provider returned an empty answer.";
        } catch (Exception e) { return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage(); }
        finally { if (c != null) c.disconnect(); }
    }
}
