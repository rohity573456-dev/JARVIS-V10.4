package com.jarvis.personalassistant;

import android.content.res.AssetManager;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Independent, separately trained multi-variant Jarvis neural verifier.
 * This is a generic wake phrase verifier, not Pratik speaker identification.
 */
final class IndependentWakeWordVerifier implements AutoCloseable {
    private static final String TAG = "JARVIS-VERIFY";
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final int keywordIndex;
    private final int melFrames;
    private final int melBins;
    private final long[] inputShape;
    private final float threshold;
    private final float[] inputData;

    IndependentWakeWordVerifier(AssetManager assets, OrtEnvironment environment) throws Exception {
        this.environment = environment;
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream input = assets.open("openwakeword/multi_jarvis_v9.3.zip");
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                    entries.put(entry.getName(), output.toByteArray());
                }
                zip.closeEntry();
            }
        }

        byte[] infoBytes = entries.get("model_info.json");
        if (infoBytes == null) throw new IllegalStateException("Verifier model_info.json missing");
        JSONObject info = new JSONObject(new String(infoBytes, StandardCharsets.UTF_8));
        JSONArray keywords = info.getJSONArray("keywords");
        int foundIndex = -1;
        for (int i = 0; i < keywords.length(); i++) {
            if ("jarvis".equalsIgnoreCase(keywords.getString(i).trim())) {
                foundIndex = i;
                break;
            }
        }
        if (foundIndex < 0) throw new IllegalStateException("Verifier model has no Jarvis output");

        String modelFile = info.optString("model_file", "model.onnx");
        byte[] modelBytes = entries.get(modelFile);
        if (modelBytes == null) throw new IllegalStateException("Verifier ONNX missing: " + modelFile);

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        OrtSession loaded;
        try {
            loaded = environment.createSession(modelBytes, options);
        } finally {
            options.close();
        }
        this.session = loaded;
        this.inputName = loaded.getInputNames().iterator().next();
        ai.onnxruntime.TensorInfo tensorInfo =
                (ai.onnxruntime.TensorInfo) loaded.getInputInfo().get(inputName).getInfo();
        this.inputShape = tensorInfo.getShape();
        this.melFrames = info.optInt("mel_time", 98);
        this.melBins = info.optInt("n_mels", 32);
        boolean hasTimeDimension = false;
        for (int i = 1; i < inputShape.length - 1; i++) {
            if (inputShape[i] == melFrames) hasTimeDimension = true;
        }
        if (inputShape.length < 3 || inputShape.length > 4
                || inputShape[inputShape.length - 1] != melBins
                || !hasTimeDimension) {
            loaded.close();
            throw new IllegalStateException("Unsupported verifier input shape: "
                    + java.util.Arrays.toString(inputShape));
        }
        this.keywordIndex = foundIndex;
        this.threshold = (float) info.optDouble("threshold", 0.5);
        this.inputData = new float[melFrames * melBins];
        Log.i(TAG, "Independent Jarvis ONNX verifier loaded: index=" + keywordIndex
                + " mel=" + melFrames + "x" + melBins + " threshold=" + threshold);
    }

    boolean verify(float[][] melHistory, int melWrite, int melCount) throws Exception {
        if (melCount < melFrames) return false;
        int historySize = melHistory.length;
        int start = (melWrite - melFrames + historySize) % historySize;
        int offset = 0;
        for (int frame = 0; frame < melFrames; frame++) {
            System.arraycopy(melHistory[(start + frame) % historySize], 0,
                    inputData, offset, melBins);
            offset += melBins;
        }
        try (OnnxTensor input = OnnxTensor.createTensor(environment,
                FloatBuffer.wrap(inputData), inputShape)) {
            try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, input))) {
                OnnxTensor output = (OnnxTensor) result.get(0);
                FloatBuffer scores = output.getFloatBuffer();
                if (scores.remaining() <= keywordIndex) return false;
                float score = scores.get(keywordIndex);
                Log.d(TAG, "Jarvis verifier score=" + score);
                return score >= threshold;
            }
        }
    }

    @Override public void close() {
        try { session.close(); } catch (Exception ignored) {}
    }
}
