# Chaoscope for Android

A modern Android reimagining of the legendary **Chaoscope** desktop tool, originally created by **Nicolas Desprez** in the early 2000s. His pioneering work introduced countless people to the visual world of strange attractors — this app carries that spirit forward, rebuilt from scratch for today's mobile devices.

> **Note:** No original code, assets, or binaries from Nicolas Desprez's Chaoscope were used. All source code was written independently. The name is used in tribute and with attribution.

---

## What is it?

Strange attractors are the visual fingerprints of chaotic systems — mathematical structures that trace hypnotic, infinitely detailed shapes when iterated millions of times. Chaoscope for Android lets you explore, tweak, and export them right from your phone.

---

## Features

### Attractors & exploration
- **16 attractors** — 2D and 3D systems, each with editable parameters and camera control
- **48 curated presets** (3 per attractor) — one tap to load a stunning starting point
- **User-saved presets** — bookmark your favourite configurations and reload them any time
- **Full 3D camera control** — yaw, pitch, roll, zoom sliders update the preview in real time
- **Blank-render auto-retry** — if the orbit doesn't converge, the engine retries at 4× iterations with wider bounds before surfacing an error

### Visuals
- **Logarithmic density rendering** — the same histogram tone-mapping technique that made the original Chaoscope images distinctive
- **11 colour palettes** — Nebula, Fire, Electric, Aurora, Matrix, Greyscale, Spectrum, Sunset, Ice, Neon, plus a fully **custom palette editor** (HSV stop editor with 2D board + hue bar and live gradient preview)
- **5 render styles** — Standard, Gas, Liquid, Plasma, Solid
- **4 render quality tiers** — Draft → Standard → High → Ultra (scales iteration count and performance)
- **Depth-cue shading** — adds a subtle near/far luminance gradient to 3D attractors
- **Full palette range toggle** — stretches the palette across the actual orbit density instead of the theoretical maximum
- **Background colour** — Black, White, or Transparent

### Export
- **PNG export** — Preview (768 px), HD (2048 px), or 4K (3840 px), saved to `Pictures/Chaoscope/`
- **Transparent PNG** — renders with a transparent background for compositing
- **Set as wallpaper** — one-tap wallpaper from any render
- **Auto-generated share caption** — every PNG share carries a caption (attractor · palette · parameters); "Copy Caption" copies it to the clipboard
- **MP4 video animation** — three modes:
  - **Morph** — smoothly interpolates between two saved keyframes (A → B)
  - **Orbit Trace** — incrementally reveals the attractor orbit, each dot coloured by its palette position (cumulative trace build-up)
  - **Param Sweep** — automatically varies all parameters toward a random target for an organic, unpredictable morph
  - Ping-pong loop option doubles any export into a seamless forward-reverse loop
  - 15 / 30 / 60 frame quick-picks or any custom frame count (2–600)
  - Runs as a **foreground service** holding a partial wake lock, so the export keeps running (and shows a progress notification) when the app is backgrounded **or the screen turns off**; Cancel button in the notification stops the job cleanly

### UX
- **Central play button** — a render button docked in the control tab bar renders the current view on demand
- **Live palette preview** — changing the palette recolours the depth-shaded dot cloud instantly instead of triggering a full re-render
- **Splash screen** — renders a live Lorenz butterfly in the background on startup
- **First-run tutorial** — 5-step coach-mark overlay highlights every major feature
- **No ads, no tracking** — fully offline (Google Play in-app review API is the sole network call, made once after 20 renders/exports)

---

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
| Barnsley Fern | 3D |
| Julia | 3D |
| Pickover | 3D |
| Halvorsen | 3D |
| Burke-Shaw | 3D |
| Chen-Lee | 3D |
| Sprott-B | 3D |

---

## Architecture

```
Kotlin / Jetpack Compose  (UI layer)
         │
         ▼
  ChaoscopeViewModel      (render state, export, tutorial, palette editor)
         │
         ├── VideoExporter.kt      (H.264/MP4 encoding — MediaCodec + MediaMuxer)
         │
         ├── VideoExportService.kt (ForegroundService — keeps process alive during export)
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

The video encoder writes NV12 (YUV420SemiPlanar) directly into MediaCodec input buffers for maximum hardware-encoder compatibility. The muxer writes to a local temp file (seekable) and copies the finished MP4 to MediaStore to avoid the moov-atom issue with non-seekable ContentResolver file descriptors.

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
| Target SDK | 36 |

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
        AttractorDefs.kt       ← attractor/palette/UiState definitions
        ChaoscopeApplication.kt← Application class (notification channel setup)
        ChaoscopeEngine.kt     ← JNI bridge
        ChaoscopeViewModel.kt  ← render state, export, tutorial, palette
        ColorMath.kt           ← pure HSV↔RGB helpers (shared with tests)
        ColorStopSerializer.kt ← pure palette stop serialization helpers
        MainActivity.kt
        VideoExporter.kt       ← H.264/MP4 encoding pipeline
        VideoExportService.kt  ← ForegroundService + export status state
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

The only outbound network call is the **Google Play in-app review API**, triggered once after approximately 20 renders or video exports. This call is made entirely by the Play Store library; no personal data or usage statistics are sent by the app itself.

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
