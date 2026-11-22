# Third-party notices

## Independent Jarvis wake-word verifier

- Model bundle: `multi_jarvis_v9.3.zip`
- Source: https://github.com/voicute/onnx-wakeword
- Model family: multi-variant English Jarvis wake phrase classifier (Hey Jarvis / Jarvis / Hi Jarvis)
- Runtime use: independent second-stage ONNX confirmation after the app's existing primary wake-word model.
- The model is a generic phrase detector. It is **not** Pratik's personal speaker-recognition model and does not identify the speaker.
- The upstream repository includes an MIT license. The upstream README points to voicute.com for model licensing details; this bundled demo model is used for personal, non-commercial testing. Confirm commercial terms with the upstream provider before commercial distribution.

The primary JARVIS wake-word ONNX model and any Pratik speaker-recognition model are not replaced by this verifier.
