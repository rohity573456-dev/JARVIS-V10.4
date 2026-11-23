package com.jarvis.personalassistant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads provider response bodies with a strict character ceiling. */
final class JarvisHttpBodyReader {
    private JarvisHttpBodyReader() {}

    static String read(InputStream stream, int maxChars) throws IOException {
        if (stream == null) return "";
        if (maxChars < 1) throw new IllegalArgumentException("maxChars must be positive");
        StringBuilder body = new StringBuilder(Math.min(maxChars, 8192));
        char[] buffer = new char[4096];
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (body.length() + count > maxChars)
                    throw new IOException("Provider response exceeded the " + maxChars + " character safety limit.");
                body.append(buffer, 0, count);
            }
        }
        return body.toString();
    }
}
