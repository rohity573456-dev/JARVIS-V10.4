#!/usr/bin/env python3
"""Replay public labeled WAV clips through JARVIS' ONNX wake pipeline.

This mirrors OpenWakeWordEngine.java's 16 kHz / 1280-sample chunking,
mel normalization, embedding/classifier windows, confidence gate and
independent Jarvis verifier. It is an OFFLINE public-corpus evaluation,
not a live Android microphone test.
"""
import argparse
import csv
import json
import math
import sys
import zipfile
from collections import deque
from pathlib import Path

np = None

SAMPLE_RATE = 16000
CHUNK = 1280
MEL_BINS = 32
MEL_WINDOW = 76
EMBEDDING_SIZE = 96
CLASSIFIER_WINDOW = 16
RAW_WINDOW = CHUNK + 480
MEL_HISTORY = 970
FAST, SOFT, SOFT_AVG = 0.30, 0.22, 0.245
BORDERLINE, ADAPTIVE, ADAPTIVE_AVG = 0.18, 0.19, 0.205
SECOND_CHANCE_FRAMES = 38
GATE_CONFIG = "fast=.30x2;soft=.22x3of4_avg=.245;borderline=.18;adaptive=.19x3_avg=.205_38f"


def mono_16k(path):
    import numpy as np
    import soundfile as sf
    audio, rate = sf.read(str(path), dtype="int16", always_2d=True)
    audio = audio.astype(np.float32).mean(axis=1)
    if rate != SAMPLE_RATE:
        target = max(1, round(len(audio) * SAMPLE_RATE / rate))
        old_x = np.arange(len(audio), dtype=np.float64)
        new_x = np.linspace(0, max(0, len(audio)-1), target)
        audio = np.interp(new_x, old_x, audio).astype(np.float32)
    return np.clip(np.rint(audio), -32768, 32767).astype(np.float32)


class ConfidenceGate:
    def __init__(self):
        self.reset()

    def reset(self):
        self.scores = deque(maxlen=4)
        self.second_chance = 0
        self.armed = False

    def add(self, raw):
        score = min(1.0, max(0.0, float(raw))) if math.isfinite(float(raw)) else 0.0
        previous = list(self.scores)
        self.scores.append(score)
        current = list(self.scores)
        if len(previous) >= 1 and previous[-1] >= FAST and score >= FAST:
            return True
        borderline_count = sum(v >= BORDERLINE for v in current)
        if borderline_count < 2:
            self.armed = False
        elif len(current) == 4 and not self.armed and self.second_chance == 0:
            self.second_chance = SECOND_CHANCE_FRAMES
            self.armed = True
        adaptive = self.second_chance > 0
        if adaptive:
            self.second_chance -= 1
        if len(current) < 4:
            return False
        if adaptive:
            return sum(v >= ADAPTIVE for v in current) >= 3 and sum(current)/4 >= ADAPTIVE_AVG
        return sum(v >= SOFT for v in current) >= 3 and sum(current)/4 >= SOFT_AVG


