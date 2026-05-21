package com.chaoscope

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaoscope.data.ChaoscopePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

// Tutorial target keys
enum class TutorialTarget { Canvas, AttractorRow, ParamSlider, RenderHdButton, PaletteRow }

data class TutorialAnchors(
    val canvas: Rect?         = null,
    val attractorRow: Rect?   = null,
    val paramSlider: Rect?    = null,
    val renderHdButton: Rect? = null,
    val paletteRow: Rect?     = null,
)

@OptIn(FlowPreview::class)
class ChaoscopeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ChaoscopePreferences(app)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _dotPoints  = MutableStateFlow<FloatArray?>(null)
    val dotPoints: StateFlow<FloatArray?> = _dotPoints.asStateFlow()

    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    val recentExports: StateFlow<List<String>> = prefs.recentExports
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Session-level splash (resets every cold start via ViewModel lifecycle) ─
    private val _sessionSplashDone = MutableStateFlow(false)
    val sessionSplashDone: StateFlow<Boolean> = _sessionSplashDone.asStateFlow()

    // ── Tutorial state ───────────────────────────────────────────────────────
    private val _showTutorial   = MutableStateFlow(false)
    val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

    private val _tutorialStep   = MutableStateFlow(0)
    val tutorialStep: StateFlow<Int> = _tutorialStep.asStateFlow()

    private val _tutorialAnchors = MutableStateFlow(TutorialAnchors())
    val tutorialAnchors: StateFlow<TutorialAnchors> = _tutorialAnchors.asStateFlow()

    // ── Palette editor ───────────────────────────────────────────────────────
    private val _showPaletteEditor = MutableStateFlow(false)
    val showPaletteEditor: StateFlow<Boolean> = _showPaletteEditor.asStateFlow()

    private var renderJob: Job? = null
    private var finishJob:  Job? = null
    private var dotJob:     Job? = null

    init {
        viewModelScope.launch {
            // Restore last persisted parameters before the first dot-preview fetch.
            prefs.loadLastState()?.let { saved ->
                _uiState.update { saved }
            }
            fetchDotPoints()
        }


        // Persist parameter changes (debounced) — skip the first emission so we
        // don't immediately overwrite restored state with defaults.
        viewModelScope.launch {
            _uiState
                .map { it.persistableSnapshot() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(PERSIST_DEBOUNCE_MS)
                .collect { snapshot -> prefs.saveState(snapshot) }
        }
    }

    /** Strip out volatile fields (bitmap, flags) before persisting. */
    private fun UiState.persistableSnapshot(): UiState = copy(
        bitmap               = null,
        isRendering          = false,
        isRetrying           = false,
        renderFailedMessage  = null,
        exportDone           = false,
        exportError          = null,
        lastExportUri        = null,
    )

    // ── Parameter updates (state only – no auto render) ────────────────────

    fun setAttractorType(type: AttractorType) {
        _uiState.update {
            it.copy(
                attractorType = type,
                params        = type.defaultParams.toList(),
                yaw   = if (type.is3D) it.yaw   else 0f,
                pitch = if (type.is3D) it.pitch else 0f,
                roll  = if (type.is3D) it.roll  else 0f,
            )
        }
        fetchDotPoints()
    }

    fun updateParam(index: Int, value: Float) {
        _uiState.update { state ->
            val p = state.params.toMutableList().also { it[index] = value }
            state.copy(params = p)
        }
        fetchDotPoints()
    }

    fun setPalette(palette: PaletteType) {
        _uiState.update { it.copy(palette = palette) }
    }

    fun setCamera(
        yaw:   Float? = null,
        pitch: Float? = null,
        roll:  Float? = null,
        zoom:  Float? = null,
    ) {
        _uiState.update { s ->
            s.copy(
                yaw   = yaw   ?: s.yaw,
                pitch = pitch ?: s.pitch,
                roll  = roll  ?: s.roll,
                zoom  = zoom  ?: s.zoom,
            )
        }
        fetchDotPoints()
    }

    fun setGamma(gamma: Float) {
        _uiState.update { it.copy(gamma = gamma) }
    }

    fun setRenderStyle(style: RenderStyle) {
        _uiState.update { it.copy(renderStyle = style) }
    }

    fun setBgColor(color: BgColor) {
        _uiState.update { it.copy(bgColor = color, bitmap = null) }
        fetchDotPoints()
    }

    fun clearRenderFailedMessage() = _uiState.update { it.copy(renderFailedMessage = null) }

    // ── Splash / tutorial ────────────────────────────────────────────────────

    fun dismissSplash() {
        _sessionSplashDone.value = true
        viewModelScope.launch {
            if (!prefs.isTutorialDismissed()) {
                _showTutorial.value = true
                _tutorialStep.value = 0
            }
        }
    }

    fun dismissTutorial() {
        _showTutorial.value = false
        _tutorialStep.value = 0
        viewModelScope.launch { prefs.setTutorialDismissed(true) }
    }

    fun showTutorialAgain() {
        _showTutorial.value = true
        _tutorialStep.value = 0
    }

    fun advanceTutorial() {
        val next = _tutorialStep.value + 1
        if (next >= TUTORIAL_STEPS) dismissTutorial() else _tutorialStep.value = next
    }

    fun updateTutorialAnchor(target: TutorialTarget, rect: Rect) {
        _tutorialAnchors.update { a ->
            when (target) {
                TutorialTarget.Canvas        -> a.copy(canvas = rect)
                TutorialTarget.AttractorRow  -> a.copy(attractorRow = rect)
                TutorialTarget.ParamSlider   -> a.copy(paramSlider = rect)
                TutorialTarget.RenderHdButton -> a.copy(renderHdButton = rect)
                TutorialTarget.PaletteRow    -> a.copy(paletteRow = rect)
            }
        }
    }

    // ── Palette editor ───────────────────────────────────────────────────────

    fun openPaletteEditor()  { _showPaletteEditor.value = true  }
    fun closePaletteEditor() { _showPaletteEditor.value = false }

    fun saveCustomStops(stops: List<ColorStop>) {
        _uiState.update { it.copy(palette = PaletteType.CUSTOM, customStops = stops) }
        viewModelScope.launch { prefs.saveCustomStops(stops) }
        fetchDotPoints()
    }

    // ── Real-time rotation (drag gesture) ────────────────────────────────────────

    /** Called on every drag frame – shows dot cloud, no histogram render. */
    fun rotateBy(deltaYaw: Float, deltaPitch: Float) {
        _uiState.update { s ->
            s.copy(
                yaw   = (s.yaw   + deltaYaw)   % 360f,
                pitch = (s.pitch + deltaPitch).coerceIn(-90f, 90f),
            )
        }
        fetchDotPoints()
    }

    /** Pinch-to-zoom on every gesture frame – shows dot cloud. */
    fun zoomBy(factor: Float) {
        _uiState.update { s ->
            s.copy(zoom = (s.zoom * factor).coerceIn(0.1f, 20f))
        }
        fetchDotPoints()
    }

    private fun fetchDotPoints() {
        // Cancel any in-progress render; dots take over while user is exploring
        renderJob?.cancel()
        renderJob = null
        _uiState.update { it.copy(isRendering = false) }

        dotJob?.cancel()
        dotJob = viewModelScope.launch(Dispatchers.Default) {
            val s   = _uiState.value
            val pts = ChaoscopeEngine.nativeGetPoints(
                attractorType = s.attractorType.ordinal,
                params        = s.params.toFloatArray(),
                nPts          = DOT_POINTS,
                yaw           = s.yaw,
                pitch         = s.pitch,
                roll          = s.roll,
                zoom          = s.zoom,
            )
            _dotPoints.value = pts
        }
        // No finishJob — dots stay visible until a render completes
    }

    /** Called when the finger lifts – just clears dot state, no render. */
    fun finishRotation() {
        finishJob?.cancel()
        _isDragging.value = false
        _dotPoints.value  = null
    }

    /** Reset camera to default orientation and zoom — no render. */
    fun resetCamera() {
        finishJob?.cancel()
        _isDragging.value = false
        _dotPoints.value  = null
        _uiState.update { it.copy(yaw = 0f, pitch = 0f, roll = 0f, zoom = 1f) }
    }

    // ── Explicit renders (user-initiated only) ───────────────────────────────

    fun renderPreview() = scheduleRender(PREVIEW_ITERATIONS, PREVIEW_SIZE, debounce = false)

    fun renderHD() = scheduleRender(HD_ITERATIONS, HD_SIZE, debounce = false)

    /** Cancel an in-flight render. The native call can't be interrupted mid-flight,
     *  but the result is dropped so the UI returns immediately. */
    fun cancelRender() {
        renderJob?.cancel()
        renderJob = null
        _uiState.update { it.copy(isRendering = false) }
    }

    // ── Export PNG ──────────────────────────────────────────────────────────

    fun exportPng(context: Context) {
        val bmp = _uiState.value.bitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,
                        "chaoscope_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Chaoscope")
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore refused to create the file.")

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    if (!ok) throw IllegalStateException("PNG encoder reported failure.")
                } ?: throw IllegalStateException("Could not open output stream.")

                prefs.addRecentExport(uri.toString())
                _uiState.update {
                    it.copy(exportDone = true, exportError = null, lastExportUri = uri.toString())
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportDone    = false,
                        exportError   = e.localizedMessage ?: "Unknown error while saving.",
                        lastExportUri = null,
                    )
                }
            }
        }
    }

    fun clearExportFlag() = _uiState.update {
        it.copy(exportDone = false, exportError = null)
    }

    // ── Randomize ─────────────────────────────────────────────────────────────

    fun randomize() {
        val type    = AttractorType.entries.random()
        val palette = PaletteType.entries.filter { it != PaletteType.CUSTOM }.random()
        val params  = type.paramRanges.map { range ->
            range.start + Random.nextFloat() * (range.endInclusive - range.start)
        }
        _uiState.update {
            it.copy(
                attractorType = type,
                params        = params,
                palette       = palette,
                yaw           = if (type.is3D) Random.nextFloat() * 360f - 180f else 0f,
                pitch         = if (type.is3D) Random.nextFloat() * 180f - 90f  else 0f,
                roll          = 0f,
            )
        }
        fetchDotPoints()
    }

    /** Randomize only params + palette, keeping the current attractor type. */
    fun randomizeParams() {
        val type = _uiState.value.attractorType
        val params = type.paramRanges.map { range ->
            range.start + Random.nextFloat() * (range.endInclusive - range.start)
        }
        _uiState.update {
            it.copy(
                params  = params,
                palette = PaletteType.entries.filter { p -> p != PaletteType.CUSTOM }.random(),
            )
        }
        fetchDotPoints()
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun nativeRenderCall(s: UiState, iterations: Long, size: Int,
                                  boundsExtraPad: Float = 0f): IntArray? {
        val customStops = if (s.palette == PaletteType.CUSTOM) {
            FloatArray(s.customStops.size * 4).also { arr ->
                s.customStops.forEachIndexed { i, stop ->
                    arr[i * 4 + 0] = stop.pos
                    arr[i * 4 + 1] = stop.r
                    arr[i * 4 + 2] = stop.g
                    arr[i * 4 + 3] = stop.b
                }
            }
        } else null
        return ChaoscopeEngine.nativeRender(
            attractorType  = s.attractorType.ordinal,
            params         = s.params.toFloatArray(),
            width          = size,
            height         = size,
            iterations     = iterations,
            yaw            = s.yaw,
            pitch          = s.pitch,
            roll           = s.roll,
            zoom           = s.zoom,
            paletteIndex   = s.palette.ordinal,
            gamma          = s.gamma,
            renderStyle    = s.renderStyle.ordinal,
            bgColor        = s.bgColor.argb,
            boundsExtraPad = boundsExtraPad,
            customStops    = customStops,
        )
    }

    private fun scheduleRender(
        iterations: Long,
        size: Int,
        debounce: Boolean = true,
    ) {
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounce) delay(DEBOUNCE_MS)
            _uiState.update { it.copy(isRendering = true, isRetrying = false) }
            try {
                val s = _uiState.value
                val pixels = nativeRenderCall(s, iterations, size)

                if (pixels == null) {
                    // First attempt blank — retry with 4× iterations and wider bounds
                    _uiState.update { it.copy(isRetrying = true) }
                    val retryPixels = nativeRenderCall(s, iterations * 4, size, boundsExtraPad = 0.15f)
                    _uiState.update { it.copy(isRetrying = false) }
                    if (retryPixels == null) {
                        _uiState.update {
                            it.copy(renderFailedMessage =
                                "Orbit didn't converge — try adjusting parameters")
                        }
                        return@launch
                    }
                    val bitmap = Bitmap.createBitmap(retryPixels, size, size, Bitmap.Config.ARGB_8888)
                    _dotPoints.value = null
                    _uiState.update { it.copy(bitmap = bitmap) }
                } else {
                    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
                    _dotPoints.value = null
                    _uiState.update { it.copy(bitmap = bitmap) }
                }
            } finally {
                _uiState.update { it.copy(isRendering = false, isRetrying = false) }
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS         = 80L
        private const val PERSIST_DEBOUNCE_MS = 500L
        private const val DOT_POINTS          = 60_000

        const val PREVIEW_ITERATIONS = 2_000_000L
        const val PREVIEW_SIZE       = 768

        const val HD_ITERATIONS      = 50_000_000L
        const val HD_SIZE            = 2048

        const val TUTORIAL_STEPS     = 5 // Canvas, AttractorRow, ParamSlider, RenderHD, Palette
    }
}
