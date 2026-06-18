# Chaoscope — Python prototype & asset tooling

This folder is **not** part of the shipping apps. It holds two things:

1. **`engine/`** — the original NumPy proof-of-concept: the attractor math + histogram
   render pipeline that was validated here before being ported to the C++ engine in
   [`core/`](../core/). It's still handy for fast visual QA without a device build.
2. **`assets/`** — scripts that generate the launcher icon and the store / social-media
   marketing assets (Instagram posts, reels, turntables, profile icon, video outro).

Plus **`qa/`** for one-off analysis scripts.

```
prototype/
├── engine/
│   ├── attractors.py     ← attractor equations (vectorised NumPy)
│   ├── renderer.py       ← density histogram + logarithmic tone-mapping + camera
│   ├── colormap.py       ← gradient / palette system
│   ├── render.py         ← CLI runner that wires the above together
│   └── requirements.txt  ← numpy, pillow, matplotlib
├── assets/
│   ├── icon_design.py    ← launcher icon (real Lorenz orbit); --emit writes the Android drawable XMLs
│   ├── profile_icon.py   ← Instagram profile photo (reuses icon_design geometry)
│   ├── marketing.py      ← growth-loop helper: CHS1 preset codes + post captions (mirrors PresetSerializer.kt)
│   ├── insta_export.py   ← curated "hero" attractor posts (1080² + 1080×1350) + per-post captions
│   ├── video_export.py   ← Instagram reels / orbit-trace "build sweep" MP4s (+ --caption)
│   └── outro_mock.py     ← QA mock of the in-app video outro frame
├── qa/
│   └── orbit_alpha_check.py  ← orbit-trace per-pixel density distribution check
└── out/                  ← generated images / MP4s (gitignored — regenerable)
```

> `out/` is **gitignored**. Everything in it is produced by the scripts above, so it's
> never committed. All scripts write there via paths relative to their own location, so
> output lands in `prototype/out/` regardless of your current directory.

---

## Setup

```bash
pip install -r engine/requirements.txt   # numpy, pillow, matplotlib
```

Some `assets/` scripts also shell out to **ffmpeg** for MP4 encoding — install it and
make sure it's on your `PATH`.

---

## `engine/` — the render POC

Run the CLI from inside `engine/` (its imports are same-folder):

```bash
cd engine

# Quick preview with matplotlib
python render.py --show

# Lorenz 3-D with camera rotation, saved to PNG
python render.py -a lorenz -n 5000000 -s 1024 -p fire --yaw 30 --pitch 20 -o ../out/lorenz.png

# List every attractor and palette
python render.py --list
```

### Attractors (engine POC)

The Python engine implements a subset of the full C++ engine — enough to prove the
pipeline:

`clifford` · `peterdejong` · `gumowskimira` · `lorenz` · `rossler` · `aizawa` ·
`thomas` · `icon` · `lorenz84` · `henon`

> The shipping apps support 15+ attractors; the authoritative list and parameter
> layouts live in [`core/attractors.h`](../core/attractors.h).

### Palettes

`nebula` · `fire` · `electric` · `aurora` · `matrix` · `greyscale` · `greyscale_inv`

### Key CLI flags

| Flag | Default | Description |
|---|---|---|
| `-a, --attractor` | `clifford` | Attractor name |
| `-n, --iters` | `2000000` | Iteration count |
| `-s, --size` | `1024` | Square canvas size (px) |
| `-p, --palette` | `nebula` | Colour palette |
| `--gamma` | `1.0` | Gamma applied to density |
| `--tone` | `log` | Tone-mapping: `log` or `linear` |
| `--yaw/--pitch/--roll` | `0` | Camera rotation for 3-D attractors (degrees) |
| `--zoom` | `1.0` | Camera zoom |
| `-o, --output` | — | PNG output path |
| `--show` | — | Display with matplotlib |

(Run `python render.py --help` for the complete list.)

### Pipeline

```
attractor formula → iterate_attractor_batch()  → xs, ys, zs
        → Camera.project()                      → u, v
        → HistogramCanvas.accumulate()          → counts[H, W]
        → log_density_map()                      → density[H, W] ∈ [0, 1]
        → Palette.apply()                        → rgba[H, W, 4]
        → PIL.Image / matplotlib
```

This mirrors the C++ engine in [`core/`](../core/) — see [core/README.md](../core/README.md).

---

## `assets/` — icon & marketing generators

These import the engine from `../engine` automatically (via a small `sys.path` shim),
so you can run them from inside `assets/`:

```bash
cd assets

python icon_design.py            # writes launcher-icon previews to ../out/
python icon_design.py --emit     # also writes the Android drawable XMLs into android/.../res/drawable/
python profile_icon.py           # Instagram profile photo → ../out/insta/
python insta_export.py           # hero-attractor posts + captions → ../out/insta/
python insta_export.py --captions-only   # just (re)write the .txt captions, no render (fast)
python video_export.py -a lorenz --caption   # orbit-trace reel MP4 + caption → ../out/insta/  (needs ffmpeg)
python outro_mock.py             # video-outro mock → ../out/  (needs icon_design output first)
```

### The Instagram growth loop

`insta_export.py` and `video_export.py` write a post-ready `<name>.txt` caption next to
each image/reel (and an aggregated `captions.md`). Every caption carries a hook, the
look description, the Play Store install link, and a **`CHS1:` preset code** — the same
shareable code the app generates. A viewer copies the code, opens Chaoscope, and the
in-app clipboard detector offers to paste it, dropping them onto the exact look from the
post. Pretty picture → install → recreate → share again.

`marketing.py` mirrors `android/.../PresetSerializer.kt` byte-for-byte to emit those
codes. **If the Android preset format or enum names change, update `marketing.py` to
match** (the app would bump the code prefix to `CHS2:`).

> `icon_design.py --emit` is the **only** script that writes outside `prototype/` — it
> regenerates `android/app/src/main/res/drawable/ic_launcher*.xml`. Don't hand-edit
> those XMLs; change `icon_design.py` and re-emit.

---

## `qa/` — analysis scripts

```bash
python qa/orbit_alpha_check.py   # compares orbit-trace density normalisation bases
```

Standalone (no engine import) — used to validate the in-app `computeOrbitDotAlpha`
normalisation against the Kotlin pipeline.
