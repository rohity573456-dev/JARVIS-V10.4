# Train the single-word JARVIS wake model

This repository intentionally does not rename or reuse the bundled **"Hey Jarvis"** classifier. The production phrase is the single word **"Jarvis"**.

## Current status

- Android ONNX runtime adapter: present.
- Service integration: present behind a model-asset check; Sherpa remains the fallback.
- Trained `jarvis.onnx`: **not yet present**.
- Device validation: **not yet performed**.

Until the custom model is supplied, the service uses the existing Sherpa pipeline. The CI workflow currently generates a Sherpa keyword list containing the text "JARVIS"; that text file is not a trained acoustic model and does not prove reliable single-word detection.

## Model contract

Place the final classifier at:

```
app/src/main/assets/wakeword/jarvis.onnx
```

It must be a classifier trained for the openWakeWord feature pipeline used by `com.github.msnilsen:openwakeword-android:0.1.0`:

- Input tensor: `[1, 16, 96]`
- Output tensor: `[1, 1]`
- Output: wake-word confidence score in the range expected by the runtime (normally 0–1).

The library supplies its own mel-spectrogram and speech-embedding ONNX assets. Do not put those files inside the custom classifier path.

## Training requirements

Use the upstream openWakeWord custom-model training notebook and export the classifier in ONNX format. Train for the exact spoken phrase **"Jarvis"**, not "Hey Jarvis".

The dataset should contain:
1. Many different speakers, accents, pitches, speaking rates and microphone distances saying "Jarvis".
2. Hard negatives such as "jar", "service", "JARVIS" spoken inside longer sentences, similar-sounding names, ordinary Hindi/English speech, music, television and silence.
3. Augmentations representative of phone use: room echo, fan noise, traffic, television, low volume, clipping and background conversations.
4. Separate train, validation and test speakers/sessions. Do not tune the threshold on the final test set.

Evaluate false activations per hour as well as missed detections in quiet and noisy conditions. Select the threshold from validation results; the current adapter default of 0.55 is only a starting point, not a validated production value.

## License and provenance

Record the dataset sources, consent/usage rights, training notebook revision, training configuration, model checksum and model license before committing the binary. Do not use a model with unclear provenance or a non-commercial restriction if this app is intended for commercial use.

## Cutover checklist

- [ ] Add the trained `jarvis.onnx` asset at the exact path above.
- [ ] Confirm tensor names, shapes and output semantics against the model.
- [ ] Run Gradle unit tests and assemble the debug APK in CI.
- [ ] Test repeated wake, false activations, quiet speech and noisy rooms on the target Android phone.
- [ ] Verify microphone release before Android SpeechRecognizer starts and restart after each response.
- [ ] Only after successful testing, remove the Sherpa AAR, Sherpa model download and Sherpa code.

Do not remove the fallback or label the migration complete until every checklist item is verified.
