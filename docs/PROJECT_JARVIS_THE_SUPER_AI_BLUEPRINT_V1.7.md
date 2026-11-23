# PROJECT JARVIS — THE SUPER AI
## Master Architecture Blueprint V1.7

**Status:** Architecture baseline / planning document  
**Repository:** `rohity573456-dev/JARVIS-V10.4`  
**Base:** Existing Android application; this project upgrades the current app, not a greenfield rewrite.  
**Budget target:** Free-first hybrid architecture. Provider quotas and free-tier availability must be verified at implementation time.  
**Non-negotiable:** Preserve the existing personal ONNX wake-word/voice-recognition assets and pipeline. Do not retrain, replace, rename, or modify them as part of brain work.

---

## 1. Mission

Build a highly capable personal AI assistant that combines natural conversation, reasoning, long-term memory, research, coding support, multimodal understanding, and permissioned Android task execution.

The target is to exceed a conventional chat assistant in personal continuity, device integration, project awareness, and reliable task completion. Do not claim that modular software can make a small/free foundation model equal to a frontier model. Measure actual quality against repeatable benchmarks.

This is an independent implementation of user-facing capabilities. It does **not** copy GPT-6 weights, proprietary source code, hidden prompts, or undisclosed internal architecture.

## 2. Existing repository assets to preserve and build upon

The current `main` branch at the time this document was created contains:
- Android application in Java/Kotlin.
- Gemini and Groq-compatible online model client.
- Optional LiteRT-LM local inference path.
- SQLite-backed long-term memory store, extraction/AI worker, semantic/embedding helpers.
- Existing ONNX wake-word assets and voice pipeline.
- Android command/device integrations and unit tests.

These are source-level observations, not proof that every feature works correctly on every device. Verify behavior with tests and device runs before relying on it.

**Protected voice assets:** `app/src/main/assets/openwakeword/` and the existing ONNX wake-word inference/verification pipeline. Any future voice work must integrate around these components and must not alter the user's personal model.

## 3. Architecture principles

1. **One orchestrator, specialist modules:** 107 divisions are capability domains, not 107 models or services.
2. **Model-agnostic interfaces:** Gemini, Groq and local models sit behind provider adapters; model IDs and quotas remain configurable.
3. **Task-based routing:** Route by intent, complexity, modality, latency, privacy and quota—not by fixed provider preference alone.
4. **No error-only fallback:** A successful HTTP response can still be irrelevant or low quality. Use bounded quality checks and fallback only when justified.
5. **Context is curated:** Retrieve only relevant conversation history and memory; never inject the entire database on every request.
6. **Deterministic tools for deterministic work:** Use code for calculations, parsing, device state and validation instead of asking an LLM to guess.
7. **Permissioned actions:** Separate answering from acting. Confirm sensitive, destructive, financial, external-communication, or privacy-impacting actions.
8. **Evidence and uncertainty:** Distinguish verified facts, model inferences, guesses and unavailable information.
9. **Observable and testable:** Log routing decisions, latency, token usage, failures and tool outcomes without logging secrets or unnecessary personal data.
10. **Incremental compatibility:** Preserve existing features and data; use migrations, feature flags and rollback paths.
11. **Free-first, not free-only claims:** Respect rate limits and quotas; degrade gracefully to local/basic functions when providers are unavailable.
12. **No silent self-modification:** Learning may improve memory, prompts, routing configuration and strategies under tests; source-code/model changes require review and rollback.

## 4. Central system layout

```text
User text / voice / image / document
              |
       Input & Session Layer
              |
     Intent + Safety Classifier
              |
       Cognitive Orchestrator
       /       |        \
 Context    Model       Tool/Agent
 Manager    Router      Planner
    |          |           |
 Memory    Gemini/Groq   Permission
 Retrieval  /Local LLM    Gateway
       \       |          /
        Reasoning & Task Engine
                  |
          Response Verifier
                  |
       Conversational Renderer
                  |
       Answer / Approved Action
                  |
       Memory Write & Metrics
```

### Core contracts

