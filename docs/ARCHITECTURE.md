# Architecture

## v0.2.3 — P2.3 Multi-signal Ad Detection (observe-only)

P2.3 adds source isolation and corroborating signals to the P2 classifier for the current Fruit Blast validation target.

### Event-source isolation

- Only Accessibility events whose package is exactly `com.funnyvideo.cgame.fruitblast` enter the classifier or replace the dashboard's latest snapshot.
- System UI, MIUI Screen Recorder and other package events are ignored. They cannot overwrite a Fruit Blast observation or create an ad-completion transition.
- This target-package filter is intentionally explicit; adding another target app requires a separately reviewed configuration and tests.

### Evidence model

- `AD_PLAYING`: a fresh, non-empty Fruit Blast tree has either a recognized semantic ad-container Resource ID or an exact allowlisted rewarded-ad SDK Activity class.
- Allowlisted SDK Activity classes: MBridge `MBRewardVideoActivity`, Google Mobile Ads `AdActivity`, Unity Ads `AdUnitActivity`, AppLovin `AppLovinFullscreenActivity`, and Meta Audience Network `AudienceNetworkActivity`.
- `SKIP_AVAILABLE`: a semantic ad-container Resource ID and a clickable node with a Skip Resource ID occur in the same fresh tree.
- `AD_COMPLETED`: a fresh, non-empty tree from the same Fruit Blast package follows a prior fresh tree with a semantic ad-container marker or allowlisted SDK Activity, and the current tree no longer contains that evidence.
- Generic `WebView`, `Button`, clickability, node counts, window changes, or external package events are not ad evidence.
- Unsupported or ambiguous cases remain `UNKNOWN`.

### Structural metadata allowlist

For at most 200 nodes per relevant Accessibility event, the service reads only View class name, Resource ID, clickable flag, and screen bounds. It also records package name, event type, window class and event timestamp. It does not read node text, content descriptions, input values, passwords, screenshots, OCR, gestures, clicks, SDK callbacks or reward state. Snapshots remain in memory only.

### Strict Safety

P2.3 remains detection-only. It does not call `performAction`, dispatch gestures, click controls, skip/close ads, verify rewards, or interact with ad SDKs. Snapshots become stale after 15 seconds; disconnect resets monitoring state.

## P2.3.1 — Structural transition diagnostics

- The monitor compares consecutive snapshots only when both are fresh, timestamps advance, and both snapshots belong to the exact Fruit Blast target package.
- Diagnostics report whether the Window class changed and the signed deltas for structural-node and clickable-node counts.
- A missing baseline, stale snapshot, non-advancing timestamp or package mismatch produces an unavailable comparison.
- Structural transitions are dashboard diagnostics only. Window changes and node-count deltas do not classify an ad and do not change AD_PLAYING, SKIP_AVAILABLE or AD_COMPLETED.
- The existing semantic Resource ID and exact SDK Activity allowlist remain the only positive ad evidence. Ambiguous observations remain UNKNOWN.

## Device validation

CI does not replace physical-device validation. The video supplied for P2.3 shows unrelated System UI/Screen Recorder events interleaved with Fruit Blast events, but does not expose a conclusive SDK Activity signature or semantic ad-container Resource ID for every ad frame. Those frames must remain `UNKNOWN` until supported evidence is actually observed.


## P2.8 — Ad context guard and execution policy hardening

- A visual match by itself is not evidence that an advertisement is active. A fresh, non-empty snapshot from the exact Fruit Blast package must independently establish an allowlisted ad SDK Activity context before a visual action can become `SKIP_AVAILABLE`.
- A verified `NEXT_AD` (`>>`) or `CLOSE` (`X`) match in a generic host/game window remains `UNKNOWN`; it cannot authorize a click.
- `ActionExecutionPolicy` accepts only classifier states `AD_PLAYING` and `SKIP_AVAILABLE`. It no longer accepts Resource ID/reward-marker inputs or makes a second, conflicting SDK-class decision.
- Resource IDs remain excluded from ad classification and click authorization.
- P2.7 two-frame temporal verification, exact package check, 15-second freshness limit, live Accessibility node/bounds revalidation, enabled/visible/clickable checks, one-attempt-per-control-state behavior, and Accessibility `ACTION_CLICK` remain required.
- `CONTINUE`, OCR, coordinate taps, screenshot persistence, SDK hooks, reward/impression spoofing, and target-app modification remain out of scope.
- CI validates logic and compilation only. Real-device validation is still required to confirm Fruit Blast exposes a supported ad-context signal and that the visual regions align with live Accessibility nodes. If the context signal is unavailable or ambiguous, the service must remain fail-closed.