class JarvisOnnxPipeline:
    def __init__(self, assets):
        global np
        import numpy as np
        import onnxruntime as ort
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 1
        opts.inter_op_num_threads = 1
        self.mel = ort.InferenceSession(str(assets/"melspectrogram.onnx"), sess_options=opts, providers=["CPUExecutionProvider"])
        self.embedding = ort.InferenceSession(str(assets/"embedding_model.onnx"), sess_options=opts, providers=["CPUExecutionProvider"])
        self.classifier = ort.InferenceSession(str(assets/"Jarvis_20260313_215039.onnx"), sess_options=opts, providers=["CPUExecutionProvider"])
        self.mel_input = self.mel.get_inputs()[0].name
        self.embedding_input = "input_1"
        self.classifier_input = self.classifier.get_inputs()[0].name
        self.mel_history = deque([np.ones(MEL_BINS, dtype=np.float32) for _ in range(MEL_WINDOW)], maxlen=MEL_HISTORY)
        self.features = deque([np.zeros(EMBEDDING_SIZE, dtype=np.float32) for _ in range(CLASSIFIER_WINDOW)], maxlen=120)
        self.gate = ConfidenceGate()
        self.verifier, self.verifier_index, self.verifier_threshold, self.verifier_shape = self._load_verifier(assets)

    def _load_verifier(self, assets):
        import onnxruntime as ort
        archive = assets/"multi_jarvis_v9.3.zip"
        with zipfile.ZipFile(archive) as zf:
            info = json.loads(zf.read("model_info.json").decode("utf-8"))
            keywords = [str(k).strip().lower() for k in info["keywords"]]
            if "jarvis" not in keywords:
                raise ValueError("Independent verifier archive has no Jarvis output")
            model_name = info.get("model_file", "model.onnx")
            model_bytes = zf.read(model_name)
        session = ort.InferenceSession(model_bytes, providers=["CPUExecutionProvider"])
        shape = session.get_inputs()[0].shape
        mel_frames = int(info.get("mel_time", 98))
        mel_bins = int(info.get("n_mels", 32))
        if len(shape) not in (3, 4) or shape[-1] != mel_bins or mel_frames not in shape[1:-1]:
            raise ValueError(f"Unsupported independent verifier input shape: {shape}")
        if any(not isinstance(dim, int) or dim <= 0 for dim in shape):
            raise ValueError(f"Independent verifier requires a static input shape: {shape}")
        return session, keywords.index("jarvis"), float(info.get("threshold", .5)), list(shape)

    def _verify(self):
        frames = list(self.mel_history)
        n = int(self.verifier_shape[1])
        if len(frames) < n:
            return False
        data = np.stack(frames[-n:]).astype(np.float32).reshape(self.verifier_shape)
        input_name = self.verifier.get_inputs()[0].name
        output = self.verifier.run(None, {input_name: data})[0].reshape(-1)
        return len(output) > self.verifier_index and float(output[self.verifier_index]) >= self.verifier_threshold

    def run_clip(self, pcm):
        raw = np.zeros(RAW_WINDOW, dtype=np.float32)
        raw_count = 0
        pending = []
        feature_frames = 0
        detections = []
        for sample_index, sample in enumerate(pcm):
            raw[:-1] = raw[1:]
            raw[-1] = sample
            raw_count = min(RAW_WINDOW, raw_count+1)
            pending.append(sample)
            if len(pending) != CHUNK:
                continue
            pending.clear()
            if raw_count < RAW_WINDOW:
                continue
            mel_out = self.mel.run(None, {self.mel_input: raw.reshape(1, -1)})[0]
            mel_out = np.asarray(mel_out, dtype=np.float32).reshape(-1, MEL_BINS)
            for frame in mel_out:
                self.mel_history.append(frame / 10.0 + 2.0)
            if len(self.mel_history) < MEL_WINDOW:
                embedding = np.zeros(EMBEDDING_SIZE, dtype=np.float32)
            else:
                mel_window = np.stack(list(self.mel_history)[-MEL_WINDOW:])[None, ..., None]
                emb_out = self.embedding.run(None, {self.embedding_input: mel_window})[0]
                embedding = np.asarray(emb_out, dtype=np.float32).reshape(-1)[:EMBEDDING_SIZE]
                if embedding.size != EMBEDDING_SIZE:
                    raise ValueError(f"Unexpected embedding size: {embedding.size}")
            self.features.append(embedding)
            feature_frames += 1
            if feature_frames < CLASSIFIER_WINDOW:
                continue
            feature_window = np.stack(list(self.features)[-CLASSIFIER_WINDOW:])[None, ...].astype(np.float32)
            score = float(np.asarray(self.classifier.run(None, {self.classifier_input: feature_window})[0]).reshape(-1)[0])
            if self.gate.add(score):
                self.gate.reset()
                if self._verify():
                    detections.append((sample_index + 1) / SAMPLE_RATE)
                    break
        return detections[0] if detections else None


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest",default="benchmark_data/picovoice/manifest.csv")
    p.add_argument("--assets",default="app/src/main/assets/openwakeword")
    p.add_argument("--output",default="benchmark_data/picovoice/jarvis_onnx_trials.csv")
    p.add_argument("--max-clips",type=int,default=0,help="Optional cap for a smoke test")
    a=p.parse_args()
    manifest=Path(a.manifest); assets=Path(a.assets)
    for name in ("melspectrogram.onnx","embedding_model.onnx","Jarvis_20260313_215039.onnx","multi_jarvis_v9.3.zip"):
        if not (assets/name).is_file():
            raise SystemExit(f"Missing model asset: {assets/name}")
    with manifest.open(newline="",encoding="utf-8") as f:
        clips=list(csv.DictReader(f))
    if a.max_clips:
        clips=clips[:a.max_clips]
    engine=JarvisOnnxPipeline(assets)
    output=Path(a.output); output.parent.mkdir(parents=True,exist_ok=True)
    fields=["trial_id","split","expected_wake","phrase_or_negative_type","speaker_id","distance_m","speech_level","noise_condition","noise_level_db","device_model","android_version","screen_state","service_active","detected","wake_latency_ms","false_wake_count","trial_duration_s","apk_commit","model_version","gate_config","notes"]
    positives=negatives=hits=misses=false_wakes=0
    with output.open("w",newline="",encoding="utf-8") as f:
        writer=csv.DictWriter(f,fieldnames=fields); writer.writeheader()
        for index,clip in enumerate(clips,1):
            path=Path(clip["relative_path"])
            if not path.is_absolute(): path=Path.cwd()/path
            pcm=mono_16k(path)
            duration=len(pcm)/SAMPLE_RATE
            detected_at=engine.run_clip(pcm)
            positive=str(clip.get("expected_wake","0"))=="1"
            detected=detected_at is not None
            if positive:
                positives+=1
                hits+=int(detected); misses+=int(not detected)
            else:
                negatives+=1
                false_wakes+=int(detected)
            writer.writerow({
                "trial_id":clip.get("clip_id",f"PUB{index:05d}"),
                "split":"public_corpus_unpartitioned",
                "expected_wake":int(positive),
                "phrase_or_negative_type":"Jarvis" if positive else (clip.get("negative_type") or "other_wakeword"),
                "speaker_id":"unknown_not_in_source_manifest",
                "distance_m":"","speech_level":"recorded_unknown","noise_condition":"clean_keyword_clip_unknown_environment",
                "noise_level_db":"","device_model":"desktop_cpu_onnxruntime",
                "android_version":"not_applicable","screen_state":"offline_replay",
                "service_active":0,"detected":int(detected),
                "wake_latency_ms":round(detected_at*1000,1) if detected_at is not None else "",
                "false_wake_count":int(detected and not positive),
                "trial_duration_s":round(duration,4),"apk_commit":"offline_replay_no_apk",
                "model_version":"repo_openwakeword_assets","gate_config":GATE_CONFIG,
                "notes":"Public offline corpus; not live phone mic; speaker identity absent; no background speech/noise claim."
            })
            if index%50==0:
                print(f"Processed {index}/{len(clips)} clips; positive hits={hits}, misses={misses}, hard-negative triggers={false_wakes}")
    print(json.dumps({"clips":len(clips),"positive":positives,"positive_hits":hits,"positive_misses":misses,
        "negative_other_wakewords":negatives,"hard_negative_triggers":false_wakes,
        "trial_csv":str(output),"warning":"This is public-corpus offline replay only. Do not report as 1,000 live Android trials or ambient false-wakes/hour."},indent=2))


if __name__=="__main__":
    try: main()
    except Exception as exc:
        print(f"Benchmark failed: {exc}",file=sys.stderr)
        raise
