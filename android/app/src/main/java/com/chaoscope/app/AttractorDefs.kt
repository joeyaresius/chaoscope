package com.chaoscope

import android.graphics.Bitmap

// ────────────────────────────────────────────────────────────────────────────
// Attractor definitions
// ────────────────────────────────────────────────────────────────────────────

enum class AttractorType(
    val displayName: String,
    val is3D: Boolean,
    val defaultParams: FloatArray,
    val paramNames: List<String>,
    val paramRanges: List<ClosedFloatingPointRange<Float>>,
    val description: String,
    val paramHints: List<String>,
) {
    CLIFFORD(
        displayName   = "Clifford",
        is3D          = true,
        defaultParams = floatArrayOf(-1.4f, 1.6f, 1.0f, 0.7f, 1.5f, 0.5f),
        paramNames    = listOf("a", "b", "c", "d", "e", "f"),
        paramRanges   = listOf(-3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f),
        description   = "A classic 2-D iterative map lifted into 3-D. Six knobs warp how each step folds and stretches the orbit — small changes produce wildly different shapes.",
        paramHints    = listOf(
            "Horizontal sine drive — controls the main wave that bends the orbit left and right.",
            "Vertical sine drive — controls how the orbit folds up and down.",
            "Horizontal cosine drive — adds a perpendicular twist to the X axis.",
            "Vertical cosine drive — adds a perpendicular twist to the Y axis.",
            "Coupling between axes — higher values create tighter, more knotted patterns.",
            "Secondary coupling — fine-tunes the overall texture density.",
        ),
    ),
    PETER_DE_JONG(
        displayName   = "Peter de Jong",
        is3D          = true,
        defaultParams = floatArrayOf(-2.0f, -2.0f, -1.2f, 2.0f, 1.8f, -1.5f),
        paramNames    = listOf("a", "b", "c", "d", "e", "f"),
        paramRanges   = listOf(-3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f),
        description   = "Cousin of Clifford with stronger nonlinearity — produces dense filigree and shell-like layered patterns.",
        paramHints    = listOf(
            "Primary X stretch — wider values pull the orbit horizontally.",
            "Primary Y stretch — wider values pull the orbit vertically.",
            "Cross-coupling — bends the orbit diagonally.",
            "Secondary X stretch — adds finer-grained horizontal detail.",
            "Secondary Y stretch — adds finer-grained vertical detail.",
            "Final twist — controls how tightly the shape spirals.",
        ),
    ),
    GUMOWSKI_MIRA(
        displayName   = "Gumowski-Mira",
        is3D          = false,
        defaultParams = floatArrayOf(0.008f, -0.496f),
        paramNames    = listOf("a", "μ"),
        paramRanges   = listOf(-1f..1f, -1f..1f),
        description   = "A 2-D map from plasma physics. Tends toward delicate, organic curves that look like seashells or jellyfish.",
        paramHints    = listOf(
            "Damping — close to 0 keeps orbits open; further from 0 tightens loops.",
            "Nonlinearity — drives the curl. Negative values flip the spiral direction.",
        ),
    ),
    LORENZ(
        displayName   = "Lorenz",
        is3D          = true,
        defaultParams = floatArrayOf(10f, 28f, 2.667f, 0.005f),
        paramNames    = listOf("σ", "ρ", "β", "dt"),
        paramRanges   = listOf(0f..20f, 0f..50f, 0f..5f, 0.001f..0.01f),
        description   = "The famous butterfly. Edward Lorenz's 1963 weather model — the system that made \"chaos\" a household word.",
        paramHints    = listOf(
            "σ (Prandtl number) — fluid viscosity. Standard value is 10.",
            "ρ (Rayleigh number) — drives the convection. Above ~24.74 the chaos kicks in.",
            "β (aspect ratio) — controls the wing geometry. Standard is 8/3 ≈ 2.667.",
            "dt — simulation step. Smaller = smoother trajectory but slower convergence.",
        ),
    ),
    ROSSLER(
        displayName   = "Rössler",
        is3D          = true,
        defaultParams = floatArrayOf(0.2f, 0.2f, 5.7f, 0.02f),
        paramNames    = listOf("a", "b", "c", "dt"),
        paramRanges   = listOf(0f..1f, 0f..1f, 0f..10f, 0.005f..0.05f),
        description   = "A simpler cousin of Lorenz. Spirals slowly outward then snaps back — like a cosmic pinball.",
        paramHints    = listOf(
            "Inner spiral rate — how fast orbits expand on the flat plane.",
            "Lift offset — small constant that nudges the orbit into 3-D.",
            "Snap threshold — when the orbit reaches this, it folds violently. Higher = wider funnel.",
            "dt — simulation step.",
        ),
    ),
    AIZAWA(
        displayName   = "Aizawa",
        is3D          = true,
        defaultParams = floatArrayOf(0.95f, 0.7f, 0.6f, 3.5f, 0.25f, 0.1f, 0.01f),
        paramNames    = listOf("a", "b", "c", "d", "e", "f", "dt"),
        paramRanges   = listOf(0f..2f, 0f..2f, 0f..2f, 0f..5f, 0f..1f, 0f..1f, 0.005f..0.02f),
        description   = "A twisted torus. Loops wrap a central axis while drifting along it — produces shell or Möbius-like shapes.",
        paramHints    = listOf(
            "Drift along the central axis.",
            "Vertical compression of the torus.",
            "Radial pull toward the axis.",
            "Twist rate around the axis — higher = tighter coil.",
            "Asymmetry — breaks the perfect torus into more organic curls.",
            "Vertical asymmetry — same idea on the up/down axis.",
            "dt — simulation step.",
        ),
    ),
    THOMAS(
        displayName   = "Thomas",
        is3D          = true,
        defaultParams = floatArrayOf(0.208186f, 0.05f),
        paramNames    = listOf("b", "dt"),
        paramRanges   = listOf(0.05f..0.5f, 0.01f..0.1f),
        description   = "Cyclically symmetric — the same equation in x, y, and z. Looks like a tangled ball of yarn.",
        paramHints    = listOf(
            "Damping — lower values make the orbit more chaotic. Below ~0.21 it becomes hyperchaotic.",
            "dt — simulation step.",
        ),
    ),
    CHAOTIC_FLOW(
        displayName   = "Chaotic Flow",
        is3D          = true,
        defaultParams = floatArrayOf(3f, 2.7f, 1.7f, 2f, 9f, 0.01f),
        paramNames    = listOf("a", "b", "c", "d", "e", "dt"),
        paramRanges   = listOf(1f..5f, 1f..5f, 0.5f..3f, 1f..4f, 5f..15f, 0.001f..0.02f),
        description   = "A generic chaotic flow with many control knobs — wide range of forms, from elegant ribbons to dense storms.",
        paramHints    = listOf(
            "Primary drive on the X axis.",
            "Primary drive on the Y axis.",
            "Coupling between axes.",
            "Vertical lift.",
            "Forcing strength — higher creates more violent dynamics.",
            "dt — simulation step.",
        ),
    ),
    ICON(
        displayName   = "Icon",
        is3D          = false,
        defaultParams = floatArrayOf(-2.5f, 5.0f, -1.8f, 1.0f),
        paramNames    = listOf("λ", "α", "β", "ω"),
        paramRanges   = listOf(-4f..-1f, 1f..8f, -3f..0.5f, 0.5f..2f),
        description   = "Symmetric icons (Field & Golubitsky). Uses complex polynomials to produce mandala-like patterns with rotational symmetry.",
        paramHints    = listOf(
            "λ — overall contraction. More negative pulls orbits inward.",
            "α — radial drive. Higher gives sharper rays.",
            "β — angular distortion. Bends the symmetry axes.",
            "ω — rotation rate. Sets the n-fold symmetry of the final pattern.",
        ),
    ),
    IFS(
        displayName   = "Barnsley Fern",
        is3D          = false,
        defaultParams = floatArrayOf(1.0f, 0.0f),
        paramNames    = listOf("width", "lean"),
        paramRanges   = listOf(0.7f..1.3f, -0.2f..0.2f),
        description   = "Iterated function system. Four affine maps applied at random produce a fractal fern, leaf by leaf.",
        paramHints    = listOf(
            "Width — fattens or thins each leaflet.",
            "Lean — biases the fern left or right of vertical.",
        ),
    ),
    JULIA(
        displayName   = "Julia",
        is3D          = false,
        defaultParams = floatArrayOf(-0.7f, 0.27f),
        paramNames    = listOf("c_re", "c_im"),
        paramRanges   = listOf(-2f..2f, -2f..2f),
        description   = "Julia set for z² + c. The shape lives at c = c_re + c_im·i — values near the edge of the Mandelbrot set yield the richest detail.",
        paramHints    = listOf(
            "Real part of c. Try values around −0.7 to −0.8 for classic dragons.",
            "Imaginary part of c. Small values give connected shapes; large values shatter into dust.",
        ),
    ),
    PICKOVER(
        displayName   = "Pickover",
        is3D          = true,
        defaultParams = floatArrayOf(2.24f, 0.43f, -0.65f, -2.43f),
        paramNames    = listOf("a", "b", "c", "d"),
        paramRanges   = listOf(-3f..3f, -3f..3f, -3f..3f, -3f..3f),
        description   = "Clifford Pickover's iconic strange attractor — produces wing- and shell-like 3-D forms with subtle layering.",
        paramHints    = listOf(
            "X stretch — how far the orbit reaches sideways.",
            "Y stretch — vertical span of the shape.",
            "Cross-coupling — bends the wings.",
            "Final twist — sets the overall handedness.",
        ),
    ),
}

