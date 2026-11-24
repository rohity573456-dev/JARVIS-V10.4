package com.jarvis.personalassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class AstraCognitiveKernelTest {
    @Test public void identifiesEngineeringMission() {
        assertEquals(AstraCognitiveKernel.MissionType.ENGINEERING,
                AstraCognitiveKernel.analyze("Debug this Kotlin Android app").type);
    }

    @Test public void identifiesHighStakesMission() {
        assertEquals(AstraCognitiveKernel.MissionType.HIGH_STAKES,
                AstraCognitiveKernel.analyze("Help me understand a medical diagnosis").type);
    }

    @Test public void identifiesHindiEngineeringMission() {
        assertEquals(AstraCognitiveKernel.MissionType.ENGINEERING,
                AstraCognitiveKernel.analyze("JARVIS ka poora brain develop karo").type);
    }

    @Test public void complexMissionRequiresIndependentReview() {
        AstraCognitiveKernel.Mission mission = AstraCognitiveKernel.analyze(
                "Design the full production architecture and integrate all modules step by step");
        assertTrue(mission.complexity >= 3);
        assertTrue(mission.requiresIndependentReview);
    }

    @Test public void plannerCreatesOrderedEngineeringSubgoals() {
        AstraCognitiveKernel.CognitivePlan plan = AstraCognitiveKernel.plan(
                AstraCognitiveKernel.analyze("Build a complete Android software architecture"));
        assertTrue(plan.subgoals.size() >= 5);
        for (int i = 0; i < plan.subgoals.size(); i++) {
            assertEquals(i + 1, plan.subgoals.get(i).index);
            assertFalse(plan.subgoals.get(i).completionCheck.isEmpty());
        }
    }

    @Test public void simpleConversationDoesNotRequireReview() {
        AstraCognitiveKernel.Mission mission = AstraCognitiveKernel.analyze("Hello JARVIS");
        assertEquals(AstraCognitiveKernel.MissionType.CONVERSATION, mission.type);
        assertFalse(mission.requiresIndependentReview);
    }

    @Test public void privateModeAddsExplicitMemoryBoundary() {
        String prompt = AstraCognitiveKernel.buildCognitivePrompt("isolated request",
                AstraCognitiveKernel.analyze("Hello"), true);
        assertTrue(prompt.contains("Private Mode is active"));
    }

    @Test public void normalModeUsesOnlySuppliedMemory() {
        String prompt = AstraCognitiveKernel.buildCognitivePrompt("memory excerpt",
                AstraCognitiveKernel.analyze("Hello"), false);
        assertTrue(prompt.contains("only memory/history explicitly included"));
        assertTrue(prompt.contains("memory excerpt"));
    }

    @Test public void highStakesPlanUsesConservativePolicy() {
        AstraCognitiveKernel.CognitivePlan plan = AstraCognitiveKernel.plan(
                AstraCognitiveKernel.analyze("Help me understand a medical diagnosis"));
        assertTrue(plan.riskPolicy.contains("High-impact"));
    }

    @Test public void failedReviewPreservesOriginalAnswer() {
        assertEquals("original", AstraCognitiveKernel.acceptReview("original", "ERROR: unavailable"));
    }

    @Test public void oversizedReviewIsRejected() {
        String original = "short answer";
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 40000; i++) huge.append('x');
        assertEquals(original, AstraCognitiveKernel.acceptReview(original, huge.toString()));
    }

    @Test public void validReviewCanReplaceDraft() {
        assertEquals("corrected answer",
                AstraCognitiveKernel.acceptReview("original answer", "corrected answer"));
    }
}
