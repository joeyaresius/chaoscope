# Chaoscope

A modern, multi-platform reimagining of the legendary **Chaoscope** desktop tool,
originally created by **Nicolas Desprez** in the early 2000s. His pioneering work
introduced countless people to the visual world of strange attractors — this project
carries that spirit forward, rebuilt from scratch for today's devices.

> **Note:** No original code, assets, or binaries from Nicolas Desprez's Chaoscope
> were used. All source code was written independently. The name is used in tribute
> and with attribution.

Strange attractors are the visual fingerprints of chaotic systems — mathematical
structures that trace hypnotic, infinitely detailed shapes when iterated millions of
times. Chaoscope lets you explore, tweak, and export them.

---

## Repository layout

```
chaoscope/
├── core/        ← Portable C++17 render engine (attractor math + histogram tone-mapping)
│                  Shared verbatim by both the Android and iOS apps.
├── android/     ← Android app — Kotlin / Jetpack Compose + JNI → core/   (Play production)
├── ios/         ← iOS app — SwiftUI + plain-C bridge → core/             (sideload-viable)
├── prototype/   ← Python proof-of-concept + marketing/asset tooling
│   ├── engine/  ← NumPy port of the render pipeline (the original POC + CLI)
│   ├── assets/  ← Launcher-icon / Instagram / video / outro asset generators
│   └── qa/      ← One-off analysis & QA scripts
├── LICENSE      ← Apache 2.0
└── PRIVACY.md   ← Privacy policy (no data collection)
```

### How the pieces connect

The **same C++ engine in [`core/`](core/) powers both apps** — there is no duplicated
render code:

```
        core/  (attractors.cpp + renderer.cpp, no platform headers)
        ╱                                                    ╲
  Android JNI bridge                                  iOS plain-C bridge
  android/.../chaoscope_jni.cpp                       ios/Core/chaoscope_c.cpp
        │                                                    │
  Kotlin / Jetpack Compose UI                         SwiftUI UI
```

The Python [`prototype/`](prototype/) is **not** wired into the apps. It was the
original research vehicle (where the math + histogram pipeline was proven before the
C++ port) and now also hosts the scripts that generate launcher icons and store /
social-media assets. See [prototype/README.md](prototype/README.md).

---

## Platform status

| Platform | Stack | Status | Docs |
|---|---|---|---|
| **Android** | Kotlin · Compose · JNI · C++ | In Google Play production (v0.3.0) | [android/README.md](android/README.md) |
| **iOS** | SwiftUI · C bridge · C++ | Builds in CI; sideload-viable on device | [ios/README.md](ios/README.md) |
| **Core engine** | C++17 | Shared by both apps | [core/README.md](core/README.md) |
| **Prototype** | Python · NumPy · Pillow | Reference POC + asset tooling | [prototype/README.md](prototype/README.md) |

---

## License

Licensed under the **Apache License 2.0** — see [LICENSE](LICENSE).

```
Copyright 2026 Lucas Balancin
```

## Privacy

This project collects no user data. All rendering is performed locally on the device.
See [PRIVACY.md](PRIVACY.md).

## Acknowledgements

- **Nicolas Desprez** — creator of the original Chaoscope for Windows, whose work
  inspired this project.
- The mathematics and chaos-theory community for decades of research into strange
  attractors.

Feedback and suggestions: **chaoscope@duck.com**.
