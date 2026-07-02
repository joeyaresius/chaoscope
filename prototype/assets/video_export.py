"""
Chaoscope — Instagram Reel exporter ("materialize" / orbit-trace build sweep).

Renders a continuous strange-attractor orbit drawing itself from nothing to its
full structure: a bright head drags a glowing, depth-shaded neon trail that
accumulates (overlaps brighten, like the in-app "Gas" glow) until the whole
attractor is revealed, then holds. Output is a vertical 1080x1920 MP4 matching
the feed look (same #06060F background + CHAOSCOPE wordmark).

Only the flowing 3-D systems trace well (lorenz, rossler, aizawa, thomas,
lorenz84); the 2-D maps (clifford, dejong, henon) jump between points and are
not continuous curves, so they're rejected.

Frames are drawn with PIL and piped to ffmpeg (no temp PNGs). ffmpeg is found
on PATH or at C:\\FFmpeg\\bin\\ffmpeg.exe.

Usage
-----
# default: Lorenz materialize, 1080x1920, ~6s, looping-friendly
python video_export.py

# pick attractor + camera, dump a single preview frame to tune the view
python video_export.py -a thomas --yaw 28 --pitch 18 --preview 0.6

# full render of one attractor
python video_export.py -a lorenz --yaw 0 --pitch 90 -o out/insta/lorenz_reel.mp4

Outputs land in prototype/out/insta/.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys

# The render engine lives in the sibling engine/ folder.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "engine"))

import numpy as np
from PIL import Image

from attractors import ATTRACTORS, iterate_attractor
from renderer import Camera
from insta_export import radial_bg, stamp_watermark

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "out", "insta")

# Attractors that form a continuous curve (ODE flows) — the only ones that
# look right as a single traced orbit.
FLOWING = {"lorenz", "rossler", "aizawa", "thomas", "lorenz84", "icon"}

# Depth ramp: far -> near, deep violet to bright cyan (matches the launcher icon).
COL_FAR = np.array([0x5B, 0x2F, 0xD1], dtype=np.float64)
COL_NEAR = np.array([0x4F, 0xC3, 0xF7], dtype=np.float64)

# Soft-saturation strength for the accumulated glow. Higher = dimmer/gentler
# (more accumulation needed before a pixel reaches full brightness), so dense
# overlap regions stay coloured instead of clipping to white.
GLOW_SCALE = 90.0


def tonemap(accum: np.ndarray, scale: float) -> np.ndarray:
    """Soft-saturate the additive RGB glow while preserving hue.

    Each pixel's peak channel drives an intensity curve 1-exp(-I/scale) that
    asymptotes to 1; the colour ratios are kept, so a bright cyan core renders
    as saturated cyan, not blown-out white.
    """
    lum = accum.max(axis=2, keepdims=True)
    sat = 1.0 - np.exp(-lum / scale)                     # 0..1, smooth
    factor = np.divide(sat * 255.0, lum, out=np.zeros_like(lum),
                       where=lum > 1e-6)
    return accum * factor


def find_ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    fallback = r"C:\FFmpeg\bin\ffmpeg.exe"
    if os.path.exists(fallback):
        return fallback
    sys.exit("ffmpeg not found on PATH or at C:\\FFmpeg\\bin\\ffmpeg.exe")


# ---------------------------------------------------------------------------
# Orbit -> resampled pixel-space polyline + per-point depth
# ---------------------------------------------------------------------------

def build_path(attractor_key: str, iters: int, yaw: float, pitch: float,
               roll: float, zoom: float, w: int, h: int,
               target_pts: int) -> tuple[np.ndarray, np.ndarray]:
    attractor = ATTRACTORS[attractor_key]
    xs, ys, zs = iterate_attractor(attractor, n_iter=iters, warmup=2000)

    cam = Camera(yaw=yaw, pitch=pitch, roll=roll, zoom=zoom)
    R = cam._rotation_matrix()
    rot = R @ np.stack([xs, ys, zs])          # (3, N)
    u, v, depth = rot[0], rot[1], rot[2]

    # Fit (u, v) uniformly into a centred region of the vertical frame.
    pad_x, pad_y = w * 0.09, h * 0.10
    span_u, span_v = u.max() - u.min(), v.max() - v.min()
    s = min((w - 2 * pad_x) / span_u, (h - 2 * pad_y) / span_v)
    px = (u - (u.max() + u.min()) / 2) * s + w / 2
    py = h / 2 - (v - (v.max() + v.min()) / 2) * s     # flip y for image space
    xy = np.stack([px, py], axis=1)

    # Resample to ~uniform arc-length spacing so the head moves at constant
    # visual speed; target_pts controls how clean/sparse the ribbon is.
    seg = np.linalg.norm(np.diff(xy, axis=0), axis=1)
    arclen = np.concatenate([[0.0], np.cumsum(seg)])
    ds = arclen[-1] / target_pts
    si = np.arange(0.0, arclen[-1], ds)
    rx = np.interp(si, arclen, xy[:, 0])
    ry = np.interp(si, arclen, xy[:, 1])
    rd = np.interp(si, arclen, depth)
    xy_r = np.stack([rx, ry], axis=1)

    # Normalise depth to [0, 1] robustly (2nd..98th percentile).
    lo, hi = np.percentile(rd, 2), np.percentile(rd, 98)
    dn = np.clip((rd - lo) / max(hi - lo, 1e-9), 0.0, 1.0)
    return xy_r, dn


def depth_color(dn: float) -> np.ndarray:
    return COL_FAR + (COL_NEAR - COL_FAR) * dn


# ---------------------------------------------------------------------------
# Head-glow kernel (added fresh each frame, not accumulated)
# ---------------------------------------------------------------------------

def head_kernel(radius: int = 26) -> np.ndarray:
    yy, xx = np.mgrid[-radius:radius + 1, -radius:radius + 1].astype(np.float64)
    r = np.hypot(xx, yy) / radius
    glow = np.clip(1.0 - r, 0.0, 1.0) ** 2.2
    tint = np.array([210, 235, 255], dtype=np.float64)
    k = glow[..., None] * tint * 1.15
    core = (np.hypot(xx, yy) <= 1.6)[..., None] * np.array([255, 255, 255])
    return np.clip(k + core, 0, 255)


def add_kernel(dst: np.ndarray, kern: np.ndarray, cx: int, cy: int) -> None:
    r = kern.shape[0] // 2
    h, w = dst.shape[:2]
    x0, x1 = max(0, cx - r), min(w, cx + r + 1)
    y0, y1 = max(0, cy - r), min(h, cy + r + 1)
    if x0 >= x1 or y0 >= y1:
        return
    kx0, ky0 = x0 - (cx - r), y0 - (cy - r)
    dst[y0:y1, x0:x1] += kern[ky0:ky0 + (y1 - y0), kx0:kx0 + (x1 - x0)]


# ---------------------------------------------------------------------------
# Frame generation
# ---------------------------------------------------------------------------

def draw_arc(accum: np.ndarray, xy: np.ndarray, dn: np.ndarray,
             a: int, b: int) -> None:
    """Add the neon contribution of points [a..b] onto the float accum buffer."""
    if b - a < 1:
        return
    from PIL import ImageDraw
    h, w = accum.shape[:2]
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    pts = [tuple(p) for p in xy[a:b + 1]]
    c = depth_color(float(dn[a:b + 1].mean()))
    rgb = tuple(int(v) for v in c)
    core = tuple(int(v + (255 - v) * 0.30) for v in c)
    d.line(pts, fill=(*rgb, 32), width=7, joint="curve")
    d.line(pts, fill=(*rgb, 72), width=3, joint="curve")
    d.line(pts, fill=(*core, 180), width=1, joint="curve")
    arr = np.asarray(layer).astype(np.float32)
    accum[..., :3] += arr[..., :3] * (arr[..., 3:4] / 255.0)


# ---------------------------------------------------------------------------
# Turntable mode: build the orbit while the camera rotates.
#
# Unlike the fixed-camera materialize (which accumulates in 2-D screen space),
# this re-projects the whole revealed orbit at a new angle every frame — far
# more compute, but it lets the attractor draw itself *and* turn. A full 360°
# over the clip makes the loop seamless.
# ---------------------------------------------------------------------------

def build_path3d(attractor_key: str, iters: int,
                 target_pts: int) -> tuple[np.ndarray, np.ndarray, float]:
    """Ordered orbit, arc-length-resampled in 3-D. Returns (P, centre, radius)."""
    attractor = ATTRACTORS[attractor_key]
    xs, ys, zs = iterate_attractor(attractor, n_iter=iters, warmup=2000)
    P = np.stack([xs, ys, zs], axis=1)

    seg = np.linalg.norm(np.diff(P, axis=0), axis=1)
    arclen = np.concatenate([[0.0], np.cumsum(seg)])
    ds = arclen[-1] / target_pts
    si = np.arange(0.0, arclen[-1], ds)
    R = np.stack([np.interp(si, arclen, P[:, k]) for k in range(3)], axis=1)

    centre = (R.max(0) + R.min(0)) / 2.0
    radius = float(np.linalg.norm(R - centre, axis=1).max())
    return R, centre, radius


def contiguous_runs(mask: np.ndarray) -> list[tuple[int, int]]:
    """Inclusive (start, end) index ranges where mask is True (len >= 2)."""
    m = mask.astype(np.int8)
    d = np.diff(np.concatenate([[0], m, [0]]))
    starts = np.where(d == 1)[0]
    ends = np.where(d == -1)[0] - 1
    return [(s, e) for s, e in zip(starts, ends) if e > s]


def rot_matrix(spin: float, tilt: float) -> np.ndarray:
    """Spin about the object's vertical (z), then tilt the view forward (x)."""
    cs, ss = np.cos(spin), np.sin(spin)
    ct, st = np.cos(tilt), np.sin(tilt)
    Rz = np.array([[cs, -ss, 0.0], [ss, cs, 0.0], [0.0, 0.0, 1.0]])
    Rx = np.array([[1.0, 0.0, 0.0], [0.0, ct, -st], [0.0, st, ct]])
    return Rx @ Rz


