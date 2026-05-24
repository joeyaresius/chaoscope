"""
Chaoscope Clone - Color / Gradient System
Phase 4: Maps a normalised density in [0, 1] to an RGBA image.

Gradient stops are defined as (position, R, G, B) tuples where
position is in [0, 1].  The special background colour is applied
where density == 0 (pixels with zero hits).

Includes several preset palettes that mimic the original Chaoscope look.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Sequence, Tuple

import numpy as np
from numpy.typing import NDArray


# ---------------------------------------------------------------------------
# Gradient stop / palette types
# ---------------------------------------------------------------------------

# A colour stop: (position_0_1, R_0_255, G_0_255, B_0_255)
ColorStop = Tuple[float, int, int, int]


@dataclass
class Palette:
    """
    A linear multi-stop gradient palette.

    Parameters
    ----------
    stops      : list of (position, R, G, B).  Must include stops at
                 position 0.0 and 1.0.
    bg_color   : (R, G, B) background colour for zero-density pixels.
    name       : human-readable label.
    """
    stops: List[ColorStop]
    bg_color: Tuple[int, int, int] = (0, 0, 0)
    name: str = "custom"

    def _build_lut(self, size: int = 1024) -> NDArray[np.uint8]:
        """Build a look-up table of *size* RGB triplets."""
        lut = np.zeros((size, 3), dtype=np.float64)
        positions = np.linspace(0.0, 1.0, size)

        sorted_stops = sorted(self.stops, key=lambda s: s[0])

        for channel in range(3):
            xs = [s[0] for s in sorted_stops]
            ys = [s[channel + 1] for s in sorted_stops]
            lut[:, channel] = np.interp(positions, xs, ys)

        return np.clip(lut, 0, 255).astype(np.uint8)

    def apply(
        self,
        density: NDArray[np.float64],
        alpha: bool = True,
    ) -> NDArray[np.uint8]:
        """
        Map a (H, W) density array in [0, 1] to an (H, W, 3 or 4) RGBA
        image.

        Zero-density pixels receive ``bg_color``.
        """
        lut = self._build_lut()
        lut_size = lut.shape[0]

        # index into LUT
        idx = np.clip((density * (lut_size - 1)).astype(np.int32), 0, lut_size - 1)
        rgb = lut[idx]  # (H, W, 3)

        # apply background where density == 0
        zero_mask = density == 0.0
        rgb[zero_mask] = self.bg_color

        if not alpha:
            return rgb

        # build alpha channel: fully transparent where density == 0
        a = np.where(zero_mask, 0, 255).astype(np.uint8)
        return np.concatenate([rgb, a[:, :, np.newaxis]], axis=-1)


# ---------------------------------------------------------------------------
# Built-in palettes
# ---------------------------------------------------------------------------

PALETTES: dict[str, Palette] = {

    # Classic blue-white nebula (closest to original Chaoscope default)
    "nebula": Palette(
        name="nebula",
        stops=[
            (0.00,   0,   0,   0),
            (0.25,   0,  20,  80),
            (0.50,  20,  80, 200),
            (0.75, 120, 180, 255),
            (1.00, 255, 255, 255),
        ],
        bg_color=(0, 0, 0),
    ),

    # Hot embers: dark red -> orange -> white
    "fire": Palette(
        name="fire",
        stops=[
            (0.00,   0,   0,   0),
            (0.30,  80,   0,   0),
            (0.60, 200,  60,   0),
            (0.80, 255, 160,  20),
            (1.00, 255, 255, 200),
        ],
        bg_color=(0, 0, 0),
    ),

    # Cyan electric
    "electric": Palette(
        name="electric",
        stops=[
            (0.00,   0,   0,   0),
            (0.20,   0,  40,  60),
            (0.50,   0, 180, 200),
            (0.80,  80, 240, 255),
            (1.00, 255, 255, 255),
        ],
        bg_color=(0, 0, 0),
    ),

    # Violet aurora
    "aurora": Palette(
        name="aurora",
        stops=[
            (0.00,   0,   0,   0),
            (0.25,  20,   0,  60),
            (0.50,  80,   0, 160),
            (0.75, 180,  80, 240),
            (1.00, 240, 200, 255),
        ],
        bg_color=(0, 0, 0),
    ),

    # Green matrix
    "matrix": Palette(
        name="matrix",
        stops=[
            (0.00,   0,   0,   0),
            (0.30,   0,  40,   0),
            (0.60,   0, 160,  20),
            (0.85,  80, 240,  80),
            (1.00, 200, 255, 200),
        ],
        bg_color=(0, 0, 0),
    ),

    # Greyscale (useful for debugging)
    "greyscale": Palette(
        name="greyscale",
        stops=[
            (0.00,   0,   0,   0),
            (1.00, 255, 255, 255),
        ],
        bg_color=(0, 0, 0),
    ),

    # Inverted greyscale (bright background)
    "greyscale_inv": Palette(
        name="greyscale_inv",
        stops=[
            (0.00, 255, 255, 255),
            (1.00,   0,   0,   0),
        ],
        bg_color=(255, 255, 255),
    ),
}


def get_palette(name: str) -> Palette:
    """Return a named palette, raising KeyError with a helpful message."""
    try:
        return PALETTES[name.lower()]
    except KeyError:
        available = ", ".join(PALETTES)
        raise KeyError(f"Unknown palette '{name}'. Available: {available}") from None


def colorize(
    density: NDArray[np.float64],
    palette: str | Palette = "nebula",
    alpha: bool = True,
) -> NDArray[np.uint8]:
    """
    Convenience wrapper: apply a palette name or Palette object to a
    density array and return an RGBA (or RGB) uint8 image.
    """
    if isinstance(palette, str):
        palette = get_palette(palette)
    return palette.apply(density, alpha=alpha)
