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
