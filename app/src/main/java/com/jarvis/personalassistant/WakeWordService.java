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
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public class WakeWordService extends Service {
    public static final String ACTION_WAKE_COMMAND = "com.jarvis.personalassistant.WAKE_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    private static final String CHANNEL_ID = "jarvis_wake_channel";
    private static final int NOTIFICATION_ID = 991;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SAMPLES = 1600; // 100 ms

    public static volatile boolean isRunning = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile boolean wakeDetected = false;
    private volatile boolean recording = false;
    private volatile boolean speechPlaying = false;
    private volatile boolean engineReady = false;
    private volatile long ignoreWakeUntilMs = 0L;
    private boolean commandCaptureFinished = true;
    private volatile String activeUtteranceId = "";
    private AudioRecord audioRecord;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private Thread audioThread;
    private OpenWakeWordEngine wakeWordEngine;
    private SpeechRecognizer commandRecognizer;
    private PowerManager.WakeLock wakeLock;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech;
    private Intent pendingCommandSpeechIntent;
    private int audioRecoveryAttempts = 0;
    private final VoiceActivityDetector voiceActivityDetector = new VoiceActivityDetector();
    private final WakeWordAudioVerifier wakeWordAudioVerifier = new WakeWordAudioVerifier();
    private final AudioPreRollBuffer audioPreRollBuffer = new AudioPreRollBuffer();
    private final WakeWordMetrics wakeWordMetrics = new WakeWordMetrics();
    private volatile short[] lastWakePreRoll = new short[0];
    private volatile long lastMetricsLogAtMs = 0L;
    private int serviceStartBatteryPercent = -1;
    private volatile boolean commandAudioRunning = false;
    private volatile AudioRecord commandAudioRecord;
    private volatile ParcelFileDescriptor commandAudioReadFd;
    private volatile ParcelFileDescriptor commandAudioWriteFd;
    private volatile OutputStream commandAudioOutput;
    private final Runnable healthCheck = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            if (engineReady && !wakeDetected && !speechPlaying) {
                if (recording && wakeWordMetrics.isAudioStalled(now, 20000L)) {
                    recoverAudio("Audio frames stalled for 20 seconds");
                } else if (!recording) {
                    android.util.Log.w("JARVIS-KWS", "Health monitor restarting idle audio capture");
                    startAudioCapture();
                } else if (audioThread == null || !audioThread.isAlive()) {
                    recoverAudio("Audio worker stopped unexpectedly");
                }
            }
            if (now - lastMetricsLogAtMs >= 300000L) {
                lastMetricsLogAtMs = now;
                logWakeDiagnostics("periodic");
            }
            handler.postDelayed(this, 15000L);
        }
    };

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
            wakeWordMetrics.start(SystemClock.elapsedRealtime(), android.os.Process.getElapsedCpuTime());
            serviceStartBatteryPercent = getBatteryPercent();
            lastMetricsLogAtMs = SystemClock.elapsedRealtime();
            handler.removeCallbacks(healthCheck);
            handler.postDelayed(healthCheck, 15000L);
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
                .setContentText("OpenWakeWord offline detection active — say Jarvis")
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
        try {
            wakeWordEngine = new OpenWakeWordEngine(getAssets());
            engineReady = true;
            handler.post(this::startAudioCapture);
        } catch (Exception e) {
            android.util.Log.e("JARVIS-OWW", "OpenWakeWord initialization failed", e);
            handler.post(() -> {
                speak("Offline wake engine could not start. Please reopen JARVIS.");
                stopSelf();
            });
        }
    }

    private void startAudioCapture() {
        if (!running || !engineReady || recording || wakeDetected || speechPlaying) return;
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

            // Use device-provided acoustic processing when available. These effects
            // are optional: unsupported devices continue with the raw microphone path.
            attachAudioProcessing(audioRecord.getAudioSessionId());

            recording = true;
            isRunning = true;
            audioRecoveryAttempts = 0;
            audioThread = new Thread(this::processAudio, "Jarvis-Continuous-KWS");
            audioThread.start();
        } catch (SecurityException e) {
            stopSelf();
        } catch (Exception e) {
            releaseAudioRecord();
            recoverAudio("Microphone start failed: " + e.getMessage());
        }
    }

    private void attachAudioProcessing(int audioSessionId) {
        releaseAudioProcessing();

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId);
                if (echoCanceler != null) {
                    int status = echoCanceler.setEnabled(true);
                    android.util.Log.i("JARVIS-AUDIO",
                            "Acoustic echo cancellation enabled=" +
                                    (status == android.media.audiofx.AudioEffect.SUCCESS));
                }
            }
        } catch (Exception e) {
            android.util.Log.w("JARVIS-AUDIO", "AEC unavailable; using raw microphone", e);
            releaseAudioEffect(echoCanceler);
            echoCanceler = null;
        }

        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                if (noiseSuppressor != null) {
                    int status = noiseSuppressor.setEnabled(true);
                    android.util.Log.i("JARVIS-AUDIO",
                            "Noise suppression enabled=" +
                                    (status == android.media.audiofx.AudioEffect.SUCCESS));
                }
            }
        } catch (Exception e) {
            android.util.Log.w("JARVIS-AUDIO", "Noise suppressor unavailable; continuing", e);
            releaseAudioEffect(noiseSuppressor);
            noiseSuppressor = null;
        }
    }

    private void releaseAudioEffect(android.media.audiofx.AudioEffect effect) {
        if (effect == null) return;
        try { effect.setEnabled(false); } catch (Exception ignored) {}
        try { effect.release(); } catch (Exception ignored) {}
    }

    private void releaseAudioProcessing() {
        releaseAudioEffect(echoCanceler);
        releaseAudioEffect(noiseSuppressor);
        echoCanceler = null;
        noiseSuppressor = null;
    }

    private void processAudio() {
        short[] pcm = new short[FRAME_SAMPLES];
        try {
            while (running && recording && !wakeDetected) {
                AudioRecord recorder = audioRecord;
                if (recorder == null) break;
                int count = recorder.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (count > 0) {
                    wakeWordMetrics.onFrame(SystemClock.elapsedRealtime());
                    audioPreRollBuffer.add(pcm, count);
                    boolean speechActive = voiceActivityDetector.update(pcm, count);
                    wakeWordAudioVerifier.addFrame(pcm, count, speechActive);
                    if (wakeWordEngine != null && wakeWordEngine.acceptAudio(pcm, count)) {
                        // Stage two: corroborate the neural temporal score with recent
                        // voice activity and basic signal-quality checks.
                        if (!wakeWordAudioVerifier.verify()) {
                            android.util.Log.i("JARVIS-OWW",
                                    "Wake candidate rejected by acoustic verification");
                            wakeWordMetrics.onCandidateRejected();
                            wakeWordAudioVerifier.reset();
                            continue;
                        }
                        // Ignore stale classifier confidence briefly after a command.
                        if (SystemClock.elapsedRealtime() < ignoreWakeUntilMs) {
                            wakeWordAudioVerifier.reset();
                            continue;
                        }
                        wakeDetected = true;
                        wakeWordMetrics.onWakeAccepted();
                        lastWakePreRoll = audioPreRollBuffer.snapshot();
                        android.util.Log.i("JARVIS-OWW",
                                "Wake word passed neural and acoustic verification");
                        wakeWordAudioVerifier.reset();
                        break;
                    }
                } else if (count < 0) {
                    throw new IllegalStateException("AudioRecord read error: " + count);
                } else {
                    // Avoid a tight CPU spin if a vendor AudioRecord returns zero.
                    SystemClock.sleep(5L);
                }
            }
        } catch (Exception e) {
            if (running && !wakeDetected) {
                android.util.Log.e("JARVIS-KWS", "Audio capture failed", e);
                handler.post(() -> recoverAudio(e.getMessage()));
            }
        } finally {
            recording = false;
            isRunning = false;
            releaseAudioRecord();
            if (running && wakeDetected) {
                handler.postDelayed(this::beginCommandCapture, 250);
            } else if (running) {
                handler.post(() -> {
                    if (running && !wakeDetected && !recording && !speechPlaying) {
                        recoverAudio("Audio capture worker exited unexpectedly");
                    }
                });
            }
        }
    }

    private void beginCommandCapture() {
        if (!running || !wakeDetected) return;
        commandCaptureFinished = false;
        pendingCommandSpeechIntent = null;
        android.util.Log.i("JARVIS-AUDIO", "Wake pre-roll retained: "
                + lastWakePreRoll.length + " PCM samples (~"
                + (lastWakePreRoll.length / 16) + " ms at 16 kHz)");

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            commandCaptureFinished = true;
            wakeDetected = false;
            ignoreWakeUntilMs = SystemClock.elapsedRealtime() + 1800L;
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
                @Override public void onEndOfSpeech() { stopInjectedCommandAudio(); }
                @Override public void onError(int error) {
                    stopInjectedCommandAudio();
                    finishCommandCapture();
                }
                @Override public void onResults(android.os.Bundle results) {
                    java.util.ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    float[] confidenceScores = results.getFloatArray("confidence_scores");
                    String command = CommandResultGuard.selectBest(matches, confidenceScores);
                    if (!command.isEmpty()) {
                        dispatchCommand(command);
                    } else {
                        android.util.Log.i("JARVIS-ASR",
                                "Rejected low-confidence, empty, or wake-only transcript alternatives");
                    }
                    stopInjectedCommandAudio();
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
            speech.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
            speech.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
            speech.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L);

            // Start command recognition immediately after the spoken acknowledgement
            // finishes, instead of relying on a fixed 900 ms delay.
            pendingCommandSpeechIntent = speech;
            speak("Yes, Pratik Boss.");
            if (pendingCommandSpeechIntent != null && ttsReady && !speechPlaying) {
                startPendingCommandRecognition();
            }
        } catch (Exception e) {
            finishCommandCapture();
        }
    }

    private void startPendingCommandRecognition() {
        Intent speech = pendingCommandSpeechIntent;
        pendingCommandSpeechIntent = null;
        if (!running || !wakeDetected || commandCaptureFinished || commandRecognizer == null || speech == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                    commandAudioReadFd = pipe[0];
                    commandAudioWriteFd = pipe[1];
                    speech.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, commandAudioReadFd);
                    speech.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
                    speech.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT);
                    speech.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE);
                    commandRecognizer.startListening(speech);
                    startInjectedCommandAudio();
                    android.util.Log.i("JARVIS-ASR", "Started PCM audio-source injection with wake pre-roll");
                    return;
                } catch (Exception injectionError) {
                    android.util.Log.w("JARVIS-ASR", "PCM injection unavailable; falling back to recognizer microphone", injectionError);
                    stopInjectedCommandAudio();
                    speech.removeExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE);
                }
            }
            commandRecognizer.startListening(speech);
        } catch (Exception e) {
            android.util.Log.w("JARVIS-ASR", "Command recognizer start failed", e);
            finishCommandCapture();
        }
    }

    /**
     * Android 13+ audio-source injection: stream the wake pre-roll first, then
     * continue with live 16 kHz mono PCM for the command utterance.
     */
    private void startInjectedCommandAudio() {
        final ParcelFileDescriptor writeFd = commandAudioWriteFd;
        if (writeFd == null) return;
        commandAudioRunning = true;
        new Thread(() -> {
            AudioRecord source = null;
            try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writeFd)) {
                commandAudioOutput = output;
                short[] preRoll = lastWakePreRoll;
                if (preRoll != null && preRoll.length > 0) {
                    byte[] preRollBytes = new byte[preRoll.length * 2];
                    for (int i = 0; i < preRoll.length; i++) {
                        preRollBytes[i * 2] = (byte) (preRoll[i] & 0xff);
                        preRollBytes[i * 2 + 1] = (byte) ((preRoll[i] >>> 8) & 0xff);
                    }
                    output.write(preRollBytes);
                    output.flush();
                    android.util.Log.i("JARVIS-ASR", "Injected pre-roll samples=" + preRoll.length);
                }
                int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minBuffer <= 0) throw new IOException("Invalid command audio buffer");
                source = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuffer * 2, FRAME_SAMPLES * 4));
                if (source.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IOException("Command AudioRecord initialization failed");
                }
                commandAudioRecord = source;
                source.startRecording();
                short[] pcm = new short[FRAME_SAMPLES];
                byte[] bytes = new byte[FRAME_SAMPLES * 2];
                while (commandAudioRunning && running && !commandCaptureFinished) {
                    int count = source.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                    if (count < 0) throw new IOException("Command audio read error " + count);
                    if (count == 0) {
                        SystemClock.sleep(5L);
                        continue;
                    }
                    for (int i = 0; i < count; i++) {
                        bytes[i * 2] = (byte) (pcm[i] & 0xff);
                        bytes[i * 2 + 1] = (byte) ((pcm[i] >>> 8) & 0xff);
                    }
                    output.write(bytes, 0, count * 2);
                    output.flush();
                }
            } catch (Exception e) {
                if (commandAudioRunning && running && !commandCaptureFinished) {
                    android.util.Log.w("JARVIS-ASR", "PCM injection stream stopped", e);
                }
            } finally {
                commandAudioRunning = false;
                commandAudioOutput = null;
                commandAudioRecord = null;
                if (source != null) {
                    try { if (source.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) source.stop(); }
                    catch (Exception ignored) {}
                    try { source.release(); } catch (Exception ignored) {}
                }
                closeCommandAudioDescriptors();
            }
        }, "Jarvis-ASR-PCM-Source").start();
    }

    private void stopInjectedCommandAudio() {
        commandAudioRunning = false;
        AudioRecord source = commandAudioRecord;
        if (source != null) {
            try { if (source.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) source.stop(); }
            catch (Exception ignored) {}
        }
        OutputStream output = commandAudioOutput;
        if (output != null) {
            try { output.close(); } catch (Exception ignored) {}
        }
        closeCommandAudioDescriptors();
    }

    private synchronized void closeCommandAudioDescriptors() {
        ParcelFileDescriptor readFd = commandAudioReadFd;
        ParcelFileDescriptor writeFd = commandAudioWriteFd;
        commandAudioReadFd = null;
        commandAudioWriteFd = null;
        if (readFd != null) {
            try { readFd.close(); } catch (Exception ignored) {}
        }
        if (writeFd != null) {
            try { writeFd.close(); } catch (Exception ignored) {}
        }
    }

    private void finishCommandCapture() {
        // SpeechRecognizer may report both an error and a completion callback.
        // Finish this wake cycle only once.
        if (commandCaptureFinished) return;
        commandCaptureFinished = true;
        pendingCommandSpeechIntent = null;
        stopInjectedCommandAudio();
        if (commandRecognizer != null) {
            try { commandRecognizer.cancel(); } catch (Exception ignored) {}
            try { commandRecognizer.destroy(); } catch (Exception ignored) {}
            commandRecognizer = null;
        }
        wakeDetected = false;
        // Let the previous wake-word audio age out of the rolling classifier window.
        ignoreWakeUntilMs = SystemClock.elapsedRealtime() + 1800L;
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
            final String prompt = memoryContext + "\n\nYou are JARVIS running in background voice mode. Answer naturally in Hindi/Hinglish or the language used. Be concise. Do not claim a phone action was performed unless confirmed. If a task requires opening an app or screen, explain that the user must open JARVIS.\nPratik Boss says: " + raw;
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
        isRunning = false;
        releaseAudioRecord();
        wakeWordMetrics.onAudioRecovery();
        audioRecoveryAttempts = Math.min(audioRecoveryAttempts + 1, 6);
        long retryDelayMs = Math.min(1500L << (audioRecoveryAttempts - 1), 30000L);
        android.util.Log.i("JARVIS-KWS", "Retrying microphone in " + retryDelayMs
                + " ms (attempt " + audioRecoveryAttempts + ")");
        restartStandby(retryDelayMs);
    }

    private void logWakeDiagnostics(String reason) {
        android.util.Log.i("JARVIS-METRICS", reason + " " + wakeWordMetrics.snapshot(
                SystemClock.elapsedRealtime(), android.os.Process.getElapsedCpuTime())
                + ",preRollSamples=" + lastWakePreRoll.length
                + ",batteryStart=" + serviceStartBatteryPercent
                + ",batteryNow=" + getBatteryPercent()
                + ",screenInteractive=" + isScreenInteractive()
                + ",powerSave=" + isPowerSaveMode());
    }

    private int getBatteryPercent() {
        try {
            android.os.BatteryManager battery =
                    (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            return battery == null ? -1 : battery.getIntProperty(
                    android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception ignored) { return -1; }
    }

    private boolean isScreenInteractive() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Exception ignored) { return false; }
    }

    private boolean isPowerSaveMode() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Exception ignored) { return false; }
    }

    private synchronized void releaseAudioRecord() {
        releaseAudioProcessing();
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
            if (running && wakeDetected && !commandCaptureFinished
                    && pendingCommandSpeechIntent != null) {
                startPendingCommandRecognition();
            } else if (running && !recording && !wakeDetected) {
                startAudioCapture();
            }
        });
    }

    @Override public void onDestroy() {
        logWakeDiagnostics("service-stop");
        running = false;
        engineReady = false;
        isRunning = false;
        recording = false;
        wakeDetected = false;
        pendingCommandSpeechIntent = null;
        handler.removeCallbacksAndMessages(null);

        if (commandRecognizer != null) {
            try { commandRecognizer.cancel(); } catch (Exception ignored) {}
            try { commandRecognizer.destroy(); } catch (Exception ignored) {}
            commandRecognizer = null;
        }

        releaseAudioRecord();
        if (wakeWordEngine != null) {
            try { wakeWordEngine.close(); } catch (Exception ignored) {}
            wakeWordEngine = null;
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
