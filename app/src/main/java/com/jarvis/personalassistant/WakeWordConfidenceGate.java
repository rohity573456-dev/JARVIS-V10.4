package com.jarvis.personalassistant;

/**
 * Temporal verification for OpenWakeWord classifier scores.
 *
 * This is a second decision stage over consecutive classifier outputs, not a
 * separate neural network. It allows consistently moderate scores to pass
 * while rejecting isolated confidence spikes.
 */
final class WakeWordConfidenceGate {
    private static final float FAST_THRESHOLD = 0.30f;
    private static final float SOFT_THRESHOLD = 0.22f;
    private static final float SOFT_AVERAGE_THRESHOLD = 0.245f;
    private static final int WINDOW_SIZE = 4;

    private final float[] scores = new float[WINDOW_SIZE];
    private int count;
    private int nextIndex;

    boolean add(float rawScore) {
        float score = Float.isFinite(rawScore)
                ? Math.max(0.0f, Math.min(1.0f, rawScore)) : 0.0f;
        scores[nextIndex] = score;
        nextIndex = (nextIndex + 1) % WINDOW_SIZE;
        if (count < WINDOW_SIZE) count++;

        // Fast path: two consecutive strong frames (about 160 ms).
        if (count >= 2) {
            int previous = (nextIndex - 2 + WINDOW_SIZE) % WINDOW_SIZE;
            int latest = (nextIndex - 1 + WINDOW_SIZE) % WINDOW_SIZE;
            if (scores[previous] >= FAST_THRESHOLD
                    && scores[latest] >= FAST_THRESHOLD) {
                return true;
            }
        }

        // Sensitive path: consistent moderate confidence over a 4-frame window.
        if (count < WINDOW_SIZE) return false;

        int qualifying = 0;
        float total = 0.0f;
        for (float value : scores) {
            total += value;
            if (value >= SOFT_THRESHOLD) qualifying++;
        }
        return qualifying >= 3
                && (total / WINDOW_SIZE) >= SOFT_AVERAGE_THRESHOLD;
    }

    void reset() {
        java.util.Arrays.fill(scores, 0.0f);
        count = 0;
        nextIndex = 0;
    }
}
