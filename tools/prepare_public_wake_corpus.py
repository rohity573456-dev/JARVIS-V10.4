#!/usr/bin/env python3
"""Download the Apache-2.0 Picovoice wake-word clips into a temporary workspace."""
import argparse
import csv
import json
from pathlib import Path

DATASET_ID = "Picovoice/wake-word-benchmark"
LABELS = {0: "alexa", 1: "computer", 2: "jarvis", 3: "smart mirror", 4: "snowboy", 5: "view glass"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=0, help="0 downloads all clips")
    parser.add_argument("--output-dir", default="benchmark_data/picovoice")
    args = parser.parse_args()
    if args.limit < 0:
        parser.error("--limit must be zero or a positive integer")

    from datasets import Audio, load_dataset
    import numpy as np
    import soundfile as sf

    out = Path(args.output_dir)
    audio_dir = out / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)
    ds = load_dataset(DATASET_ID, split="train")
    if "audio" not in ds.column_names or "label" not in ds.column_names:
        raise RuntimeError(f"Unexpected dataset schema: {ds.column_names}")
    ds = ds.cast_column("audio", Audio(decode=True))
    total = len(ds) if args.limit == 0 else min(args.limit, len(ds))
    manifest_path = out / "manifest.csv"
    rows = []
    for index in range(total):
        item = ds[index]
        label_value = item["label"]
        if isinstance(label_value, str):
            label = label_value.strip().lower()
        else:
            label = LABELS.get(int(label_value), str(label_value))
        audio = item["audio"]
        samples = np.asarray(audio["array"], dtype=np.float32)
        rate = int(audio["sampling_rate"])
        if samples.ndim == 2:
            # HF audio arrays are usually [samples, channels], but accept [channels, samples].
            channel_axis = 0 if samples.shape[0] <= 8 and samples.shape[1] > 8 else 1
            samples = samples.mean(axis=channel_axis)
        if rate != 16000:
            target_length = max(1, round(len(samples) * 16000 / rate))
            old_x = np.arange(len(samples), dtype=np.float64)
            new_x = np.linspace(0, max(0, len(samples) - 1), target_length)
            samples = np.interp(new_x, old_x, samples).astype(np.float32)
            rate = 16000
        samples = np.clip(samples, -1.0, 1.0)
        relative = Path("benchmark_data/picovoice/audio") / f"clip_{index:05d}.wav"
        absolute = Path.cwd() / relative
        absolute.parent.mkdir(parents=True, exist_ok=True)
        sf.write(str(absolute), samples, rate, subtype="PCM_16")
        rows.append({
            "clip_id": f"PV{index:05d}",
            "label": label,
            "expected_wake": int(label == "jarvis"),
            "negative_type": "" if label == "jarvis" else f"other_wakeword:{label}",
            "relative_path": str(relative),
        })
        if (index + 1) % 100 == 0:
            print(f"Downloaded {index + 1}/{total} clips")
    with manifest_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]) if rows else [
            "clip_id", "label", "expected_wake", "negative_type", "relative_path"])
        writer.writeheader()
        writer.writerows(rows)
    summary = {
        "dataset": DATASET_ID,
        "license": "Apache-2.0",
        "downloaded_clips": total,
        "positive_jarvis_clips": sum(r["expected_wake"] for r in rows),
        "negative_other_wakeword_clips": sum(not r["expected_wake"] for r in rows),
        "manifest": str(manifest_path),
        "note": "Keyword clips only; not continuous ambient audio or live Android trials."
    }
    (out / "download_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
