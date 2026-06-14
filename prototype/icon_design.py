"""
Chaoscope launcher-icon generator (v2 — "real orbit" icon).

Integrates the actual Lorenz system, projects it with a slight 3-D rotation,
resamples the orbit by arc length, splits it into depth bands, and emits:

  * prototype/out/icon_v2_preview.png        — square icon preview (1080 px)
  * prototype/out/icon_v2_preview_round.png  — circle-masked preview
  * prototype/out/icon_v2_small.png          — 48/96 px strip (legibility check)
  * android res drawables (--emit)           — ic_launcher.xml / ic_launcher_round.xml

The PNG preview draws exactly the same layered strokes the VectorDrawable
uses (no blur tricks), so what you see here is what Android renders.
"""

from __future__ import annotations

import os
import numpy as np
from PIL import Image, ImageDraw

OUT_DIR = os.path.join(os.path.dirname(__file__), "out")
RES_DIR = os.path.normpath(os.path.join(
    os.path.dirname(__file__), "..", "android", "app", "src", "main", "res", "drawable"))

VIEW = 108.0          # viewBox size (Android adaptive-icon canvas)
CX = CY = VIEW / 2.0
FIT_RADIUS = 45.0     # keep artwork inside this radius (round-mask safe)
RESAMPLE_DS = 1.5     # arc-length spacing of polyline points, viewBox units
N_BANDS = 6

# Stroke layers per depth band: (width, alpha) — halo -> mid -> core
LAYERS = [(3.4, 0x2A), (1.8, 0x6E), (0.85, 0xFF)]

# Depth gradient, far -> near (matches app's cyan/violet branding)
BAND_COLORS = [
    "#3B1E8F",  # deep violet (far)
    "#5B2FD1",
    "#3D5AFE",
    "#2196F3",
    "#00B8D4",
    "#4FC3F7",  # bright cyan (near)
]

BG_INNER = "#181040"   # radial gradient centre
BG_OUTER = "#06060F"   # radial gradient edge


# ---------------------------------------------------------------------------
# Orbit
# ---------------------------------------------------------------------------

def lorenz_orbit(n_steps: int = 3800, dt: float = 0.004,
                 warmup: int = 3000) -> np.ndarray:
    """Integrate Lorenz with RK4; returns (n, 3) array after warmup."""
    sigma, rho, beta = 10.0, 28.0, 8.0 / 3.0

    def deriv(p):
        x, y, z = p
        return np.array([sigma * (y - x), x * (rho - z) - y, x * y - beta * z])

    p = np.array([1.0, 1.0, 20.0])
    for _ in range(warmup):
        k1 = deriv(p); k2 = deriv(p + 0.5 * dt * k1)
        k3 = deriv(p + 0.5 * dt * k2); k4 = deriv(p + dt * k3)
        p = p + dt / 6.0 * (k1 + 2 * k2 + 2 * k3 + k4)

    pts = np.empty((n_steps, 3))
    for i in range(n_steps):
        k1 = deriv(p); k2 = deriv(p + 0.5 * dt * k1)
        k3 = deriv(p + 0.5 * dt * k2); k4 = deriv(p + dt * k3)
        p = p + dt / 6.0 * (k1 + 2 * k2 + 2 * k3 + k4)
        pts[i] = p
    return pts


def project(pts: np.ndarray, yaw_deg: float = 10.0,
            tilt_deg: float = 6.0) -> tuple[np.ndarray, np.ndarray]:
    """
    Rotate the classic butterfly view (x right, z up, y = depth) by a small
    yaw + tilt so the lobes overlap with parallax. Returns (xy, depth).
    """
    x, y, z = pts[:, 0], pts[:, 1], pts[:, 2] - 27.0   # centre z on the lobes
    cy_, sy_ = np.cos(np.radians(yaw_deg)), np.sin(np.radians(yaw_deg))
    xr = x * cy_ - y * sy_
    dep = x * sy_ + y * cy_
    ct, st = np.cos(np.radians(tilt_deg)), np.sin(np.radians(tilt_deg))
    zr = z * ct - dep * st
    dep = z * st + dep * ct
    return np.stack([xr, -zr], axis=1), dep


def fit_to_viewbox(xy: np.ndarray) -> np.ndarray:
    """Uniform scale + centre so every point lies within FIT_RADIUS of centre."""
    c = (xy.max(0) + xy.min(0)) / 2.0
    r = np.linalg.norm(xy - c, axis=1).max()
    return (xy - c) * (FIT_RADIUS / r) + np.array([CX, CY])


