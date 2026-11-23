package com.jarvis.personalassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded plan/execute/verify agent with verified tool-result feedback between turns. */
public final class JarvisAgentEngine {
    public static final int MAX_STEPS = 8;
    public static final int MAX_RETRIES_PER_STEP = 1;

    public enum State { PLANNING, AWAITING_APPROVAL, RUNNING, VERIFYING, COMPLETED, FAILED, CANCELLED }

    public interface Planner {
        AgentPlan createPlan(String objective) throws Exception;
        /** A fresh bounded planning turn receives only locally verified prior tool results. */
        default AgentPlan createFollowUpPlan(String objective, List<StepReport> verifiedHistory) throws Exception {
            return new AgentPlan(objective, Collections.<AgentStep>emptyList());
        }
    }
    public interface ApprovalGate { boolean approve(AgentStep step); }
    public interface Listener {
        void onState(State state, String detail);
        void onComplete(AgentReport report);
    }

    public static final class AgentStep {
        public final String id, toolName, purpose;
        public final Map<String, String> arguments;
        public AgentStep(String id, String toolName, Map<String, String> arguments, String purpose) {
            if (blank(id) || blank(toolName)) throw new IllegalArgumentException("Step id and tool name are required");
            this.id = id; this.toolName = toolName;
            this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(
                arguments == null ? Collections.<String, String>emptyMap() : arguments));
            this.purpose = purpose == null ? "" : purpose;
        }
        private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    }

    public static final class AgentPlan {
        public final String objective;
        public final List<AgentStep> steps;
        public AgentPlan(String objective, List<AgentStep> steps) {
            if (objective == null || objective.trim().isEmpty()) throw new IllegalArgumentException("Objective is required");
            if (steps == null || steps.size() > MAX_STEPS)
                throw new IllegalArgumentException("Plan must contain 0-" + MAX_STEPS + " steps");
            this.objective = objective;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }
    }

    public static final class StepReport {
        public final String stepId, toolName, output;
        public final boolean success, verified;
        public final int attempts;
        StepReport(AgentStep step, JarvisToolRegistry.ToolResult result, int attempts) {
            stepId = step.id; toolName = step.toolName; output = result.output;
            success = result.success; verified = result.verified; this.attempts = attempts;
        }
    }

    public static final class AgentReport {
        public final String objective;
        public final State finalState;
        public final List<StepReport> steps;
        public final String summary;
        AgentReport(String objective, State state, List<StepReport> steps, String summary) {
            this.objective = objective; finalState = state;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps)); this.summary = summary;
        }
    }

    // Bound both active work and queued work: a burst of requests must not grow an
    // unbounded backlog of provider calls, plans or approval dialogs.
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(8),
        new ThreadFactory() {
            private int nextId = 1;
            @Override public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "Jarvis-Agent-" + nextId++);
                thread.setDaemon(true);
                return thread;
            }
        }, new ThreadPoolExecutor.AbortPolicy());
    private final Planner planner;
    private final JarvisToolRegistry registry;
    private final ApprovalGate approvalGate;

    public JarvisAgentEngine(Planner planner, JarvisToolRegistry registry, ApprovalGate approvalGate) {
        if (planner == null || registry == null || approvalGate == null) throw new IllegalArgumentException("Planner, registry and approval gate are required");
        this.planner = planner; this.registry = registry; this.approvalGate = approvalGate;
    }

    public RunHandle submit(String objective, Listener listener) {
        if (objective == null || objective.trim().isEmpty()) throw new IllegalArgumentException("Objective is required");
        RunHandle handle = new RunHandle();
        try {
            EXECUTOR.execute(() -> run(objective.trim(), listener, handle));
        } catch (RejectedExecutionException rejected) {
            handle.finished.set(true);
            String detail = "JARVIS is busy with other agent tasks. Please try again shortly.";
            notifyState(listener, State.FAILED, detail);
            notifyComplete(listener, new AgentReport(objective.trim(), State.FAILED,
                Collections.<StepReport>emptyList(), detail));
        }
        return handle;
    }

    private void run(String objective, Listener listener, RunHandle handle) {
        List<StepReport> reports = new ArrayList<>();
        State state = State.PLANNING;
        notifyState(listener, state, "Creating a bounded execution plan");
        try {
            AgentPlan plan = planner.createPlan(objective);
            if (plan == null) throw new IllegalStateException("Planner returned a null plan");
            int planned = 0;
            while (plan.steps.size() > 0 && planned < MAX_STEPS) {
                if (handle.cancelled.get()) { state = State.CANCELLED; break; }
                // Execute one model-proposed action, then return its verified result to a new planning turn.
                AgentStep step = plan.steps.get(0);
                planned++;
                JarvisToolRegistry.ToolSpec spec = registry.get(step.toolName);
                if (spec == null) throw new IllegalStateException("Plan requested an unregistered tool: " + step.toolName);
                List<String> errors = spec.argumentErrors(step.arguments);
                if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid arguments for " + step.toolName + ": " + errors);
                if (JarvisToolRegistry.requiresApproval(spec.risk)) {
                    state = State.AWAITING_APPROVAL;
                    notifyState(listener, state, "Approval required for " + step.toolName);
                    if (!approvalGate.approve(step)) { state = State.CANCELLED; break; }
                }
                if (handle.cancelled.get()) { state = State.CANCELLED; break; }
                state = State.RUNNING;
                notifyState(listener, state, "Executing " + step.toolName);
                JarvisToolRegistry.ToolResult result = registry.invoke(step.toolName, step.arguments);
                int attempts = 1;
                // Retry only read-only operations. A failed side-effecting action may have
                // completed remotely before its response was lost; retrying could duplicate it.
                if (!result.success && spec.risk == JarvisToolRegistry.Risk.READ_ONLY
                        && !handle.cancelled.get()) {
                    attempts++;
                    notifyState(listener, State.RUNNING, "Retrying failed read-only step once");
                    result = registry.invoke(step.toolName, step.arguments);
                }
                state = State.VERIFYING;
                notifyState(listener, state, "Checking tool result");
                StepReport report = new StepReport(step, result, attempts);
                reports.add(report);
                if (!result.success) { state = State.FAILED; break; }
                if (!result.verified) {
                    state = State.FAILED;
                    notifyState(listener, state, "Tool completed but result could not be verified; stopping safely");
                    break;
                }
                if (handle.cancelled.get()) { state = State.CANCELLED; break; }
                if (planned >= MAX_STEPS) break;
                state = State.PLANNING;
                notifyState(listener, state, "Returning verified result to AI for the next decision");
                List<StepReport> history = Collections.unmodifiableList(new ArrayList<>(reports));
                plan = planner.createFollowUpPlan(objective, history);
                if (plan == null) throw new IllegalStateException("Follow-up planner returned a null plan");
            }
            if (handle.cancelled.get()) state = State.CANCELLED;
            else if (state != State.FAILED && state != State.CANCELLED) state = State.COMPLETED;
        } catch (Exception e) {
            if (handle.cancelled.get()) {
                state = State.CANCELLED;
            } else {
                state = State.FAILED;
                reports.add(new StepReport(new AgentStep("planner", "planner_error", Collections.<String,String>emptyMap(), ""),
                    JarvisToolRegistry.ToolResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage()), 1));
            }
        }
        String summary = state == State.COMPLETED ? "Agent planning finished; every executed action was verified."
            : state == State.CANCELLED ? "Execution cancelled or approval denied; no further steps were started."
            : "Execution stopped safely. Review the step report before retrying.";
        handle.finished.set(true);
        notifyState(listener, state, summary);
        notifyComplete(listener, new AgentReport(objective, state, reports, summary));
    }

    private static void notifyState(Listener listener, State state, String detail) {
        if (listener == null) return;
        try { listener.onState(state, detail); }
        catch (RuntimeException callbackFailure) {
            // UI callback failures must not turn a successful Android action into a retry.
        }
    }

    private static void notifyComplete(Listener listener, AgentReport report) {
        if (listener == null) return;
        try { listener.onComplete(report); }
        catch (RuntimeException callbackFailure) {
            // The execution result remains final even if the UI has already been destroyed.
        }
    }

    public static final class RunHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        public void cancel() { if (!finished.get()) cancelled.set(true); }
        public boolean isCancelled() { return cancelled.get(); }
        public boolean isFinished() { return finished.get(); }
    }
}