# Chaoscope — UI & Retention Plan

Goal: convert one-session players into returning creators. Strategies agreed June 2026.
Numbering matches the original analysis discussion.

Ground rules that apply throughout:
- Explicit render only — nothing here may auto-fire a render; the play FAB stays the
  single render trigger. (Daily-attractor *validation* renders happen off-screen at
  thumbnail size, which is the same precedent `PresetThumbnails` already sets.)
- Palette/look changes keep recolouring the dot preview via the LUT, never a render.
- Keep `WARMUP_STEPS >= 1000` for any background/validation render.

---

## Phase 1 — Investment & safety  ✅ implemented June 2026
*The user's own work becomes visible, beautiful, and safe to experiment around.*

Implementation notes: gallery reuses the old recents DataStore key (legacy URI-only
lines load as view/share-only entries); entries are `uri \t timestamp \t presetString`
capped at 50. The stored preset is captured when a render *completes*
(`lastRenderedPreset`), not at export time, so slider tweaks after a render can't
mismatch the saved image. Undo is single-level (`undoState` in the ViewModel),
surfaced as a snackbar with an Undo action after Surprise Me / Shuffle / any
preset apply.

### 1. Creations gallery (reopen-able renders)
Today `recentExports` (Preferences.kt) stores only PNG URIs shown as a small row in
the Export tab. Replace with a real gallery of *editable* creations.

- Extend the recents store to a list of entries: `uri | timestamp | serialized preset`
  (reuse `presetToString` / `parsePreset` from PresetSerializer.kt — versioned,
  enum-by-name, already corruption-tolerant).
- On every successful PNG/HD/4K export, capture the full current state as a preset
  string alongside the URI. Video exports keep the existing flow.
- UI: promote from a row to a "Gallery" grid (2–3 columns). Entry actions:
  **Open** (restore full state into the editor — the key feature), **View** (existing
  ACTION_VIEW), **Share**, **Delete**.
  Placement: its own panel surface — either a 5th area reached from the Export tab
  header or a full-screen dialog launched from a top-bar icon. Decide during
  implementation; full-screen grid is likely better than cramming into the panel.
- Cap stored entries (e.g. 50) FIFO; thumbnails come from the exported PNG itself
  (decode scaled), no extra rendering.
- Migration: existing URI-only entries load with a null preset → View/Share only.

### 2. User preset thumbnails
Curated presets render thumbs via `PresetThumbnails.get()`; user presets are plain
text chips (AttractorScreen.kt ~line 927).

- Swap the `AssistChip` row for the same `PresetThumb` card used by curated presets —
  `PresetThumbnails.get(context, preset)` already accepts any `Preset`.
- Keep the name label under the thumb and the delete affordance (long-press or a
  small ✕ overlay).
- Cache-invalidation is already handled: the cache key hashes every visual field.

### 8. Undo after randomize
"Surprise Me" / "Shuffle Params" destroy the current state with no recovery.

- Before either randomize action, snapshot the full visual state (attractor, params,
  camera, palette, style, bg) — same field set as a `Preset`.
- Single-level is enough: show a snackbar "Shuffled — Undo" (3–4 s). Undo restores
  the snapshot into the dot preview (no render).
- Also snapshot before applying a curated/user preset — same accident, same fix.

---

## Phase 2 — Return triggers
*A reason to open the app tomorrow.*
*Item #3 implemented June 2026 (DailyAttractor.kt: date-seeded curated pick,
±5 % param jitter, thumbnail-validated with seed+attempt fallback chain; card at
the top of the Shape tab). #4 live wallpaper still pending.*

### 3. Attractor of the Day — with blank-screen guard
A deterministic daily preset, no backend: seed an RNG with `yyyyMMdd`.

