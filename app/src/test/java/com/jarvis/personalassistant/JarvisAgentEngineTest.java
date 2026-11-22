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
}
