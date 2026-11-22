# JARVIS V10.4 Advanced Memory Upgrade

## Included
- Structured profile, semantic, procedural and episodic facts with stable keys, confidence, importance, pinning and provenance.
- Temporal fact versions with valid-from/valid-until, date-scoped search and current-vs-historical graph edges.
- Multi-hop entity graph queries (up to three hops) and confidence-tagged edges.
- Session decomposition, local consolidation and opt-in AI reflection.
- AI fact extraction from new user statements, only after explicit consent and a configured Gemini/Groq provider.
- Hybrid recall: lexical/concept score, recency, importance, feedback, optional cloud embeddings and offline feature-hash vectors.
- Feedback commands, transient-only expiry path, and local memory context injected into the offline LLM.
- Unit tests for date parsing and local vector behavior.

## Commands
- memory profile
- memory at 2024 job
- memory useful 12
- memory wrong 12
- memory forget 12
- memory ai learning on / memory ai learning off (an in-app consent dialog appears)
- memory reflect / memory consolidate
- Existing memory pin/unpin/history/graph/stats/cloud embeddings commands remain.

## Model accuracy and privacy
The local 384-dimensional feature-hash index is deterministic and offline, not a neural model. True neural embeddings currently use Gemini only when cloud embeddings are enabled. A true fully-offline neural vector encoder still requires a compatible on-device embedding model asset and tokenizer; this upgrade does not pretend one is bundled.
