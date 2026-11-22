package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeWordAudioVerifierTest {
    private short[] voice() {
        short[] pcm = new short[1600];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) ((i % 2 == 0) ? 1800 : -1800);
        return pcm;
    }

    @Test public void acceptsRecentVoicedFramesWithHealthyAudio() {
        WakeWordAudioVerifier v = new WakeWordAudioVerifier();
        v.addFrame(voice(), 1600, true);
        v.addFrame(voice(), 1600, true);
        assertTrue(v.verify());
    }

    @Test public void rejectsSilenceOnlyCandidate() {
        WakeWordAudioVerifier v = new WakeWordAudioVerifier();
        short[] silence = new short[1600];
        v.addFrame(silence, 1600, false);
        v.addFrame(silence, 1600, false);
        assertFalse(v.verify());
    }

    @Test public void rejectsOnlyOneHealthyVoicedFrame() {
        WakeWordAudioVerifier v = new WakeWordAudioVerifier();
        v.addFrame(voice(), 1600, true);
        v.addFrame(new short[1600], 1600, false);
        assertFalse(v.verify());
    }

    @Test public void rejectsHangoverFlagsOnSilentFrames() {
        WakeWordAudioVerifier v = new WakeWordAudioVerifier();
        v.addFrame(voice(), 1600, true);
        short[] silence = new short[1600];
        v.addFrame(silence, 1600, true);
        v.addFrame(silence, 1600, true);
        assertFalse(v.verify());
    }

    @Test public void rejectsClippedAudio() {
        WakeWordAudioVerifier v = new WakeWordAudioVerifier();
        short[] clipped = new short[1600];
        java.util.Arrays.fill(clipped, (short) 32767);
        v.addFrame(clipped, 1600, true);
        v.addFrame(clipped, 1600, true);
        assertFalse(v.verify());
    }

    @Test public void resetClearsHistory() {
        WakeWordAudioVerifier v = new WakeWordAudioVerifier();
        v.addFrame(voice(), 1600, true);
        v.addFrame(voice(), 1600, true);
        v.reset();
        assertFalse(v.verify());
    }
}