def resample(xy: np.ndarray, dep: np.ndarray,
             ds: float) -> tuple[np.ndarray, np.ndarray]:
    """Resample polyline to ~uniform arc-length spacing ds."""
    seg = np.linalg.norm(np.diff(xy, axis=0), axis=1)
    s = np.concatenate([[0.0], np.cumsum(seg)])
    si = np.arange(0.0, s[-1], ds)
    xi = np.interp(si, s, xy[:, 0])
    yi = np.interp(si, s, xy[:, 1])
    di = np.interp(si, s, dep)
    return np.stack([xi, yi], axis=1), di


# ---------------------------------------------------------------------------
# Depth bands -> subpath runs
# ---------------------------------------------------------------------------

def band_runs(xy: np.ndarray, dep: np.ndarray,
              n_bands: int) -> list[list[np.ndarray]]:
    """
    Assign each segment (i, i+1) to a depth band by quantile of its mean
    depth; return per-band lists of point runs (consecutive segments fused).
    """
    seg_dep = (dep[:-1] + dep[1:]) / 2.0
    qs = np.quantile(seg_dep, np.linspace(0, 1, n_bands + 1)[1:-1])
    band = np.searchsorted(qs, seg_dep)

    runs: list[list[np.ndarray]] = [[] for _ in range(n_bands)]
    start = 0
    for i in range(1, len(band) + 1):
        if i == len(band) or band[i] != band[start]:
            runs[band[start]].append(xy[start:i + 1])
            start = i
    return runs


# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------

def hex_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lighten(rgb: tuple[int, int, int], f: float) -> tuple[int, int, int]:
    return tuple(int(c + (255 - c) * f) for c in rgb)


def layer_color(band: int, layer: int) -> tuple[int, int, int, int]:
    rgb = hex_rgb(BAND_COLORS[band])
    if layer == 2:                       # bright core
        rgb = lighten(rgb, 0.45 if band >= N_BANDS - 2 else 0.25)
    return (*rgb, LAYERS[layer][1])


# ---------------------------------------------------------------------------
# PNG preview (exact same stroke spec as the VectorDrawable)
# ---------------------------------------------------------------------------

def radial_bg(size: int) -> Image.Image:
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64)
    r = np.hypot(xx - size / 2, yy - size / 2) / (size * 0.70)
    t = np.clip(r, 0, 1)[..., None]
    inner = np.array(hex_rgb(BG_INNER), dtype=np.float64)
    outer = np.array(hex_rgb(BG_OUTER), dtype=np.float64)
    img = inner * (1 - t) + outer * t
    return Image.fromarray(img.astype(np.uint8), "RGB").convert("RGBA")


def render_preview(runs, head_xy, size: int = 1080, ss: int = 2) -> Image.Image:
    S = size * ss
    k = S / VIEW
    img = radial_bg(S)

    for layer in range(len(LAYERS)):
        width = max(1, round(LAYERS[layer][0] * k))
        ov = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        for band in range(N_BANDS):
            col = layer_color(band, layer)
            for run in runs[band]:
                pts = [tuple(p * k) for p in run]
                d.line(pts, fill=col, width=width, joint="curve")
        img = Image.alpha_composite(img, ov)

    # tracer head: glow + white dot
    ov = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    hx, hy = head_xy * k
    for rad, col in [(4.4, (79, 195, 247, 70)), (2.6, (179, 229, 252, 160)),
                     (1.5, (255, 255, 255, 255))]:
        rr = rad * k
        d.ellipse([hx - rr, hy - rr, hx + rr, hy + rr], fill=col)
    img = Image.alpha_composite(img, ov)

    return img.resize((size, size), Image.LANCZOS)


def circle_mask(img: Image.Image) -> Image.Image:
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), m)
    return out


# ---------------------------------------------------------------------------
# VectorDrawable emission
# ---------------------------------------------------------------------------

def path_data(run: np.ndarray) -> str:
    p = "M%.1f,%.1f" % tuple(run[0])
    p += "".join("L%.1f,%.1f" % tuple(q) for q in run[1:])
    return p


def band_path_data(runs_b: list[np.ndarray]) -> str:
    return " ".join(path_data(r) for r in runs_b)


