package com.jarvis.personalassistant;

/** Converts platform launch exceptions into an explicit, verifiable action result. */
final class JarvisActionResultGuard {
    interface Action { void run() throws Exception; }
    private JarvisActionResultGuard() {}

    static boolean run(Action action) {
        if (action == null) return false;
        try {
            action.run();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
