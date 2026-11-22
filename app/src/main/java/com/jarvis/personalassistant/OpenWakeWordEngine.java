package com.jarvis.personalassistant;

import android.content.res.AssetManager;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

final class OpenWakeWordEngine implements AutoCloseable {
    private static final String TAG = "JARVIS-OWW";
    private static final int SAMPLE_RATE = 16000;
    private static final int AUDIO_CHUNK = 1280;       // OpenWakeWord: 80 ms
    private static final int MEL_BINS = 32;
    private static final int MEL_WINDOW = 76;
    private static final int EMBEDDING_SIZE = 96;
    private static final int CLASSIFIER_WINDOW = 16;
    private static final int RAW_HISTORY = SAMPLE_RATE * 10;
    private static final int MEL_HISTORY = 970;
    private final WakeWordConfidenceGate confidenceGate = new WakeWordConfidenceGate();

    private final OrtEnvironment environment;
    private final OrtSession melSession;
    private final OrtSession embeddingSession;
    private final OrtSession classifierSession;
    private final String classifierInputName;
    private final IndependentWakeWordVerifier independentVerifier;

    private final short[] rawHistory = new short[RAW_HISTORY];
    private int rawWrite;
    private int rawCount;

    private final short[] pendingChunk = new short[AUDIO_CHUNK];
    private int pendingCount;
    private int liveFeatureFrames;

    // Reusable hot-path buffers avoid allocating large arrays every 80 ms.
    private final float[] rawWindow = new float[AUDIO_CHUNK + 480];
    private final float[] embeddingInputData = new float[MEL_WINDOW * MEL_BINS];
    private final float[] classifierInputData =
            new float[CLASSIFIER_WINDOW * EMBEDDING_SIZE];

    // Fixed-size mel ring buffer avoids ArrayList.remove(0) shifting on every chunk.
    private final float[][] melHistory = new float[MEL_HISTORY][MEL_BINS];
    private int melWrite;
    private int melCount;
    // Preallocated embedding ring avoids one 96-float allocation every 80 ms.
    private final float[][] featureHistory = new float[120][EMBEDDING_SIZE];
    private int featureWrite;
    private int featureCount;

    OpenWakeWordEngine(AssetManager assets) throws Exception {
        environment = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        try {
            melSession = environment.createSession(
                    readAsset(assets, "openwakeword/melspectrogram.onnx"), options);
            embeddingSession = environment.createSession(
                    readAsset(assets, "openwakeword/embedding_model.onnx"), options);
            classifierSession = environment.createSession(
                    readAsset(assets, "openwakeword/Jarvis_20260313_215039.onnx"), options);
        } finally {
            options.close();
        }

        classifierInputName = classifierSession.getInputNames().iterator().next();
        independentVerifier = new IndependentWakeWordVerifier(assets, environment);
        reset();
        Log.i(TAG, "OpenWakeWord primary and independent verifier sessions loaded");
    }

