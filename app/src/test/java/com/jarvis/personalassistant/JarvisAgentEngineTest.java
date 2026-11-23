package com.jarvis.personalassistant;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class JarvisAgentEngineTest {
    @Test public void registryRejectsUnknownToolsAndUnexpectedArguments() {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        registry.register(new JarvisToolRegistry.ToolSpec("read_status", "Read status",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.singleton("target"),
            Collections.<String>emptySet(), "", args -> JarvisToolRegistry.ToolResult.success("ok")));
        assertFalse(registry.invoke("shell", Collections.<String,String>emptyMap()).success);
        Map<String,String> invalid = new HashMap<>(); invalid.put("target","battery"); invalid.put("command","rm -rf");
        assertFalse(registry.invoke("read_status", invalid).success);
    }

    @Test public void listenerStateCallbackFailureDoesNotInterruptAgentExecution() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("read_status", "Read status",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.success("ok");
            }));
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Collections.singletonList(
                new JarvisAgentEngine.AgentStep("s1", "read_status",
                    Collections.<String,String>emptyMap(), "Read"))),
            registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        engine.submit("read status", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {
                throw new IllegalStateException("activity was detached");
            }
            public void onComplete(JarvisAgentEngine.AgentReport value) {
                report[0] = value;
                done.countDown();
            }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.COMPLETED, report[0].finalState);
        assertEquals(1, calls.get());
    }

    @Test public void registryRejectsOversizedToolArgumentsBeforeHandler() {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("web_search", "Search the web",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.singleton("query"),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.success("searched");
            }));
        Map<String,String> oversized = Collections.singletonMap("query", new String(new char[301]).replace('\0', 'x'));
        JarvisToolRegistry.ToolResult result = registry.invoke("web_search", oversized);
        assertFalse(result.success);
        assertEquals(0, calls.get());
    }

    @Test public void sensitiveAndExternalToolsRequireApproval() {
        assertTrue(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.SENSITIVE));
        assertTrue(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.EXTERNAL));
        assertFalse(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.READ_ONLY));
        assertFalse(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.REVERSIBLE));
    }

    @Test public void engineExecutesOnlyRegisteredAndVerifiedSteps() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("read_status", "Read status",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.success("battery 80%");
            }));
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Arrays.asList(
                new JarvisAgentEngine.AgentStep("s1", "read_status", Collections.<String,String>emptyMap(), "Check battery"))),
            registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        engine.submit("check battery", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.COMPLETED, report[0].finalState);
        assertEquals(1, calls.get());
        assertTrue(report[0].steps.get(0).verified);
    }

    @Test public void deniedApprovalStopsBeforeToolRuns() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("send_message", "Send a message",
            JarvisToolRegistry.Risk.EXTERNAL, Collections.singleton("text"),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.success("sent");
            }));
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Arrays.asList(
                new JarvisAgentEngine.AgentStep("s1", "send_message",
                    Collections.singletonMap("text","hello"), "Send requested message"))),
            registry, step -> false);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        engine.submit("send message", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.CANCELLED, report[0].finalState);
        assertEquals(0, calls.get());
    }

    @Test public void failedExternalActionIsNeverRetriedAutomatically() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("send_message", "Send a message",
            JarvisToolRegistry.Risk.EXTERNAL, Collections.singleton("text"),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.failure("response lost after send");
            }));
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Collections.singletonList(
                new JarvisAgentEngine.AgentStep("s1", "send_message",
                    Collections.singletonMap("text","hello"), "Send once"))),
            registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        engine.submit("send message", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.FAILED, report[0].finalState);
        assertEquals("A side-effecting action must not be repeated", 1, calls.get());
        assertEquals(1, report[0].steps.get(0).attempts);
    }

    @Test public void unverifiedToolResultStopsExecution() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        registry.register(new JarvisToolRegistry.ToolSpec("unsafe_check", "Unverified",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> JarvisToolRegistry.ToolResult.unverified("unknown")));
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Arrays.asList(
                new JarvisAgentEngine.AgentStep("s1", "unsafe_check", Collections.<String,String>emptyMap(), "Check"))),
            registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        engine.submit("check", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.FAILED, report[0].finalState);
    }

    @Test public void cancelledPlannerFailureIsReportedAsCancellation() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        CountDownLatch planningEntered = new CountDownLatch(1);
        CountDownLatch releasePlanning = new CountDownLatch(1);
        JarvisAgentEngine engine = new JarvisAgentEngine(objective -> {
            planningEntered.countDown();
            try { releasePlanning.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            throw new IllegalStateException("provider request ended");
        }, registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        JarvisAgentEngine.RunHandle handle = engine.submit("cancel this plan", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(planningEntered.await(2, TimeUnit.SECONDS));
        handle.cancel();
        releasePlanning.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.CANCELLED, report[0].finalState);
        assertTrue(report[0].steps.isEmpty());
        assertTrue(handle.isFinished());
    }

    @Test public void approvalPolicyKeepsReadOnlyAndReversibleActionsLowFriction() {
        assertFalse(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.READ_ONLY));
        assertFalse(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.REVERSIBLE));
        assertTrue(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.SENSITIVE));
        assertTrue(JarvisToolRegistry.requiresApproval(JarvisToolRegistry.Risk.EXTERNAL));
    }

    @Test public void cancellationDuringApprovalPreventsToolExecution() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("external_action", "External action",
            JarvisToolRegistry.Risk.EXTERNAL, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.success("done");
            }));
        CountDownLatch approvalEntered = new CountDownLatch(1);
        CountDownLatch releaseApproval = new CountDownLatch(1);
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Collections.singletonList(
                new JarvisAgentEngine.AgentStep("s1", "external_action",
                    Collections.<String,String>emptyMap(), "Needs approval"))),
            registry, step -> {
                approvalEntered.countDown();
                try { releaseApproval.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return true;
            });
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        JarvisAgentEngine.RunHandle handle = engine.submit("external action", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(approvalEntered.await(2, TimeUnit.SECONDS));
        handle.cancel();
        releaseApproval.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.CANCELLED, report[0].finalState);
        assertEquals(0, calls.get());
        assertTrue(handle.isFinished());
    }

    @Test public void cancellationDuringPlanningPreventsLateToolExecution() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new JarvisToolRegistry.ToolSpec("read_status", "Read status",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> {
                calls.incrementAndGet();
                return JarvisToolRegistry.ToolResult.success("ok");
            }));
        CountDownLatch planningEntered = new CountDownLatch(1);
        CountDownLatch releasePlanning = new CountDownLatch(1);
        JarvisAgentEngine engine = new JarvisAgentEngine(objective -> {
            planningEntered.countDown();
            try { releasePlanning.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new JarvisAgentEngine.AgentPlan(objective, Collections.singletonList(
                new JarvisAgentEngine.AgentStep("s1", "read_status",
                    Collections.<String,String>emptyMap(), "Read after planning")));
        }, registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        JarvisAgentEngine.RunHandle handle = engine.submit("read status", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(planningEntered.await(2, TimeUnit.SECONDS));
        handle.cancel();
        releasePlanning.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.CANCELLED, report[0].finalState);
        assertEquals(0, calls.get());
        assertTrue(handle.isFinished());
    }

    @Test public void planCannotExceedStepLimit() {
        try {
            JarvisAgentEngine.AgentPlan ignored = new JarvisAgentEngine.AgentPlan("large",
                Arrays.asList(new JarvisAgentEngine.AgentStep("1","a",null,""),
                    new JarvisAgentEngine.AgentStep("2","a",null,""),
                    new JarvisAgentEngine.AgentStep("3","a",null,""),
                    new JarvisAgentEngine.AgentStep("4","a",null,""),
                    new JarvisAgentEngine.AgentStep("5","a",null,""),
                    new JarvisAgentEngine.AgentStep("6","a",null,""),
                    new JarvisAgentEngine.AgentStep("7","a",null,""),
                    new JarvisAgentEngine.AgentStep("8","a",null,""),
                    new JarvisAgentEngine.AgentStep("9","a",null,"")));
            fail("Expected bounded plan rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void verifiedResultIsReturnedBeforeNextPlanningTurn() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        registry.register(new JarvisToolRegistry.ToolSpec("first", "First action",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> JarvisToolRegistry.ToolResult.success("first verified output")));
        registry.register(new JarvisToolRegistry.ToolSpec("second", "Second action",
            JarvisToolRegistry.Risk.READ_ONLY, Collections.<String>emptySet(),
            Collections.<String>emptySet(), "", args -> JarvisToolRegistry.ToolResult.success("second verified output")));
        AtomicInteger turns = new AtomicInteger();
        JarvisAgentEngine.Planner planner = new JarvisAgentEngine.Planner() {
            public JarvisAgentEngine.AgentPlan createPlan(String objective) {
                return new JarvisAgentEngine.AgentPlan(objective, Collections.singletonList(
                    new JarvisAgentEngine.AgentStep("one", "first", Collections.<String,String>emptyMap(), "")));
            }
            @Override public JarvisAgentEngine.AgentPlan createFollowUpPlan(String objective,
                    java.util.List<JarvisAgentEngine.StepReport> history) {
                int turn = turns.incrementAndGet();
                assertEquals(turn == 1 ? 1 : 2, history.size());
                assertEquals("first verified output", history.get(0).output);
                if (turn == 1) return new JarvisAgentEngine.AgentPlan(objective, Collections.singletonList(
                    new JarvisAgentEngine.AgentStep("two", "second", Collections.<String,String>emptyMap(), "")));
                return new JarvisAgentEngine.AgentPlan(objective, Collections.<JarvisAgentEngine.AgentStep>emptyList());
            }
        };
        JarvisAgentEngine engine = new JarvisAgentEngine(planner, registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        engine.submit("complete two actions", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.COMPLETED, report[0].finalState);
        assertEquals(2, report[0].steps.size());
        assertEquals(2, turns.get());
    }

    @Test public void emptyInitialPlanCompletesWithoutExecutingTools() throws Exception {
        JarvisToolRegistry registry = new JarvisToolRegistry();
        JarvisAgentEngine engine = new JarvisAgentEngine(
            objective -> new JarvisAgentEngine.AgentPlan(objective, Collections.<JarvisAgentEngine.AgentStep>emptyList()),
            registry, step -> true);
        CountDownLatch done = new CountDownLatch(1);
        final JarvisAgentEngine.AgentReport[] report = new JarvisAgentEngine.AgentReport[1];
        JarvisAgentEngine.RunHandle handle = engine.submit("just answer a question", new JarvisAgentEngine.Listener() {
            public void onState(JarvisAgentEngine.State state, String detail) {}
            public void onComplete(JarvisAgentEngine.AgentReport value) { report[0] = value; done.countDown(); }
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(JarvisAgentEngine.State.COMPLETED, report[0].finalState);
        assertTrue(report[0].steps.isEmpty());
        assertTrue(handle.isFinished());
        handle.cancel();
        assertFalse(handle.isCancelled());
    }

}
