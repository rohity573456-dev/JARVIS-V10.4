# Public wake-word corpus for JARVIS

## Source
Picovoice Wake Word Benchmark, mirrored at:
https://huggingface.co/datasets/domdomegg/picovoice-wake-word-benchmark

The dataset card describes 2,293 audio rows, 16 kHz / 16-bit / mono WAV audio, six wake-word classes, and more than 50 speakers. The source publishes the dataset under Apache-2.0; retain attribution and verify the source card and license before redistribution.

## Prepare locally
From the repository root, on a computer with Python 3 and internet access:

```bash
python -m pip install "datasets[audio]" soundfile
python tools/prepare_public_wake_corpus.py
```

Optional smaller download for a quick check:

```bash
python tools/prepare_public_wake_corpus.py --limit 50
```

Output is written under `benchmark_data/picovoice/`:
- `positive_jarvis/`: clips labelled Jarvis;
- `hard_negative_other_wakeword/`: Alexa, Computer, Smart Mirror, Snowboy, View Glass and other non-Jarvis wake-word classes;
- `manifest.csv`: per-clip labels, file path, sample rate and duration;
- `summary.json`: counts and provenance.

## Correct interpretation
This corpus is useful for offline research and hard-negative comparison. It does **not** contain 1,000 independent real-device trials, and other wake words are not a substitute for real conversation, TV, silence, or environmental-noise negatives. The public source manifest does not provide per-clip speaker IDs, so the downloader marks speaker identity as unavailable rather than inventing it.

Do not feed these clips into model training or alter thresholds using the final evaluation split without creating a separate held-out test set. Do not count them as POCO F5 live-microphone results. The corpus is not copied into the APK or committed to this repository; it is downloaded locally when requested.
