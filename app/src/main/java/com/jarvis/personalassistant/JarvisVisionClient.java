package com.jarvis.personalassistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** On-demand Gemini vision request. Images are resized in memory and never persisted. */
public final class JarvisVisionClient {
    private JarvisVisionClient() {}

    public static String analyze(Context context, Uri uri, String apiKey, String model, String prompt) {
        HttpURLConnection connection = null;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return "Image file open nahi ho payi.";
                BitmapFactory.decodeStream(in,null,bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return "Image file dobara open nahi ho payi.";
                bitmap = BitmapFactory.decodeStream(in,null,options);
            }
            if (bitmap == null) return "Image format supported nahi hai.";
            int max = 1280;
            if (bitmap.getWidth() > max || bitmap.getHeight() > max) {
                float scale = Math.min((float)max / bitmap.getWidth(),(float)max / bitmap.getHeight());
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),
                    Math.max(1,(int)(bitmap.getHeight()*scale)),true);
                if (scaled != bitmap) { bitmap.recycle(); bitmap = scaled; }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG,82,bytes);
            String encoded = Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);

            String safeModel = model == null || model.trim().isEmpty() ? "gemini-2.5-flash" : model.trim();
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + safeModel + ":generateContent?key=" + java.net.URLEncoder.encode(apiKey,"UTF-8");
            connection = (HttpURLConnection)new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");

            JSONObject inline = new JSONObject();
            inline.put("mime_type","image/jpeg");
            inline.put("data",encoded);
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text",prompt == null ? "Describe this image." : prompt));
            parts.put(new JSONObject().put("inline_data",inline));
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("parts",parts));
            JSONObject body = new JSONObject();
            body.put("contents",contents);
            body.put("generationConfig",new JSONObject().put("temperature",0.2).put("maxOutputTokens",1200));
            byte[] payload = body.toString().getBytes("UTF-8");
            try (OutputStream out = connection.getOutputStream()) { out.write(payload); }

            int code = connection.getResponseCode();
            InputStream response = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (response != null) try (InputStream in = response) {
                byte[] buffer = new byte[4096]; int count;
                while ((count=in.read(buffer))!=-1) raw.append(new String(buffer,0,count,"UTF-8"));
            }
            if (code < 200 || code >= 300) return "Gemini Vision error " + code + ": " + raw;
            JSONObject json = new JSONObject(raw.toString());
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length()==0) return "Gemini Vision ne koi result nahi diya.";
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            JSONArray resultParts = content == null ? null : content.optJSONArray("parts");
            if (resultParts == null) return "Gemini Vision response format samajh nahi aaya.";
            StringBuilder answer = new StringBuilder();
            for (int i=0;i<resultParts.length();i++) {
                String text = resultParts.getJSONObject(i).optString("text","");
                if (!text.isEmpty()) { if (answer.length()>0) answer.append("\n"); answer.append(text); }
            }
            return answer.length()==0 ? "Image ka readable analysis nahi mila." : answer.toString();
        } catch (Exception e) {
            return "Vision analysis failed: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (connection != null) connection.disconnect();
        }
    }
}
