package com.jarvis.personalassistant;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;

public class MultiBrainRouterTest {
    @Test public void codingPrefersNebiusThenGroqThenGemini() {
        assertEquals(Arrays.asList(MultiBrainRouter.Provider.NEBIUS,
                MultiBrainRouter.Provider.GROQ, MultiBrainRouter.Provider.GEMINI),
                MultiBrainRouter.route("Review this Java code", true, true, true));
    }

    @Test public void generalChatPrefersGeminiThenGroqThenNebius() {
        assertEquals(Arrays.asList(MultiBrainRouter.Provider.GEMINI,
                MultiBrainRouter.Provider.GROQ, MultiBrainRouter.Provider.NEBIUS),
                MultiBrainRouter.route("Hello, how are you?", true, true, true));
    }

    @Test public void planningPrefersGroqThenNebiusThenGemini() {
        assertEquals(Arrays.asList(MultiBrainRouter.Provider.GROQ,
                MultiBrainRouter.Provider.NEBIUS, MultiBrainRouter.Provider.GEMINI),
                MultiBrainRouter.route("Create a project roadmap", true, true, true));
    }

    @Test public void unavailableProvidersAreExcluded() {
        assertEquals(Collections.singletonList(MultiBrainRouter.Provider.NEBIUS),
                MultiBrainRouter.route("Write code", true, false, false));
    }

    @Test public void noConfiguredProviderReturnsEmptyRoute() {
        assertEquals(Collections.emptyList(),
                MultiBrainRouter.route("Hello", false, false, false));
    }
}