- **Intent result:** intent, entities, constraints, confidence, ambiguity, requested output and whether this is a device command.
- **Model route:** provider/model, reason for selection, budget, timeout, fallback plan and privacy class.
- **Memory retrieval:** ranked records with provenance, confidence, timestamps and sensitivity labels.
- **Tool execution:** typed arguments, permission decision, confirmation state, result and audit record.
- **Final answer:** answer, uncertainty, citations/evidence when applicable, completed-action status and memory-write candidates.

## 5. Capability catalogue — 107 divisions

### 1–16: Original cognitive foundation
1. Core Intelligence — reasoning, logic, critical thinking, causal and abstract problem solving.
2. Conversational Intelligence — natural Hinglish, context, tone, humour, follow-ups and adaptive response length.
3. Advanced Cognition — multi-hypothesis analysis, critique, simulation, contradiction checks and confidence.
4. Memory & Learning — working, short/long-term, episodic, semantic, retrieval and consolidation.
5. Autonomous Agents — planning, tools, multi-step execution, monitoring, recovery and approval gates.
6. Knowledge Intelligence — research, coding, maths, documents, data and scientific information.
7. Multimodal Intelligence — image, audio, video, OCR and document understanding.
8. Real-World Awareness — permitted device state, date/time, weather, calendar and current information.
9. Android Intelligence — app/device control, screen context, files and supported automation.
10. Creative Intelligence — writing, diagrams, reports, presentations and creative tool integration.
11. Meta Intelligence — strategy selection, self-evaluation and adaptive cognitive control.
12. Truth & Verification — evidence, fact-checking, uncertainty and hallucination detection.
13. Security Intelligence — privacy, prompt-injection defense, key safety, permissions and audit.
14. Self-Optimization — latency, token use, model health, quality and resource optimization.
15. Advanced Cognitive Systems — cognitive workspace, world models, causal and temporal reasoning.
16. Command & Action Intelligence — voice commands, intent detection, Android action execution and recovery.

### 17–33: Proactive, personal and specialist systems
17. Proactive Intelligence Engine.
18. Personal Digital Twin (user-controlled preference/workflow representation; not a hidden identity profile).
19. Real-Time Voice Intelligence.
20. Screen & Computer Vision Engine.
21. Scientific & Research Intelligence.
22. Financial Intelligence (analysis only unless explicitly approved for transactions).
23. Project Management Intelligence.
24. Developer Intelligence (repository analysis, tests, reviews and build diagnostics).
25. Digital Security Intelligence.
26. Multimodal Creation Engine.
27. Personal Knowledge Assistant.
28. Environment Awareness Engine.
29. Simulation & Prediction Engine.
30. Education & Teaching Engine.
31. Notification Intelligence.
32. Advanced Search Engine.
33. Digital Continuity Engine.

### 34–50: Advanced reasoning and long-horizon cognition
34. Neural Reasoning Architecture.
35. Meta-Learning Engine.
36. World Model Engine.
37. Causal Discovery Engine.
38. Temporal Intelligence Engine.
39. Swarm Intelligence Engine.
40. Knowledge Synthesis Engine.
41. Scientific Discovery Assistant.
42. Adaptive Cognitive Control.
43. Uncertainty & Risk Intelligence.
44. Long-Horizon Agent Engine.
45. Self-Testing Intelligence.
46. Recursive Task Decomposition.
47. Semantic Understanding Engine.
48. Predictive Maintenance Intelligence.
49. Personal Decision Intelligence.
50. Multi-Modal Fusion Engine.

### 51: Complete Cognitive Memory System
51. Complete Cognitive Memory Architecture — working, short-term, long-term, episodic, semantic, procedural, preference, profile, project, relationship, temporal, contextual, interaction, goal/task, error/correction, knowledge, summary, event, decision and environmental memory. Includes extraction, importance/confidence scoring, deduplication, consolidation, source provenance, contradiction resolution, expiry, user review/edit/delete, privacy controls, semantic retrieval, associative recall, context reconstruction, project isolation, backup and restoration.

