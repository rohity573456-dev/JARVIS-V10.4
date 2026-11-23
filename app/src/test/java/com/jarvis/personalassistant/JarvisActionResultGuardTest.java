package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisActionResultGuardTest {
    @Test public void reportsSuccessOnlyWhenPlatformActionReturnsNormally() {
        assertTrue(JarvisActionResultGuard.run(() -> { }));
    }

    @Test public void reportsFailureWhenPlatformLaunchThrows() {
        assertFalse(JarvisActionResultGuard.run(() -> { throw new SecurityException("blocked"); }));
        assertFalse(JarvisActionResultGuard.run(() -> { throw new IllegalStateException("no handler"); }));
    }

    @Test public void nullActionFailsClosed() {
        assertFalse(JarvisActionResultGuard.run(null));
    }
}
