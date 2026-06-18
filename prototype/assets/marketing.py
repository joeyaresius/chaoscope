"""
Chaoscope — marketing / growth-loop helpers.

This is the brain of the "Instagram loop": it turns a rendered hero image or
reel into a *closed acquisition loop* by attaching a ready-to-post caption that
carries

  1. a hook + the human-readable look description,
  2. the Play Store install link, and
  3. a `CHS1:` preset code — the exact viral mechanic the app ships with.

A viewer copies the code, opens Chaoscope, and the in-app clipboard detector
offers to paste it → they land directly on the look from the post. Pretty
picture → install → recreate → share again.

`CHS1:` codes are produced here in *byte-for-byte* the same format the Android
app uses, mirrored from
`android/app/src/main/java/com/chaoscope/app/PresetSerializer.kt`:

    name|TYPE|p0,p1,..|yaw|pitch|roll|zoom|PALETTE|STYLE|BG   (name dropped)
    -> base64url(no padding) -> "CHS1:" + that

Keep this file in sync with PresetSerializer.kt / AttractorDefs.kt if the enum
names or the field layout ever change (they'd bump the prefix to `CHS2:` etc.).
"""

from __future__ import annotations

import base64
import os
from dataclasses import dataclass, field

# Play Store listing — applicationId is com.chaoscope (android/app/build.gradle.kts).
PLAY_URL = "https://play.google.com/store/apps/details?id=com.chaoscope"

PRESET_CODE_PREFIX = "CHS1:"

# Default hashtag set appended to every caption. Order = most to least specific;
# Instagram weights the first few most, and caps the useful count well under 30.
HASHTAGS = [
    "#strangeattractor", "#chaostheory", "#generativeart", "#mathart",
    "#fractal", "#proceduralart", "#digitalart", "#lorenzattractor",
    "#chaoscope", "#abstractart", "#codeart", "#livewallpaper",
]


# ────────────────────────────────────────────────────────────────────────────
# Authoritative Android metadata (mirrors AttractorDefs.kt).
#
# Only the attractors/palettes used by the marketing scripts are listed. Each
# CHS1 code we emit is anchored on a *known-good* parameter set so that pasting
# it in-app always lands on a clean, render-worthy look — not a random POC value.
# ────────────────────────────────────────────────────────────────────────────

# Android enum name -> (UI display name, parameter symbol names).
ATTRACTOR_META: dict[str, tuple[str, list[str]]] = {
    "CLIFFORD":      ("Clifford",      ["a", "b", "c", "d", "e", "f"]),
    "PETER_DE_JONG": ("Peter de Jong", ["a", "b", "c", "d", "e", "f"]),
    "LORENZ":        ("Lorenz",        ["σ", "ρ", "β", "dt"]),
    "ROSSLER":       ("Rössler",  ["a", "b", "c", "dt"]),
    "AIZAWA":        ("Aizawa",        ["a", "b", "c", "d", "e", "f", "dt"]),
    "THOMAS":        ("Thomas",        ["b", "dt"]),
    "LORENZ_84":     ("Lorenz-84",     ["a", "b", "F", "G", "dt"]),
    "HENON":         ("Hénon",    ["a", "b"]),
    "ICON":          ("Icon",          ["λ", "α", "β", "ω"]),
}

# Android PaletteType enum name -> UI display name.
PALETTE_META: dict[str, str] = {
    "NEBULA": "Nebula", "FIRE": "Fire", "ELECTRIC": "Electric",
    "AURORA": "Aurora", "MATRIX": "Matrix", "GREYSCALE": "Grey",
}

# Python POC palette key (colormap.PALETTES) -> Android PaletteType enum name.
POC_PALETTE_TO_ENUM: dict[str, str] = {
    "nebula": "NEBULA", "fire": "FIRE", "electric": "ELECTRIC",
    "aurora": "AURORA", "matrix": "MATRIX", "greyscale": "GREYSCALE",
}

# POC attractor key -> (Android enum name, known-good default params). Lets the
# reel exporter (which works off POC attractor keys) emit a valid CHS1 code.
POC_ATTRACTOR_DEFAULTS: dict[str, tuple[str, list[float]]] = {
    "lorenz":   ("LORENZ",    [10, 28, 2.667, 0.005]),
    "rossler":  ("ROSSLER",   [0.2, 0.2, 5.7, 0.02]),
    "aizawa":   ("AIZAWA",    [0.95, 0.7, 0.6, 3.5, 0.25, 0.1, 0.01]),
    "thomas":   ("THOMAS",    [0.208186, 0.05]),
    "lorenz84": ("LORENZ_84", [0.25, 4.0, 8.0, 1.0, 0.01]),
    "icon":     ("ICON",      [-2.5, 5.0, -1.8, 1.0]),
}


