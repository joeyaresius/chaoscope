package com.chaoscope

/**
 * Pure serialization helpers for [ColorStop] lists.
 * Format: comma-joined floats, 4 per stop — "pos,r,g,b,pos,r,g,b,..."
 * Returns null when input is malformed or contains fewer than 2 stops.
 */

internal fun colorStopsToString(stops: List<ColorStop>): String =
    stops.joinToString(",") { "${it.pos},${it.r},${it.g},${it.b}" }

internal fun stringToColorStops(raw: String): List<ColorStop>? {
    if (raw.isBlank()) return null
    return try {
        val parts = raw.split(',')
        val stops = mutableListOf<ColorStop>()
        var i = 0
        while (i + 3 < parts.size) {
            stops += ColorStop(
                parts[i].toFloat(),
                parts[i + 1].toFloat(),
                parts[i + 2].toFloat(),
                parts[i + 3].toFloat(),
            )
            i += 4
        }
        if (stops.size >= 2) stops else null
    } catch (_: Exception) {
        null
    }
}
