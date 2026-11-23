package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisResponseSequencerTest {
    @Test public void newestRequestInvalidatesOlderCallbacks() {
        JarvisResponseSequencer sequencer = new JarvisResponseSequencer();
        long first = sequencer.beginRequest();
        assertTrue(sequencer.isCurrent(first));
        long second = sequencer.beginRequest();
        assertFalse(sequencer.isCurrent(first));
        assertTrue(sequencer.isCurrent(second));
    }

    @Test public void activityInvalidationRejectsOutstandingCallbacks() {
        JarvisResponseSequencer sequencer = new JarvisResponseSequencer();
        long request = sequencer.beginRequest();
        sequencer.invalidate();
        assertFalse(sequencer.isCurrent(request));
        assertFalse(sequencer.isCurrent(0));
    }
}