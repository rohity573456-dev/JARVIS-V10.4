package com.jarvis.personalassistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local, offline-first long-term memory and conversation store for JARVIS.
 * Only explicit "remember" requests are promoted to long-term memories.
 * Conversation turns are stored separately and can be cleared by the user.
 */
public final class JarvisMemoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "jarvis_long_term_memory.db";
    private static final int DB_VERSION = 1;
    private static final String MEMORIES = "memories";
    private static final String CONVERSATIONS = "conversations";

    public JarvisMemoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + MEMORIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "category TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_memories_updated ON " + MEMORIES + "(updated_at DESC)");
        db.execSQL("CREATE TABLE " + CONVERSATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "speaker TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_conversations_created ON " + CONVERSATIONS + "(created_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema migrations belong here. Never drop user memory during an upgrade.
    }

    public synchronized long saveMemory(String category, String content) {
        if (content == null || content.trim().isEmpty()) return -1;
        String clean = content.trim();
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM " + MEMORIES +
                " WHERE lower(content)=lower(?) LIMIT 1", new String[]{clean});
        try {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                ContentValues update = new ContentValues();
                update.put("category", category == null ? "general" : category);
                update.put("content", clean);
                update.put("updated_at", now);
                db.update(MEMORIES, update, "id=?", new String[]{String.valueOf(id)});
                return id;
            }
        } finally {
            cursor.close();
        }
        ContentValues values = new ContentValues();
        values.put("category", category == null ? "general" : category);
        values.put("content", clean);
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insert(MEMORIES, null, values);
    }

    public synchronized void addConversation(String speaker, String content) {
        if (content == null || content.trim().isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("speaker", speaker == null ? "unknown" : speaker);
        values.put("content", content.trim());
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert(CONVERSATIONS, null, values);
        // Keep a bounded local history so the database cannot grow without limit.
        getWritableDatabase().execSQL("DELETE FROM " + CONVERSATIONS +
                " WHERE id NOT IN (SELECT id FROM " + CONVERSATIONS +
                " ORDER BY created_at DESC LIMIT 2000)");
    }

    public synchronized String searchMemories(String query, int limit) {
        StringBuilder out = new StringBuilder();
        String q = query == null ? "" : query.trim();
        Cursor c;
        if (q.isEmpty()) {
            c = getReadableDatabase().rawQuery("SELECT category,content FROM " + MEMORIES +
                    " ORDER BY updated_at DESC LIMIT ?", new String[]{String.valueOf(Math.max(1, limit))});
        } else {
            c = getReadableDatabase().rawQuery("SELECT category,content FROM " + MEMORIES +
                    " WHERE content LIKE ? OR category LIKE ? ORDER BY updated_at DESC LIMIT ?",
                    new String[]{"%" + q + "%", "%" + q + "%", String.valueOf(Math.max(1, limit))});
        }
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.append("\n");
                out.append("• [").append(c.getString(0)).append("] ").append(c.getString(1));
            }
        } finally {
            c.close();
        }
        return out.length() == 0 ? "No matching saved memories." : out.toString();
    }

    public synchronized String getRecentConversation(int limit) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT speaker,content FROM " + CONVERSATIONS +
                " ORDER BY created_at DESC LIMIT ?", new String[]{String.valueOf(Math.max(1, limit))});
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.insert(0, "\n");
                out.insert(0, c.getString(0) + ": " + c.getString(1));
            }
        } finally {
            c.close();
        }
        return out.toString();
    }

    public synchronized String getMemoryContext(String query, int limit) {
        String memories = searchMemories(query, limit);
        if (memories.startsWith("No matching")) memories = searchMemories("", Math.min(8, limit));
        String recent = getRecentConversation(6);
        StringBuilder result = new StringBuilder("SAVED LONG-TERM MEMORIES:\n").append(memories);
        if (!recent.isEmpty()) result.append("\n\nRECENT CONVERSATION:\n").append(recent);
        return result.toString();
    }

    public synchronized String getAllMemoriesText() {
        return searchMemories("", 100);
    }

    public synchronized void clearMemories() {
        getWritableDatabase().delete(MEMORIES, null, null);
    }

    public synchronized void clearConversationHistory() {
        getWritableDatabase().delete(CONVERSATIONS, null, null);
    }

    public synchronized int getMemoryCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + MEMORIES, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }
}
