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
    name.replace('|', ' ').replace('\n', ' ').replace('\t', ' ').trim()

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

private fun parsePreset(line: String): Preset? {
    return try {
        val f = line.split('|')
        if (f.size < 10) return null
        val type   = AttractorType.valueOf(f[1])
        val params = f[2].split(',').mapNotNull { it.toFloatOrNull() }
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
}

internal fun stringToPresets(raw: String): List<Preset> {
    if (raw.isBlank()) return emptyList()
    return raw.split('\n').mapNotNull { if (it.isBlank()) null else parsePreset(it) }
}

// ────────────────────────────────────────────────────────────────────────────
// Shareable preset codes — `CHS1:<base64url(presetString minus name)>`.
// Appended to share captions so every shared image carries its own recipe;
// `CHS2:` etc. later if the field set ever changes.
// ────────────────────────────────────────────────────────────────────────────

const val PRESET_CODE_PREFIX = "CHS1:"

/** Compact shareable code for [p]. The name is dropped — codes describe a look. */
fun presetToCode(p: Preset): String {
    val payload = presetToString(p.copy(name = ""))
    val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.toByteArray(Charsets.UTF_8))
    return PRESET_CODE_PREFIX + b64
}

/**
 * Extracts and decodes the first preset code found anywhere in [raw] (a bare
 * code, a full share caption, a chat message…). Null if absent or malformed —
 * including codes from a newer app version whose enums don't resolve here.
 */
fun presetFromCode(raw: String): Preset? {
    val idx = raw.indexOf(PRESET_CODE_PREFIX)
    if (idx < 0) return null
    val token = raw.substring(idx + PRESET_CODE_PREFIX.length)
        .takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
    if (token.isEmpty()) return null
    return runCatching {
        val payload = String(java.util.Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
        parsePreset(payload)
    }.getOrNull()
}

// ────────────────────────────────────────────────────────────────────────────
// Gallery entries — an exported render plus the preset that produced it, so a
// past creation can be reopened in the editor (not just viewed as a PNG).
// One entry per line; fields tab-separated (preset strings use '|' internally
// and sanitize() strips tabs from names):
//
//   uri \t timestampMillis \t presetString \t kind
//
// `kind` is "vid" for video exports, anything else (or absent) means image.
// Legacy lines from the URI-only recents store have no tab — they load with a
// null preset (view/share only).
// ────────────────────────────────────────────────────────────────────────────

data class GalleryEntry(
    val uri:       String,
    val timestamp: Long,
    val preset:    Preset?,
    val isVideo:   Boolean = false,
)

internal fun galleryEntryToString(e: GalleryEntry): String = listOf(
    e.uri,
    e.timestamp.toString(),
    e.preset?.let { presetToString(it) } ?: "",
    if (e.isVideo) "vid" else "img",
).joinToString("\t")

internal fun parseGalleryEntry(line: String): GalleryEntry? {
    if (line.isBlank()) return null
    val f = line.split('\t')
    if (f[0].isBlank()) return null
    return GalleryEntry(
        uri       = f[0],
        timestamp = f.getOrNull(1)?.toLongOrNull() ?: 0L,
        preset    = f.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { parsePreset(it) },
        isVideo   = f.getOrNull(3) == "vid",
    )
}
