# JARVIS Phase 6 — Real-World Wake-Word Benchmark

## Implemented analyzer
`tools/wake_benchmark.py` validates labeled trial CSVs and calculates positive recall, miss rate, false activations per negative listening hour, wake latency P50/P95, and breakdowns by noise condition, screen state, distance, and speech level. It records the APK commit, model version, gate configuration, device, and Android versions represented by the dataset.

It does not generate synthetic trials, infer missing labels, or claim the target dataset has been collected.

## Dataset target
Collect at least 1,000 labeled trials before making a broad accuracy claim:
- 300 or more positive wake-phrase trials;
- 700 or more negative/no-wake trials;
- 10 or more distinct consenting speakers if available;
- keep final evaluation speakers/sessions separate from tuning data;
- never tune thresholds on the final evaluation set.

These are target minimums, not completed tests. Repeated copies of one clip are not independent speaker samples.

## Test matrix
Use a frozen APK/model/gate configuration. Test quiet room at 0.5 m, 1.5 m, and 3 m; normal and soft speech; fan, TV/conversation, traffic-like noise, music, and quiet; multiple speakers; negative conversation, similar-sounding words, silence, TV/radio speech, and environmental noise; screen-on and screen-off with the foreground listening service active. Use consistent 16 kHz mono PCM/WAV for replay tests. For live tests, record device, Android version, room, distance, and approximate noise condition.

## Recording trials
Use `docs/JARVIS_PHASE6_RESULTS_TEMPLATE.csv`. One row is one trial, with a unique trial ID and anonymous speaker ID.
- Positive: `expected_wake=1`; `detected=1` only if JARVIS wakes within the agreed window, otherwise 0. Record latency in ms when measurable.
- Negative: `expected_wake=0`; record unintended activations in `false_wake_count` and measured listening duration in `trial_duration_s`.
- Identify the tested APK commit, model version, gate configuration, device, Android version, screen state, and service state on every row.
- Log every miss and false wake.

## Analyze
Run from the repository root with Python 3:
```bash
python3 tools/wake_benchmark.py path/to/your_trials.csv --json wake-benchmark-report.json
```
Enforce the minimum sample target:
```bash
python3 tools/wake_benchmark.py path/to/your_trials.csv --json wake-benchmark-report.json --require-target
```
False-wake rate = total false wakes / total negative listening hours. Latency percentiles use correctly detected positives with a measured latency; missing latency counts are reported separately.

## Battery and acceptance
Run multi-hour negative idle sessions and report total negative listening hours. Measure battery start/end over fixed-duration sessions at least three times under comparable conditions. Measure CPU/battery on the physical phone; do not infer from source code. Freeze the APK SHA, model version, device, Android version, and thresholds. Grant mic permission, start the foreground service, verify its persistent notification, randomize trial order where practical, and do not change thresholds during scored sessions. No pass/fail or broad accuracy claim until physical-device data is complete; smaller samples are preliminary only.

## Privacy
Get consent from recorded speakers. Use anonymous IDs, avoid unrelated private conversations, keep raw recordings local unless everyone explicitly agrees to sharing, and never commit personal recordings to this repository.
