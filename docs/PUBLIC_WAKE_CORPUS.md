# Offline replay of the public wake-word corpus through JARVIS ONNX

This step runs the repository's actual three-stage primary ONNX pipeline and independent Jarvis verifier against downloaded public WAV clips. It mirrors the Java implementation's 16 kHz conversion, 1,280-sample chunks, 1,760-sample rolling window, mel normalization `value / 10 + 2`, 76x32 embedding window, 16x96 classifier window, temporal confidence gate and independent verifier.

## Install on a computer
```bash
python -m pip install numpy soundfile onnxruntime
python tools/prepare_public_wake_corpus.py
python tools/evaluate_public_wake_corpus.py
```
Use `--max-clips 20` for a short smoke test. The full corpus contains roughly 2,293 clips and can take time.

## Output
Writes `benchmark_data/picovoice/jarvis_onnx_trials.csv`, with one record per clip. Positive examples are the Jarvis class; the other five wake-word classes are hard negatives. The script reports positive hits/misses and hard-negative triggers.

## Critical limitations
- This is desktop CPU ONNX replay, not the Android app or POCO F5 microphone.
- Public recordings are short, clean wake-word utterances. They are not continuous background speech, TV, silence or ambient-noise negatives.
- Source manifest has no reliable per-clip speaker identity; speaker IDs are marked unknown rather than invented.
- The public corpus has only a few hundred distinct wake recordings according to its card; 2,293 dataset rows are not 2,293 independent live user trials.
- The trial CSV uses `public_corpus_unpartitioned`; do not use its negative duration as ambient false-wakes/hour and do not merge it into live-device acceptance metrics.
- No threshold tuning or model training is performed. Do not copy the audio into the Android APK or commit the downloaded files.
