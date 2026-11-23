package com.jarvis.personalassistant;

import java.util.concurrent.atomic.AtomicLong;

/** Prevents late asynchronous answers from overwriting a newer conversation turn. */
public final class JarvisResponseSequencer {
    private final AtomicLong generation = new AtomicLong();

    /** Starts a new foreground request and returns its identity token. */
    public long beginRequest() {
        return generation.incrementAndGet();
    }

    /** True only while the supplied request is still the newest conversation turn. */
    public boolean isCurrent(long token) {
        return token > 0 && generation.get() == token;
    }

    /** Invalidates all outstanding callbacks, for example when the Activity is destroyed. */
    public long invalidate() {
        return generation.incrementAndGet();
    }
}