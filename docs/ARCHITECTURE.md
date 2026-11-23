# Architecture

## v0.2.1 — P1 Monitor Freshness and Lifecycle

P1 adds event timestamps and explicit snapshot freshness without changing the observe-only safety boundary.

### Snapshot and dashboard

Each in-memory snapshot includes the event-received epoch timestamp. The dashboard formats the local event time and calculates age against the current clock, refreshing the age display every second. A snapshot is shown as stale after 15 seconds without a newer Accessibility event. This is a telemetry freshness indicator only; stale does not imply that an ad ended or that an application changed state.

### Repository lifecycle

- Events are accepted only while the service is connected.
- Disconnect/unbind/destroy clears the latest snapshot.
- A subsequent service connection can publish new snapshots.
- Snapshot timestamps are not persisted.

### Existing P1 data policy and Strict Safety

The monitor records only source package name, event type, window class name, bounded node counts, and event-received timestamp. It does not read or persist node text, content descriptions, input values, passwords, screenshots, or view hierarchies.

P1 remains observe-only: no `performAction`, gesture dispatch, clicking, Skip/Close execution, ad classification, or reward verification.