### 52–66: Foundation model and quality systems
52. Foundation Model Intelligence — provider adapters, model capability registry, instruction-following benchmarks and model health.
53. Deep Reasoning & Verification — multi-path problem solving, proof/checking where applicable and evidence validation.
54. Instruction Following Intelligence — constraints, multi-part requests, ambiguity and correction handling.
55. Native Multimodal Understanding — coordinated text/image/audio/video/document interpretation.
56. Real-Time Audio Intelligence — streaming, interruption, VAD coordination, low latency and speech output.
57. Advanced Computer & Device Use — screen/UI understanding, navigation, action checks and recovery.
58. Deep Research & Evidence Engine — multi-source retrieval, source quality, citations and conflict resolution.
59. Advanced Coding & Software Engineering — repository comprehension, edits, tests, debugging and regression checks.
60. Dynamic Intelligence & Compute Management — adaptive reasoning depth, model routing and token/latency budgets.
61. Alignment & Behavioral Intelligence — honesty, safe boundaries, consent and action permissions.
62. Output Quality Intelligence — answer structure, clarity, tone, concise/detailed modes and format selection.
63. Long-Context Intelligence — summarization, context compression, retrieval and multi-document continuity.
64. Tool Intelligence & API Ecosystem — discovery, typed calls, retries, error diagnosis and result verification.
65. Continuous Evaluation & Benchmarking — reasoning, coding, memory, voice, routing, safety and regression metrics.
66. Adaptive Learning & Model Improvement — benchmark-guided updates to retrieval, routing and strategies with review/rollback.

### 67–71: Super-AI coordination layer
67. Super Intelligence Orchestrator — coordinates specialist capabilities and selects a fit-for-task plan.
68. Continuous Intelligence Improvement — controlled, benchmark-led system improvement.
69. Universal Personal Agent — permissioned end-to-end digital task workflows.
70. Frontier Intelligence Routing — selects the best available model for a task under quality, privacy, latency and quota constraints.
71. Unified Intelligence Core — shared state and interfaces for reasoning, memory, perception, planning and action.

### 72–96: Next-level expansion
72. Multi-Brain Intelligence — specialist model comparison and evidence-based consensus.
73. Strategic Thinking Engine — long-term consequences and trade-off analysis.
74. Future Prediction Engine — scenario simulation with explicit uncertainty.
75. Self-Improving Intelligence — improve prompts, routing and workflows through measured feedback; no uncontrolled source self-editing.
76. Universal Knowledge Connection — cross-domain relationship discovery.
77. Personal Life Operating System — user-controlled tasks, schedules, projects and routines.
78. Anticipation Engine — relevant proactive reminders and suggestions with user controls.
79. Personal History Intelligence — recall past decisions and lessons with provenance.
80. Universal Device Intelligence — permissioned coordination of supported devices and apps.
81. Mission Control System — break goals into tracked missions and milestones.
82. Specialist Agent Workforce — researcher, coder, analyst, planner and verifier roles.
83. Workflow Automation Intelligence — reusable, user-approved workflows.
84. Autonomous Debugging Engine — reproduce, diagnose, propose fix and run tests.
85. Digital Guardian — detect risky permissions, suspicious behavior and unsafe actions.
86. Virtual Testing Laboratory — sandbox/simulation before consequential actions.
87. Human-Level Conversation Engine — natural turn-taking, interruption and context-aware style.
88. Visual Awareness Engine — permissioned camera/screen interpretation.
89. Unified Workspace Intelligence — connected chat, code, files, research and tasks.
90. Universal Language Intelligence — multilingual conversation and translation.
91. Continuous Context Awareness — continuity across sessions and restarts.
92. AI Council — specialist agents challenge and review each other's conclusions.
93. Digital World Model — representation of accessible digital environment state.
94. Goal Discovery Intelligence — suggest useful next steps from explicit goals and unfinished work.
95. Autonomous Quality Inspector — verify completion and find omissions.
96. Intelligence Evolution Framework — evaluate and safely adopt improved strategies.

### 97–103: Repository audit-driven upgrades
97. Intelligent Intent Router — resolve the user's actual intent before keyword/device-command handlers; disambiguate mixed requests and avoid accidental actions.
98. Central AI Orchestrator — one entry point for model, memory, tools, agents, budgets and verification.
99. Advanced Context Manager — bounded conversation history, relevant memory retrieval, summarization and token budgeting.
100. Multi-Turn Conversation Engine — maintain turn state, references, corrections and follow-up continuity across sessions.
101. Response Quality Verifier — detect irrelevant, incomplete, contradictory or unsupported outputs with bounded verification.
102. Intelligent Model Fallback — fallback for provider errors, timeouts, invalid outputs or low-confidence/low-quality answers when the expected benefit justifies extra quota.
103. AI Performance & Token Optimizer — track latency, token use, quotas, retries, caching and graceful degradation.

