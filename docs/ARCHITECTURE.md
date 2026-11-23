# Architecture

## v0.1.0 scope

The first milestone intentionally contains only the Android application shell and build pipeline.

## Planned components

- **app** — Activity, Compose UI, navigation, settings.
- **core** — domain models, state, and interfaces.
- **accessibility** — Android AccessibilityService and window observation.
- **detection** — ad and skip UI detection.
- **overlay** — floating status and user controls.
- **engine** — decision engine and strict safety controller.
- **action** — explicitly permitted UI actions.
- **verification** — post-action state verification.
- **data** — preferences and event persistence.
- **logging** — auditable event records.

Components will be introduced incrementally. No planned component is considered implemented until it exists in source and has tests.

## Safety flow

Observe UI → classify state → evaluate safety constraints → request/perform only a permitted action → verify observed result → log.

Unknown or ambiguous state must resolve to no action.
