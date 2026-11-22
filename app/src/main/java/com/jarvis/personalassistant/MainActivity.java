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
    private static final int PICK_MODEL_COPY_BUFFER = 65536;
    private static final int REQUEST_NOTIFICATIONS = 4102;
    private String localModelPath = "";
    private TextView status, conversation, dashboard;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SharedPreferences memory;
    private SecureStore secureStore;
    private JarvisMemoryStore longTermMemory;
    private boolean ttsReady = false;
    private boolean commandRetryUsed = false;
    private boolean serviceRetryUsed = false;
    private final Handler voiceHandler = new Handler(Looper.getMainLooper());
    private EditText commandInput;
    private String lastResponse = "I am ready, Commander.";
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
                    // Wake acknowledgement is spoken by the foreground service to avoid duplicates.
                } else {
                    if (status != null) status.setText("● COMMAND RECEIVED");
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
        getWindow().setNavigationBarColor(BG);
        memory = getSharedPreferences("jarvis_memory", MODE_PRIVATE);
        secureStore = new SecureStore(this);
        longTermMemory = new JarvisMemoryStore(this);
        lastResponse = memory.getString("last_response", lastResponse);
        localModelPath = memory.getString("local_model_path", "");
        if (!localModelPath.isEmpty() && !new File(localModelPath).exists()) localModelPath = "";
        buildUi();
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
        speak("JARVIS systems online. At your service, Commander.");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20,22,20,18); root.setBackgroundColor(BG);
        TextView title = new TextView(this); title.setText("J.A.R.V.I.S  //  ULTIMATE"); title.setTextColor(CYAN);
        title.setTextSize(23); title.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,62));
        status = new TextView(this); status.setText("● CORE ONLINE  |  VOICE STANDBY"); status.setTextColor(CYAN);
        status.setTextSize(12); status.setGravity(Gravity.CENTER); root.addView(status);
        dashboard = new TextView(this); dashboard.setTextColor(Color.LTGRAY); dashboard.setTextSize(12);
        dashboard.setTypeface(Typeface.MONOSPACE); dashboard.setPadding(12,12,12,12);
        dashboard.setBackgroundColor(Color.rgb(10,25,42)); LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2); dp.topMargin=14; root.addView(dashboard,dp);
        LinearLayout typeRow = new LinearLayout(this); typeRow.setOrientation(LinearLayout.HORIZONTAL);
        commandInput = new EditText(this); commandInput.setSingleLine(true); commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.GRAY); commandInput.setHint("Type a command for JARVIS...");
        commandInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) { submitTypedCommand(); return true; }
            return false;
        });
        typeRow.addView(commandInput, new LinearLayout.LayoutParams(0, 52, 1));
        Button run = button("RUN"); typeRow.addView(run, new LinearLayout.LayoutParams(78, 52));
        run.setOnClickListener(v -> submitTypedCommand());
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, -2); trp.topMargin = 8; root.addView(typeRow, trp);
        ScrollView scroll = new ScrollView(this); conversation = new TextView(this); conversation.setTextColor(Color.WHITE);
        conversation.setTextSize(15); conversation.setPadding(12,18,12,18); scroll.addView(conversation);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,0,1); sp.topMargin=12; sp.bottomMargin=12; root.addView(scroll,sp);
        Button listen = button("TAP TO SPEAK  •  VOICE COMMAND"); root.addView(listen,new LinearLayout.LayoutParams(-1,54)); listen.setOnClickListener(v -> listen());
        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row1,"MEMORY",v->memoryDialog()); addSmall(row1,"TASKS",v->taskDialog()); addSmall(row1,"CUSTOM COMMAND",v->customCommandDialog()); root.addView(row1);
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row2,"CAMERA",v->launch(new Intent("android.media.action.IMAGE_CAPTURE"),"Camera"));
        addSmall(row2,"MAPS",v->open("https://maps.google.com"));
        addSmall(row2,"REPEAT",v->speak(lastResponse)); root.addView(row2);
        LinearLayout row3 = new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL);
        addSmall(row3,"AI BRAIN / MODEL",v->aiBrainDialog()); addSmall(row3,"ONLINE AI SETUP",v->onlineAiDialog()); root.addView(row3);
        setContentView(root);
    }

    private Button button(String label) { Button b=new Button(this); b.setText(label); b.setTextColor(Color.BLACK); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CYAN)); return b; }
    private void addSmall(LinearLayout row,String label,View.OnClickListener listener) {
        Button b=new Button(this); b.setText(label); b.setTextSize(9); b.setPadding(2,2,2,2); b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,48,1); p.setMargins(2,4,2,0); row.addView(b,p);
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
        if (requestCode == 4101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                status.setText("● MICROPHONE READY");
                requestNotificationPermissionIfNeeded();
                startAlwaysOnWakeService();
                respond("Microphone permission granted. I am ready, Commander.");
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
        respond("Microphone permission allow karo, Commander.");
        return false;
    }

    private void listen() {
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
        boolean privateMode = memory.getBoolean("memory_private_mode", false);
        if (longTermMemory != null && !privateMode) longTermMemory.addConversation("Commander", raw);
        if (!privateMode) autoLearnMemory(raw);
        String q=raw.toLowerCase(Locale.ROOT); show("COMMANDER",raw); String answer;
        if (q.contains("time") || q.contains("samay") || q.contains("समय") || q.contains("kitne baje")) answer="Abhi " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()) + " baj rahe hain.";
        else if (q.contains("date") || q.contains("tareekh") || q.contains("तारीख") || q.contains("aaj ki date")) answer="Aaj " + new SimpleDateFormat("dd MMMM yyyy", new Locale("hi","IN")).format(new Date()) + " hai.";
        else if (q.contains("battery") || q.contains("बैटरी")) answer=batteryReport();
        else if (q.contains("system status") || q.contains("system report") || q.contains("ram") || q.contains("storage")) { systemReport(); return; }
        else if (q.contains("wifi") || q.contains("wi-fi")) { launch(new Intent(Settings.ACTION_WIFI_SETTINGS),"Wi-Fi settings"); answer="Wi-Fi settings khol raha hoon."; }
        else if (q.contains("bluetooth")) { launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),"Bluetooth settings"); answer="Bluetooth settings khol raha hoon."; }
        else if (q.contains("settings") || q.contains("सेटिंग")) { launch(new Intent(Settings.ACTION_SETTINGS),"Settings"); answer="Phone settings khol raha hoon."; }
        else if (q.contains("youtube")) { open("https://www.youtube.com"); answer="YouTube khol raha hoon."; }
        else if (q.contains("google") || q.contains("search")) { open("https://www.google.com/search?q="+Uri.encode(raw)); answer="Search kar raha hoon."; }
        else if (q.contains("camera") || q.contains("कैमरा")) { launch(new Intent("android.media.action.IMAGE_CAPTURE"),"Camera"); answer="Camera khol raha hoon."; }
        else if (q.contains("map") || q.contains("navigate") || q.contains("raasta")) { open("https://maps.google.com/?q="+Uri.encode(raw)); answer="Maps khol raha hoon. Destination confirm kar lena."; }
        else if (q.contains("translate") || q.contains("translation")) { open("https://translate.google.com"); answer="Translation service khol raha hoon."; }
        else if (q.contains("alarm")) { Intent a=new Intent(AlarmClock.ACTION_SET_ALARM); a.putExtra(AlarmClock.EXTRA_SKIP_UI,false); launch(a,"Clock"); answer="Alarm set karne ke liye Clock khol raha hoon."; }
        else if (q.equals("memory privacy on") || q.equals("private mode on") || q.equals("memory band")) { memory.edit().putBoolean("memory_private_mode",true).apply(); answer="Privacy mode ON. Conversation history aur automatic learning save nahi honge."; }
        else if (q.equals("memory privacy off") || q.equals("private mode off") || q.equals("memory chalu")) { memory.edit().putBoolean("memory_private_mode",false).apply(); answer="Memory mode ON. Local history aur automatic learning resume."; }
        else if (q.startsWith("memory delete ")) { long id=parseMemoryId(raw); answer=longTermMemory!=null && longTermMemory.deleteMemory(id)?"Memory #"+id+" delete ho gayi.":"Memory ID nahi mili."; }
        else if (q.startsWith("memory edit ")) { String data=raw.substring("memory edit ".length()).trim(); int bar=data.indexOf('|'); if(bar>0){long id=parseLong(data.substring(0,bar).trim()); answer=longTermMemory!=null && longTermMemory.updateMemory(id,"personal",data.substring(bar+1).trim())?"Memory #"+id+" update ho gayi.":"Memory update nahi hui.";}else answer="Format: memory edit ID | naya text"; }
        else if (q.startsWith("memory search") || q.startsWith("search memory") || q.startsWith("find memory")) { String term=raw.replaceFirst("(?i)^(memory search|search memory|find memory)\\s*", "").trim(); answer=longTermMemory == null ? "Memory database unavailable." : longTermMemory.searchMemories(term, 30); }
        else if (q.equals("memory graph") || q.equals("show knowledge graph") || q.startsWith("memory related ")) { String term=q.startsWith("memory related ")?raw.substring("memory related ".length()).trim():""; answer=longTermMemory==null?"Knowledge graph unavailable.":longTermMemory.getKnowledgeGraphText(term); }
        else if (q.startsWith("remind me at ")) { answer=scheduleReminder(raw); }
        else if (q.startsWith("task add ") || q.startsWith("reminder add ") || q.startsWith("yaad dilana ")) { String task=raw.replaceFirst("(?i)^(task add|reminder add|yaad dilana)\\s*", "").trim(); long id=longTermMemory==null?-1:longTermMemory.addTask(task,0); answer=id>0?"Task/reminder #"+id+" local list mein save ho gaya. Due time set karne ke liye TASKS screen kholo.":"Task save nahi hua."; }
        else if (q.equals("tasks") || q.equals("task list") || q.equals("reminders") || q.equals("show tasks")) { answer=longTermMemory==null?"Task store unavailable.":longTermMemory.getTasksText(false); }
        else if (q.startsWith("task done ") || q.startsWith("task complete ")) { long id=parseLong(raw.replaceFirst("(?i)^task (done|complete)\\s*","").trim()); answer=longTermMemory!=null && longTermMemory.completeTask(id)?"Task #"+id+" complete mark kar diya.":"Task ID nahi mili."; }
        else if (q.startsWith("task delete ")) { long id=parseLong(raw.replaceFirst("(?i)^task delete\\s*","").trim()); answer=longTermMemory!=null && longTermMemory.deleteTask(id)?"Task #"+id+" delete kar diya.":"Task ID nahi mili."; }
        else if (q.contains("remember") || q.contains("yaad rakh") || q.contains("याद रख")) { String note=raw.replaceAll("(?i)remember|yaad rakh|yaad rakhna", "").trim(); saveNote(note); answer="Memory mein save kar diya: "+note; }
        else if (q.contains("what did i ask you to remember") || q.contains("maine kya yaad") || q.equals("memory") || q.contains("show memory") || q.contains("meri memory")) { memoryDialog(); return; }
        else if (q.startsWith("mission plan") || q.contains("plan my task") || q.contains("mission briefing")) answer="Mission protocol: objective define karein, task ko steps mein divide karein, risks aur required resources check karein, phir execution se pehle confirmation lein. Main abhi local planning template use kar raha hoon.";
        else if (runCustomCommand(raw)) return;
        else if (q.contains("hello") || q.contains("namaste") || q.contains("हेलो")) answer="Hello Commander. JARVIS systems are ready.";
        else {
            String geminiKey = secureStore.get("gemini_key");
            String groqKey = secureStore.get("groq_key");
            if (!geminiKey.isEmpty() || !groqKey.isEmpty()) {
                status.setText("● HYBRID AI CONNECTING...");
                final String memoryContext = longTermMemory == null || memory.getBoolean("memory_private_mode", false) ? "No saved memory context. Respect privacy." : longTermMemory.getMemoryContext(raw, 10);
                final String prompt = memoryContext + "\nLegacy saved note: " + memory.getString("note", "none") + "\nPrevious response: " + lastResponse + "\nCommander says: " + raw;
                new Thread(() -> {
                    String result = "ERROR: No provider key configured.";
                    if (!geminiKey.isEmpty()) {
                        result = OnlineAiClient.ask("https://generativelanguage.googleapis.com/v1beta/openai", geminiKey,
                            memory.getString("gemini_model", "gemini-2.5-flash"), prompt);
                    }
                    if (result.startsWith("ERROR") && !groqKey.isEmpty()) {
                        result = OnlineAiClient.ask("https://api.groq.com/openai/v1", groqKey,
                            memory.getString("groq_model", "openai/gpt-oss-120b"), prompt);
                    }
                    if (result.startsWith("ERROR") && !localModelPath.isEmpty() && new File(localModelPath).exists()) {
                        LocalAiEngine.ask(localModelPath, raw, localResult -> { runOnUiThread(() -> respond("Online providers unavailable. Offline response: " + localResult)); return kotlin.Unit.INSTANCE; });
                        return;
                    }
                    final String finalResult = result.startsWith("ERROR") ? "Gemini aur Groq dono abhi unavailable hain. Details: " + result : result;
                    runOnUiThread(() -> respond(finalResult));
                }).start();
                return;
            }
            if (!localModelPath.isEmpty() && new File(localModelPath).exists()) {
                status.setText("● OFFLINE AI THINKING...");
                LocalAiEngine.ask(localModelPath, raw, result -> { runOnUiThread(() -> respond(result)); return kotlin.Unit.INSTANCE; });
                return;
            }
            answer="AI brain configure nahi hai, Commander. ONLINE AI SETUP mein Gemini ya Groq key add karein, ya AI BRAIN / MODEL mein offline model load karein.";
        }
        respond(answer);
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
    private void saveNote(String note) {
        if (note == null || note.trim().isEmpty()) return;
        memory.edit().putString("note",note.trim()).apply();
        if (longTermMemory != null) longTermMemory.saveMemory("personal", note.trim());
    }
    private String batteryReport() { try { Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); int level=b==null?-1:b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1); return level<0?"Battery status nahi mil raha.":"Battery "+level+" percent hai."; } catch(Exception e){return "Battery status unavailable.";} }
    private void systemReport() { refreshDashboard(); respond("System report. "+batteryReport()+" Available storage aur memory dashboard par dikhayi gayi hai."); }
    private void memoryDialog() {
        String[] options={"Add memory","Search memories","Edit memory by ID","Delete memory by ID","View conversation history","Clear conversation history","Clear all saved memories","Toggle privacy mode","View knowledge graph","Tasks & reminders"};
        new AlertDialog.Builder(this).setTitle("MEMORY CENTER • "+(longTermMemory==null?0:longTermMemory.getMemoryCount())+" SAVED")
            .setItems(options,(d,which)->{
                if(which==0){EditText in=new EditText(this);in.setHint("What should JARVIS remember?");in.setMinLines(2);new AlertDialog.Builder(this).setTitle("ADD MEMORY").setView(in).setPositiveButton("SAVE",(x,y)->{saveNote(in.getText().toString());respond("Memory saved locally.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==1){EditText in=new EditText(this);in.setHint("Search word or phrase");new AlertDialog.Builder(this).setTitle("SEARCH MEMORY").setView(in).setPositiveButton("SEARCH",(x,y)->show("MEMORY RESULTS",longTermMemory==null?"Unavailable":longTermMemory.searchMemories(in.getText().toString(),50))).setNegativeButton("CANCEL",null).show();}
                else if(which==2){EditText in=new EditText(this);in.setHint("ID | replacement text");new AlertDialog.Builder(this).setTitle("EDIT MEMORY").setMessage("Format: 4 | My favourite colour is blue").setView(in).setPositiveButton("UPDATE",(x,y)->{String[] p=in.getText().toString().split("\\|",2);if(p.length==2&&longTermMemory!=null&&longTermMemory.updateMemory(parseLong(p[0].trim()),"personal",p[1].trim()))respond("Memory updated.");else respond("Format or ID invalid.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==3){EditText in=new EditText(this);in.setHint("Memory ID");new AlertDialog.Builder(this).setTitle("DELETE MEMORY").setView(in).setPositiveButton("DELETE",(x,y)->respond(longTermMemory!=null&&longTermMemory.deleteMemory(parseLong(in.getText().toString()))?"Memory deleted.":"Memory ID not found.")).setNegativeButton("CANCEL",null).show();}
                else if(which==4){show("CONVERSATION HISTORY",longTermMemory==null?"Unavailable":longTermMemory.getRecentConversation(100));}
                else if(which==5){new AlertDialog.Builder(this).setTitle("CLEAR HISTORY?").setMessage("Saved memories will remain.").setPositiveButton("CLEAR",(x,y)->{if(longTermMemory!=null)longTermMemory.clearConversationHistory();respond("Conversation history cleared.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==6){new AlertDialog.Builder(this).setTitle("DELETE ALL MEMORIES?").setMessage("Saved facts and preferences will be permanently deleted.").setPositiveButton("DELETE",(x,y)->{if(longTermMemory!=null)longTermMemory.clearMemories();memory.edit().remove("note").apply();respond("All memories cleared.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==7){boolean on=!memory.getBoolean("memory_private_mode",false);memory.edit().putBoolean("memory_private_mode",on).apply();respond(on?"Privacy mode ON. History and auto-learning paused.":"Privacy mode OFF. Memory resumed.");}
                else if(which==8){show("KNOWLEDGE GRAPH",longTermMemory==null?"Unavailable":longTermMemory.getKnowledgeGraphText(""));}
                else taskDialog();
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
        try {
            Intent alarmIntent=new Intent(this,JarvisReminderReceiver.class);
            alarmIntent.putExtra("reminder_title",title);
            alarmIntent.putExtra("reminder_id",(int)id);
            int requestCode=(int)(id & 0x7fffffff);
            PendingIntent pending=PendingIntent.getBroadcast(this,requestCode,alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms=(AlarmManager)getSystemService(ALARM_SERVICE);
            if(alarms==null)return "Reminder task list mein save hua, lekin Android Alarm service unavailable hai.";
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,dueAt,pending);
        } catch(Exception e) {
            return "Reminder task list mein save hua, lekin alarm schedule nahi hua: "+e.getMessage();
        }
        return "JARVIS reminder schedule ho gaya: "+title+" — "+new SimpleDateFormat("dd MMM, hh:mm a",Locale.getDefault()).format(new Date(dueAt))+". Notification permission ON rakhein.";
    }
    private void taskDialog() {
        EditText in=new EditText(this);in.setHint("New task / reminder");in.setMinLines(2);
        String tasks=longTermMemory==null?"Unavailable":longTermMemory.getTasksText(true);
        new AlertDialog.Builder(this).setTitle("TASKS & REMINDERS").setMessage(tasks).setView(in)
            .setPositiveButton("ADD TASK",(d,w)->{long id=longTermMemory==null?-1:longTermMemory.addTask(in.getText().toString(),0);respond(id>0?"Task saved as #"+id+". Say task done "+id+" when completed.":"Enter a task first.");})
            .setNeutralButton("MARK DONE",(d,w)->{EditText id=new EditText(this);id.setHint("Task ID");new AlertDialog.Builder(this).setTitle("COMPLETE TASK").setView(id).setPositiveButton("DONE",(x,y)->respond(longTermMemory!=null&&longTermMemory.completeTask(parseLong(id.getText().toString()))?"Task completed.":"Task ID not found.")).setNegativeButton("CANCEL",null).show();})
            .setNegativeButton("CLOSE",null).show();
    }
    private long parseLong(String s){try{return Long.parseLong(s.trim());}catch(Exception e){return -1;}}
    private long parseMemoryId(String s){return parseLong(s.replaceFirst("(?i)^memory delete\\s*","").trim());}
    private void autoLearnMemory(String raw){
        if(longTermMemory==null||raw==null)return;
        String s=raw.trim(),q=s.toLowerCase(Locale.ROOT);
        if(q.length()<8||q.startsWith("memory ")||q.startsWith("task ")||q.startsWith("reminder "))return;
        String[] blocked={"password","passcode","otp","cvv","bank account","aadhaar","pan number","medical","medicine","diagnosis","disease","sexual"};
        for(String word:blocked)if(q.contains(word))return;
        boolean learn=q.startsWith("my name is ")||q.startsWith("mera naam ")||q.startsWith("my favourite ")||q.startsWith("my favorite ")||q.startsWith("mera favourite ")||q.startsWith("meri favourite ")||q.startsWith("i like ")||q.startsWith("i love ")||q.startsWith("i prefer ")||q.startsWith("my preference ")||q.startsWith("i live in ")||q.startsWith("my goal is ")||q.startsWith("mera goal ")||q.startsWith("i work as ")||q.startsWith("my job is ")||q.startsWith("actually my ")||q.startsWith("actually, my ")||q.startsWith("correction: my ")||q.startsWith("nahi, mera ")||q.startsWith("nahi mera ");
        if(!learn)return;
        String category=(q.startsWith("my favourite")||q.startsWith("my favorite")||q.startsWith("mera favourite")||q.startsWith("meri favourite")||q.startsWith("i like ")||q.startsWith("i love ")||q.startsWith("i prefer "))?"preference":"personal";
        longTermMemory.saveMemory(category,s);
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
    private void respond(String s) { lastResponse=s; show("JARVIS",s); if (longTermMemory != null && !memory.getBoolean("memory_private_mode", false)) longTermMemory.addConversation("JARVIS", s); memory.edit().putString("last_response",s).apply(); speak(s); status.setText("● CORE ONLINE"); }
    private void speak(String s) { if(!ttsReady) { pendingSpeech = s; return; } if(ttsReady) {
        boolean hindi = s != null && s.matches("(?s).*\\p{InDevanagari}.*");
        tts.setLanguage(hindi ? new Locale("hi","IN") : new Locale("en","IN"));
        tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis");
    } }
    private void show(String who,String s) { conversation.append("\n"+who+"\n"+s+"\n"); }
    @Override protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && !WakeWordService.isRunning)
            startAlwaysOnWakeService();
        if (dashboard != null) refreshDashboard();
    }
    @Override protected void onPause() { super.onPause(); }
    @Override protected void onDestroy() { if(wakeReceiverRegistered && wakeReceiver!=null) { try { unregisterReceiver(wakeReceiver); } catch(Exception ignored) {} wakeReceiverRegistered=false; wakeReceiverActive=false; } if(recognizer!=null) { try { recognizer.cancel(); recognizer.destroy(); } catch(Exception ignored) {} } if(tts!=null) tts.shutdown(); super.onDestroy(); }
}
