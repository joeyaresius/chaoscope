"""
Chaoscope Clone - Main Runner
CLI entry-point that wires together all Phase 1-4 modules.

Usage examples
--------------
# Quick preview (1 M iterations, 800x800)
python render.py --attractor clifford --iters 1000000 --size 800

# High-quality render saved to PNG
python render.py --attractor lorenz --iters 10000000 --size 2048 \
    --palette fire --gamma 0.8 --yaw 30 --pitch 20 --output out/lorenz.png

# List available attractors and palettes
python render.py --list
"""

from __future__ import annotations

import argparse
import os
import sys
import time

import numpy as np

# ── optional GUI (matplotlib) – only imported when --show is requested ──────
_MPL_AVAILABLE = True
try:
    import matplotlib  # noqa: F401
except ImportError:
    _MPL_AVAILABLE = False

# ── optional image save (Pillow) ─────────────────────────────────────────────
_PIL_AVAILABLE = True
try:
    from PIL import Image  # noqa: F401
except ImportError:
    _PIL_AVAILABLE = False

from attractors import ATTRACTORS, iterate_attractor_batch, iterate_attractor
from renderer import Camera, build_density_image
from colormap import PALETTES, colorize


# ---------------------------------------------------------------------------
# CLI argument parser
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="render",
        description="Chaoscope prototype – render strange attractors as density images.",
    )

    p.add_argument(
        "--attractor", "-a",
        default="clifford",
        metavar="NAME",
        help="Attractor to render (default: clifford).",
    )
    p.add_argument(
        "--iters", "-n",
        type=int,
        default=2_000_000,
        metavar="N",
        help="Number of iterations (default: 2 000 000).",
    )
    p.add_argument(
        "--size", "-s",
        type=int,
        default=1024,
        metavar="PX",
        help="Canvas size in pixels, square (default: 1024).",
    )
    p.add_argument(
        "--palette", "-p",
        default="nebula",
        metavar="NAME",
        help="Colour palette (default: nebula).",
    )
    p.add_argument(
        "--gamma", "-g",
        type=float,
        default=1.0,
        metavar="G",
        help="Gamma correction on density (default: 1.0).",
    )
    p.add_argument(
        "--tone", "-t",
        choices=["log", "linear"],
        default="log",
        help="Tone mapping mode: log (Chaoscope-style) or linear (default: log).",
    )

    # Camera / projection controls
    cam = p.add_argument_group("Camera (for 3-D attractors)")
    cam.add_argument("--yaw",   type=float, default=0.0, help="Camera yaw   (degrees, default 0).")
    cam.add_argument("--pitch", type=float, default=0.0, help="Camera pitch (degrees, default 0).")
    cam.add_argument("--roll",  type=float, default=0.0, help="Camera roll  (degrees, default 0).")
    cam.add_argument("--zoom",  type=float, default=1.0, help="Camera zoom  (default 1.0).")

    # Output
    p.add_argument(
        "--output", "-o",
        default=None,
        metavar="PATH",
        help="Save rendered image to this PNG path.",
    )
    p.add_argument(
        "--show",
        action="store_true",
        help="Display the image with matplotlib after rendering.",
    )
    p.add_argument(
        "--batch-size",
        type=int,
        default=10_000,
        metavar="B",
        help="Batch size for vectorised iteration (default: 10 000).",
    )
    p.add_argument(
        "--list",
        action="store_true",
        help="List available attractors and palettes, then exit.",
    )

    return p


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(argv = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.list:
        print("Attractors:")
        for name, attr in ATTRACTORS.items():
            print(f"  {name:<20} {attr.name}")
        print("\nPalettes:")
        for name in PALETTES:
            print(f"  {name}")
        return 0

    # ── validate attractor ──────────────────────────────────────────────────
    key = args.attractor.lower().replace("_", "").replace("-", "")
    if key not in ATTRACTORS:
        print(f"ERROR: Unknown attractor '{args.attractor}'.", file=sys.stderr)
        print(f"Run with --list to see available attractors.", file=sys.stderr)
        return 1
    attractor = ATTRACTORS[key]

    # ── validate palette ────────────────────────────────────────────────────
    if args.palette.lower() not in PALETTES:
        print(f"ERROR: Unknown palette '{args.palette}'.", file=sys.stderr)
        print(f"Run with --list to see available palettes.", file=sys.stderr)
        return 1

    print(f"Attractor : {attractor.name}")
    print(f"Iterations: {args.iters:,}")
    print(f"Canvas    : {args.size}×{args.size} px")
    print(f"Palette   : {args.palette}  |  Tone-map : {args.tone}  |  Gamma: {args.gamma}")

    # ── iterate ─────────────────────────────────────────────────────────────
    t0 = time.perf_counter()
    print("Iterating...", end=" ", flush=True)

    xs, ys, zs = iterate_attractor_batch(
        attractor,
        n_iter=args.iters,
        batch_size=args.batch_size,
    )

    t1 = time.perf_counter()
    print(f"{len(xs):,} points in {t1-t0:.2f}s  ({len(xs)/(t1-t0)/1e6:.1f} Mpts/s)")

    # ── project ─────────────────────────────────────────────────────────────
    camera = Camera(
        yaw=args.yaw,
        pitch=args.pitch,
        roll=args.roll,
        zoom=args.zoom,
    )
    u, v = camera.project(xs, ys, zs)

    # ── density + tone-map ──────────────────────────────────────────────────
    print("Building histogram...", end=" ", flush=True)
    t2 = time.perf_counter()
    density, canvas = build_density_image(
        u, v,
        width=args.size,
        height=args.size,
        gamma=args.gamma,
        tone_map=args.tone,
    )
    t3 = time.perf_counter()
    print(f"done in {t3-t2:.2f}s  (max hits/px: {canvas.counts.max():,})")

    # ── colorize ────────────────────────────────────────────────────────────
    rgba = colorize(density, palette=args.palette, alpha=True)

    # ── save PNG ─────────────────────────────────────────────────────────────
    if args.output:
        if not _PIL_AVAILABLE:
            print("WARNING: Pillow not installed; cannot save PNG.  "
                  "Install it with:  pip install Pillow", file=sys.stderr)
        else:
            from PIL import Image as _Image
            os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
            img = _Image.fromarray(rgba, mode="RGBA")
            img.save(args.output)
            print(f"Saved → {args.output}")

    # ── show ─────────────────────────────────────────────────────────────────
    if args.show:
        if not _MPL_AVAILABLE:
            print("WARNING: matplotlib not installed; cannot display image.  "
                  "Install it with:  pip install matplotlib", file=sys.stderr)
        else:
            import matplotlib.pyplot as plt
            fig, ax = plt.subplots(figsize=(8, 8), facecolor="black")
            ax.imshow(rgba, origin="lower")
            ax.axis("off")
            ax.set_title(
                f"{attractor.name} | {args.iters/1e6:.1f}M iters | {args.palette}",
                color="white",
                fontsize=10,
            )
            plt.tight_layout(pad=0.2)
            plt.show()

    total = time.perf_counter() - t0
    print(f"Total time: {total:.2f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