Blank/degenerate guard (the "nothing of the day" risk):
1. **Pick from safe stock, jitter mildly.** Daily = random *curated* preset for a
   random attractor, then jitter each param by ≤ ±5 % of its slider range, and
   randomize palette / render style / camera freely (look changes can't blank).
   Curated presets are known-good, so small jitter rarely escapes the basin.
2. **Validate before showing.** Render at thumbnail size (128 px, low iterations,
   warmup ≥ 1000) and run the existing blank-render check (same logic as the
   ViewModel's blank-render retry / PresetThumbnails' retry). This happens once per
   day, off the UI thread, and is cached.
3. **Deterministic fallback chain.** If blank: re-seed with `yyyyMMdd + attempt` and
   retry (max ~5); final fallback = the un-jittered curated preset itself, which
   cannot be blank. Every device converges on the same result for the same day.
- UI: a card at the top of the Shape tab ("Today's discovery — Burke-Shaw #142")
  showing the validated thumb; tapping applies the preset to the dot preview.
  Optional later: home-screen widget showing the same daily image.

### 4. Live wallpaper  ✅ implemented June 2026
The strongest "app is present daily" mechanic available to this product.

*Implementation notes: `ChaoscopeWallpaperService` draws the CPU dot-preview
pipeline (30 k points, palette LUT buckets, ThemeBackgroundRenderer behind) on a
background HandlerThread at ~12 fps, visible-only; battery saver → static frame
rechecked every 15 s. 3-D attractors spin via yaw; 2-D ones spin in-plane via
roll (yaw would collapse them edge-on). Source (current creation / daily
attractor) and speed (0.5–8 °/s, default 2) live in DataStore, edited by
`WallpaperSettingsActivity` (wired as android:settingsActivity in
res/xml/wallpaper.xml). Look re-resolves on every visibility gain, so editor
changes and the daily rollover apply without re-setting the wallpaper. Entry:
"Set as Live Wallpaper" button in the Export tab → ACTION_CHANGE_LIVE_WALLPAPER
with chooser fallback.*

- Android `WallpaperService` + GLES surface reusing the GPU dot-preview pipeline
  (gpu_renderer.cpp) — *not* full renders. Slowly increment yaw (e.g. 360° over
  2–5 min) on the user's chosen preset or the daily attractor.
- Battery discipline: render only while visible, target ≤ 30 fps (yaw moves slowly
  so even 10–15 fps reads as smooth), drop to a static frame on power-saver.
- Entry points: "Set as Live Wallpaper" button next to the existing static wallpaper
  button in the Export tab; settings screen in the wallpaper picker chooses
  preset source (current / daily) and rotation speed.
- This is the largest item in the plan (new service, GL lifecycle, its own settings
  activity). Schedule as its own milestone after Phase 1 ships.

---

## Phase 3 — Social loop  ✅ implemented June 2026
*Notes: `presetToCode`/`presetFromCode` in PresetSerializer.kt (java.util.Base64
url-safe, name stripped); every share caption now ends with "Recreate it in
Chaoscope: CHS1:…". Clipboard detection runs on window-focus gain (Android 10+
clipboard rule), offers each unique code once, and skips the user's own current
look. Paste-dialog fallback via the Import button beside "+ Save current".*

### 5. Shareable preset codes — import mechanics
Answer to "how would that be imported?":

- **Format:** `CHS1:<base64url(presetToString minus name)>` — versioned prefix,
  ~80–120 chars. Reuses PresetSerializer; `CHS2:` later if fields change.
- **Export side:** append the code to the existing share caption (buildShareCaption)
  so every shared image carries its own recipe.
- **Import side, two mechanisms:**
  1. **Clipboard offer (primary).** On app resume, peek at the clipboard; if it
     contains a `CHS1:` token, show a snackbar/card: "Preset found in clipboard —
     Apply?". One tap applies to the dot preview. Friend's flow: long-press the
     caption in WhatsApp → copy → open Chaoscope → tap Apply. Zero new screens.
     (Android 12+ shows a paste notice on clipboard read — acceptable, read only
     on resume, never in a loop.)
  2. **Paste field (fallback).** An "Import" text button beside "+ Save current" in
     My Presets opens a dialog with a paste box, for codes arriving via channels
     where the clipboard offer was missed.
  - **Later, optional:** `https://` App Link (needs a domain) so the code is tappable;
    custom `chaoscope://` scheme is not tappable from plain-text messages, so it
    adds little — skip it.
- Robustness: parse failures show "Invalid or newer-version code" — `parsePreset`
  already returns null on anything malformed.

---

## Phase 4 — Discovery & polish
*Items #7, #9, #10, #11 implemented June 2026 (the "Phase 2" batch from the
sequencing table). Notes: settings gear sits next to About on the canvas top bar
and opens a ModalBottomSheet (Render Detail, Preview Density, Language, Replay
tutorial); the Export tab kept only export actions. Camera tab is dimmed (not
disabled) for 2-D attractors and keeps a working Zoom slider. The launch splash
hides tribute/coffee/feedback/credits (About keeps them) and gained a tutorial
button that force-shows the tutorial. FAB pulse is driven by a persisted
`has_ever_rendered` flag set on first completed render.*

### 6. Attractor grid picker with thumbnails  ✅ implemented June 2026
17 text chips in a LazyRow hide the catalogue's variety.

