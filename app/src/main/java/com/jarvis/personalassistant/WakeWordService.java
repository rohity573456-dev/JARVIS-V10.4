package com.jarvis.personalassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotter;
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig;
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig;
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig;
import com.k2fsa.sherpa.onnx.QnnConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class WakeWordService extends Service {
    public static final String ACTION_WAKE_COMMAND = "com.jarvis.personalassistant.WAKE_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    private static final String CHANNEL_ID = "jarvis_wake_channel";
    private static final int NOTIFICATION_ID = 991;
    private static final String MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01";
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SAMPLES = 1600; // 100 ms
    private static final String[] MODEL_FILES = {
            "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "tokens.txt",
            "keywords.txt"
    };

    public static volatile boolean isRunning = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile boolean wakeDetected = false;
    private volatile boolean recording = false;
    private volatile boolean speechPlaying = false;
    private volatile String activeUtteranceId = "";
    private AudioRecord audioRecord;
    private Thread audioThread;
    private KeywordSpotter keywordSpotter;
    private JarvisOnnxWakeEngine onnxWakeEngine;
    private OnlineStream keywordStream;
    private SpeechRecognizer commandRecognizer;
    private PowerManager.WakeLock wakeLock;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        tts = new TextToSpeech(getApplicationContext(), result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("en", "IN"));
                tts.setPitch(0.78f);
                tts.setSpeechRate(0.90f);
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { speechPlaying = true; }
                    @Override public void onDone(String utteranceId) { finishSpeech(utteranceId); }
                    @Override public void onError(String utteranceId) { finishSpeech(utteranceId); }
                    @Override public void onStop(String utteranceId, boolean interrupted) { finishSpeech(utteranceId); }
                });
                ttsReady = true;
                if (pendingSpeech != null) {
                    String queued = pendingSpeech;
                    pendingSpeech = null;
                    speak(queued);
                }
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        } catch (SecurityException | IllegalStateException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            running = true;
            isRunning = true;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS:WakeWordCpu");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(10 * 60 * 60 * 1000L);
            }
            new Thread(this::initializeAndStart, "Jarvis-KWS-Init").start();
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("JARVIS voice standby")
                .setContentText("Offline wake detection active — say Jarvis")
                .setContentIntent(pending).setOngoing(true).build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "JARVIS Wake Listening", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Continuous offline microphone standby for JARVIS");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void initializeAndStart() {
        // Prefer the custom single-word ONNX detector when its trained model is installed.
        // Keep Sherpa as a safe fallback until that model is supplied and validated.
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                onnxWakeEngine = new JarvisOnnxWakeEngine(getApplicationContext());
                handler.post(this::startWakeDetection);
                android.util.Log.i("JARVIS-KWS", "Custom ONNX wake engine selected");
                return;
            } catch (Exception unavailable) {
                onnxWakeEngine = null;
                android.util.Log.w("JARVIS-KWS",
                        "Custom Jarvis ONNX model unavailable; using Sherpa fallback", unavailable);
            }
        }

        try {
            File dir = new File(getFilesDir(), MODEL_DIR);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create KWS model folder");

            for (String name : MODEL_FILES) {
                File target = new File(dir, name);
                if (!target.exists() || target.length() == 0) {
                    copyAsset(MODEL_DIR + "/" + name, target);
                }
                if (!target.exists() || target.length() == 0) {
                    throw new IllegalStateException("Missing wake model file: " + name);
                }
            }

            String prefix = dir.getAbsolutePath() + "/";
            // sherpa-onnx 2.0.1 Android AAR exposes Kotlin data-class
            // constructors (not the Java builder API).
            OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig(
                    prefix + MODEL_FILES[0],
                    prefix + MODEL_FILES[1],
                    prefix + MODEL_FILES[2],
                    new QnnConfig("", "", ""));
            OnlineModelConfig model = new OnlineModelConfig(
                    transducer,
                    new OnlineParaformerModelConfig("", ""),
                    new OnlineZipformer2CtcModelConfig(""),
                    new OnlineNeMoCtcModelConfig(""),
                    new OnlineToneCtcModelConfig(""),
                    prefix + "tokens.txt",
                    2,
                    false,
                    "cpu",
                    "zipformer2",
                    "",
                    "");
            FeatureConfig features = new FeatureConfig(SAMPLE_RATE, 80, 0.0f);
            KeywordSpotterConfig config = new KeywordSpotterConfig(
                    features,
                    model,
                    4,
                    prefix + "keywords.txt",
                    2.0f,
                    0.25f,
                    2);

            // Null AssetManager selects file-path loading because model files
            // have already been copied to this app's private files directory.
            keywordSpotter = new KeywordSpotter(null, config);
            keywordStream = keywordSpotter.createStream("");
            handler.post(this::startAudioCapture);
        } catch (Exception e) {
            android.util.Log.e("JARVIS-KWS", "Offline wake engine initialization failed", e);
            handler.post(() -> {
                speak("Offline wake engine could not start. Please reopen JARVIS.");
                stopSelf();
            });
        }
    }

    private void copyAsset(String assetPath, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create model directory");
        }
        try (InputStream in = getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.flush();
        }
    }

    private void startWakeDetection() {
        if (!running || wakeDetected || speechPlaying) return;
        if (onnxWakeEngine == null) {
            startWakeDetection();
            return;
        }
        try {
            if (!onnxWakeEngine.isStarted()) {
                onnxWakeEngine.start(confidence -> {
                    if (!running || wakeDetected) return;
                    wakeDetected = true;
                    android.util.Log.i("JARVIS-KWS",
                            "Custom ONNX Jarvis wake word detected: " + confidence);
                    try { onnxWakeEngine.stop(); } catch (Exception ignored) {}
                    handler.postDelayed(this::beginCommandCapture, 250);
                });
            }
        } catch (Exception error) {
            android.util.Log.e("JARVIS-KWS", "ONNX wake engine start failed", error);
            try { onnxWakeEngine.release(); } catch (Exception ignored) {}
            onnxWakeEngine = null;
            startAudioCapture();
        }
    }

    private void startAudioCapture() {
        if (!running || recording || wakeDetected || speechPlaying) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            recoverAudio("AudioRecord returned invalid buffer size: " + minBuffer);
            return;
        }

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuffer * 2, FRAME_SAMPLES * 4));
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseAudioRecord();
                recoverAudio("AudioRecord initialization failed");
                return;
            }

            audioRecord.startRecording();
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                releaseAudioRecord();
                recoverAudio("Microphone did not enter recording state");
                return;
            }

            recording = true;
            audioThread = new Thread(this::processAudio, "Jarvis-Continuous-KWS");
            audioThread.start();
        } catch (SecurityException e) {
            stopSelf();
        } catch (Exception e) {
            releaseAudioRecord();
            recoverAudio("Microphone start failed: " + e.getMessage());
        }
    }

    private void processAudio() {
        short[] pcm = new short[FRAME_SAMPLES];
        try {
            while (running && recording && !wakeDetected) {
                AudioRecord recorder = audioRecord;
                if (recorder == null) break;
                int count = recorder.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (count > 0) {
                    float[] samples = new float[count];
                    for (int i = 0; i < count; i++) samples[i] = pcm[i] / 32768.0f;
                    keywordStream.acceptWaveform(samples, SAMPLE_RATE);

                    while (keywordSpotter.isReady(keywordStream)) {
                        keywordSpotter.decode(keywordStream);
                        String keyword = keywordSpotter.getResult(keywordStream).getKeyword();
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            keywordSpotter.reset(keywordStream);
                            wakeDetected = true;
                            android.util.Log.i("JARVIS-KWS", "Wake word detected: " + keyword);
                            break;
                        }
                    }
                } else if (count < 0) {
                    throw new IllegalStateException("AudioRecord read error: " + count);
                }
            }
        } catch (Exception e) {
            if (running && !wakeDetected) {
                android.util.Log.e("JARVIS-KWS", "Audio capture failed", e);
                handler.post(() -> recoverAudio(e.getMessage()));
            }
        } finally {
            recording = false;
            releaseAudioRecord();
            if (running && wakeDetected) {
                handler.postDelayed(this::beginCommandCapture, 250);
            }
        }
    }

    private void beginCommandCapture() {
        if (!running || !wakeDetected) return;
        if (onnxWakeEngine != null) {
            try { onnxWakeEngine.stop(); } catch (Exception ignored) {}
        }
        speak("Yes, Commander.");
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            wakeDetected = false;
            restartStandby(1000);
            return;
        }

        try {
            if (commandRecognizer != null) {
                try { commandRecognizer.destroy(); } catch (Exception ignored) {}
            }
            commandRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
            commandRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    finishCommandCapture();
                }
                @Override public void onResults(android.os.Bundle results) {
                    java.util.ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String command = matches == null || matches.isEmpty() ? "" : matches.get(0).trim();
                    if (!command.isEmpty()) dispatchCommand(command);
                    finishCommandCapture();
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) {}
                @Override public void onEvent(int eventType, android.os.Bundle params) {}
            });

            Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
            speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            speech.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

            // Allow the spoken acknowledgement to finish before command capture.
            handler.postDelayed(() -> {
                if (running && commandRecognizer != null) {
                    try { commandRecognizer.startListening(speech); }
                    catch (Exception e) { finishCommandCapture(); }
                }
            }, 900);
        } catch (Exception e) {
            finishCommandCapture();
        }
    }

    private void finishCommandCapture() {
        if (commandRecognizer != null) {
            try { commandRecognizer.cancel(); } catch (Exception ignored) {}
            try { commandRecognizer.destroy(); } catch (Exception ignored) {}
            commandRecognizer = null;
        }
        wakeDetected = false;
        restartStandby(700);
    }

    private void dispatchCommand(String command) {
        if (MainActivity.isWakeReceiverActive()) {
            Intent event = new Intent(ACTION_WAKE_COMMAND);
            event.setPackage(getPackageName());
            event.putExtra(EXTRA_COMMAND, command);
            sendBroadcast(event);
        } else {
            executeBackgroundCommand(command);
        }
    }

    /**
     * Service-safe fallback for commands heard while the Activity is not alive.
     * It handles a small set of local actions directly and otherwise routes to the
     * configured AI provider without attempting a restricted background UI launch.
     */
    private void executeBackgroundCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;
        final String raw = command.trim();
        final String q = raw.toLowerCase(Locale.ROOT);

        if (q.contains("time") || q.contains("samay") || q.contains("kitne baje")) {
            speak("Abhi " + new java.text.SimpleDateFormat("hh:mm a",Locale.getDefault()).format(new java.util.Date()) + " baj rahe hain.");
            return;
        }
        if (q.contains("date") || q.contains("tareekh") || q.contains("aaj ki date")) {
            speak("Aaj " + new java.text.SimpleDateFormat("dd MMMM yyyy",new Locale("hi","IN")).format(new java.util.Date()) + " hai.");
            return;
        }
        if (q.contains("battery")) {
            try {
                Intent battery = registerReceiver(null,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = battery == null ? -1 : battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1);
                speak(level < 0 ? "Battery status unavailable." : "Battery " + level + " percent hai.");
            } catch (Exception e) { speak("Battery status unavailable."); }
            return;
        }
        if (q.contains("volume up") || q.contains("volume down") || q.equals("mute")) {
            try {
                android.media.AudioManager audio = (android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                if (audio != null) {
                    int direction = q.contains("up") ? android.media.AudioManager.ADJUST_RAISE :
                        q.contains("down") ? android.media.AudioManager.ADJUST_LOWER : android.media.AudioManager.ADJUST_MUTE;
                    audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,direction,android.media.AudioManager.FLAG_SHOW_UI);
                    speak("Media volume updated.");
                }
            } catch (Exception e) { speak("Volume control unavailable."); }
            return;
        }
        if (q.contains("flashlight") || q.contains("torch")) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                speak("Flashlight ke liye JARVIS app kholkar Camera permission allow karein.");
                return;
            }
            try {
                android.hardware.camera2.CameraManager manager =
                    (android.hardware.camera2.CameraManager)getSystemService(CAMERA_SERVICE);
                boolean enabled = q.contains("on") || q.contains("enable") || q.contains("chalu");
                String selected = null;
                for (String id : manager.getCameraIdList()) {
                    Boolean available = manager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (Boolean.TRUE.equals(available)) { selected=id; break; }
                }
                if (selected == null) speak("Flashlight hardware nahi mila.");
                else { manager.setTorchMode(selected,enabled); speak(enabled ? "Flashlight ON." : "Flashlight OFF."); }
            } catch (Exception e) { speak("Flashlight control unavailable."); }
            return;
        }

        new Thread(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences("jarvis_memory",MODE_PRIVATE);
            boolean privateMode = prefs.getBoolean("memory_private_mode",false);
            SecureStore keys = new SecureStore(getApplicationContext());
            String gemini = keys.get("gemini_key");
            String groq = keys.get("groq_key");
            String localPath = prefs.getString("local_model_path","");
            String memoryContext = "No saved memory context.";
            JarvisMemoryStore store = null;
            try {
                if (!privateMode) {
                    store = new JarvisMemoryStore(getApplicationContext());
                    memoryContext = store.getMemoryContext(raw,8);
                }
            } catch (Exception ignored) {
                memoryContext = "Memory context unavailable.";
            } finally {
                if (store != null) store.close();
            }
            final String prompt = memoryContext + "\n\nYou are JARVIS running in background voice mode. Answer naturally in Hindi/Hinglish or the language used. Be concise. Do not claim a phone action was performed unless confirmed. If a task requires opening an app or screen, explain that the user must open JARVIS.\nCommander says: " + raw;
            String result = "ERROR: no online provider configured.";
            if (!gemini.isEmpty()) {
                result = OnlineAiClient.ask("https://generativelanguage.googleapis.com/v1beta/openai",gemini,
                    prefs.getString("gemini_model","gemini-2.5-flash"),prompt);
            }
            if (result.startsWith("ERROR") && !groq.isEmpty()) {
                result = OnlineAiClient.ask("https://api.groq.com/openai/v1",groq,
                    prefs.getString("groq_model","openai/gpt-oss-120b"),prompt);
            }
            if (!result.startsWith("ERROR")) {
                final String answer = result;
                handler.post(() -> speak(answer));
                return;
            }
            if (!localPath.isEmpty() && new File(localPath).exists()) {
                LocalAiEngine.ask(localPath,prompt,answer -> {
                    handler.post(() -> speak(answer));
                    return kotlin.Unit.INSTANCE;
                });
            } else {
                handler.post(() -> speak("AI provider configured nahi hai. JARVIS app kholkar ONLINE AI SETUP karein."));
            }
        },"Jarvis-Background-AI").start();
    }

    private void restartStandby(long delayMs) {
        if (!running) return;
        handler.postDelayed(() -> {
            if (running && !recording && !wakeDetected) startAudioCapture();
        }, delayMs);
    }

    private void recoverAudio(String reason) {
        if (!running || wakeDetected) return;
        android.util.Log.w("JARVIS-KWS", reason);
        recording = false;
        releaseAudioRecord();
        restartStandby(1500);
    }

    private synchronized void releaseAudioRecord() {
        AudioRecord recorder = audioRecord;
        audioRecord = null;
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
            } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
        }
    }

    private void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ttsReady || tts == null) {
            pendingSpeech = text;
            return;
        }
        String id = "jarvis-service-" + System.nanoTime();
        activeUtteranceId = id;
        speechPlaying = true;
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        if (result == TextToSpeech.ERROR) finishSpeech(id);
    }

    private void finishSpeech(String utteranceId) {
        if (utteranceId == null || !utteranceId.equals(activeUtteranceId)) return;
        handler.post(() -> {
            if (!utteranceId.equals(activeUtteranceId)) return;
            speechPlaying = false;
            activeUtteranceId = "";
            if (running && !recording && !wakeDetected) startAudioCapture();
        });
    }

    @Override public void onDestroy() {
        running = false;
        isRunning = false;
        recording = false;
        wakeDetected = false;
        handler.removeCallbacksAndMessages(null);

        if (commandRecognizer != null) {
            try { commandRecognizer.cancel(); } catch (Exception ignored) {}
            try { commandRecognizer.destroy(); } catch (Exception ignored) {}
            commandRecognizer = null;
        }

        releaseAudioRecord();
        if (onnxWakeEngine != null) {
            try { onnxWakeEngine.release(); } catch (Exception ignored) {}
            onnxWakeEngine = null;
        }
        if (keywordStream != null) {
            try { keywordStream.release(); } catch (Exception ignored) {}
            keywordStream = null;
        }
        if (keywordSpotter != null) {
            try { keywordSpotter.release(); } catch (Exception ignored) {}
            keywordSpotter = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
