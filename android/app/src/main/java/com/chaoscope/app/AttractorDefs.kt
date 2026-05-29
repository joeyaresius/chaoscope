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
        paramRanges   = listOf(-4f..-1f, 1f..8f, -3f..0.5f, 0.1f..2f),
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
        is3D          = true,
        defaultParams = floatArrayOf(1.0f, 0.0f, 0.2f),
        paramNames    = listOf("width", "lean", "twist"),
        paramRanges   = listOf(0.7f..1.3f, -0.2f..0.2f, 0f..0.6f),
        description   = "Iterated function system. Four affine maps applied at random produce a fractal fern; the twist knob spirals the fronds out of the plane into 3-D.",
        paramHints    = listOf(
            "Width — fattens or thins each leaflet.",
            "Lean — biases the fern left or right of vertical.",
            "Twist — rotates each frond out of plane. 0 is the flat 2-D fern; higher coils it into 3-D.",
        ),
    ),
    JULIA(
        displayName   = "Julia",
        is3D          = true,
        defaultParams = floatArrayOf(-0.2f, 0.6f, 0.3f),
        paramNames    = listOf("c_re", "c_im", "c_j"),
        paramRanges   = listOf(-2f..2f, -2f..2f, -1f..1f),
        description   = "Quaternion Julia set for q² + c, rendered as a 3-D surface. The j-term (c_j) drives the depth — set it to 0 for the classic flat 2-D Julia.",
        paramHints    = listOf(
            "Real (scalar) part of c.",
            "Imaginary (i) part of c. Values near the Mandelbrot edge give the richest detail.",
            "j part of c — the third dimension. 0 collapses to a flat 2-D Julia; larger values open it into a 3-D body.",
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
    HALVORSEN(
        displayName   = "Halvorsen",
        is3D          = true,
        defaultParams = floatArrayOf(1.89f, 0.005f),
        paramNames    = listOf("a", "dt"),
        paramRanges   = listOf(1.0f..2.5f, 0.002f..0.01f),
        description   = "Cyclically symmetric — the same equation cycled through x, y and z. Folds into interlocking torus-knot loops, like a 3-D pretzel.",
        paramHints    = listOf(
            "Damping — lower values loosen the loops; near 1.4 the orbit barely stays bound.",
            "dt — simulation step. Smaller = smoother, slower convergence.",
        ),
    ),
    BURKE_SHAW(
        displayName   = "Burke-Shaw",
        is3D          = true,
        defaultParams = floatArrayOf(10f, 4.272f, 0.005f),
        paramNames    = listOf("s", "v", "dt"),
        paramRanges   = listOf(5f..15f, 1f..8f, 0.002f..0.01f),
        description   = "A fast double-spiral that winds into a tight tornado of nested shells — dense, galaxy-like swirls.",
        paramHints    = listOf(
            "Coupling strength — drives the swirl rate. Higher winds the spiral tighter.",
            "Vertical forcing — lifts and stretches the funnel along its axis.",
            "dt — simulation step.",
        ),
    ),
    SPROTT_B(
        displayName   = "Sprott-B",
        is3D          = true,
        defaultParams = floatArrayOf(1f, 1f, 0.02f),
        paramNames    = listOf("a", "b", "dt"),
        paramRanges   = listOf(0.5f..2f, 0.5f..2f, 0.005f..0.04f),
        description   = "One of Sprott's minimal chaotic systems — just two terms, yet it traces a broad swirling disc. Elegant and beginner-friendly.",
        paramHints    = listOf(
            "Coupling — scales the y·z drive that feeds the swirl.",
            "Feedback — scales the x·y term that closes the loop. Reshapes the disc.",
            "dt — simulation step.",
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
    SPECTRUM ("Spectrum",  "Full hue wheel — violet through cyan, green, yellow to orange, dark-to-bright."),
    SUNSET   ("Sunset",    "Deep purple to magenta, orange and golden cream — warm and dramatic."),
    ICE      ("Ice",       "Navy to steel blue, ice blue and white — cold and crystalline."),
    NEON     ("Neon",      "Hot pink to electric green — high-contrast, vibrant."),
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

private fun stop(pos: Float, r: Int, g: Int, b: Int) =
    ColorStop(pos, r / 255f, g / 255f, b / 255f)

/** Colour stops for the built-in palettes (mirrors the LUTs in renderer.cpp), so
 *  the custom editor can load one as a starting point. CUSTOM is excluded. */
val builtInPaletteStops: Map<PaletteType, List<ColorStop>> = mapOf(
    PaletteType.NEBULA to listOf(
        stop(0.00f, 0, 0, 0), stop(0.25f, 0, 20, 80),
        stop(0.50f, 20, 80, 200), stop(0.75f, 120, 180, 255), stop(1.00f, 255, 255, 255),
    ),
    PaletteType.FIRE to listOf(
        stop(0.00f, 0, 0, 0), stop(0.30f, 80, 0, 0),
        stop(0.60f, 200, 60, 0), stop(0.80f, 255, 160, 20), stop(1.00f, 255, 255, 200),
    ),
    PaletteType.ELECTRIC to listOf(
        stop(0.00f, 0, 0, 0), stop(0.20f, 0, 40, 60),
        stop(0.50f, 0, 180, 200), stop(0.80f, 80, 240, 255), stop(1.00f, 255, 255, 255),
    ),
    PaletteType.AURORA to listOf(
        stop(0.00f, 0, 0, 0), stop(0.25f, 20, 0, 60),
        stop(0.50f, 80, 0, 160), stop(0.75f, 180, 80, 240), stop(1.00f, 240, 200, 255),
    ),
    PaletteType.MATRIX to listOf(
        stop(0.00f, 0, 0, 0), stop(0.30f, 0, 40, 0),
        stop(0.60f, 0, 160, 20), stop(0.85f, 80, 240, 80), stop(1.00f, 200, 255, 200),
    ),
    PaletteType.GREYSCALE to listOf(
        stop(0.00f, 0, 0, 0), stop(1.00f, 255, 255, 255),
    ),
    PaletteType.SPECTRUM to listOf(
        stop(0.00f,   0,   0,   0), stop(0.15f,  80,   0, 160),
        stop(0.35f,   0,  40, 220), stop(0.50f,   0, 200, 200),
        stop(0.65f,   0, 200,  60), stop(0.80f, 220, 220,   0),
        stop(0.90f, 255, 130,   0), stop(1.00f, 255, 240, 220),
    ),
    PaletteType.SUNSET to listOf(
        stop(0.00f,   0,   0,   0), stop(0.20f,  60,   0,  80),
        stop(0.40f, 180,   0, 120), stop(0.65f, 255,  80,  20),
        stop(0.85f, 255, 200,  50), stop(1.00f, 255, 240, 200),
    ),
    PaletteType.ICE to listOf(
        stop(0.00f,   0,   0,   0), stop(0.20f,   0,  20,  60),
        stop(0.45f,   0,  80, 140), stop(0.70f,  80, 200, 240),
        stop(0.90f, 200, 240, 255), stop(1.00f, 255, 255, 255),
    ),
    PaletteType.NEON to listOf(
        stop(0.00f,   0,   0,   0), stop(0.25f,  60,   0,  60),
        stop(0.45f, 255,   0, 180), stop(0.65f,   0, 255, 120),
        stop(0.85f, 200, 255,  80), stop(1.00f, 255, 255, 255),
    ),
)

// ────────────────────────────────────────────────────────────────────────────
// Render style
// ────────────────────────────────────────────────────────────────────────────

enum class RenderStyle(val displayName: String, val description: String) {
    STANDARD("Gas",    "Log-histogram accumulation — the classic Chaoscope mode."),
    GAS     ("Dust",   "4th-root sparse points — diffuse starfield or dust-cloud look."),
    LIQUID  ("Liquid", "Gas with z-buffer depth — near points bright, far points dim."),
    PLASMA  ("Plasma", "Cyclic colour bands — high-frequency striped patterns."),
    SOLID   ("Solid",  "Hard threshold silhouette — bold, poster-like fills."),
    LIGHT   ("Light",  "Per-point orbit speed × curvature — warm core, cool periphery, bright at bends."),
}

// ────────────────────────────────────────────────────────────────────────────
// Performance: render detail + preview density
// ────────────────────────────────────────────────────────────────────────────

/** How many points to iterate. More = denser, smoother, slower. Iteration count
 *  scales time, not memory (the histogram size is fixed by resolution). */
enum class RenderQuality(
    val displayName: String,
    val previewIterations: Long,
    val hdIterations: Long,
    val description: String,
) {
    DRAFT   ("Draft",    1_000_000L,  15_000_000L, "Fastest — fewer points, rougher detail."),
    STANDARD("Standard", 2_000_000L,  50_000_000L, "Balanced default."),
    HIGH    ("High",     5_000_000L, 120_000_000L, "Denser and smoother — slower render."),
    ULTRA   ("Ultra",   10_000_000L, 300_000_000L, "Maximum detail — an HD render can take a while."),
}

/** How many dots the live rotation preview draws. Lower if rotation feels sluggish. */
enum class PreviewDensity(val displayName: String, val dots: Int, val description: String) {
    LOW   ("Low",  20_000,  "Lightest — smoothest rotation on slower phones."),
    MEDIUM("Med",  60_000,  "Balanced default."),
    HIGH  ("High", 120_000, "Most detail while rotating — best on fast phones."),
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
    /** User-defined colour; actual ARGB lives in [UiState.customBgArgb]. */
    CUSTOM    ("Custom",   0xFF000000.toInt()),
}

// ────────────────────────────────────────────────────────────────────────────
// Curated presets
// ────────────────────────────────────────────────────────────────────────────

// ────────────────────────────────────────────────────────────────────────────
// Animation keyframe
// ────────────────────────────────────────────────────────────────────────────

/**
 * Lightweight snapshot of the params + camera that get linearly interpolated
 * between two keyframes for the video-export animation.  Palette, style,
 * bg and other "look" settings are taken from the current UI state at export
 * time and stay constant across the animation.
 */
data class AnimKeyframe(
    val params: List<Float>,
    val yaw:    Float,
    val pitch:  Float,
    val roll:   Float,
    val zoom:   Float,
)

/**
 * A hand-picked starting point: attractor + parameters + camera + look.
 * Camera fields are ignored for 2-D attractors (forced to 0 on apply).
 */
data class Preset(
    val name: String,
    val type: AttractorType,
    val params: List<Float>,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val zoom: Float = 1f,
    val palette: PaletteType = PaletteType.NEBULA,
    val renderStyle: RenderStyle = RenderStyle.STANDARD,
    val bgColor: BgColor = BgColor.BLACK,
)

/**
 * Curated presets per attractor. Each list leads with a "signature" preset built
 * on the attractor's known-good defaults, followed by appearance/parameter variants.
 */
val CURATED_PRESETS: Map<AttractorType, List<Preset>> = mapOf(
    AttractorType.CLIFFORD to listOf(
        Preset("Classic",  AttractorType.CLIFFORD, listOf(-1.4f, 1.6f, 1.0f, 0.7f, 1.5f, 0.5f), palette = PaletteType.NEBULA),
        Preset("Filigree", AttractorType.CLIFFORD, listOf(-1.7f, 1.8f, 0.9f, 0.6f, 2.0f, 0.7f), yaw = 35f, pitch = -20f, palette = PaletteType.AURORA, renderStyle = RenderStyle.GAS),
        Preset("Ember",    AttractorType.CLIFFORD, listOf(-1.2f, 1.4f, 1.1f, 0.8f, 1.0f, 0.4f), yaw = -25f, pitch = 15f, palette = PaletteType.FIRE, renderStyle = RenderStyle.PLASMA),
    ),
    AttractorType.PETER_DE_JONG to listOf(
        Preset("Shells",   AttractorType.PETER_DE_JONG, listOf(-2.0f, -2.0f, -1.2f, 2.0f, 1.8f, -1.5f), palette = PaletteType.ELECTRIC),
        Preset("Storm",    AttractorType.PETER_DE_JONG, listOf(-2.4f, -1.6f, -1.0f, 2.2f, 2.0f, -1.8f), yaw = 40f, pitch = 25f, palette = PaletteType.NEBULA, renderStyle = RenderStyle.LIQUID),
        Preset("Filament", AttractorType.PETER_DE_JONG, listOf(-1.8f, -2.2f, -1.4f, 1.7f, 1.5f, -1.2f), palette = PaletteType.AURORA),
    ),
    AttractorType.GUMOWSKI_MIRA to listOf(
        Preset("Jellyfish", AttractorType.GUMOWSKI_MIRA, listOf(0.008f, -0.496f), palette = PaletteType.NEBULA),
        Preset("Coral",     AttractorType.GUMOWSKI_MIRA, listOf(0.02f, -0.7f), palette = PaletteType.FIRE, renderStyle = RenderStyle.LIQUID),
        Preset("Bloom",     AttractorType.GUMOWSKI_MIRA, listOf(0.005f, -0.3f), palette = PaletteType.AURORA),
    ),
    AttractorType.LORENZ to listOf(
        Preset("Butterfly", AttractorType.LORENZ, listOf(10f, 28f, 2.667f, 0.005f), yaw = 25f, pitch = 15f, palette = PaletteType.NEBULA),
        Preset("Side Wing", AttractorType.LORENZ, listOf(10f, 28f, 2.667f, 0.005f), yaw = 90f, palette = PaletteType.FIRE),
        Preset("Storm Cell", AttractorType.LORENZ, listOf(10f, 40f, 2.667f, 0.005f), yaw = 35f, pitch = 20f, palette = PaletteType.ELECTRIC, renderStyle = RenderStyle.PLASMA),
    ),
    AttractorType.ROSSLER to listOf(
        Preset("Spiral",  AttractorType.ROSSLER, listOf(0.2f, 0.2f, 5.7f, 0.02f), yaw = 20f, pitch = 30f, palette = PaletteType.AURORA),
        Preset("Funnel",  AttractorType.ROSSLER, listOf(0.2f, 0.2f, 8.0f, 0.02f), yaw = 45f, pitch = 10f, palette = PaletteType.FIRE, renderStyle = RenderStyle.LIQUID),
        Preset("Pinball", AttractorType.ROSSLER, listOf(0.1f, 0.1f, 5.7f, 0.02f), pitch = 60f, palette = PaletteType.ELECTRIC, renderStyle = RenderStyle.GAS),
    ),
    AttractorType.AIZAWA to listOf(
        Preset("Torus",  AttractorType.AIZAWA, listOf(0.95f, 0.7f, 0.6f, 3.5f, 0.25f, 0.1f, 0.01f), yaw = 30f, pitch = 25f, palette = PaletteType.NEBULA),
        Preset("Möbius", AttractorType.AIZAWA, listOf(0.95f, 0.7f, 0.6f, 3.5f, 0.4f, 0.1f, 0.01f), yaw = 60f, pitch = 10f, palette = PaletteType.AURORA, renderStyle = RenderStyle.LIQUID),
        Preset("Coil",   AttractorType.AIZAWA, listOf(0.95f, 0.7f, 0.6f, 3.5f, 0.25f, 0.1f, 0.01f), yaw = 80f, pitch = 5f, palette = PaletteType.FIRE),
    ),
    AttractorType.THOMAS to listOf(
        Preset("Yarn Ball",  AttractorType.THOMAS, listOf(0.208186f, 0.05f), yaw = 30f, pitch = 30f, palette = PaletteType.ELECTRIC),
        Preset("Hyperchaos", AttractorType.THOMAS, listOf(0.15f, 0.05f), yaw = 45f, pitch = 20f, palette = PaletteType.NEBULA, renderStyle = RenderStyle.GAS),
        Preset("Lattice",    AttractorType.THOMAS, listOf(0.19f, 0.05f), palette = PaletteType.AURORA),
    ),
    AttractorType.CHAOTIC_FLOW to listOf(
        Preset("Ribbon",  AttractorType.CHAOTIC_FLOW, listOf(3f, 2.7f, 1.7f, 2f, 9f, 0.01f), yaw = 25f, pitch = 20f, palette = PaletteType.AURORA),
        Preset("Tempest", AttractorType.CHAOTIC_FLOW, listOf(3.5f, 3.0f, 1.7f, 2f, 11f, 0.01f), yaw = 40f, pitch = 15f, palette = PaletteType.FIRE),
        Preset("Mist",    AttractorType.CHAOTIC_FLOW, listOf(2.5f, 2.4f, 1.5f, 2f, 7f, 0.01f), yaw = 10f, pitch = 35f, palette = PaletteType.ELECTRIC, renderStyle = RenderStyle.GAS),
    ),
    AttractorType.ICON to listOf(
        Preset("Mandala",   AttractorType.ICON, listOf(-2.5f, 5.0f, -1.8f, 1.0f), palette = PaletteType.NEBULA),
        Preset("Sunwheel",  AttractorType.ICON, listOf(-2.0f, 6.0f, -1.5f, 1.2f), palette = PaletteType.FIRE, renderStyle = RenderStyle.PLASMA),
        Preset("Snowflake", AttractorType.ICON, listOf(-2.34f, 2.0f, 0.2f, 0.1f), palette = PaletteType.ELECTRIC),
    ),
    AttractorType.IFS to listOf(
        Preset("Fern",      AttractorType.IFS, listOf(1.0f, 0.0f, 0.0f), palette = PaletteType.MATRIX),
        Preset("Spiral",    AttractorType.IFS, listOf(1.0f, 0.0f, 0.3f), yaw = 30f, pitch = 10f, palette = PaletteType.AURORA),
        Preset("Broadleaf", AttractorType.IFS, listOf(1.1f, 0.0f, 0.15f), yaw = 20f, pitch = 10f, palette = PaletteType.FIRE, renderStyle = RenderStyle.LIQUID),
    ),
    AttractorType.JULIA to listOf(
        Preset("Dragon", AttractorType.JULIA, listOf(-0.7f, 0.27f, 0.0f), palette = PaletteType.NEBULA),
        Preset("Bud",    AttractorType.JULIA, listOf(-0.2f, 0.6f, 0.2f), yaw = 30f, pitch = 20f, palette = PaletteType.ELECTRIC),
        Preset("Bulb",   AttractorType.JULIA, listOf(0.0f, 0.5f, 0.5f), yaw = 30f, pitch = 20f, palette = PaletteType.AURORA),
    ),
    AttractorType.PICKOVER to listOf(
        Preset("Wings", AttractorType.PICKOVER, listOf(2.24f, 0.43f, -0.65f, -2.43f), yaw = 25f, pitch = 20f, palette = PaletteType.NEBULA),
        Preset("Conch", AttractorType.PICKOVER, listOf(2.1f, 0.5f, -0.7f, -2.3f), yaw = 40f, pitch = 15f, palette = PaletteType.FIRE),
        Preset("Veil",  AttractorType.PICKOVER, listOf(2.3f, 0.35f, -0.6f, -2.5f), yaw = 15f, pitch = 35f, palette = PaletteType.AURORA),
    ),
    AttractorType.HALVORSEN to listOf(
        Preset("Pretzel", AttractorType.HALVORSEN, listOf(1.89f, 0.005f), yaw = 30f, pitch = 20f, palette = PaletteType.AURORA),
        Preset("Knot",    AttractorType.HALVORSEN, listOf(1.89f, 0.005f), yaw = 60f, pitch = 10f, palette = PaletteType.NEBULA, renderStyle = RenderStyle.GAS),
        Preset("Coil",    AttractorType.HALVORSEN, listOf(1.89f, 0.005f), yaw = 90f, pitch = 5f,  palette = PaletteType.FIRE,   renderStyle = RenderStyle.PLASMA),
    ),
    AttractorType.BURKE_SHAW to listOf(
        Preset("Galaxy",    AttractorType.BURKE_SHAW, listOf(10f, 4.272f, 0.005f), yaw = 25f, pitch = 25f, palette = PaletteType.ELECTRIC),
        Preset("Tornado",   AttractorType.BURKE_SHAW, listOf(10f, 4.272f, 0.005f), yaw = 90f, pitch = 0f,  palette = PaletteType.FIRE,   renderStyle = RenderStyle.LIQUID),
        Preset("Whirlpool", AttractorType.BURKE_SHAW, listOf(10f, 4.272f, 0.005f), yaw = 10f, pitch = 35f, palette = PaletteType.AURORA, renderStyle = RenderStyle.GAS),
    ),
    AttractorType.SPROTT_B to listOf(
        Preset("Disc",   AttractorType.SPROTT_B, listOf(1f, 1f, 0.02f), yaw = 30f, pitch = 20f, palette = PaletteType.NEBULA),
        Preset("Halo",   AttractorType.SPROTT_B, listOf(1f, 1f, 0.02f), yaw = 0f,  pitch = 60f, palette = PaletteType.AURORA),
        Preset("Vortex", AttractorType.SPROTT_B, listOf(1f, 1f, 0.02f), yaw = 60f, pitch = 10f, palette = PaletteType.FIRE, renderStyle = RenderStyle.LIQUID),
    ),
)

/** Curated presets for this attractor (empty if none defined). */
val AttractorType.presets: List<Preset>
    get() = CURATED_PRESETS[this].orEmpty()

// ────────────────────────────────────────────────────────────────────────────
// Animation mode
// ────────────────────────────────────────────────────────────────────────────

/**
 * Controls which video-export algorithm is used.
 *
 * MORPH       — interpolate between two manually set keyframes (params + camera).
 * ORBIT_TRACE — render the attractor orbit as cumulative coloured dots; each
 *               frame shows more of the path, producing a spirograph-like trace.
 * PARAM_SWEEP — auto-varies all parameters from the current state to a randomly
 *               generated target and morphs between them; no keyframes needed.
 */
enum class AnimMode { MORPH, ORBIT_TRACE, PARAM_SWEEP }

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
    val depthCue: Float              = 0.5f,
    val fullRange: Boolean           = true,
    val exportDone: Boolean          = false,
    val exportError: String?         = null,
    val lastExportUri: String?       = null,
    val renderStyle: RenderStyle     = RenderStyle.STANDARD,
    val bgColor: BgColor             = BgColor.BLACK,
    val customBgArgb: Int            = 0xFF1A0028.toInt(),
    val renderQuality: RenderQuality = RenderQuality.STANDARD,
    val previewDensity: PreviewDensity = PreviewDensity.MEDIUM,
    val transparentBg: Boolean       = false,
    val wallpaperDone: Boolean       = false,
    val wallpaperError: String?      = null,
    // ── Animation export ──────────────────────────────────────────────────
    val animMode: AnimMode           = AnimMode.MORPH,
    val keyframeA: AnimKeyframe?     = null,
    val keyframeB: AnimKeyframe?     = null,
    val animFrames: Int              = 30,
    val animPingPong: Boolean        = false,
    val isExportingVideo: Boolean    = false,
    val videoExportProgress: Int     = 0,
    val videoExportTotal: Int        = 0,
    val videoExportError: String?    = null,
    val videoExportUri: String?      = null,
) {
    /** Resolves the effective background ARGB — custom value when [bgColor] == CUSTOM. */
    val effectiveBgArgb: Int get() =
        if (bgColor == BgColor.CUSTOM) customBgArgb else bgColor.argb
}

// ────────────────────────────────────────────────────────────────────────────
// Sharing
// ────────────────────────────────────────────────────────────────────────────

/** Format a parameter value compactly: trim trailing zeros, locale-independent. */
private fun formatParamValue(v: Float): String =
    String.format(java.util.Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')

/**
 * Human-readable caption for sharing / clipboard, e.g.
 * "Lorenz attractor · Nebula palette · σ=10 ρ=28 β=2.667 — made with Chaoscope".
 */
fun buildShareCaption(
    type: AttractorType,
    palette: PaletteType,
    params: List<Float>,
): String {
    val paramStr = type.paramNames
        .zip(params)
        .joinToString(" ") { (name, v) -> "$name=${formatParamValue(v)}" }
    val paletteName = if (palette == PaletteType.CUSTOM) "Custom" else palette.displayName
    return buildString {
        append(type.displayName)
        append(" attractor · ")
        append(paletteName)
        append(" palette")
        if (paramStr.isNotBlank()) {
            append(" · ")
            append(paramStr)
        }
        append(" — made with Chaoscope")
    }
}
