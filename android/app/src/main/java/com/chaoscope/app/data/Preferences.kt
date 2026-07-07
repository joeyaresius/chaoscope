package com.chaoscope.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chaoscope.AttractorType
import com.chaoscope.BgColor
import com.chaoscope.ColorStop
import com.chaoscope.PaletteType
import com.chaoscope.Preset
import com.chaoscope.PreviewDensity
import com.chaoscope.RenderQuality
import com.chaoscope.RenderStyle
import com.chaoscope.UiState
import com.chaoscope.GalleryEntry
import com.chaoscope.colorStopsToString
import com.chaoscope.defaultCustomStops
import com.chaoscope.galleryEntryToString
import com.chaoscope.parseGalleryEntry
import com.chaoscope.presetsToString
import com.chaoscope.stringToColorStops
import com.chaoscope.stringToPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chaoscope_prefs")

class ChaoscopePreferences(private val context: Context) {

    private val data: Flow<Preferences> get() = context.dataStore.data

    // ── Splash dismissal (kept for API compat; splash now shows every session) ─

    val splashDismissed: Flow<Boolean> = data.map { it[KEY_SPLASH_DISMISSED] ?: false }

    suspend fun setSplashDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[KEY_SPLASH_DISMISSED] = dismissed }
    }

    // ── Tutorial dismissal ────────────────────────────────────────────────────

    suspend fun isTutorialDismissed(): Boolean =
        data.first()[KEY_TUTORIAL_DISMISSED] ?: false

    suspend fun setTutorialDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[KEY_TUTORIAL_DISMISSED] = dismissed }
    }

    // ── Custom palette stops ──────────────────────────────────────────────────

    suspend fun loadCustomStops(): List<ColorStop> {
        val raw = data.first()[KEY_CUSTOM_STOPS] ?: return defaultCustomStops
        return stringToColorStops(raw) ?: defaultCustomStops
    }

    suspend fun saveCustomStops(stops: List<ColorStop>) {
        context.dataStore.edit { it[KEY_CUSTOM_STOPS] = colorStopsToString(stops) }
    }

    // ── Last UI state ────────────────────────────────────────────────────────

    /** Read the persisted [UiState] once on cold start, or null if nothing saved. */
    suspend fun loadLastState(): UiState? {
        val prefs = data.first()
        val attractorOrdinal = prefs[KEY_ATTRACTOR] ?: return null
        val attractor = AttractorType.entries.getOrNull(attractorOrdinal) ?: return null

        val params = (0 until attractor.paramNames.size).map { i ->
            prefs[floatPreferencesKey("$KEY_PARAM_PREFIX$i")]
                ?: attractor.defaultParams[i]
        }

        val savedCustomStops = prefs[KEY_CUSTOM_STOPS]
            ?.let { raw -> stringToColorStops(raw) }
            ?: defaultCustomStops

        return UiState(
            attractorType = attractor,
            params        = params,
            palette       = PaletteType.entries.getOrNull(prefs[KEY_PALETTE] ?: 0)
                            ?: PaletteType.NEBULA,
            renderStyle   = RenderStyle.entries.getOrNull(prefs[KEY_RENDER_STYLE] ?: 0)
                            ?: RenderStyle.STANDARD,
            bgColor       = BgColor.entries.getOrNull(prefs[KEY_BG_COLOR] ?: 0)
                            ?: BgColor.BLACK,
            yaw           = prefs[KEY_YAW]   ?: 0f,
            pitch         = prefs[KEY_PITCH] ?: 0f,
            roll          = prefs[KEY_ROLL]  ?: 0f,
            zoom          = prefs[KEY_ZOOM]  ?: 1f,
            gamma         = prefs[KEY_GAMMA] ?: 1f,
            depthCue      = prefs[KEY_DEPTH_CUE] ?: 0.5f,
            fullRange     = prefs[KEY_FULL_RANGE] ?: false,
            renderQuality = RenderQuality.entries.getOrNull(prefs[KEY_RENDER_QUALITY] ?: 1)
                            ?: RenderQuality.STANDARD,
            previewDensity = PreviewDensity.entries.getOrNull(prefs[KEY_PREVIEW_DENSITY] ?: 1)
                            ?: PreviewDensity.MEDIUM,
            customStops    = savedCustomStops,
            transparentBg  = prefs[KEY_TRANSPARENT_BG] ?: false,
            customBgArgb   = prefs[KEY_CUSTOM_BG_ARGB] ?: 0xFF1A0028.toInt(),
            customBgPath   = prefs[KEY_CUSTOM_BG_PATH],
        )
    }

    suspend fun saveState(state: UiState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ATTRACTOR]     = state.attractorType.ordinal
            prefs[KEY_PALETTE]       = state.palette.ordinal
            prefs[KEY_RENDER_STYLE]  = state.renderStyle.ordinal
            prefs[KEY_BG_COLOR]      = state.bgColor.ordinal
            prefs[KEY_YAW]           = state.yaw
            prefs[KEY_PITCH]         = state.pitch
            prefs[KEY_ROLL]          = state.roll
            prefs[KEY_ZOOM]          = state.zoom
            prefs[KEY_GAMMA]         = state.gamma
            prefs[KEY_DEPTH_CUE]     = state.depthCue
            prefs[KEY_FULL_RANGE]    = state.fullRange
            prefs[KEY_RENDER_QUALITY]  = state.renderQuality.ordinal
            prefs[KEY_PREVIEW_DENSITY] = state.previewDensity.ordinal
            prefs[KEY_TRANSPARENT_BG]  = state.transparentBg
            prefs[KEY_CUSTOM_BG_ARGB]  = state.customBgArgb
            state.customBgPath?.let { prefs[KEY_CUSTOM_BG_PATH] = it }
                ?: prefs.remove(KEY_CUSTOM_BG_PATH)

            // Clear any stale param entries from a previous attractor with more params.
            val maxParams = AttractorType.entries.maxOf { it.paramNames.size }
            for (i in 0 until maxParams) prefs.remove(floatPreferencesKey("$KEY_PARAM_PREFIX$i"))
            state.params.forEachIndexed { i, v ->
                prefs[floatPreferencesKey("$KEY_PARAM_PREFIX$i")] = v
            }
        }
    }

    // ── Video-export background warning (shown before the first export only) ─

    val videoWarningSeen: Flow<Boolean> = data.map { it[KEY_VIDEO_WARNING_SEEN] ?: false }

    suspend fun setVideoWarningSeen() {
        context.dataStore.edit { it[KEY_VIDEO_WARNING_SEEN] = true }
    }

    // ── First-ever render (drives the render-FAB pulse hint) ────────────────

    val hasEverRendered: Flow<Boolean> = data.map { it[KEY_HAS_RENDERED] ?: false }

    suspend fun setHasRendered() {
        context.dataStore.edit { it[KEY_HAS_RENDERED] = true }
    }

    // ── In-app review counter ────────────────────────────────────────────────

    /** Increment the render/export counter and return the new value. */
    suspend fun incrementRenderExportCount(): Int {
        var newCount = 0
        context.dataStore.edit { prefs ->
            newCount = (prefs[KEY_RENDER_EXPORT_COUNT] ?: 0) + 1
            prefs[KEY_RENDER_EXPORT_COUNT] = newCount
        }
        return newCount
    }

    suspend fun isReviewTriggered(): Boolean =
        data.first()[KEY_REVIEW_TRIGGERED] ?: false

    suspend fun setReviewTriggered() {
        context.dataStore.edit { it[KEY_REVIEW_TRIGGERED] = true }
    }

    // ── Gallery (exported renders + their presets, newest first, capped) ─────
    // Reuses the old recents key: legacy URI-only lines parse as entries with a
    // null preset, so existing users keep their recent renders (view/share only).

    val galleryEntries: Flow<List<GalleryEntry>> = data.map { prefs ->
        prefs[KEY_RECENTS]
            ?.split('\n')
            ?.mapNotNull { parseGalleryEntry(it) }
            ?: emptyList()
    }

    suspend fun addGalleryEntry(entry: GalleryEntry) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_RECENTS]
                ?.split('\n')
                ?.mapNotNull { parseGalleryEntry(it) }
                ?.filter { it.uri != entry.uri }
                ?: emptyList()
            val updated = (listOf(entry) + existing).take(MAX_GALLERY)
            prefs[KEY_RECENTS] = updated.joinToString("\n") { galleryEntryToString(it) }
        }
    }

    suspend fun deleteGalleryEntry(uri: String) {
        context.dataStore.edit { prefs ->
            val kept = prefs[KEY_RECENTS]
                ?.split('\n')
                ?.mapNotNull { parseGalleryEntry(it) }
                ?.filter { it.uri != uri }
                ?: return@edit
            prefs[KEY_RECENTS] = kept.joinToString("\n") { galleryEntryToString(it) }
        }
    }

    // ── User-saved presets (favourites) ──────────────────────────────────────

    val userPresets: Flow<List<Preset>> = data.map { prefs ->
        prefs[KEY_USER_PRESETS]?.let { stringToPresets(it) } ?: emptyList()
    }

    /** Save a preset, replacing any existing one with the same name. Newest first, capped. */
    suspend fun saveUserPreset(preset: Preset) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USER_PRESETS]
                ?.let { stringToPresets(it) }
                ?.filter { it.name != preset.name }
                ?: emptyList()
            val updated = (listOf(preset) + existing).take(MAX_USER_PRESETS)
            prefs[KEY_USER_PRESETS] = presetsToString(updated)
        }
    }

    suspend fun deleteUserPreset(name: String) {
        context.dataStore.edit { prefs ->
            val kept = prefs[KEY_USER_PRESETS]
                ?.let { stringToPresets(it) }
                ?.filter { it.name != name }
                ?: return@edit
            prefs[KEY_USER_PRESETS] = presetsToString(kept)
        }
    }

    companion object {
        private const val MAX_GALLERY = 50
        private const val MAX_USER_PRESETS = 30
        private const val KEY_PARAM_PREFIX = "param_"

        private val KEY_SPLASH_DISMISSED   = booleanPreferencesKey("splash_dismissed")
        private val KEY_TUTORIAL_DISMISSED = booleanPreferencesKey("tutorial_dismissed")
        private val KEY_ATTRACTOR          = intPreferencesKey("attractor_type")
        private val KEY_PALETTE            = intPreferencesKey("palette")
        private val KEY_RENDER_STYLE       = intPreferencesKey("render_style")
        private val KEY_BG_COLOR           = intPreferencesKey("bg_color")
        private val KEY_YAW                = floatPreferencesKey("yaw")
        private val KEY_PITCH              = floatPreferencesKey("pitch")
        private val KEY_ROLL               = floatPreferencesKey("roll")
        private val KEY_ZOOM               = floatPreferencesKey("zoom")
        private val KEY_GAMMA              = floatPreferencesKey("gamma")
        private val KEY_DEPTH_CUE          = floatPreferencesKey("depth_cue")
        private val KEY_FULL_RANGE         = booleanPreferencesKey("full_range")
        private val KEY_RENDER_QUALITY     = intPreferencesKey("render_quality")
        private val KEY_PREVIEW_DENSITY    = intPreferencesKey("preview_density")
        private val KEY_TRANSPARENT_BG     = booleanPreferencesKey("transparent_bg")
        private val KEY_CUSTOM_BG_ARGB      = intPreferencesKey("custom_bg_argb")
        private val KEY_CUSTOM_BG_PATH      = stringPreferencesKey("custom_bg_path")
        private val KEY_VIDEO_WARNING_SEEN  = booleanPreferencesKey("video_warning_seen")
        private val KEY_HAS_RENDERED        = booleanPreferencesKey("has_ever_rendered")
        private val KEY_RENDER_EXPORT_COUNT = intPreferencesKey("render_export_count")
        private val KEY_REVIEW_TRIGGERED    = booleanPreferencesKey("review_triggered")
        private val KEY_RECENTS            = stringPreferencesKey("recent_exports")
        private val KEY_CUSTOM_STOPS       = stringPreferencesKey("custom_stops")
        private val KEY_USER_PRESETS       = stringPreferencesKey("user_presets")
    }
}
