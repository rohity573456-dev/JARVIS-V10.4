#!/usr/bin/env python3
"""Download and prepare the public Picovoice wake-word benchmark corpus.

Audio is stored locally under benchmark_data/ and is never copied into the APK
or committed to Git. Jarvis-labelled clips are positives; the other wake-word
classes are hard negatives, not conversational/background negatives.
"""
import argparse
import csv
import json
import re
import sys
from pathlib import Path


def safe_name(value):
    return re.sub(r"[^a-z0-9_-]+", "_", str(value).lower()).strip("_") or "unknown"


def label_name(dataset, value):
    feature = dataset.features.get("label")
    if hasattr(feature, "int2str"):
        return str(feature.int2str(int(value))).lower()
    return str(value).lower()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="benchmark_data/picovoice")
    parser.add_argument("--limit", type=int, default=0, help="Optional per-class cap; 0 means all clips")
    args = parser.parse_args()
    if args.limit < 0:
        parser.error("--limit cannot be negative")
    try:
        from datasets import load_dataset
        import soundfile as sf
        import numpy as np
    except ImportError as exc:
        print("Install dependencies: python -m pip install 'datasets[audio]' soundfile numpy", file=sys.stderr)
        raise SystemExit(2) from exc

    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    print("Downloading public corpus metadata/audio from Hugging Face...")
    dataset = load_dataset("domdomegg/picovoice-wake-word-benchmark", split="train")
    counts = {}
    rows = []
    for index, item in enumerate(dataset):
        audio = item.get("audio")
        if isinstance(audio, dict):
            samples = audio.get("array")
            rate = audio.get("sampling_rate")
        elif audio is not None and hasattr(audio, "get_all_samples"):
            # datasets >= 4 returns an AudioDecoder instead of the legacy dict.
            decoded = audio.get_all_samples()
            samples = decoded.data
            rate = decoded.sample_rate
            if hasattr(samples, "detach"):
                samples = samples.detach().cpu().numpy()
            else:
                samples = np.asarray(samples)
            # AudioSamples uses channels-first; soundfile expects frames-first.
            if samples.ndim == 2 and samples.shape[0] <= 8 and samples.shape[1] > samples.shape[0]:
                samples = samples.T
        else:
            raise RuntimeError(
                f"Dataset row {index} has unsupported audio value: "
                f"{type(audio).__name__}; expected legacy audio dict or AudioDecoder"
            )

        if samples is None or rate is None:
            raise RuntimeError(f"Dataset row {index} decoded without samples or sample rate")
        samples = np.asarray(samples)
        if samples.size == 0:
            raise RuntimeError(f"Dataset row {index} contains empty audio")
        rate = int(rate)

        label = label_name(dataset, item.get("label", "unknown"))
        counts[label] = counts.get(label, 0) + 1
        if args.limit and counts[label] > args.limit:
            continue
        positive = label == "jarvis"
        category = "positive_jarvis" if positive else "hard_negative_other_wakeword"
        folder = out / category
        folder.mkdir(parents=True, exist_ok=True)
        filename = f"{safe_name(label)}_{index:05d}.wav"
        destination = folder / filename
        sf.write(destination, samples, rate, subtype="PCM_16")
        duration = len(samples) / rate if rate else 0.0
        rows.append({
            "clip_id": f"PV{index:05d}",
            "expected_wake": 1 if positive else 0,
            "label": label,
            "negative_type": "" if positive else "other_wakeword",
            "relative_path": str(destination.as_posix()),
            "sample_rate_hz": rate,
            "duration_s": round(duration, 4),
            "speaker_id": "not_provided_by_source_manifest",
            "source": "domdomegg/picovoice-wake-word-benchmark",
            "license": "Apache-2.0",
        })

    manifest = out / "manifest.csv"
    with manifest.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]) if rows else [
            "clip_id","expected_wake","label","negative_type","relative_path",
            "sample_rate_hz","duration_s","speaker_id","source","license"])
        writer.writeheader()
        writer.writerows(rows)
    summary = {
        "source": "https://huggingface.co/datasets/domdomegg/picovoice-wake-word-benchmark",
        "license": "Apache-2.0 (verify source license before redistribution)",
        "clips_written": len(rows),
        "jarvis_positive_clips": sum(r["expected_wake"] == 1 for r in rows),
        "other_wakeword_hard_negatives": sum(r["expected_wake"] == 0 for r in rows),
        "classes_seen": counts,
        "manifest": str(manifest.as_posix()),
        "warning": "Offline public-corpus clips are not live POCO F5 microphone trials. Do not mix these with real-device accuracy metrics.",
    }
    (out / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    print("Audio remains local. Do not commit benchmark_data/ or package it in the Android APK.")


if __name__ == "__main__":
    main()