# ────────────────────────────────────────────────────────────────────────────
# CHS1 preset-code encoding (mirror of PresetSerializer.presetToCode)
# ────────────────────────────────────────────────────────────────────────────

def kfloat(v: float) -> str:
    """Format a float the way Kotlin's Float.toString would for these values.

    The app decodes with toFloatOrNull, so exact byte-parity isn't required —
    only that the string parses to the same number. Integers render as "30.0",
    fractions keep up to 6 significant decimals with trailing zeros trimmed
    ("2.667", "0.208186", "0.005").
    """
    if v == int(v):
        return f"{int(v)}.0"
    s = f"{v:.6f}".rstrip("0").rstrip(".")
    return s


@dataclass
class PresetSpec:
    """The minimum to (a) emit a CHS1 code and (b) describe a look in a caption.

    Mirrors a single Android `Preset`. Camera fields are ignored in-app for the
    two 2-D maps (Gumowski-Mira, Hénon) but are still serialized.
    """
    type: str                       # Android AttractorType enum name
    params: list[float]
    palette: str                    # Android PaletteType enum name
    yaw: float = 0.0
    pitch: float = 0.0
    roll: float = 0.0
    zoom: float = 1.0
    style: str = "STANDARD"         # RenderStyle enum name (STANDARD == "Gas")
    bg: str = "BLACK"               # BgColor enum name

    def preset_string(self, name: str = "") -> str:
        return "|".join([
            name,
            self.type,
            ",".join(kfloat(p) for p in self.params),
            kfloat(self.yaw), kfloat(self.pitch), kfloat(self.roll), kfloat(self.zoom),
            self.palette,
            self.style,
            self.bg,
        ])

    def code(self) -> str:
        """`CHS1:` shareable code — the name is dropped (codes describe a look)."""
        payload = self.preset_string(name="").encode("utf-8")
        b64 = base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
        return PRESET_CODE_PREFIX + b64

    def human_caption(self) -> str:
        """Mirror of AttractorDefs.buildShareCaption (the human-readable line)."""
        disp, names = ATTRACTOR_META[self.type]
        pal = PALETTE_META.get(self.palette, self.palette.title())
        parts = " ".join(f"{n}={kfloat(v)}" for n, v in zip(names, self.params))
        line = f"{disp} attractor · {pal} palette"
        if parts:
            line += f" · {parts}"
        return line + " — made with Chaoscope"


# ────────────────────────────────────────────────────────────────────────────
# Caption assembly
# ────────────────────────────────────────────────────────────────────────────

def spec_for_poc(poc_key: str, palette: str, yaw: float = 0.0,
                 pitch: float = 0.0) -> PresetSpec | None:
    """Build a CHS1-ready spec for a reel from its POC attractor key.

    `palette` may be a POC palette key ("fire") or an Android enum ("FIRE").
    Returns None for attractors with no known Android default (caller skips the
    caption rather than emitting a broken code).
    """
    entry = POC_ATTRACTOR_DEFAULTS.get(poc_key.lower().replace("_", "").replace("-", ""))
    if entry is None:
        return None
    type_name, params = entry
    pal = POC_PALETTE_TO_ENUM.get(palette, palette.upper())
    return PresetSpec(type_name, params, pal, yaw=yaw, pitch=pitch)


def build_caption(spec: PresetSpec, hook: str, hashtags: list[str] | None = None) -> str:
    """Full Instagram caption: hook + look + install link + CHS1 code + tags."""
    tags = HASHTAGS if hashtags is None else hashtags
    return "\n".join([
        hook,
        "",
        f"\U0001f300 {spec.human_caption()}.",
        "Chaoscope is a free strange-attractor visualizer for Android.",
        "",
        f"▶️  Get it: {PLAY_URL}",
        "\U0001f3a8  Recreate this exact look — copy the code below, open "
        "Chaoscope, and tap paste:",
        spec.code(),
        "",
        " ".join(tags),
        "",
    ])


def write_caption(spec: PresetSpec, hook: str, out_dir: str, name: str) -> str:
    """Write `<name>.txt` next to the asset; return the path."""
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{name}.txt")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(build_caption(spec, hook))
    return path
