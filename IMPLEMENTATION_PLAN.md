# Chaoscope — Implementation Plan

_Last updated: 2026-05-21_

This plan covers tracks for advancing the Android app, ordered by value-to-effort:

1. **Track A — Curated preset gallery** (high impact, contained) — ✅ DONE
2. **Track B — Depth shading for 3D attractors** (makes existing 3D work read visually) — ✅ DONE
3. **Track C — Complete the 3D set** (3D Barnsley Fern, quaternion Julia, Pickover) — ✅ DONE
4. **Track D — auto-rotate toggle** (cheap motion win) — ✅ DONE
5. **Track E — bigger bets** (video export, wallpaper/4K, true quaternion ray-marcher) — NOT STARTED

**Status (2026-05-21):** Tracks A–D shipped, plus a batch of fixes/extras beyond the
original plan: zoom fix, full-palette-range (histogram equalisation), auto-render on look
changes, 2D colour picker + "start from" palettes, Icon Snowflake fix, and configurable
render detail + preview density. The engine is now 10/12 attractors in 3-D (only
Gumowski-Mira and Icon remain 2-D — no canonical 3-D form). Track E is the remaining work.

---

## Background: current attractor dimensionality

The engine has 12 attractors (`attractors.cpp` + `AttractorDefs.kt`). The `is3D` flag
is correct against the actual z-equations. Status:

- **Genuinely 3D (8):** Lorenz, Rössler, Aizawa, Thomas, Chaotic Flow (native 3D ODEs);
  Clifford, Peter de Jong (2D maps lifted with an added z-equation); Pickover
  (3D but `z' = sin(x)` only — a thin folded sheet, not a true volume).
