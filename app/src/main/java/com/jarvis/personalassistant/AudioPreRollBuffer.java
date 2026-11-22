package com.jarvis.personalassistant;

/**
 * Fixed-size rolling PCM buffer for retaining audio immediately before a wake.
 * At 16 kHz mono PCM16, 32,000 samples represent two seconds.
 */
final class AudioPreRollBuffer {
    static final int DEFAULT_CAPACITY_SAMPLES = 32000;
    private final short[] samples;
    private int writeIndex;
    private int size;

    AudioPreRollBuffer() { this(DEFAULT_CAPACITY_SAMPLES); }

    AudioPreRollBuffer(int capacitySamples) {
        if (capacitySamples <= 0) throw new IllegalArgumentException("capacitySamples");
        samples = new short[capacitySamples];
    }

    synchronized void add(short[] input, int length) {
        if (input == null || length <= 0 || length > input.length) return;
        if (length >= samples.length) {
            System.arraycopy(input, length - samples.length, samples, 0, samples.length);
            writeIndex = 0;
            size = samples.length;
            return;
        }
        int first = Math.min(length, samples.length - writeIndex);
        System.arraycopy(input, 0, samples, writeIndex, first);
        int remaining = length - first;
        if (remaining > 0) System.arraycopy(input, first, samples, 0, remaining);
        writeIndex = (writeIndex + length) % samples.length;
        size = Math.min(samples.length, size + length);
    }

    synchronized short[] snapshot() {
        short[] result = new short[size];
        int start = (writeIndex - size + samples.length) % samples.length;
        int first = Math.min(size, samples.length - start);
        System.arraycopy(samples, start, result, 0, first);
        if (size > first) System.arraycopy(samples, 0, result, first, size - first);
        return result;
    }

    synchronized int size() { return size; }

    synchronized void clear() {
        java.util.Arrays.fill(samples, (short) 0);
        writeIndex = 0;
        size = 0;
    }
}
