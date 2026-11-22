package com.jarvis.personalassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.List;

/** One source of truth for scheduling and restoring local prospective-memory reminders. */
public final class JarvisReminderScheduler {
    private JarvisReminderScheduler() {}

    public static boolean schedule(Context context, long id, String title, long dueAt) {
        try {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return false;
            Intent intent = new Intent(context, JarvisReminderReceiver.class);
            intent.putExtra("reminder_title", title);
            intent.putExtra("reminder_id", (int)id);
            PendingIntent pending = PendingIntent.getBroadcast(context, requestCode(id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static void cancel(Context context, long id) {
        try {
            Intent intent = new Intent(context, JarvisReminderReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(context, requestCode(id), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) {
                AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (alarms != null) alarms.cancel(pending);
                pending.cancel();
            }
        } catch (Exception ignored) {}
    }

    public static void restore(Context context) {
        JarvisMemoryStore store = new JarvisMemoryStore(context);
        List<JarvisMemoryStore.ReminderInfo> reminders = store.getUpcomingReminders();
        for (JarvisMemoryStore.ReminderInfo reminder : reminders)
            schedule(context, reminder.id, reminder.title, reminder.dueAt);
        store.close();
    }

    private static int requestCode(long id) { return (int)(id & 0x7fffffff); }
}