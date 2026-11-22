package com.jarvis.personalassistant;

import java.util.ArrayList;
import java.util.Locale;

/** Transcript-level directed-command guard; not an ASR lattice or trained semantic model. */
final class CommandResultGuard {
    private static final float MIN_CONFIDENCE = 0.30f;
    private static final String[] WAKE_PREFIXES = {
            "hey jarvis", "hi jarvis", "hello jarvis", "okay jarvis", "ok jarvis", "jarvis"
    };
    private CommandResultGuard() {}

    static String cleanDirectedCommand(String command) {
        if (command == null) return "";
        String cleaned = command.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll("\\s+", " ");
        for (String prefix : WAKE_PREFIXES) {
            if (cleaned.equals(prefix)) return "";
            if (cleaned.startsWith(prefix + " ")) {
                cleaned = cleaned.substring(prefix.length()).trim();
                break;
            }
        }
        return cleaned;
    }

    static boolean isUsable(String command, float confidence) {
        String normalized = cleanDirectedCommand(command);
        if (normalized.length() < 2) return false;
        return !Float.isFinite(confidence) || confidence >= MIN_CONFIDENCE;
    }

    static String selectBest(ArrayList<String> alternatives, float[] confidences) {
        if (alternatives == null || alternatives.isEmpty()) return "";
        int bestIndex = -1;
        float bestConfidence = -Float.MAX_VALUE;
        for (int i = 0; i < alternatives.size(); i++) {
            float confidence = confidences != null && i < confidences.length
                    ? confidences[i] : Float.NaN;
            if (!isUsable(alternatives.get(i), confidence)) continue;
            float rank = Float.isFinite(confidence) ? confidence : (i == 0 ? 0.0f : -i * 0.001f);
            if (bestIndex < 0 || rank > bestConfidence) {
                bestIndex = i;
                bestConfidence = rank;
            }
        }
        return bestIndex < 0 ? "" : cleanDirectedCommand(alternatives.get(bestIndex));
    }
}
