"""
Chaoscope — Instagram profile-photo exporter.

Reuses the launcher-icon geometry from icon_design.py (the real Lorenz orbit,
depth-shaded neon strokes + tracer head) and renders it at high resolution with
a little extra margin so it sits comfortably inside Instagram's circular crop.

Writes to prototype/out/insta/:
  * profile_icon.png        — square, ready to upload (IG crops to a circle)
  * profile_icon_round.png  — circle-masked preview (so you can see the crop)

Run:  python profile_icon.py [--size 1440] [--margin 0.12]
"""

from __future__ import annotations

import argparse
import os

from PIL import Image

import icon_design as ic

OUT_DIR = os.path.join(os.path.dirname(__file__), "out", "insta")


def build_icon(size: int, margin: float) -> Image.Image:
    """Render the launcher-icon art at `size`, inset by `margin` fraction.

    The inset is applied by temporarily shrinking the fit radius, so the whole
    image keeps a single radial background (no composited inner-square seam).
    """
    orig_fit = ic.FIT_RADIUS
    ic.FIT_RADIUS = orig_fit * (1.0 - 2.0 * max(0.0, margin))
    try:
        pts = ic.lorenz_orbit()
        xy, dep = ic.project(pts)
        xy = ic.fit_to_viewbox(xy)
        xy, dep = ic.resample(xy, dep, ic.RESAMPLE_DS)
        runs = ic.band_runs(xy, dep, ic.N_BANDS)
        head = xy[-1]
        return ic.render_preview(runs, head, size=size, ss=2)
    finally:
        ic.FIT_RADIUS = orig_fit


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Render IG profile photo from the app icon.")
    ap.add_argument("--size", type=int, default=1440,
                    help="Output edge in px (default 1440).")
    ap.add_argument("--margin", type=float, default=0.12,
                    help="Inset as a fraction of the edge (default 0.12).")
    args = ap.parse_args(argv)

    os.makedirs(OUT_DIR, exist_ok=True)
    img = build_icon(args.size, args.margin)

    sq = os.path.join(OUT_DIR, "profile_icon.png")
    img.convert("RGB").save(sq)
    print(f"-> {sq}  ({args.size}x{args.size})")

    rnd = os.path.join(OUT_DIR, "profile_icon_round.png")
    ic.circle_mask(img).save(rnd)
    print(f"-> {rnd}  (circle-crop preview)")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