### 104–107: GPT-style brain and memory capabilities
104. Advanced Cognitive Brain — deep reasoning, contextual understanding, multi-step analysis, solution comparison, self-critique and uncertainty communication.
105. ChatGPT-Style Memory Intelligence — automatic memory extraction, cross-conversation recall, personal/project memory, associative retrieval, consolidation, conflict handling and user-controlled deletion.
106. Conversational Personality Engine — natural Hinglish/multilingual conversation, tone adaptation, follow-up understanding, non-robotic responses and honest correction.
107. Cognitive Self-Evaluation Engine — answer checking, hallucination detection, knowledge-gap detection, calibrated confidence and benchmark-led improvement.

## 6. Implementation strategy: build the foundation before the feature catalogue

Do not implement 107 divisions independently. Implement shared primitives once and expose them to capability modules.

### Phase 0 — Baseline and safety
- Confirm current default branch, commit, Gradle/Android configuration and existing tests.
- Map all current command handlers, AI call sites, memory flows and voice dependencies.
- Establish a baseline build/test report and protect voice-model files with hashes.
- Add a feature inventory and regression checklist.
- No UI rewrite during brain foundation work.

### Phase 1 — Brain foundation (first engineering milestone)
- Introduce typed request/response contracts and a central orchestrator.
- Add an intent classifier that distinguishes conversation, information lookup and device action.
- Ensure broad technical questions are not intercepted by incidental words like “battery”, “time”, “storage” or “system report”.
- Put deterministic device commands behind high-confidence intent plus permission/confirmation rules.
- Centralize Gemini/Groq/local model routing, timeouts, error classification, quota budgets and bounded fallback.
- Preserve current provider settings and user-configured model IDs.
- Add structured diagnostics with secrets and personal content redacted.

### Phase 2 — Conversation and context
- Add session-aware multi-turn message construction.
- Preserve role boundaries and compact older turns into summaries.
- Retrieve relevant memory by semantic and keyword signals, recency, confidence and project scope.
- Inject only a small, relevant context package.
- Support references, corrections, follow-up questions and uncertainty.

### Phase 3 — Memory upgrade
- Audit existing SQLite schema before migration.
- Reuse and extend current memory records rather than creating a competing database.
- Add source/provenance, sensitivity, timestamps, confidence and lifecycle fields where missing.
- Add memory review, edit, deletion and temporary/no-save controls.
- Test false-memory resistance, duplicate detection, contradiction handling and retrieval precision.
- Never store secrets or sensitive data by default.

### Phase 4 — Verification and specialist agents
- Add task-specific answer checks, evidence validation and tool-result verification.
- Use multiple models/agents only for complex or high-impact tasks; avoid routine multi-model calls.
- Add research, coding, planning and verifier workflows with explicit budgets and stop conditions.
- Keep actions permissioned and auditable.

### Phase 5 — Multimodal and Android autonomy
- Expand image/document/screen understanding behind explicit permissions.
- Add safe Android action adapters and UI automation only where Android APIs and permissions allow.
- Keep voice recognition and personal ONNX wake-word model unchanged.
- Improve TTS/streaming around the existing recognition pipeline, not by replacing it.

### Phase 6 — Evaluation and controlled evolution
- Run regression tests after each module.
- Measure answer relevance, instruction adherence, memory precision/recall, task completion, false actions, latency, tokens and provider failures.
- Compare against a fixed test suite; do not claim “better than ChatGPT” without repeatable, domain-specific results.
- Ship changes incrementally with rollback paths.

## 7. First acceptance tests

