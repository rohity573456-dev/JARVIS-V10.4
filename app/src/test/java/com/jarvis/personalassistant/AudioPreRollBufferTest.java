package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioPreRollBufferTest {
    @Test public void snapshotReturnsSamplesInChronologicalOrder() {
        AudioPreRollBuffer b = new AudioPreRollBuffer(5);
        b.add(new short[]{1,2,3},3);
        b.add(new short[]{4,5,6},3);
        assertArrayEquals(new short[]{2,3,4,5,6}, b.snapshot());
    }

    @Test public void snapshotKeepsOnlyNewestCapacityWhenInputIsLarge() {
        AudioPreRollBuffer b = new AudioPreRollBuffer(4);
        b.add(new short[]{1,2,3,4,5,6},6);
        assertArrayEquals(new short[]{3,4,5,6}, b.snapshot());
    }

    @Test public void clearRemovesPreviousAudio() {
        AudioPreRollBuffer b = new AudioPreRollBuffer(4);
        b.add(new short[]{1,2,3},3);
        b.clear();
        assertEquals(0,b.size());
        assertArrayEquals(new short[0],b.snapshot());
    }
}
