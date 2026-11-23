package com.jarvis.personalassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Delivers locally scheduled JARVIS reminders without a network connection. */
public final class JarvisReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "jarvis_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String title = intent == null ? null : intent.getStringExtra("reminder_title");
        if (title == null || title.trim().isEmpty()) title = "You have a reminder.";
        int notificationId = intent == null ? (int) System.currentTimeMillis()
            : intent.getIntExtra("reminder_id", (int) System.currentTimeMillis());

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "JARVIS reminders",
                NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Locally scheduled reminders from JARVIS");
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, notificationId, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL_ID)
            : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("JARVIS REMINDER")
            .setContentText(title)
            .setStyle(new Notification.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setContentIntent(content);
        if (Build.VERSION.SDK_INT < 26) builder.setPriority(Notification.PRIORITY_HIGH);
        try { manager.notify(notificationId, builder.build()); }
        catch (SecurityException ignored) {
            // Notification permission may be revoked between scheduling and delivery.
        }
    }
}
