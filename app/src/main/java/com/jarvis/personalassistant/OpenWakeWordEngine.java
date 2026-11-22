package com.jarvis.personalassistant;

import android.content.res.AssetManager;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
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
    private static final float THRESHOLD = 0.35f;

    private final OrtEnvironment environment;
    private final OrtSession melSession;
    private final OrtSession embeddingSession;
    private final OrtSession classifierSession;
    private final String classifierInputName;

    private final short[] rawHistory = new short[RAW_HISTORY];
    private int rawWrite;
    private int rawCount;

    private final short[] pendingChunk = new short[AUDIO_CHUNK];
    private int pendingCount;
    private int liveFeatureFrames;

    private final ArrayList<float[]> melHistory = new ArrayList<>();
    private final ArrayDeque<float[]> featureHistory = new ArrayDeque<>();

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
        reset();
        Log.i(TAG, "OpenWakeWord ONNX sessions loaded");
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
        melHistory.clear();
        featureHistory.clear();

        for (int i = 0; i < MEL_WINDOW; i++) {
            float[] initialMel = new float[MEL_BINS];
            java.util.Arrays.fill(initialMel, 1.0f);
            melHistory.add(initialMel);
        }
        // Fill the initial classifier context; live embeddings replace these
        // as the microphone stream advances.
        for (int i = 0; i < CLASSIFIER_WINDOW; i++) {
            featureHistory.addLast(new float[EMBEDDING_SIZE]);
        }
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
                    float[] embedding = makeEmbedding();
                    featureHistory.addLast(embedding);
                    liveFeatureFrames++;
                    while (featureHistory.size() > 120) featureHistory.removeFirst();

                    if (liveFeatureFrames >= CLASSIFIER_WINDOW) {
                        float score = predict();
                        Log.d(TAG, "Wake confidence=" + score);
                        if (score >= THRESHOLD) {
                            detected = true;
                            break;
                        }
                    }
                }
            }
        }
        return detected;
    }

    private float[] latestRawWindow() {
        int length = AUDIO_CHUNK + 480;
        float[] values = new float[length];
        int start = (rawWrite - length + RAW_HISTORY) % RAW_HISTORY;
        for (int i = 0; i < length; i++) {
            values[i] = rawHistory[(start + i) % RAW_HISTORY];
        }
        return values;
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
                    float[] mel = new float[MEL_BINS];
                    for (int bin = 0; bin < MEL_BINS; bin++) {
                        mel[bin] = values.get() / 10.0f + 2.0f;
                    }
                    melHistory.add(mel);
                }
                while (melHistory.size() > MEL_HISTORY) melHistory.remove(0);
            }
        }
    }

    private float[] makeEmbedding() throws Exception {
        if (melHistory.size() < MEL_WINDOW) {
            return new float[EMBEDDING_SIZE];
        }

        float[] inputData = new float[MEL_WINDOW * MEL_BINS];
        int start = melHistory.size() - MEL_WINDOW;
        for (int i = 0; i < MEL_WINDOW; i++) {
            System.arraycopy(melHistory.get(start + i), 0,
                    inputData, i * MEL_BINS, MEL_BINS);
        }

        try (OnnxTensor input = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(inputData),
                new long[]{1, MEL_WINDOW, MEL_BINS, 1})) {
            try (OrtSession.Result result = embeddingSession.run(
                    Collections.singletonMap("input_1", input))) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                FloatBuffer values = output.getFloatBuffer();
                if (values.remaining() < EMBEDDING_SIZE) {
                    throw new IllegalStateException("Unexpected embedding output size");
                }
                float[] embedding = new float[EMBEDDING_SIZE];
                for (int i = 0; i < EMBEDDING_SIZE; i++) {
                    embedding[i] = values.get();
                }
                return embedding;
            }
        }
    }

    private float predict() throws Exception {
        float[] features = new float[CLASSIFIER_WINDOW * EMBEDDING_SIZE];
        int offset = 0;
        int skip = featureHistory.size() - CLASSIFIER_WINDOW;
        int index = 0;
        for (float[] feature : featureHistory) {
            if (index++ < skip) continue;
            System.arraycopy(feature, 0, features, offset, EMBEDDING_SIZE);
            offset += EMBEDDING_SIZE;
        }

        try (OnnxTensor input = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(features),
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
        try { classifierSession.close(); } catch (Exception ignored) {}
        try { embeddingSession.close(); } catch (Exception ignored) {}
        try { melSession.close(); } catch (Exception ignored) {}
    }
}
