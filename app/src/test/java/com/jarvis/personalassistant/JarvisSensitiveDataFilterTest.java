package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisSensitiveDataFilterTest {
    @Test public void blocksCommonIdentityCredentialAndFinancialData() {
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("My Aadhaar is 123456789012"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("PAN ABCDE1234F"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("bank account number 12345"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("OTP 123456"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("credit card details"));
    }

    @Test public void blocksSensitiveHealthBeliefAndBiometricContent() {
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("I have diabetes"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("my religious belief"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("my political party"));
        assertTrue(JarvisSensitiveDataFilter.isSensitiveText("my fingerprint data"));
    }

    @Test public void allowsOrdinaryNonSensitivePreferences() {
        assertFalse(JarvisSensitiveDataFilter.isSensitiveText("I prefer concise answers"));
        assertFalse(JarvisSensitiveDataFilter.isSensitiveText("My current project is JARVIS"));
        assertFalse(JarvisSensitiveDataFilter.isSensitiveText(null));
    }
}