package com.jarvis.personalassistant;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-flight background indexing; failed requests leave rows pending for a later retry. */
public final class JarvisEmbeddingWorker {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private JarvisEmbeddingWorker() {}

    public static void indexPending(JarvisMemoryStore store, SecureStore secureStore, int maximum) {
        if (store == null || secureStore == null || !RUNNING.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            try {
                String key = secureStore.get("gemini_key");
                if (key.isEmpty()) return;
                List<JarvisMemoryStore.MemoryRecord> records = store.getMemoriesMissingEmbedding(maximum);
                for (JarvisMemoryStore.MemoryRecord record : records) {
                    float[] vector = JarvisEmbeddingClient.embed(key, record.content, false);
                    if (vector == null) break;
                    store.putEmbedding(record.id, vector, JarvisEmbeddingClient.MODEL);
                    try { Thread.sleep(180L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } finally {
                RUNNING.set(false);
            }
        });
    }
}