# JARVIS Voice Engine — implementation status and hard limits

This document distinguishes implemented app code from features requiring trained model weights, vendor support, or measured evidence. It is not a claim of Siri-equivalent performance.

## Implemented in current source
- Streaming OpenWakeWord ONNX inference on 16 kHz mono PCM.
- Temporal confidence gate and adaptive second-chance window.
- Independent multi-variant Jarvis ONNX phrase verifier (not speaker identification and not a Conformer).
- Adaptive energy VAD, recent acoustic checks, PCM pre-roll, microphone recovery and audio-stall monitoring.
- Optional Android AEC / NoiseSuppressor when exposed by the device.
- Android 13+ EXTRA_AUDIO_SOURCE PCM handoff attempt; recognition providers may ignore the supplied source.
- Service-side command fallback while MainActivity is absent. It supports limited local actions and configured AI providers; it is not full parity with foreground app actions.
- Transcript guard strips an explicit leading Jarvis phrase, rejects wake-only output, and selects the best usable Android ASR text alternative when confidence values are available.

## Still not implemented / not honestly claimable
1. True DNN/HMM decoder: temporal score gating is not an HMM decoder.
2. Conformer second pass: the independent verifier is a keyword classifier, not a Conformer. Compatible trained weights, preprocessing, calibration and runtime integration are required.
3. Personal speaker identification: intentionally left untouched per project instruction. Do not overwrite or retrain Pratik's personal voice model.
4. Full-utterance directed-speech neural model: current VAD and transcript guard are lightweight checks, not a trained SDSD model.
5. ASR lattice FTM: Android SpeechRecognizer exposes text alternatives, not the internal token/acoustic lattice needed for Siri-style analysis. The guard selects among returned text alternatives only.
6. Semantic FTM: no separately trained semantic false-trigger model is bundled. General AI routing is not a safe substitute for a low-latency wake false-trigger classifier.
7. 4-bit quantization: no validated quantized replacement models or accuracy comparison are bundled.
8. AOP / always-on DSP: app code cannot create a hardware always-on processor. Requires chipset/vendor DSP or low-power KWS API and supported firmware. ONNX currently runs on the application processor.
9. Multiple independent wake phrases: current deployed pipeline is Jarvis-oriented. Independently calibrated aliases require model outputs/training for each phrase; model-family variants alone do not prove each phrase is separately enabled.
10. Boot auto-start microphone: Android 14/15 background-start restrictions limit microphone foreground-service launch from boot. Listening must resume through a user-initiated, OS-permitted flow.
11. Full background command parity: a service fallback exists, but only a subset of commands work without MainActivity. UI, camera picker and foreground-only tasks require the app to open.
12. Real-world accuracy / battery proof: benchmark protocol and CSV template exist; no 1,000-trial labeled result set or device battery/latency report has been recorded.

## Required acceptance testing
Test quiet/normal/far-field voice, TV/music playback, multiple speakers, Hindi/English, screen-off, app-swiped-away, battery saver, reboot and HyperOS restrictions on the target phone. Report recall, miss rate, false wakes per negative listening hour, median/P95 latency, CPU time and battery drain. Do not claim improvement until a held-out test set confirms it.
