package com.jarvis.personalassistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Offline-first private memory, conversation and task store. */
public final class JarvisMemoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "jarvis_long_term_memory.db";
    private static final int DB_VERSION = 2;
    private static final String MEMORIES = "memories";
    private static final String CONVERSATIONS = "conversations";
    private static final String TASKS = "tasks";

    public JarvisMemoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createMemories(db);
        createConversations(db);
        createTasks(db);
    }
    private void createMemories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memories (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated ON memories(updated_at DESC)");
    }
    private void createConversations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id INTEGER PRIMARY KEY AUTOINCREMENT, speaker TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_created ON conversations(created_at DESC)");
    }
    private void createTasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_created ON tasks(done, created_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migration only. Existing memories and conversations are never dropped.
        if (oldVersion < 2) createTasks(db);
    }

    public synchronized long saveMemory(String category, String content) {
        if (content == null || content.trim().isEmpty()) return -1;
        String clean = content.trim();
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM memories WHERE lower(content)=lower(?) LIMIT 1", new String[]{clean});
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
        } finally { cursor.close(); }
        ContentValues values = new ContentValues();
        values.put("category", category == null ? "general" : category);
        values.put("content", clean);
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insert("memories", null, values);
    }

    public synchronized boolean updateMemory(long id, String category, String content) {
        if (id <= 0 || content == null || content.trim().isEmpty()) return false;
        ContentValues v = new ContentValues();
        v.put("category", category == null ? "general" : category);
        v.put("content", content.trim());
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("memories", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }
    public synchronized boolean deleteMemory(long id) {
        return id > 0 && getWritableDatabase().delete("memories", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized void addConversation(String speaker, String content) {
        if (content == null || content.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("speaker", speaker == null ? "unknown" : speaker);
        v.put("content", content.trim());
        v.put("created_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insert("conversations", null, v);
        db.execSQL("DELETE FROM conversations WHERE id NOT IN (SELECT id FROM conversations ORDER BY created_at DESC LIMIT 2000)");
    }

    public synchronized String searchMemories(String query, int limit) {
        StringBuilder out = new StringBuilder();
        String q = query == null ? "" : query.trim();
        Cursor c;
        if (q.isEmpty()) {
            c = getReadableDatabase().rawQuery("SELECT id,category,content FROM memories ORDER BY updated_at DESC LIMIT ?", new String[]{String.valueOf(Math.max(1, limit))});
        } else {
            String[] words = q.split("\\s+");
            StringBuilder where = new StringBuilder();
            java.util.ArrayList<String> args = new java.util.ArrayList<>();
            for (String word : words) {
                if (word.length() < 2) continue;
                if (where.length() > 0) where.append(" OR ");
                where.append("content LIKE ? OR category LIKE ?");
                args.add("%" + word + "%"); args.add("%" + word + "%");
            }
            if (where.length() == 0) return "No matching saved memories.";
            args.add(String.valueOf(Math.max(1, limit)));
            c = getReadableDatabase().rawQuery("SELECT id,category,content FROM memories WHERE " + where + " ORDER BY updated_at DESC LIMIT ?", args.toArray(new String[0]));
        }
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.append("\n");
                out.append("#").append(c.getLong(0)).append(" [").append(c.getString(1)).append("] ").append(c.getString(2));
            }
        } finally { c.close(); }
        return out.length() == 0 ? "No matching saved memories." : out.toString();
    }

    public synchronized String getRecentConversation(int limit) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT speaker,content FROM conversations ORDER BY created_at DESC LIMIT ?", new String[]{String.valueOf(Math.max(1, limit))});
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.insert(0, "\n");
                out.insert(0, c.getString(0) + ": " + c.getString(1));
            }
        } finally { c.close(); }
        return out.toString();
    }
    public synchronized String getMemoryContext(String query, int limit) {
        String memories = searchMemories(query, limit);
        if (memories.startsWith("No matching")) memories = searchMemories("", Math.min(8, limit));
        String recent = getRecentConversation(8);
        StringBuilder result = new StringBuilder("SAVED LONG-TERM MEMORIES (local device):\n").append(memories);
        if (!recent.isEmpty()) result.append("\n\nRECENT CONVERSATION:\n").append(recent);
        return result.toString();
    }
    public synchronized String getAllMemoriesText() { return searchMemories("", 200); }
    public synchronized void clearMemories() { getWritableDatabase().delete("memories", null, null); }
    public synchronized void clearConversationHistory() { getWritableDatabase().delete("conversations", null, null); }
    public synchronized int getConversationCount() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM conversations",null);
        try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }
    public synchronized int getMemoryCount() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories",null);
        try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }

    public synchronized long addTask(String title, long dueAt) {
        if (title == null || title.trim().isEmpty()) return -1;
        ContentValues v=new ContentValues(); v.put("title",title.trim()); v.put("due_at",Math.max(0,dueAt));
        v.put("done",0); v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insert("tasks",null,v);
    }
    public synchronized String getTasksText(boolean includeDone) {
        StringBuilder out=new StringBuilder();
        Cursor c=getReadableDatabase().rawQuery("SELECT id,title,due_at,done FROM tasks "+(includeDone?"":"WHERE done=0 ")+"ORDER BY done ASC, CASE WHEN due_at=0 THEN created_at ELSE due_at END ASC LIMIT 100",null);
        try {
            while(c.moveToNext()) {
                if(out.length()>0) out.append("\n");
                out.append("#").append(c.getLong(0)).append(" ").append(c.getInt(3)==1?"[DONE] ":"[OPEN] ").append(c.getString(1));
                if(c.getLong(2)>0) out.append(" — ").append(new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))));
            }
        } finally { c.close(); }
        return out.length()==0?"Abhi koi task/reminder saved nahi hai.":out.toString();
    }
    public synchronized boolean completeTask(long id) {
        ContentValues v=new ContentValues(); v.put("done",1);
        return getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)})>0;
    }
    public synchronized boolean deleteTask(long id) {
        return id>0 && getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)})>0;
    }
}
