package com.chaoscope

import android.app.Application
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaoscope.data.ChaoscopePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt
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

    val userPresets: StateFlow<List<Preset>> = prefs.userPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Session-level splash (resets every cold start via ViewModel lifecycle) ─
    private val _sessionSplashDone = MutableStateFlow(false)
    val sessionSplashDone: StateFlow<Boolean> = _sessionSplashDone.asStateFlow()

    // ── In-app review trigger (emits once when the counter crosses 20) ────────
    private val _reviewTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewTrigger: SharedFlow<Unit> = _reviewTrigger.asSharedFlow()

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

    private var renderJob:       Job? = null
    private var finishJob:       Job? = null
    private var dotJob:          Job? = null
    private var videoExportJob:  Job? = null

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
        wallpaperDone        = false,
        wallpaperError       = null,
        // Animation state is session-only; don't persist mode, keyframes or export status
        animMode             = AnimMode.MORPH,
        keyframeA            = null,
        keyframeB            = null,
        isExportingVideo     = false,
        videoExportProgress  = 0,
        videoExportTotal     = 0,
        videoExportError     = null,
        videoExportUri       = null,
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

    /** Snapshot the current state as a named user preset and persist it. */
    fun saveCurrentAsPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val s = _uiState.value
        val preset = Preset(
            name        = trimmed,
            type        = s.attractorType,
            params      = s.params,
            yaw         = s.yaw,
            pitch       = s.pitch,
            roll        = s.roll,
            zoom        = s.zoom,
            palette     = s.palette,
            renderStyle = s.renderStyle,
            bgColor     = s.bgColor,
        )
        viewModelScope.launch { prefs.saveUserPreset(preset) }
    }

    fun deleteUserPreset(name: String) {
        viewModelScope.launch { prefs.deleteUserPreset(name) }
    }

    /** Apply a curated preset: attractor + params + camera + look, then preview. */
    fun applyPreset(preset: Preset) {
        _uiState.update {
            it.copy(
                attractorType = preset.type,
                params        = preset.params,
                palette       = preset.palette,
                renderStyle   = preset.renderStyle,
                bgColor       = preset.bgColor,
                yaw   = if (preset.type.is3D) preset.yaw   else 0f,
                pitch = if (preset.type.is3D) preset.pitch else 0f,
                roll  = if (preset.type.is3D) preset.roll  else 0f,
                zoom  = preset.zoom,
                bitmap = null,
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
        renderLookPreview()
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
        renderLookPreview()
    }

    fun setDepthCue(value: Float) {
        _uiState.update { it.copy(depthCue = value) }
        renderLookPreview()
    }

    fun setFullRange(enabled: Boolean) {
        _uiState.update { it.copy(fullRange = enabled) }
        renderLookPreview()
    }

    fun setRenderStyle(style: RenderStyle) {
        _uiState.update { it.copy(renderStyle = style) }
        renderLookPreview()
    }

    fun setBgColor(color: BgColor) {
        _uiState.update { it.copy(bgColor = color) }
        renderLookPreview()
    }

    fun setRenderQuality(quality: RenderQuality) {
        _uiState.update { it.copy(renderQuality = quality) }
        renderLookPreview() // re-render preview at the new detail so the change shows
    }

    fun setPreviewDensity(density: PreviewDensity) {
        _uiState.update { it.copy(previewDensity = density) }
        fetchDotPoints()
    }

    fun setTransparentBg(enabled: Boolean) {
        _uiState.update { it.copy(transparentBg = enabled) }
        renderLookPreview()
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
        renderLookPreview()
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
            _dotPoints.value = computeDots(_uiState.value)
        }
        // No finishJob — dots stay visible until a render completes
    }

    /** Compute the projected dot cloud for a state snapshot (shared by preview + auto-rotate). */
    private fun computeDots(s: UiState): FloatArray =
        ChaoscopeEngine.nativeGetPoints(
            attractorType = s.attractorType.ordinal,
            params        = s.params.toFloatArray(),
            nPts          = s.previewDensity.dots,
            yaw           = s.yaw,
            pitch         = s.pitch,
            roll          = s.roll,
            zoom          = s.zoom,
        )

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

    fun renderPreview() {
        scheduleRender(_uiState.value.renderQuality.previewIterations, PREVIEW_SIZE)
    }

    fun renderHD() {
        scheduleRender(_uiState.value.renderQuality.hdIterations, HD_SIZE)
    }

    fun renderHD4K() {
        scheduleRender(_uiState.value.renderQuality.hdIterations, HD_SIZE_4K)
    }

    /** Debounced preview render used when a "look" parameter changes, so palette,
     *  depth, gamma, style, background and full-range changes are visible live. */
    private fun renderLookPreview() {
        scheduleRender(_uiState.value.renderQuality.previewIterations, PREVIEW_SIZE,
                       debounceMs = LOOK_DEBOUNCE_MS)
    }

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

    // ── Set as Wallpaper ────────────────────────────────────────────────────

    fun setWallpaper(context: Context) {
        val bmp = _uiState.value.bitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wm = WallpaperManager.getInstance(context)
                wm.setBitmap(bmp)
                _uiState.update { it.copy(wallpaperDone = true, wallpaperError = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        wallpaperDone  = false,
                        wallpaperError = e.localizedMessage ?: "Could not set wallpaper.",
                    )
                }
            }
        }
    }

    fun clearWallpaperFlag() = _uiState.update {
        it.copy(wallpaperDone = false, wallpaperError = null)
    }

    // ── Animation keyframes ──────────────────────────────────────────────────

    fun setKeyframeA() {
        val s = _uiState.value
        _uiState.update {
            it.copy(keyframeA = AnimKeyframe(
                params = s.params,
                yaw    = s.yaw,
                pitch  = s.pitch,
                roll   = s.roll,
                zoom   = s.zoom,
            ))
        }
    }

    fun setKeyframeB() {
        val s = _uiState.value
        _uiState.update {
            it.copy(keyframeB = AnimKeyframe(
                params = s.params,
                yaw    = s.yaw,
                pitch  = s.pitch,
                roll   = s.roll,
                zoom   = s.zoom,
            ))
        }
    }

    fun setAnimMode(mode: AnimMode) { _uiState.update { it.copy(animMode = mode) } }

    fun setAnimFrames(n: Int) { _uiState.update { it.copy(animFrames = n) } }

    fun setAnimPingPong(enabled: Boolean) { _uiState.update { it.copy(animPingPong = enabled) } }

    // ── Video export ─────────────────────────────────────────────────────────

    fun exportVideo(context: Context) {
        val s = _uiState.value

        // Ping-pong appends the reverse (minus the duplicated last frame)
        val baseFrames  = s.animFrames.coerceAtLeast(2)
        val totalFrames = if (s.animPingPong) baseFrames * 2 - 1 else baseFrames

        // Resolve keyframes for modes that need them
        val kfA: AnimKeyframe?
        val kfB: AnimKeyframe?
        when (s.animMode) {
            AnimMode.MORPH -> {
                kfA = s.keyframeA ?: return
                kfB = s.keyframeB ?: return
            }
            AnimMode.PARAM_SWEEP -> {
                // Snapshot current state as A; auto-generate B with random param variation
                kfA = AnimKeyframe(s.params, s.yaw, s.pitch, s.roll, s.zoom)
                kfB = generateSweepTarget(s)
            }
            AnimMode.ORBIT_TRACE -> { kfA = null; kfB = null }
        }

        _uiState.update {
            it.copy(
                isExportingVideo    = true,
                videoExportProgress = 0,
                videoExportTotal    = totalFrames,
                videoExportError    = null,
                videoExportUri      = null,
            )
        }

        // Register the notification-cancel callback BEFORE starting the service so
        // it is always in place by the time the notification's Cancel button is live.
        VideoExportState.onCancelRequested = { cancelVideoExport() }
        VideoExportState.status.value = ExportStatus.Running(0, totalFrames)
        VideoExportService.start(getApplication())

        videoExportJob = viewModelScope.launch(Dispatchers.Default) {
            // Pre-compute the full orbit point cloud once for ORBIT_TRACE so that
            // every frame is a prefix of the same orbit (true cumulative trace).
            // maxPts scales with render quality so the final frame is noticeably dense.
            val orbitPts: FloatArray? = if (s.animMode == AnimMode.ORBIT_TRACE) {
                val maxPts = when (s.renderQuality) {
                    RenderQuality.DRAFT    -> 100_000
                    RenderQuality.STANDARD -> 200_000
                    RenderQuality.HIGH     -> 350_000
                    RenderQuality.ULTRA    -> 500_000
                }
                ChaoscopeEngine.nativeGetPoints(
                    attractorType = s.attractorType.ordinal,
                    params        = s.params.toFloatArray(),
                    nPts          = maxPts,
                    yaw           = s.yaw,
                    pitch         = s.pitch,
                    roll          = s.roll,
                    zoom          = s.zoom,
                )
            } else null

            try {
                val uri = withContext(Dispatchers.IO) {
                    VideoExporter.export(
                        context    = context,
                        frameCount = totalFrames,
                        fps        = 30,
                        frameSize  = PREVIEW_SIZE,
                        renderFrame = { frameIdx ->
                            if (!isActive) return@export null

                            // t ∈ [0,1], mirrored in the second half for ping-pong
                            val t = if (frameIdx < baseFrames) {
                                frameIdx.toFloat() / (baseFrames - 1).coerceAtLeast(1)
                            } else {
                                (totalFrames - 1 - frameIdx).toFloat() /
                                    (baseFrames - 1).coerceAtLeast(1)
                            }

                            when (s.animMode) {
                                // ── Morph: lerp params + camera A → B ───────────────
                                AnimMode.MORPH, AnimMode.PARAM_SWEEP -> {
                                    val params = kfA!!.params.mapIndexed { i, av ->
                                        av + (kfB!!.params.getOrElse(i) { av } - av) * t
                                    }
                                    val yaw   = kfA.yaw   + (kfB!!.yaw   - kfA.yaw)   * t
                                    val pitch = kfA.pitch + (kfB.pitch - kfA.pitch) * t
                                    val roll  = kfA.roll  + (kfB.roll  - kfA.roll)  * t
                                    val zoom  = kfA.zoom  + (kfB.zoom  - kfA.zoom)  * t
                                    val fs    = s.copy(params = params, yaw = yaw,
                                                       pitch = pitch, roll = roll, zoom = zoom)
                                    val pixels = nativeRenderCall(fs,
                                                     s.renderQuality.previewIterations, PREVIEW_SIZE)
                                        ?: nativeRenderCall(fs,
                                               s.renderQuality.previewIterations * 4, PREVIEW_SIZE,
                                               boundsExtraPad = 0.15f)
                                        ?: return@export null
                                    Bitmap.createBitmap(pixels, PREVIEW_SIZE, PREVIEW_SIZE,
                                                        Bitmap.Config.ARGB_8888)
                                }

                                // ── Orbit Trace: cumulative coloured dot cloud ────────
                                AnimMode.ORBIT_TRACE -> {
                                    val allPts = orbitPts!!
                                    val maxPts = allPts.size / 2
                                    val nPts   = (maxPts * t).roundToInt()
                                                     .coerceIn(1, maxPts)
                                    renderOrbitTraceBitmap(allPts, nPts, s, PREVIEW_SIZE)
                                }
                            }
                        },
                        onProgress = { done, _ ->
                            _uiState.update { it.copy(videoExportProgress = done) }
                            VideoExportState.status.value =
                                ExportStatus.Running(done, totalFrames)
                        },
                    )
                }

                VideoExportState.status.value   = ExportStatus.Done(uri)
                VideoExportState.onCancelRequested = null   // no longer needed
                onActionCompleted()
                _uiState.update {
                    it.copy(
                        isExportingVideo    = false,
                        videoExportProgress = totalFrames,
                        videoExportUri      = uri,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal user-initiated cancellation — state already cleaned up by
                // cancelVideoExport(); just ensure service + callback are cleared.
                VideoExportState.onCancelRequested = null
                VideoExportState.status.value = ExportStatus.Idle
                // Don't re-throw: the Job is already cancelled, and re-throwing
                // would try to propagate through viewModelScope (SupervisorJob —
                // safe, but unnecessary noise).
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Video export failed."
                VideoExportState.onCancelRequested = null
                VideoExportState.status.value = ExportStatus.Error(msg)
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        videoExportError = msg,
                    )
                }
            }
        }
    }

    // ── Video export helpers ──────────────────────────────────────────────────

    /**
     * Generate an [AnimKeyframe] that varies every parameter by up to ±40 % of
     * its slider range, plus a ±30° yaw nudge. Used by [AnimMode.PARAM_SWEEP].
     */
    private fun generateSweepTarget(s: UiState): AnimKeyframe {
        val newParams = s.attractorType.paramRanges.mapIndexed { i, range ->
            val current   = s.params.getOrElse(i) { 0f }
            val variation = (range.endInclusive - range.start) * 0.4f
            (current + kotlin.random.Random.nextFloat() * variation * 2f - variation)
                .coerceIn(range.start, range.endInclusive)
        }
        val yawNudge   = kotlin.random.Random.nextFloat() * 60f - 30f
        val pitchNudge = kotlin.random.Random.nextFloat() * 30f - 15f
        return AnimKeyframe(
            params = newParams,
            yaw    = s.yaw + if (s.attractorType.is3D) yawNudge else 0f,
            pitch  = (s.pitch + if (s.attractorType.is3D) pitchNudge else 0f)
                         .coerceIn(-90f, 90f),
            roll   = s.roll,
            zoom   = s.zoom,
        )
    }

    /**
     * Render [nPts] points from [pts] (a prefix of the full orbit) to a [Bitmap]
     * with each dot coloured by its position in the current palette.
     */
    private fun renderOrbitTraceBitmap(
        pts:   FloatArray,
        nPts:  Int,
        s:     UiState,
        size:  Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(s.bgColor.argb)

        val paint = Paint().apply {
            strokeWidth = 3f
            strokeCap   = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val stops = if (s.palette == PaletteType.CUSTOM) s.customStops
                    else builtInPaletteStops[s.palette] ?: builtInPaletteStops[PaletteType.NEBULA]!!
        val sortedStops = stops.sortedBy { it.pos }

        val halfW = size * 0.5f
        val halfH = size * 0.5f

        var i     = 0
        var ptIdx = 0
        while (i + 1 < pts.size && ptIdx < nPts) {
            val x = halfW + pts[i]     * halfW
            val y = halfH + pts[i + 1] * halfH
            val t = ptIdx.toFloat() / nPts.coerceAtLeast(1)
            val (r, g, b) = samplePaletteRgb(sortedStops, t)
            paint.color = android.graphics.Color.argb(
                230,
                (r * 255f).toInt().coerceIn(0, 255),
                (g * 255f).toInt().coerceIn(0, 255),
                (b * 255f).toInt().coerceIn(0, 255),
            )
            canvas.drawPoint(x, y, paint)
            i     += 2
            ptIdx += 1
        }
        return bitmap
    }

    /** Linearly interpolate between palette colour stops. [stops] must be sorted by pos. */
    private fun samplePaletteRgb(
        stops: List<ColorStop>,
        t:     Float,
    ): Triple<Float, Float, Float> {
        if (stops.isEmpty()) return Triple(1f, 1f, 1f)
        val t01   = t.coerceIn(0f, 1f)
        val hiIdx = stops.indexOfFirst { it.pos >= t01 }.takeIf { it >= 0 } ?: stops.lastIndex
        val loIdx = (hiIdx - 1).coerceAtLeast(0)
        if (hiIdx == loIdx) {
            val c = stops[loIdx]; return Triple(c.r, c.g, c.b)
        }
        val lo    = stops[loIdx]
        val hi    = stops[hiIdx]
        val range = hi.pos - lo.pos
        val f     = if (range < 1e-6f) 0f else (t01 - lo.pos) / range
        return Triple(lo.r + f * (hi.r - lo.r), lo.g + f * (hi.g - lo.g), lo.b + f * (hi.b - lo.b))
    }

    fun cancelVideoExport() {
        VideoExportState.onCancelRequested = null  // prevent double-invoke
        videoExportJob?.cancel()
        videoExportJob = null
        VideoExportState.status.value = ExportStatus.Idle  // signals service to stop
        _uiState.update {
            it.copy(isExportingVideo = false, videoExportProgress = 0)
        }
    }

    fun clearVideoExportFlag() = _uiState.update {
        it.copy(videoExportError = null, videoExportUri = null)
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
            depthCue       = if (s.attractorType.is3D) s.depthCue else 0f,
            fullRange      = if (s.fullRange) 1 else 0,
            customStops    = customStops,
            transparentBg  = if (s.transparentBg) 1 else 0,
        )
    }

    /**
     * Call after every successful render or completed video export.
     * Increments the persistent counter; fires [reviewTrigger] exactly once
     * when the count first reaches [REVIEW_TRIGGER_COUNT].
     * The Play API applies its own quota, so it won't show the dialog every time.
     */
    private fun onActionCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            if (prefs.isReviewTriggered()) return@launch
            val count = prefs.incrementRenderExportCount()
            if (count >= REVIEW_TRIGGER_COUNT) {
                prefs.setReviewTriggered()
                _reviewTrigger.tryEmit(Unit)
            }
        }
    }

    private fun scheduleRender(
        iterations: Long,
        size: Int,
        debounceMs: Long = 0L,
    ) {
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounceMs > 0L) delay(debounceMs)
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
                    onActionCompleted()
                } else {
                    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
                    _dotPoints.value = null
                    _uiState.update { it.copy(bitmap = bitmap) }
                    onActionCompleted()
                }
            } finally {
                _uiState.update { it.copy(isRendering = false, isRetrying = false) }
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS         = 80L
        private const val LOOK_DEBOUNCE_MS    = 300L
        private const val PERSIST_DEBOUNCE_MS = 500L

        // Iteration counts now come from RenderQuality; only the canvas sizes are fixed.
        const val PREVIEW_SIZE       = 768
        const val HD_SIZE            = 2048
        const val HD_SIZE_4K         = 3840

        const val TUTORIAL_STEPS          = 5 // Canvas, AttractorRow, ParamSlider, RenderHD, Palette
        private const val REVIEW_TRIGGER_COUNT = 20
    }
}
