package com.jarvis.personalassistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Offline-first, temporal, multi-layer JARVIS memory.
 *
 * Layers: core/profile facts, semantic facts, procedural preferences, episodic
 * conversations/summaries, prospective tasks, a time-aware relation graph,
 * optional local vector storage, and user feedback.
 */
public final class JarvisMemoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "jarvis_long_term_memory.db";
    private static final int DB_VERSION = 6;
    private static final String MEMORIES = "memories";
    private static final String CONVERSATIONS = "conversations";
    private static final String TASKS = "tasks";
    private static final String NODES = "knowledge_nodes";
    private static final String EDGES = "knowledge_edges";

    public JarvisMemoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createMemories(db);
        createConversations(db);
        createTasks(db);
        createKnowledgeGraph(db);
        createAdvancedTables(db);
    }

    private void createMemories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memories (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, content TEXT NOT NULL, " +
            "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
            "memory_type TEXT NOT NULL DEFAULT 'semantic', memory_key TEXT NOT NULL DEFAULT '', " +
            "confidence REAL NOT NULL DEFAULT 0.75, importance INTEGER NOT NULL DEFAULT 50, " +
            "pinned INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 1, " +
            "valid_from INTEGER NOT NULL DEFAULT 0, valid_until INTEGER NOT NULL DEFAULT 0, " +
            "last_accessed INTEGER NOT NULL DEFAULT 0, access_count INTEGER NOT NULL DEFAULT 0, " +
            "source TEXT NOT NULL DEFAULT 'manual', expires_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated ON memories(updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_key_active ON memories(memory_key,is_active)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_type_active ON memories(memory_type,is_active)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_importance ON memories(pinned,importance DESC)");
    }

    private void createConversations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS conversations (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, speaker TEXT NOT NULL, content TEXT NOT NULL, " +
            "created_at INTEGER NOT NULL, session_id TEXT NOT NULL DEFAULT 'legacy', importance INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_created ON conversations(created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_session ON conversations(session_id,created_at)");
    }

    private void createTasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_created ON tasks(done, created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(done,due_at)");
    }

    private void createKnowledgeGraph(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_nodes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, node_type TEXT NOT NULL DEFAULT 'concept')");
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_edges (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, source_id INTEGER NOT NULL, target_id INTEGER NOT NULL, " +
            "relation TEXT NOT NULL, memory_id INTEGER NOT NULL, created_at INTEGER NOT NULL, " +
            "valid_from INTEGER NOT NULL DEFAULT 0, valid_until INTEGER NOT NULL DEFAULT 0, " +
            "confidence REAL NOT NULL DEFAULT 0.75, is_active INTEGER NOT NULL DEFAULT 1, " +
            "UNIQUE(source_id,target_id,relation,memory_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_source ON knowledge_edges(source_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_target ON knowledge_edges(target_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_memory ON knowledge_edges(memory_id)");
    }

    private void createAdvancedTables(SQLiteDatabase db) {
        ensureColumn(db, MEMORIES, "memory_type", "TEXT NOT NULL DEFAULT 'semantic'");
        ensureColumn(db, MEMORIES, "memory_key", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, MEMORIES, "confidence", "REAL NOT NULL DEFAULT 0.75");
        ensureColumn(db, MEMORIES, "importance", "INTEGER NOT NULL DEFAULT 50");
        ensureColumn(db, MEMORIES, "pinned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, MEMORIES, "is_active", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn(db, MEMORIES, "valid_from", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, MEMORIES, "valid_until", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, MEMORIES, "last_accessed", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, MEMORIES, "access_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, MEMORIES, "source", "TEXT NOT NULL DEFAULT 'manual'");
        ensureColumn(db, MEMORIES, "expires_at", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, CONVERSATIONS, "session_id", "TEXT NOT NULL DEFAULT 'legacy'");
        ensureColumn(db, CONVERSATIONS, "importance", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, EDGES, "valid_from", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, EDGES, "valid_until", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, EDGES, "confidence", "REAL NOT NULL DEFAULT 0.75");
        ensureColumn(db, EDGES, "is_active", "INTEGER NOT NULL DEFAULT 1");

        db.execSQL("CREATE TABLE IF NOT EXISTS memory_embeddings (" +
            "memory_id INTEGER PRIMARY KEY, model TEXT NOT NULL, dimensions INTEGER NOT NULL, " +
            "vector BLOB NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_sessions (" +
            "session_id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL, " +
            "turn_count INTEGER NOT NULL DEFAULT 0, summary TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_ai_reflections (session_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_summaries (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, summary_type TEXT NOT NULL, summary_key TEXT NOT NULL UNIQUE, " +
            "content TEXT NOT NULL, importance INTEGER NOT NULL DEFAULT 50, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_feedback (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, memory_id INTEGER NOT NULL, rating INTEGER NOT NULL, " +
            "query TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_local_vectors (" +
            "memory_id INTEGER PRIMARY KEY, model TEXT NOT NULL, dimensions INTEGER NOT NULL, " +
            "vector BLOB NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_summary_type ON memory_summaries(summary_type,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_memory ON memory_feedback(memory_id)");
        db.execSQL("UPDATE memories SET valid_from=created_at WHERE valid_from=0");
        db.execSQL("UPDATE knowledge_edges SET valid_from=created_at WHERE valid_from=0");
    }

    private void ensureColumn(SQLiteDatabase db, String table, String column, String definition) {
        boolean found = false;
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try { while (c.moveToNext()) if (column.equalsIgnoreCase(c.getString(1))) { found = true; break; } }
        finally { c.close(); }
        if (!found) db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migration only: existing facts, graph nodes, conversations and tasks stay intact.
        if (oldVersion < 2) createTasks(db);
        if (oldVersion < 3) {
            createKnowledgeGraph(db);
            Cursor c = db.rawQuery("SELECT id,content FROM memories", null);
            try { while (c.moveToNext()) indexMemoryGraph(db, c.getLong(0), c.getString(1)); }
            finally { c.close(); }
        }
        createAdvancedTables(db);
    }

    private boolean isSensitiveContent(String value) {
        return JarvisSensitiveDataFilter.isSensitiveText(value);
    }
    private long insertMemory(SQLiteDatabase db, String category, String type, String key,
            String content, double confidence, int importance, boolean pinned, String source) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("category", category == null ? "general" : category);
        v.put("content", content.trim());
        v.put("created_at", now);
        v.put("updated_at", now);
        v.put("memory_type", type == null ? "semantic" : type);
        v.put("memory_key", key == null ? "" : key);
        v.put("confidence", Math.max(0.0, Math.min(1.0, confidence)));
        v.put("importance", Math.max(0, Math.min(100, importance)));
        v.put("pinned", pinned ? 1 : 0);
        v.put("is_active", 1);
        v.put("valid_from", now);
        v.put("valid_until", 0);
        v.put("source", source == null ? "manual" : source);
        long id = db.insert(MEMORIES, null, v);
        if (id > 0) {
            indexMemoryGraph(db, id, content);
            putLocalVector(id, JarvisLocalVectorEngine.embed(content));
        }
        return id;
    }

    /** Save a manually supplied fact. Exact duplicates update their timestamp instead of multiplying. */
    public synchronized long saveMemory(String category, String content) {
        if (content == null || content.trim().isEmpty() || isSensitiveContent(content)) return -1;
        String clean = content.trim();
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM memories WHERE lower(content)=lower(?) AND is_active=1 LIMIT 1", new String[]{clean});
        try {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                ContentValues v = new ContentValues();
                v.put("category", category == null ? "general" : category);
                v.put("updated_at", now);
                db.update(MEMORIES, v, "id=?", new String[]{String.valueOf(id)});
                return id;
            }
        } finally { c.close(); }
        return insertMemory(db, category, "semantic", "", clean, 0.90, 60, false, "manual");
    }

    /**
     * Mem0-style fact upsert with Graphiti-style temporal history.
     * Replaced facts are retained as inactive historical versions, never silently overwritten.
     */
    public synchronized long upsertFact(String category, String type, String key, String content,
            double confidence, int importance, boolean pinned, String source) {
        if (content == null || content.trim().isEmpty() || isSensitiveContent(content)) return -1;
        String clean = content.trim();
        String memoryKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (memoryKey.isEmpty()) return saveMemory(category, clean);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        long result = -1;
        try {
            Cursor c = db.rawQuery("SELECT id,content FROM memories WHERE memory_key=? AND is_active=1 ORDER BY updated_at DESC",
                new String[]{memoryKey});
            boolean exact = false;
            try {
                while (c.moveToNext()) {
                    long oldId = c.getLong(0);
                    String oldContent = c.getString(1);
                    if (oldContent.equalsIgnoreCase(clean) && !exact) {
                        ContentValues update = new ContentValues();
                        update.put("category", category == null ? "general" : category);
                        update.put("memory_type", type == null ? "semantic" : type);
                        update.put("confidence", Math.max(0.0, Math.min(1.0, confidence)));
                        update.put("importance", Math.max(0, Math.min(100, importance)));
                        update.put("pinned", pinned ? 1 : 0);
                        update.put("updated_at", now);
                        update.put("source", source == null ? "auto" : source);
                        db.update(MEMORIES, update, "id=?", new String[]{String.valueOf(oldId)});
                        result = oldId;
                        exact = true;
                    } else {
                        ContentValues close = new ContentValues();
                        close.put("is_active", 0);
                        close.put("valid_until", now);
                        db.update(MEMORIES, close, "id=?", new String[]{String.valueOf(oldId)});
                        ContentValues edgeClose = new ContentValues();
                        edgeClose.put("is_active", 0);
                        edgeClose.put("valid_until", now);
                        db.update(EDGES, edgeClose, "memory_id=?", new String[]{String.valueOf(oldId)});
                    }
                }
            } finally { c.close(); }
            if (result <= 0) result = insertMemory(db, category, type, memoryKey, clean, confidence, importance, pinned, source);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return result;
    }

    public synchronized boolean updateMemory(long id, String category, String content) {
        if (id <= 0 || content == null || content.trim().isEmpty() || isSensitiveContent(content)) return false;
        String[] args = new String[]{String.valueOf(id)};
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            if (category != null && !category.trim().isEmpty()) v.put("category", category.trim());
            v.put("content", content.trim());
            v.put("updated_at", System.currentTimeMillis());
            v.put("valid_until", 0);
            v.put("is_active", 1);
            v.put("source", "manual-edit");
            boolean changed = db.update(MEMORIES, v, "id=?", args) > 0;
            if (changed) {
                db.delete(EDGES, "memory_id=?", args);
                db.delete("memory_embeddings", "memory_id=?", args);
                db.delete("memory_local_vectors", "memory_id=?", args);
                db.delete("memory_feedback", "memory_id=?", args);
                pruneOrphanKnowledgeNodes(db);
                indexMemoryGraph(db, id, content.trim());
                putLocalVector(id, JarvisLocalVectorEngine.embed(content.trim()));
            }
            db.setTransactionSuccessful();
            return changed;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean deleteMemory(long id) {
        if (id <= 0) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(id)};
            db.delete(EDGES, "memory_id=?", args);
            db.delete("memory_embeddings", "memory_id=?", args);
            db.delete("memory_local_vectors", "memory_id=?", args);
            db.delete("memory_feedback", "memory_id=?", args);
            boolean deleted = db.delete(MEMORIES, "id=?", args) > 0;
            pruneOrphanKnowledgeNodes(db);
            db.setTransactionSuccessful();
            return deleted;
        } finally { db.endTransaction(); }
    }

    public synchronized void setMemoryValidity(long id,long validFrom,long validUntil) {
        if(id<=0)return;
        ContentValues v=new ContentValues();
        if(validFrom>0)v.put("valid_from",validFrom);
        v.put("valid_until",Math.max(0,validUntil));
        if(validUntil>0&&validUntil<System.currentTimeMillis())v.put("is_active",0);
        else v.put("is_active",1);
        getWritableDatabase().update(MEMORIES,v,"id=?",new String[]{String.valueOf(id)});
    }

    public synchronized boolean setMemoryPinned(long id, boolean pinned) {
        ContentValues v = new ContentValues();
        v.put("pinned", pinned ? 1 : 0);
        v.put("importance", pinned ? 100 : 60);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update(MEMORIES, v, "id=? AND is_active=1", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized void recordMemoryFeedback(long id, int rating, String query) {
        if (id <= 0) return;
        int safeRating = rating < 0 ? -1 : (rating > 0 ? 1 : 0);
        ContentValues v = new ContentValues();
        v.put("memory_id", id);
        v.put("rating", safeRating);
        v.put("query", query == null ? "" : query);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("memory_feedback", null, v);
        if (safeRating > 0) getWritableDatabase().execSQL("UPDATE memories SET importance=MIN(100,importance+5) WHERE id=?", new Object[]{id});
        else if (safeRating < 0) getWritableDatabase().execSQL("UPDATE memories SET importance=MAX(0,importance-8) WHERE id=?", new Object[]{id});
    }

    private long node(SQLiteDatabase db, String name, String type) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return -1;
        Cursor c = db.rawQuery("SELECT id FROM knowledge_nodes WHERE name=? COLLATE NOCASE LIMIT 1", new String[]{clean});
        try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        ContentValues v = new ContentValues();
        v.put("name", clean);
        v.put("node_type", type == null ? "concept" : type);
        return db.insertWithOnConflict(NODES, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void addEdge(SQLiteDatabase db, long memoryId, String relation, String object) {
        if (object == null || object.trim().isEmpty()) return;
        String value = object.trim().replaceAll("[.!?]+$", "").trim();
        if (value.length() < 2 || value.length() > 180) return;
        long now = System.currentTimeMillis();
        long user = node(db, "Commander", "person");
        long target = node(db, value, "fact");
        if (user <= 0 || target <= 0) return;
        ContentValues edge = new ContentValues();
        edge.put("source_id", user);
        edge.put("target_id", target);
        edge.put("relation", relation);
        edge.put("memory_id", memoryId);
        edge.put("created_at", now);
        edge.put("valid_from", now);
        edge.put("confidence", 0.85);
        edge.put("is_active", 1);
        db.insertWithOnConflict(EDGES, null, edge, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Adds a provenance-linked edge between two named graph entities. */
    public synchronized void addGraphRelation(long memoryId,String subject,String relation,String object,
            double confidence,long validFrom,long validUntil) {
        if(memoryId<=0||object==null||object.trim().length()<2)return;
        SQLiteDatabase db=getWritableDatabase();
        String sourceName=(subject==null||subject.trim().isEmpty())?"Commander":subject.trim();
        String targetName=object.trim().replaceAll("[.!?]+$","").trim();
        if(targetName.length()>180)return;
        long source=node(db,sourceName,"entity"),target=node(db,targetName,"entity");
        if(source<=0||target<=0)return;
        ContentValues edge=new ContentValues();
        edge.put("source_id",source);edge.put("target_id",target);
        edge.put("relation",relation==null||relation.trim().isEmpty()?"related_to":relation.trim().toLowerCase(Locale.ROOT));
        edge.put("memory_id",memoryId);edge.put("created_at",System.currentTimeMillis());
        edge.put("valid_from",validFrom>0?validFrom:System.currentTimeMillis());
        edge.put("valid_until",Math.max(0,validUntil));
        edge.put("confidence",Math.max(0.0,Math.min(1.0,confidence)));edge.put("is_active",1);
        db.insertWithOnConflict(EDGES,null,edge,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void indexMemoryGraph(SQLiteDatabase db, long memoryId, String text) {
        if (text == null) return;
        String s = text.trim();
        String[][] patterns = {
            {"(?i)^(?:my name is|mera naam)\\s+(.+)$", "name"},
            {"(?i)^(?:i live in|main rehta hoon|main rehti hoon|mera ghar)\\s+(.+)$", "lives_in"},
            {"(?i)^(?:i am from|main .+ se hoon)\\s+(.+)$", "origin"},
            {"(?i)^(?:i work as|my job is|main kaam karta hoon|main kaam karti hoon)\\s+(.+)$", "works_as"},
            {"(?i)^(?:i like|i love|i prefer|mujhe pasand hai)\\s+(.+)$", "prefers"},
            {"(?i)^(?:my favourite|my favorite|mera favourite|meri favourite)\\s+(.+)$", "favourite"},
            {"(?i)^(?:my goal is|mera goal|my dream is)\\s+(.+)$", "goal"}
        };
        for (String[] p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p[0]).matcher(s);
            if (m.matches()) { addEdge(db, memoryId, p[1], m.group(1)); return; }
        }
        long user = node(db, "Commander", "person");
        long fact = node(db, s, "fact");
        if (user > 0 && fact > 0) {
            ContentValues edge = new ContentValues();
            edge.put("source_id", user); edge.put("target_id", fact);
            edge.put("relation", "remembers"); edge.put("memory_id", memoryId);
            edge.put("created_at", System.currentTimeMillis());
            edge.put("valid_from", System.currentTimeMillis());
            edge.put("confidence", 0.70); edge.put("is_active", 1);
            db.insertWithOnConflict(EDGES, null, edge, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    /** Conversation turns are grouped into sessions (30-minute idle boundary). */
    public synchronized void addConversation(String speaker, String content) {
        if (content == null || content.trim().isEmpty() || isSensitiveContent(content)) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            String sessionId = null;
            Cursor last = db.rawQuery("SELECT session_id,created_at FROM conversations ORDER BY created_at DESC,id DESC LIMIT 1", null);
            try {
                if (last.moveToFirst() && now - last.getLong(1) <= 30L * 60L * 1000L &&
                        !"legacy".equals(last.getString(0))) sessionId = last.getString(0);
            } finally { last.close(); }
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
                ContentValues session = new ContentValues();
                session.put("session_id", sessionId); session.put("started_at", now);
                session.put("ended_at", now); session.put("turn_count", 0); session.put("summary", "");
                db.insertWithOnConflict("memory_sessions", null, session, SQLiteDatabase.CONFLICT_IGNORE);
            }
            ContentValues v = new ContentValues();
            v.put("speaker", speaker == null ? "unknown" : speaker);
            v.put("content", content.trim());
            v.put("created_at", now);
            v.put("session_id", sessionId);
            db.insert(CONVERSATIONS, null, v);
            db.execSQL("UPDATE memory_sessions SET ended_at=?,turn_count=turn_count+1 WHERE session_id=?",
                new Object[]{now, sessionId});
            db.execSQL("DELETE FROM conversations WHERE id NOT IN (SELECT id FROM conversations ORDER BY created_at DESC,id DESC LIMIT 3000)");
            pruneOrphanSessions(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private String buildExtractiveSummary(String sessionId) {
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT content FROM conversations WHERE session_id=? AND speaker='Commander' ORDER BY created_at ASC LIMIT 80",
            new String[]{sessionId});
        List<String> candidates = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                String line = c.getString(0);
                if (line == null || line.trim().length() < 28) continue;
                String q = line.toLowerCase(Locale.ROOT);
                if (q.startsWith("memory ") || q.startsWith("task ") || q.startsWith("remind me")) continue;
                candidates.add(line.trim());
            }
        } finally { c.close(); }
        StringBuilder summary = new StringBuilder();
        int start = Math.max(0, candidates.size() - 5);
        for (int i = start; i < candidates.size(); i++) {
            String line = candidates.get(i);
            if (summary.length() + line.length() > 850) continue;
            if (summary.length() > 0) summary.append(" | ");
            summary.append(line);
        }
        return summary.toString();
    }

    /** Offline reflective consolidation: creates compact session digests, never deletes facts. */
    public synchronized int consolidateMemories() {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery(
            "SELECT session_id,turn_count FROM memory_sessions WHERE summary='' AND turn_count>=4 ORDER BY ended_at DESC LIMIT 100",
            null);
        List<String> ids = new ArrayList<>();
        try { while (c.moveToNext()) ids.add(c.getString(0)); } finally { c.close(); }
        int made = 0;
        for (String sessionId : ids) {
            String summary = buildExtractiveSummary(sessionId);
            if (summary.isEmpty()) continue;
            ContentValues sv = new ContentValues();
            sv.put("summary", summary);
            db.update("memory_sessions", sv, "session_id=?", new String[]{sessionId});
            ContentValues v = new ContentValues();
            v.put("summary_type", "session");
            v.put("summary_key", "session:" + sessionId);
            v.put("content", summary);
            v.put("importance", 55);
            v.put("created_at", System.currentTimeMillis());
            db.insertWithOnConflict("memory_summaries", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            made++;
        }
        // Gentle importance decay affects ranking only; no stored fact is automatically erased.
        db.execSQL("UPDATE memories SET importance=MAX(10,importance-1) WHERE pinned=0 AND is_active=1 AND updated_at<?",
            new Object[]{System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L});
        return made;
    }

    public static final class PendingSession {
        public final String sessionId,transcript;
        public PendingSession(String id,String text){sessionId=id;transcript=text;}
    }

    public synchronized List<PendingSession> getPendingReflectionSessions(int limit) {
        List<PendingSession> out=new ArrayList<>();
        long cutoff=System.currentTimeMillis()-20L*60L*1000L;
        Cursor sessions=getReadableDatabase().rawQuery(
            "SELECT s.session_id FROM memory_sessions s WHERE s.turn_count>=4 AND s.ended_at<=? " +
            "AND NOT EXISTS(SELECT 1 FROM memory_ai_reflections r WHERE r.session_id=s.session_id) " +
            "ORDER BY s.ended_at ASC LIMIT ?",
            new String[]{String.valueOf(cutoff),String.valueOf(Math.max(1,Math.min(10,limit)))});
        List<String> ids=new ArrayList<>();
        try{while(sessions.moveToNext())ids.add(sessions.getString(0));}finally{sessions.close();}
        for(String id:ids){
            StringBuilder transcript=new StringBuilder();
            Cursor turns=getReadableDatabase().rawQuery(
                "SELECT speaker,content FROM conversations WHERE session_id=? ORDER BY created_at ASC LIMIT 80",new String[]{id});
            try{while(turns.moveToNext()){
                if(transcript.length()>0)transcript.append("\n");
                transcript.append(turns.getString(0)).append(": ").append(turns.getString(1));
            }}finally{turns.close();}
            if(transcript.length()>0)out.add(new PendingSession(id,transcript.toString()));
        }
        return out;
    }

    public synchronized void saveAiSessionReflection(String sessionId,String summary) {
        if(sessionId==null||summary==null||summary.trim().isEmpty())return;
        String clean=summary.trim();ContentValues v=new ContentValues();v.put("summary",clean);
        getWritableDatabase().update("memory_sessions",v,"session_id=?",new String[]{sessionId});
        long now=System.currentTimeMillis();
        ContentValues marker=new ContentValues();marker.put("session_id",sessionId);marker.put("created_at",now);
        getWritableDatabase().insertWithOnConflict("memory_ai_reflections",null,marker,SQLiteDatabase.CONFLICT_REPLACE);
        ContentValues s=new ContentValues();s.put("summary_type","ai-session");s.put("summary_key","ai-session:"+sessionId);
        s.put("content",clean);s.put("importance",65);s.put("created_at",now);
        getWritableDatabase().insertWithOnConflict("memory_summaries",null,s,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String getSessionSummaryText(int limit) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT content FROM memory_summaries WHERE summary_type IN ('session','ai-session') ORDER BY created_at DESC LIMIT ?",
            new String[]{String.valueOf(Math.max(1, Math.min(10, limit)))});
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.append("\n");
                out.append("- ").append(c.getString(0));
            }
        } finally { c.close(); }
        return out.toString();
    }

    /** Hybrid lexical/temporal/priority recall, optionally fused with real stored vectors. */
    public synchronized String searchMemories(String query, int limit) {
        return searchMemoriesInternal(query, limit, false, null);
    }

    public synchronized String searchMemoriesWithEmbedding(String query, int limit, float[] queryVector) {
        return searchMemoriesInternal(query, limit, false, queryVector);
    }

    public synchronized String searchMemoryHistory(String query, int limit) {
        return searchMemoriesInternal(query, limit, true, null);
    }

    private String searchMemoriesInternal(String query, int limit, boolean includeInactive, float[] queryVector) {
        return searchMemoriesInternal(query,limit,includeInactive,queryVector,null);
    }

    public synchronized String searchMemoriesDuring(String query,int limit,long startAt,long endAt) {
        JarvisTimeQuery.Window w=new JarvisTimeQuery.Window(startAt,endAt,"requested time range",query==null?"":query);
        return searchMemoriesInternal(query,limit,false,null,w);
    }

    private String searchMemoriesInternal(String query,int limit,boolean includeInactive,float[] queryVector,JarvisTimeQuery.Window timeWindow) {
        String q=query==null?"":query.trim();
        SQLiteDatabase db=getReadableDatabase();
        expireTransientMemories();
        StringBuilder where=new StringBuilder();
        List<String> args=new ArrayList<>();
        if(!includeInactive)where.append("m.is_active=1");
        if(timeWindow!=null){
            if(where.length()>0)where.append(" AND ");
            where.append("m.valid_from<=? AND (m.valid_until=0 OR m.valid_until>=?)");
            args.add(String.valueOf(timeWindow.endAt));args.add(String.valueOf(timeWindow.startAt));
        }
        String sql="SELECT m.id,m.category,m.content,m.updated_at,m.memory_type,m.importance,m.confidence,m.pinned,m.is_active,m.valid_until,m.access_count,e.vector,lv.vector " +
            "FROM memories m LEFT JOIN memory_embeddings e ON e.memory_id=m.id " +
            "LEFT JOIN memory_local_vectors lv ON lv.memory_id=m.id " +
            (where.length()>0?"WHERE "+where+" ":"")+"ORDER BY m.pinned DESC,m.updated_at DESC LIMIT 3000";
        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));
        float[] localQueryVector=JarvisLocalVectorEngine.embed(q);
        List<MemoryHit> hits=new ArrayList<>();
        long now = System.currentTimeMillis();
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String category = c.getString(1), content = c.getString(2);
                int importance = c.getInt(5), pinned = c.getInt(7), active = c.getInt(8);
                double confidence = c.getDouble(6);
                int lexical = q.isEmpty() ? 1 : JarvisSemanticEngine.score(q, content, category);
                double semantic=0.0;
                byte[] cloudBlob=c.getBlob(11),localBlob=c.getBlob(12);
                if(queryVector!=null&&cloudBlob!=null)
                    semantic=Math.max(semantic,JarvisEmbeddingClient.cosineSimilarity(queryVector,decodeVector(cloudBlob)));
                if(localBlob!=null)
                    semantic=Math.max(semantic,JarvisLocalVectorEngine.similarity(localQueryVector,decodeVector(localBlob)));
                if(!q.isEmpty()&&lexical<=0&&semantic<0.18)continue;
                long updated = c.getLong(3);
                double ageDays = Math.max(0, (now - updated) / 86400000.0);
                double recency = 12.0 / (1.0 + ageDays / 30.0);
                double score = lexical + Math.max(0.0, semantic) * 120.0 +
                    importance * 0.30 + confidence * 8.0 + recency +
                    Math.min(10, c.getInt(10) * 0.25) + (pinned == 1 ? 45.0 : 0.0) -
                    (active == 1 ? 0.0 : 18.0);
                hits.add(new MemoryHit(id, category, content, updated, score,
                    c.getString(4), pinned == 1, active == 1, c.getLong(9)));
            }
        } finally { c.close(); }
        Collections.sort(hits, (a, b) -> Double.compare(b.score, a.score));
        int max = Math.max(1, Math.min(limit, 100));
        StringBuilder out = new StringBuilder();
        int count = 0;
        List<Long> touched = new ArrayList<>();
        for (MemoryHit h : hits) {
            if (count++ >= max) break;
            if (out.length() > 0) out.append("\n");
            out.append("#").append(h.id).append(" [").append(h.type).append("/").append(h.category).append("]");
            if (h.pinned) out.append(" [PINNED]");
            if (!h.active) out.append(" [HISTORICAL]");
            out.append(" ").append(h.content);
            touched.add(h.id);
        }
        if (!touched.isEmpty()) {
            ContentValues access = new ContentValues();
            access.put("last_accessed", now);
            for (Long id : touched) db.execSQL("UPDATE memories SET last_accessed=?,access_count=access_count+1 WHERE id=?",
                new Object[]{now, id});
        }
        return out.length() == 0 ? "No matching saved memories." : out.toString();
    }

    private static final class MemoryHit {
        final long id, updatedAt, validUntil;
        final String category, content, type;
        final double score;
        final boolean pinned, active;
        MemoryHit(long id, String category, String content, long updatedAt, double score,
                String type, boolean pinned, boolean active, long validUntil) {
            this.id=id; this.category=category; this.content=content; this.updatedAt=updatedAt;
            this.score=score; this.type=type; this.pinned=pinned; this.active=active; this.validUntil=validUntil;
        }
    }

    public synchronized String getPinnedMemoriesText(int limit) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT id,category,content FROM memories WHERE pinned=1 AND is_active=1 ORDER BY importance DESC,updated_at DESC LIMIT ?",
            new String[]{String.valueOf(Math.max(1, Math.min(30, limit)))});
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.append("\n");
                out.append("#").append(c.getLong(0)).append(" [").append(c.getString(1)).append("] ").append(c.getString(2));
            }
        } finally { c.close(); }
        return out.toString();
    }

    public synchronized String getMemoryHistoryText(String query) {
        String q = query == null ? "" : query.trim();
        Cursor c = q.isEmpty()
            ? getReadableDatabase().rawQuery("SELECT id,content,valid_from,valid_until,is_active FROM memories ORDER BY updated_at DESC LIMIT 80", null)
            : getReadableDatabase().rawQuery("SELECT id,content,valid_from,valid_until,is_active FROM memories WHERE content LIKE ? OR memory_key LIKE ? ORDER BY updated_at DESC LIMIT 80",
                new String[]{"%" + q + "%", "%" + q + "%"});
        StringBuilder out = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.append("\n");
                out.append("#").append(c.getLong(0)).append(c.getInt(4)==1?" [CURRENT] ":" [PAST] ")
                    .append(c.getString(1)).append(" | since ").append(df.format(new Date(c.getLong(2))));
                if (c.getLong(3)>0) out.append(" until ").append(df.format(new Date(c.getLong(3))));
            }
        } finally { c.close(); }
        return out.length()==0 ? "No memory history found." : out.toString();
    }

    public synchronized String getMemoryStatsText() {
        SQLiteDatabase db = getReadableDatabase();
        int total = count(db, "SELECT COUNT(*) FROM memories");
        int active = count(db, "SELECT COUNT(*) FROM memories WHERE is_active=1");
        int historical = count(db, "SELECT COUNT(*) FROM memories WHERE is_active=0");
        int pinned = count(db, "SELECT COUNT(*) FROM memories WHERE pinned=1 AND is_active=1");
        int vectors = count(db, "SELECT COUNT(*) FROM memory_embeddings");
        int localVectors = count(db, "SELECT COUNT(*) FROM memory_local_vectors");
        int sessions = count(db, "SELECT COUNT(*) FROM memory_sessions");
        int summaries = count(db, "SELECT COUNT(*) FROM memory_summaries");
        int turns = count(db, "SELECT COUNT(*) FROM conversations");
        int tasks = count(db, "SELECT COUNT(*) FROM tasks WHERE done=0");
        int orphanCloud = count(db, "SELECT COUNT(*) FROM memory_embeddings e LEFT JOIN memories m ON m.id=e.memory_id WHERE m.id IS NULL");
        int orphanLocal = count(db, "SELECT COUNT(*) FROM memory_local_vectors v LEFT JOIN memories m ON m.id=v.memory_id WHERE m.id IS NULL");
        int orphanFeedback = count(db, "SELECT COUNT(*) FROM memory_feedback f LEFT JOIN memories m ON m.id=f.memory_id WHERE m.id IS NULL");
        int orphanEdges = count(db, "SELECT COUNT(*) FROM knowledge_edges e LEFT JOIN memories m ON m.id=e.memory_id WHERE e.memory_id>0 AND m.id IS NULL");
        int orphanSessions = count(db, "SELECT COUNT(*) FROM memory_sessions s LEFT JOIN conversations c ON c.session_id=s.session_id WHERE c.session_id IS NULL");
        return "JARVIS MEMORY HEALTH\nActive facts: "+active+"\nHistorical facts: "+historical+
            "\nPinned core facts: "+pinned+"\nCloud neural vectors: "+vectors+"\nOffline feature vectors (not neural): "+localVectors+
            "\nConversation turns: "+turns+"\nSessions: "+sessions+"\nSession digests: "+summaries+
            "\nOpen tasks/reminders: "+tasks+"\nTotal stored fact versions: "+total+
            "\nOrphan indexes (cloud/local/feedback/edges): "+orphanCloud+"/"+orphanLocal+"/"+orphanFeedback+"/"+orphanEdges+
            "\nSessions without conversation turns: "+orphanSessions+
            "\nStorage: on-device SQLite (offline-first)";
    }

    /** Safe repair for derived rows whose source records no longer exist. */
    public synchronized int cleanupMemoryOrphans() {
        expireTransientMemories();
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        int removed=0;
        try{
            removed+=db.delete("memory_embeddings","memory_id NOT IN (SELECT id FROM memories)",null);
            removed+=db.delete("memory_local_vectors","memory_id NOT IN (SELECT id FROM memories)",null);
            removed+=db.delete("memory_feedback","memory_id NOT IN (SELECT id FROM memories)",null);
            removed+=db.delete(EDGES,"memory_id>0 AND memory_id NOT IN (SELECT id FROM memories)",null);
            int before=count(db,"SELECT COUNT(*) FROM memory_sessions");
            pruneOrphanSessions(db);
            int after=count(db,"SELECT COUNT(*) FROM memory_sessions");
            removed+=Math.max(0,before-after);
            pruneOrphanKnowledgeNodes(db);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return removed;
    }

    private int count(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** Multi-hop graph traversal (up to three hops) over current temporal edges. */
    public synchronized String getKnowledgeGraphText(String query) {
        String q=query==null?"":query.trim();
        SQLiteDatabase db=getReadableDatabase();
        List<Long> frontier=new ArrayList<>();
        if(q.isEmpty()){
            Cursor c=db.rawQuery("SELECT id FROM knowledge_nodes WHERE name='Commander' COLLATE NOCASE LIMIT 1",null);
            try{if(c.moveToFirst())frontier.add(c.getLong(0));}finally{c.close();}
        }else{
            Cursor seeds=db.rawQuery("SELECT id FROM knowledge_nodes WHERE name LIKE ? ORDER BY length(name) LIMIT 20",new String[]{"%"+q+"%"});
            try{while(seeds.moveToNext())frontier.add(seeds.getLong(0));}finally{seeds.close();}
            if(frontier.isEmpty())for(String term:JarvisSemanticEngine.terms(q)){
                Cursor c=db.rawQuery("SELECT id FROM knowledge_nodes WHERE name LIKE ? LIMIT 10",new String[]{"%"+term+"%"});
                try{while(c.moveToNext()){long id=c.getLong(0);if(!frontier.contains(id))frontier.add(id);}}finally{c.close();}
            }
        }
        StringBuilder out=new StringBuilder();
        java.util.HashSet<Long> visited=new java.util.HashSet<>(),seenEdges=new java.util.HashSet<>();
        long now=System.currentTimeMillis();int written=0;
        for(int depth=0;depth<3&&!frontier.isEmpty()&&written<80;depth++){
            List<Long> next=new ArrayList<>();
            for(Long nodeId:frontier){
                if(!visited.add(nodeId))continue;
                Cursor edges=db.rawQuery(
                    "SELECT e.id,e.source_id,e.target_id,e.relation,s.name,t.name,e.confidence FROM knowledge_edges e "+
                    "JOIN knowledge_nodes s ON s.id=e.source_id JOIN knowledge_nodes t ON t.id=e.target_id "+
                    "WHERE e.is_active=1 AND e.valid_from<=? AND (e.valid_until=0 OR e.valid_until>=?) AND (e.source_id=? OR e.target_id=?) "+
                    "ORDER BY e.confidence DESC,e.created_at DESC LIMIT 50",
                    new String[]{String.valueOf(now),String.valueOf(now),String.valueOf(nodeId),String.valueOf(nodeId)});
                try{while(edges.moveToNext()&&written<80){
                    long edgeId=edges.getLong(0);if(!seenEdges.add(edgeId))continue;
                    long source=edges.getLong(1),target=edges.getLong(2);
                    boolean forward=source==nodeId;
                    String a=forward?edges.getString(4):edges.getString(5);
                    String b=forward?edges.getString(5):edges.getString(4);
                    if(out.length()>0)out.append("\n");
                    out.append(a).append(" —").append(edges.getString(3)).append("→ ").append(b)
                        .append(" [confidence ").append(String.format(Locale.US,"%.2f",edges.getDouble(6))).append("]");
                    written++;long other=forward?target:source;
                    if(!visited.contains(other)&&!next.contains(other))next.add(other);
                }}finally{edges.close();}
            }
            frontier=next;
        }
        return out.length()==0?"No linked knowledge found.":out.toString();
    }

    public synchronized String getRecentConversation(int limit) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT speaker,content FROM conversations ORDER BY created_at DESC,id DESC LIMIT ?",
            new String[]{String.valueOf(Math.max(1, Math.min(200, limit)))});
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.insert(0, "\n");
                out.insert(0, c.getString(0) + ": " + c.getString(1));
            }
        } finally { c.close(); }
        return out.toString();
    }

    public synchronized String getMemoryContext(String query, int limit) {
        return getMemoryContext(query, limit, null);
    }

    public synchronized String getMemoryContext(String query, int limit, float[] queryVector) {
        JarvisTimeQuery.Window timeWindow=JarvisTimeQuery.parse(query);
        String scopedQuery=timeWindow==null?query:timeWindow.query;
        String memories=timeWindow!=null?searchMemoriesInternal(scopedQuery,limit,false,queryVector,timeWindow):
            (queryVector==null?searchMemories(query,limit):searchMemoriesWithEmbedding(query,limit,queryVector));
        if(memories.startsWith("No matching")&&timeWindow==null)memories=searchMemories("",Math.min(6,limit));
        String pinned = getPinnedMemoriesText(6);
        String graph = getKnowledgeGraphText(query);
        String summaries = getSessionSummaryText(3);
        String recent = getRecentConversation(12);
        StringBuilder result = new StringBuilder(
            "JARVIS MEMORY CONTEXT. Treat explicit facts as evidence; do not invent missing personal details. " +
            "Prefer CURRENT facts over historical versions. If recall is uncertain, say you do not have that detail.\n");
        if(timeWindow!=null)result.append("\nTIME SCOPE: ").append(timeWindow.label).append(" (facts overlapping this period).");
        if (!pinned.isEmpty()) result.append("\nCORE / PINNED PROFILE:\n").append(pinned);
        if (!memories.startsWith("No matching")) result.append("\n\nRELEVANT LONG-TERM FACTS:\n").append(memories);
        if (!graph.startsWith("No linked")) result.append("\n\nRELATED KNOWLEDGE GRAPH:\n").append(graph);
        if (!summaries.isEmpty()) result.append("\n\nPAST SESSION DIGESTS:\n").append(summaries);
        if (!recent.isEmpty()) result.append("\n\nRECENT EPISODIC CONTEXT:\n").append(recent);
        // Keep the profile/relevant-fact prefix and newest conversation tail when trimming.
        return JarvisPromptBudget.fit("", result.toString(), 8500).input;
    }

    public synchronized String getAllMemoriesText() { return searchMemories("", 100); }

    /** Remove every fact and every dependent index atomically; chat history is a separate control. */
    public synchronized void clearMemories() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(EDGES, null, null);
            db.delete(NODES, null, null);
            db.delete(MEMORIES, null, null);
            db.delete("memory_embeddings", null, null);
            db.delete("memory_local_vectors", null, null);
            db.delete("memory_feedback", null, null);
            db.delete("memory_summaries", null, null);
            db.delete("memory_ai_reflections", null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** Conversation turns, session transcripts/digests and reflection markers form one retention unit. */
    public synchronized void clearConversationHistory() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(CONVERSATIONS, null, null);
            db.delete("memory_sessions", null, null);
            db.delete("memory_ai_reflections", null, null);
            db.delete("memory_summaries", "summary_type IN ('session','ai-session')", null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void pruneOrphanSessions(SQLiteDatabase db) {
        db.execSQL("DELETE FROM memory_sessions WHERE session_id NOT IN (SELECT DISTINCT session_id FROM conversations)");
        db.execSQL("DELETE FROM memory_ai_reflections WHERE session_id NOT IN (SELECT session_id FROM memory_sessions)");
        db.execSQL("DELETE FROM memory_summaries WHERE summary_type IN ('session','ai-session') " +
            "AND summary_key NOT IN (SELECT 'session:'||session_id FROM memory_sessions) " +
            "AND summary_key NOT IN (SELECT 'ai-session:'||session_id FROM memory_sessions)");
    }

    private void pruneOrphanKnowledgeNodes(SQLiteDatabase db) {
        db.execSQL("DELETE FROM knowledge_nodes WHERE id NOT IN " +
            "(SELECT source_id FROM knowledge_edges UNION SELECT target_id FROM knowledge_edges)");
    }

    public synchronized int getConversationCount() { return count(getReadableDatabase(), "SELECT COUNT(*) FROM conversations"); }
    public synchronized int getMemoryCount() { return count(getReadableDatabase(), "SELECT COUNT(*) FROM memories WHERE is_active=1"); }

    public static final class MemoryRecord {
        public final long id;
        public final String category;
        public final String content;
        public MemoryRecord(long id, String category, String content) {
            this.id=id; this.category=category; this.content=content;
        }
    }

    public synchronized List<MemoryRecord> getMemoriesMissingEmbedding(int limit) {
        List<MemoryRecord> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT m.id,m.category,m.content FROM memories m LEFT JOIN memory_embeddings e ON e.memory_id=m.id " +
            "WHERE m.is_active=1 AND e.memory_id IS NULL ORDER BY m.pinned DESC,m.importance DESC,m.updated_at DESC LIMIT ?",
            new String[]{String.valueOf(Math.max(1, Math.min(500, limit)))});
        try { while (c.moveToNext()) out.add(new MemoryRecord(c.getLong(0),c.getString(1),c.getString(2))); }
        finally { c.close(); }
        return out;
    }

    public synchronized void putEmbedding(long memoryId, float[] vector, String model) {
        if (memoryId <= 0 || vector == null || vector.length == 0 || vector.length > 4096) return;
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        double norm = 0;
        for (float f : vector) norm += f * f;
        float scale = norm > 0 ? (float)(1.0 / Math.sqrt(norm)) : 1.0f;
        for (float f : vector) buffer.putFloat(f * scale);
        ContentValues v = new ContentValues();
        v.put("memory_id", memoryId); v.put("model", model == null ? "unknown" : model);
        v.put("dimensions", vector.length); v.put("vector", buffer.array());
        v.put("updated_at", System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            Cursor exists=db.rawQuery("SELECT 1 FROM memories WHERE id=? AND is_active=1",
                new String[]{String.valueOf(memoryId)});
            boolean valid;
            try{valid=exists.moveToFirst();}finally{exists.close();}
            if(valid)db.insertWithOnConflict("memory_embeddings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            else db.delete("memory_embeddings","memory_id=?",new String[]{String.valueOf(memoryId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private float[] decodeVector(byte[] blob) {
        if (blob == null || blob.length == 0 || blob.length % 4 != 0) return null;
        ByteBuffer b = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[blob.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return v;
    }

    public synchronized void putLocalVector(long memoryId,float[] vector) {
        if(memoryId<=0||vector==null||vector.length==0||vector.length>4096)return;
        ByteBuffer b=ByteBuffer.allocate(vector.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for(float f:vector)b.putFloat(f);
        ContentValues v=new ContentValues();v.put("memory_id",memoryId);v.put("model",JarvisLocalVectorEngine.MODEL);
        v.put("dimensions",vector.length);v.put("vector",b.array());v.put("updated_at",System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            Cursor exists=db.rawQuery("SELECT 1 FROM memories WHERE id=? AND is_active=1",
                new String[]{String.valueOf(memoryId)});
            boolean valid;
            try{valid=exists.moveToFirst();}finally{exists.close();}
            if(valid)db.insertWithOnConflict("memory_local_vectors",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            else db.delete("memory_local_vectors","memory_id=?",new String[]{String.valueOf(memoryId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized int indexMissingLocalVectors(int limit) {
        Cursor c=getReadableDatabase().rawQuery(
            "SELECT m.id,m.content FROM memories m LEFT JOIN memory_local_vectors v ON v.memory_id=m.id WHERE v.memory_id IS NULL ORDER BY m.pinned DESC,m.importance DESC LIMIT ?",
            new String[]{String.valueOf(Math.max(1,Math.min(1000,limit)))});
        List<MemoryRecord> rows=new ArrayList<>();
        try{while(c.moveToNext())rows.add(new MemoryRecord(c.getLong(0),"local",c.getString(1)));}finally{c.close();}
        for(MemoryRecord row:rows)putLocalVector(row.id,JarvisLocalVectorEngine.embed(row.content));
        return rows.size();
    }
    public synchronized int getLocalVectorCount(){return count(getReadableDatabase(),"SELECT COUNT(*) FROM memory_local_vectors");}
    public synchronized String getProfileSummaryText(){
        StringBuilder out=new StringBuilder();
        Cursor c=getReadableDatabase().rawQuery("SELECT memory_key,content,confidence FROM memories WHERE is_active=1 AND memory_key LIKE 'profile.%' ORDER BY pinned DESC,importance DESC LIMIT 30",null);
        try{while(c.moveToNext()){
            if(out.length()>0)out.append("\n");
            out.append(c.getString(0)).append(": ").append(c.getString(1))
                .append(" (confidence ").append(String.format(Locale.US,"%.2f",c.getDouble(2))).append(")");
        }}finally{c.close();}
        return out.length()==0?"No structured profile facts yet.":out.toString();
    }
    /** Save a temporary note that is automatically purged after its requested TTL. */
    public synchronized long addTransientMemory(String content,long ttlMillis) {
        if(content==null||content.trim().isEmpty()||ttlMillis<=0||ttlMillis>365L*24L*60L*60L*1000L||isSensitiveContent(content))return -1;
        SQLiteDatabase db=getWritableDatabase();
        long id=insertMemory(db,"temporary","transient","","Temporary note: "+content.trim(),0.65,20,false,"user-temporary");
        if(id>0){
            ContentValues v=new ContentValues();v.put("expires_at",System.currentTimeMillis()+ttlMillis);
            db.update(MEMORIES,v,"id=?",new String[]{String.valueOf(id)});
        }
        return id;
    }

    /** Only explicitly transient records expire; stable facts are never auto-erased. */
    public synchronized int expireTransientMemories(){
        SQLiteDatabase db=getWritableDatabase();long now=System.currentTimeMillis();
        Cursor c=db.rawQuery("SELECT id FROM memories WHERE memory_type='transient' AND expires_at>0 AND expires_at<=?",new String[]{String.valueOf(now)});
        List<String> ids=new ArrayList<>();try{while(c.moveToNext())ids.add(String.valueOf(c.getLong(0)));}finally{c.close();}
        if(ids.isEmpty())return 0;
        db.beginTransaction();
        try{
            for(String id:ids){
                String[] args=new String[]{id};
                db.delete(EDGES,"memory_id=?",args);
                db.delete("memory_embeddings","memory_id=?",args);
                db.delete("memory_local_vectors","memory_id=?",args);
                db.delete("memory_feedback","memory_id=?",args);
                db.delete(MEMORIES,"id=?",args);
            }
            pruneOrphanKnowledgeNodes(db);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return ids.size();
    }

    public synchronized int getEmbeddingCount() { return count(getReadableDatabase(), "SELECT COUNT(*) FROM memory_embeddings"); }
    public synchronized void clearEmbeddings() { SQLiteDatabase db=getWritableDatabase(); db.delete("memory_embeddings",null,null); db.delete("memory_local_vectors",null,null); }

    public static final class ReminderInfo {
        public final long id, dueAt;
        public final String title;
        public ReminderInfo(long id, String title, long dueAt) { this.id=id; this.title=title; this.dueAt=dueAt; }
    }

    public synchronized List<ReminderInfo> getUpcomingReminders() {
        List<ReminderInfo> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT id,title,due_at FROM tasks WHERE done=0 AND due_at>? ORDER BY due_at ASC LIMIT 100",
            new String[]{String.valueOf(System.currentTimeMillis())});
        try { while (c.moveToNext()) out.add(new ReminderInfo(c.getLong(0),c.getString(1),c.getLong(2))); }
        finally { c.close(); }
        return out;
    }

    public synchronized long addTask(String title, long dueAt) {
        if (title == null || title.trim().isEmpty()) return -1;
        ContentValues v = new ContentValues();
        v.put("title", title.trim()); v.put("due_at", Math.max(0, dueAt));
        v.put("done", 0); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("tasks", null, v);
    }

    public synchronized String getTasksText(boolean includeDone) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT id,title,due_at,done FROM tasks " + (includeDone ? "" : "WHERE done=0 ") +
            "ORDER BY done ASC, CASE WHEN due_at=0 THEN created_at ELSE due_at END ASC LIMIT 100", null);
        try {
            while (c.moveToNext()) {
                if (out.length() > 0) out.append("\n");
                out.append("#").append(c.getLong(0)).append(c.getInt(3)==1?" [DONE] ":" [OPEN] ").append(c.getString(1));
                if (c.getLong(2)>0) out.append(" — ").append(new SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(new Date(c.getLong(2))));
            }
        } finally { c.close(); }
        return out.length()==0 ? "Abhi koi task/reminder saved nahi hai." : out.toString();
    }

    public synchronized boolean completeTask(long id) {
        ContentValues v = new ContentValues(); v.put("done", 1);
        return getWritableDatabase().update("tasks", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized boolean deleteTask(long id) {
        return id > 0 && getWritableDatabase().delete("tasks", "id=?", new String[]{String.valueOf(id)}) > 0;
    }
}