package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeWordMetricsTest {
    @Test public void detectsStalledAudioAndTracksEvents() {
        WakeWordMetrics m = new WakeWordMetrics();
        m.start(1000L,200L);
        m.onFrame(1100L);
        m.onWakeAccepted();
        m.onCandidateRejected();
        m.onAudioRecovery();
        assertFalse(m.isAudioStalled(2000L,1000L));
        assertTrue(m.isAudioStalled(2201L,1000L));
        String snapshot=m.snapshot(2201L,260L);
        assertTrue(snapshot.contains("frames=1"));
        assertTrue(snapshot.contains("accepted=1"));
        assertTrue(snapshot.contains("rejected=1"));
        assertTrue(snapshot.contains("recoveries=1"));
    }
}
