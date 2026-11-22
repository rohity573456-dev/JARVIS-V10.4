package com.jarvis.personalassistant;

import android.content.Context;
import android.content.res.AssetManager;

import com.openwakeword.OpenWakeWord;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adapter for the free, fully on-device OpenWakeWord ONNX pipeline.
 *
 * IMPORTANT:
 * - The custom classifier must be placed at assets/wakeword/jarvis.onnx.
 * - It must be trained/exported for the openWakeWord feature pipeline
 *   (classifier input [1, 16, 96], output [1, 1]).
 * - Do not substitute the bundled HEY_JARVIS model: that detects a different phrase.
 *
 * This class deliberately does not start itself from the Service. The service must
 * coordinate microphone ownership with Android SpeechRecognizer before enabling it.
 */
public final class JarvisOnnxWakeEngine {

    public static final String MODEL_ASSET = "wakeword/jarvis.onnx";
    private static final float DEFAULT_THRESHOLD = 0.55f;
    private static final long DEFAULT_DEBOUNCE_MS = 2500L;

    public interface Listener {
        void onWakeDetected(float confidence);
    }

    private final OpenWakeWord detector;
    private boolean started;
    private boolean released;

    public JarvisOnnxWakeEngine(Context context, float threshold, long debounceMs)
            throws IOException {
        Context appContext = context.getApplicationContext();
        verifyModelAsset(appContext.getAssets());

        detector = new OpenWakeWord.Builder(appContext)
                .setModelAsset(MODEL_ASSET)
                .setThreshold(threshold > 0.0f ? threshold : DEFAULT_THRESHOLD)
                .setDebounceMs(debounceMs > 0L ? debounceMs : DEFAULT_DEBOUNCE_MS)
                .build();
    }

    public JarvisOnnxWakeEngine(Context context) throws IOException {
        this(context, DEFAULT_THRESHOLD, DEFAULT_DEBOUNCE_MS);
    }

    private static void verifyModelAsset(AssetManager assets) throws IOException {
        try (InputStream input = assets.open(MODEL_ASSET)) {
            if (input.read() == -1) {
                throw new IOException("Jarvis ONNX model asset is empty: " + MODEL_ASSET);
            }
        } catch (IOException error) {
            throw new IOException(
                    "Custom single-word Jarvis model is not installed. Expected app/src/main/assets/"
                            + MODEL_ASSET
                            + ". The Hey Jarvis model is intentionally not used.",
                    error);
        }
    }

    public synchronized void start(Listener listener) {
        if (released) {
            throw new IllegalStateException("JarvisOnnxWakeEngine has already been released");
        }
        if (started) return;
        if (listener == null) {
            throw new IllegalArgumentException("Wake listener must not be null");
        }

        detector.start(confidence -> {
            Listener callback;
            synchronized (JarvisOnnxWakeEngine.this) {
                if (!started || released) return;
                callback = listener;
            }
            callback.onWakeDetected(confidence);
        });
        started = true;
    }

    public synchronized void stop() {
        if (released || !started) return;
        detector.stop();
        started = false;
    }

    public synchronized void release() {
        if (released) return;
        try {
            if (started) detector.stop();
        } finally {
            started = false;
            released = true;
            detector.release();
        }
    }

    public synchronized boolean isStarted() {
        return started && !released;
    }
}
