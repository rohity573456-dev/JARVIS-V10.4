package com.jarvis.personalassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.os.Bundle
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * V10.4: one foreground service owns the microphone during standby.
 * AudioRecord feeds an offline Sherpa-ONNX keyword spotter continuously.
 * SpeechRecognizer is opened only after the wake phrase has been detected.
 */
class WakeWordService : Service() {
    companion object {
        const val ACTION_WAKE_COMMAND = "com.jarvis.personalassistant.WAKE_COMMAND"
        const val EXTRA_COMMAND = "command"
        private const val CHANNEL_ID = "jarvis_wake_channel"
        private const val NOTIFICATION_ID = 991
        private const val TAG = "JARVIS-KWS"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
        @JvmField @Volatile var isRunning: Boolean = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    @Volatile private var commandMode = false
    @Volatile private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var lastWakeAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val lang = t.setLanguage(Locale("en", "IN"))
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale("hi", "IN"))
                }
                t.setPitch(0.78f)
                t.setSpeechRate(0.90f)
                ttsReady = true
                pendingSpeech?.let { pendingSpeech = null; speak(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else startForeground(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }

        if (active.compareAndSet(false, true)) {
            isRunning = true
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS:ContinuousKws")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(10 * 60 * 60 * 1000L)
            thread(name = "JarvisKwsInit") { initializeKeywordSpotterAndStart() }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS voice standby")
            .setContentText("Offline wake detection active — say Hey Jarvis")
            .setContentIntent(pi).setOngoing(true).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS Wake Listening", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Continuous offline wake-word detection"
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun initializeKeywordSpotterAndStart() {
        try {
            // Upstream Sherpa KWS example model type 1 is the English GigaSpeech Zipformer model.
            val model = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = MODEL_DIR + "/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                    decoder = MODEL_DIR + "/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                    joiner = MODEL_DIR + "/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
                ),
                tokens = MODEL_DIR + "/tokens.txt",
                numThreads = 2,
                modelType = "zipformer2",
                modelingUnit = "bpe",
                bpeVocab = MODEL_DIR + "/bpe.model"
            )
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
                keywordsFile = MODEL_DIR + "/keywords.txt",
                keywordsScore = 2.0f,
                keywordsThreshold = 0.25f
            )
            spotter = KeywordSpotter(assetManager = assets, config = config)
            stream = spotter!!.createStream()
            handler.post { startContinuousAudioCapture() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "KWS initialization failed", e)
            handler.post { speak("Offline wake model could not start. Please rebuild JARVIS V10.4.") }
            active.set(false)
            isRunning = false
            stopSelf()
        }
    }

    private fun startContinuousAudioCapture() {
        if (!active.get() || commandMode || audioRecord != null) return
        try {
            val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minBytes > 0) { "Invalid AudioRecord buffer size: $minBytes" }
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes * 2, 6400))
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
            recorder.startRecording()
            audioRecord = recorder
            val kws = spotter ?: error("KWS model not initialized")
            val kwsStream = stream ?: error("KWS stream not initialized")
            recordThread = thread(name = "JarvisContinuousMic") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val pcm = ShortArray(1600) // 100 ms at 16 kHz
                try {
                    while (active.get() && !commandMode && audioRecord === recorder) {
                        val count = recorder.read(pcm, 0, pcm.size)
                        if (count > 0) {
                            val samples = FloatArray(count) { pcm[it] / 32768.0f }
                            kwsStream.acceptWaveform(samples, SAMPLE_RATE)
                            while (kws.isReady(kwsStream)) {
                                kws.decode(kwsStream)
                                val detected = kws.getResult(kwsStream).keyword
                                if (detected.isNotBlank()) {
                                    kws.reset(kwsStream)
                                    if (detected.contains("HEY_JARVIS", ignoreCase = true) || detected.contains("HEY JARVIS", ignoreCase = true)) {
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        if (now - lastWakeAt > 1800) {
                                            lastWakeAt = now
                                            handler.post { onWakeDetected() }
                                        }
                                    }
                                }
                            }
                        } else if (count < 0) {
                            throw IllegalStateException("AudioRecord.read failed: $count")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Continuous microphone loop stopped", e)
                } finally {
                    // A failed read/decode previously left audioRecord non-null, so the
                    // restart guard refused to open the microphone again. Clear and release
                    // this recorder before scheduling a clean standby restart.
                    val shouldRecover = active.get() && !commandMode && audioRecord === recorder
                    if (audioRecord === recorder) audioRecord = null
                    try { recorder.stop() } catch (_: Exception) {}
                    try { recorder.release() } catch (_: Exception) {}
                    if (shouldRecover) {
                        handler.postDelayed({
                            if (active.get() && !commandMode && audioRecord == null) {
                                startContinuousAudioCapture()
                            }
                        }, 1200)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Could not start continuous microphone", e)
            releaseAudioRecord()
            handler.postDelayed({ if (active.get() && !commandMode) startContinuousAudioCapture() }, 1500)
        }
    }

    private fun onWakeDetected() {
        if (!active.get() || commandMode) return
        commandMode = true
        releaseAudioRecord() // hand off mic exactly once: KWS standby -> one command session
        speak("Yes, Commander.")
        handler.postDelayed({ startCommandRecognition() }, 900)
    }

    private fun startCommandRecognition() {
        if (!active.get()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { finishCommandSession(""); return }
        try {
            commandRecognizer?.destroy()
            commandRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).also { recognizer ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onError(error: Int) { finishCommandSession("") }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        finishCommandSession(matches?.firstOrNull()?.trim().orEmpty())
                    }
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                }
                recognizer.startListening(intent)
            }
            handler.removeCallbacks(commandTimeout)
            handler.postDelayed(commandTimeout, 10000)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Command recognition failed", e)
            finishCommandSession("")
        }
    }

    private val commandTimeout = Runnable { finishCommandSession("") }

    private fun finishCommandSession(command: String) {
        handler.removeCallbacks(commandTimeout)
        try { commandRecognizer?.cancel() } catch (_: Exception) {}
        try { commandRecognizer?.destroy() } catch (_: Exception) {}
        commandRecognizer = null
        if (command.isNotBlank() && MainActivity.isWakeReceiverActive()) {
            sendBroadcast(Intent(ACTION_WAKE_COMMAND).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
        } else if (command.isNotBlank()) {
            speak("I heard: $command. Open JARVIS to execute this command.")
        }
        commandMode = false
        handler.postDelayed({ startContinuousAudioCapture() }, 350)
    }

    private fun releaseAudioRecord() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            try { recorder.stop() } catch (_: Exception) {}
            try { recorder.release() } catch (_: Exception) {}
        }
    }

    private fun speak(message: String) {
        val engine = tts
        if (!ttsReady || engine == null) { pendingSpeech = message; return }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-kws")
    }

    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }

    override fun onDestroy() {
        active.set(false)
        isRunning = false
        commandMode = true
        handler.removeCallbacksAndMessages(null)
        releaseAudioRecord()
        try { commandRecognizer?.cancel(); commandRecognizer?.destroy() } catch (_: Exception) {}
        commandRecognizer = null
        try { stream?.release() } catch (_: Exception) {}
        stream = null
        try { spotter?.release() } catch (_: Exception) {}
        spotter = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

}