def render_turntable(args, key: str, W: int, H: int) -> None:
    from PIL import ImageDraw

    P, centre, radius = build_path3d(key, args.iters, args.points)
    Pc = (P - centre).T                       # (3, M)
    M = Pc.shape[1]
    tilt = np.radians(args.tilt)

    # Fit from the worst-case projected silhouette over the whole spin, so the
    # attractor fills the frame yet never clips as it turns (a bounding-sphere
    # fit would be far too small for an elongated shape like Lorenz).
    umin = vmin = 1e9
    umax = vmax = -1e9
    for sp in np.linspace(0, 2 * np.pi * max(args.spins, 1.0), 48, endpoint=False):
        r = rot_matrix(sp, tilt) @ Pc
        umin, umax = min(umin, r[0].min()), max(umax, r[0].max())
        vmin, vmax = min(vmin, r[1].min()), max(vmax, r[1].max())
    pad_x, pad_y = W * 0.045, H * 0.045
    scale = min((W - 2 * pad_x) / (umax - umin), (H - 2 * pad_y) / (vmax - vmin))
    ox = W / 2.0 - scale * (umin + umax) / 2.0
    oy = H / 2.0 + scale * (vmin + vmax) / 2.0

    bg = np.asarray(radial_bg(W, H).convert("RGB")).astype(np.float32)
    kern = head_kernel()
    nb = 7

    def frame(spin: float, head: int, show_head: bool) -> bytes:
        rot = rot_matrix(spin, tilt) @ Pc[:, :head + 1]
        u, v, dep = rot[0], rot[1], rot[2]
        px = u * scale + ox
        py = oy - v * scale
        xy = np.stack([px, py], axis=1)
        dn = np.clip(dep / radius * 0.5 + 0.5, 0.0, 1.0)
        band = np.clip((dn * nb).astype(np.int32), 0, nb - 1)

        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        for b in range(nb):                   # far -> near, near painted last
            c = depth_color(b / (nb - 1))
            rgb = tuple(int(x) for x in c)
            core = tuple(int(x + (255 - x) * 0.30) for x in c)
            for s, e in contiguous_runs(band == b):
                # Extend one point past the run so the segment bridging into the
                # next band is drawn — otherwise band boundaries leave dark gaps.
                pts = [tuple(p) for p in xy[s:min(e + 2, head + 1)]]
                d.line(pts, fill=(*rgb, 32), width=7, joint="curve")
                d.line(pts, fill=(*rgb, 72), width=3, joint="curve")
                d.line(pts, fill=(*core, 180), width=1, joint="curve")

        arr = np.asarray(layer).astype(np.float32)
        contrib = arr[..., :3] * (arr[..., 3:4] / 255.0)
        out = bg + tonemap(contrib, args.glow)
        if show_head:
            add_kernel(out, kern, int(xy[head, 0]), int(xy[head, 1]))
        out = np.clip(out, 0, 255).astype(np.uint8)
        img = Image.fromarray(out).convert("RGBA")
        stamp_watermark(img)
        return img.convert("RGB").tobytes()

    build_frames = int(args.build * args.fps)
    hold_frames = int(args.hold * args.fps)
    total = build_frames + hold_frames

    if args.preview is not None:
        t = float(np.clip(args.preview, 0, 1))
        head = int(t * (M - 1))
        png = Image.frombytes("RGB", (W, H),
                              frame(2 * np.pi * args.spins * t, head, True))
        p = os.path.join(OUT_DIR, f"reel_preview_{key}.png")
        png.save(p)
        print(f"turntable preview ({t:.2f}) -> {p}  [{M} pts]")
        return

    out_path = args.output or os.path.join(OUT_DIR, f"{key}_turntable.mp4")
    cmd = [find_ffmpeg(), "-y", "-nostats", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(args.fps), "-i", "-", "-an", "-c:v", "libx264",
           "-pix_fmt", "yuv420p", "-crf", "18", "-movflags", "+faststart",
           out_path]
    print(f"[{key}] turntable | {M} pts | {total} frames | {args.spins:g} turn(s) "
          f"-> {out_path}")
    errlog_path = os.path.join(OUT_DIR, f"_ffmpeg_{key}.log")
    errlog = open(errlog_path, "wb")
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE,
                            stdout=subprocess.DEVNULL, stderr=errlog)

    for f in range(total):
        spin = 2 * np.pi * args.spins * f / total      # one full turn => loops
        if f < build_frames:
            head, show = int(round((f + 1) / build_frames * (M - 1))), True
        else:
            head, show = M - 1, False
        proc.stdin.write(frame(spin, head, show))
        if f % 15 == 0:
            print(f"  frame {f}/{total}", flush=True)

    proc.stdin.close()
    rc = proc.wait()
    errlog.close()
    if rc != 0:
        with open(errlog_path, "r", encoding="utf-8", errors="ignore") as fh:
            sys.exit("ffmpeg failed:\n" + fh.read()[-1500:])
    print(f"done -> {out_path}  ({os.path.getsize(out_path) / 1e6:.1f} MB, "
          f"{total / args.fps:.1f}s)")


