package com.jarvis.personalassistant;

/** Adaptive energy VAD for 16-bit mono PCM; not speech recognition. */
final class VoiceActivityDetector {
    private static final float INITIAL_NOISE_FLOOR = 0.0035f;
    private static final float ABSOLUTE_FLOOR = 0.0045f;
    private static final float NOISE_MULTIPLIER = 2.4f;
    private static final float NOISE_ALPHA = 0.04f;
    private static final int HANGOVER_FRAMES = 4;
    private float noiseFloor = INITIAL_NOISE_FLOOR;
    private int hangoverFrames;

    boolean update(short[] pcm, int count) {
        if (pcm == null || count <= 0 || count > pcm.length) return false;
        double sumSquares = 0.0;
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int sample = pcm[i];
            peak = Math.max(peak, Math.abs(sample));
            double normalized = sample / 32768.0;
            sumSquares += normalized * normalized;
        }
        float rms = (float) Math.sqrt(sumSquares / count);
        boolean active = rms >= Math.max(ABSOLUTE_FLOOR, noiseFloor * NOISE_MULTIPLIER)
                && peak >= 180;
        if (active) {
            hangoverFrames = HANGOVER_FRAMES;
        } else {
            boolean inHangover = hangoverFrames > 0;
            if (inHangover) hangoverFrames--;
            // Do not train the ambient noise estimate on the tail of speech.
            if (!inHangover) {
                noiseFloor = (1.0f - NOISE_ALPHA) * noiseFloor + NOISE_ALPHA * rms;
                noiseFloor = Math.max(0.0005f, Math.min(0.08f, noiseFloor));
            }
        }
        return active || hangoverFrames > 0;
    }

    boolean isSpeechActive() { return hangoverFrames > 0; }
    float getNoiseFloor() { return noiseFloor; }
    void reset() { noiseFloor = INITIAL_NOISE_FLOOR; hangoverFrames = 0; }
}