1. A technical self-audit prompt containing the word “battery” must be treated as a technical question, not a battery command.
2. “Battery kitni hai?” must invoke the battery report only when the user clearly asks for device status.
3. A mixed request such as “explain battery health and tell me how to improve it” must answer conversationally and not execute unrelated settings actions.
4. Follow-up references such as “usmein kya missing hai?” must resolve to the current project context.
5. A memory recall answer must identify whether the fact is stored, inferred or unknown.
6. A provider HTTP success containing irrelevant output must not be blindly treated as a good answer.
7. If one provider hits a quota/token error, the app must use a bounded fallback or explain the limitation without retry loops.
8. No test or brain change may modify the protected ONNX model assets or voice-recognition pipeline.
9. Sensitive Android actions require explicit permission/confirmation and must report actual execution results.
10. Memory deletion and temporary mode must prevent later retrieval of the deleted/unsaved item.

## 8. Definition of done

A capability is not “implemented” merely because a class, button, prompt or configuration key exists. It is:
- **Implemented** when code path exists and is integrated.
- **Verified** when automated tests cover expected and failure behavior.
- **Device-validated** when tested on a real supported Android device.
- **Production-ready** only after privacy, permission, performance and regression checks pass.

Every project report must label each capability as:
- Implemented
- Partially implemented
- Planned
- Unknown / not verified

## 9. Immediate next actions

1. Complete a source-level dependency map of `MainActivity`, `OnlineAiClient`, memory classes, local inference and voice service.
2. Inspect current intent routing and all command interception paths.
3. Write focused tests for the battery/self-audit collision and related ambiguous requests.
4. Design the orchestrator interfaces without changing the ONNX assets or UI.
5. Implement Phase 1 on a dedicated feature branch, run tests/build, and review the diff before merging to `main`.

**Project rule:** Preserve the current app. Upgrade in small, testable modules. No feature is allowed to break voice recognition, memory data, existing Android commands or the user's current workflow.


## GPT-6.2 Astra integration (optional premium tier)

- JARVIS does not contain or copy OpenAI's proprietary model weights, hidden source code, or private internal memory implementation.
- The app includes an independent Responses API adapter for the public GPT-6.2 Astra model ID `gpt-6.2-astra`.
- Gemini/Groq remain the everyday and fallback providers. Astra is selected for complex coding, research, data-analysis and planning requests when an OpenAI API key is configured; the explicit `Astra ...` prefix can request Astra for a general question.
- The same optional Astra adapter can produce structured function-call plans for the existing Android agent. The existing local schema validation and user approval gate remain mandatory; model output never executes a device action directly.
- GPT-6.2 Astra API usage is paid separately from a ChatGPT subscription. Do not make it the default for routine turns; preserve a free-first route and expose the cost implication in provider setup.
- The OpenAI key is stored through the existing Android Keystore-backed SecureStore. Never commit keys to GitHub or embed them in source.
- Astra-assisted memory extraction/reflection is available only through the existing explicit cloud-memory consent. Local SQLite remains the source of truth for profile facts, semantic/procedural/episodic memory, session summaries, temporal validity, knowledge-graph relations and retrieval.
- Private mode must suppress cloud memory extraction and memory-context injection. Sensitive categories remain excluded from automatic extraction. The user must be able to disable cloud learning and clear local memory independently.
- The Responses API adapter uses the documented `reasoning.effort` field; complex tasks may use high effort while routine tasks remain on Gemini/Groq/local fallback.

## Conversation, routing and token-safety implementation update

- Conversation turns are persisted in the existing SQLite conversations table; each user request receives up to 12 recent chronological turns (six user/assistant pairs), relevant long-term facts, pinned profile facts, session digests and related knowledge-graph context.
- Context ordering is deterministic for equal timestamps. If memory context must be shortened, preserve its useful opening facts and newest conversational tail instead of cutting off the latest turn.
- A bounded context composer keeps follow-up instructions, prevents unsupported memory claims, and suppresses saved memory, legacy notes and previous-turn context in private mode.
- Routing policy: optional Astra first for configured complex specialist work; Groq is preferred for coding/data-analysis/planning when available; Gemini is preferred for general/research; the other configured cloud provider is the fallback; local inference is the final offline fallback.
- Cloud prompts use a conservative 9,000-character standard budget and 1,536-token response ceiling to reduce provider TPM/context failures. Astra uses a separate bounded context budget. Truncation preserves both the earlier context prefix and latest user-turn tail.
- All providers receive a silent answer self-check instruction. Complex Astra specialist responses may receive a second-pass audit; do not spend a second API call on routine conversation.
- These changes are automated-test covered but remain pending latest CI completion and real-device validation. Character/token estimates are protective approximations, not exact tokenizer measurements.

