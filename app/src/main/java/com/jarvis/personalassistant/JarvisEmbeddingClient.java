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

/** Optional Google Gemini semantic embeddings. The caller must explicitly enable this cloud feature. */
public final class JarvisEmbeddingClient {
    public static final String MODEL = "gemini-embedding-001";
    private static final int DIMENSIONS = 768;
    private JarvisEmbeddingClient() {}

    public static float[] embed(String apiKey, String text, boolean query) {
        if (apiKey == null || apiKey.trim().isEmpty() || text == null || text.trim().isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":embedContent");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("x-goog-api-key", apiKey);
            JSONObject part = new JSONObject().put("text", text.length() > 7000 ? text.substring(0,7000) : text);
            JSONArray parts = new JSONArray().put(part);
            JSONObject content = new JSONObject().put("parts", parts);
            JSONObject body = new JSONObject()
                .put("content", content)
                .put("taskType", query ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT")
                .put("outputDimensionality", DIMENSIONS);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) raw.append(line);
            }
            if (code < 200 || code >= 300) return null;
            JSONObject response = new JSONObject(raw.toString());
            JSONObject embedding = response.optJSONObject("embedding");
            JSONArray values = embedding == null ? null : embedding.optJSONArray("values");
            if (values == null || values.length() == 0) return null;
            float[] vector = new float[values.length()];
            for (int i=0;i<values.length();i++) vector[i]=(float)values.getDouble(i);
            return vector;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0.0;
        double dot=0, na=0, nb=0;
        for (int i=0;i<a.length;i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        if (na <= 0 || nb <= 0) return 0.0;
        return dot / (Math.sqrt(na)*Math.sqrt(nb));
    }
}