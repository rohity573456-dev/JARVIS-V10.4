package com.jarvis.personalassistant;

import android.app.Activity;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 34)
public class JarvisAndroidToolAdapterTest {
    @Test public void agentCanCreateAndListAReversibleLocalReminder() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JarvisMemoryStore store = new JarvisMemoryStore(RuntimeEnvironment.getApplication());
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        JarvisToolRegistry registry = new JarvisToolRegistry();
        new JarvisAndroidToolAdapter(activity, (context, id, title, dueAt) -> true).registerInto(registry);
        Map<String,String> args = new HashMap<>();
        args.put("title", "JARVIS agent reminder test");
        args.put("delay_minutes", "5");

        JarvisToolRegistry.ToolResult created = registry.invoke("create_reminder", args);
        assertTrue(created.output, created.success);
        assertTrue(created.output.contains("JARVIS agent reminder test"));
        assertEquals(JarvisToolRegistry.Risk.REVERSIBLE, registry.get("create_reminder").risk);
        List<JarvisMemoryStore.ReminderInfo> reminders = store.getUpcomingReminders();
        JarvisMemoryStore.ReminderInfo createdReminder = null;
        for (JarvisMemoryStore.ReminderInfo item : reminders) {
            if ("JARVIS agent reminder test".equals(item.title)) createdReminder = item;
        }
        assertNotNull(createdReminder);

        JarvisToolRegistry.ToolResult listed = registry.invoke("list_reminders", Collections.<String,String>emptyMap());
        assertTrue(listed.success);
        assertTrue(listed.output.contains("JARVIS agent reminder test"));
        assertEquals(JarvisToolRegistry.Risk.READ_ONLY, registry.get("list_reminders").risk);

        Map<String,String> taskArgs = new HashMap<>();
        taskArgs.put("id", String.valueOf(createdReminder.id));
        assertEquals(JarvisToolRegistry.Risk.SENSITIVE, registry.get("complete_task").risk);
        JarvisToolRegistry.ToolResult completed = registry.invoke("complete_task", taskArgs);
        assertTrue(completed.output, completed.success);
        assertFalse(store.getTasksText(false).contains("JARVIS agent reminder test"));
        assertTrue(store.getTasksText(true).contains("[DONE] JARVIS agent reminder test"));

        assertEquals(JarvisToolRegistry.Risk.SENSITIVE, registry.get("delete_task").risk);
        JarvisToolRegistry.ToolResult deleted = registry.invoke("delete_task", taskArgs);
        assertTrue(deleted.output, deleted.success);
        assertFalse(store.getTasksText(true).contains("JARVIS agent reminder test"));

        store.deleteTask(createdReminder.id);
        store.close();
        activity.finish();
    }

    @Test public void missingNotificationPermissionPreventsReminderPersistence() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JarvisMemoryStore store = new JarvisMemoryStore(RuntimeEnvironment.getApplication());
        JarvisToolRegistry registry = new JarvisToolRegistry();
        new JarvisAndroidToolAdapter(activity, (context, id, title, dueAt) -> true).registerInto(registry);
        Map<String,String> args = new HashMap<>();
        args.put("title", "Permission denied reminder test");
        args.put("delay_minutes", "5");

        JarvisToolRegistry.ToolResult result = registry.invoke("create_reminder", args);
        assertFalse(result.success);
        assertTrue(result.output.contains("Notification permission is off"));
        assertTrue(store.getUpcomingReminders().stream().noneMatch(r -> "Permission denied reminder test".equals(r.title)));
        store.close();
        activity.finish();
    }

    @Test public void invalidReminderDelayDoesNotPersistATask() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JarvisMemoryStore store = new JarvisMemoryStore(RuntimeEnvironment.getApplication());
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        JarvisToolRegistry registry = new JarvisToolRegistry();
        new JarvisAndroidToolAdapter(activity).registerInto(registry);
        Map<String,String> args = new HashMap<>();
        args.put("title", "Invalid reminder");
        args.put("delay_minutes", "0");

        JarvisToolRegistry.ToolResult result = registry.invoke("create_reminder", args);
        assertFalse(result.success);
        assertTrue(store.getUpcomingReminders().stream().noneMatch(r -> "Invalid reminder".equals(r.title)));
        store.close();
        activity.finish();
    }
}