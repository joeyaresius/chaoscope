"""
Chaoscope — Instagram launch-asset exporter.

Renders a curated set of "hero" attractors (using the real density pipeline in
render.py / renderer.py / colormap.py) onto the app's near-black background and
writes post-ready images in the two formats Instagram actually uses:

  * square   1080x1080  (grid thumbnails)
  * portrait 1080x1350  (4:5 — takes the most vertical space in the feed)

Each render is the same log-density "Gas" image the app produces, composited
over a subtle radial #06060F background so the neon actually glows (on a white
feed card a transparent PNG looks washed out). An optional low-opacity
"CHAOSCOPE" wordmark is stamped bottom-centre so reposts carry the name.

Usage
-----
# render the whole launch batch (square + portrait, watermarked)
python insta_export.py

# just one post, square only, no watermark, quick preview density
python insta_export.py --only thomas_aurora --no-portrait --no-watermark --iters 2000000

# list the curated posts and exit
python insta_export.py --list

Outputs land in prototype/out/insta/.
"""

from __future__ import annotations

import argparse
import os
import sys

# The render engine lives in the sibling engine/ folder.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "engine"))

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from attractors import ATTRACTORS, iterate_attractor_batch
from renderer import Camera, build_density_image
from colormap import colorize, PALETTES

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "out", "insta")

# App branding background (matches launcher icon BG_OUTER / BG_INNER).
BG_OUTER = (0x06, 0x06, 0x0F)   # near-black edge
BG_INNER = (0x12, 0x0C, 0x2A)   # faint violet glow at centre

WATERMARK_TEXT = "CHAOSCOPE"
WATERMARK_ALPHA = 64            # 0-255; subtle but legible

# ---------------------------------------------------------------------------
# Curated launch posts.
#
# Each entry is one piece of feed content. yaw/pitch only matter for the 3-D
# systems (lorenz, rossler, aizawa, thomas, lorenz84); the 2-D maps ignore them.
# gamma < 1 lifts the faint outer wisps; iters drives density (detail vs. time).
# These combos reproduce the renders you already liked, plus palette variants
# so the opening grid reads as a set.
# ---------------------------------------------------------------------------

POSTS: list[dict] = [
    # ── Row 1: three hero stills, one attractor each ──────────────────────
    dict(name="thomas_aurora",   attractor="thomas",   palette="aurora",
         iters=12_000_000, gamma=0.85, yaw=28, pitch=18),
    dict(name="lorenz84_electric", attractor="lorenz84", palette="electric",
         iters=12_000_000, gamma=0.85, yaw=35, pitch=22),
    dict(name="clifford_nebula", attractor="clifford", palette="nebula",
         iters=10_000_000, gamma=0.90),

    # ── Row 2: motion-adjacent + palette range ────────────────────────────
    dict(name="lorenz_fire",     attractor="lorenz",   palette="fire",
         iters=12_000_000, gamma=0.80, yaw=24, pitch=16),
    dict(name="aizawa_aurora",   attractor="aizawa",   palette="aurora",
         iters=14_000_000, gamma=0.85, yaw=30, pitch=20),

    # ── Row 3: more variety, show the palette LUT range ───────────────────
    dict(name="thomas_matrix",   attractor="thomas",   palette="matrix",
         iters=12_000_000, gamma=0.85, yaw=28, pitch=18),
    dict(name="dejong_electric", attractor="peterdejong", palette="electric",
         iters=10_000_000, gamma=0.90),
    dict(name="rossler_nebula",  attractor="rossler",  palette="nebula",
         iters=12_000_000, gamma=0.85, yaw=40, pitch=12),
    dict(name="clifford_fire",   attractor="clifford", palette="fire",
         iters=10_000_000, gamma=0.90),
]


# ---------------------------------------------------------------------------
# Background
# ---------------------------------------------------------------------------

