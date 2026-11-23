package com.jarvis.personalassistant;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small, explicit Android adapter for the Phase 2 agent.
 *
 * Only the tools registered here can cause platform side effects. UI and
 * framework calls are marshalled onto the main thread. Network-facing actions
 * are tagged EXTERNAL so JarvisAgentEngine requires explicit approval.
 */
public final class JarvisAndroidToolAdapter {
    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 8L;
    private final Activity activity;

    public JarvisAndroidToolAdapter(Activity activity) {
        if (activity == null) throw new IllegalArgumentException("Activity is required");
        this.activity = activity;
    }

    public void registerInto(JarvisToolRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("Tool registry is required");

        registry.register(new JarvisToolRegistry.ToolSpec(
            "get_battery", "Read the current battery percentage.", JarvisToolRegistry.Risk.READ_ONLY,
            none(), none(), "", args -> onMain(() -> {
                Intent battery = activity.registerReceiver(null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery == null) return JarvisToolRegistry.ToolResult.failure("Battery status unavailable.");
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level < 0 || scale <= 0) return JarvisToolRegistry.ToolResult.failure("Battery level unavailable.");
                int percent = Math.round(level * 100f / scale);
                return JarvisToolRegistry.ToolResult.success("Battery is " + percent + " percent.");
            })
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "open_settings", "Open one supported Android settings panel.", JarvisToolRegistry.Risk.REVERSIBLE,
            setOf("panel"), none(), "", args -> onMain(() -> {
                String panel = args.get("panel");
                Intent intent;
                if ("wifi".equals(panel)) intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                else if ("bluetooth".equals(panel)) intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                else if ("display".equals(panel)) intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                else if ("sound".equals(panel)) intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
                else if ("general".equals(panel)) intent = new Intent(Settings.ACTION_SETTINGS);
                else return JarvisToolRegistry.ToolResult.failure("Unsupported settings panel. Allowed: wifi, bluetooth, display, sound, general.");
                if (intent.resolveActivity(activity.getPackageManager()) == null)
                    return JarvisToolRegistry.ToolResult.failure("This settings panel is not available on this device.");
                activity.startActivity(intent);
                return JarvisToolRegistry.ToolResult.success("Opened " + panel + " settings.");
            })
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "open_app", "Open an installed app by its exact Android package name.", JarvisToolRegistry.Risk.REVERSIBLE,
            setOf("package"), none(), "", args -> onMain(() -> {
                String packageName = args.get("package");
                if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
                    return JarvisToolRegistry.ToolResult.failure("Invalid Android package name.");
                Intent launch = activity.getPackageManager().getLaunchIntentForPackage(packageName);
                if (launch == null) return JarvisToolRegistry.ToolResult.failure("No launchable installed app found for that package.");
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launch);
                return JarvisToolRegistry.ToolResult.success("Opened app " + packageName + ".");
            })
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "set_media_volume", "Raise, lower, or mute media volume by one step.", JarvisToolRegistry.Risk.REVERSIBLE,
            setOf("direction"), none(), "", args -> onMain(() -> {
                String direction = args.get("direction");
                AudioManager audio = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
                if (audio == null) return JarvisToolRegistry.ToolResult.failure("Audio service unavailable.");
                if ("up".equals(direction)) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                else if ("down".equals(direction)) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                else if ("mute".equals(direction) && android.os.Build.VERSION.SDK_INT >= 23)
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                else return JarvisToolRegistry.ToolResult.failure("Direction must be up, down, or mute; mute requires Android 6+.");
                return JarvisToolRegistry.ToolResult.success("Media volume action applied: " + direction + ".");
            })
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "get_local_datetime", "Read the device's current local date, time and timezone.",
            JarvisToolRegistry.Risk.READ_ONLY, none(), none(), "", args ->
                JarvisToolRegistry.ToolResult.success(
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.getDefault())
                        .format(new java.util.Date()))
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "list_reminders", "Read the user's upcoming local reminders and tasks.",
            JarvisToolRegistry.Risk.READ_ONLY, none(), none(), "", args -> {
                JarvisMemoryStore store = new JarvisMemoryStore(activity);
                try {
                    return JarvisToolRegistry.ToolResult.success(store.getTasksText(false));
                } finally { store.close(); }
            }
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "create_reminder", "Create a local reminder after an explicit user request.",
            JarvisToolRegistry.Risk.REVERSIBLE, setOf("title", "delay_minutes"), none(), "", args -> {
                String title = args.get("title");
                String delayRaw = args.get("delay_minutes");
                long delay;
                try { delay = Long.parseLong(delayRaw == null ? "" : delayRaw.trim()); }
                catch (NumberFormatException e) { return JarvisToolRegistry.ToolResult.failure("delay_minutes must be a whole number."); }
                if (title == null || title.trim().isEmpty() || title.trim().length() > 160)
                    return JarvisToolRegistry.ToolResult.failure("Reminder title must contain 1-160 characters.");
                if (delay < 1 || delay > 10080)
                    return JarvisToolRegistry.ToolResult.failure("Reminder delay must be between 1 minute and 7 days.");
                if (android.os.Build.VERSION.SDK_INT >= 33
                        && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    return JarvisToolRegistry.ToolResult.failure("Notification permission is off. Enable JARVIS notifications in Android Settings; no reminder was saved.");
                long dueAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delay);
                android.content.Context appContext = activity.getApplicationContext();
                JarvisMemoryStore store = new JarvisMemoryStore(appContext);
                try {
                    long id = store.addTask(title.trim(), dueAt);
                    if (id <= 0) return JarvisToolRegistry.ToolResult.failure("Reminder could not be saved.");
                    if (!JarvisReminderScheduler.schedule(appContext, id, title.trim(), dueAt)) {
                        store.deleteTask(id);
                        return JarvisToolRegistry.ToolResult.failure("Reminder alarm could not be scheduled; no task was kept.");
                    }
                    return JarvisToolRegistry.ToolResult.success("Reminder saved for " +
                        new java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                            .format(new java.util.Date(dueAt)) + ": " + title.trim());
                } finally { store.close(); }
            }
        ));

        registry.register(new JarvisToolRegistry.ToolSpec(
            "web_search", "Open a web search for the supplied query.", JarvisToolRegistry.Risk.EXTERNAL,
            setOf("query"), none(), "", args -> onMain(() -> {
                String query = args.get("query");
                if (query == null || query.trim().isEmpty() || query.length() > 300)
                    return JarvisToolRegistry.ToolResult.failure("Search query must contain 1-300 characters.");
                Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim())));
                if (browser.resolveActivity(activity.getPackageManager()) == null)
                    return JarvisToolRegistry.ToolResult.failure("No browser available to open web search.");
                activity.startActivity(browser);
                return JarvisToolRegistry.ToolResult.success("Opened web search for: " + query.trim());
            })
        ));
    }

    private JarvisToolRegistry.ToolResult onMain(Callable<JarvisToolRegistry.ToolResult> action) {
        if (Thread.currentThread() == activity.getMainLooper().getThread()) {
            try { return action.call(); }
            catch (SecurityException e) { return JarvisToolRegistry.ToolResult.failure("Permission denied."); }
            catch (Exception e) { return JarvisToolRegistry.ToolResult.failure("Android action failed: " + e.getClass().getSimpleName()); }
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JarvisToolRegistry.ToolResult> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    result.set(JarvisToolRegistry.ToolResult.failure("JARVIS activity is no longer available."));
                } else result.set(action.call());
            } catch (Exception e) { error.set(e); }
            finally { latch.countDown(); }
        });
        try {
            if (!latch.await(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                return JarvisToolRegistry.ToolResult.failure("Timed out waiting for Android main thread.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return JarvisToolRegistry.ToolResult.failure("Android action was interrupted.");
        }
        if (error.get() != null) return JarvisToolRegistry.ToolResult.failure("Android action failed: " + error.get().getClass().getSimpleName());
        return result.get() == null ? JarvisToolRegistry.ToolResult.failure("Android action returned no result.") : result.get();
    }

    private static Set<String> none() { return Collections.emptySet(); }
    private static Set<String> setOf(String... items) {
        Set<String> values = new HashSet<>();
        if (items != null) Collections.addAll(values, items);
        return values;
    }
}
