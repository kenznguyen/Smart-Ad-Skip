# Smart Ad Skip

Standalone Android proof of concept for safe, user-authorized ad UI assistance.

## Project status

**v0.1.0 — Foundation**

This initial milestone establishes the Android application skeleton and CI pipeline. Accessibility monitoring, ad detection, overlay controls, and skip actions are not implemented yet.

## Technology

- Kotlin
- Jetpack Compose
- Android Gradle Plugin
- Minimum Android version: API 26 (Android 8.0)
- Compile/target SDK: API 36
- JDK 17

## Build

The project uses Gradle 8.13 and Android SDK 36.

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Safety boundaries

- No ad SDK modification or reward callback spoofing.
- No fabricated ad views, clicks, or reward completion.
- No CAPTCHA or anti-bot bypass.
- No automatic action when the UI state is ambiguous.
- Skip/close actions will only be considered when clearly exposed by the target app and permitted by the user-approved safety rules.
- A dismissed ad window is not proof that a reward was granted.

## Planned milestones

1. Foundation and CI
2. Accessibility service lifecycle and window observation
3. Ad/skip UI detection
4. Floating control window
5. Strict safety decision engine
6. Action verification and device testing

Fruit Blast is the initial test application for controlled feasibility testing.