// ────────────────────────────────────────────────────────────────────────────
// Palette definitions
// ────────────────────────────────────────────────────────────────────────────

enum class PaletteType(val displayName: String, val description: String) {
    NEBULA   ("Nebula",    "Deep blues and purples — cosmic and quiet."),
    FIRE     ("Fire",      "Warm reds, oranges and yellow — molten."),
    ELECTRIC ("Electric",  "Cool cyan and bright blue — high-voltage."),
    AURORA   ("Aurora",    "Greens, teals and violets — northern lights."),
    MATRIX   ("Matrix",    "Mono-green on black — terminal nostalgia."),
    GREYSCALE("Grey",      "Pure luminance — focus on form, not colour."),
    CUSTOM   ("Custom",    "Your own colour stops — tap Edit to customise."),
}

/** A gradient colour stop used by the custom palette. r/g/b are in 0..1. */
data class ColorStop(val pos: Float, val r: Float, val g: Float, val b: Float)

/** Default custom stops mirror the Nebula palette so the starting point looks good. */
val defaultCustomStops: List<ColorStop> = listOf(
    ColorStop(0.00f, 0f,         0f,         0f        ),
    ColorStop(0.25f, 0f,         20f / 255f, 80f / 255f),
    ColorStop(0.50f, 20f / 255f, 80f / 255f, 200f/ 255f),
    ColorStop(0.75f, 120f/ 255f, 180f/ 255f, 1f        ),
    ColorStop(1.00f, 1f,         1f,         1f        ),
)

