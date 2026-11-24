# Smart Ad Skip

Android proof of concept for Fruit Blast ad-interface automation using on-device Qwen3-VL.

## P2.17 — Autonomous AI → Accessibility

The AI model independently chooses `NEXT_AD`, `CLOSE`, or `WAIT`. OCR no longer selects actions. The local Qwen3-VL runtime receives a screenshot and a bounded list of visible, enabled, clickable Accessibility nodes. A shared execution-integrity layer validates the selected target before Accessibility can click it.

See [docs/P2.17-AUTONOMOUS-AI.md](docs/P2.17-AUTONOMOUS-AI.md) for architecture, model package format, safety checks, and device-validation steps.

## Build and test

Requirements: JDK 17, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1, and Gradle 8.13.

```bash
chmod +x scripts/fetch_llama_cpp.sh
scripts/fetch_llama_cpp.sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The native build targets `arm64-v8a`. The APK does not bundle model weights. Import a verified Qwen3-VL-2B model ZIP from the dashboard after installation.

## Safety boundaries

- No ad SDK modification, reward callback spoofing, CAPTCHA bypass, or fabricated rewards.
- The model is the sole action selector; there is no OCR or rule-based action fallback.
- Model failure, stale context, malformed output, ambiguous target, or failed validation resolves to `WAIT`.
- Android Accessibility permission is required for device actions.
- A dismissed ad window is not proof that a reward was granted.

## Project status

The local runtime and Accessibility wiring are implemented on the P2.17 feature branch. Native CI and physical Fruit Blast inference still require successful verification; this branch is not yet declared production-ready.
