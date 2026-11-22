#!/usr/bin/env python3
"""Summarize JARVIS labeled wake trials from a CSV produced by the replay tool."""
import argparse
import csv
import json
import math
from pathlib import Path


def number(value, default=0.0):
    try:
        parsed = float(value)
        return parsed if math.isfinite(parsed) else default
    except (TypeError, ValueError):
        return default


def analyze(path):
    with Path(path).open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    positives = [r for r in rows if str(r.get("expected_wake", "0")).strip() in ("1", "true", "True")]
    negatives = [r for r in rows if r not in positives]
    hits = sum(str(r.get("detected", "0")).strip() in ("1", "true", "True") for r in positives)
    false_hits = sum(str(r.get("detected", "0")).strip() in ("1", "true", "True") for r in negatives)
    latencies = sorted(number(r.get("wake_latency_ms")) for r in positives
                       if str(r.get("detected", "0")).strip() in ("1", "true", "True")
                       and str(r.get("wake_latency_ms", "")).strip())
    duration = sum(number(r.get("trial_duration_s")) for r in negatives)
    def percentile(values, p):
        if not values:
            return None
        index = max(0, min(len(values)-1, math.ceil(p * len(values))-1))
        return round(values[index], 2)
    return {
        "total_trials": len(rows),
        "positive_trials": len(positives),
        "negative_trials": len(negatives),
        "true_positives": hits,
        "false_negatives": len(positives) - hits,
        "hard_negative_triggers": false_hits,
        "recall": round(hits / len(positives), 4) if positives else None,
        "miss_rate": round((len(positives)-hits) / len(positives), 4) if positives else None,
        "hard_negative_trigger_rate": round(false_hits / len(negatives), 4) if negatives else None,
        "hard_negative_clips_hours": round(duration / 3600, 4),
        "false_activations_per_negative_listening_hour": round(false_hits / (duration / 3600), 3) if duration else None,
        "wake_latency_p50_ms": percentile(latencies, .50),
        "wake_latency_p95_ms": percentile(latencies, .95),
        "limitations": [
            "Public keyword clips are not continuous ambient background speech.",
            "Do not interpret hard-negative triggers as ambient false wakes per hour.",
            "Offline desktop ONNX replay is not a live Android microphone test."
        ]
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path")
    parser.add_argument("--json", dest="json_path")
    parser.add_argument("--require-target", action="store_true")
    parser.add_argument("--min-total", type=int, default=1000)
    parser.add_argument("--min-positive", type=int, default=300)
    parser.add_argument("--min-negative", type=int, default=700)
    args = parser.parse_args()
    report = analyze(args.csv_path)
    print(json.dumps(report, indent=2))
    if args.json_path:
        Path(args.json_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    if args.require_target and (
        report["total_trials"] < args.min_total or
        report["positive_trials"] < args.min_positive or
        report["negative_trials"] < args.min_negative
    ):
        raise SystemExit("Trial target not met; collect more labeled trials.")


if __name__ == "__main__":
    main()
