"""
Chaoscope Clone - Histogram Renderer
Phase 2: Density accumulation + logarithmic tone-mapping.

The core idea (identical to the original Chaoscope "Gas" mode):
  1. Project each 3-D point onto a 2-D canvas.
  2. Accumulate hit-counts in an integer grid (the histogram).
  3. Apply log(1 + count) to compress the dynamic range.
  4. Map the normalised log-density to a colour gradient.

This module is intentionally kept pure-numpy so it can be
ported 1-to-1 to C/C++ later.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import TYPE_CHECKING

import numpy as np
from numpy.typing import NDArray
from typing import Optional, Tuple

if TYPE_CHECKING:
    pass


# ---------------------------------------------------------------------------
# Camera / projection helpers
# ---------------------------------------------------------------------------

@dataclass
class Camera:
    """
    Simple orthographic camera for 3-D -> 2-D projection.

    Rotation is stored as Euler angles (degrees) applied in ZYX order.
    Use yaw / pitch / roll to orbit around a 3-D attractor.
    For 2-D attractors leave all angles at 0.
    """
    yaw: float = 0.0     # rotation around Z axis (degrees)
    pitch: float = 0.0   # rotation around Y axis (degrees)
    roll: float = 0.0    # rotation around X axis (degrees)
    zoom: float = 1.0    # uniform scale applied after rotation
    offset_x: float = 0.0
    offset_y: float = 0.0

    def _rotation_matrix(self) -> NDArray[np.float64]:
        yr = math.radians(self.yaw)
        pr = math.radians(self.pitch)
        rr = math.radians(self.roll)

        cy, sy = math.cos(yr), math.sin(yr)
        cp, sp = math.cos(pr), math.sin(pr)
        cr, sr = math.cos(rr), math.sin(rr)

        # ZYX convention
        Rz = np.array([[cy, -sy, 0],
                        [sy,  cy, 0],
                        [0,   0,  1]], dtype=np.float64)
        Ry = np.array([[ cp, 0, sp],
                        [  0, 1,  0],
                        [-sp, 0, cp]], dtype=np.float64)
        Rx = np.array([[1,  0,   0],
                        [0, cr, -sr],
                        [0, sr,  cr]], dtype=np.float64)
        return Rz @ Ry @ Rx

    def project(
        self,
        xs: NDArray[np.float64],
        ys: NDArray[np.float64],
        zs: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64]]:
        """
        Project world-space (x, y, z) to canvas-space (u, v)
        using orthographic projection (drop Z after rotation).
        """
        pts = np.stack([xs, ys, zs], axis=0)  # (3, N)
        R = self._rotation_matrix()
        rotated = R @ pts                       # (3, N)

        u = rotated[0] * self.zoom + self.offset_x
        v = rotated[1] * self.zoom + self.offset_y
        return u, v


# ---------------------------------------------------------------------------
# Histogram accumulator
# ---------------------------------------------------------------------------

@dataclass
class HistogramCanvas:
    """
    A floating-point accumulator that maps world-space points to pixels
    and keeps a hit-count per pixel.

    The canvas auto-fits the attractor on the first call to
    ``accumulate`` (unless ``world_bounds`` is set explicitly).

    Parameters
    ----------
    width, height : canvas size in pixels.
    world_bounds  : (x_min, x_max, y_min, y_max) in world units.
                    If None, it is computed from the first batch of points.
    padding       : fraction of the world extent to add as margin.
    """
    width: int = 1024
    height: int = 1024
    world_bounds: "Optional[Tuple[float, float, float, float]]" = None
    padding: float = 0.05

    # internal state
    _counts: NDArray[np.int64] = field(init=False, repr=False)

    def __post_init__(self) -> None:
        self._counts = np.zeros((self.height, self.width), dtype=np.int64)

    # ------------------------------------------------------------------
    def reset(self) -> None:
        self._counts[:] = 0
        self.world_bounds = None

    # ------------------------------------------------------------------
    def _auto_bounds(
        self,
        u: NDArray[np.float64],
        v: NDArray[np.float64],
    ) -> Tuple[float, float, float, float]:
        pad_u = (u.max() - u.min()) * self.padding + 1e-9
        pad_v = (v.max() - v.min()) * self.padding + 1e-9
        return float(u.min() - pad_u), float(u.max() + pad_u), \
               float(v.min() - pad_v), float(v.max() + pad_v)

    # ------------------------------------------------------------------
    def accumulate(
        self,
        u: NDArray[np.float64],
        v: NDArray[np.float64],
    ) -> None:
        """
        Project (u, v) world coordinates into pixel space and
        increment the hit-count for each in-bounds pixel.
        """
        if self.world_bounds is None:
            self.world_bounds = self._auto_bounds(u, v)

        x_min, x_max, y_min, y_max = self.world_bounds
        x_range = x_max - x_min
        y_range = y_max - y_min

        # map to [0, width) and [0, height)
        px = ((u - x_min) / x_range * self.width).astype(np.int64)
        py = ((v - y_min) / y_range * self.height).astype(np.int64)

        # keep only in-bounds pixels
        mask = (px >= 0) & (px < self.width) & (py >= 0) & (py < self.height)
        np.add.at(self._counts, (py[mask], px[mask]), 1)

    # ------------------------------------------------------------------
    @property
    def counts(self) -> NDArray[np.int64]:
        return self._counts

    @property
    def total_hits(self) -> int:
        return int(self._counts.sum())


# ---------------------------------------------------------------------------
# Tone-mapping  (log-density -> [0, 1])
# ---------------------------------------------------------------------------

def log_density_map(
    counts: NDArray[np.int64],
    gamma: float = 1.0,
) -> NDArray[np.float64]:
    """
    Convert raw hit-counts to a normalised [0, 1] density map using
    the logarithmic formula that Chaoscope uses for its "Gas" mode:

        density = log(1 + count) / log(1 + max_count)

    An optional *gamma* correction is applied afterwards.
    """
    log_counts = np.log1p(counts.astype(np.float64))
    max_log = log_counts.max()
    if max_log == 0.0:
        return np.zeros_like(log_counts)
    density = log_counts / max_log
    if gamma != 1.0:
        density = np.power(density, gamma)
    return density


def linear_density_map(
    counts: NDArray[np.int64],
    gamma: float = 1.0,
) -> NDArray[np.float64]:
    """Linear normalisation (useful for comparison / debugging)."""
    m = counts.max()
    if m == 0:
        return np.zeros(counts.shape, dtype=np.float64)
    density = counts.astype(np.float64) / m
    if gamma != 1.0:
        density = np.power(density, gamma)
    return density


# ---------------------------------------------------------------------------
# Convenience: build a full density image in one call
# ---------------------------------------------------------------------------

def build_density_image(
    u: NDArray[np.float64],
    v: NDArray[np.float64],
    width: int = 1024,
    height: int = 1024,
    gamma: float = 1.0,
    tone_map: str = "log",
) -> Tuple[NDArray[np.float64], HistogramCanvas]:
    """
    Accumulate (u, v) points into a histogram and return a normalised
    density image of shape (height, width) with values in [0, 1].

    Parameters
    ----------
    tone_map : "log" (default, Chaoscope-style) or "linear".

    Returns
    -------
    density  : float64 array (height, width) in [0, 1]
    canvas   : the HistogramCanvas (contains raw counts)
    """
    canvas = HistogramCanvas(width=width, height=height)
    canvas.accumulate(u, v)

    if tone_map == "log":
        density = log_density_map(canvas.counts, gamma=gamma)
    else:
        density = linear_density_map(canvas.counts, gamma=gamma)

    return density, canvas
