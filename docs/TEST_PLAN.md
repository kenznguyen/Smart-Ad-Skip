# Test Plan

## P0 — Foundation

- [ ] GitHub Actions workflow installs JDK 17 and Android SDK 36.
- [ ] Gradle configuration resolves successfully.
- [ ] `:app:testDebugUnitTest` passes.
- [ ] `:app:assembleDebug` produces a debug APK.
- [ ] APK launches on an Android API 26+ device.

## Planned detection and safety tests

- [ ] Distinguish ad-playing, skip-available, ad-completed, and unknown states.
- [ ] Unknown UI state never triggers an action.
- [ ] Stop control prevents further actions.
- [ ] A dismissed window alone never verifies reward completion.
- [ ] Only explicitly permitted Skip/Close controls may be actionable.

## Device validation

Initial target: Fruit Blast on a physical Android device. Device testing is a separate gate and must not be inferred from a successful CI build.
