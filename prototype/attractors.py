"""
Chaoscope Clone - Attractor Math Engine
Phase 1: Mathematical core for strange attractor iteration.

Each attractor is a callable that maps (x, y, z) -> (x', y', z').
All computation is vectorised with NumPy so we can iterate millions
of points per second on the CPU before moving to C/C++.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Callable, Protocol

import numpy as np
from numpy.typing import NDArray


# ---------------------------------------------------------------------------
# Protocol / base type
# ---------------------------------------------------------------------------

class Attractor(Protocol):
    name: str

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        ...


# ---------------------------------------------------------------------------
# 2-D Attractors
# ---------------------------------------------------------------------------

@dataclass
class CliffordAttractor:
    """
    Clifford Pickover attractor (2-D).
    x_{n+1} = sin(a * y_n) + c * cos(a * x_n)
    y_{n+1} = sin(b * x_n) + d * cos(b * y_n)
    z is always 0 for this attractor.
    """
    name: str = "Clifford"
    a: float = -1.4
    b: float = 1.6
    c: float = 1.0
    d: float = 0.7

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        xn = np.sin(self.a * y) + self.c * np.cos(self.a * x)
        yn = np.sin(self.b * x) + self.d * np.cos(self.b * y)
        return xn, yn, np.zeros_like(xn)


@dataclass
class PeterDeJongAttractor:
    """
    Peter de Jong attractor (2-D).
    x_{n+1} = sin(a * y_n) - cos(b * x_n)
    y_{n+1} = sin(c * x_n) - cos(d * y_n)
    """
    name: str = "PeterDeJong"
    a: float = -2.0
    b: float = -2.0
    c: float = -1.2
    d: float = 2.0

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        xn = np.sin(self.a * y) - np.cos(self.b * x)
        yn = np.sin(self.c * x) - np.cos(self.d * y)
        return xn, yn, np.zeros_like(xn)


@dataclass
class GumowskiMiraAttractor:
    """
    Gumowski-Mira attractor (2-D).
    mu in (-1, 1) controls the attractor shape.
    """
    name: str = "GumowskiMira"
    a: float = 0.008
    mu: float = -0.496

    @staticmethod
    def _f(x: NDArray[np.float64], mu: float) -> NDArray[np.float64]:
        return mu * x + 2.0 * (1.0 - mu) * x**2 / (1.0 + x**2)

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        xn = y + self.a * (1.0 - 0.05 * y**2) * y + self._f(x, self.mu)
        yn = -x + self._f(xn, self.mu)
        return xn, yn, np.zeros_like(xn)


# ---------------------------------------------------------------------------
# 3-D Attractors
# ---------------------------------------------------------------------------

@dataclass
class LorenzAttractor:
    """
    Lorenz system solved with a fixed-step Euler integrator (3-D).
    dx/dt = sigma*(y - x)
    dy/dt = x*(rho - z) - y
    dz/dt = x*y - beta*z
    """
    name: str = "Lorenz"
    sigma: float = 10.0
    rho: float = 28.0
    beta: float = 8.0 / 3.0
    dt: float = 0.005

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        dx = self.sigma * (y - x)
        dy = x * (self.rho - z) - y
        dz = x * y - self.beta * z
        return x + self.dt * dx, y + self.dt * dy, z + self.dt * dz


@dataclass
class RosslerAttractor:
    """
    Rössler system (3-D) – Euler integration.
    dx/dt = -(y + z)
    dy/dt = x + a*y
    dz/dt = b + z*(x - c)
    """
    name: str = "Rossler"
    a: float = 0.2
    b: float = 0.2
    c: float = 5.7
    dt: float = 0.02

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        dx = -(y + z)
        dy = x + self.a * y
        dz = self.b + z * (x - self.c)
        return x + self.dt * dx, y + self.dt * dy, z + self.dt * dz


@dataclass
class AizawaAttractor:
    """
    Aizawa attractor (3-D) – Euler integration.
    """
    name: str = "Aizawa"
    a: float = 0.95
    b: float = 0.7
    c: float = 0.6
    d: float = 3.5
    e: float = 0.25
    f: float = 0.1
    dt: float = 0.01

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        dx = (z - self.b) * x - self.d * y
        dy = self.d * x + (z - self.b) * y
        dz = (self.c + self.a * z - (z**3) / 3.0
              - (x**2 + y**2) * (1.0 + self.e * z)
              + self.f * z * x**3)
        return x + self.dt * dx, y + self.dt * dy, z + self.dt * dz


@dataclass
class ThomasAttractor:
    """
    Thomas' cyclically symmetric attractor (3-D).
    dx/dt = sin(y) - b*x
    dy/dt = sin(z) - b*y
    dz/dt = sin(x) - b*z
    """
    name: str = "Thomas"
    b: float = 0.208186
    dt: float = 0.05

    def iterate(
        self,
        x: NDArray[np.float64],
        y: NDArray[np.float64],
        z: NDArray[np.float64],
    ) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
        dx = np.sin(y) - self.b * x
        dy = np.sin(z) - self.b * y
        dz = np.sin(x) - self.b * z
        return x + self.dt * dx, y + self.dt * dy, z + self.dt * dz


# ---------------------------------------------------------------------------
# Utility: run an attractor for N iterations (warm-up + collect)
# ---------------------------------------------------------------------------

def iterate_attractor(
    attractor: Attractor,
    n_iter: int = 2_000_000,
    warmup: int = 1_000,
    seed: int = 0,
) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
    """
    Iterate a single trajectory for *n_iter* steps after discarding
    *warmup* transient steps.

    Returns arrays (xs, ys, zs) of length n_iter.
    """
    rng = np.random.default_rng(seed)
    x = np.array([rng.uniform(-0.1, 0.1)], dtype=np.float64)
    y = np.array([rng.uniform(-0.1, 0.1)], dtype=np.float64)
    z = np.array([rng.uniform(-0.1, 0.1)], dtype=np.float64)

    # warm-up: discard transients
    for _ in range(warmup):
        x, y, z = attractor.iterate(x, y, z)

    # collect
    xs = np.empty(n_iter, dtype=np.float64)
    ys = np.empty(n_iter, dtype=np.float64)
    zs = np.empty(n_iter, dtype=np.float64)

    for i in range(n_iter):
        x, y, z = attractor.iterate(x, y, z)
        xs[i] = x[0]
        ys[i] = y[0]
        zs[i] = z[0]

    return xs, ys, zs


def iterate_attractor_batch(
    attractor: Attractor,
    n_iter: int = 2_000_000,
    warmup: int = 1_000,
    batch_size: int = 10_000,
    seed: int = 0,
) -> tuple[NDArray[np.float64], NDArray[np.float64], NDArray[np.float64]]:
    """
    Faster than iterate_attractor: processes *batch_size* points at once
    by running *batch_size* independent trajectories simultaneously with
    NumPy vectorisation, each for n_iter // batch_size steps.

    Ideal for attractors where independent orbits stay on the same
    attractor (Clifford, de Jong, etc.).
    """
    rng = np.random.default_rng(seed)
    x = rng.uniform(-0.5, 0.5, batch_size).astype(np.float64)
    y = rng.uniform(-0.5, 0.5, batch_size).astype(np.float64)
    z = np.zeros(batch_size, dtype=np.float64)

    for _ in range(warmup):
        x, y, z = attractor.iterate(x, y, z)

    steps = n_iter // batch_size
    all_xs = np.empty(steps * batch_size, dtype=np.float64)
    all_ys = np.empty(steps * batch_size, dtype=np.float64)
    all_zs = np.empty(steps * batch_size, dtype=np.float64)

    for i in range(steps):
        x, y, z = attractor.iterate(x, y, z)
        sl = slice(i * batch_size, (i + 1) * batch_size)
        all_xs[sl] = x
        all_ys[sl] = y
        all_zs[sl] = z

    return all_xs, all_ys, all_zs


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

ATTRACTORS: dict[str, Attractor] = {
    "clifford":      CliffordAttractor(),
    "peterdejong":   PeterDeJongAttractor(),
    "gumowskimira":  GumowskiMiraAttractor(),
    "lorenz":        LorenzAttractor(),
    "rossler":       RosslerAttractor(),
    "aizawa":        AizawaAttractor(),
    "thomas":        ThomasAttractor(),
}