def emit_xml(runs, head_xy, round_variant: bool) -> str:
    hx, hy = head_xy
    lines = []
    a = lines.append
    a('<?xml version="1.0" encoding="utf-8"?>')
    a('<!--')
    a('  Chaoscope launcher icon — a real Lorenz attractor orbit (RK4-integrated,')
    a('  yaw 24 / tilt 12 view), depth-shaded far->near from deep violet to bright')
    a('  cyan. Each depth band is stroked three times (halo / mid / core) to fake')
    a('  the neon-glow accumulation look of the in-app renderer. The white dot is')
    a('  the orbit head ("the point being traced").')
    a('  Generated by prototype/icon_design.py — edit that script, not this file.')
    a('-->')
    a('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    a('    xmlns:aapt="http://schemas.android.com/aapt"')
    a('    android:width="108dp"')
    a('    android:height="108dp"')
    a('    android:viewportWidth="108"')
    a('    android:viewportHeight="108">')
    a('')
    if round_variant:
        a('    <clip-path android:pathData="M0,54 A54,54 0 1,0 108,54 A54,54 0 1,0 0,54"/>')
        a('')
    a('    <!-- Radial night-sky background -->')
    a('    <path android:pathData="M0,0h108v108H0z">')
    a('        <aapt:attr name="android:fillColor">')
    a('            <gradient')
    a('                android:type="radial"')
    a('                android:centerX="54" android:centerY="54"')
    a('                android:gradientRadius="76"')
    a(f'                android:startColor="{BG_INNER}"')
    a(f'                android:endColor="{BG_OUTER}"/>')
    a('        </aapt:attr>')
    a('    </path>')
    for layer, (width, alpha) in enumerate(LAYERS):
        name = ["halo", "mid", "core"][layer]
        a('')
        a(f'    <!-- ── {name} pass (w={width}) ── -->')
        for band in range(N_BANDS):
            r, g, b, al = layer_color(band, layer)
            col = "#%02X%02X%02X%02X" % (al, r, g, b)
            a('    <path')
            a(f'        android:strokeColor="{col}"')
            a(f'        android:strokeWidth="{width}"')
            a('        android:strokeLineCap="round"')
            a('        android:strokeLineJoin="round"')
            a('        android:fillColor="@android:color/transparent"')
            a(f'        android:pathData="{band_path_data(runs[band])}"/>')
    a('')
    a('    <!-- Orbit head: glow + white dot -->')
    for rad, col in [(4.4, "#464FC3F7"), (2.6, "#A0B3E5FC"), (1.5, "#FFFFFFFF")]:
        a('    <path')
        a(f'        android:fillColor="{col}"')
        a(f'        android:pathData="M{hx - rad:.1f},{hy:.1f} '
          f'A{rad:.1f},{rad:.1f} 0 1,0 {hx + rad:.1f},{hy:.1f} '
          f'A{rad:.1f},{rad:.1f} 0 1,0 {hx - rad:.1f},{hy:.1f}"/>')
    a('</vector>')
    a('')
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(emit: bool = False) -> None:
    os.makedirs(OUT_DIR, exist_ok=True)

    pts = lorenz_orbit()
    xy, dep = project(pts)
    xy = fit_to_viewbox(xy)
    xy, dep = resample(xy, dep, RESAMPLE_DS)
    runs = band_runs(xy, dep, N_BANDS)
    head = xy[-1]

    n_pts = sum(len(r) for b in runs for r in b)
    print(f"orbit: {len(xy)} pts after resample, {n_pts} pts in "
          f"{sum(len(b) for b in runs)} runs across {N_BANDS} bands")

    img = render_preview(runs, head)
    img.save(os.path.join(OUT_DIR, "icon_v2_preview.png"))
    circle_mask(img).save(os.path.join(OUT_DIR, "icon_v2_preview_round.png"))

    strip = Image.new("RGBA", (96 + 8 + 48, 96), (32, 32, 32, 255))
    strip.paste(img.resize((96, 96), Image.LANCZOS), (0, 0))
    strip.paste(img.resize((48, 48), Image.LANCZOS), (104, 24))
    strip.save(os.path.join(OUT_DIR, "icon_v2_small.png"))
    print("previews written to", OUT_DIR)

    if emit:
        for rnd, fname in [(False, "ic_launcher.xml"), (True, "ic_launcher_round.xml")]:
            path = os.path.join(RES_DIR, fname)
            with open(path, "w", encoding="utf-8") as f:
                f.write(emit_xml(runs, head, rnd))
            print("wrote", path, f"({os.path.getsize(path) / 1024:.1f} KB)")


if __name__ == "__main__":
    import sys
    main(emit="--emit" in sys.argv)
