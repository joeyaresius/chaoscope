package com.chaoscope

/**
 * Pure HSV ↔ RGB conversion utilities shared by the palette editor and unit tests.
 * All channel values are in 0..1 except hue which is in 0..360.
 */

/** RGB (0..1 each) → HSV (hue 0..360, saturation 0..1, value 0..1). */
internal fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val h = when {
        delta == 0f -> 0f
        max == r    -> 60f * (((g - b) / delta) % 6f)
        max == g    -> 60f * ((b - r) / delta + 2f)
        else        -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val s = if (max == 0f) 0f else delta / max
    return floatArrayOf(h, s, max)
}

/** HSV (hue 0..360, saturation 0..1, value 0..1) → RGB (0..1 each). */
internal fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
    if (s == 0f) return Triple(v, v, v)
    val sector = (h / 60f).toInt() % 6
    val f = h / 60f - sector
    val p = v * (1f - s)
    val q = v * (1f - f * s)
    val t = v * (1f - (1f - f) * s)
    return when (sector) {
        0    -> Triple(v, t, p)
        1    -> Triple(q, v, p)
        2    -> Triple(p, v, t)
        3    -> Triple(p, q, v)
        4    -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
}
