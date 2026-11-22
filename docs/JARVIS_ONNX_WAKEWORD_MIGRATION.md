# JARVIS V10.4 — Free ONNX Wake-Word Migration

## Goal

Replace the current Sherpa-ONNX keyword-spotting runtime with a locally executed ONNX Runtime pipeline that detects exactly the spoken wake word **"Jarvis"**.

Constraints:
- No Picovoice, paid API, subscription, cloud wake-word service, or mandatory access key.
- Keep microphone capture on-device.
- Preserve the existing command-recognition handoff, TTS acknowledgement, AI providers, local AI, memory, and background command handling.
- Keep the current main branch buildable while the replacement is being validated.

## Current baseline (main)

The current `WakeWordService` uses Sherpa-ONNX classes and a streaming Zipformer/GigaSpeech keyword model. The GitHub Actions workflow downloads the Sherpa Android AAR and its KWS model. The keyword text was changed to `JARVIS`, but changing the keyword text does **not** create or train a true single-word Jarvis acoustic model.

Do not describe the current detector as a verified custom Jarvis model. Do not remove Sherpa until the replacement is integrated and the app builds.

## Target architecture

```text
AudioRecord (16 kHz mono PCM)
        |
        v
Audio conditioning / voice activity handling
        |
        v
Wake-word feature pipeline required by the selected model
        |
        v
Microsoft ONNX Runtime for Android (CPU first)
        |
        v
Custom, compatible Jarvis-only ONNX classifier
        |
        v
Existing wake acknowledgement + command capture
        |
        v
Existing JARVIS command / AI / memory code
```

The classifier alone is not necessarily the complete wake-word system. The exact feature extractor, embedding model, tensor names, tensor shapes, sample rate, frame/window sizes, normalization, and score threshold must match the model's training/export pipeline.

## Candidate runtime

Evaluate a maintained Android implementation that supports custom openWakeWord-compatible ONNX classifiers, with Microsoft ONNX Runtime Android as the execution backend. Before adopting any third-party wrapper:
1. Verify its source, Android minimum SDK, ABI support, license, and current dependency coordinates.
2. Confirm whether it bundles its own ONNX Runtime or expects the app to supply it. Avoid duplicate ONNX Runtime native libraries.
3. Inspect the exact input/output tensor metadata of the selected model and compare it with the runtime's expected contract.
4. Confirm that every required feature/embedding model is available locally and has a license compatible with the intended use.

Do not add two competing ONNX Runtime dependencies.

## Model requirement

A real single-word **Jarvis** classifier is required. Existing models trained for "Hey Jarvis" cannot be made into a reliable "Jarvis" detector by editing a text keyword file.

Preferred route:
- Train/export a custom Jarvis wake-word classifier using a documented, reproducible open-source workflow.
- Include positive examples from varied speakers, accents, microphones, distances, and speaking rates.
- Include hard negatives (similar-sounding words and ordinary conversation), silence, music, television, and noisy-room samples.
- Use augmentation such as room impulse responses, background noise, gain variation, and reverberation.
- Keep the feature/embedding model and classifier versioned together.
- Record model provenance, license, training recipe, threshold, and evaluation results.

A downloadable community model may be used only after its license and exact model/runtime compatibility are checked. A benchmark score from a model page is not a guarantee of performance on the user's phone.

## Required validation before cutover

### Model and runtime
- Load every ONNX model from app assets without network access.
- Verify tensor names, dimensions, data types, and inference outputs on Android arm64.
- Verify cold-start and repeated inference; handle model-load errors without crashing the service.
- Measure CPU use, memory, battery impact, and end-to-end wake latency.

### Wake-word quality
- Test "Jarvis" across quiet and noisy environments, near/far microphone distances, and different pronunciations.
- Test similar phrases and ordinary conversation for false activations.
- Tune threshold and debounce using a recorded evaluation set; do not tune solely from a few successful demonstrations.
- Confirm that "Hey Jarvis" is not required and that the target phrase is only "Jarvis".

### Android service
- Preserve the existing foreground microphone service and permission handling.
- Verify Android 15 background/lock-screen behaviour on the target device.
- Confirm the mic is released for command recognition and safely reacquired for standby.
- Confirm wake acknowledgement, command capture, TTS, and standby recovery.
- Add guarded recovery for audio-device disconnects and ONNX inference failures.

## Cutover policy

Do not remove the Sherpa AAR, Sherpa imports, Sherpa model download step, or current detector until all of the following are true:
1. A compatible Jarvis-only ONNX model and every required supporting model are available.
2. The ONNX pipeline is implemented and integrated into `WakeWordService`.
3. GitHub Actions compiles the debug APK successfully.
4. Static checks confirm there are no remaining Sherpa runtime references or duplicate native runtimes after cutover.
5. The APK has been installed and exercised on the target Android device.

Until then, the existing main branch remains the rollback-safe baseline. A successful CI build is not proof of real-world wake-word accuracy or reliable always-on operation.
