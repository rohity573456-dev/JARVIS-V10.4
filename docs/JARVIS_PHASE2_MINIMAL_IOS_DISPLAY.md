# JARVIS Phase 2 — Minimal iOS Display

## Approved direction
- Theme 5: Minimal iOS-inspired dark interface.
- Soft slate/blue glass surfaces, restrained accent colours, clean typography.
- Keep the existing JARVIS feature entry points and conversation capabilities.
- Preserve the existing custom 5D orb artwork; improve its animation scheduling.
- Request 90 Hz rendering when supported by the display. This is a request, not a guarantee; Android, the panel, power mode and OEM policy decide the actual refresh rate.

## Implemented foundation
- Reworked the current programmatic MainActivity dashboard into a cleaner minimal dark/glass layout.
- Kept existing actions wired: memory, tasks, custom commands, camera, maps, repeat, AI model setup, online AI setup, image vision, web search and system status.
- Added a dedicated text conversation area and rounded message composer.
- Updated buttons and cards to restrained iOS-like surfaces.
- Replaced the orb's fixed 32 ms invalidation timer with display-vsync invalidation (postInvalidateOnAnimation) so it can animate at the supported display cadence.
- Added a small background wake orb overlay. It is shown only when a wake word is accepted while MainActivity is not active, changes to thinking/speaking states, and is removed after the response finishes or after a 30-second safety timeout.
- Added a user-initiated FLOATING ORB SETUP action to request Android's Display over other apps permission. The overlay remains disabled until the user grants that permission.

## Android platform limits
- 90 FPS cannot be guaranteed by an app. The implementation requests 90 Hz and follows vsync; actual FPS must be measured on the target phone.
- Android may block or hide application overlays on the lock screen or under OEM power/security policies. The overlay works only where Android permits it and after explicit user permission.
- Microphone foreground-service restrictions, notification permission, battery optimization and OEM background limits still apply.
- This does not alter personal speaker-recognition assets, wake-word ONNX models, or wake-word model inference logic.

## Not yet proven
- No physical-device FPS, frame-time, battery, lock-screen, or overlay permission test has been run in this commit.
- The exact reference screenshot layout needs comparison against the user's original three-screen/photo reference before claiming pixel-identical placement.