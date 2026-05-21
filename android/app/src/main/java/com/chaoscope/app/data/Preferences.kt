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
import com.chaoscope.RenderStyle
import com.chaoscope.UiState
import com.chaoscope.colorStopsToString
import com.chaoscope.defaultCustomStops
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
            customStops   = savedCustomStops,
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

            // Clear any stale param entries from a previous attractor with more params.
            val maxParams = AttractorType.entries.maxOf { it.paramNames.size }
            for (i in 0 until maxParams) prefs.remove(floatPreferencesKey("$KEY_PARAM_PREFIX$i"))
            state.params.forEachIndexed { i, v ->
                prefs[floatPreferencesKey("$KEY_PARAM_PREFIX$i")] = v
            }
        }
    }

    // ── Recent exports (Uri strings, newest first, capped) ───────────────────

    val recentExports: Flow<List<String>> = data.map { prefs ->
        prefs[KEY_RECENTS]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun addRecentExport(uri: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_RECENTS]
                ?.split('\n')
                ?.filter { it.isNotBlank() && it != uri }
                ?: emptyList()
            val updated = (listOf(uri) + existing).take(MAX_RECENTS)
            prefs[KEY_RECENTS] = updated.joinToString("\n")
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
        private const val MAX_RECENTS = 8
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
        private val KEY_RECENTS            = stringPreferencesKey("recent_exports")
        private val KEY_CUSTOM_STOPS       = stringPreferencesKey("custom_stops")
        private val KEY_USER_PRESETS       = stringPreferencesKey("user_presets")
    }
}
