package com.jarvis.personalassistant;

/** Lightweight counters for diagnosing wake listening reliability and CPU cost. */
final class WakeWordMetrics {
    private long startedAtMs;
    private long lastFrameAtMs;
    private long frames;
    private long acceptedWakes;
    private long rejectedCandidates;
    private long audioRecoveries;
    private long cpuStartedMs;
    private boolean started;

    synchronized void start(long elapsedRealtimeMs, long processCpuMs) {
        startedAtMs = elapsedRealtimeMs;
        lastFrameAtMs = elapsedRealtimeMs;
        cpuStartedMs = processCpuMs;
        frames = acceptedWakes = rejectedCandidates = audioRecoveries = 0L;
        started = true;
    }

    synchronized void onFrame(long elapsedRealtimeMs) {
        frames++;
        lastFrameAtMs = elapsedRealtimeMs;
    }

    synchronized void onWakeAccepted() { acceptedWakes++; }
    synchronized void onCandidateRejected() { rejectedCandidates++; }
    synchronized void onAudioRecovery() { audioRecoveries++; }

    synchronized boolean isAudioStalled(long nowMs, long timeoutMs) {
        return started && nowMs - lastFrameAtMs > timeoutMs;
    }

    synchronized String snapshot(long nowMs, long processCpuMs) {
        long elapsed = started ? Math.max(0L, nowMs - startedAtMs) : 0L;
        long cpu = started ? Math.max(0L, processCpuMs - cpuStartedMs) : 0L;
        return "listenMs=" + elapsed + ",frames=" + frames
                + ",accepted=" + acceptedWakes + ",rejected=" + rejectedCandidates
                + ",recoveries=" + audioRecoveries + ",cpuMs=" + cpu;
    }
}
