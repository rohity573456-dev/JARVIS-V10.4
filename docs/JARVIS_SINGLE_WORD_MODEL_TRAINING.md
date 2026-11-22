# Train the single-word JARVIS wake model

This repository intentionally does not rename or reuse the bundled **"Hey Jarvis"** classifier. The production phrase is the single word **"Jarvis"**.

## Current status

- Android ONNX runtime adapter: present.
- Service integration: present behind a model-asset check; Sherpa remains the fallback.
- Trained `jarvis.onnx`: **not yet present**.
- Device validation: **not yet performed**.

Until the custom model is supplied, the service uses the existing Sherpa pipeline. The CI workflow currently generates a Sherpa keyword list containing the text "JARVIS"; that text file is not a trained acoustic model and does not prove reliable single-word detection.

## Existing exact-phrase candidate

The OpenWakeWord library currently lists an exact single-word **Jarvis** model:

- Model page: https://openwakeword.com/library/422
- Export: ONNX, about 405 KB
- Pipeline: V1; the Android wrapper's 3-stage feature pipeline must be checked against this exact export.
- Published benchmark: 87.3% recall with background noise/room reverb and 0.00 false activations/hour at threshold 0.5. These are synthetic benchmark results, not a guarantee on a Xiaomi/Android handset.
- Download access: the page requires signing in.
- Default model license on that page: personal and non-commercial use; check the linked terms before redistribution or commercial use.

Do not use the older model at https://openwakeword.com/library/293 as the preferred candidate: its published noisy-room recall is only 14.4% and benchmark false activations are 10.50/hour.

The existing candidate is preferable to inventing a classifier, but its binary is not committed because the download requires an authenticated session. Do not fabricate or rename another ONNX model as `jarvis.onnx`.

The Sherpa fallback and OpenWakeWord runtime both package the ONNX Runtime native library. The Android packaging configuration selects one `libonnxruntime.so` for the arm64 APK while retaining the Java/JNI bindings. CI must verify this packaging and runtime combination.

## Android compatibility

The selected Android OpenWakeWord runtime declares Android API 26 (Android 8.0) as its minimum. The app's `minSdk` is therefore 26 on this migration branch; Android 7.x and older are not supported by this build. The target Android 15 device is within the supported range.

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
