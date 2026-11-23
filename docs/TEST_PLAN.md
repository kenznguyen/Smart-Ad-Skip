# Test Plan

## P0 — Foundation

- [x] GitHub Actions workflow installs JDK 17 and Android SDK 36.
- [x] Gradle configuration resolves successfully.
- [x] `:app:testDebugUnitTest` passes.
- [x] `:app:assembleDebug` produces a debug APK.
- [ ] APK launches on an Android API 26+ device.

## P1 — Accessibility Monitor

- [x] Snapshot preserves approved package/window metadata and timestamp.
- [x] Node counts are clamped to 0–200.
- [x] Disconnect clears the latest snapshot; reconnect accepts fresh events.
- [x] No automatic Skip/Close action is implemented.
- [ ] Validate timestamp refresh/stale indication and disconnect/reconnect on a physical device.

## P2 — Ad State Detection

- [x] Structural metadata is limited to view class, Resource ID, clickable flag and screen bounds.
- [x] Structural metadata is capped at 200 nodes per event.
- [x] Ad container marker without Skip marker classifies as AD_PLAYING.
- [x] A clickable Skip marker plus ad container classifies as SKIP_AVAILABLE.
- [x] Clickable nodes alone do not prove Skip availability.
- [x] AD_COMPLETED requires a prior ad marker and a subsequent non-empty tree in the same package.
- [x] Empty trees, stale snapshots and insufficient evidence resolve to UNKNOWN.
- [x] No UI action or reward verification is implemented.
- [ ] Validate classifier behavior against Fruit Blast on a physical device.
- [ ] Confirm resource-ID markers actually exist in target app; otherwise keep UNKNOWN.

## Device validation

Initial target: Fruit Blast (`com.funnyvideo.cgame.fruitblast`) on a physical Android device. CI does not replace device validation.
