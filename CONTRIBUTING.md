# Contributing to Palleria

Thank you for taking the time to contribute.

## Project Overview

Palleria is an Android app built with Kotlin, Jetpack Compose, and Miuix-based UI components. The project uses a single Android application module in this directory.

## Before You Start

- Read the code in the area you plan to change before editing it.
- Keep changes small and focused.
- Match the existing conventions in the surrounding files.
- Avoid unrelated formatting or refactors.

## Suggested Workflow

1. Create a branch for your work.
2. Make the change.
3. Run the relevant checks.
4. Verify the behavior on device or emulator when the change affects UI or app flow.
5. Open a pull request with a clear summary of the change and validation performed.

## Development Notes

- The Android module lives in this directory.
- Main source code is under `src/main/...`.
- Common validation commands are:
  - `..\gradlew.bat :app:compileDebugKotlin`
  - `..\gradlew.bat :app:assembleDebug`
- If you touch release packaging, resource shrinking, or R8-sensitive code, run a broader build before submitting.

### Faster debug builds

Debug builds default to one ABI (`arm64-v8a`) and the unoptimized Rust profile, so iterating does not pay for three LTO release builds of the Rust core. Adding `-Ppalleria.splits=false` also skips the four-way APK packaging. Match your device's ABI if it differs:

```sh
adb shell getprop ro.product.cpu.abi
..\gradlew.bat :app:assembleDebug -Ppalleria.abis=arm64-v8a -Ppalleria.splits=false
```

Release always uses all three ABIs with the optimized profile and ABI splits; these properties only shorten local debug builds. See [docs/dev/build.md](../docs/dev/build.md#debugビルドを速くする) for the full matrix.

## Style Guidelines

- Prefer the smallest change that solves the problem.
- Preserve public behavior unless the task explicitly asks for a behavior change.
- Keep UI changes consistent with the current design language.
- Use descriptive names and avoid unnecessary abstraction.
- When editing localization strings, update all affected locale files.

## Testing

Run the most relevant checks for your change:

- Kotlin or state wiring changes: `:app:compileDebugKotlin`
- UI, navigation, or resource changes: `:app:assembleDebug`
- Storage, persistence, or startup behavior: add targeted manual verification in addition to build checks

If you add or change logic, include tests when practical.

## Pull Requests

Please include:

- A short summary of what changed
- Any user-visible impact
- Validation steps you ran
- Screenshots or screen recordings for UI changes when useful

## Documentation

If your change affects setup, behavior, or usage, update the relevant documentation alongside the code.

