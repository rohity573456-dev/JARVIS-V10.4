package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeWordConfidenceGateTest {
    @Test
    public void rejectsSingleHighConfidenceSpike() {
        WakeWordConfidenceGate gate = new WakeWordConfidenceGate();
        assertFalse(gate.add(0.46f));
        assertFalse(gate.add(0.08f));
        assertFalse(gate.add(0.09f));
        assertFalse(gate.add(0.07f));
    }

    @Test
    public void acceptsTwoConsecutiveStrongFrames() {
        WakeWordConfidenceGate gate = new WakeWordConfidenceGate();
        assertFalse(gate.add(0.12f));
        assertFalse(gate.add(0.31f));
        assertTrue(gate.add(0.32f));
    }

    @Test
    public void acceptsConsistentModerateFrames() {
        WakeWordConfidenceGate gate = new WakeWordConfidenceGate();
        assertFalse(gate.add(0.24f));
        assertFalse(gate.add(0.26f));
        assertFalse(gate.add(0.25f));
        assertTrue(gate.add(0.24f));
    }

    @Test
    public void resetClearsPreviousEvidence() {
        WakeWordConfidenceGate gate = new WakeWordConfidenceGate();
        assertFalse(gate.add(0.31f));
        assertTrue(gate.add(0.31f));
        gate.reset();
        assertFalse(gate.add(0.31f));
        assertFalse(gate.add(0.08f));
    }

    @Test
    public void rejectsNonFiniteScores() {
        WakeWordConfidenceGate gate = new WakeWordConfidenceGate();
        assertFalse(gate.add(Float.NaN));
        assertFalse(gate.add(Float.POSITIVE_INFINITY));
        assertFalse(gate.add(0.05f));
        assertFalse(gate.add(0.06f));
    }
}
