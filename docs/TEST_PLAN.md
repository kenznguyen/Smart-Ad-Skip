# Test Plan

## P0 — Foundation

- [x] GitHub Actions workflow installs JDK 17 and Android SDK 36.
- [x] Gradle configuration resolves successfully.
- [x] `:app:testDebugUnitTest` passes.
- [x] `:app:assembleDebug` produces a debug APK.
- [ ] APK launches on an Android API 26+ device.

## P1 — Accessibility Monitor

- [x] Snapshot preserves only approved window metadata.
- [x] Blank package and window class names are normalized to null.
- [x] Node counts are clamped to 0–200.
- [x] Clickable node count cannot exceed total node count.
- [x] Snapshot model contains no UI text or input-value fields.
- [x] Every snapshot carries an event-received timestamp.
- [x] Dashboard displays the last event time and marks snapshots stale after 15 seconds.
- [x] Disconnect clears the latest snapshot; reconnect accepts fresh events.
- [x] Service is declared with the Android accessibility binding permission.
- [x] Service only observes window state/windows/content-change events.
- [x] Dashboard exposes service connection and latest metadata.
- [x] No automatic Skip/Close action is implemented.
- [ ] Validate timestamp refresh and stale indication on a physical Android device.
- [ ] Validate service disconnect/reconnect behavior on the target device.

## Planned detection and safety tests

- [ ] Distinguish ad-playing, skip-available, ad-completed, and unknown states.
- [ ] Unknown UI state never triggers an action.
- [ ] Stop control prevents further actions.
- [ ] A dismissed window alone never verifies reward completion.
- [ ] Only explicitly permitted Skip/Close controls may be actionable.

## Device validation

Initial target: Fruit Blast on a physical Android device. Device testing is a separate gate and must not be inferred from a successful CI build.