- **Still 2D, z=0 (4):** Barnsley Fern (IFS), Julia, Icon, Gumowski-Mira.
  Rotation sliders are forced to 0 for these
  ([ChaoscopeViewModel.kt:120](android/app/src/main/java/com/chaoscope/app/ChaoscopeViewModel.kt#L120)).

Clear 3D gaps where a canonical 3D form exists: **Barnsley Fern** and **Julia**.
**Pickover**'s z-coupling is degenerate and can be deepened. Icon and Gumowski-Mira
have no canonical 3D form — leave as 2D.

---

## Track A — Curated preset gallery

**Goal:** Each attractor ships with a handful of hand-picked "beautiful" parameter sets
(plus camera + palette + style), shown as a tappable list/grid. Tames the huge param
space and makes the app feel polished immediately. Add save/load of user favourites as a
follow-up.

### A1. Data model
- Add a `Preset` data class in `AttractorDefs.kt`:
  ```kotlin
  data class Preset(
      val name: String,
      val type: AttractorType,
      val params: List<Float>,
      val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,
      val zoom: Float = 1f,
      val palette: PaletteType = PaletteType.NEBULA,
      val renderStyle: RenderStyle = RenderStyle.STANDARD,
      val bgColor: BgColor = BgColor.BLACK,
  )
  ```
- Add `val presets: List<Preset>` either as a field on each `AttractorType` enum entry
  or as a top-level `val CURATED_PRESETS: Map<AttractorType, List<Preset>>`. Prefer the
  map — keeps the enum declaration readable. Seed 3–5 presets per attractor.

### A2. Sourcing the preset values
- Reuse the existing randomize path to explore, then freeze good values. The randomize
  logic is in `ChaoscopeViewModel.kt` (~line 351). Run it, screenshot, record the params.
- For each preset record: params, yaw/pitch (3D only), zoom, palette, style, bg.

### A3. UI surface
- In `AttractorScreen.kt`, add a "Presets" entry point (icon in the top bar or a tab in
  the existing controls sheet). Show a horizontally scrollable row or a grid of cards.
- Each card: a small thumbnail + name. Thumbnails can be generated on first run via the
  existing `getProjectedPoints` dot-preview path (cheap), cached to disk, or pre-rendered
  PNGs bundled in `assets/`. Start with text-only cards if thumbnails slow delivery.
- Tapping a preset applies it through a single new ViewModel method
  `applyPreset(preset: Preset)` that updates `UiState` (type, params, camera, palette,
  style, bg) and triggers a render.

### A4. User favourites (follow-up)
- Persist user-saved presets via the existing `Preferences.kt` (DataStore/SharedPrefs).
- Serialize `Preset` to JSON. Add "Save current as preset" + a "My presets" section.

**Files touched:** `AttractorDefs.kt`, `ChaoscopeViewModel.kt`, `ui/AttractorScreen.kt`,
`data/Preferences.kt` (favourites only). No native changes.

**Risk:** Low. Pure Kotlin/UI. Main effort is curating good-looking values.

---

## Track B — Depth shading for 3D attractors

**Goal:** Modulate point brightness/colour by camera-space depth (projected z) so the 3D
structure actually reads on a flat screen. Today `projectBatch` discards z entirely
([renderer.cpp:30](android/app/src/main/cpp/renderer.cpp#L30)).

### B1. Carry depth through projection
- Extend `projectBatch` to also compute camera-space depth
  `w = R[6]*x + R[7]*y + R[8]*z` (third row of the rotation matrix) and write it to a
  `float* ws` buffer alongside `us`/`vs`.
- Add a `ws` work buffer in `renderAttractor` (mirror `us`/`vs` allocation,
  [renderer.cpp:160](android/app/src/main/cpp/renderer.cpp#L160)).

### B2. Depth-weighted accumulation
- The current histogram is a single `uint32_t` count per pixel. To shade by depth, also
  accumulate a depth sum per pixel: add a parallel `std::vector<float> depthAccum(W*H)`
  and add `w` for each hit in `accumulateBatch`. After the loop, per-pixel mean depth =
  `depthAccum[i] / hist[i]`.
- Track global `wMin`/`wMax` during the bounds pass (extend the existing min/max scan at
  [renderer.cpp:185](android/app/src/main/cpp/renderer.cpp#L185)) to normalize depth to
  [0,1].

### B3. Apply depth in colorize
- In the colorize loop ([renderer.cpp:265](android/app/src/main/cpp/renderer.cpp#L265)),
  compute `depthNorm` for the pixel and apply a brightness factor, e.g.
  `density *= mix(nearBoost, farDim, depthNorm)` (near = brighter). Keep it subtle
  (e.g. 0.55–1.0) so it reads as depth, not a vignette.
- Optionally add a fog tint toward `bgColor` for far points.

### B4. Plumbing + control
- Add `float depthCue` (0 = off, 1 = full) to `RenderParams`
  ([renderer.h:13](android/app/src/main/cpp/renderer.h#L13)) and pass it through the JNI
  bridge (`chaoscope_jni.cpp`) and the Kotlin `RenderParams` builder.
- Gate the UI control on `attractorType.is3D` (only meaningful for 3D). Default on for 3D.
- Apply the same logic to `getProjectedPoints` so the live dot-preview also shows depth
  (optional — can ship preview without it first).

**Files touched:** `renderer.cpp`, `renderer.h`, `chaoscope_jni.cpp`,
`ChaoscopeEngine.kt`, `AttractorDefs.kt` (UiState field), `ui/AttractorScreen.kt`.

**Risk:** Medium. Touches the hot render loop — watch the extra `float` buffer's memory
(`W*H*4` bytes) and the per-pixel division. Benchmark on a mid-range device.

---

## Track C — Complete the 3D set

### C1. 3D Barnsley Fern
- The classic 3D fern uses 3×4 affine matrices (rotation in x/z plus the y growth axis).
  Replace the 2D affine maps in `ATTRACTOR_IFS`
  ([attractors.cpp:163](android/app/src/main/cpp/attractors.cpp#L163)) with 3D ones, or
  add a new `ATTRACTOR_IFS_3D` case to keep the 2D fern available.
- Set `is3D = true` for the (new or updated) entry in `AttractorDefs.kt`. Keep `width`
  and `lean` params; consider adding a `twist` param for the z-rotation.
- Verify auto-bounds and rotation behave (they already handle 3D generically).

### C2. Quaternion / 3D Julia
- A quaternion Julia set `q' = q² + c` is the standard 3D analogue. This is a
  **boundary/escape-time** set, not an orbit-density attractor — it does **not** fit the
  current histogram-of-iterated-points pipeline cleanly.
- Two options:
  - **(Recommended, smaller) Pseudo-3D Julia via orbit perturbation:** extend the current
    inverse-iteration map with a z term so it occupies a thin volume — cheap, fits the
    pipeline, but not a "true" quaternion Julia.
  - **(Larger) True quaternion Julia:** requires a separate ray-march / voxel sampler and
    a different render path. Scope as its own project; do not bundle into this track.
- Decide which based on appetite. Default to the pseudo-3D version for now; flag
  `is3D = true`.

### C3. Fix Pickover's flat z
- Current: `z' = sin(x)` only ([attractors.cpp:216](android/app/src/main/cpp/attractors.cpp#L216)).
- Replace with a fully-coupled 3D Pickover variant, e.g.
  `z' = sin(b·x) - cos(c·z)` (or another standard 3-term form) so z depends on the
  evolving state. Re-tune `defaultParams` so the default still looks good (the existing
  defaults were chosen for the old equation).
- No flag change (already `is3D = true`); update `description`/`paramHints` if behavior
  shifts noticeably.

**Files touched:** `attractors.cpp`, `attractors.h` (if adding new enum constants),
`AttractorDefs.kt`. Native enum ordinals must stay in sync with the Kotlin
`AttractorType` order — verify against `AttractorDefsTest.kt`.

**Risk:** Medium. New/changed math can diverge or produce empty histograms — the renderer
already has a blank-render retry path, but test each new attractor across its param range.
Update `AttractorDefsTest.kt`.

---

## Track D — Auto-rotate toggle (cheap motion win)

- Add an `autoRotate: Boolean` to `UiState` and a toggle in the UI.
- When on, advance `yaw` (and optionally `pitch`) on a timer/coroutine and re-render the
  dot-preview each frame; render the full histogram on stop. Reuse the existing
  `getProjectedPoints` preview path for smooth feedback.
- Gate on `is3D` (no-op for 2D attractors).

**Files touched:** `ChaoscopeViewModel.kt`, `ui/AttractorScreen.kt`. No native changes.

**Risk:** Low.

---

## Suggested sequencing

1. **Track A** (preset gallery) — biggest perceived-quality jump, no math/native risk. ✅
2. **Track B** (depth shading) — makes the 8 existing 3D attractors pay off visually. ✅
3. **Track C** (complete 3D) — directly closes the "make them all 3D" goal. ✅
4. **Track D** (auto-rotate) — drop in whenever; pairs naturally with B. ✅

---

## Track E — Bigger bets (future)

The marquee features that need new infrastructure. Ordered by likely value.

### E1. Parameter animation / morphing → looping video
- The headline feature. Interpolate between two parameter sets (and/or sweep the camera
  yaw) over N frames, render each frame, and encode to an MP4/GIF loop.
- **Needs a frame-capture pipeline:** drive `renderAttractor` per frame off the UI thread,
  feed frames to `MediaCodec` + `MediaMuxer` (H.264/MP4) or an animated-GIF/WebP encoder.
- Design questions: keyframe model (A→B param lerp, or a timeline of stops), frame count /
  fps / duration controls, resolution (preview vs export), progress + cancel UI, and where
  output lands (MediaStore, like PNG export).
- **Risk:** High — new encoder dependency, long-running background work, memory pressure if
  frames are buffered. Render time × frame count can be minutes; needs robust progress/cancel.

### E2. Wallpaper + export upgrades
- **Live/static wallpaper:** set the current render as the device wallpaper
  (`WallpaperManager`); optionally a `WallpaperService` that slow-auto-rotates (reuse Track D).
- **Export upgrades:** 4K resolution (mind the `W·H·(hist+depth+dens)` memory — ~256MB at 4K,
  so allocate carefully or tile), transparent-PNG (skip the bg fill, emit alpha), and a
  share-sheet "set as wallpaper" action.
- **Risk:** Medium — 4K memory is the main hazard; wallpaper APIs are straightforward.

### E3. True quaternion-Julia ray-marcher (the "large" C2 option)
- The current Julia is an inverse-iteration point cloud in a k=0 slice (Track C2). A *true*
  solid quaternion Julia needs a different render path: a distance-estimator ray-marcher
  (ideally a GLSL compute/fragment shader), separate from the histogram pipeline.
- **Risk:** High — essentially a second renderer (GPU). Only worth it if E1/E2 land first.

**Status (2026-05-22):** E1 ✅ DONE (video export, ping-pong, MediaCodec pipeline).
E2 ✅ DONE (4K, transparent PNG, set-as-wallpaper). E3 deferred — see below.

### E3. True quaternion-Julia ray-marcher — DEFERRED / SHOWCASE
- The current Julia (Track C2) is an inverse-iteration point cloud — a glowing fog cloud.
  A true ray-marched Julia is a sharp solid surface (glass-like, crystalline) — a completely
  different visual style that would feel inconsistent with the histogram-glow aesthetic of
  all other attractors.
- **Architecture:** GLSL ES 3.0 fragment shader + offscreen FBO → `glReadPixels` → `Bitmap`.
  Replaces the Julia render path only; all other attractors unchanged.
- **Math:** quaternion DE `0.5·r·log(r)/|dz|` + sphere tracing + normal estimation via
  central differences + Phong+AO shading.
- **Effort:** ~4 days. **Risk:** High (shader precision on mobile, GLSurfaceView threading).
- **Decision:** Shelved in favour of Track F (more attractors + UX polish) which delivers
  broader value with lower risk. Revisit when the attractor set is complete.

---

## Track F — Attractor expansion + UX polish (current focus)

Consistent-pipeline improvements that benefit all users immediately.

### F1. More attractors — ✅ DONE
Added 4 new 3-D attractors on the existing C++ histogram pipeline (12 → 16 total),
each with 3 curated presets (36 → 48). Ordinals 12–15 are appended after Pickover so
existing saved-preset palette/attractor ordinals stay stable. Equations were validated
in the Python host port (`prototype/`) before porting to confirm convergence and tune
defaults/cameras.
- **Halvorsen** (ord 12) — cyclically symmetric; interlocking torus-knot loops. `a, dt`
- **Burke-Shaw** (ord 13) — fast galaxy-like double-spiral. `s, v, dt`
- **Chen-Lee** (ord 14) — wing-like double-lobe butterfly. `a, b, c, dt` (small dt — diverges easily)
- **Sprott-B** (ord 15) — minimal two-term system; broad swirling disc. `a, b, dt`
Dadras was dropped — it already ships as "Chaotic Flow". Nosé-Hoover was evaluated but
dropped (too diffuse vs. the others).
**Files:** `attractors.h`, `attractors.cpp`, `AttractorDefs.kt` (enum + `CURATED_PRESETS`).

### F2. Preset thumbnail previews — ✅ DONE
Curated presets now show a 64dp rendered thumbnail (image + name) instead of a text
chip. Implemented via the "generate + cache" route (no bundle size cost): `PresetThumbnails`
renders each preset once at 128px through the existing native engine, holds an in-memory
`LruCache`, and persists PNGs under `cacheDir/preset_thumbs/`. Only the selected attractor's
presets are requested at a time, so cost is one-time and negligible.
**Files:** `PresetThumbnails.kt` (new), `ui/AttractorScreen.kt` (`PresetThumb` composable).

### F3. Palette additions
- **Spectrum** palette — full hue wheel (rainbow). 30-min addition to the LUT table.
- **Sunset**, **Ice**, **Neon** — 3 more tasteful presets to round out the palette row.

### F4. Social / sharing polish — ✅ DONE
- Auto-generated caption ("Lorenz attractor · Nebula palette · σ=10 ρ=28 … — made with
  Chaoscope") via `buildShareCaption` in `AttractorDefs.kt`; attached as `EXTRA_TEXT` on
  every PNG share intent (export Snackbar + recent-render chips).
- "Copy Caption" button in the Export tab copies the caption to the clipboard
  (`LocalClipboardManager`) with a confirmation Snackbar.
- Covered by unit tests in `AttractorDefsTest`.

**Suggested order:** F3 (palette additions, 30 min) → F1 (attractors, ~2 days) → F2 (thumbnails, 1 day) → F4 (sharing, 1 hr).

---

## Track G — Interaction polish (user feedback)

### G1. Central play/render button — ✅ DONE
A circular play FAB docked in the centre of the control tab bar runs the quick preview
render (`onRender` → `renderPreview`); it shows a spinner while rendering. The plain
"Render" button was removed from the Export tab, which now keeps only the HD / 4K
export-resolution renders. **Files:** `ui/AttractorScreen.kt`.

### G2. Live palette recolour without re-render — ✅ DONE
Changing the palette (or editing custom stops) no longer triggers a full histogram
render — it recolours the live dot preview instead. The dot cloud is now coloured by
camera-axis depth through a palette LUT:
- `getProjectedPointsDepth` (native) returns `(u,v,depth)` triples; `getPaletteLutARGB`
  exposes the palette as ARGB samples. Both reach Kotlin via new JNI methods
  (`nativeGetPointsDepth`, `nativePaletteLut`).
- The ViewModel keeps a `paletteLut` StateFlow, rebuilt on palette/custom-stop changes;
  `setPalette`/`saveCustomStops` now call `fetchDotPoints()` instead of `renderLookPreview()`.
- The preview Canvas buckets dots by depth-mapped LUT colour. Flat-depth attractors
  (2-D, or head-on views) fall back to the palette midpoint so dots stay visible.
**Files:** `renderer.cpp/.h`, `chaoscope_jni.cpp`, `ChaoscopeEngine.kt`,
`ChaoscopeViewModel.kt`, `ui/AttractorScreen.kt`.
