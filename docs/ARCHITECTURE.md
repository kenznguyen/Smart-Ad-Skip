# Architecture

## v0.2.0 — P1 Accessibility Monitor

P1 adds an opt-in Android AccessibilityService for observation only.

### Implemented components

- **app** — Compose dashboard showing monitor connection and latest safe metadata.
- **accessibility** — AccessibilityService lifecycle, bounded UI-node counting, snapshot policy, and in-process state repository.
- **core safety boundary** — No UI action executor is present in P1.

### Snapshot data policy

The monitor records only:
- source package name;
- accessibility event type;
- window class name;
- bounded total node count (maximum 200);
- bounded clickable-node count (never greater than total nodes).

It does not read or persist node text, content descriptions, input values, passwords, screenshots, or view hierarchies. The service observes window state, windows, and window content change events with a 500 ms notification timeout.

### Strict Safety

- Accessibility monitoring is disabled until the user explicitly enables the service in Android Settings.
- P1 does not call `performAction`, dispatch gestures, click controls, or execute Skip/Close.
- No ad classification, reward verification, or automated decision is implemented.
- Unknown or missing metadata remains observational only.
- Disconnecting the service clears the latest in-memory snapshot.

## Planned components

- **detection** — ad and skip UI detection.
- **overlay** — floating status and user controls.
- **engine** — decision engine and strict safety controller.
- **action** — explicitly permitted UI actions (later phase; disabled until separately approved).
- **verification** — post-action state verification.
- **data** — preferences and event persistence.
- **logging** — auditable event records.

Components will be introduced incrementally. No planned component is considered implemented until it exists in source and has tests.

## Safety flow

Observe UI metadata → classify state (future phase) → evaluate safety constraints (future phase) → request/perform only a separately approved permitted action (future phase) → verify observed result (future phase) → log.

Unknown or ambiguous state must resolve to no action.
