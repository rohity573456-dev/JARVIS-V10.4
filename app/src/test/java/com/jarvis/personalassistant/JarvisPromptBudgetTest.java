package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisPromptBudgetTest {
    @Test public void keepsCurrentTurnTailWhenContextIsTooLarge() {
        String input = "EARLY_MEMORY_" + new String(new char[18000]).replace((char) 0, 'x') + "_CURRENT_USER_QUESTION";
        JarvisPromptBudget.Prompt p = JarvisPromptBudget.fit("JARVIS_SYSTEM", input, 4000);
        assertTrue(p.input.contains("Earlier context safely truncated"));
        assertTrue(p.input.endsWith("_CURRENT_USER_QUESTION"));
        assertTrue(p.system.contains("JARVIS_SYSTEM"));
        assertTrue(p.system.length() + p.input.length() <= 4000);
    }

    @Test public void preservesSmallMultiTurnPromptsWithoutTruncation() {
        String input = "Earlier turn" + "\n" + "Commander: And what about that?";
        JarvisPromptBudget.Prompt p = JarvisPromptBudget.fit("System", input, 14000);
        assertEquals(input, p.input);
        assertEquals("System", p.system);
    }

    @Test public void tokenEstimateIsConservativeAndEmptySafe() {
        assertEquals(0, JarvisPromptBudget.estimateTokens(""));
        assertTrue(JarvisPromptBudget.estimateTokens("abcdef") >= 2);
    }
}