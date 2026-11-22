package com.jarvis.personalassistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.IntentFilter;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static volatile boolean wakeReceiverActive = false;
    public static boolean isWakeReceiverActive() { return wakeReceiverActive; }

    private static final int CYAN = Color.rgb(0,229,255);
    private static final int BG = Color.rgb(5,11,22);
    private static final int PICK_MODEL = 901;
    private static final int PICK_IMAGE = 902;
    private static final int PICK_MODEL_COPY_BUFFER = 65536;
    private static final int REQUEST_NOTIFICATIONS = 4102;
    private static final int REQUEST_CAMERA_TORCH = 4104;
    private String localModelPath = "";
    private TextView status, conversation, dashboard;
    private JarvisOrbView orbView;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SharedPreferences memory;
    private SecureStore secureStore;
    private JarvisMemoryStore longTermMemory;
    private JarvisToolRegistry agentToolRegistry;
    private JarvisAgentEngine.RunHandle activeAgentRun;
    private boolean ttsReady = false;
    private boolean commandRetryUsed = false;
    private boolean serviceRetryUsed = false;
    private final Handler voiceHandler = new Handler(Looper.getMainLooper());
    private EditText commandInput;
    private String lastResponse = "I am ready, Pratik Boss.";
    private android.content.BroadcastReceiver wakeReceiver;
    private boolean wakeReceiverRegistered = false;
    private String pendingSpeech = null;

    private void startAlwaysOnWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        Intent service = new Intent(this, WakeWordService.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            if (status != null) status.setText("● JARVIS WAKE SERVICE STARTING");
        } catch (Exception e) {
            if (status != null) status.setText("● WAKE SERVICE BLOCKED - OPEN APP AGAIN");
        }
    }

    private void registerWakeReceiver() {
        if (wakeReceiverRegistered) return;
        wakeReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String command = intent.getStringExtra(WakeWordService.EXTRA_COMMAND);
                if (command == null || command.trim().isEmpty()) {
                    if (status != null) status.setText("● JARVIS ACTIVATED");
                    if (orbView != null) orbView.setState("LISTENING");
                    // Wake acknowledgement is spoken by the foreground service to avoid duplicates.
                } else {
                    if (status != null) status.setText("● COMMAND RECEIVED");
                    if (orbView != null) orbView.setState("THINKING");
                    handle(command);
                }
            }
        };
        IntentFilter filter = new IntentFilter(WakeWordService.ACTION_WAKE_COMMAND);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(wakeReceiver, filter);
        wakeReceiverRegistered = true;
        wakeReceiverActive = true;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.rgb(5, 7, 14));
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            android.view.WindowManager.LayoutParams displayParams = getWindow().getAttributes();
            displayParams.preferredRefreshRate = 90.0f; // Request 90 Hz when the device/display supports it.
            getWindow().setAttributes(displayParams);
        }
        memory = getSharedPreferences("jarvis_memory", MODE_PRIVATE);
        secureStore = new SecureStore(this);
        agentToolRegistry = new JarvisToolRegistry();
        new JarvisAndroidToolAdapter(this).registerInto(agentToolRegistry);
        longTermMemory = new JarvisMemoryStore(this);
        longTermMemory.expireTransientMemories();
        longTermMemory.indexMissingLocalVectors(500);
        longTermMemory.consolidateMemories();
        lastResponse = memory.getString("last_response", lastResponse);
        localModelPath = memory.getString("local_model_path", "");
        if (!localModelPath.isEmpty() && !new File(localModelPath).exists()) localModelPath = "";
        buildUi();
        if(memory.getBoolean("memory_cloud_embeddings",false)) indexSemanticMemoryIfEnabled(300);
        runAiMemoryReflectionIfEnabled();
        registerWakeReceiver();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded();
            startAlwaysOnWakeService();
        } else ensureAudioPermission();
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                int language = tts.setLanguage(new Locale("en", "IN"));
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts.setLanguage(new Locale("hi", "IN"));
                // Pitch below 1.0 gives a deeper sound when supported by the installed TTS engine.
                tts.setPitch(0.78f);
                tts.setSpeechRate(0.88f);
                ttsReady = true;
                if (pendingSpeech != null) { String queued = pendingSpeech; pendingSpeech = null; speak(queued); }
            }
        });
        refreshDashboard();
        show("JARVIS CORE", "V9 foundation online. Voice, memory and command modules ready.");
        speak("JARVIS systems online. At your service, Pratik Boss.");
    }

    private android.graphics.drawable.GradientDrawable panel(int fill, int stroke, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        d.setStroke(Math.max(1, (int)getResources().getDisplayMetrics().density), stroke);
        return d;
    }

    private void buildUi() {
        // Minimal iOS-inspired glass layout. Existing JARVIS actions and their order remain available.
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 22);
        root.setBackgroundColor(Color.rgb(5, 7, 14));

        TextView title = new TextView(this);
        title.setText("JARVIS");
        title.setTextColor(Color.rgb(238, 243, 255));
        title.setTextSize(25);
        title.setLetterSpacing(0.12f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 42));

        status = new TextView(this);
        status.setText("● ONLINE  ·  VOICE STANDBY");
        status.setTextColor(Color.rgb(126, 220, 190));
        status.setTextSize(11);
        status.setLetterSpacing(0.08f);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, 28));

        orbView = new JarvisOrbView(this);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(-1, 184);
        orbParams.topMargin = 4;
        root.addView(orbView, orbParams);

        dashboard = new TextView(this);
        dashboard.setTextColor(Color.rgb(211, 220, 239));
        dashboard.setTextSize(11);
        dashboard.setLineSpacing(3f, 1.0f);
        dashboard.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        dashboard.setPadding(16, 13, 16, 13);
        dashboard.setBackground(panel(Color.rgb(17, 21, 34), Color.rgb(48, 57, 82), 20));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = 10;
        root.addView(dashboard, dp);

        TextView chatLabel = new TextView(this);
        chatLabel.setText("CONVERSATION");
        chatLabel.setTextColor(Color.rgb(153, 169, 197));
        chatLabel.setTextSize(10);
        chatLabel.setLetterSpacing(0.13f);
        chatLabel.setPadding(4, 16, 4, 7);
        root.addView(chatLabel);

        ScrollView conversationScroll = new ScrollView(this);
        conversationScroll.setFillViewport(false);
        conversationScroll.setBackground(panel(Color.rgb(13, 17, 29), Color.rgb(42, 50, 72), 20));
        conversationScroll.setClipToPadding(false);
        conversation = new TextView(this);
        conversation.setTextColor(Color.rgb(242, 245, 252));
        conversation.setTextSize(14);
        conversation.setLineSpacing(4f, 1.0f);
        conversation.setPadding(16, 15, 16, 15);
        conversationScroll.addView(conversation);
        int conversationHeight = Math.max(150, Math.min(220,
                (int)(getResources().getDisplayMetrics().heightPixels * 0.21f)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, conversationHeight);
        root.addView(conversationScroll, cp);

        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        typeRow.setPadding(12, 2, 6, 2);
        typeRow.setBackground(panel(Color.rgb(20, 25, 40), Color.rgb(67, 82, 117), 28));
        commandInput = new EditText(this);
        commandInput.setSingleLine(true);
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.rgb(139, 151, 177));
        commandInput.setHint("Message JARVIS...");
        commandInput.setTextSize(14);
        commandInput.setBackgroundColor(Color.TRANSPARENT);
        commandInput.setPadding(8, 0, 4, 0);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                submitTypedCommand();
                return true;
            }
            return false;
        });
        typeRow.addView(commandInput, new LinearLayout.LayoutParams(0, 52, 1));
        Button run = button("➤");
        typeRow.addView(run, new LinearLayout.LayoutParams(48, 46));
        run.setOnClickListener(v -> submitTypedCommand());
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, -2);
        trp.topMargin = 12;
        root.addView(typeRow, trp);

        Button listen = button("  MIC  ·  TALK TO JARVIS  ");
        LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(-1, 48);
        listenParams.topMargin = 9;
        root.addView(listen, listenParams);
        listen.setOnClickListener(v -> listen());

        TextView toolsLabel = new TextView(this);
        toolsLabel.setText("QUICK ACTIONS");
        toolsLabel.setTextColor(Color.rgb(153, 169, 197));
        toolsLabel.setTextSize(10);
        toolsLabel.setLetterSpacing(0.13f);
        toolsLabel.setPadding(4, 15, 4, 6);
        root.addView(toolsLabel);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row1, "MEMORY", v -> memoryDialog());
        addSmall(row1, "TASKS", v -> taskDialog());
        addSmall(row1, "CUSTOM COMMAND", v -> customCommandDialog());
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row2, "CAMERA", v -> launch(new Intent("android.media.action.IMAGE_CAPTURE"), "Camera"));
        addSmall(row2, "MAPS", v -> open("https://maps.google.com"));
        addSmall(row2, "REPEAT", v -> speak(lastResponse));
        root.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row3, "AI BRAIN / MODEL", v -> aiBrainDialog());
        addSmall(row3, "ONLINE AI SETUP", v -> onlineAiDialog());
        root.addView(row3);

        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row4, "VISION / IMAGE", v -> pickImage());
        addSmall(row4, "WEB SEARCH", v -> searchWeb(commandInput == null ? "" : commandInput.getText().toString()));
        addSmall(row4, "SYSTEM STATUS", v -> systemReport());
        root.addView(row4);

        LinearLayout row5 = new LinearLayout(this);
        row5.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row5, "FLOATING ORB SETUP", v -> requestFloatingOrbPermission());
        root.addView(row5);

        page.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(page);
    }

    private void requestFloatingOrbPermission() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            respond("Floating orb overlay requires Android 6.0 or newer.");
            return;
        }
        if (Settings.canDrawOverlays(this)) {
            respond("Floating orb permission already enabled. Say Jarvis while the app is in background to test it.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("ALLOW JARVIS FLOATING ORB")
            .setMessage("Android requires your permission to show the small JARVIS orb over other apps after the wake word is detected. The orb appears only during a wake interaction and hides after the response. Allow 'Display over other apps' on the next screen.")
            .setPositiveButton("OPEN SETTINGS", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    respond("Overlay permission settings could not be opened.");
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void pickImage() {
        try {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,PICK_IMAGE);
        } catch (Exception e) {
            respond("Image picker available nahi hai.");
        }
    }

    private void analyzeSelectedImage(Uri uri) {
        String key = secureStore == null ? "" : secureStore.get("gemini_key");
        if (key.isEmpty()) {
            respond("Image analysis ke liye ONLINE AI SETUP mein Gemini API key add karein.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("SEND IMAGE TO GEMINI?")
            .setMessage("Selected image will be resized in memory and sent to your configured Google Gemini API for analysis. JARVIS will not save the image in its memory database. Continue only if you agree.")
            .setPositiveButton("ANALYZE",(d,w)->{
                status.setText("● VISION ANALYZING...");
                show("VISION","Analyzing selected image...");
                final String model = memory.getString("gemini_model","gemini-2.5-flash");
                new Thread(() -> {
                    String result = JarvisVisionClient.analyze(this,uri,key,model,
                        "Describe the image accurately. Identify visible objects, text, layout and relevant details. Do not guess hidden information.");
                    runOnUiThread(() -> respond(result));
                },"Jarvis-Vision").start();
            }).setNegativeButton("CANCEL",null).show();
    }

    private void searchWeb(String query) {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("What should I search on the web?");
            new AlertDialog.Builder(this).setTitle("JARVIS WEB SEARCH")
                .setView(input)
                .setPositiveButton("SEARCH",(d,w)->searchWeb(input.getText().toString()))
                .setNegativeButton("CANCEL",null).show();
            return;
        }
        try {
            Intent webSearch = new Intent(Intent.ACTION_WEB_SEARCH);
            webSearch.putExtra(android.app.SearchManager.QUERY,clean);
            if (webSearch.resolveActivity(getPackageManager()) != null) {
                startActivity(webSearch);
            } else {
                open("https://www.google.com/search?q="+Uri.encode(clean));
            }
            if (commandInput != null) commandInput.setText("");
            show("WEB SEARCH","Searching the web for: "+clean);
            speak("Searching the web for "+clean);
        } catch (Exception e) {
            open("https://www.google.com/search?q="+Uri.encode(clean));
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(245, 248, 255));
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackground(panel(Color.rgb(36, 49, 75), Color.rgb(92, 125, 177), 22));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }
    private void addSmall(LinearLayout row, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(10);
        b.setTextColor(Color.rgb(220, 229, 247));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(3, 2, 3, 2);
        b.setBackground(panel(Color.rgb(19, 24, 39), Color.rgb(51, 62, 88), 17));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 48, 1);
        p.setMargins(3, 4, 3, 0);
        row.addView(b, p);
    }
    private void refreshDashboard() {
        int battery=-1; try { Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); if(i!=null) battery=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1); } catch(Exception ignored) {}
        Runtime rt=Runtime.getRuntime(); long freeRam=(rt.maxMemory()-rt.totalMemory()+rt.freeMemory())/(1024*1024);
        StatFs fs=new StatFs(Environment.getDataDirectory().getPath()); long freeStorage=(fs.getAvailableBytes()/(1024*1024*1024));
        String brain = !localModelPath.isEmpty() && new File(localModelPath).exists() ? "LOCAL LLM MODEL READY" : "LOCAL LLM MODEL NOT LOADED";
        dashboard.setText("SYSTEM TELEMETRY\nBATTERY: "+(battery<0?"N/A":battery+"%")+"     FREE APP HEAP: "+freeRam+" MB\nAVAILABLE STORAGE: "+freeStorage+" GB\nAI MODE: HYBRID ROUTER\nOFFLINE BRAIN: "+brain+"\nONLINE BRAIN: "+((!secureStore.get("gemini_key").isEmpty() || !secureStore.get("groq_key").isEmpty())?"HYBRID READY":"KEYS NOT SET")+"\nWAKE LISTENING: "+(WakeWordService.isRunning?"ACTIVE":"NOT RUNNING"));
    }
    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void ensureAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 4101);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                show("NOTIFICATION", "JARVIS background service ke liye notification visible rakhne ke liye Settings > Apps > JARVIS > Notifications allow karein.");
            }
            return;
        }
        if (requestCode == REQUEST_CAMERA_TORCH) {
            boolean enabled = memory.getBoolean("pending_torch_enabled",false);
            memory.edit().remove("pending_torch_enabled").apply();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) setFlashlight(enabled);
            else respond("Camera permission denied; flashlight control unavailable.");
            return;
        }
        if (requestCode == 4101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                status.setText("● MICROPHONE READY");
                requestNotificationPermissionIfNeeded();
                startAlwaysOnWakeService();
                respond("Microphone permission granted. I am ready, Pratik Boss.");
            } else {
                status.setText("● MICROPHONE PERMISSION NEEDED");
                show("SYSTEM", "Android Settings mein JARVIS ke liye Microphone permission Allow karein.");
            }
        }
    }

    private void submitTypedCommand() {
        String text = commandInput == null ? "" : commandInput.getText().toString().trim();
        if (text.isEmpty()) { respond("Please type a command first."); return; }
        commandInput.setText("");
        handle(text);
    }

    private boolean hasMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true;
        ensureAudioPermission();
        respond("Microphone permission allow karo, Pratik Boss.");
        return false;
    }

    private void listen() {
        if (orbView != null) orbView.setState("LISTENING");
        if (!hasMicPermission()) return;
        stopService(new Intent(this, WakeWordService.class));
        commandRetryUsed = false;
        serviceRetryUsed = false;
        startRecognition(false, "en-IN");
    }

    // V10.4: always-on standby is owned only by WakeWordService (AudioRecord + offline KWS).
    // MainActivity uses SpeechRecognizer only for the explicit TAP TO SPEAK action.

    private void startRecognition(boolean waitingForWake, String language) {
        if (!hasMicPermission()) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.setText("● SPEECH SERVICE MISSING"); respond("Google Speech Services install ya enable karo."); return; }
        try {
            if (recognizer != null) { try { recognizer.cancel(); recognizer.destroy(); } catch(Exception ignored) {} }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p) { status.setText("● LISTENING... "+language); }
                public void onBeginningOfSpeech() { status.setText("● HEARING YOU..."); }
                public void onRmsChanged(float r) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { status.setText("● ANALYSING..."); }
                public void onError(int error) {
                    // Error 11 is ERROR_SERVER_DISCONNECTED. Recreate the service once,
                    // after the callback has returned, instead of immediately re-entering it.
                    if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED && !serviceRetryUsed) {
                        serviceRetryUsed = true;
                        status.setText("● SPEECH SERVICE RECONNECTING");
                        voiceHandler.postDelayed(() -> { if (!isFinishing()) startRecognition(false, language); }, 900);
                        return;
                    }
                    // Try Hindi only when English recognition heard no usable phrase.
                    if (!waitingForWake && !commandRetryUsed &&
                            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        commandRetryUsed = true;
                        status.setText("● RETRYING IN HINDI...");
                        voiceHandler.postDelayed(() -> startRecognition(false, "hi-IN"), 500);
                        return;
                    }
                    status.setText("● CORE ONLINE");
                    {
                        show("VOICE ERROR", speechError(error)+". Check internet and Google Speech Services.");
                        voiceHandler.postDelayed(() -> startAlwaysOnWakeService(), 1000);
                    }
                }
                public void onResults(Bundle b) {
                    ArrayList<String> matches=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(matches==null || matches.isEmpty()) { status.setText("● CORE ONLINE"); voiceHandler.postDelayed(() -> startAlwaysOnWakeService(), 700); return; }
                    String spoken=matches.get(0).trim();
                    serviceRetryUsed = false;
                    status.setText("● CORE ONLINE");
                    handle(spoken);
                    voiceHandler.postDelayed(() -> {
                        if (!isFinishing() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            startAlwaysOnWakeService();
                    }, 1400);
                }
                public void onPartialResults(Bundle b) {}
                public void onEvent(int t, Bundle p) {}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1800L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1000L);
            recognizer.startListening(intent);
        } catch(Exception e) {
            status.setText("● SPEECH START FAILED");
            show("VOICE ERROR", "Speech start failed: "+e.getClass().getSimpleName()+" - "+e.getMessage());
        }
    }

    private String speechError(int error) {
        switch(error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Speech client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "Speech not recognized";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Speech server error";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED: return "Speech service disconnected (Error 11)";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech detected";
            default: return "Speech error code " + error;
        }
    }

    private void handle(String raw) {
        if (orbView != null) orbView.setState("THINKING");
        boolean privateMode = memory.getBoolean("memory_private_mode", false);
        if (longTermMemory != null && !privateMode) longTermMemory.addConversation("Commander", raw);
        if (!privateMode) autoLearnMemory(raw);
        String q=raw.toLowerCase(Locale.ROOT); show("COMMANDER",raw); String answer;
        if (q.contains("time") || q.contains("samay") || q.contains("समय") || q.contains("kitne baje")) answer="Abhi " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()) + " baj rahe hain.";
        else if (q.contains("date") || q.contains("tareekh") || q.contains("तारीख") || q.contains("aaj ki date")) answer="Aaj " + new SimpleDateFormat("dd MMMM yyyy", new Locale("hi","IN")).format(new Date()) + " hai.";
        else if (q.contains("battery") || q.contains("बैटरी")) answer=batteryReport();
        else if (q.contains("flashlight") || q.contains("torch")) { setFlashlight(q.contains("on") || q.contains("enable") || q.contains("chalu")); return; }
        else if (q.contains("volume up") || q.contains("volume badhao")) { adjustVolume(1); return; }
        else if (q.contains("volume down") || q.contains("volume kam")) { adjustVolume(-1); return; }
        else if (q.contains("mute volume") || q.equals("mute")) { adjustVolume(0); return; }
        else if (q.contains("brightness") || q.contains("screen brightness")) { launch(new Intent(Settings.ACTION_DISPLAY_SETTINGS),"Display settings"); answer="Display settings khol raha hoon. Android permission ke bina brightness directly change nahi kar sakta."; }
        else if (q.contains("system status") || q.contains("system report") || q.contains("ram") || q.contains("storage")) { systemReport(); return; }
        else if (q.contains("wifi") || q.contains("wi-fi")) { launch(new Intent(Settings.ACTION_WIFI_SETTINGS),"Wi-Fi settings"); answer="Wi-Fi settings khol raha hoon."; }
        else if (q.contains("bluetooth")) { launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),"Bluetooth settings"); answer="Bluetooth settings khol raha hoon."; }
        else if (q.contains("settings") || q.contains("सेटिंग")) { launch(new Intent(Settings.ACTION_SETTINGS),"Settings"); answer="Phone settings khol raha hoon."; }
        else if (q.contains("youtube")) { open("https://www.youtube.com"); answer="YouTube khol raha hoon."; }
        else if (q.startsWith("web search ") || q.startsWith("search web ")) { runAgent(raw); return; }
        else if (q.startsWith("search ") || q.contains("google")) { searchWeb(raw.replaceFirst("(?i)^(web\\s+)?search\\s*","")); return; }
        else if (q.contains("camera") || q.contains("कैमरा")) { launch(new Intent("android.media.action.IMAGE_CAPTURE"),"Camera"); answer="Camera khol raha hoon."; }
        else if (q.contains("map") || q.contains("navigate") || q.contains("raasta")) { open("https://maps.google.com/?q="+Uri.encode(raw)); answer="Maps khol raha hoon. Destination confirm kar lena."; }
        else if (q.contains("translate") || q.contains("translation")) { open("https://translate.google.com"); answer="Translation service khol raha hoon."; }
        else if (q.contains("alarm")) { Intent a=new Intent(AlarmClock.ACTION_SET_ALARM); a.putExtra(AlarmClock.EXTRA_SKIP_UI,false); launch(a,"Clock"); answer="Alarm set karne ke liye Clock khol raha hoon."; }
        else if (q.startsWith("memory useful ")) { long id=parseLong(raw.replaceFirst("(?i)^memory useful\\s*","")); if(longTermMemory!=null)longTermMemory.recordMemoryFeedback(id,1,raw); answer="Memory usefulness feedback saved for #"+id+"."; }
        else if (q.startsWith("memory wrong ")) { long id=parseLong(raw.replaceFirst("(?i)^memory wrong\\s*","")); if(longTermMemory!=null)longTermMemory.recordMemoryFeedback(id,-1,raw); answer="Memory reliability feedback saved for #"+id+"; its retrieval priority was reduced."; }
        else if (q.startsWith("memory forget ")) { long id=parseLong(raw.replaceFirst("(?i)^memory forget\\s*","")); answer=longTermMemory!=null&&longTermMemory.deleteMemory(id)?"Memory #"+id+" permanently deleted with its indexes.":"Memory ID not found."; }
        else if (q.equals("memory ai learning on")) { if(memory.getBoolean("memory_ai_learning",false))answer="AI memory learning already ON.";else{confirmAiMemoryLearning();return;} }
        else if (q.equals("memory ai learning off")) { memory.edit().putBoolean("memory_ai_learning",false).apply(); answer="AI memory extraction/reflection OFF. Local memory stays enabled."; }
        else if (q.startsWith("memory at ")) { String term=raw.substring("memory at ".length()).trim(); JarvisTimeQuery.Window w=JarvisTimeQuery.parse(term); answer=longTermMemory==null?"Memory unavailable.":w==null?"Date/time period not recognized. Try: memory at 2024 job":longTermMemory.searchMemoriesDuring(w.query,30,w.startAt,w.endAt); }
        else if (q.equals("memory profile") || q.equals("show my profile")) { answer=longTermMemory==null?"Memory unavailable.":longTermMemory.getProfileSummaryText(); }
        else if (q.startsWith("memory pin ")) { long id=parseLong(raw.replaceFirst("(?i)^memory pin\\s*","")); answer=longTermMemory!=null && longTermMemory.setMemoryPinned(id,true)?"Memory #"+id+" pinned as core memory.":"Memory ID nahi mili."; }
        else if (q.startsWith("memory unpin ")) { long id=parseLong(raw.replaceFirst("(?i)^memory unpin\\s*","")); answer=longTermMemory!=null && longTermMemory.setMemoryPinned(id,false)?"Memory #"+id+" unpinned.":"Memory ID nahi mili."; }
        else if (q.startsWith("memory history")) { String term=raw.replaceFirst("(?i)^memory history\\s*","").trim(); answer=longTermMemory==null?"Memory unavailable.":longTermMemory.getMemoryHistoryText(term); }
        else if (q.equals("memory stats") || q.equals("memory status")) { answer=longTermMemory==null?"Memory unavailable.":longTermMemory.getMemoryStatsText(); }
        else if (q.equals("memory consolidate") || q.equals("memory reflect") || q.equals("memory dreaming")) { int made=longTermMemory==null?0:longTermMemory.consolidateMemories(); runAiMemoryReflectionIfEnabled(); answer="Local consolidation complete. "+made+" digest(s) created. AI reflection runs in the background only when opted in."; }
        else if (q.equals("memory embeddings on")) { memory.edit().putBoolean("memory_cloud_embeddings",true).apply(); indexSemanticMemoryIfEnabled(300); answer="Gemini semantic memory ON. Saved memory snippets and future search queries may be sent to Google for embedding. Local recall remains as fallback."; }
        else if (q.equals("memory embeddings off")) { memory.edit().putBoolean("memory_cloud_embeddings",false).apply(); answer="Cloud semantic embeddings OFF. JARVIS will use on-device lexical, temporal and graph recall."; }
        else if (q.equals("memory embeddings delete")) { if(longTermMemory!=null)longTermMemory.clearEmbeddings(); answer="Stored semantic vector index deleted. Your original memories are unchanged."; }
        else if (q.equals("memory privacy on") || q.equals("private mode on") || q.equals("memory band")) { memory.edit().putBoolean("memory_private_mode",true).apply(); answer="Privacy mode ON. Conversation history aur automatic learning save nahi honge."; }
        else if (q.equals("memory privacy off") || q.equals("private mode off") || q.equals("memory chalu")) { memory.edit().putBoolean("memory_private_mode",false).apply(); answer="Memory mode ON. Local history aur automatic learning resume."; }
        else if (q.startsWith("memory delete ")) { long id=parseMemoryId(raw); answer=longTermMemory!=null && longTermMemory.deleteMemory(id)?"Memory #"+id+" delete ho gayi.":"Memory ID nahi mili."; }
        else if (q.startsWith("memory edit ")) { String data=raw.substring("memory edit ".length()).trim(); int bar=data.indexOf('|'); if(bar>0){long id=parseLong(data.substring(0,bar).trim()); answer=longTermMemory!=null && longTermMemory.updateMemory(id,"personal",data.substring(bar+1).trim())?"Memory #"+id+" update ho gayi.":"Memory update nahi hui.";}else answer="Format: memory edit ID | naya text"; }
        else if (q.startsWith("memory search") || q.startsWith("search memory") || q.startsWith("find memory")) { String term=raw.replaceFirst("(?i)^(memory search|search memory|find memory)\\s*", "").trim(); answer=longTermMemory == null ? "Memory database unavailable." : longTermMemory.searchMemories(term, 30); }
        else if (q.equals("memory graph") || q.equals("show knowledge graph") || q.startsWith("memory related ")) { String term=q.startsWith("memory related ")?raw.substring("memory related ".length()).trim():""; answer=longTermMemory==null?"Knowledge graph unavailable.":longTermMemory.getKnowledgeGraphText(term); }
        else if (q.startsWith("remind me at ")) { answer=scheduleReminder(raw); }
        else if (q.startsWith("task add ") || q.startsWith("reminder add ") || q.startsWith("yaad dilana ")) { String task=raw.replaceFirst("(?i)^(task add|reminder add|yaad dilana)\\s*", "").trim(); long id=longTermMemory==null?-1:longTermMemory.addTask(task,0); answer=id>0?"Task/reminder #"+id+" local list mein save ho gaya. Due time set karne ke liye TASKS screen kholo.":"Task save nahi hua."; }
        else if (q.equals("tasks") || q.equals("task list") || q.equals("reminders") || q.equals("show tasks")) { answer=longTermMemory==null?"Task store unavailable.":longTermMemory.getTasksText(false); }
        else if (q.startsWith("task done ") || q.startsWith("task complete ")) { long id=parseLong(raw.replaceFirst("(?i)^task (done|complete)\\s*","").trim()); boolean done=longTermMemory!=null && longTermMemory.completeTask(id); if(done)JarvisReminderScheduler.cancel(this,id); answer=done?"Task #"+id+" complete mark kar diya.":"Task ID nahi mili."; }
        else if (q.startsWith("task delete ")) { long id=parseLong(raw.replaceFirst("(?i)^task delete\\s*","").trim()); boolean removed=longTermMemory!=null && longTermMemory.deleteTask(id); if(removed)JarvisReminderScheduler.cancel(this,id); answer=removed?"Task #"+id+" delete kar diya.":"Task ID nahi mili."; }
        else if (q.startsWith("memory temp ")) { java.util.regex.Matcher tm=java.util.regex.Pattern.compile("(?i)^memory temp\\s+(\\d+)\\s*(m|min|h|hour|d|day)s?\\s*\\|\\s*(.+)$").matcher(raw.trim()); if(!tm.matches())answer="Format: memory temp 2h | meeting notes"; else {long n=parseLong(tm.group(1));String unit=tm.group(2).toLowerCase(Locale.ROOT);long factor=unit.startsWith("m")?60000L:unit.startsWith("h")?3600000L:86400000L;long id=longTermMemory==null?-1:longTermMemory.addTransientMemory(tm.group(3),n*factor);answer=id>0?"Temporary memory #"+id+" saved; it will expire automatically.":"Temporary memory could not be saved."; } }
        else if (q.contains("remember") || q.contains("yaad rakh") || q.contains("याद रख")) { String note=raw.replaceAll("(?i)remember|yaad rakh|yaad rakhna", "").trim(); answer=saveNote(note)?"Memory mein save kar diya: "+note:"Memory save nahi hui. Text empty, sensitive ya invalid ho sakta hai."; }
        else if (q.contains("what did i ask you to remember") || q.contains("maine kya yaad") || q.equals("memory") || q.contains("show memory") || q.contains("meri memory")) { memoryDialog(); return; }
        else if (q.startsWith("mission plan") || q.contains("plan my task") || q.contains("mission briefing")) answer="Mission protocol: objective define karein, task ko steps mein divide karein, risks aur required resources check karein, phir execution se pehle confirmation lein. Main abhi local planning template use kar raha hoon.";
        else if (runCustomCommand(raw)) return;
        else if (q.contains("hello") || q.contains("namaste") || q.contains("हेलो")) answer="Hello Pratik Boss. JARVIS systems are ready.";
        else {
            if (isAgentActionIntent(q)) {
                runAgent(raw);
                return;
            }
            String geminiKey = secureStore.get("gemini_key");
            String groqKey = secureStore.get("groq_key");
            if (!geminiKey.isEmpty() || !groqKey.isEmpty() || (!localModelPath.isEmpty() && new File(localModelPath).isFile())) {
                status.setText("● HYBRID AI ROUTING...");
                final boolean privateContext = memory.getBoolean("memory_private_mode", false);
                final boolean useCloudVector = !privateContext && memory.getBoolean("memory_cloud_embeddings", false) && !geminiKey.isEmpty();
                new Thread(() -> {
                    float[] queryVector = useCloudVector ? JarvisEmbeddingClient.embed(geminiKey, raw, true) : null;
                    final String memoryContext = longTermMemory == null || privateContext ? "No saved memory context. Respect privacy." :
                        (queryVector == null ? longTermMemory.getMemoryContext(raw, 10) : longTermMemory.getMemoryContext(raw, 10, queryVector));
                    final String legacyNote = privateContext ? "none" : memory.getString("note", "none");
                    final String prompt = memoryContext + "\\nLegacy saved note: " + legacyNote +
                        "\\nPrevious response: " + lastResponse + "\\nCommander says: " + raw;
                    JarvisHybridBrain.ask(geminiKey, memory.getString("gemini_model", "gemini-2.5-flash"),
                        groqKey, memory.getString("groq_model", "openai/gpt-oss-120b"),
                        localModelPath, prompt, raw, result -> runOnUiThread(() -> respond(result)));
                }, "Jarvis-HybridBrain").start();
                return;
            }
            answer="AI brain configure nahi hai, Pratik Boss. ONLINE AI SETUP mein Gemini ya Groq key add karein, ya AI BRAIN / MODEL mein offline model load karein.";
        }
        respond(answer);
    }
    private boolean isAgentActionIntent(String q) {
        return JarvisActionIntentRouter.isExplicitAction(q);
    }

    private void runAgent(String objective) {
        String geminiKey = secureStore.get("gemini_key");
        String groqKey = secureStore.get("groq_key");
        if (geminiKey.isEmpty() && groqKey.isEmpty()) {
            respond("Android agent actions ke liye Gemini ya Groq API key configure karo. Local chat brain se direct phone actions enabled nahi hain.");
            return;
        }
        if (activeAgentRun != null && !activeAgentRun.isCancelled()) {
            respond("JARVIS is already handling an agent task. Pehle current task complete hone do.");
            return;
        }
        status.setText("● AGENT PLANNING...");
        JarvisAgentEngine engine = new JarvisAgentEngine(
            new JarvisStructuredPlanner(agentToolRegistry, geminiKey,
                memory.getString("gemini_model", "gemini-2.5-flash"),
                groqKey, memory.getString("groq_model", "openai/gpt-oss-120b")),
            agentToolRegistry, this::requestAgentApproval);
        activeAgentRun = engine.submit(objective, new JarvisAgentEngine.Listener() {
            @Override public void onState(JarvisAgentEngine.State state, String detail) {
                runOnUiThread(() -> {
                    if (status == null || isFinishing()) return;
                    if (state == JarvisAgentEngine.State.AWAITING_APPROVAL) status.setText("● WAITING FOR YOUR APPROVAL");
                    else if (state == JarvisAgentEngine.State.RUNNING) status.setText("● EXECUTING APPROVED ACTION");
                    else if (state == JarvisAgentEngine.State.PLANNING) status.setText("● AI ACTION PLANNING...");
                    else if (state == JarvisAgentEngine.State.VERIFYING) status.setText("● VERIFYING ACTION...");
                });
            }
            @Override public void onComplete(JarvisAgentEngine.AgentReport report) {
                runOnUiThread(() -> {
                    activeAgentRun = null;
                    if (isFinishing()) return;
                    if (report.finalState == JarvisAgentEngine.State.COMPLETED && report.steps.isEmpty()) {
                        respond("Is request ke liye koi supported Android action identify nahi hua. App ka exact naam batao, ya normal question ki tarah pucho.");
                        return;
                    }
                    StringBuilder message = new StringBuilder();
                    message.append(report.finalState == JarvisAgentEngine.State.COMPLETED
                        ? "Agent task complete. " : report.finalState == JarvisAgentEngine.State.CANCELLED
                        ? "Agent task cancelled or approval denied. " : "Agent task stopped safely. ");
                    for (JarvisAgentEngine.StepReport step : report.steps) {
                        message.append("\n").append(step.toolName).append(": ")
                            .append(step.output);
                        if (step.success && step.verified) message.append(" ✓");
                    }
                    respond(message.toString().trim());
                });
            }
        });
    }

    private boolean requestAgentApproval(JarvisAgentEngine.AgentStep step) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicBoolean resolved = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean allowed = new java.util.concurrent.atomic.AtomicBoolean(false);
        final AlertDialog[] dialog = new AlertDialog[1];
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || !hasWindowFocus()) {
                resolved.set(true);
                latch.countDown();
                return;
            }
            StringBuilder details = new StringBuilder();
            details.append("Action: ").append(step.toolName).append("\n");
            if (!step.purpose.isEmpty()) details.append("Reason: ").append(step.purpose).append("\n");
            if (!step.arguments.isEmpty()) details.append("Arguments: ").append(step.arguments);
            details.append("\n\nJARVIS will perform this action only once if you allow it.");
            dialog[0] = new AlertDialog.Builder(MainActivity.this)
                .setTitle("JARVIS APPROVAL REQUIRED")
                .setMessage(details.toString())
                .setPositiveButton("ALLOW ONCE", (d, which) -> {
                    if (resolved.compareAndSet(false, true)) {
                        allowed.set(true);
                        latch.countDown();
                    }
                })
                .setNegativeButton("DENY", (d, which) -> {
                    if (resolved.compareAndSet(false, true)) latch.countDown();
                })
                .setOnCancelListener(d -> {
                    if (resolved.compareAndSet(false, true)) latch.countDown();
                })
                .create();
            dialog[0].show();
        });
        try {
            if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                if (resolved.compareAndSet(false, true)) runOnUiThread(() -> {
                    if (dialog[0] != null && dialog[0].isShowing()) dialog[0].dismiss();
                });
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (resolved.compareAndSet(false, true)) runOnUiThread(() -> {
                if (dialog[0] != null && dialog[0].isShowing()) dialog[0].dismiss();
            });
            return false;
        }
        return allowed.get();
    }

    private boolean runCustomCommand(String raw) {
        Map<String,?> all=memory.getAll();
        for(String key:all.keySet()) if(key.startsWith("custom_")) {
            String value=memory.getString(key,""); String[] pair=value.split("\\|",2);
            if(pair.length==2 && raw.equalsIgnoreCase(pair[0].trim())) { String action=pair[1].trim();
                if(action.startsWith("https://")||action.startsWith("http://")) open(action);
                respond("Custom command executing: "+action); return true;
            }
        } return false;
    }
    private boolean saveNote(String note) {
        if (note == null || note.trim().isEmpty() || longTermMemory == null) return false;
        long id = longTermMemory.saveMemory("personal", note.trim());
        if (id <= 0) return false;
        // Keep the legacy backup only after the secure memory store accepted the content.
        memory.edit().putString("note",note.trim()).apply();
        indexSemanticMemoryIfEnabled(1);
        if(memory.getBoolean("memory_ai_learning",false))
            JarvisMemoryAiWorker.learnAsync(longTermMemory,secureStore.get("gemini_key"),
                memory.getString("gemini_model","gemini-2.5-flash"),secureStore.get("groq_key"),
                memory.getString("groq_model","openai/gpt-oss-120b"),note.trim());
        return true;
    }
    private void setFlashlight(boolean enabled) {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            respond("Is Android version par torch API supported nahi hai.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            memory.edit().putBoolean("pending_torch_enabled",enabled).apply();
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_TORCH);
            respond("Flashlight control ke liye Camera permission allow karein.");
            return;
        }
        try {
            android.hardware.camera2.CameraManager manager =
                (android.hardware.camera2.CameraManager)getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) { respond("Camera service unavailable."); return; }
            String selected = null;
            for (String id : manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics info = manager.getCameraCharacteristics(id);
                Boolean flash = info.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { selected=id; break; }
            }
            if (selected == null) { respond("Is phone mein flashlight hardware nahi mila."); return; }
            manager.setTorchMode(selected,enabled);
            respond(enabled ? "Flashlight ON." : "Flashlight OFF.");
        } catch (Exception e) {
            respond("Flashlight control failed: "+e.getClass().getSimpleName());
        }
    }

    private void adjustVolume(int direction) {
        try {
            android.media.AudioManager audio = (android.media.AudioManager)getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) { respond("Audio service unavailable."); return; }
            if (direction > 0) audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_RAISE,android.media.AudioManager.FLAG_SHOW_UI);
            else if (direction < 0) audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_LOWER,android.media.AudioManager.FLAG_SHOW_UI);
            else if (android.os.Build.VERSION.SDK_INT >= 23) audio.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.ADJUST_MUTE,
                android.media.AudioManager.FLAG_SHOW_UI);
            respond(direction>0 ? "Media volume increased." : direction<0 ? "Media volume decreased." : "Media volume muted.");
        } catch (Exception e) { respond("Volume control failed: "+e.getMessage()); }
    }

    private String batteryReport() { try { Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); int level=b==null?-1:b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1); return level<0?"Battery status nahi mil raha.":"Battery "+level+" percent hai."; } catch(Exception e){return "Battery status unavailable.";} }
    private void systemReport() { refreshDashboard(); respond("System report. "+batteryReport()+" Available storage aur memory dashboard par dikhayi gayi hai."); }
    private void memoryDialog() {
        String[] options={"Add memory","Search memories","Edit memory by ID","Delete memory by ID","View conversation history","Clear conversation history","Clear all saved memories","Toggle privacy mode","View knowledge graph","Tasks & reminders","Memory statistics","Consolidate & reflect","View pinned core memories","Toggle Gemini semantic vectors","Delete semantic vector index","Toggle AI extraction & reflection","View structured profile","Memory temporary note"};
        new AlertDialog.Builder(this).setTitle("MEMORY CENTER • "+(longTermMemory==null?0:longTermMemory.getMemoryCount())+" SAVED")
            .setItems(options,(d,which)->{
                if(which==0){EditText in=new EditText(this);in.setHint("What should JARVIS remember?");in.setMinLines(2);new AlertDialog.Builder(this).setTitle("ADD MEMORY").setView(in).setPositiveButton("SAVE",(x,y)->respond(saveNote(in.getText().toString())?"Memory saved locally.":"Memory save nahi hui. Text empty, sensitive ya invalid ho sakta hai.")).setNegativeButton("CANCEL",null).show();}
                else if(which==1){EditText in=new EditText(this);in.setHint("Search word or phrase");new AlertDialog.Builder(this).setTitle("SEARCH MEMORY").setView(in).setPositiveButton("SEARCH",(x,y)->show("MEMORY RESULTS",longTermMemory==null?"Unavailable":longTermMemory.searchMemories(in.getText().toString(),50))).setNegativeButton("CANCEL",null).show();}
                else if(which==2){EditText in=new EditText(this);in.setHint("ID | replacement text");new AlertDialog.Builder(this).setTitle("EDIT MEMORY").setMessage("Format: 4 | My favourite colour is blue").setView(in).setPositiveButton("UPDATE",(x,y)->{String[] p=in.getText().toString().split("\\|",2);if(p.length==2&&longTermMemory!=null&&longTermMemory.updateMemory(parseLong(p[0].trim()),"personal",p[1].trim()))respond("Memory updated.");else respond("Format or ID invalid.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==3){EditText in=new EditText(this);in.setHint("Memory ID");new AlertDialog.Builder(this).setTitle("DELETE MEMORY").setView(in).setPositiveButton("DELETE",(x,y)->respond(longTermMemory!=null&&longTermMemory.deleteMemory(parseLong(in.getText().toString()))?"Memory deleted.":"Memory ID not found.")).setNegativeButton("CANCEL",null).show();}
                else if(which==4){show("CONVERSATION HISTORY",longTermMemory==null?"Unavailable":longTermMemory.getRecentConversation(100));}
                else if(which==5){new AlertDialog.Builder(this).setTitle("CLEAR HISTORY?").setMessage("Saved memories will remain.").setPositiveButton("CLEAR",(x,y)->{if(longTermMemory!=null)longTermMemory.clearConversationHistory();respond("Conversation history cleared.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==6){new AlertDialog.Builder(this).setTitle("DELETE ALL MEMORIES?").setMessage("Saved facts and preferences will be permanently deleted.").setPositiveButton("DELETE",(x,y)->{if(longTermMemory!=null)longTermMemory.clearMemories();memory.edit().remove("note").apply();respond("All memories cleared.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==7){boolean on=!memory.getBoolean("memory_private_mode",false);memory.edit().putBoolean("memory_private_mode",on).apply();respond(on?"Privacy mode ON. History and auto-learning paused.":"Privacy mode OFF. Memory resumed.");}
                else if(which==8){show("KNOWLEDGE GRAPH",longTermMemory==null?"Unavailable":longTermMemory.getKnowledgeGraphText(""));}
                else if(which==9) taskDialog();
                else if(which==10) show("MEMORY HEALTH",longTermMemory==null?"Unavailable":longTermMemory.getMemoryStatsText());
                else if(which==11){int made=longTermMemory==null?0:longTermMemory.consolidateMemories();respond("Memory reflection complete. "+made+" session digests created; facts preserved.");}
                else if(which==12) show("PINNED CORE MEMORIES",longTermMemory==null?"Unavailable":longTermMemory.getPinnedMemoriesText(50));
                else if(which==13){
                    boolean enabled=memory.getBoolean("memory_cloud_embeddings",false);
                    if(enabled){memory.edit().putBoolean("memory_cloud_embeddings",false).apply();respond("Gemini semantic vectors OFF. Local memory recall remains active.");}
                    else new AlertDialog.Builder(this).setTitle("ENABLE GEMINI SEMANTIC MEMORY?")
                        .setMessage("This optional feature sends saved memory snippets and future memory-search queries to Google Gemini Embeddings. Your memory database remains on this phone, and local search stays available as fallback. Enable only if you agree.")
                        .setPositiveButton("ENABLE",(x,y)->{memory.edit().putBoolean("memory_cloud_embeddings",true).apply();indexSemanticMemoryIfEnabled(300);respond("Gemini semantic vectors enabled. Existing memories will be indexed in the background.");})
                        .setNegativeButton("CANCEL",null).show();
                }
                else if(which==14){if(longTermMemory!=null)longTermMemory.clearEmbeddings();respond("Semantic vector index deleted. Saved facts remain.");}
                else if(which==15) confirmAiMemoryLearning();
                else if(which==16) show("STRUCTURED PROFILE",longTermMemory==null?"Unavailable":longTermMemory.getProfileSummaryText());
                else if(which==17){EditText temp=new EditText(this);temp.setHint("TTL command: 2h | short note");new AlertDialog.Builder(this).setTitle("TEMPORARY MEMORY").setMessage("Example: 30m | call the courier").setView(temp).setPositiveButton("SAVE",(x,y)->handle("memory temp "+temp.getText().toString())).setNegativeButton("CANCEL",null).show();}
            }).setNegativeButton("CLOSE",null).show();
    }
    private String scheduleReminder(String raw) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)remind me at (\\d{1,2}):(\\d{2})\\s*(am|pm)?\\s+to\\s+(.+)").matcher(raw.trim());
        if(!m.matches()) return "Format bolo: remind me at 7:30 pm to call home";
        int hour=Integer.parseInt(m.group(1)), minute=Integer.parseInt(m.group(2));
        String meridiem=m.group(3), title=m.group(4).trim();
        if(minute>59 || hour>(meridiem==null?23:12) || hour<0 || (meridiem!=null&&hour==0)) return "Time sahi format mein nahi hai.";
        if(meridiem!=null){hour=hour%12;if(meridiem.equalsIgnoreCase("pm"))hour+=12;}
        java.util.Calendar when=java.util.Calendar.getInstance();
        when.set(java.util.Calendar.HOUR_OF_DAY,hour);when.set(java.util.Calendar.MINUTE,minute);
        when.set(java.util.Calendar.SECOND,0);when.set(java.util.Calendar.MILLISECOND,0);
        if(when.getTimeInMillis()<=System.currentTimeMillis())when.add(java.util.Calendar.DAY_OF_YEAR,1);
        long dueAt=when.getTimeInMillis();
        long id=longTermMemory==null?-1:longTermMemory.addTask(title,dueAt);
        if(id<=0)return "Reminder save nahi ho paya.";
        if(!JarvisReminderScheduler.schedule(this,id,title,dueAt))
            return "Reminder list mein save hua, lekin Android Alarm service schedule nahi kar payi.";
        return "JARVIS reminder schedule ho gaya: "+title+" — "+new SimpleDateFormat("dd MMM, hh:mm a",Locale.getDefault()).format(new Date(dueAt))+". Notification permission ON rakhein.";
    }
    private void taskDialog() {
        EditText in=new EditText(this);in.setHint("New task / reminder");in.setMinLines(2);
        String tasks=longTermMemory==null?"Unavailable":longTermMemory.getTasksText(true);
        new AlertDialog.Builder(this).setTitle("TASKS & REMINDERS").setMessage(tasks).setView(in)
            .setPositiveButton("ADD TASK",(d,w)->{long id=longTermMemory==null?-1:longTermMemory.addTask(in.getText().toString(),0);respond(id>0?"Task saved as #"+id+". Say task done "+id+" when completed.":"Enter a task first.");})
            .setNeutralButton("MARK DONE",(d,w)->{EditText id=new EditText(this);id.setHint("Task ID");new AlertDialog.Builder(this).setTitle("COMPLETE TASK").setView(id).setPositiveButton("DONE",(x,y)->{long taskId=parseLong(id.getText().toString()); boolean done=longTermMemory!=null&&longTermMemory.completeTask(taskId); if(done)JarvisReminderScheduler.cancel(this,taskId); respond(done?"Task completed.":"Task ID not found.");}).setNegativeButton("CANCEL",null).show();})
            .setNegativeButton("CLOSE",null).show();
    }
    private long parseLong(String s){try{return Long.parseLong(s.trim());}catch(Exception e){return -1;}}
    private long parseMemoryId(String s){return parseLong(s.replaceFirst("(?i)^memory delete\\s*","").trim());}
    private void autoLearnMemory(String raw){
        if(longTermMemory==null||raw==null||memory.getBoolean("memory_private_mode",false))return;
        if(JarvisMemoryExtractor.learn(longTermMemory,raw)) indexSemanticMemoryIfEnabled(1);
        if(memory.getBoolean("memory_ai_learning",false))
            JarvisMemoryAiWorker.learnAsync(longTermMemory,secureStore.get("gemini_key"),
                memory.getString("gemini_model","gemini-2.5-flash"),secureStore.get("groq_key"),
                memory.getString("groq_model","openai/gpt-oss-120b"),raw);
    }
    private void runAiMemoryReflectionIfEnabled(){
        if(longTermMemory==null||memory.getBoolean("memory_private_mode",false)||!memory.getBoolean("memory_ai_learning",false))return;
        JarvisMemoryAiWorker.reflectPendingAsync(longTermMemory,secureStore.get("gemini_key"),
            memory.getString("gemini_model","gemini-2.5-flash"),secureStore.get("groq_key"),
            memory.getString("groq_model","openai/gpt-oss-120b"));
    }
    private String buildOfflineMemoryPrompt(String raw){
        if(memory==null||memory.getBoolean("memory_private_mode",false)||longTermMemory==null)return raw;
        return longTermMemory.getMemoryContext(raw,10)+"\n\nCommander says: "+raw;
    }
    private void confirmAiMemoryLearning(){
        boolean enabled=memory.getBoolean("memory_ai_learning",false);
        if(enabled){
            memory.edit().putBoolean("memory_ai_learning",false).apply();
            respond("AI memory extraction and cloud reflection OFF. Local learning remains available.");
            return;
        }
        if(secureStore.get("gemini_key").isEmpty()&&secureStore.get("groq_key").isEmpty()){
            respond("Pehle ONLINE AI SETUP mein Gemini ya Groq API key add karo.");return;
        }
        new AlertDialog.Builder(this).setTitle("ENABLE AI MEMORY LEARNING?")
            .setMessage("When enabled, new user messages and eligible past conversation sessions will be sent to your configured Gemini/Groq provider to extract durable facts and create summaries. Sensitive categories are filtered, but cloud processing is still involved. Local memory remains available. Enable only if you agree.")
            .setPositiveButton("ENABLE",(d,w)->{
                memory.edit().putBoolean("memory_ai_learning",true).apply();
                runAiMemoryReflectionIfEnabled();
                respond("AI memory learning ON. Local learning remains active too.");
            }).setNegativeButton("CANCEL",null).show();
    }
    private void indexSemanticMemoryIfEnabled(int maximum){
        if(memory!=null && memory.getBoolean("memory_cloud_embeddings",false) && secureStore!=null)
            JarvisEmbeddingWorker.indexPending(longTermMemory,secureStore,maximum);
    }
    private void customCommandDialog() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,8,24,8);
        EditText phrase=new EditText(this); phrase.setHint("Voice phrase, e.g. open my website");
        EditText action=new EditText(this); action.setHint("Action: website URL (https://...)"); box.addView(phrase); box.addView(action);
        new AlertDialog.Builder(this).setTitle("ADD CUSTOM COMMAND").setMessage("This first version supports opening a URL.").setView(box).setPositiveButton("SAVE",(d,w)->{
            String p=phrase.getText().toString().trim(), a=action.getText().toString().trim();
            if(!p.isEmpty()&&!a.isEmpty()&&a.startsWith("https://")){String id="custom_"+System.currentTimeMillis();memory.edit().putString(id,p+"|"+a).apply();respond("Custom command saved.");} else respond("Valid phrase aur https website URL enter karein.");
        }).setNegativeButton("CANCEL",null).show();
    }

    private void aiBrainDialog() {
        String state = localModelPath.isEmpty() ? "No offline model loaded." : "Offline model: " + new File(localModelPath).getName();
        new AlertDialog.Builder(this).setTitle("JARVIS OFFLINE AI BRAIN")
            .setMessage(state + "\n\nSelect a compatible .litertlm model file. Model files are large and are not included in the APK.")
            .setPositiveButton("SELECT MODEL", (d,w) -> { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_MODEL); })
            .setNeutralButton("UNLOAD MODEL", (d,w) -> { localModelPath=""; memory.edit().remove("local_model_path").apply(); LocalAiEngine.close(); refreshDashboard(); respond("Offline model unloaded."); })
            .setNegativeButton("CLOSE",null).show();
    }

    private void onlineAiDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(22,8,22,8);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        EditText geminiKey = new EditText(this); geminiKey.setSingleLine(true); geminiKey.setHint("Gemini API key"); geminiKey.setInputType(129); geminiKey.setText(secureStore.get("gemini_key"));
        EditText geminiModel = new EditText(this); geminiModel.setSingleLine(true); geminiModel.setHint("Gemini model"); geminiModel.setText(memory.getString("gemini_model", "gemini-2.5-flash"));
        EditText groqKey = new EditText(this); groqKey.setSingleLine(true); groqKey.setHint("Groq API key"); groqKey.setInputType(129); groqKey.setText(secureStore.get("groq_key"));
        EditText groqModel = new EditText(this); groqModel.setSingleLine(true); groqModel.setHint("Groq model"); groqModel.setText(memory.getString("groq_model", "openai/gpt-oss-120b"));
        box.addView(geminiKey); box.addView(geminiModel); box.addView(groqKey); box.addView(groqModel);
        new AlertDialog.Builder(this).setTitle("JARVIS GEMINI + GROQ HYBRID")
            .setMessage("Gemini primary, Groq automatic backup. Keys are encrypted at rest with Android Keystore. Free quotas and model access depend on your provider account.")
            .setView(scroll).setPositiveButton("SAVE", (d,w) -> {
                try {
                    secureStore.put("gemini_key", geminiKey.getText().toString().trim());
                    secureStore.put("groq_key", groqKey.getText().toString().trim());
                    String gm = geminiModel.getText().toString().trim(); String gr = groqModel.getText().toString().trim();
                    memory.edit().putString("gemini_model", gm.isEmpty()?"gemini-2.5-flash":gm)
                        .putString("groq_model", gr.isEmpty()?"openai/gpt-oss-120b":gr).apply();
                    refreshDashboard(); respond("Hybrid AI settings saved. Gemini primary, Groq backup.");
                } catch (Exception e) { respond("Secure key storage failed: " + e.getMessage()); }
            }).setNeutralButton("CLEAR KEYS", (d,w) -> { try { secureStore.put("gemini_key", ""); secureStore.put("groq_key", ""); refreshDashboard(); respond("Provider keys cleared."); } catch(Exception e) { respond("Could not clear keys."); } })
            .setNegativeButton("CANCEL", null).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_IMAGE && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            analyzeSelectedImage(data.getData());
            return;
        }
        if(requestCode==PICK_MODEL && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try {
                File target=new File(getFilesDir(),"jarvis_model.litertlm");
                try(InputStream in=getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(target)) {
                    if(in==null) throw new Exception("Cannot open selected model file"); byte[] buf=new byte[PICK_MODEL_COPY_BUFFER]; int n; while((n=in.read(buf))>0) out.write(buf,0,n);
                }
                localModelPath=target.getAbsolutePath(); memory.edit().putString("local_model_path",localModelPath).apply();
                refreshDashboard();
                status.setText("● LOADING OFFLINE MODEL...");
                LocalAiEngine.load(localModelPath, result -> { runOnUiThread(() -> { status.setText("● CORE ONLINE"); respond(result); refreshDashboard(); }); return kotlin.Unit.INSTANCE; });
            } catch(Exception e) { respond("Model import failed: "+e.getMessage()); }
        }
    }

    private void launch(Intent i,String label) { try { startActivity(i); } catch(Exception e) { show("SYSTEM",label+" app available nahi hai."); } }
    private void open(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored) {} }
    private void respond(String s) { if (orbView != null) orbView.setState("SPEAKING"); lastResponse=s; show("JARVIS",s); if (longTermMemory != null && !memory.getBoolean("memory_private_mode", false)) longTermMemory.addConversation("JARVIS", s); memory.edit().putString("last_response",s).apply(); speak(s); status.setText("● CORE ONLINE"); }
    private void speak(String s) { if (orbView != null) orbView.setState("SPEAKING"); if(!ttsReady) { pendingSpeech = s; return; } if(ttsReady) {
        boolean hindi = s != null && s.matches("(?s).*\\p{InDevanagari}.*");
        tts.setLanguage(hindi ? new Locale("hi","IN") : new Locale("en","IN"));
        tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis");
    } }
    private void show(String who,String s) { if (conversation != null) conversation.append("\n"+who+"\n"+s+"\n"); }
    @Override protected void onResume() {
        super.onResume();
        wakeReceiverActive = wakeReceiverRegistered;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && !WakeWordService.isRunning)
            startAlwaysOnWakeService();
        if (dashboard != null) refreshDashboard();
    }
    @Override protected void onPause() { wakeReceiverActive = false; super.onPause(); }
    @Override protected void onDestroy() { if(activeAgentRun!=null) activeAgentRun.cancel(); wakeReceiverActive=false; if(wakeReceiverRegistered && wakeReceiver!=null) { try { unregisterReceiver(wakeReceiver); } catch(Exception ignored) {} wakeReceiverRegistered=false; } if(recognizer!=null) { try { recognizer.cancel(); recognizer.destroy(); } catch(Exception ignored) {} } if(tts!=null) tts.shutdown(); super.onDestroy(); }
}
