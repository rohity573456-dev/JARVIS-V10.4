package com.jarvis.personalassistant;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class JarvisHttpBodyReaderTest {
    @Test public void readsUtf8ResponseWithinLimit() throws Exception {
        String expected = "JARVIS नमस्ते";
        String actual = JarvisHttpBodyReader.read(
                new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)), 100);
        assertEquals(expected, actual);
    }

    @Test public void rejectsOversizedResponseBody() throws Exception {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
        try {
            JarvisHttpBodyReader.read(new ByteArrayInputStream(body), 9);
            fail("Oversized response must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("safety limit"));
        }
    }

    @Test public void handlesNullStreamAsEmptyBody() throws Exception {
        assertEquals("", JarvisHttpBodyReader.read(null, 10));
    }
}