    private byte[] readAsset(AssetManager assets, String path) throws Exception {
        try (InputStream input = assets.open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    synchronized void reset() {
        rawWrite = 0;
        rawCount = 0;
        pendingCount = 0;
        liveFeatureFrames = 0;
        melWrite = 0;
        melCount = 0;
        featureWrite = 0;
        featureCount = CLASSIFIER_WINDOW;
        confidenceGate.reset();
        for (int i = 0; i < featureHistory.length; i++) {
            java.util.Arrays.fill(featureHistory[i], 0.0f);
        }
        featureWrite = CLASSIFIER_WINDOW;

        for (int i = 0; i < MEL_HISTORY; i++) {
            java.util.Arrays.fill(melHistory[i], 0.0f);
        }
        for (int i = 0; i < MEL_WINDOW; i++) {
            java.util.Arrays.fill(melHistory[i], 1.0f);
        }
        melCount = MEL_WINDOW;
        melWrite = MEL_WINDOW % MEL_HISTORY;
    }

    synchronized boolean acceptAudio(short[] pcm, int count) throws Exception {
        if (pcm == null || count <= 0) return false;

        boolean detected = false;
        for (int i = 0; i < count; i++) {
            short sample = pcm[i];

            rawHistory[rawWrite] = sample;
            rawWrite = (rawWrite + 1) % RAW_HISTORY;
            if (rawCount < RAW_HISTORY) rawCount++;

            pendingChunk[pendingCount++] = sample;

            if (pendingCount == AUDIO_CHUNK) {
                pendingCount = 0;
                if (rawCount >= AUDIO_CHUNK + 480) {
                    appendMelFrames();
                    float[] embedding = featureHistory[featureWrite];
                    makeEmbedding(embedding);
                    featureWrite = (featureWrite + 1) % featureHistory.length;
                    if (featureCount < featureHistory.length) featureCount++;
                    liveFeatureFrames++;

                    if (liveFeatureFrames >= CLASSIFIER_WINDOW) {
                        float score = predict();
                        // Avoid a log write for every 80 ms inference; it adds
                        // needless wakeups and I/O during continuous standby.
                        if (confidenceGate.add(score)) {
                            confidenceGate.reset();
                            if (independentVerifier.verify(melHistory, melWrite, melCount)) {
                                detected = true;
                                Log.i(TAG, "Wake word passed independent Jarvis neural verification");
                                break;
                            } else {
                                Log.i(TAG, "Primary candidate rejected by independent Jarvis ONNX verifier");
                            }
                        }
                    }
                }
            }
        }
        return detected;
    }

    private float[] latestRawWindow() {
        int length = rawWindow.length;
        int start = (rawWrite - length + RAW_HISTORY) % RAW_HISTORY;
        for (int i = 0; i < length; i++) {
            rawWindow[i] = rawHistory[(start + i) % RAW_HISTORY];
        }
        return rawWindow;
    }

    private void appendMelFrames() throws Exception {
        float[] audio = latestRawWindow();
        try (OnnxTensor input = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(audio), new long[]{1, audio.length})) {
            try (OrtSession.Result result = melSession.run(
                    Collections.singletonMap("input", input))) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                long[] shape = output.getInfo().getShape();
                if (shape.length < 2 || shape[shape.length - 1] != MEL_BINS) {
                    throw new IllegalStateException("Unexpected melspectrogram output shape");
                }
                FloatBuffer values = output.getFloatBuffer();
                int frames = values.remaining() / MEL_BINS;
                for (int frame = 0; frame < frames; frame++) {
                    float[] mel = melHistory[melWrite];
                    for (int bin = 0; bin < MEL_BINS; bin++) {
                        mel[bin] = values.get() / 10.0f + 2.0f;
                    }
                    melWrite = (melWrite + 1) % MEL_HISTORY;
                    if (melCount < MEL_HISTORY) melCount++;
                }
            }
        }
    }

    private void makeEmbedding(float[] embedding) throws Exception {
        if (melCount < MEL_WINDOW) {
            java.util.Arrays.fill(embedding, 0.0f);
            return;
        }

        int start = (melWrite - MEL_WINDOW + MEL_HISTORY) % MEL_HISTORY;
        for (int i = 0; i < MEL_WINDOW; i++) {
            float[] mel = melHistory[(start + i) % MEL_HISTORY];
            System.arraycopy(mel, 0,
                    embeddingInputData, i * MEL_BINS, MEL_BINS);
        }

        try (OnnxTensor input = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(embeddingInputData),
                new long[]{1, MEL_WINDOW, MEL_BINS, 1})) {
            try (OrtSession.Result result = embeddingSession.run(
                    Collections.singletonMap("input_1", input))) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                FloatBuffer values = output.getFloatBuffer();
                if (values.remaining() < EMBEDDING_SIZE) {
                    throw new IllegalStateException("Unexpected embedding output size");
                }
                for (int i = 0; i < EMBEDDING_SIZE; i++) {
                    embedding[i] = values.get();
                }
            }
        }
    }

    private float predict() throws Exception {
        java.util.Arrays.fill(classifierInputData, 0.0f);
        int offset = 0;
        int start = (featureWrite - CLASSIFIER_WINDOW + featureHistory.length)
                % featureHistory.length;
        for (int i = 0; i < CLASSIFIER_WINDOW; i++) {
            float[] feature = featureHistory[(start + i) % featureHistory.length];
            System.arraycopy(feature, 0, classifierInputData, offset, EMBEDDING_SIZE);
            offset += EMBEDDING_SIZE;
        }

        try (OnnxTensor input = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(classifierInputData),
                new long[]{1, CLASSIFIER_WINDOW, EMBEDDING_SIZE})) {
            try (OrtSession.Result result = classifierSession.run(
                    Collections.singletonMap(classifierInputName, input))) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                return output.getFloatBuffer().get(0);
            }
        }
    }

    @Override
    public void close() {
        try { independentVerifier.close(); } catch (Exception ignored) {}
        try { classifierSession.close(); } catch (Exception ignored) {}
        try { embeddingSession.close(); } catch (Exception ignored) {}
        try { melSession.close(); } catch (Exception ignored) {}
    }
}