- Replace the chip row with a 2-row horizontally-scrolling grid of small rendered
  thumbs (attractor's first curated preset via `PresetThumbnails`), name underneath,
  selected state = accent border.
- Keep it inside the Shape tab at the same position; update the tutorial's
  AttractorRow anchor (Tutorial.kt) to the new layout.
- *Implementation notes: LazyHorizontalGrid (2 rows, 180 dp) with `AttractorCell`;
  thumbnails are the attractor's first curated preset (defaults as fallback) and
  disk-cache after the first scroll. Tutorial anchor moved to the grid container;
  tutorial_pick_body reworded ("thumbnail gallery", count dropped — it said 15,
  there are 17) in all 5 locales.*

### 7. FAB pulse until first render
- Persist `hasEverRendered` in Preferences. Until true, run an infinite gentle
  scale/glow pulse on the render FAB (infiniteRepeatable, ~1.6 s period).
  Stops permanently after the first completed render. No auto-render.

### 9. Settings menu (answer: yes — a config sheet from the top bar)
The Export tab currently mixes export actions with set-once performance settings.

- Add a **gear icon** next to the existing About (ℹ) icon in the canvas top-right.
- It opens a settings sheet/dialog containing: **Render Detail**, **Preview
  Density** (moved out of the Export tab), **Language** (currently splash-only —
  duplicating it here means users can find it after first launch), and **Replay
  tutorial**.
- Export tab keeps only: HD/4K/PNG, wallpaper, caption, gallery entry point,
  animation export.

### 10. Camera tab for 2D attractors
- When `!attractorType.is3D`, render the Camera `PanelTab` dimmed/disabled with the
  existing explanation as a tooltip-style hint, **and** keep Zoom available there so
  the tab is never a dead end (zoom applies to 2D too).

### 11. Slim first-launch splash
- First launch shows only: logo/render, tagline, language row, **Explore Attractors**
  (primary), **Show tutorial** (secondary).
- Coffee, feedback, tribute, credits move to the About screen permanently; returning-
  visit splash (opened via About → splash, or if kept on launch) may show them.

### 12. HD/4K render progress  ✅ implemented June 2026
- Engine change: native render loop updates an atomic iteration counter exposed via
  JNI (poll from Kotlin every ~200 ms) — no callback plumbing needed.
- UI: replace the indeterminate spinner with a thin determinate LinearProgressIndicator
  (canvas top edge or under the FAB) + percentage; keep the cancel button.
- Applies to HD/4K and video frame renders; preview renders stay spinner-only
  (too fast to matter).
- *Implementation notes: counters live in renderer.cpp and only track renders
  ≥ 1024 px wide, so concurrent thumbnail/preview renders can't pollute the bar.
  GPU renders are a single compute dispatch and never report — the UI keeps the
  spinner there (progress stays -1). Bar + percentage sit along the canvas top
  edge, clear of the spinner/cancel icons. Video frames render at 768 px so they
  intentionally don't track (the export already has frame-count progress).*

### Post-test feedback round (June 2026)  ✅
- Gallery delete now asks "remove from gallery only" vs "delete file from device
  too" (MediaStore delete; SecurityException from older-install files is
  swallowed — entry removed, file stays).
- Videos joined the gallery: GalleryEntry gained a `kind` field ("vid"/"img"),
  exportVideo adds an entry with the launch-state preset, thumbs via
  contentResolver.loadThumbnail (API 29+) / MediaMetadataRetriever fallback,
  ▶ badge, mime-aware view/share.
- Launch splash regained a one-line tribute (`splash_tribute_short`); full card
  stays About-only.
- Fixed About ✕ never responding: the scrollable column was composed after the
  button and swallowed its taps — button now composes last (on top).
- Tutorial button removed from About (settings sheet covers replay); kept on the
  launch splash.

---

## Sequencing & effort

| Order | Items | Why first | Rough size |
|-------|-------|-----------|------------|
| 1 | #1 gallery, #2 preset thumbs, #8 undo | Builds investment; all reuse existing serializer/thumbnail code | M + S + S |
| 2 | #9 settings menu, #10 camera tab, #11 splash slim, #7 FAB pulse | Cheap polish, clears Export tab before gallery entry point lands | S each |
| 3 | #3 daily attractor, #5 preset codes | Return trigger + social loop; both depend on serializer patterns from step 1 | M + M |
| 4 | #12 render progress | Touches C++ engine; isolated | M |
| 5 | #4 live wallpaper | Largest, own milestone | L |
| later | #6 attractor grid | Nice-to-have; revisit after thumbnails infra is exercised by #2 | M |

All strings go through `strings.xml` + the four locale files (es, fr, pt-BR, zh-CN);
remember the bundle-language-split constraint for any new user-facing copy.