def radial_bg(w: int, h: int) -> Image.Image:
    """Subtle radial: faint violet centre fading to near-black at the edges."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    cx, cy = w / 2.0, h / 2.0
    r = np.hypot(xx - cx, yy - cy) / (max(w, h) * 0.62)
    t = np.clip(r, 0.0, 1.0)[..., None]
    inner = np.array(BG_INNER, dtype=np.float64)
    outer = np.array(BG_OUTER, dtype=np.float64)
    img = inner * (1.0 - t) + outer * t
    return Image.fromarray(img.astype(np.uint8)).convert("RGBA")


# ---------------------------------------------------------------------------
# Watermark
# ---------------------------------------------------------------------------

def _load_font(size: int) -> ImageFont.FreeTypeFont:
    for name in ("segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def stamp_watermark(img: Image.Image) -> None:
    """Draw a low-opacity letter-spaced wordmark bottom-centre, in place."""
    w, h = img.size
    font = _load_font(max(14, w // 38))
    text = " ".join(WATERMARK_TEXT)          # letter-spacing via spaces
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (w - tw) / 2 - bbox[0]
    y = h - th - bbox[1] - int(h * 0.045)
    d.text((x, y), text, font=font, fill=(220, 230, 255, WATERMARK_ALPHA))
    img.alpha_composite(ov)


# ---------------------------------------------------------------------------
# Render one post -> a square RGBA glow image
# ---------------------------------------------------------------------------

def render_glow(post: dict, size: int) -> Image.Image:
    """Render the attractor as a transparent neon glow (no background)."""
    key = post["attractor"].lower().replace("_", "").replace("-", "")
    attractor = ATTRACTORS[key]

    xs, ys, zs = iterate_attractor_batch(attractor, n_iter=post["iters"])
    cam = Camera(yaw=post.get("yaw", 0.0), pitch=post.get("pitch", 0.0),
                 roll=post.get("roll", 0.0), zoom=post.get("zoom", 1.0))
    u, v = cam.project(xs, ys, zs)

    density, _ = build_density_image(u, v, width=size, height=size,
                                     gamma=post.get("gamma", 1.0),
                                     tone_map="log")
    rgba = colorize(density, palette=post["palette"], alpha=True)
    # Density images are built with origin at the bottom (v increases upward);
    # flip so the saved PNG is the right way up.
    return Image.fromarray(rgba).transpose(Image.FLIP_TOP_BOTTOM)


def compose_square(glow: Image.Image, size: int) -> Image.Image:
    canvas = radial_bg(size, size)
    canvas.alpha_composite(glow)
    return canvas


def compose_portrait(glow: Image.Image, w: int, h: int) -> Image.Image:
    """Composite the glow, centred with a small inset, onto a 4:5 canvas."""
    canvas = radial_bg(w, h)
    inset = int(w * 0.04)
    side = w - 2 * inset
    art = glow.resize((side, side), Image.LANCZOS)
    canvas.alpha_composite(art, (inset, (h - side) // 2))
    return canvas


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Render Instagram launch assets.")
    ap.add_argument("--only", metavar="NAME",
                    help="Render just one post by its name (see --list).")
    ap.add_argument("--size", type=int, default=1080,
                    help="Square edge in px (default 1080).")
    ap.add_argument("--iters", type=int, default=None,
                    help="Override iteration count for all posts "
                         "(use a small value like 2000000 for quick previews).")
    ap.add_argument("--no-portrait", action="store_true",
                    help="Skip the 1080x1350 portrait variant.")
    ap.add_argument("--no-watermark", action="store_true",
                    help="Skip the CHAOSCOPE wordmark.")
    ap.add_argument("--list", action="store_true",
                    help="List curated posts and exit.")
    args = ap.parse_args(argv)

    if args.list:
        print(f"{'name':<20}{'attractor':<14}{'palette':<10}{'iters':>12}")
        for p in POSTS:
            print(f"{p['name']:<20}{p['attractor']:<14}{p['palette']:<10}"
                  f"{p['iters']:>12,}")
        print("\nPalettes available:", ", ".join(PALETTES))
        return 0

    posts = POSTS
    if args.only:
        posts = [p for p in POSTS if p["name"] == args.only]
        if not posts:
            print(f"No post named '{args.only}'. Try --list.")
            return 1

    os.makedirs(OUT_DIR, exist_ok=True)
    pw, ph = args.size, round(args.size * 1350 / 1080)   # 4:5 portrait

    for p in posts:
        post = dict(p)
        if args.iters is not None:
            post["iters"] = args.iters
        print(f"[{post['name']}] {post['attractor']} / {post['palette']} "
              f"@ {post['iters']:,} iters ...", flush=True)

        glow = render_glow(post, args.size)

        square = compose_square(glow, args.size)
        if not args.no_watermark:
            stamp_watermark(square)
        sq_path = os.path.join(OUT_DIR, f"{post['name']}_1x1.png")
        square.convert("RGB").save(sq_path)
        print(f"   -> {sq_path}")

        if not args.no_portrait:
            portrait = compose_portrait(glow, pw, ph)
            if not args.no_watermark:
                stamp_watermark(portrait)
            pt_path = os.path.join(OUT_DIR, f"{post['name']}_4x5.png")
            portrait.convert("RGB").save(pt_path)
            print(f"   -> {pt_path}")

    print(f"\nDone. {len(posts)} post(s) written to {OUT_DIR}")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
