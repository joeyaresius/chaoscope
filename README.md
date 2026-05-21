# Chaoscope for Android

A modern Android reimagining of the legendary **Chaoscope** desktop tool, originally created by **Nicolas Desprez** in the early 2000s. His pioneering work introduced countless people to the visual world of strange attractors — this app carries that spirit forward, rebuilt from scratch for today's mobile devices.

> **Note:** No original code, assets, or binaries from Nicolas Desprez's Chaoscope were used. All source code was written independently. The name is used in tribute and with attribution.

---

## What is it?

Strange attractors are the visual fingerprints of chaotic systems — mathematical structures that trace hypnotic, infinitely detailed shapes when iterated millions of times. Chaoscope for Android lets you explore, tweak, and export them right from your phone.

---

## Features

- **12 attractors** — 2D and 3D systems, each with editable parameters and camera control
- **Logarithmic density rendering** — the same histogram tone-mapping technique that made the original Chaoscope images distinctive
- **7 colour palettes** — Nebula, Fire, Electric, Aurora, Matrix, Greyscale, and a fully **custom palette editor** (HSV stop editor with live gradient preview)
- **5 render styles** — Standard, Gas, Liquid, Plasma, Solid
- **Full 3D camera control** — yaw, pitch, roll, zoom sliders update the preview in real time
- **Blank-render auto-retry** — if the orbit doesn't converge, the engine retries at 4× iterations with wider bounds before surfacing an error
- **First-run tutorial** — 5-step coach-mark overlay highlights every major feature
- **PNG export** — saved to `Pictures/Chaoscope/` on-device
- **No internet, no tracking, no ads** — fully offline

### Attractors included

| Name | Dimensions |
|---|---|
| Clifford | 3D |
| Peter de Jong | 3D |
| Gumowski-Mira | 2D |
| Lorenz | 3D |
| Rössler | 3D |
| Aizawa | 3D |
| Thomas | 3D |
| Chaotic Flow | 3D |
| Icon | 2D |
| Barnsley Fern | 2D |
| Julia | 2D |
| Pickover | 3D |

---

## Architecture

```
Kotlin / Jetpack Compose  (UI layer)
         │
         ▼
  ChaoscopeViewModel      (render state, tutorial, palette editor)
         │
         ▼
  ChaoscopeEngine.kt      (JNI bridge)
         │
         ▼
  libchaoscope.so  (C++17 native library)
  ├── attractors.cpp   ← iterate attractor equations
  └── renderer.cpp     ← histogram accumulation + tone-mapping + palette LUT
```

The rendering pipeline follows the classic Chaoscope approach:

1. Iterate the attractor equation for millions of steps → collect `(x, y, z)` points
2. Project 3D points through a configurable camera → `(u, v)` screen coordinates
3. Accumulate into a density histogram
4. Apply logarithmic tone-mapping to compress the dynamic range
5. Map through a colour palette LUT (preset or user-defined) → final RGBA bitmap

If the histogram is empty (orbit diverged), the native layer returns `null` and the ViewModel retries once at 4× iterations with 15% extra bounds padding. A snackbar is shown if both attempts fail.

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog or newer |
| Android Gradle Plugin | 8.x |
| Kotlin | 2.x |
| CMake | 3.22.1+ |
| NDK | r25 or newer |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

---

## Building

```bash
# Clone the repo
git clone https://github.com/lucasbalancin/chaoscope.git
cd chaoscope/android

# Build debug APK
./gradlew assembleDebug

# Install on a connected device or running emulator
./gradlew installDebug
```

The C++ native library is compiled automatically by the Gradle CMake integration. No manual NDK steps are needed as long as the NDK is installed in Android Studio (**SDK Manager → SDK Tools → NDK (Side by side)**).

### Release build

Set the following environment variables before running `assembleRelease`:

```bash
export CHAOSCOPE_KEYSTORE=/path/to/keystore.jks
export CHAOSCOPE_KEYSTORE_PASSWORD=...
export CHAOSCOPE_KEY_ALIAS=chaoscope
export CHAOSCOPE_KEY_PASSWORD=...
./gradlew assembleRelease
```

If no keystore is configured, the release build falls back to the debug signing config so local smoke-tests still work.

---

## Running the tests

The project includes pure-Kotlin JUnit 4 unit tests (no Android framework, no emulator needed):

```bash
cd android
./gradlew :app:test
```

Test coverage includes:

| Test class | What it covers |
|---|---|
| `ColorMathTest` | HSV↔RGB conversion — primary colours, roundtrips, edge cases, hue never negative |
| `ColorStopSerializerTest` | Palette stop serialization — roundtrips, malformed input, partial groups |
| `AttractorDefsTest` | Attractor param count consistency, defaults within ranges, `PaletteType.CUSTOM` ordinal, `UiState` defaults |

---

## Project structure

```
android/              ← Android app
  app/src/
    main/
      cpp/            ← C++17 native rendering engine
      java/com/chaoscope/
        ui/           ← Compose screens (SplashScreen, AttractorScreen,
        │               Tutorial, PaletteEditorDialog)
        data/         ← Preferences (DataStore)
        AttractorDefs.kt      ← attractor/palette/UiState definitions
        ColorMath.kt          ← pure HSV↔RGB helpers (shared with tests)
        ColorStopSerializer.kt← pure palette stop serialization helpers
        ChaoscopeEngine.kt    ← JNI bridge
        ChaoscopeViewModel.kt ← render state, export, tutorial, palette
        MainActivity.kt
      res/            ← resources, icons, themes
    test/             ← JUnit 4 unit tests (no emulator needed)
prototype/            ← Python proof-of-concept
LICENSE
README.md
PRIVACY.md
```

---

## Contributing

Contributions are welcome. Please open an issue first for significant changes so we can discuss the direction before you invest time in a PR.

### Guidelines

- **Tests:** Add or update tests in `android/app/src/test/` for any pure logic you touch. Run `./gradlew :app:test` before opening a PR.
- **Native changes:** If you modify `renderer.cpp` or `chaoscope_jni.cpp`, test on both an arm64 device and an x86_64 emulator. The CMake build targets both ABIs.
- **Compose UI:** Changes to UI composables should be verified by hand on a real device with gesture navigation enabled, since there are no Compose UI tests in the repo.
- **Code style:** Follow the existing Kotlin conventions (4-space indents, named parameters for multi-arg calls, no trailing summaries in comments).

Feedback and suggestions can also be sent to **chaoscope@duck.com**.

---

## Privacy

This app collects no user data. All processing is performed locally on the device. No third-party analytics, crash reporting, or advertising SDKs are included.

See the full [Privacy Policy](PRIVACY.md) for details.

---

## License

Licensed under the **Apache License 2.0** — see [LICENSE](LICENSE) for the full text.

```
Copyright 2026 Lucas Balancin

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## Acknowledgements

- **Nicolas Desprez** — creator of the original Chaoscope for Windows, whose work inspired this project.
- The mathematics and chaos theory community for decades of research into strange attractors.
