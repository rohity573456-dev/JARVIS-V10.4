# JARVIS Hybrid AI Brain — Phase 2: Agent Engine Foundation

## Scope
Phase 2 adds the safe execution foundation required to evolve the Phase 1 provider router into a tool-using agent. This branch is based on `feature/hybrid-ai-brain-phase1`; it does not merge into `main`.

## Implemented in this increment
- Typed, explicitly registered tool catalog. Model-generated names cannot invoke arbitrary Java methods or shell commands.
- Strict required/optional argument validation and immutable argument maps.
- Risk classification: READ_ONLY, REVERSIBLE, SENSITIVE, EXTERNAL.
- Approval gate for SENSITIVE and EXTERNAL operations.
- Bounded plan size (8 steps), bounded retry (one retry), cancellation checks and sequential execution.
- Plan/execute/verify state transitions and structured per-step reports.
- Fail-closed behavior for unknown tools, invalid arguments, denied approval, tool exceptions and unverified results.
- Unit tests for registry validation, risk policy, successful execution, approval denial, verification failure and plan limits.

## Architecture contracts
- Provider-specific planning is injected through `Planner`; it must return a typed `AgentPlan`. Raw model text is not executable.
- Android features must be exposed as narrow tools with explicit argument schemas and permission checks.
- Tool handlers must return verified results only when the platform confirms the operation. Otherwise return `ToolResult.unverified` and the engine stops.
- Sending messages, payments, deleting/sharing files, account changes and other external or irreversible actions must require an explicit user confirmation in the UI. Never auto-approve from model output.
- Background jobs require durable persistence and lifecycle-aware scheduling; this engine currently runs in-process and is not a durable WorkManager job system.

## Not yet wired / next increments
- Provider-native function calling and structured plan generation from Gemini/Groq/OpenAI-compatible models.
- Android UI approval dialog and registration of real Android tools.
- Durable task journal, WorkManager recovery and notification controls.
- Web search connector and source-citation pipeline.
- Vision/document tools and sandboxed code/data workspace.
- Streaming response transport, speech barge-in and real-device latency/battery evaluation.
- Integration tests across provider failures and Android API levels.

## Explicit non-goals
- No shell access, arbitrary reflection, dynamic code loading or unrestricted file access.
- No modification of personal speaker recognition, wake-word models, ONNX assets or wake-word thresholds.
- No claim of ChatGPT Plus parity or autonomous execution until provider planning, tool integrations and device tests are complete.
