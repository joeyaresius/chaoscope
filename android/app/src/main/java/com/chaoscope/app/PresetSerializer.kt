package com.chaoscope

/**
 * Pure serialization helpers for user-saved [Preset]s.
 * One preset per line; fields '|'-separated; params ','-separated. Enums are
 * stored by name so reordering an enum can't corrupt saved data. A field that
 * no longer resolves causes that preset to be skipped on load.
 *
 *   name|TYPE|p0,p1,..|yaw|pitch|roll|zoom|PALETTE|STYLE|BG
 */

private fun sanitize(name: String): String =
    name.replace('|', ' ').replace('\n', ' ').trim()

internal fun presetToString(p: Preset): String = listOf(
    sanitize(p.name),
    p.type.name,
    p.params.joinToString(","),
    p.yaw, p.pitch, p.roll, p.zoom,
    p.palette.name,
    p.renderStyle.name,
    p.bgColor.name,
).joinToString("|")

internal fun presetsToString(presets: List<Preset>): String =
    presets.joinToString("\n") { presetToString(it) }

private fun parsePreset(line: String): Preset? = try {
    val f = line.split('|')
    if (f.size < 10) return null
    val type    = AttractorType.valueOf(f[1])
    val params  = f[2].split(',').mapNotNull { it.toFloatOrNull() }
    if (params.isEmpty()) return null
    Preset(
        name        = f[0],
        type        = type,
        params      = params,
        yaw         = f[3].toFloat(),
        pitch       = f[4].toFloat(),
        roll        = f[5].toFloat(),
        zoom        = f[6].toFloat(),
        palette     = PaletteType.valueOf(f[7]),
        renderStyle = RenderStyle.valueOf(f[8]),
        bgColor     = BgColor.valueOf(f[9]),
    )
} catch (_: Exception) {
    null
}

internal fun stringToPresets(raw: String): List<Preset> {
    if (raw.isBlank()) return emptyList()
    return raw.split('\n').mapNotNull { if (it.isBlank()) null else parsePreset(it) }
}
