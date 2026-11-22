package com.jarvis.personalassistant;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Single-flight background indexing; failed requests leave rows pending for a later retry. */
public final class JarvisEmbeddingWorker {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private JarvisEmbeddingWorker() {}

    /** Legacy callers fail closed; indexing requires a live user-consent gate. */
    public static void indexPending(JarvisMemoryStore store, SecureStore secureStore, int maximum) {
        indexPending(store,secureStore,maximum,()->false);
    }

    public static void indexPending(JarvisMemoryStore store, SecureStore secureStore, int maximum, BooleanSupplier consent) {
        if (store == null || secureStore == null || consent == null || !consent.getAsBoolean() ||
                !RUNNING.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            try {
                if(!consent.getAsBoolean())return;
                String key = secureStore.get("gemini_key");
                if (key.isEmpty()) return;
                List<JarvisMemoryStore.MemoryRecord> records = store.getMemoriesMissingEmbedding(maximum);
                for (JarvisMemoryStore.MemoryRecord record : records) {
                    if(!consent.getAsBoolean())break;
                    float[] vector = JarvisEmbeddingClient.embed(key, record.content, false);
                    if (vector == null) break;
                    if(!consent.getAsBoolean())break;
                    store.putEmbedding(record.id, vector, JarvisEmbeddingClient.MODEL);
                    try { Thread.sleep(180L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } finally {
                RUNNING.set(false);
            }
        });
    }
}