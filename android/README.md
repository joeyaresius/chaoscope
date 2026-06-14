# Chaoscope for Android

The Android app — Kotlin / Jetpack Compose UI over the shared C++ render engine in
[`core/`](../core/), bridged via JNI. Currently in **Google Play production** (v0.3.0).

For the project overview and how the platforms connect, see the
[root README](../README.md).

---

## Features

### Attractors & exploration
- **15 attractors** — 2D and 3D systems, each with editable parameters and camera control
- **45 curated presets** (3 per attractor) — one tap to load a stunning starting point
- **User-saved presets** — bookmark your favourite configurations and reload them any time
- **Full 3D camera control** — yaw, pitch, roll, zoom sliders update the preview in real time
- **Blank-render auto-retry** — if the orbit doesn't converge, the engine retries at 4× iterations with wider bounds before surfacing an error

### Visuals
- **Logarithmic density rendering** — the histogram tone-mapping technique that made the original Chaoscope images distinctive
- **11 colour palettes** — Nebula, Fire, Electric, Aurora, Matrix, Greyscale, Spectrum, Sunset, Ice, Neon, plus a fully **custom palette editor** (HSV stop editor with 2D board + hue bar and live gradient preview)
- **5 render styles** — Standard, Gas, Liquid, Plasma, Solid
- **4 render quality tiers** — Draft → Standard → High → Ultra
- **Depth-cue shading** — a subtle near/far luminance gradient on 3D attractors
- **Full palette range toggle** — stretches the palette across actual orbit density
- **Background colour** — Black, White, or Transparent

### Export
- **PNG export** — Preview (768 px), HD (2048 px), or 4K (3840 px), saved to `Pictures/Chaoscope/`
- **Transparent PNG** — renders with a transparent background for compositing
- **Set as wallpaper** — one-tap wallpaper from any render
- **Auto-generated share caption** — every PNG share carries a caption (attractor · palette · parameters)
- **MP4 video animation** — three modes:
  - **Morph** — smoothly interpolates between two saved keyframes (A → B)
  - **Orbit Trace** — incrementally reveals the orbit, each dot coloured by its palette position
  - **Param Sweep** — varies all parameters toward a random target for an organic morph
  - Ping-pong loop option doubles any export into a seamless forward-reverse loop
  - 15 / 30 / 60 frame quick-picks or any custom frame count (2–600)
  - Runs as a **foreground service** with a partial wake lock, so export survives backgrounding or screen-off; cancellable from the notification

### UX
- **Central play button** — renders the current view on demand from the control tab bar
- **Live palette preview** — palette changes recolour the depth-shaded dot cloud instantly instead of a full re-render
- **Splash screen** — a live Lorenz butterfly renders in the background on startup
- **First-run tutorial** — 5-step coach-mark overlay
- **No ads, no tracking** — fully offline (the Play in-app review API is the sole network call, made once after ~20 renders/exports)

### Attractors included

| Name | Dim | Name | Dim | Name | Dim |
|---|---|---|---|---|---|
| Clifford | 3D | Aizawa | 3D | Barnsley Fern | 3D |
| Peter de Jong | 3D | Thomas | 3D | Julia | 3D |
| Gumowski-Mira | 2D | Chaotic Flow | 3D | Pickover | 3D |
| Lorenz | 3D | Icon | 2D | Halvorsen | 3D |
| Rössler | 3D | Burke-Shaw | 3D | Sprott-B | 3D |

---

## Architecture

```
Kotlin / Jetpack Compose      (UI layer)
         │
         ▼
  ChaoscopeViewModel           (render state, export, tutorial, palette editor)
         ├── VideoExporter.kt        (H.264/MP4 — MediaCodec + MediaMuxer)
         ├── VideoExportService.kt   (ForegroundService — keeps process alive during export)
         ▼
  ChaoscopeEngine.kt           (JNI bridge)
         ▼
  libchaoscope.so  (C++17)
  ├── core/attractors.cpp      ← iterate attractor equations   (shared with iOS)
  ├── core/renderer.cpp        ← histogram + tone-map + palette (shared with iOS)
  ├── cpp/gpu_renderer.cpp     ← Android-only GPU path (EGL / GLES v3)
  └── cpp/chaoscope_jni.cpp    ← Android-only JNI entry points
```

