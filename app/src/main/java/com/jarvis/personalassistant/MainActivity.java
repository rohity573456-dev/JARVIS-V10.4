package com.jarvis.personalassistant;

// JARVIS three-screen reference UI: home, project chat, and tool exploration.

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
import android.content.ClipboardManager;
import android.content.ClipData;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;
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
    private TextView status;
    private LinearLayout conversationList;
    private ScrollView conversationScroll;
    private JarvisOrbView orbView;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SharedPreferences memory;
    private SecureStore secureStore;
    private JarvisMemoryStore longTermMemory;
    private JarvisToolRegistry agentToolRegistry;
    private JarvisAgentEngine.RunHandle activeAgentRun;
    private volatile AlertDialog activeAgentApprovalDialog;
    private final JarvisResponseSequencer responseSequencer = new JarvisResponseSequencer();
    private boolean ttsReady = false;
    private boolean commandRetryUsed = false;
    private boolean serviceRetryUsed = false;
    private final Handler voiceHandler = new Handler(Looper.getMainLooper());
    private EditText commandInput;
    private LinearLayout contentHost;
    private LinearLayout homeScreen;
    private LinearLayout chatScreen;
    private LinearLayout exploreScreen;
    private LinearLayout navigationBar;
    private TextView headerTitle;
    private TextView headerLeading;
    private LinearLayout chatWelcomePanel;
    private int activeTab = 0;
    private String lastResponse = "I am ready, Boss.";
    private String lastUserPrompt = "";
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
        if (!JarvisPrivacyPolicy.shouldPersistLastResponse(memory.getBoolean("memory_private_mode", false))) memory.edit().remove("last_response").apply();
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
        refreshUiStatus();
        show("JARVIS CORE", "V9 foundation online. Voice, memory and command modules ready.");
        speak("JARVIS systems online. At your service, Boss.");
    }

    private android.graphics.drawable.GradientDrawable panel(int fill, int stroke, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        d.setStroke(Math.max(1, (int)getResources().getDisplayMetrics().density), stroke);
        return d;
    }

    private void buildUi() {
        final float d=getResources().getDisplayMetrics().density;
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(12*d),(int)(5*d),(int)(12*d),(int)(5*d)); root.setClipToPadding(false);
        root.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(0,3,9),Color.rgb(2,17,31),Color.rgb(0,5,13)}));
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        headerLeading=new TextView(this); headerLeading.setText("◉"); headerLeading.setTextColor(Color.rgb(180,229,255)); headerLeading.setTextSize(23); headerLeading.setGravity(Gravity.CENTER); headerLeading.setBackground(panel(Color.rgb(22,35,49),Color.rgb(65,104,139),24));
        header.addView(headerLeading,new LinearLayout.LayoutParams((int)(38*d),(int)(38*d))); headerLeading.setOnClickListener(v->{if(activeTab!=0)switchTab(0);else openSettingsMenu();});
        LinearLayout heading=new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding((int)(10*d),0,0,0);
        headerTitle=new TextView(this); headerTitle.setText("JARVIS"); headerTitle.setTextColor(Color.WHITE); headerTitle.setTextSize(17); headerTitle.setLetterSpacing(0.12f); heading.addView(headerTitle);
        status=new TextView(this); status.setText("● SYSTEM READY"); status.setTextColor(Color.rgb(105,226,211)); status.setTextSize(9); heading.addView(status); header.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        TextView menu=new TextView(this); menu.setText("⋮"); menu.setTextColor(Color.WHITE); menu.setTextSize(23); menu.setGravity(Gravity.CENTER); menu.setBackground(panel(Color.rgb(27,31,40),Color.rgb(69,86,108),25));
        header.addView(menu,new LinearLayout.LayoutParams((int)(42*d),(int)(42*d))); menu.setOnClickListener(v->openSettingsMenu()); root.addView(header,new LinearLayout.LayoutParams(-1,(int)(48*d)));
        contentHost=new LinearLayout(this); contentHost.setOrientation(LinearLayout.VERTICAL); root.addView(contentHost,new LinearLayout.LayoutParams(-1,0,1f));
        buildHomeScreen(d); buildChatScreen(d); buildExploreScreen(d);
        LinearLayout composer=new LinearLayout(this); composer.setOrientation(LinearLayout.HORIZONTAL); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding((int)(7*d),(int)(5*d),(int)(7*d),(int)(5*d)); composer.setBackground(panel(Color.rgb(9,24,41),Color.rgb(57,103,143),29));
        TextView attach=new TextView(this); attach.setText("⌕"); attach.setTextColor(Color.rgb(151,213,246)); attach.setTextSize(21); attach.setGravity(Gravity.CENTER); composer.addView(attach,new LinearLayout.LayoutParams((int)(32*d),(int)(42*d))); attach.setOnClickListener(v->pickImage());
        TextView globe=new TextView(this); globe.setText("◎"); globe.setTextColor(Color.rgb(151,213,246)); globe.setTextSize(18); globe.setGravity(Gravity.CENTER); composer.addView(globe,new LinearLayout.LayoutParams((int)(30*d),(int)(42*d))); globe.setOnClickListener(v->{switchTab(1);searchWeb("");});
        commandInput=new EditText(this); commandInput.setSingleLine(true); commandInput.setTextColor(Color.rgb(244,249,255)); commandInput.setHintTextColor(Color.rgb(135,158,185)); commandInput.setHint("Ask me anything..."); commandInput.setTextSize(14); commandInput.setPadding((int)(6*d),0,(int)(3*d),0); commandInput.setBackgroundColor(Color.TRANSPARENT); commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        commandInput.setOnEditorActionListener((v,actionId,event)->{if(actionId==EditorInfo.IME_ACTION_SEND||actionId==EditorInfo.IME_ACTION_DONE||actionId==EditorInfo.IME_ACTION_GO){submitTypedCommand();return true;}return false;}); composer.addView(commandInput,new LinearLayout.LayoutParams(0,(int)(44*d),1f));
        TextView mic=new TextView(this); mic.setText("♩"); mic.setTextColor(Color.rgb(151,213,246)); mic.setTextSize(19); mic.setGravity(Gravity.CENTER); composer.addView(mic,new LinearLayout.LayoutParams((int)(30*d),(int)(42*d))); mic.setOnClickListener(v->{switchTab(1);listen();});
        TextView send=new TextView(this); send.setText("➤"); send.setTextColor(Color.rgb(1,16,30)); send.setTextSize(19); send.setGravity(Gravity.CENTER); android.graphics.drawable.GradientDrawable sendBg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(155,241,255),Color.rgb(64,169,239)}); sendBg.setCornerRadius(24*d); send.setBackground(sendBg); composer.addView(send,new LinearLayout.LayoutParams((int)(42*d),(int)(42*d))); send.setOnClickListener(v->submitTypedCommand());
        LinearLayout.LayoutParams composerLp=new LinearLayout.LayoutParams(-1,(int)(54*d)); composerLp.topMargin=(int)(7*d); root.addView(composer,composerLp); buildNavigation(d); root.addView(navigationBar,new LinearLayout.LayoutParams(-1,(int)(54*d))); setContentView(root); switchTab(0);
    }
    private void buildHomeScreen(float d) {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeScreen=new LinearLayout(this); homeScreen.setOrientation(LinearLayout.VERTICAL); homeScreen.setGravity(Gravity.CENTER_HORIZONTAL); homeScreen.setPadding((int)(2*d),(int)(8*d),(int)(2*d),(int)(12*d));
        orbView=new JarvisOrbView(this); orbView.setState("STANDBY"); LinearLayout.LayoutParams orbLp=new LinearLayout.LayoutParams(-1,(int)(124*d)); orbLp.topMargin=(int)d; orbLp.bottomMargin=(int)(2*d); homeScreen.addView(orbView,orbLp);
        TextView greeting=new TextView(this); greeting.setText("Hello, I am JARVIS"); greeting.setTextColor(Color.WHITE); greeting.setTextSize(21); greeting.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); greeting.setGravity(Gravity.CENTER); homeScreen.addView(greeting,new LinearLayout.LayoutParams(-1,-2));
        TextView subtitle=new TextView(this); subtitle.setText("How can I help you?"); subtitle.setTextColor(Color.rgb(177,203,226)); subtitle.setTextSize(13); subtitle.setGravity(Gravity.CENTER); LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=(int)(2*d); subLp.bottomMargin=(int)(9*d); homeScreen.addView(subtitle,subLp);
        LinearLayout quick=new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL); quick.setGravity(Gravity.CENTER);
        addQuickChip(quick,"▧  Image edit",()->pickImage(),d); addQuickChip(quick,"✧  Generate prompt",()->{switchTab(1);handle("Help me generate a detailed prompt");},d); addQuickChip(quick,"⌕  Deep research",()->{switchTab(1);searchWeb("");},d);
        HorizontalScrollView quickScroll=new HorizontalScrollView(this); quickScroll.setHorizontalScrollBarEnabled(false); quickScroll.addView(quick,new HorizontalScrollView.LayoutParams(-2,(int)(36*d))); homeScreen.addView(quickScroll,new LinearLayout.LayoutParams(-1,(int)(40*d)));
        TextView toolsTitle=sectionTitle("Discover AI tools",d); LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2); titleLp.topMargin=(int)(12*d); titleLp.bottomMargin=(int)(5*d); homeScreen.addView(toolsTitle,titleLp);
        HorizontalScrollView toolsScroll=new HorizontalScrollView(this); toolsScroll.setHorizontalScrollBarEnabled(false); LinearLayout cards=new LinearLayout(this); cards.setOrientation(LinearLayout.HORIZONTAL);
        addVisualToolCard(cards,"Image Generator","Create images",new int[]{0xff12466c,0xff071c35},()->pickImage(),d);
        addVisualToolCard(cards,"Homework Helper","Solve and explain",new int[]{0xff24566c,0xff102b43},()->{switchTab(1);handle("Help me with homework");},d);
        addVisualToolCard(cards,"Generate Prompt","Create better prompts",new int[]{0xff173f52,0xff0b2037},()->{switchTab(1);handle("Generate a prompt");},d);
        toolsScroll.addView(cards); homeScreen.addView(toolsScroll,new LinearLayout.LayoutParams(-1,(int)(126*d)));
        scroll.addView(homeScreen,new ScrollView.LayoutParams(-1,-2)); homeScreen.setTag(scroll);
    }

    private void addQuickChip(LinearLayout row,String label,Runnable action,float d) {
        TextView chip=new TextView(this); chip.setText(label); chip.setTextColor(Color.rgb(220,239,255)); chip.setTextSize(9); chip.setGravity(Gravity.CENTER); chip.setPadding((int)(9*d),(int)(6*d),(int)(9*d),(int)(6*d)); chip.setBackground(panel(Color.rgb(14,34,54),Color.rgb(69,111,148),20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,(int)(30*d)); lp.setMargins((int)(3*d),0,(int)(3*d),0); row.addView(chip,lp); chip.setOnClickListener(v->action.run());
    }

    private void addVisualToolCard(LinearLayout row,String title,String subtitle,int[] colors,Runnable action,float d) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.BOTTOM); card.setPadding((int)(10*d),(int)(8*d),(int)(10*d),(int)(9*d));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,colors); bg.setCornerRadius(17*d); bg.setStroke((int)Math.max(1,d),0xff345d7b); card.setBackground(bg);
        TextView art=new TextView(this); art.setText("✦"); art.setTextColor(0xffa8e8ff); art.setTextSize(25); art.setGravity(Gravity.CENTER); card.addView(art,new LinearLayout.LayoutParams(-1,0,1f));
        TextView name=new TextView(this); name.setText(title); name.setTextColor(Color.WHITE); name.setTextSize(12); name.setTypeface(Typeface.DEFAULT_BOLD); card.addView(name);
        TextView detail=new TextView(this); detail.setText(subtitle); detail.setTextColor(0xffb7d0e3); detail.setTextSize(9); card.addView(detail);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams((int)(126*d),(int)(116*d)); lp.setMargins((int)(3*d),0,(int)(7*d),0); row.addView(card,lp); card.setOnClickListener(v->action.run());
    }

    private void buildChatScreen(float d) {
        chatScreen=new LinearLayout(this); chatScreen.setOrientation(LinearLayout.VERTICAL);
        chatWelcomePanel=new LinearLayout(this); chatWelcomePanel.setOrientation(LinearLayout.VERTICAL); chatWelcomePanel.setGravity(Gravity.CENTER_HORIZONTAL); chatWelcomePanel.setPadding((int)(4*d),(int)(10*d),(int)(4*d),(int)(8*d));
        TextView project=new TextView(this); project.setText("New Project  ▾"); project.setTextColor(Color.WHITE); project.setTextSize(14); project.setGravity(Gravity.CENTER); project.setBackground(panel(Color.rgb(10,28,47),Color.rgb(46,86,119),18)); chatWelcomePanel.addView(project,new LinearLayout.LayoutParams((int)(150*d),(int)(34*d)));
        TextView welcome=new TextView(this); welcome.setText("How can I help you today?"); welcome.setTextColor(Color.WHITE); welcome.setTextSize(19); welcome.setGravity(Gravity.CENTER); LinearLayout.LayoutParams welcomeLp=new LinearLayout.LayoutParams(-1,-2); welcomeLp.topMargin=(int)(9*d); chatWelcomePanel.addView(welcome,welcomeLp);
        LinearLayout suggestions=new LinearLayout(this); suggestions.setOrientation(LinearLayout.HORIZONTAL); suggestions.setGravity(Gravity.CENTER);
        addQuickChip(suggestions,"▧ Image edit",()->pickImage(),d); addQuickChip(suggestions,"✧ Generate prompt",()->handle("Generate a prompt"),d); addQuickChip(suggestions,"⌕ Deep research",()->searchWeb(""),d);
        HorizontalScrollView suggestionScroll=new HorizontalScrollView(this); suggestionScroll.setHorizontalScrollBarEnabled(false); suggestionScroll.addView(suggestions); LinearLayout.LayoutParams suggestionLp=new LinearLayout.LayoutParams(-1,(int)(40*d)); suggestionLp.topMargin=(int)(8*d); chatWelcomePanel.addView(suggestionScroll,suggestionLp);
        chatScreen.addView(chatWelcomePanel,new LinearLayout.LayoutParams(-1,-2));
        conversationScroll=new ScrollView(this); conversationScroll.setFillViewport(true); conversationScroll.setVerticalScrollBarEnabled(false); conversationScroll.setOverScrollMode(View.OVER_SCROLL_NEVER); conversationScroll.setClipToPadding(false);
        conversationList=new LinearLayout(this); conversationList.setOrientation(LinearLayout.VERTICAL); conversationList.setPadding((int)(2*d),(int)(6*d),(int)(2*d),(int)(12*d)); conversationScroll.addView(conversationList,new ScrollView.LayoutParams(-1,-2));
        chatScreen.addView(conversationScroll,new LinearLayout.LayoutParams(-1,0,1f)); chatScreen.setBackgroundColor(Color.TRANSPARENT);
    }

    private void buildExploreScreen(float d) {
        ScrollView scroll=new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding((int)(3*d),(int)(12*d),(int)(3*d),(int)(20*d));
        list.addView(sectionTitle("Explore JARVIS",d));
        addExploreRow(list,"Image studio","Create or analyze images with your configured vision AI",()->pickImage(),d);
        addExploreRow(list,"Deep research","Search the web and investigate a topic",()->{switchTab(1);searchWeb("");},d);
        addExploreRow(list,"Memory center","Review, search and manage long-term memory",()->memoryDialog(),d);
        addExploreRow(list,"Tasks & reminders","Create and manage your tasks",()->taskDialog(),d);
        addExploreRow(list,"AI brain & models","Configure online providers or local model",()->aiBrainDialog(),d);
        addExploreRow(list,"System diagnostics","Check battery, storage and JARVIS status",()->systemReport(),d);
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2)); exploreScreen=list; exploreScreen.setTag(scroll);
    }

    private TextView sectionTitle(String text,float d) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.rgb(226,243,255));
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        title.setPadding((int)(5*d),(int)(7*d),(int)(5*d),(int)(7*d));
        return title;
    }

    private void addFeatureCard(LinearLayout row,String icon,String title,String subtitle,Runnable action,float d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int)(9*d),(int)(8*d),(int)(7*d),(int)(8*d));
        card.setBackground(panel(Color.rgb(12,35,57),Color.rgb(35,86,122),17));
        TextView glyph = new TextView(this);
        glyph.setText(icon);
        glyph.setTextColor(Color.rgb(132,221,255));
        glyph.setTextSize(19);
        card.addView(glyph);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(11);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);
        TextView desc = new TextView(this);
        desc.setText(subtitle);
        desc.setTextColor(Color.rgb(151,183,210));
        desc.setTextSize(8);
        desc.setMaxLines(2);
        card.addView(desc);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-1,1f);
        lp.setMargins((int)(3*d),(int)(3*d),(int)(3*d),(int)(3*d));
        row.addView(card,lp);
        card.setOnClickListener(v -> action.run());
    }

    private void addExploreRow(LinearLayout parent,String title,String description,Runnable action,float d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int)(15*d),(int)(12*d),(int)(15*d),(int)(12*d));
        card.setBackground(panel(Color.rgb(11,31,52),Color.rgb(37,79,112),17));
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);
        TextView detail = new TextView(this);
        detail.setText(description);
        detail.setTextColor(Color.rgb(155,188,216));
        detail.setTextSize(11);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1,-2);
        detailLp.topMargin = (int)(3*d);
        card.addView(detail,detailLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,(int)(5*d),0,(int)(5*d));
        parent.addView(card,lp);
        card.setOnClickListener(v -> action.run());
    }

    private void buildNavigation(float d) {
        navigationBar=new LinearLayout(this); navigationBar.setOrientation(LinearLayout.HORIZONTAL); navigationBar.setGravity(Gravity.CENTER); navigationBar.setPadding((int)(5*d),(int)(3*d),(int)(5*d),(int)(3*d)); navigationBar.setBackground(panel(Color.rgb(8,20,34),Color.rgb(45,77,105),27));
        addNavigationItem("⌂","Home",0,d); addNavigationItem("◌","Chat",1,d); addNavigationItem("✧","Explore",2,d);
    }

    private void addNavigationItem(String icon,String label,int tab,float d) {
        LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        TextView glyph=new TextView(this); glyph.setText(icon); glyph.setTextSize(17); glyph.setGravity(Gravity.CENTER); glyph.setTextColor(Color.rgb(159,193,220)); item.addView(glyph);
        TextView text=new TextView(this); text.setText(label); text.setTextSize(9); text.setGravity(Gravity.CENTER); text.setTextColor(Color.rgb(159,193,220)); item.addView(text);
        item.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1f)); item.setTag(new View[]{glyph,text}); item.setOnClickListener(v->switchTab(tab)); navigationBar.addView(item);
    }

    private void switchTab(int tab) {
        if(contentHost==null||navigationBar==null)return;
        activeTab=tab; contentHost.removeAllViews();
        View screen=tab==1?chatScreen:(tab==2?(View)exploreScreen.getTag():(View)homeScreen.getTag());
        if(orbView!=null&&orbView.getParent()!=null)((android.view.ViewGroup)orbView.getParent()).removeView(orbView);
        if(tab==0){
            homeScreen.addView(orbView,0,new LinearLayout.LayoutParams(-1,(int)(124*getResources().getDisplayMetrics().density))); headerLeading.setText("◉"); headerTitle.setText("JARVIS"); status.setText("● SYSTEM READY");
        }else if(tab==1){
            chatScreen.addView(orbView,0,new LinearLayout.LayoutParams(-1,(int)(145*getResources().getDisplayMetrics().density))); headerLeading.setText("‹"); headerTitle.setText("New Project"); status.setText("● JARVIS ASSISTANT");
        }else{headerLeading.setText("‹");headerTitle.setText("Explore");status.setText("● AI TOOLS");}
        if(chatWelcomePanel!=null)chatWelcomePanel.setVisibility(View.VISIBLE);
        contentHost.addView(screen,new LinearLayout.LayoutParams(-1,-1));
        for(int i=0;i<navigationBar.getChildCount();i++){
            LinearLayout item=(LinearLayout)navigationBar.getChildAt(i); View[] parts=(View[])item.getTag(); boolean selected=i==tab;
            parts[0].setAlpha(selected?1f:0.68f); parts[1].setAlpha(selected?1f:0.68f); parts[0].setBackground(selected?panel(Color.rgb(22,79,119),Color.rgb(73,174,225),18):null);
            ((TextView)parts[0]).setTextColor(selected?Color.rgb(149,231,255):Color.rgb(159,193,220)); ((TextView)parts[1]).setTextColor(selected?Color.rgb(149,231,255):Color.rgb(159,193,220));
        }
    }

    private void openSettingsMenu() {
        final String[] items = {
            "AI brain / model", "Online AI setup", "Memory center",
            "Tasks & reminders", "Vision / image", "System status",
            "Floating orb setup"
        };
        new AlertDialog.Builder(this).setTitle("JARVIS SETTINGS").setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: aiBrainDialog(); break;
                case 1: onlineAiDialog(); break;
                case 2: memoryDialog(); break;
                case 3: taskDialog(); break;
                case 4: pickImage(); break;
                case 5: systemReport(); break;
                case 6: requestFloatingOrbPermission(); break;
                default: break;
            }
        }).setNegativeButton("CLOSE", null).show();
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
        try {            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
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
        boolean opened = false;
        try {
            Intent webSearch = new Intent(Intent.ACTION_WEB_SEARCH);
            webSearch.putExtra(android.app.SearchManager.QUERY,clean);
            if (webSearch.resolveActivity(getPackageManager()) != null) {
                startActivity(webSearch);
                opened = true;
            } else {
                opened = open("https://www.google.com/search?q="+Uri.encode(clean));
            }
        } catch (Exception e) {
            opened = open("https://www.google.com/search?q="+Uri.encode(clean));
        }
        if (commandInput != null) commandInput.setText("");
        if (opened) {
            show("WEB SEARCH","Opened web search for: "+clean);
            speak("Opening web search for "+clean);
        } else {
            respond("Browser ya web-search app available nahi hai. Search open nahi hua.");
        }
    }

    private void refreshUiStatus() {
        if(status==null)return;
        boolean cloudReady=secureStore!=null&&(!secureStore.get("nebius_key").isEmpty()||!secureStore.get("gemini_key").isEmpty()||!secureStore.get("groq_key").isEmpty()||!secureStore.get("openai_key").isEmpty());
        status.setText("● "+(WakeWordService.isRunning?"VOICE ACTIVE":"SYSTEM READY")+(cloudReady?"  ·  AI CONNECTED":""));
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
                respond("Microphone permission granted. I am ready, Boss.");
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
        respond("Microphone permission allow karo, Boss.");
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
                public void onBeginningOfSpeech() { status.setText("● HEARING YOU..."); }                public void onRmsChanged(float r) {}
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
        switchTab(1);
        final long responseToken = responseSequencer.beginRequest();
        lastUserPrompt = raw == null ? "" : raw;
        if (orbView != null) orbView.setState("THINKING");
        String q=raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        boolean enablingPrivateMode = JarvisIntentRouter.isPrivateModeEnableCommand(raw);
        boolean privacyToggle = enablingPrivateMode || JarvisIntentRouter.isPrivateModeDisableCommand(raw);
        boolean privateMode = memory.getBoolean("memory_private_mode", false) || enablingPrivateMode;
        if (longTermMemory != null && !privateMode && !privacyToggle) longTermMemory.addConversation("Commander", raw);
        if (!privateMode && !privacyToggle && !JarvisIntentRouter.isRememberCommand(raw)) autoLearnMemory(raw);
        show("COMMANDER",raw); String answer;
        if (JarvisIntentRouter.isTimeQuery(raw)) answer="Abhi " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()) + " baj rahe hain.";
        else if (JarvisIntentRouter.isDateQuery(raw)) answer="Aaj " + new SimpleDateFormat("dd MMMM yyyy", new Locale("hi","IN")).format(new Date()) + " hai.";
        else if (JarvisIntentRouter.isBatteryStatusQuery(raw)) answer=batteryReport();
        else if (JarvisIntentRouter.isFlashlightCommand(raw)) { setFlashlight(q.contains("on") || q.contains("enable") || q.contains("chalu")); return; }
        else if (JarvisIntentRouter.isVolumeUpCommand(raw)) { adjustVolume(1); return; }
        else if (JarvisIntentRouter.isVolumeDownCommand(raw)) { adjustVolume(-1); return; }
        else if (JarvisIntentRouter.isMuteCommand(raw)) { adjustVolume(0); return; }
        else if (JarvisIntentRouter.isBrightnessSettingsCommand(raw)) { answer=launch(new Intent(Settings.ACTION_DISPLAY_SETTINGS))?"Display settings khol raha hoon. Android permission ke bina brightness directly change nahi kar sakta.":"Display settings is device par available nahi hain."; }
        else if (JarvisIntentRouter.isSystemReportQuery(raw)) { systemReport(); return; }
        else if (JarvisIntentRouter.isCancelAgentCommand(raw)) {
            if (activeAgentRun != null && !activeAgentRun.isFinished()) {
                activeAgentRun.cancel();
                AlertDialog approval = activeAgentApprovalDialog;
                if (approval != null && approval.isShowing()) approval.cancel();
                answer="Agent cancellation requested. Any in-flight operation may finish; no further steps will start.";
            } else answer="No active agent task to cancel.";
        }
        else if (JarvisIntentRouter.isWifiSettingsCommand(raw)) { answer=launch(new Intent(Settings.ACTION_WIFI_SETTINGS))?"Wi-Fi settings khol raha hoon.":"Wi-Fi settings is device par available nahi hain."; }
        else if (JarvisIntentRouter.isBluetoothSettingsCommand(raw)) { answer=launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))?"Bluetooth settings khol raha hoon.":"Bluetooth settings is device par available nahi hain."; }
        else if (JarvisIntentRouter.isGeneralSettingsCommand(raw)) { answer=launch(new Intent(Settings.ACTION_SETTINGS))?"Phone settings khol raha hoon.":"Phone settings is device par available nahi hain."; }
        else if (JarvisIntentRouter.isOpenYoutubeCommand(raw)) { answer=open("https://www.youtube.com")?"YouTube khol raha hoon.":"YouTube ya browser available nahi hai."; }
        else if (q.startsWith("web search ") || q.startsWith("search web ")) { searchWeb(raw.replaceFirst("(?i)^(web\\s+search|search\\s+web)\\s*","")); return; }
        else if (JarvisIntentRouter.isWebSearchCommand(raw)) { searchWeb(raw.replaceFirst("(?i)^(web\\s+)?search\\s*","")); return; }
        else if (JarvisIntentRouter.isOpenCameraCommand(raw)) { answer=launch(new Intent("android.media.action.IMAGE_CAPTURE"))?"Camera khol raha hoon.":"Camera app available nahi hai."; }
        else if (JarvisIntentRouter.isMapsCommand(raw)) { answer=open("https://maps.google.com/?q="+Uri.encode(raw))?"Maps khol raha hoon. Destination confirm kar lena.":"Maps ya browser available nahi hai."; }
        else if (JarvisIntentRouter.isOpenTranslateCommand(raw)) { answer=open("https://translate.google.com")?"Translation service khol raha hoon.":"Browser available nahi hai."; }
        else if (JarvisIntentRouter.isAlarmCommand(raw)) { Intent a=new Intent(AlarmClock.ACTION_SET_ALARM); a.putExtra(AlarmClock.EXTRA_SKIP_UI,false); answer=launch(a)?"Alarm set karne ke liye Clock khol raha hoon.":"Clock app available nahi hai."; }
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
        else if (q.equals("memory embeddings off")) { invalidatePendingMemoryJobs(); memory.edit().putBoolean("memory_cloud_embeddings",false).apply(); answer="Cloud semantic embeddings OFF. JARVIS will use on-device lexical, temporal and graph recall."; }
        else if (q.equals("memory embeddings delete")) { invalidatePendingMemoryJobs(); if(longTermMemory!=null)longTermMemory.clearEmbeddings(); answer="Stored semantic vector index deleted. Your original memories are unchanged."; }
        else if (JarvisIntentRouter.isPrivateModeEnableCommand(raw)) { invalidatePendingMemoryJobs(); memory.edit().putBoolean("memory_private_mode",true).remove("last_response").apply(); respondEphemeral("Privacy mode ON. History aur auto-learning save nahi honge; current request phir bhi configured cloud AI ko bheji ja sakti hai."); return; }
        else if (JarvisIntentRouter.isPrivateModeDisableCommand(raw)) { memory.edit().putBoolean("memory_private_mode",false).apply(); respondEphemeral("Memory mode ON. Local history aur automatic learning resume."); return; }
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
        else if (JarvisIntentRouter.isRememberCommand(raw)) { String note=raw.replaceFirst("(?i)^(please\\s+)?remember\\s+(that|this|my)\\s*","").replaceFirst("(?i)^remember\\s*[:,-]?\\s*","").replaceFirst("(?i)^yaad\\s+rakh(na)?\\s*","").replaceFirst("^याद\\s+रख(ना|ो)?\\s*","").trim(); answer=saveNote(note)?"Memory mein save kar diya: "+note:"Memory save nahi hui. Text empty, sensitive ya invalid ho sakta hai."; }
        else if (JarvisIntentRouter.isMemoryOverviewQuery(raw)) { memoryDialog(); return; }
        else if (q.startsWith("mission plan") || q.contains("plan my task") || q.contains("mission briefing")) answer="Mission protocol: objective define karein, task ko steps mein divide karein, risks aur required resources check karein, phir execution se pehle confirmation lein. Main abhi local planning template use kar raha hoon.";
        else if (runCustomCommand(raw)) return;
        else if (JarvisIntentRouter.isGreeting(raw)) answer="Hello Boss. JARVIS systems are ready.";
        else {
            if (isAgentActionIntent(q)) {
                runAgent(raw, responseToken);
                return;
            }
            String geminiKey = secureStore.get("gemini_key");
            String groqKey = secureStore.get("groq_key");
            String nebiusKey = secureStore.get("nebius_key");
            String openAiKey = secureStore.get("openai_key");
            if (!geminiKey.isEmpty() || !groqKey.isEmpty() || !nebiusKey.isEmpty() || !openAiKey.isEmpty() || (!localModelPath.isEmpty() && new File(localModelPath).isFile())) {
                status.setText("● HYBRID AI ROUTING...");
                final boolean privateContext = memory.getBoolean("memory_private_mode", false);
                final boolean useCloudVector = !privateContext && memory.getBoolean("memory_cloud_embeddings", false) && !geminiKey.isEmpty();
                new Thread(() -> {                    if (!responseSequencer.isCurrent(responseToken)) return;
                    float[] queryVector = useCloudVector ? JarvisEmbeddingClient.embed(geminiKey, raw, true) : null;
                    if (!responseSequencer.isCurrent(responseToken)) return;
                    String baseMemoryContext = longTermMemory == null || privateContext ? "No saved memory context. Respect privacy." :
                        (queryVector == null ? longTermMemory.getMemoryContext(raw, 10) : longTermMemory.getMemoryContext(raw, 10, queryVector));
                    final String memoryContext = privateContext || longTermMemory == null ? baseMemoryContext :
                        JarvisHistoryVerifier.attachEvidence(baseMemoryContext, longTermMemory.getConversationEvidence(raw, 18));
                    final String legacyNote = privateContext ? "none" : memory.getString("note", "none");
                    final String prompt = JarvisConversationContext.compose(memoryContext, legacyNote,
                        lastResponse, raw, privateContext);
                    if (!responseSequencer.isCurrent(responseToken)) return;
                    JarvisHybridBrain.ask(geminiKey, memory.getString("gemini_model", "gemini-2.5-flash"),
                        groqKey, memory.getString("groq_model", "openai/gpt-oss-120b"),
                        nebiusKey, localModelPath, prompt, raw, openAiKey, result -> runOnUiThread(() -> {
                        if (!isFinishing() && responseSequencer.isCurrent(responseToken)) respond(result);
                    }));
                }, "Jarvis-HybridBrain").start();
                return;
            }
            answer="AI brain configure nahi hai, Boss. ONLINE AI SETUP mein Nebius, Gemini ya Groq key add karein, ya AI BRAIN / MODEL mein offline model load karein.";
        }
        respond(answer);
    }
    private boolean isAgentActionIntent(String q) {
        return JarvisActionIntentRouter.isExplicitAction(q);
    }

    private void runAgent(String objective, long responseToken) {
        String geminiKey = secureStore.get("gemini_key");
        String groqKey = secureStore.get("groq_key");
        String nebiusKey = secureStore.get("nebius_key");
        String openAiKey = secureStore.get("openai_key");
        if (geminiKey.isEmpty() && groqKey.isEmpty() && nebiusKey.isEmpty() && openAiKey.isEmpty()) {
            respond("Android agent actions ke liye Nebius, Gemini ya Groq API key configure karo. Local chat brain se direct phone actions enabled nahi hain.");
            return;
        }
        if (activeAgentRun != null && !activeAgentRun.isFinished()) {
            respond("JARVIS is already handling an agent task. Pehle current task complete hone do.");
            return;
        }
        status.setText("● AGENT PLANNING...");
        JarvisAgentEngine engine = new JarvisAgentEngine(
            new JarvisStructuredPlanner(agentToolRegistry, geminiKey,
                memory.getString("gemini_model", "gemini-2.5-flash"),
                groqKey, memory.getString("groq_model", "openai/gpt-oss-120b"), nebiusKey, openAiKey),
            agentToolRegistry, this::requestAgentApproval);
        final JarvisAgentEngine.RunHandle[] submittedRun = new JarvisAgentEngine.RunHandle[1];
        submittedRun[0] = engine.submit(objective, new JarvisAgentEngine.Listener() {
            @Override public void onState(JarvisAgentEngine.State state, String detail) {
                runOnUiThread(() -> {
                    if (status == null || isFinishing() || !responseSequencer.isCurrent(responseToken)) return;
                    if (state == JarvisAgentEngine.State.AWAITING_APPROVAL) status.setText("● WAITING FOR YOUR APPROVAL");
                    else if (state == JarvisAgentEngine.State.RUNNING) status.setText("● EXECUTING APPROVED ACTION");
                    else if (state == JarvisAgentEngine.State.PLANNING) status.setText("● AI ACTION PLANNING...");
                    else if (state == JarvisAgentEngine.State.VERIFYING) status.setText("● VERIFYING ACTION...");
                });
            }
            @Override public void onComplete(JarvisAgentEngine.AgentReport report) {
                runOnUiThread(() -> {
                    if (activeAgentRun == submittedRun[0]) activeAgentRun = null;
                    if (isFinishing() || !responseSequencer.isCurrent(responseToken)) return;
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
        activeAgentRun = submittedRun[0];
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
                .setOnDismissListener(d -> {
                    if (resolved.compareAndSet(false, true)) latch.countDown();
                    if (activeAgentApprovalDialog == dialog[0]) activeAgentApprovalDialog = null;
                })
                .create();
            activeAgentApprovalDialog = dialog[0];
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
                if(action.startsWith("https://")||action.startsWith("http://")) {
                    respond(open(action)?"Custom website opened successfully.":"Browser unavailable; custom command could not be opened.");
                } else {
                    respond("Custom command is invalid. Only saved HTTPS website URLs are supported.");
                }
                return true;
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
                memory.getString("groq_model","openai/gpt-oss-120b"),secureStore.get("openai_key"),note.trim(),
                memoryConsentGate());
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
        try {            android.media.AudioManager audio = (android.media.AudioManager)getSystemService(Context.AUDIO_SERVICE);
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
    private void systemReport() { refreshUiStatus(); respond("System report. "+batteryReport()+" Storage and memory information are available in the system report."); }
    private void memoryDialog() {
        String[] options={"Add memory","Search memories","Edit memory by ID","Delete memory by ID","View conversation history","Clear conversation history","Clear all saved memories","Toggle privacy mode","View knowledge graph","Tasks & reminders","Memory statistics","Consolidate & reflect","View pinned core memories","Toggle Gemini semantic vectors","Delete semantic vector index","Toggle AI extraction & reflection","View structured profile","Memory temporary note","Repair memory index & orphan records"};
        new AlertDialog.Builder(this).setTitle("MEMORY CENTER • "+(longTermMemory==null?0:longTermMemory.getMemoryCount())+" SAVED")
            .setItems(options,(d,which)->{
                if(which==0){EditText in=new EditText(this);in.setHint("What should JARVIS remember?");in.setMinLines(2);new AlertDialog.Builder(this).setTitle("ADD MEMORY").setView(in).setPositiveButton("SAVE",(x,y)->respond(saveNote(in.getText().toString())?"Memory saved locally.":"Memory save nahi hui. Text empty, sensitive ya invalid ho sakta hai.")).setNegativeButton("CANCEL",null).show();}
                else if(which==1){EditText in=new EditText(this);in.setHint("Search word or phrase");new AlertDialog.Builder(this).setTitle("SEARCH MEMORY").setView(in).setPositiveButton("SEARCH",(x,y)->show("MEMORY RESULTS",longTermMemory==null?"Unavailable":longTermMemory.searchMemories(in.getText().toString(),50))).setNegativeButton("CANCEL",null).show();}
                else if(which==2){EditText in=new EditText(this);in.setHint("ID | replacement text");new AlertDialog.Builder(this).setTitle("EDIT MEMORY").setMessage("Format: 4 | My favourite colour is blue").setView(in).setPositiveButton("UPDATE",(x,y)->{String[] p=in.getText().toString().split("\\|",2);if(p.length==2&&longTermMemory!=null&&longTermMemory.updateMemory(parseLong(p[0].trim()),null,p[1].trim()))respond("Memory updated.");else respond("Format or ID invalid.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==3){EditText in=new EditText(this);in.setHint("Memory ID");new AlertDialog.Builder(this).setTitle("DELETE MEMORY").setView(in).setPositiveButton("DELETE",(x,y)->respond(longTermMemory!=null&&longTermMemory.deleteMemory(parseLong(in.getText().toString()))?"Memory deleted.":"Memory ID not found.")).setNegativeButton("CANCEL",null).show();}
                else if(which==4){show("CONVERSATION HISTORY",longTermMemory==null?"Unavailable":longTermMemory.getRecentConversation(100));}
                else if(which==5){new AlertDialog.Builder(this).setTitle("CLEAR HISTORY?").setMessage("Saved facts will remain. Conversation turns, session digests, and the persisted last response will be cleared. Pending AI memory jobs from this history will be cancelled.").setPositiveButton("CLEAR",(x,y)->{invalidatePendingMemoryJobs();if(longTermMemory!=null)longTermMemory.clearConversationHistory();memory.edit().remove("last_response").apply();if(conversationList!=null)conversationList.removeAllViews();lastUserPrompt="";lastResponse="";respondEphemeral("Conversation history cleared. Saved facts remain; pending history learning cancelled.");lastResponse="";}).setNegativeButton("CANCEL",null).show();}
                else if(which==6){new AlertDialog.Builder(this).setTitle("DELETE ALL MEMORIES?").setMessage("Saved facts, summaries and indexes will be permanently deleted. Pending AI memory jobs will be cancelled. Chat history remains a separate control.").setPositiveButton("DELETE",(x,y)->{invalidatePendingMemoryJobs();if(longTermMemory!=null)longTermMemory.clearMemories();memory.edit().remove("note").apply();respond("All saved memories cleared. Chat history remains.");}).setNegativeButton("CANCEL",null).show();}
                else if(which==7){boolean on=!memory.getBoolean("memory_private_mode",false);if(on)invalidatePendingMemoryJobs();if(on)memory.edit().remove("last_response").apply();memory.edit().putBoolean("memory_private_mode",on).apply();respondEphemeral(on?"Privacy mode ON. History and auto-learning paused; current requests may still go to your configured cloud AI.":"Privacy mode OFF. Memory resumed.");}
                else if(which==8){show("KNOWLEDGE GRAPH",longTermMemory==null?"Unavailable":longTermMemory.getKnowledgeGraphText(""));}
                else if(which==9) taskDialog();
                else if(which==10) show("MEMORY HEALTH",longTermMemory==null?"Unavailable":longTermMemory.getMemoryStatsText());
                else if(which==11){int made=longTermMemory==null?0:longTermMemory.consolidateMemories();respond("Memory reflection complete. "+made+" session digests created; facts preserved.");}
                else if(which==12) show("PINNED CORE MEMORIES",longTermMemory==null?"Unavailable":longTermMemory.getPinnedMemoriesText(50));
                else if(which==13){
                    boolean enabled=memory.getBoolean("memory_cloud_embeddings",false);
                    if(enabled){invalidatePendingMemoryJobs();memory.edit().putBoolean("memory_cloud_embeddings",false).apply();respond("Gemini semantic vectors OFF. Local memory recall remains active.");}
                    else new AlertDialog.Builder(this).setTitle("ENABLE GEMINI SEMANTIC MEMORY?")
                        .setMessage("This optional feature sends saved memory snippets and future memory-search queries to Google Gemini Embeddings. Your memory database remains on this phone, and local search stays available as fallback. Enable only if you agree.")
                        .setPositiveButton("ENABLE",(x,y)->{memory.edit().putBoolean("memory_cloud_embeddings",true).apply();indexSemanticMemoryIfEnabled(300);respond("Gemini semantic vectors enabled. Existing memories will be indexed in the background.");})
                        .setNegativeButton("CANCEL",null).show();
                }
                else if(which==14){if(longTermMemory!=null)longTermMemory.clearEmbeddings();respond("Semantic vector index deleted. Saved facts remain.");}
                else if(which==15) confirmAiMemoryLearning();
                else if(which==16) show("STRUCTURED PROFILE",longTermMemory==null?"Unavailable":longTermMemory.getProfileSummaryText());
                else if(which==17){EditText temp=new EditText(this);temp.setHint("TTL command: 2h | short note");new AlertDialog.Builder(this).setTitle("TEMPORARY MEMORY").setMessage("Example: 30m | call the courier").setView(temp).setPositiveButton("SAVE",(x,y)->handle("memory temp "+temp.getText().toString())).setNegativeButton("CANCEL",null).show();}
                else if(which==18){int removed=longTermMemory==null?0:longTermMemory.cleanupMemoryOrphans();respond("Memory maintenance complete. "+removed+" orphan records removed.");}
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
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return "Notification permission OFF hai. Android Settings mein JARVIS notifications ON karke reminder dobara set karein; abhi kuch save nahi hua.";
        long id=longTermMemory==null?-1:longTermMemory.addTask(title,dueAt);
        if(id<=0)return "Reminder save nahi ho paya.";
        if(!JarvisReminderScheduler.schedule(getApplicationContext(),id,title,dueAt)) {
            longTermMemory.deleteTask(id);
            return "Android Alarm service reminder schedule nahi kar payi; task save nahi rakha gaya.";
        }
        return "JARVIS reminder schedule ho gaya: "+title+" — "+new SimpleDateFormat("dd MMM, hh:mm a",Locale.getDefault()).format(new Date(dueAt))+".";

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
    private void invalidatePendingMemoryJobs(){
        if(memory!=null)memory.edit().putLong("memory_generation",memory.getLong("memory_generation",0L)+1L).apply();
    }
    private JarvisMemoryAiWorker.ConsentGate memoryConsentGate(){
        final long generation=memory==null?0L:memory.getLong("memory_generation",0L);
        return () -> memory!=null && memory.getBoolean("memory_ai_learning",false) &&
            !memory.getBoolean("memory_private_mode",false) &&
            memory.getLong("memory_generation",0L)==generation;
    }
    private void autoLearnMemory(String raw){
        if(longTermMemory==null||raw==null||memory.getBoolean("memory_private_mode",false))return;
        if(JarvisMemoryExtractor.learn(longTermMemory,raw)) indexSemanticMemoryIfEnabled(1);
        if(memory.getBoolean("memory_ai_learning",false))
            JarvisMemoryAiWorker.learnAsync(longTermMemory,secureStore.get("gemini_key"),
                memory.getString("gemini_model","gemini-2.5-flash"),secureStore.get("groq_key"),
                memory.getString("groq_model","openai/gpt-oss-120b"),secureStore.get("openai_key"),raw,
                memoryConsentGate());
    }
    private void runAiMemoryReflectionIfEnabled(){
        if(longTermMemory==null||memory.getBoolean("memory_private_mode",false)||!memory.getBoolean("memory_ai_learning",false))return;
        JarvisMemoryAiWorker.reflectPendingAsync(longTermMemory,secureStore.get("gemini_key"),
            memory.getString("gemini_model","gemini-2.5-flash"),secureStore.get("groq_key"),
            memory.getString("groq_model","openai/gpt-oss-120b"),secureStore.get("openai_key"),            memoryConsentGate());
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
        if(secureStore.get("gemini_key").isEmpty()&&secureStore.get("groq_key").isEmpty()&&secureStore.get("openai_key").isEmpty()){
            respond("Pehle ONLINE AI SETUP mein Gemini, Groq ya OpenAI API key add karo.");return;
        }
        new AlertDialog.Builder(this).setTitle("ENABLE AI MEMORY LEARNING?")
            .setMessage("When enabled, new user messages and eligible past conversation sessions may be sent to your configured Gemini, Groq or OpenAI provider to extract durable facts and create summaries. Sensitive categories are filtered, but cloud processing is still involved. Local memory remains available. Enable only if you agree.")
            .setPositiveButton("ENABLE",(d,w)->{
                memory.edit().putBoolean("memory_ai_learning",true).apply();
                runAiMemoryReflectionIfEnabled();
                respond("AI memory learning ON. Local learning remains active too.");
            }).setNegativeButton("CANCEL",null).show();
    }
    private void indexSemanticMemoryIfEnabled(int maximum){
        if(memory==null||!memory.getBoolean("memory_cloud_embeddings",false)||
                memory.getBoolean("memory_private_mode",false)||secureStore==null||longTermMemory==null)return;
        final long generation=memory.getLong("memory_generation",0L);
        JarvisEmbeddingWorker.indexPending(longTermMemory,secureStore,maximum,()->memory!=null &&
            memory.getBoolean("memory_cloud_embeddings",false) &&
            !memory.getBoolean("memory_private_mode",false) &&
            memory.getLong("memory_generation",0L)==generation);
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
            .setNeutralButton("UNLOAD MODEL", (d,w) -> { localModelPath=""; memory.edit().remove("local_model_path").apply(); LocalAiEngine.close(); refreshUiStatus(); respond("Offline model unloaded."); })
            .setNegativeButton("CLOSE",null).show();
    }

    private void onlineAiDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(22,8,22,8);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        EditText nebiusKey = new EditText(this); nebiusKey.setSingleLine(true); nebiusKey.setHint("Nebius API key (GPT-OSS-120B)"); nebiusKey.setInputType(129); nebiusKey.setText(secureStore.get("nebius_key"));
        EditText geminiKey = new EditText(this); geminiKey.setSingleLine(true); geminiKey.setHint("Gemini API key"); geminiKey.setInputType(129); geminiKey.setText(secureStore.get("gemini_key"));
        EditText geminiModel = new EditText(this); geminiModel.setSingleLine(true); geminiModel.setHint("Gemini model"); geminiModel.setText(memory.getString("gemini_model", "gemini-2.5-flash"));
        EditText groqKey = new EditText(this); groqKey.setSingleLine(true); groqKey.setHint("Groq API key"); groqKey.setInputType(129); groqKey.setText(secureStore.get("groq_key"));
        EditText groqModel = new EditText(this); groqModel.setSingleLine(true); groqModel.setHint("Groq model"); groqModel.setText(memory.getString("groq_model", "openai/gpt-oss-120b"));
        EditText openAiKey = new EditText(this); openAiKey.setSingleLine(true); openAiKey.setHint("Optional OpenAI API key (GPT-6.2 Astra; paid API)"); openAiKey.setInputType(129); openAiKey.setText(secureStore.get("openai_key"));
        box.addView(nebiusKey); box.addView(geminiKey); box.addView(geminiModel); box.addView(groqKey); box.addView(groqModel); box.addView(openAiKey);
        new AlertDialog.Builder(this).setTitle("JARVIS MULTI-BRAIN PROVIDERS")
            .setMessage("JARVIS routes requests across Nebius GPT-OSS-120B, Gemini and Groq, selecting a preferred provider by task and falling back when a provider fails. Optional OpenAI Astra remains a separate premium route. Provider API usage may be billed separately. Keys are encrypted at rest with Android Keystore.")
            .setView(scroll).setPositiveButton("SAVE", (d,w) -> {
                try {
                    secureStore.put("nebius_key", nebiusKey.getText().toString().trim());
                    secureStore.put("gemini_key", geminiKey.getText().toString().trim());
                    secureStore.put("groq_key", groqKey.getText().toString().trim());
                    secureStore.put("openai_key", openAiKey.getText().toString().trim());
                    String gm = geminiModel.getText().toString().trim(); String gr = groqModel.getText().toString().trim();
                    memory.edit().putString("gemini_model", gm.isEmpty()?"gemini-2.5-flash":gm)
                        .putString("groq_model", gr.isEmpty()?"openai/gpt-oss-120b":gr).apply();
                    refreshUiStatus(); respond("AI provider settings saved. Astra is optional for complex tasks; Gemini/Groq remain fallback providers.");
                } catch (Exception e) { respond("Secure key storage failed: " + e.getMessage()); }
            }).setNeutralButton("CLEAR KEYS", (d,w) -> { try { secureStore.put("nebius_key", ""); secureStore.put("gemini_key", ""); secureStore.put("groq_key", ""); secureStore.put("openai_key", ""); refreshUiStatus(); respond("Provider keys cleared."); } catch(Exception e) { respond("Could not clear keys."); } })
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
                refreshUiStatus();
                status.setText("● LOADING OFFLINE MODEL...");
                LocalAiEngine.load(localModelPath, result -> { runOnUiThread(() -> { status.setText("● CORE ONLINE"); respond(result); refreshUiStatus(); }); return kotlin.Unit.INSTANCE; });
            } catch(Exception e) { respond("Model import failed: "+e.getMessage()); }
        }
    }

    private boolean launch(Intent i) { return JarvisActionResultGuard.run(() -> startActivity(i)); }
    private boolean open(String url) { return JarvisActionResultGuard.run(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))); }
    private void respondEphemeral(String s) { if (orbView != null) orbView.setState("SPEAKING"); lastResponse=s; show("JARVIS",s); speak(s); status.setText("● CORE ONLINE"); }
    private void respond(String s) { if (orbView != null) orbView.setState("SPEAKING"); lastResponse=s; show("JARVIS",s); if (longTermMemory != null && JarvisPrivacyPolicy.shouldPersistConversation(memory.getBoolean("memory_private_mode", false))) longTermMemory.addConversation("JARVIS", s); persistLastResponse(s); speak(s); status.setText("● CORE ONLINE"); }

    /** Private Mode must not leave a persisted copy of the latest response. */
    private void persistLastResponse(String response) {
        if (memory == null) return;
        android.content.SharedPreferences.Editor editor = memory.edit();
        if (!JarvisPrivacyPolicy.shouldPersistLastResponse(memory.getBoolean("memory_private_mode", false))) editor.remove("last_response");
        else editor.putString("last_response", response == null ? "" : response);
        editor.apply();
    }
    private void speak(String s) { if (orbView != null) orbView.setState("SPEAKING"); if(!ttsReady) { pendingSpeech = s; return; } if(ttsReady) {
        boolean hindi = s != null && s.matches("(?s).*\\p{InDevanagari}.*");
        tts.setLanguage(hindi ? new Locale("hi","IN") : new Locale("en","IN"));
        tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis");
    } }
    private void show(String who,String s) {
        if(who!=null&&(who.equalsIgnoreCase("YOU")||who.equalsIgnoreCase("COMMANDER"))&&chatWelcomePanel!=null)chatWelcomePanel.setVisibility(View.GONE);
        if(conversationList==null)return;
        final float d=getResources().getDisplayMetrics().density;
        boolean fromUser=who!=null&&(who.equalsIgnoreCase("YOU")||who.equalsIgnoreCase("COMMANDER"));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(fromUser?Gravity.END:Gravity.START); row.setPadding(0,(int)(4*d),0,(int)(4*d));
        TextView label=new TextView(this); label.setText(fromUser?"YOU":"JARVIS");
        label.setTextColor(fromUser?Color.rgb(104,203,255):Color.rgb(111,226,207));
        label.setTextSize(9); label.setLetterSpacing(0.12f);
        label.setPadding((int)(10*d),0,(int)(10*d),(int)(4*d));
        row.addView(label,new LinearLayout.LayoutParams(-2,-2));
        TextView message=new TextView(this); message.setText(s==null?"":s);
        message.setTextColor(Color.rgb(232,241,252)); message.setTextSize(14);
        message.setLineSpacing(3*d,1.04f); message.setPadding((int)(13*d),(int)(10*d),(int)(13*d),(int)(10*d));
        message.setBackground(panel(fromUser?Color.rgb(19,49,75):Color.rgb(14,29,49),
            fromUser?Color.rgb(44,107,148):Color.rgb(35,70,99),18));
        LinearLayout.LayoutParams bubble=new LinearLayout.LayoutParams(
            fromUser?(int)(getResources().getDisplayMetrics().widthPixels*0.78f):-2,-2);
        bubble.setMargins(fromUser?(int)(34*d):0,0,fromUser?0:(int)(34*d),0);
        row.addView(message,bubble);
        message.setOnLongClickListener(v -> { copyResponse(message.getText().toString()); return true; });
        if (!fromUser) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding((int)(8*d), (int)(3*d), 0, 0);
            addMessageAction(actions, "Copy", () -> copyResponse(message.getText().toString()));
            addMessageAction(actions, "Like", () -> saveResponseFeedback("like"));
            addMessageAction(actions, "Dislike", () -> saveResponseFeedback("dislike"));
            addMessageAction(actions, "Read", () -> speak(message.getText().toString()));
            addMessageAction(actions, "Share", () -> shareResponse(message.getText().toString()));
            addMessageAction(actions, "Retry", () -> { if (!lastUserPrompt.isEmpty()) handle(lastUserPrompt); });
            row.addView(actions, new LinearLayout.LayoutParams(-2, (int)(34*d)));
        }
        conversationList.addView(row,new LinearLayout.LayoutParams(-1,-2));
        conversationScroll.post(()->conversationScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageAction(LinearLayout parent, String title, Runnable action) {
        final float d = getResources().getDisplayMetrics().density;
        TextView button = new TextView(this);
        button.setText(title);
        button.setTextColor(Color.rgb(139, 171, 199));
        button.setTextSize(11);
        button.setGravity(Gravity.CENTER);
        button.setPadding((int)(7*d), 0, (int)(7*d), 0);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-2, -1));
    }

    private void copyResponse(String text) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS response", text == null ? "" : text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResponse(String text) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
        startActivity(Intent.createChooser(share, "Share JARVIS response"));
    }

    private void saveResponseFeedback(String type) {
        String key = "response_feedback_" + type;
        int count = memory.getInt(key, 0) + 1;
        memory.edit().putInt(key, count).apply();
        Toast.makeText(this, type.equals("like") ? "Thanks for the feedback" : "Feedback saved", Toast.LENGTH_SHORT).show();
    }
    @Override protected void onResume() {
        super.onResume();
        wakeReceiverActive = wakeReceiverRegistered;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && !WakeWordService.isRunning)
            startAlwaysOnWakeService();
        refreshUiStatus();
    }
    @Override protected void onPause() { wakeReceiverActive = false; super.onPause(); }
    @Override protected void onDestroy() { responseSequencer.invalidate(); if(activeAgentRun!=null) activeAgentRun.cancel(); if(activeAgentApprovalDialog!=null) activeAgentApprovalDialog.dismiss(); wakeReceiverActive=false; if(wakeReceiverRegistered && wakeReceiver!=null) { try { unregisterReceiver(wakeReceiver); } catch(Exception ignored) {} wakeReceiverRegistered=false; } if(recognizer!=null) { try { recognizer.cancel(); recognizer.destroy(); } catch(Exception ignored) {} } if(tts!=null) tts.shutdown(); super.onDestroy(); }
}