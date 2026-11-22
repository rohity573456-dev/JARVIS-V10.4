
package com.jarvis.personalassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

public class WakeWordService extends Service {

    public static final String ACTION_WAKE_COMMAND =
            "com.jarvis.personalassistant.WAKE_COMMAND";
    public static final String EXTRA_COMMAND = "command";

    private static final String CHANNEL_ID = "jarvis_wake_channel";
    private static final int NOTIFICATION_ID = 991;

    public static volatile boolean isRunning = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private PowerManager.WakeLock wakeLock;
    private TextToSpeech tts;

    private boolean ttsReady = false;
    private boolean running = false;
    private boolean listening = false;
    private boolean restartQueued = false;
    private boolean waitingForCommand = false;

    private String pendingSpeech;
    private int consecutiveErrors = 0;
    private long lastWakeAt = 0L;

    private final Runnable commandTimeout = () -> {
        waitingForCommand = false;
        speak("I am listening, Commander. Please say your command again.");
        scheduleListen(1800);
    };

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        tts = new TextToSpeech(getApplicationContext(), result -> {
            if (result == TextToSpeech.SUCCESS) {
                int language = tts.setLanguage(new Locale("en", "IN"));

                if (language == TextToSpeech.LANG_MISSING_DATA ||
                        language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(new Locale("hi", "IN"));
                }

                tts.setPitch(0.78f);
                tts.setSpeechRate(0.90f);
                ttsReady = true;

                if (pendingSpeech != null) {
                    String queued = pendingSpeech;
                    pendingSpeech = null;
                    speak(queued);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                );
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

            PowerManager pm =
                    (PowerManager) getSystemService(POWER_SERVICE);

            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "JARVIS:WakeWordCpu"
                );

                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(10 * 60 * 60 * 1000L);
            }

            scheduleListen(500);
        }

        return START_STICKY;
    }

    private Notification buildNotification() {

        Intent open = new Intent(this, MainActivity.class);

        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23
                                ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, CHANNEL_ID)
                        : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("JARVIS voice standby")
                .setContentText("Wake listening active — say Jarvis")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS Wake Listening",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Foreground microphone service for JARVIS wake-word detection"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void ensureRecognizer() {

        if (recognizer != null) return;

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition service is unavailable. Please enable Google Speech Services.");
            scheduleListen(5000);
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(
                getApplicationContext()
        );

        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                listening = true;
            }

            @Override
            public void onBeginningOfSpeech() {
                listening = true;
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                listening = false;
            }

            @Override
            public void onError(int error) {

                listening = false;

                if (!running) return;

                if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                    destroyRecognizer();
                    scheduleListen(1800);
                    return;
                }

                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {

                    consecutiveErrors++;

                    scheduleListen(1200);
                    return;
                }

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    scheduleListen(1200);
                    return;
                }

                consecutiveErrors++;

                scheduleListen(
                        Math.min(6000, 700L * consecutiveErrors)
                );
            }

            @Override
            public void onResults(Bundle results) {

                listening = false;
                consecutiveErrors = 0;

                processResults(results);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void scheduleListen(long delayMs) {

        if (!running || restartQueued) return;

        restartQueued = true;

        handler.postDelayed(() -> {

            restartQueued = false;

            if (!running || listening) return;

            try {

                ensureRecognizer();

                if (recognizer == null) return;

                Intent speech = new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        "en-IN"
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                        "en-IN"
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        5
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        false
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_CALLING_PACKAGE,
                        getPackageName()
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        8000L
                );

                speech.putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        6000L
                );

                recognizer.startListening(speech);
                listening = true;

            } catch (SecurityException e) {

                listening = false;
                stopSelf();

            } catch (Exception e) {

                listening = false;
                destroyRecognizer();
                scheduleListen(1500);
            }

        }, delayMs);
    }

    private void processResults(Bundle results) {

        ArrayList<String> matches =
                results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                );

        String spoken =
                matches == null || matches.isEmpty()
                        ? ""
                        : matches.get(0).trim();

        String lower = spoken.toLowerCase(Locale.ROOT);

        if (waitingForCommand) {

            waitingForCommand = false;
            handler.removeCallbacks(commandTimeout);

            if (!spoken.isEmpty()) {
                dispatchCommand(spoken);
            } else {
                speak("I did not catch that, Commander.");
            }

            scheduleListen(1800);
            return;
        }

        int wakeEnd = wakePhraseEnd(lower);

        if (wakeEnd < 0 && matches != null) {

            for (String candidate : matches) {

                if (candidate == null) continue;

                int candidateEnd =
                        wakePhraseEnd(candidate.toLowerCase(Locale.ROOT));

                if (candidateEnd >= 0) {

                    spoken = candidate.trim();
                    lower = spoken.toLowerCase(Locale.ROOT);
                    wakeEnd = candidateEnd;
                    break;
                }
            }
        }

        if (wakeEnd >= 0) {

            long now = android.os.SystemClock.elapsedRealtime();

            if (now - lastWakeAt > 1800) {

                lastWakeAt = now;

                String rest = spoken
                        .substring(Math.min(spoken.length(), wakeEnd))
                        .trim();

                if (rest.startsWith(",") || rest.startsWith(":")) {
                    rest = rest.substring(1).trim();
                }

                speak("Yes, Commander.");

                if (rest.isEmpty()) {

                    waitingForCommand = true;

                    handler.removeCallbacks(commandTimeout);
                    handler.postDelayed(commandTimeout, 9000);

                } else {

                    dispatchCommand(rest);
                }
            }
        }

        scheduleListen(300);
    }

    // ONLY WAKE WORD: JARVIS
    private int wakePhraseEnd(String lower) {

        String phrase = "jarvis";

        int index = lower.indexOf(phrase);

        if (index >= 0) {
            return index + phrase.length();
        }

        return -1;
    }

    private void dispatchCommand(String command) {

        if (MainActivity.isWakeReceiverActive()) {

            Intent event = new Intent(ACTION_WAKE_COMMAND);

            event.setPackage(getPackageName());

            event.putExtra(EXTRA_COMMAND, command);

            sendBroadcast(event);

        } else {

            speak("I heard: " + command +
                    ". Open JARVIS to execute this command.");
        }
    }

    private void speak(String text) {

        if (text == null || text.trim().isEmpty()) return;

        if (!ttsReady || tts == null) {
            pendingSpeech = text;
            return;
        }

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis-service"
        );
    }

    private void destroyRecognizer() {

        if (recognizer != null) {

            try {
                recognizer.cancel();
            } catch (Exception ignored) {}

            try {
                recognizer.destroy();
            } catch (Exception ignored) {}

            recognizer = null;
        }

        listening = false;
    }

    @Override
    public void onDestroy() {

        running = false;
        isRunning = false;

        handler.removeCallbacksAndMessages(null);

        waitingForCommand = false;

        destroyRecognizer();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        ttsReady = false;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
