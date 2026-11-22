# JARVIS Hybrid AI Brain — Phase 1

## Implemented in this phase
- A dedicated hybrid routing layer: Gemini primary, Groq fallback, local LiteRT-LM fallback.
- Task-specialist routing labels for research, software engineering, data analysis, planning and general requests.
- Specialist-specific system directives with accuracy, uncertainty and tool-result honesty requirements.
- Provider request timeout raised to 90 seconds and configurable system prompts supported.
- Existing long-term memory context and optional cloud-vector retrieval remain injected by MainActivity.
- API credentials continue to be read from Android Keystore-backed SecureStore.
- Personal wake-word / speaker assets and ONNX files are not modified.

## Architecture target
1. Voice / text input layer.
2. Intent and specialist router.
3. Reasoning model selection: cloud primary, cloud backup, local model.
4. Memory retrieval and context assembly.
5. Tool planner and permission policy.
6. Typed Android / app / web / file tools.
7. Result verification, audit trail and spoken response.

## Not implemented yet
- True provider-native function/tool calling and execution loop.
- Web search provider integration with source citations.
- Android app functions / notification / call-screening capabilities.
- Sandboxed coding and data-analysis workspaces.
- Background task orchestration and durable job recovery.
- Streaming speech output and barge-in.
- Automated model evaluation and cost/latency routing.

## Safety and platform constraints
The router itself never performs Android actions. Phone actions must be implemented through narrow allow-listed tools and Android permissions. Payments, sending messages, deleting files, sharing data and other irreversible or externally visible actions need an explicit confirmation gate. Android microphone foreground-service restrictions still apply; see official Android documentation:
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/declare
