package com.jarvis.personalassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded plan/execute/verify orchestrator. Planning is injected so model providers
 * remain replaceable; all side effects must pass through JarvisToolRegistry.
 */
public final class JarvisAgentEngine {
    public static final int MAX_STEPS = 8;
    public static final int MAX_RETRIES_PER_STEP = 1;

    public enum State { PLANNING, AWAITING_APPROVAL, RUNNING, VERIFYING, COMPLETED, FAILED, CANCELLED }

    public interface Planner { AgentPlan createPlan(String objective) throws Exception; }
    public interface ApprovalGate { boolean approve(AgentStep step); }
    public interface Listener {
        void onState(State state, String detail);
        void onComplete(AgentReport report);
    }

    public static final class AgentStep {
        public final String id;
        public final String toolName;
        public final Map<String, String> arguments;
        public final String purpose;

        public AgentStep(String id, String toolName, Map<String, String> arguments, String purpose) {
            if (blank(id) || blank(toolName)) throw new IllegalArgumentException("Step id and tool name are required");
            this.id = id;
            this.toolName = toolName;
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

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
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
        EXECUTOR.execute(() -> run(objective.trim(), listener, handle));
        return handle;
    }

    private void run(String objective, Listener listener, RunHandle handle) {
        List<StepReport> reports = new ArrayList<>();
        State state = State.PLANNING;
        notifyState(listener, state, "Creating a bounded execution plan");
        try {
            AgentPlan plan = planner.createPlan(objective);
            if (plan == null || plan.steps.size() > MAX_STEPS) throw new IllegalStateException("Planner returned an invalid plan");
            for (AgentStep step : plan.steps) {
                if (handle.cancelled.get()) { state = State.CANCELLED; break; }
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
                if (!result.success && !handle.cancelled.get()) {
                    attempts++;
                    notifyState(listener, State.RUNNING, "Retrying failed step once");
                    result = registry.invoke(step.toolName, step.arguments);
                }
                state = State.VERIFYING;
                notifyState(listener, state, "Checking tool result");
                reports.add(new StepReport(step, result, attempts));
                if (!result.success) { state = State.FAILED; break; }
                if (!result.verified) {
                    state = State.FAILED;
                    notifyState(listener, state, "Tool completed but result could not be verified; stopping safely");
                    break;
                }
            }
            if (handle.cancelled.get()) state = State.CANCELLED;
            else if (state != State.FAILED && state != State.CANCELLED) state = State.COMPLETED;
        } catch (Exception e) {
            state = State.FAILED;
            reports.add(new StepReport(new AgentStep("planner", "planner_error", Collections.<String,String>emptyMap(), ""),
                JarvisToolRegistry.ToolResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage()), 1));
        }
        String summary = state == State.COMPLETED ? "All planned steps completed and verified."
            : state == State.CANCELLED ? "Execution cancelled; no further steps were started."
            : "Execution stopped safely. Review the step report before retrying.";
        notifyState(listener, state, summary);
        if (listener != null) listener.onComplete(new AgentReport(objective, state, reports, summary));
    }

    private static void notifyState(Listener listener, State state, String detail) {
        if (listener != null) listener.onState(state, detail);
    }

    public static final class RunHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        public void cancel() { cancelled.set(true); }
        public boolean isCancelled() { return cancelled.get(); }
    }
}
