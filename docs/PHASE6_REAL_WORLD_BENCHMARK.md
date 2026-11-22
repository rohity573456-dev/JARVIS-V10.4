# JARVIS Phase 6 — Real-World Wake-Word Benchmark

## Purpose
Measure the existing JARVIS wake-word pipeline on real recordings and real-device sessions. Do not infer accuracy from synthetic scores or unit tests.

## Test matrix
Run the same frozen APK/model/gate configuration across these conditions:
- Quiet room: 0.5 m, 1.5 m, 3 m.
- Normal speaking and deliberately quiet/soft speaking.
- Background fan, TV/conversation, traffic-like noise, and music.
- Different speakers, including speakers not used during any tuning.
- Negative audio: ordinary conversation, similar-sounding words, silence, TV/radio speech, and environmental noise without the wake phrase.
- Screen on and screen off, with the foreground listening service visibly active.

Use a consistent 16 kHz mono PCM/WAV source when replaying recorded samples. For live microphone tests, record the device, Android version, room, distance, and approximate noise condition. Keep the phone position and playback level fixed for repeatability.

## Dataset size and split
Target at least 1,000 labeled trials before making a broad accuracy claim:
- At least 300 positive wake-phrase trials.
- At least 700 negative/no-wake trials.
- Include at least 10 distinct consenting speakers if available.
- Keep speakers or recording sessions used for final evaluation separate from any tuning set. Never tune thresholds on the final evaluation set.
- Repeat each important condition enough times to identify inconsistent behavior.

These are target minimums, not evidence that the tests have already been run. Do not count repeated copies of the same clip as independent speaker samples.

## What to record
Use `JARVIS_PHASE6_RESULTS_TEMPLATE.csv`. One row is one trial. Use anonymous speaker IDs (S01, S02, ...); do not store names or personal details.

For positive trials:
- `expected_wake=1`
- `detected=1` if JARVIS woke within the agreed trial window, otherwise 0.
- Record wake latency from the start of the wake phrase in milliseconds when measurable.

For negative trials:
- `expected_wake=0`
- `false_wake_count` is the number of unintended activations during the trial.
- For long idle tests, record the duration in seconds and count false wakes.

## Metrics
Report separately by condition and overall:
- True positive rate / recall = detected positive trials ÷ positive trials.
- Miss rate = missed positive trials ÷ positive trials.
- False activation rate = false wakes ÷ negative listening hours.
- Wake latency: median and 95th percentile for correctly detected positive trials.
- Screen-off vs screen-on results.
- Battery change over a fixed-duration session, with starting/ending battery percentage and duration; repeat at least three times under comparable conditions.
- CPU/battery results must be measured on the physical phone. Do not claim power savings from code inspection alone.

## Test procedure
1. Freeze and note the APK build SHA, model files, app version, phone model, Android version, and gate thresholds.
2. Grant microphone permission and start the JARVIS foreground listening service.
3. Verify the persistent listening notification is present before each run.
4. Run positive and negative trials in randomized order where practical.
5. Reset the service between sessions; do not change thresholds midway through a scored run.
6. Log every miss and every false wake, not just successful examples.
7. Repeat the negative idle test for multiple hours; report total negative listening hours.
8. Keep raw audio locally unless every recorded speaker has explicitly agreed to sharing. Do not commit personal recordings to this repository.

## Acceptance / reporting
No pass/fail claim until the dataset and physical-device sessions are complete. Publish counts, denominators, condition breakdowns, and test configuration. A small sample must be labeled preliminary. Never describe a target dataset size as completed tests.

## Privacy
Obtain informed consent from anyone whose voice is recorded. Use anonymous speaker IDs. Avoid capturing unrelated private conversations; store test clips securely and delete them when no longer needed.
