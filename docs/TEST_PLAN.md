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

## P2.3 — Multi-signal Detection

- [x] System UI and Screen Recorder package events are marked irrelevant to the Fruit Blast classifier.
- [x] Exact allowlisted rewarded-ad SDK Activity signatures classify as AD_PLAYING only with a fresh, non-empty tree.
- [x] A same-package transition from explicit ad evidence to a fresh non-empty host-app tree can classify as AD_COMPLETED.
- [x] Generic WebView, Button, clickable state and node counts alone remain UNKNOWN.
- [x] Foreign-package events cannot prove AD_COMPLETED.
- [x] SKIP_AVAILABLE still requires semantic ad-container and clickable Skip Resource ID evidence in the same tree.
- [ ] Validate each SDK signature and transition on a physical device.
- [ ] Confirm actual Fruit Blast ad frames expose one of the allowlisted signatures or semantic Resource IDs; otherwise retain UNKNOWN.
- [x] No UI action, SDK callback, reward verification, text/OCR/screenshot collection is implemented.

## P2.3.1 — Structural transition diagnostics

- [x] Compare only fresh snapshots from the exact same target package.
- [x] Report Window class changes and signed structural/clickable node-count deltas.
- [x] Missing baseline, stale history and non-advancing timestamps produce unavailable diagnostics.
- [x] Structural diagnostics do not independently change the ad classifier state.
- [ ] Verify dashboard diagnostics across real ad appearance and return-to-game transitions on a physical device.

## Device validation

Initial target: Fruit Blast (`com.funnyvideo.cgame.fruitblast`) on a physical Android device. CI does not replace device validation.


## P2.16 — Candidate discovery and click authorization

- [x] OCR `Next`/`Skip` resolves to `NEXT_AD` without requiring Visual Verification.
- [x] OCR `Close`/`Dismiss`/`X` resolves to `CLOSE` without requiring Visual Verification.
- [x] Protected labels and phrases reject the candidate.
- [x] Ambiguous OCR text does not resolve to an action.
- [x] NO_TEXT requires symbol candidate discovery for `>>` or `X`; it does not grant click permission.
- [x] Only `NEXT_AD` and `CLOSE` are eligible actions.
- [x] Freshness, package, live Accessibility, Click Authorization, completed-ad block and one-attempt guard remain mandatory.
- [x] Denial logs identify the stage and reason.
- [ ] Validate OCR text buttons, symbol-only `>>` and `X`, protected CTA labels, and deny logs on a physical Fruit Blast device.


## P2.17 — Close-Only candidate discovery and authorization

- [x] OCR recognizes only `X`, `Close` and `Dismiss` as candidate labels.
- [x] `Next`, `Skip` and `>>` never resolve to an action.
- [x] Pixel detector recognizes the X-on-neutral-circle pattern only; former yellow double-chevron pattern returns UNKNOWN.
- [x] Execution policy permits CLOSE only and blocks legacy NEXT_AD, UNKNOWN and AD_COMPLETED.
- [x] Protected labels and ambiguous text reject; symbol-only candidate requires positive X detection.
- [x] Exact package, Freshness Guard, live Accessibility matching, visible/enabled/clickable checks, one-attempt guard and final ACTION_CLICK remain required.
- [ ] CI: unit tests and debug APK build.
- [ ] Physical Fruit Blast validation: close button X detection, Close/Dismiss text, protected CTA rejection, live node match and denial logs.
