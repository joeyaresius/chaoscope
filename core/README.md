# Chaoscope — Shared render engine (`core/`)

The portable C++17 engine that powers both the Android and iOS apps. It contains
**only platform-neutral code** — no JNI, no EGL/OpenGL, no `<android/*>` or Apple
headers — so it compiles unchanged on both platforms.

```
core/
├── attractors.cpp / .h   ← attractor equations; iterate (x,y,z) → (x',y',z')
└── renderer.cpp  / .h    ← camera projection, density histogram,
                             logarithmic tone-mapping, palette LUT
```

## Who consumes it

| Consumer | Binding | Build wiring |
|---|---|---|
| **Android** | JNI — `android/app/src/main/cpp/chaoscope_jni.cpp` | `android/app/src/main/cpp/CMakeLists.txt` compiles `core/*.cpp` and adds `core/` to the include path. The Android-only `gpu_renderer.*` (EGL/GLESv3) and the JNI bridge stay in the Android tree and `#include "renderer.h"` / `"attractors.h"` from here. |
| **iOS** | Plain-C bridge — `ios/Core/chaoscope_c.cpp` (`extern "C"`, imported via the Swift bridging header) | `ios/project.yml` adds `../core` as a source path and header search path. |

> **Rule:** keep `core/` free of platform headers. Anything that needs JNI, OpenGL,
> Metal, or an OS framework belongs in the platform tree, not here. This is what lets
> both apps share one copy of the engine.

## Pipeline

```
attractorIterateN()        iterate the equation for millions of steps → (x, y, z)
        ↓
camera project             3-D points → (u, v) screen coords (yaw/pitch/roll/zoom)
        ↓
density histogram          accumulate integer hit-counts per pixel
        ↓
log tone-mapping           log(1 + count) to compress dynamic range (Chaoscope "Gas")
        ↓
palette LUT                map normalised density through a colour palette → RGBA
```

If the histogram is empty (the orbit diverged), the render function returns
`null`/`nullptr`; the platform layer is responsible for the retry-at-higher-iterations
fallback.

## Attractors

Type ordinals **must stay in sync** with the Kotlin `AttractorType` enum and the Swift
`AttractorDefs`. From `attractors.h`:

| # | Attractor | params layout |
|---|---|---|
| 0 | Clifford | a, b, c, d |
| 1 | Peter de Jong | a, b, c, d |
| 2 | Gumowski-Mira | a, mu |
| 3 | Lorenz | sigma, rho, beta, dt |
| 4 | Rössler | a, b, c, dt |
| 5 | Aizawa | a, b, c, d, e, f, dt |
| 6 | Thomas | b, dt |
| 7 | Chaotic Flow (Dadras) | a, b, c, d, e, dt |
| 8 | Icon (Symmetry Icons, p=3) | lambda, alpha, beta, omega |
| 9 | IFS (Barnsley Fern, 3-D) | width, lean, twist |
| 10 | Julia (quaternion, 3-D) | c_re, c_im, c_j |
| 11 | Pickover | a, b, c, d |
| 12 | Halvorsen | a, dt |
| 13 | Burke-Shaw | s, v, dt |
| 14 | Sprott-B | a, b, dt |
| 15 | Lorenz-84 | a, b, F, G, dt |
| 16 | Hénon (2-D map) | a, b |

## Building / testing

`core/` has no standalone build — it is compiled by each platform's toolchain:

- **Android:** `cd android && ./gradlew :app:assembleDebug` (CMake compiles `core/`).
- **iOS:** `cd ios && xcodegen generate && xcodebuild ...` (see [ios/README.md](../ios/README.md)).

The Python [`prototype/engine/`](../prototype/engine/) is an independent NumPy
reference implementation of the same algorithm — useful for fast visual QA without a
device build, but it is **not** the source for these C++ files.
