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
    val descriptionRes: Int,
    val paramHintsRes: List<Int>,
) {
    CLIFFORD(
        displayName   = "Clifford",
        is3D          = true,
        defaultParams = floatArrayOf(-1.4f, 1.6f, 1.0f, 0.7f, 1.5f, 0.5f),
        paramNames    = listOf("a", "b", "c", "d", "e", "f"),
        paramRanges   = listOf(-3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f),
        descriptionRes = R.string.attr_clifford_desc,
        paramHintsRes  = listOf(
            R.string.attr_clifford_hint0, R.string.attr_clifford_hint1,
            R.string.attr_clifford_hint2, R.string.attr_clifford_hint3,
            R.string.attr_clifford_hint4, R.string.attr_clifford_hint5,
        ),
    ),
    PETER_DE_JONG(
        displayName   = "Peter de Jong",
        is3D          = true,
        defaultParams = floatArrayOf(-2.0f, -2.0f, -1.2f, 2.0f, 1.8f, -1.5f),
        paramNames    = listOf("a", "b", "c", "d", "e", "f"),
        paramRanges   = listOf(-3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f, -3f..3f),
        descriptionRes = R.string.attr_peter_de_jong_desc,
        paramHintsRes  = listOf(
            R.string.attr_peter_de_jong_hint0, R.string.attr_peter_de_jong_hint1,
            R.string.attr_peter_de_jong_hint2, R.string.attr_peter_de_jong_hint3,
            R.string.attr_peter_de_jong_hint4, R.string.attr_peter_de_jong_hint5,
        ),
    ),
    GUMOWSKI_MIRA(
        displayName   = "Gumowski-Mira",
        is3D          = false,
        defaultParams = floatArrayOf(0.008f, -0.496f),
        paramNames    = listOf("a", "μ"),
        paramRanges   = listOf(-1f..1f, -1f..1f),
        descriptionRes = R.string.attr_gumowski_mira_desc,
        paramHintsRes  = listOf(
            R.string.attr_gumowski_mira_hint0, R.string.attr_gumowski_mira_hint1,
        ),
    ),
    LORENZ(
        displayName   = "Lorenz",
        is3D          = true,
        defaultParams = floatArrayOf(10f, 28f, 2.667f, 0.005f),
        paramNames    = listOf("σ", "ρ", "β", "dt"),
        paramRanges   = listOf(0f..20f, 0f..50f, 0f..5f, 0.001f..0.01f),
        descriptionRes = R.string.attr_lorenz_desc,
        paramHintsRes  = listOf(
            R.string.attr_lorenz_hint0, R.string.attr_lorenz_hint1,
            R.string.attr_lorenz_hint2, R.string.attr_lorenz_hint3,
        ),
    ),
    ROSSLER(
        displayName   = "Rössler",
        is3D          = true,
        defaultParams = floatArrayOf(0.2f, 0.2f, 5.7f, 0.02f),
        paramNames    = listOf("a", "b", "c", "dt"),
        paramRanges   = listOf(0f..1f, 0f..1f, 0f..10f, 0.005f..0.05f),
        descriptionRes = R.string.attr_rossler_desc,
        paramHintsRes  = listOf(
            R.string.attr_rossler_hint0, R.string.attr_rossler_hint1,
            R.string.attr_rossler_hint2, R.string.attr_rossler_hint3,
        ),
    ),
    AIZAWA(
        displayName   = "Aizawa",
        is3D          = true,
        defaultParams = floatArrayOf(0.95f, 0.7f, 0.6f, 3.5f, 0.25f, 0.1f, 0.01f),
        paramNames    = listOf("a", "b", "c", "d", "e", "f", "dt"),
        paramRanges   = listOf(0f..2f, 0f..2f, 0f..2f, 0f..5f, 0f..1f, 0f..1f, 0.005f..0.02f),
        descriptionRes = R.string.attr_aizawa_desc,
        paramHintsRes  = listOf(
            R.string.attr_aizawa_hint0, R.string.attr_aizawa_hint1,
            R.string.attr_aizawa_hint2, R.string.attr_aizawa_hint3,
            R.string.attr_aizawa_hint4, R.string.attr_aizawa_hint5,
            R.string.attr_aizawa_hint6,
        ),
    ),
    THOMAS(
        displayName   = "Thomas",
        is3D          = true,
        defaultParams = floatArrayOf(0.208186f, 0.05f),
        paramNames    = listOf("b", "dt"),
        paramRanges   = listOf(0.05f..0.5f, 0.01f..0.1f),
        descriptionRes = R.string.attr_thomas_desc,
        paramHintsRes  = listOf(
            R.string.attr_thomas_hint0, R.string.attr_thomas_hint1,
        ),
    ),
    CHAOTIC_FLOW(
        displayName   = "Chaotic Flow",
        is3D          = true,
        defaultParams = floatArrayOf(3f, 2.7f, 1.7f, 2f, 9f, 0.01f),
        paramNames    = listOf("a", "b", "c", "d", "e", "dt"),
        paramRanges   = listOf(1f..5f, 1f..5f, 0.5f..3f, 1f..4f, 5f..15f, 0.001f..0.02f),
        descriptionRes = R.string.attr_chaotic_flow_desc,
        paramHintsRes  = listOf(
            R.string.attr_chaotic_flow_hint0, R.string.attr_chaotic_flow_hint1,
            R.string.attr_chaotic_flow_hint2, R.string.attr_chaotic_flow_hint3,
            R.string.attr_chaotic_flow_hint4, R.string.attr_chaotic_flow_hint5,
        ),
    ),
    ICON(
        displayName   = "Icon",
        is3D          = true,
        defaultParams = floatArrayOf(-2.5f, 5.0f, -1.8f, 1.0f),
        paramNames    = listOf("λ", "α", "β", "ω"),
        paramRanges   = listOf(-4f..-1f, 1f..8f, -3f..0.5f, 0.1f..2f),
        descriptionRes = R.string.attr_icon_desc,
        paramHintsRes  = listOf(
            R.string.attr_icon_hint0, R.string.attr_icon_hint1,
            R.string.attr_icon_hint2, R.string.attr_icon_hint3,
        ),
    ),
    IFS(
        displayName   = "Barnsley Fern",
        is3D          = true,
        defaultParams = floatArrayOf(1.0f, 0.0f, 0.2f),
        paramNames    = listOf("width", "lean", "twist"),
        paramRanges   = listOf(0.7f..1.3f, -0.2f..0.2f, 0f..0.6f),
        descriptionRes = R.string.attr_ifs_desc,
        paramHintsRes  = listOf(
            R.string.attr_ifs_hint0, R.string.attr_ifs_hint1, R.string.attr_ifs_hint2,
        ),
    ),
    JULIA(
        displayName   = "Julia",
        is3D          = true,
        defaultParams = floatArrayOf(-0.2f, 0.6f, 0.3f),
        paramNames    = listOf("c_re", "c_im", "c_j"),
        paramRanges   = listOf(-2f..2f, -2f..2f, -1f..1f),
        descriptionRes = R.string.attr_julia_desc,
        paramHintsRes  = listOf(
            R.string.attr_julia_hint0, R.string.attr_julia_hint1, R.string.attr_julia_hint2,
        ),
    ),
    PICKOVER(
        displayName   = "Pickover",
        is3D          = true,
        defaultParams = floatArrayOf(2.24f, 0.43f, -0.65f, -2.43f),
        paramNames    = listOf("a", "b", "c", "d"),
        paramRanges   = listOf(-3f..3f, -3f..3f, -3f..3f, -3f..3f),
        descriptionRes = R.string.attr_pickover_desc,
        paramHintsRes  = listOf(
            R.string.attr_pickover_hint0, R.string.attr_pickover_hint1,
            R.string.attr_pickover_hint2, R.string.attr_pickover_hint3,
        ),
    ),
    HALVORSEN(
        displayName   = "Halvorsen",
        is3D          = true,
        defaultParams = floatArrayOf(1.89f, 0.005f),
        paramNames    = listOf("a", "dt"),
        paramRanges   = listOf(1.0f..2.5f, 0.002f..0.01f),
        descriptionRes = R.string.attr_halvorsen_desc,
        paramHintsRes  = listOf(
            R.string.attr_halvorsen_hint0, R.string.attr_halvorsen_hint1,
        ),
    ),
    BURKE_SHAW(
        displayName   = "Burke-Shaw",
        is3D          = true,
        defaultParams = floatArrayOf(10f, 4.272f, 0.005f),
        paramNames    = listOf("s", "v", "dt"),
        paramRanges   = listOf(5f..15f, 1f..8f, 0.002f..0.01f),
        descriptionRes = R.string.attr_burke_shaw_desc,
        paramHintsRes  = listOf(
            R.string.attr_burke_shaw_hint0, R.string.attr_burke_shaw_hint1,
            R.string.attr_burke_shaw_hint2,
        ),
    ),
    SPROTT_B(
        displayName   = "Sprott-B",
        is3D          = true,
        defaultParams = floatArrayOf(1f, 1f, 0.02f),
        paramNames    = listOf("a", "b", "dt"),
        paramRanges   = listOf(0.5f..2f, 0.5f..2f, 0.005f..0.04f),
        descriptionRes = R.string.attr_sprott_b_desc,
        paramHintsRes  = listOf(
            R.string.attr_sprott_b_hint0, R.string.attr_sprott_b_hint1,
            R.string.attr_sprott_b_hint2,
        ),
    ),
    LORENZ_84(
        displayName   = "Lorenz-84",
        is3D          = true,
        defaultParams = floatArrayOf(0.25f, 4.0f, 8.0f, 1.0f, 0.01f),
        paramNames    = listOf("a", "b", "F", "G", "dt"),
        paramRanges   = listOf(0f..1f, 0f..8f, 0f..12f, 0f..3f, 0.002f..0.02f),
        descriptionRes = R.string.attr_lorenz_84_desc,
        paramHintsRes  = listOf(
            R.string.attr_lorenz_84_hint0, R.string.attr_lorenz_84_hint1,
            R.string.attr_lorenz_84_hint2, R.string.attr_lorenz_84_hint3,
            R.string.attr_lorenz_84_hint4,
        ),
    ),
    HENON(
        displayName   = "Hénon",
        is3D          = false,
        defaultParams = floatArrayOf(1.4f, 0.3f),
        paramNames    = listOf("a", "b"),
        paramRanges   = listOf(1.0f..1.42f, 0.1f..0.35f),
        descriptionRes = R.string.attr_henon_desc,
        paramHintsRes  = listOf(
            R.string.attr_henon_hint0, R.string.attr_henon_hint1,
        ),
    ),
}

