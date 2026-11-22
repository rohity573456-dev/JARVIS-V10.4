package com.jarvis.personalassistant;

/**
 * Lightweight adaptive energy VAD for 16-bit mono PCM.
 * It is an acoustic activity signal, not speech recognition.
 */
final class VoiceActivityDetector {
    private static final float INITIAL_NOISE_FLOOR = 0.0035f;
    private static final float ABSOLUTE_FLOOR = 0.0045f;
    private static final float NOISE_MULTIPLIER = 2.4f;
    private static final float NOISE_ALPHA = 0.04f;
    private static final int HANGOVER_FRAMES = 4; // 400 ms at 100 ms/frame

    private float noiseFloor = INITIAL_NOISE_FLOOR;
    private int hangoverFrames;

    boolean update(short[] pcm, int count) {
        if (pcm == null || count <= 0) return false;

        double sumSquares = 0.0;
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int sample = pcm[i];
            int magnitude = Math.abs(sample);
            if (magnitude > peak) peak = magnitude;
            double normalized = sample / 32768.0;
            sumSquares += normalized * normalized;
        }

        float rms = (float) Math.sqrt(sumSquares / count);
        float threshold = Math.max(ABSOLUTE_FLOOR, noiseFloor * NOISE_MULTIPLIER);
        boolean active = rms >= threshold && peak >= 180;

        if (active) {
            hangoverFrames = HANGOVER_FRAMES;
        } else {
            if (hangoverFrames > 0) hangoverFrames--;
            // Learn the ambient floor only during frames currently classified as silence.
            noiseFloor = (1.0f - NOISE_ALPHA) * noiseFloor + NOISE_ALPHA * rms;
            noiseFloor = Math.max(0.0005f, Math.min(0.08f, noiseFloor));
        }
        return active || hangoverFrames > 0;
    }

    boolean isSpeechActive() {
        return hangoverFrames > 0;
    }

    float getNoiseFloor() {
        return noiseFloor;
    }

    void reset() {
        noiseFloor = INITIAL_NOISE_FLOOR;
        hangoverFrames = 0;
    }
}
