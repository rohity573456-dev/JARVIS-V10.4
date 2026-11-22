package com.jarvis.personalassistant;

/** Recent-window acoustic sanity check, not a second neural or speaker model. */
final class WakeWordAudioVerifier {
    private static final int HISTORY_FRAMES = 8;
    private static final int MIN_HEALTHY_VOICED_FRAMES = 2;
    private static final float MIN_FRAME_RMS = 0.003f;
    private final boolean[] voicedHistory = new boolean[HISTORY_FRAMES];
    private final boolean[] healthyHistory = new boolean[HISTORY_FRAMES];
    private final float[] clippedRatioHistory = new float[HISTORY_FRAMES];
    private int next;
    private int count;

    void addFrame(short[] pcm, int length, boolean voiced) {
        if (pcm == null || length <= 0 || length > pcm.length) return;
        double sumSquares = 0.0;
        int peak = 0;
        int clipped = 0;
        for (int i = 0; i < length; i++) {
            int value = pcm[i];
            int magnitude = Math.abs(value);
            peak = Math.max(peak, magnitude);
            if (magnitude >= 32700) clipped++;
            double normalized = value / 32768.0;
            sumSquares += normalized * normalized;
        }
        float rms = (float) Math.sqrt(sumSquares / length);
        voicedHistory[next] = voiced;
        healthyHistory[next] = peak >= 180 && rms >= MIN_FRAME_RMS;
        clippedRatioHistory[next] = (float) clipped / length;
        next = (next + 1) % HISTORY_FRAMES;
        if (count < HISTORY_FRAMES) count++;
    }

    boolean verify() {
        int healthyVoicedFrames = 0;
        float maxClippedRatio = 0.0f;
        for (int i = 0; i < count; i++) {
            if (voicedHistory[i] && healthyHistory[i]) healthyVoicedFrames++;
            maxClippedRatio = Math.max(maxClippedRatio, clippedRatioHistory[i]);
        }
        return count >= MIN_HEALTHY_VOICED_FRAMES
                && healthyVoicedFrames >= MIN_HEALTHY_VOICED_FRAMES
                && maxClippedRatio < 0.60f;
    }

    void reset() {
        java.util.Arrays.fill(voicedHistory, false);
        java.util.Arrays.fill(healthyHistory, false);
        java.util.Arrays.fill(clippedRatioHistory, 0.0f);
        next = 0;
        count = 0;
    }
}