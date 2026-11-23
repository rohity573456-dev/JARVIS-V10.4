package com.jarvis.personalassistant;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Single-flight background indexing; failed requests leave rows pending for a later retry. */
public final class JarvisEmbeddingWorker {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1),
        new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "Jarvis-Embedding-Index");
                thread.setDaemon(true);
                return thread;
            }
        }, new ThreadPoolExecutor.AbortPolicy());
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private JarvisEmbeddingWorker() {}

    /** Legacy callers fail closed; indexing requires a live user-consent gate. */
    public static void indexPending(JarvisMemoryStore store, SecureStore secureStore, int maximum) {
        indexPending(store,secureStore,maximum,()->false);
    }

    public static void indexPending(JarvisMemoryStore store, SecureStore secureStore, int maximum, BooleanSupplier consent) {
        if (store == null || secureStore == null || consent == null || !consent.getAsBoolean() ||
                !RUNNING.compareAndSet(false, true)) return;
        try {
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
        } catch (RejectedExecutionException overloaded) {
            RUNNING.set(false);
            android.util.Log.w("JARVIS-MEMORY", "Embedding worker is busy; pending rows will be retried later.");
        }
    }
}