## Implementation audit log — 23 November 2026

### Audit pass 1 — baseline and intent-routing safety

**Scope:** Source-level inspection of the active development branch. This is not a full 107-division audit and is not device validation.

**Confirmed source evidence**
- Android app baseline: app/build.gradle uses Java 17, compile SDK 35, min SDK 24, target SDK 35, app version 10.4.1-openwakeword, and arm64-v8a ABI.
- Existing local intent gates: JarvisIntentRouter provides narrow time/date/battery/system-report and explicit device-command matching; MainActivity.handle() uses these gates for foreground requests.
- Conversation context: JarvisConversationContext is wired into the foreground AI path. JarvisMemoryStore persists conversations and combines pinned facts, relevant memories, graph relations, session summaries and recent turns.
- Provider routing: JarvisHybridBrain routes between configured Astra, Gemini, Groq and local inference. Current classification is keyword-based and should not yet be treated as a complete intent/complexity classifier.
- Memory: existing SQLite implementation includes profile facts, temporal records, graph edges, semantic/cloud embeddings, local feature vectors, summaries, tasks and transient notes. This does not yet prove all 20 memory types or lifecycle guarantees in Division 51.
- Voice: WakeWordService retains the existing ONNX/OpenWakeWord pipeline. No ONNX asset was changed by this audit pass.
- Voice evidence limitation: docs/VOICE_ENGINE_IMPLEMENTATION_STATUS.md explicitly records that real-phone accuracy, battery/latency evidence and full background command parity still require validation.

**Issue found and corrected**
- Before this pass, WakeWordService.executeBackgroundCommand() used broad substring checks for time, date, battery, volume and flashlight. A technical question mentioning one of those words could be intercepted as a device action.
- Updated the background voice path to use the same strict JarvisIntentRouter predicates as the foreground path for those local actions.
- Added unit-test cases covering explanatory battery/time/flashlight/volume questions and explicit commands.
- **Verification state:** source changes committed to the active feature branch; the next GitHub Actions run must pass before this fix is marked verified. Real-device voice testing remains pending.

**Preliminary division status (source-level only)**
- **Partially implemented; further audit required:** 4 (Memory & Learning), 7 (Multimodal Intelligence), 9 (Android Intelligence), 13 (Security Intelligence), 16 (Command & Action Intelligence), 24 (Developer Intelligence), 51 (Complete Cognitive Memory Architecture), 52 (Foundation Model Intelligence), 60 (Dynamic Intelligence & Compute Management), 64 (Tool Intelligence & API Ecosystem), 69 (Universal Personal Agent), 70 (Frontier Intelligence Routing), 97 (Intelligent Intent Router), 98 (Central AI Orchestrator), 99 (Advanced Context Manager), 100 (Multi-Turn Conversation Engine), 101 (Response Quality Verifier), 102 (Intelligent Model Fallback), 103 (AI Performance & Token Optimizer), 104 (Advanced Cognitive Brain), 105 (ChatGPT-Style Memory Intelligence), 106 (Conversational Personality Engine), 107 (Cognitive Self-Evaluation Engine).
- **Not yet audited:** all other divisions. “Not yet audited” does not mean absent; each must be checked against source, tests and device behavior.
- No division is marked fully complete or production-ready by this pass.

**Next audit sequence**
1. Complete the source dependency map for command routing, model clients, agent planner, memory extraction/retrieval and voice.
2. Verify all 107 divisions against implementation, automated tests and device evidence.
3. Prioritize central orchestrator/intent safety, memory lifecycle/privacy, tool permission gates and answer-quality/fallback behavior.
4. Keep each capability status explicit: Planned, Partially implemented, Implemented, Verified, Device-validated or Production-ready.

### Audit pass 2 — memory privacy and agent safety boundaries

**Memory consent and data flow confirmed**
- MainActivity calls AI memory extraction only when the explicit memory_ai_learning setting is enabled; enabling it displays a cloud-processing consent dialog.
- Private mode blocks new conversation persistence, automatic AI extraction and background AI reflection.
- The OpenAI key is passed through the same encrypted SecureStore path; Gemini/Groq/OpenAI are optional providers.
- Local memory remains SQLite-backed. Cloud semantic embeddings are a separate opt-in setting.

