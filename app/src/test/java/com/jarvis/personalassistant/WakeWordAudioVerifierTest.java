package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeWordAudioVerifierTest {
    @Test public void acceptsRecentVoicedFramesWithHealthyAudio() {
        WakeWordAudioVerifier verifier = new WakeWordAudioVerifier();
        short[] voice = new short[1600];
        for (int i = 0; i < voice.length; i++) voice[i] = (short) ((i % 2 == 0) ? 1800 : -1800);
        verifier.addFrame(voice, voice.length, true);
        verifier.addFrame(voice, voice.length, true);
        assertTrue(verifier.verify());
    }

    @Test public void rejectsSilenceOnlyCandidate() {
        WakeWordAudioVerifier verifier = new WakeWordAudioVerifier();
        short[] silence = new short[1600];
        verifier.addFrame(silence, silence.length, false);
        verifier.addFrame(silence, silence.length, false);
        assertFalse(verifier.verify());
    }

    @Test public void rejectsCandidateWithOnlyOneVoicedFrame() {
        WakeWordAudioVerifier verifier = new WakeWordAudioVerifier();
        short[] voice = new short[1600];
        for (int i = 0; i < voice.length; i++) voice[i] = (short) ((i % 2 == 0) ? 1800 : -1800);
        verifier.addFrame(voice, voice.length, true);
        verifier.addFrame(new short[1600], 1600, false);
        assertFalse(verifier.verify());
    }
}
