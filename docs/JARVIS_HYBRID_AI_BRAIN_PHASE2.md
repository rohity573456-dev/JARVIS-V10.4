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
- Gemini/Groq planning uses OpenAI-compatible native `tools` / `tool_calls` transport. Tool schemas are generated from the local registry, and returned arguments are parsed as strings and revalidated against that registry before execution.
- The current UI invokes the agent for explicit app-open/launch and explicit web-search requests; ordinary questions continue through the hybrid chat path. A no-tool response is treated as no action, not as an execution failure.
- Sensitive/external calls show a one-time Allow/Deny dialog. The dialog times out after 30 seconds and denies by default; approval is never inferred from model output.
- App visibility is limited to launcher activities declared through Android package-visibility queries; the adapter does not request broad package inventory access.
- Android features must be exposed as narrow tools with explicit argument schemas and permission checks.
- Tool handlers must return verified results only when the platform confirms the operation. Otherwise return `ToolResult.unverified` and the engine stops.
- Sending messages, payments, deleting/sharing files, account changes and other external or irreversible actions must require an explicit user confirmation in the UI. Never auto-approve from model output.
- Background jobs require durable persistence and lifecycle-aware scheduling; this engine currently runs in-process and is not a durable WorkManager job system.

## Android adapter added in this increment
- `JarvisAndroidToolAdapter` registers read-only battery status, allow-listed settings panels, exact-package app launch, one-step media volume controls, and web search.
- UI/framework calls are marshalled to the Android main thread with a bounded wait; timeout, interruption and activity teardown fail closed.
- Web search is tagged `EXTERNAL`, so the agent engine's approval gate must approve it before launch.
- No shell, arbitrary intent action, package installation, message sending, payment, or unrestricted device setting is exposed.
- The registry is created and populated by `MainActivity`; provider-native planning and a user-facing approval dialog are still pending, so these tools are not yet autonomously invoked from chat.

## Current end-to-end agent path
- For explicit app-open/launch and web-search requests, MainActivity sends the objective to Gemini first and Groq as fallback using native function/tool calling.
- The planner exposes only registered tool schemas, validates every returned tool name and argument, and creates a bounded typed plan.
- The engine runs the tool on its Android adapter. Sensitive/external steps wait for a one-time approval dialog; timeout, dismissal, app backgrounding, or activity teardown denies/cancels.
- Tool results are checked and shown to the user. The app does not yet send tool results back to the model for a second reasoning turn.

## Not yet wired / next increments
- Provider-native function calling and structured plan generation from Gemini/Groq/OpenAI-compatible models.
- Multi-turn tool-result handoff back to the model, multi-action planning, and natural-language post-action synthesis.
- Wider command-intent coverage beyond explicit app-open/launch requests.
- Durable task journal, WorkManager recovery and notification controls.
- Web search connector and source-citation pipeline.
- Vision/document tools and sandboxed code/data workspace.
- Streaming response transport, speech barge-in and real-device latency/battery evaluation.
- Integration tests across provider failures and Android API levels.

## Explicit non-goals
- No shell access, arbitrary reflection, dynamic code loading or unrestricted file access.
- No modification of personal speaker recognition, wake-word models, ONNX assets or wake-word thresholds.
- No claim of ChatGPT Plus parity or autonomous execution until provider planning, tool integrations and device tests are complete.
