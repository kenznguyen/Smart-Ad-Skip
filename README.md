# Smart Ad Skip

Standalone Android proof of concept for Fruit Blast ad-interface automation.

## Project status

P2.17 introduces the autonomous AI decision contract for local Qwen3-VL. See
[docs/P2.17-AUTONOMOUS-AI.md](docs/P2.17-AUTONOMOUS-AI.md) for the implementation
boundary and native-runtime gate.

**Important:** this PR does not yet ship the Qwen3-VL native runtime or model importer.
The existing P2.16 execution path remains active; do not enable autonomous actions until
the local inference bridge is built and validated on a physical device.

## Technology

- Kotlin, Jetpack Compose, Android Accessibility
- Android API 26+, compile/target SDK 36
- JDK 17

## Build and test

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## Safety boundaries

- No ad SDK modification, reward callback spoofing, CAPTCHA bypass, or fabricated rewards.
- The intended AI decision set is limited to `NEXT_AD`, `CLOSE`, and `WAIT`.
- No rule-based fallback is allowed when the local model is unavailable.
- Android Accessibility permission remains required for any device action.

Fruit Blast is the initial test application for controlled feasibility testing.
