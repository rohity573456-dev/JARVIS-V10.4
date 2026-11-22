package com.jarvis.personalassistant;

/**
 * Independent acoustic sanity check after the neural wake-word confidence gate.
 * This is not a second neural model or speaker identity check.
 */
final class WakeWordAudioVerifier {
    private static final int HISTORY_FRAMES = 8;
    private static final int MIN_VOICED_FRAMES = 2;
    private final boolean[] voicedHistory = new boolean[HISTORY_FRAMES];
    private int next;
    private int count;
    private int lastPeak;
    private float lastRms;
    private float lastClippedRatio;

    void addFrame(short[] pcm, int length, boolean voiced) {
        if (pcm == null || length <= 0) return;
        double sumSquares = 0.0;
        int peak = 0;
        int clipped = 0;
        for (int i = 0; i < length; i++) {
            int value = pcm[i];
            int magnitude = Math.abs(value);
            if (magnitude > peak) peak = magnitude;
            if (magnitude >= 32700) clipped++;
            double normalized = value / 32768.0;
            sumSquares += normalized * normalized;
        }
        lastPeak = peak;
        lastRms = (float) Math.sqrt(sumSquares / length);
        lastClippedRatio = (float) clipped / length;

        voicedHistory[next] = voiced;
        next = (next + 1) % HISTORY_FRAMES;
        if (count < HISTORY_FRAMES) count++;
    }

    boolean verify() {
        int voicedFrames = 0;
        for (int i = 0; i < count; i++) {
            if (voicedHistory[i]) voicedFrames++;
        }
        // Reject silence-only and heavily clipped candidates. Multiple recent
        // voiced frames corroborate the detector's temporal confidence decision.
        return count >= MIN_VOICED_FRAMES
                && voicedFrames >= MIN_VOICED_FRAMES
                && lastPeak >= 180
                && lastRms >= 0.003f
                && lastClippedRatio < 0.60f;
    }

    void reset() {
        java.util.Arrays.fill(voicedHistory, false);
        next = 0;
        count = 0;
        lastPeak = 0;
        lastRms = 0.0f;
        lastClippedRatio = 0.0f;
    }
}
