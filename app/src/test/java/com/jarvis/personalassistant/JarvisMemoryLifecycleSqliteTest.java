package com.jarvis.personalassistant;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class JarvisMemoryLifecycleSqliteTest {
    private Context context;
    private JarvisMemoryStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("jarvis_long_term_memory.db");
        store = new JarvisMemoryStore(context);
        store.getWritableDatabase();
    }

    @After public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase("jarvis_long_term_memory.db");
    }

    private int count(String table) {
        Cursor c = store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    private int countWhere(String table, String where) {
        Cursor c = store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    private String onlySessionId() {
        Cursor c = store.getReadableDatabase().rawQuery("SELECT session_id FROM memory_sessions LIMIT 1", null);
        try { assertTrue(c.moveToFirst()); return c.getString(0); }
        finally { c.close(); }
    }

    private void addFeedback(long memoryId) {
        ContentValues v = new ContentValues();
        v.put("memory_id", memoryId); v.put("rating", 1); v.put("query", "test"); v.put("created_at", System.currentTimeMillis());
        assertTrue(store.getWritableDatabase().insert("memory_feedback", null, v) > 0);
    }

    @Test public void sensitiveContentNeverEntersManualMemoryOrConversationHistory() {
        assertEquals(-1, store.saveMemory("personal", "My Aadhaar number is 123456789012"));
        assertEquals(-1, store.saveMemory("personal", "My PAN is ABCDE1234F"));
        store.addConversation("USER", "My medical diagnosis is diabetes");
        store.addConversation("USER", "My bank account number is 123456789");
        assertEquals(0, store.getMemoryCount());
        assertEquals(0, store.getConversationCount());

        long safe = store.saveMemory("preference", "I prefer short answers");
        assertTrue(safe > 0);
        assertEquals(1, store.getMemoryCount());
    }

    @Test public void supersedingFactRetainsHistoryButRemovesStaleVectors() {
        long oldId = store.upsertFact("profile", "semantic", "profile.city",
            "I live in Deoria", 0.9, 80, true, "ai");
        assertTrue(oldId > 0);
        store.putEmbedding(oldId, new float[]{0.2f, 0.4f}, "test-model");
        store.recordMemoryFeedback(oldId, 1, "city fact");
        assertEquals(1, count("memory_local_vectors", oldId));
        assertEquals(1, count("memory_embeddings", oldId));

        long newId = store.upsertFact("profile", "semantic", "profile.city",
            "I live in Mumbai", 0.95, 85, true, "ai");
        assertTrue(newId > 0);
        assertNotEquals(oldId, newId);
        assertEquals(0, count("memory_local_vectors", oldId));
        assertEquals(0, count("memory_embeddings", oldId));
        assertEquals(1, count("memory_local_vectors", newId));

        android.database.Cursor oldFact = store.getWritableDatabase().rawQuery(
            "SELECT is_active,valid_until FROM memories WHERE id=?", new String[]{String.valueOf(oldId)});
        try {
            assertTrue(oldFact.moveToFirst());
            assertEquals(0, oldFact.getInt(0));
            assertTrue(oldFact.getLong(1) > 0);
        } finally { oldFact.close(); }
        assertTrue(store.getMemoryContext("city", 10).contains("I live in Mumbai"));
        assertFalse(store.getMemoryContext("city", 10).contains("I live in Deoria"));
    }

    @Test public void editingMemoryAtomicallyRebuildsIndexesAndPreservesCategory() {
        long id = store.saveMemory("preference", "I prefer detailed answers");
        assertTrue(id > 0);
        store.putLocalVector(id, JarvisLocalVectorEngine.embed("I prefer detailed answers"));
        store.putEmbedding(id, new float[]{0.1f, 0.2f}, "test-model");
        store.recordMemoryFeedback(id, 1, "detailed answers");
        assertEquals(1, count("memory_local_vectors", id));
        assertEquals(1, count("memory_embeddings", id));
        assertEquals(1, count("memory_feedback", id));
        assertTrue(store.updateMemory(id, null, "I prefer concise answers"));

        android.database.Cursor fact = store.getWritableDatabase().rawQuery(
            "SELECT category,content FROM memories WHERE id=?", new String[]{String.valueOf(id)});
        try {
            assertTrue(fact.moveToFirst());
            assertEquals("preference", fact.getString(0));
            assertEquals("I prefer concise answers", fact.getString(1));
        } finally { fact.close(); }
        assertEquals(0, count("memory_embeddings", id));
        assertEquals(0, count("memory_feedback", id));
        assertEquals(1, count("memory_local_vectors", id));
    }

    private int count(String table, long id) {
        android.database.Cursor c = store.getWritableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + table + " WHERE memory_id=?", new String[]{String.valueOf(id)});
        try { c.moveToFirst(); return c.getInt(0); }
        finally { c.close(); }
    }

    @Test public void deletingMemoryRemovesEveryDependentIndexAtomically() {
        long id = store.saveMemory("project", "JARVIS SQLite lifecycle test fact");
        assertTrue(id > 0);
        assertEquals(1, countWhere("memory_local_vectors", "memory_id=" + id));
        addFeedback(id);
        store.putEmbedding(id, new float[]{1f, 0.5f, 0.25f}, "test-model");
        assertEquals(1, countWhere("memory_embeddings", "memory_id=" + id));

        assertTrue(store.deleteMemory(id));
        assertEquals(0, countWhere("memories", "id=" + id));
        assertEquals(0, countWhere("memory_local_vectors", "memory_id=" + id));
        assertEquals(0, countWhere("memory_embeddings", "memory_id=" + id));
        assertEquals(0, countWhere("memory_feedback", "memory_id=" + id));
        assertEquals(0, countWhere("knowledge_edges", "memory_id=" + id));
        assertEquals(0, store.getMemoryCount());
    }

    @Test public void lateEmbeddingResultsCannotRecreateRowsAfterDeletion() {
        long id = store.saveMemory("project", "late vector result test");
        assertTrue(id > 0);
        assertTrue(store.deleteMemory(id));

        store.putEmbedding(id, new float[]{0.1f, 0.2f}, "late-cloud-model");
        store.putLocalVector(id, new float[]{0.3f, 0.4f});

        assertEquals(0, countWhere("memory_embeddings", "memory_id=" + id));
        assertEquals(0, countWhere("memory_local_vectors", "memory_id=" + id));
    }

    @Test public void expiringTransientMemoryRemovesVectorsFeedbackAndFact() {
        long id = store.addTransientMemory("temporary SQLite lifecycle note", 60_000L);
        assertTrue(id > 0);
        addFeedback(id);
        store.putEmbedding(id, new float[]{0.4f, 0.6f}, "test-model");

        ContentValues expired = new ContentValues();
        expired.put("expires_at", System.currentTimeMillis() - 1L);
        assertEquals(1, store.getWritableDatabase().update("memories", expired, "id=?", new String[]{String.valueOf(id)}));

        assertEquals(1, store.expireTransientMemories());
        assertEquals(0, countWhere("memories", "id=" + id));
        assertEquals(0, countWhere("memory_local_vectors", "memory_id=" + id));
        assertEquals(0, countWhere("memory_embeddings", "memory_id=" + id));
        assertEquals(0, countWhere("memory_feedback", "memory_id=" + id));
        assertEquals(0, store.expireTransientMemories());
    }

    @Test public void clearHistoryRemovesSessionsAndDigestsButKeepsFacts() {
        long factId = store.saveMemory("preference", "Keep this durable preference");
        store.addConversation("USER", "First turn in a test session");
        String sessionId = onlySessionId();

        ContentValues reflection = new ContentValues();
        reflection.put("session_id", sessionId); reflection.put("created_at", System.currentTimeMillis());
        store.getWritableDatabase().insert("memory_ai_reflections", null, reflection);
        ContentValues summary = new ContentValues();
        summary.put("summary_type", "session"); summary.put("summary_key", "session:" + sessionId);
        summary.put("content", "test digest"); summary.put("importance", 50); summary.put("created_at", System.currentTimeMillis());
        store.getWritableDatabase().insert("memory_summaries", null, summary);

        store.clearConversationHistory();

        assertEquals(0, store.getConversationCount());
        assertEquals(0, count("memory_sessions"));
        assertEquals(0, count("memory_ai_reflections"));
        assertEquals(0, countWhere("memory_summaries", "summary_type IN ('session','ai-session')"));
        assertEquals(1, store.getMemoryCount());
        assertTrue(countWhere("memories", "id=" + factId) == 1);
    }

    @Test public void clearMemoriesRemovesFactsAndIndexesButPreservesChatHistory() {
        long id = store.saveMemory("project", "A saved fact to clear");
        store.addConversation("USER", "Conversation that should remain");
        assertTrue(id > 0);
        assertEquals(1, store.getConversationCount());

        store.clearMemories();

        assertEquals(0, store.getMemoryCount());
        assertEquals(0, count("knowledge_edges"));
        assertEquals(0, count("knowledge_nodes"));
        assertEquals(0, count("memory_embeddings"));
        assertEquals(0, count("memory_local_vectors"));
        assertEquals(0, count("memory_feedback"));
        assertEquals(1, store.getConversationCount());
    }

    @Test public void orphanRepairRemovesDanglingIndexesAndSessionMetadata() {
        SQLiteDatabase db = store.getWritableDatabase();
        db.execSQL("INSERT INTO memory_embeddings(memory_id,model,dimensions,vector,updated_at) VALUES(9001,'orphan',1,X'00000000',1)");
        db.execSQL("INSERT INTO memory_local_vectors(memory_id,model,dimensions,vector,updated_at) VALUES(9001,'orphan',1,X'00000000',1)");
        db.execSQL("INSERT INTO memory_feedback(memory_id,rating,query,created_at) VALUES(9001,1,'orphan',1)");
        db.execSQL("INSERT INTO knowledge_nodes(name,node_type) VALUES('orphan-source','concept')");
        db.execSQL("INSERT INTO knowledge_nodes(name,node_type) VALUES('orphan-target','concept')");
        long source = scalarLong("SELECT id FROM knowledge_nodes WHERE name='orphan-source'");
        long target = scalarLong("SELECT id FROM knowledge_nodes WHERE name='orphan-target'");
        db.execSQL("INSERT INTO knowledge_edges(source_id,target_id,relation,memory_id,created_at) VALUES(?,?, 'related_to',9001,1)",
            new Object[]{source, target});
        ContentValues session = new ContentValues();
        session.put("session_id", "orphan-session"); session.put("started_at", 1L);
        session.put("ended_at", 1L); session.put("turn_count", 0); session.put("summary", "");
        db.insert("memory_sessions", null, session);
        ContentValues digest = new ContentValues();
        digest.put("summary_type", "session"); digest.put("summary_key", "session:orphan-session");
        digest.put("content", "orphan"); digest.put("importance", 1); digest.put("created_at", 1L);
        db.insert("memory_summaries", null, digest);

        int removed = store.cleanupMemoryOrphans();

        assertTrue(removed >= 5);
        assertEquals(0, count("memory_embeddings"));
        assertEquals(0, count("memory_local_vectors"));
        assertEquals(0, count("memory_feedback"));
        assertEquals(0, count("knowledge_edges"));
        assertEquals(0, count("knowledge_nodes"));
        assertEquals(0, count("memory_sessions"));
        assertEquals(0, countWhere("memory_summaries", "summary_type IN ('session','ai-session')"));
    }

    private long scalarLong(String sql) {
        Cursor c = store.getReadableDatabase().rawQuery(sql, null);
        try { assertTrue(c.moveToFirst()); return c.getLong(0); }
        finally { c.close(); }
    }
}