The portable engine lives in the top-level [`core/`](../core/) directory and is
compiled into `libchaoscope.so` by the CMake build (`app/src/main/cpp/CMakeLists.txt`
references `../../../../../core`). The Android-only GPU renderer and JNI bridge stay
under `app/src/main/cpp/`.

The video encoder writes NV12 (YUV420SemiPlanar) directly into MediaCodec input
buffers for maximum hardware-encoder compatibility, muxes to a seekable temp file,
then copies the finished MP4 to MediaStore (avoids the moov-atom issue with
non-seekable ContentResolver descriptors).

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog or newer |
| Android Gradle Plugin | 8.x |
| Kotlin | 2.x |
| CMake | 3.22.1+ |
| NDK | r25 or newer |
| compileSdk | 37 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |

---

## Building

```bash
# from the repo root
cd android

# Debug APK
./gradlew assembleDebug

# Install on a connected device / running emulator
./gradlew installDebug
```

The C++ native library (including `core/`) is compiled automatically by the Gradle
CMake integration. No manual NDK steps are needed as long as the NDK is installed in
Android Studio (**SDK Manager → SDK Tools → NDK (Side by side)**).

### Release build

Set these environment variables before `assembleRelease`:

```bash
export CHAOSCOPE_KEYSTORE=/path/to/keystore.jks
export CHAOSCOPE_KEYSTORE_PASSWORD=...
export CHAOSCOPE_KEY_ALIAS=chaoscope
export CHAOSCOPE_KEY_PASSWORD=...
./gradlew assembleRelease
```

If no keystore is configured, the release build falls back to the debug signing config
so local smoke-tests still work.

---

## Tests

Pure-Kotlin JUnit 4 unit tests (no Android framework, no emulator):

```bash
cd android
./gradlew :app:test
```

| Test class | Covers |
|---|---|
| `ColorMathTest` | HSV↔RGB conversion — primary colours, roundtrips, edge cases |
| `ColorStopSerializerTest` | Palette stop serialization — roundtrips, malformed input, partial groups |
| `AttractorDefsTest` | Param-count consistency, defaults within ranges, `PaletteType.CUSTOM` ordinal, `UiState` defaults |
| `PresetSerializerTest` | Preset (de)serialization |

---

## Project structure

```
android/
  app/src/
    main/
      cpp/            ← Android-only native: gpu_renderer.*, chaoscope_jni.cpp, CMakeLists.txt
                        (portable engine is in the top-level core/)
      java/com/chaoscope/
        ui/           ← Compose screens (SplashScreen, AttractorScreen, Tutorial, PaletteEditorDialog)
        data/         ← Preferences (DataStore)
        AttractorDefs.kt        ← attractor / palette / UiState definitions
        ChaoscopeEngine.kt      ← JNI bridge
        ChaoscopeViewModel.kt   ← render state, export, tutorial, palette
        ColorMath.kt            ← pure HSV↔RGB helpers (shared with tests)
        VideoExporter.kt        ← H.264/MP4 encoding pipeline
        VideoExportService.kt   ← ForegroundService + export status state
        ...
      res/            ← resources, icons, themes, localized strings
    test/             ← JUnit 4 unit tests
  fastlane/           ← Play Store metadata / changelogs
```

---

## Contributing

- **Tests:** add/update tests in `app/src/test/` for any pure logic you touch; run `./gradlew :app:test` before a PR.
- **Native changes:** if you modify anything in `core/` or `cpp/`, test on both an arm64 device and an x86_64 emulator (CMake targets both ABIs). Changes to `core/` also affect iOS — see [core/README.md](../core/README.md).
- **Compose UI:** verify by hand on a real device with gesture navigation (there are no Compose UI tests).
- **Code style:** follow existing Kotlin conventions (4-space indents, named params for multi-arg calls).