// ────────────────────────────────────────────────────────────────────────────
// Palette definitions
// ────────────────────────────────────────────────────────────────────────────

enum class PaletteType(val displayName: String, val descriptionRes: Int) {
    NEBULA   ("Nebula",    R.string.palette_nebula_desc),
    FIRE     ("Fire",      R.string.palette_fire_desc),
    ELECTRIC ("Electric",  R.string.palette_electric_desc),
    AURORA   ("Aurora",    R.string.palette_aurora_desc),
    MATRIX   ("Matrix",    R.string.palette_matrix_desc),
    GREYSCALE("Grey",      R.string.palette_grey_desc),
    SPECTRUM ("Spectrum",  R.string.palette_spectrum_desc),
    SUNSET   ("Sunset",    R.string.palette_sunset_desc),
    ICE      ("Ice",       R.string.palette_ice_desc),
    NEON     ("Neon",      R.string.palette_neon_desc),
    CUSTOM   ("Custom",    R.string.palette_custom_desc),
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

enum class RenderStyle(val displayName: String, val descriptionRes: Int) {
    STANDARD("Gas",    R.string.style_gas_desc),
    GAS     ("Dust",   R.string.style_dust_desc),
    LIQUID  ("Liquid", R.string.style_liquid_desc),
    PLASMA  ("Plasma", R.string.style_plasma_desc),
    SOLID   ("Solid",  R.string.style_solid_desc),
    LIGHT   ("Light",  R.string.style_light_desc),
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
    val descriptionRes: Int,
) {
    DRAFT   ("Draft",    1_000_000L,  15_000_000L, R.string.quality_draft_desc),
    STANDARD("Standard", 2_000_000L,  50_000_000L, R.string.quality_standard_desc),
    HIGH    ("High",     5_000_000L, 120_000_000L, R.string.quality_high_desc),
    ULTRA   ("Ultra",   10_000_000L, 300_000_000L, R.string.quality_ultra_desc),
}

/** How many dots the live rotation preview draws. Lower if rotation feels sluggish. */
enum class PreviewDensity(val displayName: String, val dots: Int, val descriptionRes: Int) {
    LOW   ("Low",   30_000,  R.string.density_low_desc),
    MEDIUM("Med",   80_000,  R.string.density_med_desc),
    HIGH  ("High", 160_000,  R.string.density_high_desc),
}

// ────────────────────────────────────────────────────────────────────────────
// Background colour presets
// ────────────────────────────────────────────────────────────────────────────

enum class BgColor(val displayName: String, val argb: Int, val isTheme: Boolean = false) {
    /** Procedural themed backgrounds — drawn in Compose, render uses transparent bitmap. */
    STARS      ("Stars",      0xFF04040F.toInt(), isTheme = true),
    FOREST_BG  ("Forest",     0xFF011A01.toInt(), isTheme = true),
    OCEAN_BG   ("Ocean",      0xFF00101A.toInt(), isTheme = true),
    AURORA_BG  ("Aurora",     0xFF050510.toInt(), isTheme = true),
    /** Solid colour backgrounds */
    BLACK      ("Black",      0xFF000000.toInt()),
    WHITE      ("White",      0xFFFFFFFF.toInt()),
    /** User-defined colour; actual ARGB lives in [UiState.customBgArgb]. */
    CUSTOM     ("Custom",     0xFF000000.toInt()),
    /** User-picked photo; the image file path lives in [UiState.customBgPath]. */
    IMAGE      ("Image",      0xFF000000.toInt()),
}

/**
 * True when the attractor must render onto a transparent bitmap and the
 * background art (procedural theme or a picked photo) is drawn behind it,
 * rather than the native renderer filling a solid colour.
 */
val BgColor.drawsArtBehind: Boolean get() = isTheme || this == BgColor.IMAGE

/** Display order for the background selector: solids first, then themed, then custom/photo. */
val BG_DISPLAY_ORDER: List<BgColor> = listOf(
    BgColor.BLACK, BgColor.WHITE,
    BgColor.STARS, BgColor.FOREST_BG, BgColor.OCEAN_BG, BgColor.AURORA_BG,
    BgColor.CUSTOM, BgColor.IMAGE,
)

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
        Preset("Mandala",   AttractorType.ICON, listOf(-2.5f, 5.0f, -1.8f, 1.0f), yaw = 25f, pitch = 30f, palette = PaletteType.NEBULA),
        Preset("Sunwheel",  AttractorType.ICON, listOf(-2.0f, 6.0f, -1.5f, 1.2f), yaw = 35f, pitch = 20f, palette = PaletteType.FIRE, renderStyle = RenderStyle.PLASMA),
        Preset("Snowflake", AttractorType.ICON, listOf(-2.34f, 2.0f, 0.2f, 0.1f), yaw = 20f, pitch = 35f, palette = PaletteType.ELECTRIC),
    ),
    AttractorType.IFS to listOf(
        Preset("Fern",      AttractorType.IFS, listOf(1.0f, 0.0f, 0.0f), palette = PaletteType.MATRIX),
        // twist=0.3 with any camera caused the dot-preview bounds detection to miss
        // the large z-extent (~13 units); reduced to within the range the fixed
        // BOUNDS_DOT=8 detection handles correctly. twist=0.22 at pitch=55° still hit a
        // near-collinear projection that collapsed the fern onto a ~1D strip → black;
        // nudged to 0.221 to break that degenerate alignment.
        Preset("Spiral",    AttractorType.IFS, listOf(1.0f, 0.0f, 0.221f), yaw = 0f, pitch = 55f, palette = PaletteType.AURORA),
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
    AttractorType.LORENZ_84 to listOf(
        Preset("Climate",  AttractorType.LORENZ_84, listOf(0.25f, 4.0f, 8.0f, 1.0f, 0.01f), yaw = 30f, pitch = 25f, palette = PaletteType.ELECTRIC),
        Preset("Jet",      AttractorType.LORENZ_84, listOf(0.25f, 4.0f, 6.5f, 1.0f, 0.01f), yaw = 60f, pitch = 15f, palette = PaletteType.NEBULA, renderStyle = RenderStyle.LIQUID),
        Preset("Monsoon",  AttractorType.LORENZ_84, listOf(0.3f,  4.0f, 9.5f, 1.3f, 0.01f), yaw = 15f, pitch = 35f, palette = PaletteType.AURORA, renderStyle = RenderStyle.GAS),
    ),
    AttractorType.HENON to listOf(
        Preset("Banana",   AttractorType.HENON, listOf(1.4f, 0.3f), palette = PaletteType.NEBULA),
        Preset("Filament", AttractorType.HENON, listOf(1.39f, 0.27f), palette = PaletteType.ELECTRIC, renderStyle = RenderStyle.GAS),
        Preset("Crescent", AttractorType.HENON, listOf(1.4f, 0.2f), palette = PaletteType.FIRE, renderStyle = RenderStyle.PLASMA),
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
    /** HD/4K render progress in [0,1]; -1 = indeterminate (preview/GPU render). */
    val renderProgress: Float        = -1f,
    val renderFailedMessage: String? = null,
    // Fresh-install "first impression" look — the Burke-Shaw "Galaxy" preset.
    // Returning users get their persisted state restored over these defaults.
    val attractorType: AttractorType = AttractorType.BURKE_SHAW,
    val params: List<Float>          = AttractorType.BURKE_SHAW.defaultParams.toList(),
    val palette: PaletteType         = PaletteType.ELECTRIC,
    val customStops: List<ColorStop> = defaultCustomStops,
    val yaw: Float                   = 25f,
    val pitch: Float                 = 25f,
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
    /** Absolute path to the user-picked background photo (copied into app storage). */
    val customBgPath: String?        = null,
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

/** The visual state captured as a [Preset]. */
fun UiState.toPreset(name: String = attractorType.displayName) = Preset(
    name        = name,
    type        = attractorType,
    params      = params,
    yaw         = yaw,
    pitch       = pitch,
    roll        = roll,
    zoom        = zoom,
    palette     = palette,
    renderStyle = renderStyle,
    bgColor     = bgColor,
)

// ────────────────────────────────────────────────────────────────────────────
// Sharing
// ────────────────────────────────────────────────────────────────────────────

/** Official Instagram presence — used by the share caption (tag nudge) and the
 *  "Follow on Instagram" button on the About screen. */
const val INSTAGRAM_HANDLE = "@chaoscope.app"
const val INSTAGRAM_URL    = "https://www.instagram.com/chaoscope.app/"

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

/**
 * Full share caption for [preset]: the human-readable line plus a `CHS1:` code
 * a friend can paste back into Chaoscope to recreate the exact look.
 */
fun buildShareCaption(preset: Preset): String =
    buildShareCaption(preset.type, preset.palette, preset.params) +
        "\n\nRecreate it in Chaoscope: " + presetToCode(preset) +
        "\n\n📷 Tag $INSTAGRAM_HANDLE to be featured"
