package com.jarvis.personalassistant;

/**
 * Temporal verification for OpenWakeWord classifier scores.
 *
 * A short adaptive second-chance window is armed by repeated borderline
 * evidence. Sensitivity rises briefly, while multi-frame consistency remains
 * required to avoid accepting a single noisy confidence spike.
 */
final class WakeWordConfidenceGate {
    private static final float FAST_THRESHOLD = 0.30f;
    private static final float SOFT_THRESHOLD = 0.22f;
    private static final float SOFT_AVERAGE_THRESHOLD = 0.245f;

    private static final float BORDERLINE_THRESHOLD = 0.18f;
    private static final int BORDERLINE_REQUIRED = 2;
    private static final float ADAPTIVE_THRESHOLD = 0.19f;
    private static final float ADAPTIVE_AVERAGE_THRESHOLD = 0.205f;

    private static final int WINDOW_SIZE = 4;
    // Classifier runs once per 80 ms audio chunk: 38 frames are about 3 seconds.
    private static final int SECOND_CHANCE_FRAMES = 38;

    private final float[] scores = new float[WINDOW_SIZE];
    private int count;
    private int nextIndex;
    private int secondChanceFramesRemaining;
    private boolean secondChanceArmedForEpisode;

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

        int borderlineCount = 0;
        float total = 0.0f;
        for (float value : scores) {
            total += value;
            if (value >= BORDERLINE_THRESHOLD) borderlineCount++;
        }

        // Arm only after repeated borderline evidence, never on one isolated spike.
        if (borderlineCount < BORDERLINE_REQUIRED) {
            secondChanceArmedForEpisode = false;
        } else if (count == WINDOW_SIZE && !secondChanceArmedForEpisode
                && secondChanceFramesRemaining == 0) {
            secondChanceFramesRemaining = SECOND_CHANCE_FRAMES;
            secondChanceArmedForEpisode = true;
        }

        boolean adaptive = secondChanceFramesRemaining > 0;
        if (adaptive) secondChanceFramesRemaining--;

        if (count < WINDOW_SIZE) return false;

        if (adaptive) {
            int qualifying = 0;
            for (float value : scores) {
                if (value >= ADAPTIVE_THRESHOLD) qualifying++;
            }
            return qualifying >= 3
                    && (total / WINDOW_SIZE) >= ADAPTIVE_AVERAGE_THRESHOLD;
        }

        int qualifying = 0;
        for (float value : scores) {
            if (value >= SOFT_THRESHOLD) qualifying++;
        }
        return qualifying >= 3
                && (total / WINDOW_SIZE) >= SOFT_AVERAGE_THRESHOLD;
    }

    void reset() {
        java.util.Arrays.fill(scores, 0.0f);
        count = 0;
        nextIndex = 0;
        secondChanceFramesRemaining = 0;
        secondChanceArmedForEpisode = false;
    }
}
