package com.jarvis.personalassistant;

import java.util.Locale;

/** Conservative post-ASR guard for empty and wake-phrase-only transcripts. */
final class CommandResultGuard {
    private static final float MIN_CONFIDENCE = 0.30f;
    private CommandResultGuard() {}

    static boolean isUsable(String command, float confidence) {
        if (command == null) return false;
        String normalized = command.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ").trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() < 2) return false;
        if (normalized.equals("jarvis") || normalized.equals("hey jarvis")
                || normalized.equals("ok jarvis") || normalized.equals("okay jarvis")) return false;
        // Some recognition providers do not return confidence. Only enforce it when present.
        return Float.isNaN(confidence) || !Float.isFinite(confidence)
                || confidence >= MIN_CONFIDENCE;
    }
}
