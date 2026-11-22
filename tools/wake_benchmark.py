#!/usr/bin/env python3
"""Validate labeled JARVIS wake trials and calculate reproducible metrics (stdlib only)."""
import argparse, csv, json, math, sys
from collections import defaultdict
from pathlib import Path

REQUIRED={"trial_id","split","expected_wake","phrase_or_negative_type","speaker_id","device_model","android_version","screen_state","service_active","detected","wake_latency_ms","false_wake_count","trial_duration_s","apk_commit","model_version","gate_config"}
GROUP_FIELDS=("noise_condition","screen_state","distance_m","speech_level")

def num(value, field, line, blank=False):
    value=(value or "").strip()
    if not value and blank: return None
    try: result=float(value)
    except ValueError: raise ValueError(f"row {line}: {field} must be numeric")
    if not math.isfinite(result): raise ValueError(f"row {line}: {field} must be finite")
    return result

def percentile(values,p):
    if not values: return None
    a=sorted(values)
    return a[max(0,math.ceil(p*len(a))-1)]

def load_trials(path):
    rows=[]; ids=set()
    with Path(path).open(newline="",encoding="utf-8-sig") as f:
        reader=csv.DictReader(f)
        missing=REQUIRED-set(reader.fieldnames or [])
        if missing: raise ValueError("CSV missing columns: "+", ".join(sorted(missing)))
        for line,r in enumerate(reader,2):
            if not any((v or "").strip() for v in r.values()): continue
            tid=(r.get("trial_id") or "").strip()
            if not tid: raise ValueError(f"row {line}: trial_id required")
            if tid in ids: raise ValueError(f"row {line}: duplicate trial_id {tid}")
            ids.add(tid)
            expected=(r.get("expected_wake") or "").strip()
            detected=(r.get("detected") or "").strip()
            service=(r.get("service_active") or "").strip()
            if expected not in {"0","1"}: raise ValueError(f"row {line}: expected_wake must be 0 or 1")
            if expected=="1" and detected not in {"0","1"}: raise ValueError(f"row {line}: positive trial requires detected 0 or 1")
            if service not in {"0","1"}: raise ValueError(f"row {line}: service_active must be 0 or 1")
            duration=num(r.get("trial_duration_s"),"trial_duration_s",line,True)
            false=num(r.get("false_wake_count"),"false_wake_count",line,True)
            latency=num(r.get("wake_latency_ms"),"wake_latency_ms",line,True)
            if expected=="0" and duration is None: raise ValueError(f"row {line}: negative trial requires duration")
            if duration is not None and duration<0: raise ValueError(f"row {line}: duration cannot be negative")
            if false is not None and (false<0 or not false.is_integer()): raise ValueError(f"row {line}: false_wake_count must be a non-negative integer")
            if latency is not None and latency<0: raise ValueError(f"row {line}: latency cannot be negative")
            r.update(expected_wake=int(expected),detected=int(detected) if detected in {"0","1"} else 0,duration_s=duration or 0.0,false_wakes=int(false or 0),latency_ms=latency)
            rows.append(r)
    return rows

def summarize(rows):
    pos=[r for r in rows if r["expected_wake"]==1]; neg=[r for r in rows if r["expected_wake"]==0]
    tp=sum(r["detected"]==1 for r in pos); misses=len(pos)-tp
    fw=sum(r["false_wakes"] for r in neg); hours=sum(r["duration_s"] for r in neg)/3600
    lat=[r["latency_ms"] for r in pos if r["detected"]==1 and r["latency_ms"] is not None]
    return {"trials_total":len(rows),"positive_trials":len(pos),"negative_trials":len(neg),"true_positives":tp,"misses":misses,
      "recall_percent":round(100*tp/len(pos),2) if pos else None,"miss_rate_percent":round(100*misses/len(pos),2) if pos else None,
      "false_wakes":fw,"negative_listening_hours":round(hours,4),"false_wakes_per_negative_hour":round(fw/hours,4) if hours else None,
      "wake_latency_samples":len(lat),"wake_latency_p50_ms":percentile(lat,.50),"wake_latency_p95_ms":percentile(lat,.95),
      "detected_without_latency_count":sum(r["detected"]==1 and r["latency_ms"] is None for r in pos)}

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument("csv_file"); p.add_argument("--json",dest="json_file")
    p.add_argument("--require-target",action="store_true"); a=p.parse_args()
    try:
        rows=load_trials(a.csv_file); overall=summarize(rows)
        breakdown={}
        for field in GROUP_FIELDS:
            groups=defaultdict(list)
            for r in rows: groups[(r.get(field) or "").strip() or "(unspecified)"].append(r)
            breakdown[field]={k:summarize(v) for k,v in sorted(groups.items())}
        target=overall["trials_total"]>=1000 and overall["positive_trials"]>=300 and overall["negative_trials"]>=700
        report={"disclaimer":"Metrics apply only to supplied labeled trials and recorded conditions.","target_met":target,
          "target":{"total":1000,"positive":300,"negative":700},"overall":overall,"breakdowns":breakdown,
          "configuration":{k:sorted({(r.get(k) or "").strip() for r in rows if (r.get(k) or "").strip()}) for k in ("apk_commit","model_version","gate_config","device_model","android_version")}}
        print("JARVIS REAL-WORLD WAKE BENCHMARK")
        print(f"Trials {overall['trials_total']} | positive {overall['positive_trials']} | negative {overall['negative_trials']}")
        print(f"Recall {overall['recall_percent']}% | miss rate {overall['miss_rate_percent']}%")
        print(f"False wakes {overall['false_wakes']} / {overall['negative_listening_hours']} negative hours = {overall['false_wakes_per_negative_hour']} per hour")
        print(f"Latency P50/P95: {overall['wake_latency_p50_ms']} / {overall['wake_latency_p95_ms']} ms")
        print("1,000-trial target: "+("MET" if target else "NOT MET"))
        if a.json_file: Path(a.json_file).write_text(json.dumps(report,indent=2),encoding="utf-8")
        return 2 if a.require_target and not target else 0
    except (OSError,ValueError) as e:
        print("ERROR:",e,file=sys.stderr); return 1

if __name__=="__main__": raise SystemExit(main())
