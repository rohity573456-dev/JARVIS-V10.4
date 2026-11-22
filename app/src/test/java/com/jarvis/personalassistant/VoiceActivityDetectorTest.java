package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceActivityDetectorTest {
    @Test public void detectsSpeechAboveAdaptiveFloor() {
        VoiceActivityDetector vad = new VoiceActivityDetector();
        short[] voice = new short[1600];
        for (int i = 0; i < voice.length; i++) voice[i] = (short) ((i % 2 == 0) ? 1800 : -1800);
        assertTrue(vad.update(voice, voice.length));
    }

    @Test public void rejectsDigitalSilence() {
        VoiceActivityDetector vad = new VoiceActivityDetector();
        assertFalse(vad.update(new short[1600], 1600));
    }

    @Test public void keepsShortHangoverAfterSpeech() {
        VoiceActivityDetector vad = new VoiceActivityDetector();
        short[] voice = new short[1600];
        for (int i = 0; i < voice.length; i++) voice[i] = (short) ((i % 2 == 0) ? 1800 : -1800);
        assertTrue(vad.update(voice, voice.length));
        assertTrue(vad.update(new short[1600], 1600));
    }

    @Test public void resetClearsHangover() {
        VoiceActivityDetector vad = new VoiceActivityDetector();
        short[] voice = new short[1600];
        for (int i = 0; i < voice.length; i++) voice[i] = (short) ((i % 2 == 0) ? 1800 : -1800);
        vad.update(voice, voice.length);
        vad.reset();
        assertFalse(vad.isSpeechActive());
    }
}