def render(args) -> None:
    key = args.attractor.lower().replace("_", "").replace("-", "")
    if key not in ATTRACTORS:
        sys.exit(f"Unknown attractor '{args.attractor}'. See render.py --list.")
    if key not in FLOWING:
        sys.exit(f"'{args.attractor}' is a 2-D map and doesn't trace as a "
                 f"continuous curve. Try: {', '.join(sorted(FLOWING))}.")

    W, H = args.width, args.height
    os.makedirs(OUT_DIR, exist_ok=True)

    if args.spins and args.spins > 0:
        render_turntable(args, key, W, H)
        return

    xy, dn = build_path(key, args.iters, args.yaw, args.pitch, args.roll,
                        args.zoom, W, H, args.points)
    M = len(xy)

    bg = np.asarray(radial_bg(W, H).convert("RGB")).astype(np.float32)
    kern = head_kernel()

    def compose(accum, head_idx):
        out = bg + tonemap(accum[..., :3], args.glow)
        if head_idx < M:
            add_kernel(out, kern, int(xy[head_idx, 0]), int(xy[head_idx, 1]))
        out = np.clip(out, 0, 255).astype(np.uint8)
        img = Image.fromarray(out).convert("RGBA")
        stamp_watermark(img)
        return img.convert("RGB")

    # Single preview frame at a given progress fraction, then exit.
    if args.preview is not None:
        accum = np.zeros((H, W, 3), np.float32)
        head = int(np.clip(args.preview, 0, 1) * (M - 1))
        draw_arc(accum, xy, dn, 0, head)
        p = os.path.join(OUT_DIR, f"reel_preview_{key}.png")
        compose(accum, head).save(p)
        print(f"preview ({args.preview:.2f}) -> {p}  [{M} pts]")
        return

    build_frames = int(args.build * args.fps)
    hold_frames = int(args.hold * args.fps)
    out_path = args.output or os.path.join(OUT_DIR, f"{key}_reel.mp4")

    # ffmpeg stderr goes to a log FILE, never a PIPE: piping stderr and only
    # reading it after the write loop deadlocks once ffmpeg's stderr buffer
    # fills (ffmpeg then stops draining stdin, so stdin.write blocks forever).
    cmd = [find_ffmpeg(), "-y", "-nostats", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24",
           "-s", f"{W}x{H}", "-r", str(args.fps), "-i", "-", "-an",
           "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18",
           "-movflags", "+faststart", out_path]
    print(f"[{key}] {M} pts | {build_frames}+{hold_frames} frames @ {args.fps}fps "
          f"-> {out_path}")
    errlog_path = os.path.join(OUT_DIR, f"_ffmpeg_{key}.log")
    errlog = open(errlog_path, "wb")
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE,
                            stdout=subprocess.DEVNULL, stderr=errlog)

    accum = np.zeros((H, W, 3), np.float32)
    prev = 0
    for f in range(build_frames):
        head = int(round((f + 1) / build_frames * (M - 1)))
        draw_arc(accum, xy, dn, prev, head)
        prev = head
        proc.stdin.write(compose(accum, head).tobytes())
        if f % 15 == 0:
            print(f"  build {f}/{build_frames}", flush=True)

    final = compose(accum, M)             # full structure, no head dot
    final_bytes = final.tobytes()
    for _ in range(hold_frames):
        proc.stdin.write(final_bytes)

    proc.stdin.close()
    rc = proc.wait()
    errlog.close()
    if rc != 0:
        with open(errlog_path, "r", encoding="utf-8", errors="ignore") as fh:
            sys.exit("ffmpeg failed:\n" + fh.read()[-1500:])
    size_mb = os.path.getsize(out_path) / 1e6
    print(f"done -> {out_path}  ({size_mb:.1f} MB, "
          f"{(build_frames + hold_frames) / args.fps:.1f}s)")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Render an Instagram orbit-trace Reel.")
    ap.add_argument("--attractor", "-a", default="lorenz")
    ap.add_argument("--iters", type=int, default=70_000,
                    help="Orbit steps to integrate (default 70000).")
    ap.add_argument("--points", type=int, default=9000,
                    help="Resampled trail points (default 9000; lower = sparser).")
    ap.add_argument("--width", type=int, default=1080)
    ap.add_argument("--height", type=int, default=1920)
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--build", type=float, default=5.0,
                    help="Seconds for the orbit to draw itself (default 5).")
    ap.add_argument("--hold", type=float, default=1.5,
                    help="Seconds to hold the finished frame (default 1.5).")
    ap.add_argument("--yaw", type=float, default=0.0)
    ap.add_argument("--pitch", type=float, default=90.0)
    ap.add_argument("--roll", type=float, default=0.0)
    ap.add_argument("--zoom", type=float, default=1.0)
    ap.add_argument("--glow", type=float, default=GLOW_SCALE,
                    help=f"Glow softness; higher = dimmer/gentler "
                         f"(default {GLOW_SCALE:g}).")
    ap.add_argument("--spins", type=float, default=0.0,
                    help="Turntable mode: full camera turns over the clip "
                         "(0 = fixed camera; 1 = one seamless-looping turn).")
    ap.add_argument("--tilt", type=float, default=22.0,
                    help="Turntable viewing tilt in degrees (default 22).")
    ap.add_argument("--preview", type=float, default=None,
                    help="Dump one frame at this progress (0..1) and exit.")
    ap.add_argument("--caption", action="store_true",
                    help="Also write a post-ready .txt caption (install link + "
                         "CHS1 recreate-code) next to the MP4.")
    ap.add_argument("--palette", default="electric",
                    help="Palette for the caption's CHS1 code (default electric). "
                         "The reel itself uses the depth ramp, not a palette.")
    ap.add_argument("--hook", default="Watch a strange attractor draw itself "
                    "from a single equation. 🌀",
                    help="First caption line (the scroll-stopper).")
    ap.add_argument("--output", "-o", default=None)
    args = ap.parse_args(argv)
    render(args)

    if args.caption and args.preview is None:
        from marketing import spec_for_poc, build_caption
        key = args.attractor.lower().replace("_", "").replace("-", "")
        turntable = bool(args.spins and args.spins > 0)
        spec = spec_for_poc(key, args.palette,
                            yaw=args.yaw,
                            pitch=args.tilt if turntable else args.pitch)
        if spec is None:
            print(f"(no CHS1 default for '{key}'; caption skipped)")
        else:
            suffix = "_turntable" if turntable else "_reel"
            mp4 = args.output or os.path.join(OUT_DIR, f"{key}{suffix}.mp4")
            cap = os.path.splitext(mp4)[0] + ".txt"
            with open(cap, "w", encoding="utf-8") as fh:
                fh.write(build_caption(spec, args.hook))
            print(f"caption -> {cap}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