**Issues found and corrected**
- AI-generated memory metadata was previously trusted too broadly: category, key, confidence, importance, pinned flag and graph relation strings were accepted with limited deterministic validation.
- AI-generated session summaries were saved without a final local sensitivity/length check.
- Added deterministic local guards before writes:
  - Reject sensitive categories (health, medical, religion, political, sexual, criminal, credential, government-ID, biometric and financial-identifier categories).
  - Reject known sensitive phrases and common government-ID patterns in memory text, keys, summaries and graph relation values.
  - Bound key/category/graph-field lengths; clamp confidence to 0..1 and importance to 0..100.
  - Model output cannot pin arbitrary memories. Pinning is restricted to approved durable key families such as profile.name, instruction.*, preference.*, project.current and goal.primary, and never sensitive categories.
  - Session summaries are only persisted if non-empty, at most 600 characters and locally pass the sensitive-text filter.
- Added JarvisMemoryPrivacyTest coverage for sensitive categories, Aadhaar/PAN-like identifiers, credentials, health terms and pin restrictions.

**Agent boundary review**
- JarvisAgentEngine limits each run to eight total actions and one retry per failed action; each follow-up planning turn receives only locally verified previous tool results.
- JarvisToolRegistry validates registered tool names and required/optional arguments before invoking handlers; model-provided code is never executed.
- SENSITIVE and EXTERNAL tools require the approval gate. Current Android registry includes battery read, settings panel, installed-app launch, media-volume control and external web search.
- The permission string on ToolSpec is descriptive metadata; platform permissions are still enforced by Android handlers/runtime. This is an area for a dedicated permission audit.
- Web search is explicitly gated by user intent and EXTERNAL approval; no arbitrary shell or unrestricted filesystem tool is registered.

**Verification state**
- Source changes and new unit tests committed on the active feature branch.
- CI for the latest commits has not yet been confirmed by the available workflow status query. Do not mark these changes verified until GitHub Actions passes.
- No device validation performed.

**Next audit targets**
1. Review all memory lifecycle operations (deletion, history, session queues, embeddings and private-mode transitions) for stale asynchronous writes and data-retention gaps.
2. Review Android agent approval timeout, cancellation and activity lifecycle behavior.
3. Inspect foreground/background voice parity after the first routing fix.
4. Complete all remaining divisions with evidence-backed status labels.

### Audit pass 3 — asynchronous cloud-memory consent race

**Issue found**
- Cloud-memory consent and private mode were checked before enqueueing background extraction/reflection jobs, but a queued job could continue after the user switched private mode on or disabled AI memory learning.

**Correction committed**
- Added a live consent-gate callback to AI memory extraction and reflection.
- Jobs now re-check consent before starting provider requests, before storing extracted facts, before saving session summaries, and before each reflected session.
- Legacy worker overloads now fail closed unless a caller supplies an explicit live consent gate.
- MainActivity supplies a gate that reads the current memory_ai_learning and memory_private_mode settings. The same protection now covers both normal user messages and saved-note extraction.

**Verification**
- Source committed to the active feature branch. Fresh GitHub Actions result is not yet available/confirmed.
- Runtime race testing (turn private mode on while a cloud request is queued/in flight) remains a required device/integration test.

### Audit pass 4 — memory deletion, retention and index integrity

**Issues found**
- Deleting one memory removed its cloud embedding and feedback but left its local vector row behind.
- Expiring temporary memories did not remove feedback rows and did not wrap the full cleanup in a transaction.
- Clearing conversation history and clearing all saved facts were multi-table operations without transaction boundaries.
- Conversation retention capped turns but did not prune sessions, AI reflection markers and session summaries whose turns had been evicted.
- Background embedding requests could finish after a memory was deleted and recreate an orphan embedding row.

