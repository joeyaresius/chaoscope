"""Quick check: orbit-trace per-pixel density distribution.

Reproduces computeOrbitDotAlpha's binning for a few attractors and compares
the absolute peak bin (current normalisation basis) against high percentiles
of the nonzero bins (proposed basis). Mirrors the Kotlin pipeline: 2M orbit
points binned at 768x768.
"""
import numpy as np

SIZE = 768
N = 2_000_000
PEAK_TARGET = 220
ALPHA_CAP = 28


def lorenz(n):
    x, y, z = 0.1, 0.0, 0.0
    pts = np.empty((n, 2), dtype=np.float32)
    s, r, b, dt = 10.0, 28.0, 2.667, 0.005
    for i in range(n):
        dx = s * (y - x); dy = x * (r - z) - y; dz = x * y - b * z
        x += dx * dt; y += dy * dt; z += dz * dt
        pts[i] = (x, z)
    return pts


def henon(n):
    x, y = 0.1, 0.1
    pts = np.empty((n, 2), dtype=np.float32)
    for i in range(n):
        x, y = 1 - 1.4 * x * x + y, 0.3 * x
        pts[i] = (x, y)
    return pts


def gumowski_mira(n):
    a, mu = 0.008, -0.496
    def f(v):
        return mu * v + 2 * (1 - mu) * v * v / (1 + v * v)
    x, y = 0.1, 0.1
    pts = np.empty((n, 2), dtype=np.float32)
    for i in range(n):
        x, y = y + a * (1 - 0.05 * y * y) * y + f(x), -x + f(y + a * (1 - 0.05 * y * y) * y + f(x))
        pts[i] = (x, y)
    return pts


def clifford(n):
    a, b, c, d = -1.4, 1.6, 1.0, 0.7
    x, y = 0.1, 0.1
    pts = np.empty((n, 2), dtype=np.float32)
    for i in range(n):
        x, y = np.sin(a * y) + c * np.cos(a * x), np.sin(b * x) + d * np.cos(b * y)
        pts[i] = (x, y)
    return pts


def analyse(name, pts):
    pts = pts[1000:]  # warmup discard
    finite = np.isfinite(pts).all(axis=1)
    pts = pts[finite]
    lo = pts.min(axis=0); hi = pts.max(axis=0)
    span = np.maximum(hi - lo, 1e-9)
    ij = ((pts - lo) / span * (SIZE - 1)).astype(np.int32)
    bins = np.zeros(SIZE * SIZE, dtype=np.int64)
    np.add.at(bins, ij[:, 1] * SIZE + ij[:, 0], 1)
    nz = bins[bins > 0]
    peak = nz.max()
    p999, p99, p95, med = (np.percentile(nz, q) for q in (99.9, 99, 95, 50))
    a_now = int(np.clip(PEAK_TARGET // peak, 1, ALPHA_CAP))
    a_p99 = int(np.clip(PEAK_TARGET // p99, 1, ALPHA_CAP))
    print(f"{name:14s} nz_px={len(nz):7d} peak={peak:8d} p99.9={p999:8.0f} "
          f"p99={p99:7.0f} p95={p95:6.0f} med={med:4.0f} "
          f"alpha now={a_now:3d} -> p99-based={a_p99:3d} "
          f"median px value now={min(255, a_now * med):.0f} new={min(255, a_p99 * med):.0f}")


analyse("Lorenz", lorenz(N))
analyse("Henon", henon(N))
analyse("Gumowski-Mira", gumowski_mira(N))
analyse("Clifford", clifford(N))
