# Architecture

## v0.2.2 — P2 Ad State Detection (observe-only)

P2 adds bounded structural metadata and a conservative rule-based classifier. It does not perform UI actions.

### Structural metadata allowlist

For at most 200 nodes per Accessibility event, the service reads only:
- View class name
- Resource ID
- Clickable flag
- Screen bounds (left, top, right, bottom)

The existing package, event, window, node-count and timestamp fields remain. No node text, content description, input value, password, screenshot, OCR, gesture or click data is read or stored. Structural snapshots remain in memory only.

### State classification

- `AD_PLAYING`: a fresh non-empty tree contains a recognized ad-container Resource ID marker.
- `SKIP_AVAILABLE`: the same fresh tree contains both a recognized ad-container marker and a clickable node whose Resource ID contains `skip`.
- `AD_COMPLETED`: a fresh, non-empty tree in the same package follows a prior fresh tree containing a recognized ad-container marker, and the marker is now absent.
- `UNKNOWN`: missing/empty tree, stale/future timestamp, absent semantic markers, package mismatch, or insufficient evidence.

Recognized ad Resource ID markers are deliberately narrow: `ad_container`, `ad_view`, `interstitial_ad`, and `rewarded_ad`. Generic clickability alone is never evidence of Skip. These are heuristics over app-provided identifiers, not universal ad recognition; unsupported apps will remain UNKNOWN.

### Strict Safety

P2 is detection-only. It does not call `performAction`, dispatch gestures, click controls, skip/close ads, verify rewards, or interact with ad SDKs. The dashboard forces the displayed state to UNKNOWN when the latest snapshot becomes stale (15 seconds). Disconnect resets the detected state.

## P1 freshness and lifecycle

Each in-memory snapshot includes the event-received epoch timestamp. The dashboard formats local event time and calculates age against the current clock. Disconnect/unbind/destroy clears the latest snapshot; reconnect accepts new events.