// ────────────────────────────────────────────────────────────────────────────
// Render style
// ────────────────────────────────────────────────────────────────────────────

enum class RenderStyle(val displayName: String, val description: String) {
    STANDARD("Standard", "Balanced histogram render — a good default."),
    GAS     ("Gas",      "Sparse low-density points — like cosmic dust or starfields."),
    LIQUID  ("Liquid",   "Smooth flowing streams — soft, painterly washes."),
    PLASMA  ("Plasma",   "High-energy glow with bright hot cores."),
    SOLID   ("Solid",    "Densely filled regions — bold, poster-like."),
}

// ────────────────────────────────────────────────────────────────────────────
// Background colour presets
// ────────────────────────────────────────────────────────────────────────────

enum class BgColor(val displayName: String, val argb: Int) {
    BLACK     ("Black",    0xFF000000.toInt()),
    DEEP_SPACE("Space",    0xFF060618.toInt()),
    MIDNIGHT  ("Midnight", 0xFF0A0A2A.toInt()),
    DARK_TEAL ("Teal",     0xFF001A1A.toInt()),
    DARK_GREEN("Forest",   0xFF001200.toInt()),
    DARK_RED  ("Crimson",  0xFF1A0000.toInt()),
    DARK_PLUM ("Plum",     0xFF12001A.toInt()),
    WHITE     ("White",    0xFFFFFFFF.toInt()),
}

// ────────────────────────────────────────────────────────────────────────────
// UI state
// ────────────────────────────────────────────────────────────────────────────

data class UiState(
    val bitmap: Bitmap?              = null,
    val isRendering: Boolean         = false,
    val isRetrying: Boolean          = false,
    val renderFailedMessage: String? = null,
    val attractorType: AttractorType = AttractorType.CLIFFORD,
    val params: List<Float>          = AttractorType.CLIFFORD.defaultParams.toList(),
    val palette: PaletteType         = PaletteType.NEBULA,
    val customStops: List<ColorStop> = defaultCustomStops,
    val yaw: Float                   = 0f,
    val pitch: Float                 = 0f,
    val roll: Float                  = 0f,
    val zoom: Float                  = 1f,
    val gamma: Float                 = 1f,
    val exportDone: Boolean          = false,
    val exportError: String?         = null,
    val lastExportUri: String?       = null,
    val renderStyle: RenderStyle     = RenderStyle.STANDARD,
    val bgColor: BgColor             = BgColor.BLACK,
)