**Corrections committed**
- Single-memory deletion now atomically removes graph edges, cloud embeddings, local vectors, feedback and the memory row, then prunes orphan graph nodes.
- Temporary-memory expiry now atomically removes all dependent indexes and feedback.
- Cloud and local vector writes now verify inside the write transaction that the source memory still exists and is active. Late results for deleted/inactive memories are discarded.
- Conversation insertion, session bookkeeping, 3,000-turn retention and orphan-session pruning now run in one transaction.
- Session pruning removes stale reflection markers and both extractive/AI session summaries after the source session is removed.
- Clear-history and clear-all-facts operations now use transactions and remove their dependent summary/reflection data consistently. Chat history and saved facts remain separate user controls.
- Added memory integrity diagnostics for orphan cloud/local vectors, feedback, graph edges and sessions; added a user-invokable Memory Center repair action.
- Clearing history, clearing all facts, or enabling Private Mode increments a memory-generation token. Queued AI extraction/reflection callbacks compare their captured generation before provider requests and writes, preventing old queued work from repopulating cleared data.


- Follow-up hardening: cloud semantic embedding jobs now require a live consent callback and re-check it before each provider request and before storing each vector. The callback also checks Private Mode and the memory-generation token. Turning embeddings off, deleting the vector index, clearing history/facts or enabling Private Mode invalidates queued work. The legacy embedding-worker overload fails closed.
- All foreground voice/text privacy commands now invalidate queued work as well as the Memory Center controls.

- Clear-history confirmation is rendered/spoken without persisting the confirmation turn, so the just-cleared history remains empty until the next real conversation turn.

- Final race-window hardening: the live consent gate is checked inside AI fact extraction loops before each individual fact write and before graph relation writes, not only once per provider response.

**Verification state**
- Source changes committed to the active feature branch.
- Automated Android SQLite lifecycle tests and fresh CI are still required. The repository currently has only plain JUnit configured; SQLite runtime tests cannot be honestly claimed from source inspection alone.
- Real-device tests remain pending: delete memory during embedding request, clear history during AI reflection, turn Private Mode on with queued jobs, expire transient note, and inspect Memory Health before/after repair.


### Audit pass 5 — SQLite lifecycle integration coverage

**Test implementation**
- Enabled Android-resource-aware local unit tests and added Robolectric 4.14.1 so memory-store behavior is exercised against Android SQLite rather than only mocked/plain Java objects.
- Added `JarvisMemoryLifecycleSqliteTest` with real database setup/teardown and six lifecycle scenarios:
  1. Single-memory deletion removes the fact and every dependent vector, feedback and graph index.
  2. Late cloud/local embedding writes cannot recreate indexes after source-memory deletion.
  3. Expiring temporary memories removes the fact and dependent indexes/feedback, and repeated expiry is safe.
  4. Clearing conversation history removes conversations, sessions and session summaries while preserving durable facts.
  5. Clearing saved memories removes facts and indexes while preserving chat history.
  6. Integrity repair removes orphan vectors, feedback, graph edges/nodes and stale session metadata.
- Added a Google-hosted Maven Central mirror ahead of Maven Central to reduce CI failures caused by transient HTTP 429 responses.

**Verification state**
- A GitHub Actions run completed successfully on the immediately preceding branch revision, executing `gradle testDebugUnitTest` and reporting `BUILD SUCCESSFUL`.
- A newer run is validating the latest memory-prompt and agent retry changes; its result must be checked before treating the current branch as green.
- Robolectric validates Android SQLite behavior in CI, but it does not replace real-device validation of process death, app lifecycle, concurrent provider requests or OS permission behavior.
- No physical-device validation is claimed.

### Audit pass 6 — agent side-effect retry safety

**Issue found**
- The agent engine previously retried every failed tool once. For an external or side-effecting operation, a timeout/lost response can occur after the action actually happened; an automatic retry could duplicate the action.

**Correction committed**
- Automatic retry is now limited to READ_ONLY tools, where repeating the operation cannot duplicate a user-visible side effect.
- REVERSIBLE, SENSITIVE and EXTERNAL tools are never automatically retried after failure. They stop and return a failure report for user-directed recovery.
- Added a regression test proving an EXTERNAL send-like tool is invoked exactly once when it returns a failure after a simulated lost response.

**Remaining verification**
- Latest CI is pending for this change.
- Device tests remain necessary for activity destruction during approval, delayed Android main-thread dispatch, and cancellation while a platform action is already executing.
