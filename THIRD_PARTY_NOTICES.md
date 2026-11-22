# Third-party notices

## Independent Jarvis wake-word verifier

- Model bundle: `multi_jarvis_v9.3.zip`
- Source: https://github.com/voicute/onnx-wakeword
- Model family: multi-variant English Jarvis wake phrase classifier (Hey Jarvis / Jarvis / Hi Jarvis)
- Runtime use: independent second-stage ONNX confirmation after the app's existing primary wake-word model.
- The model is a generic phrase detector. It is **not** Pratik's personal speaker-recognition model and does not identify the speaker.
- The upstream repository's source code includes an MIT license. The model artifact's separate distribution/commercial terms are not clearly specified in the repository; verify terms with the upstream provider before redistribution or commercial use.

The primary JARVIS wake-word ONNX model and any Pratik speaker-recognition model are not replaced by this verifier.
