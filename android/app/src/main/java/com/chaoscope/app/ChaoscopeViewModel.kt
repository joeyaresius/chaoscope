package com.chaoscope

import android.app.Application
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import com.chaoscope.R
import com.chaoscope.ui.ThemeBackgroundRenderer
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
enum class TutorialTarget { Canvas, AttractorRow, ParamSlider, RenderButton, RenderHdButton, PaletteRow }

data class TutorialAnchors(
    val canvas: Rect?         = null,
    val attractorRow: Rect?   = null,
    val paramSlider: Rect?    = null,
    val renderButton: Rect?   = null,
    val renderHdButton: Rect? = null,
    val paletteRow: Rect?     = null,
)

@OptIn(FlowPreview::class)
class ChaoscopeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ChaoscopePreferences(app)

    // Starts as CpuAttractorRenderer (zero main-thread cost); upgraded to
    // GpuAttractorRenderer on a background thread in init once the capability
    // probe completes.  @Volatile ensures the swap is visible to all threads.
    @Volatile private var renderer: AttractorRenderer = CpuAttractorRenderer()

    private val _gpuSupported = MutableStateFlow(false)
    val gpuSupported: StateFlow<Boolean> = _gpuSupported.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Pre-bucketed dot cloud: heavy O(n) work done on Dispatchers.Default so
    // the Canvas draw thread only does a fast coord-scale pass per bucket.
    private val _bucketedDots = MutableStateFlow<BucketedDots?>(null)
    val bucketedDots: StateFlow<BucketedDots?> = _bucketedDots.asStateFlow()

    // Palette colours for the dot preview, sampled across the active palette.
    // Rebuilt on palette / custom-stop changes; lets colour edits recolour the
    // live dots instead of triggering a full histogram render.
    private val _paletteLut = MutableStateFlow(IntArray(0))
    val paletteLut: StateFlow<IntArray> = _paletteLut.asStateFlow()

    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    // Decoded user-picked background photo (BgColor.IMAGE). Drawn behind the live
    // preview and composited into rendered/exported bitmaps.
    private val _customBgBitmap = MutableStateFlow<Bitmap?>(null)
    val customBgBitmap: StateFlow<Bitmap?> = _customBgBitmap.asStateFlow()

    val galleryEntries: StateFlow<List<GalleryEntry>> = prefs.galleryEntries
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val userPresets: StateFlow<List<Preset>> = prefs.userPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Initial value true so veterans never see the first-render FAB pulse flash
    // while the persisted value loads; genuinely new users flip to false quickly.
    val hasEverRendered: StateFlow<Boolean> = prefs.hasEverRendered
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Attractor of the Day — null until the date-seeded candidate is validated
    // (a thumbnail render on a background thread; cached, so usually instant).
    private val _dailyPreset = MutableStateFlow<Preset?>(null)
    val dailyPreset: StateFlow<Preset?> = _dailyPreset.asStateFlow()

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

    // State that produced the current bitmap, captured when a render completes.
    // The gallery stores this (not the live state) so an export made after the
    // user tweaks sliders without re-rendering still reopens the rendered look.
    private var lastRenderedPreset: Preset? = null

    init {
        // GPU capability probe + context init on a background thread so the main
        // thread is never blocked and the loading spinner can animate freely.
        // Runs in parallel with the data-load launch below.
        viewModelScope.launch(Dispatchers.Default) {
            if (GlesCapabilities.supportsComputeShaders(app)) {
                renderer = GpuAttractorRenderer()
                _gpuSupported.value = true
            }
        }

        // Resolve today's attractor off the main thread (validation render is
        // thumbnail-sized and disk-cached after the first call of the day).
        viewModelScope.launch(Dispatchers.Default) {
            _dailyPreset.value = DailyAttractor.preset(app)
        }

        viewModelScope.launch {
            // Restore last persisted parameters before the first dot-preview fetch.
            prefs.loadLastState()?.let { saved ->
                _uiState.update { saved }
                // Decode the persisted background photo, if any, off the main thread.
                saved.customBgPath?.let { path ->
                    val bmp = withContext(Dispatchers.Default) { decodeBgImage(path) }
                    _customBgBitmap.value = bmp
                }
            }
            rebuildPaletteLut()
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
        renderProgress       = -1f,
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
        val preset = _uiState.value.toPreset(trimmed)
        viewModelScope.launch { prefs.saveUserPreset(preset) }
    }

    fun deleteUserPreset(name: String) {
        viewModelScope.launch { prefs.deleteUserPreset(name) }
    }

    /**
     * Remove a gallery entry; with [deleteFile] also delete the exported file
     * from MediaStore. Deleting media this app created needs no permission; a
     * file contributed by an older install throws SecurityException — we drop
     * the entry anyway and the file simply stays in Pictures/Movies.
     */
    fun deleteGalleryEntry(uri: String, deleteFile: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFile) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .delete(Uri.parse(uri), null, null)
                }
            }
            prefs.deleteGalleryEntry(uri)
        }
    }

    /** Apply a curated preset: attractor + params + camera + look, then preview. */
    fun applyPreset(preset: Preset) {
        snapshotForUndo()
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
        rebuildPaletteLut()   // preset carries its own palette — recolour the dots
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
        // Colour change only — recolour the live dots, don't run a full render.
        rebuildPaletteLut()
        fetchDotPoints()
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

    fun setCustomBgColor(argb: Int) {
        _uiState.update { it.copy(bgColor = BgColor.CUSTOM, customBgArgb = argb) }
        renderLookPreview()
    }

    /**
     * Copy a user-picked photo into app storage, decode it (capped at 2048 px),
     * and switch the background to [BgColor.IMAGE]. Runs entirely off the main
     * thread; the path persists so the choice survives restarts.
     */
    fun onPickBackgroundImage(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(app.filesDir, "custom_bg.png")
            val copied = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied) return@launch
            val bmp = decodeBgImage(dest.absolutePath) ?: return@launch
            _customBgBitmap.value = bmp
            _uiState.update { it.copy(bgColor = BgColor.IMAGE, customBgPath = dest.absolutePath) }
            renderLookPreview()
        }
    }

    /** Decode a background photo from [path], down-sampled so its longest side is ≤ 2048 px. */
    private fun decodeBgImage(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > 2048) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

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
                TutorialTarget.RenderButton  -> a.copy(renderButton = rect)
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
        // Colour change only — recolour the live dots, don't run a full render.
        rebuildPaletteLut()
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
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val pts = computeDots(_uiState.value)
            // Bucket by depth here on the background thread so the Canvas draw
            // thread only has to do a fast coord-scale pass — no Offset boxing.
            _bucketedDots.value = bucketDots(pts, _paletteLut.value)
        }
        // No finishJob — dots stay visible until a render completes
    }

    /**
     * Groups raw dot triples [u, v, depth, …] into per-palette-bucket float
     * arrays [u0, v0, u1, v1, …] in normalised [-1, 1] space.
     * Two linear passes (count then fill) with exact-size allocation avoids
     * ArrayList resizing and Offset boxing entirely.
     */
    private fun bucketDots(pts: FloatArray, lut: IntArray): BucketedDots {
        if (lut.isEmpty()) {
            // Palette not ready yet — single white bucket, no depth colour
            val uvs = FloatArray(pts.size / 3 * 2)
            var si = 0; var di = 0
            while (si < pts.size - 2) { uvs[di++] = pts[si++]; uvs[di++] = pts[si++]; si++ }
            return BucketedDots(arrayOf(uvs), intArrayOf(0xFFFFFFFF.toInt()))
        }
        val n       = lut.size
        val lastIdx = n - 1
        // Pass 1: count per bucket
        val counts = IntArray(n)
        var i = 0
        while (i < pts.size - 2) {
            counts[((pts[i + 2].coerceIn(0f, 1f)) * lastIdx).toInt()]++
            i += 3
        }
        // Allocate exact-size arrays — no wasted memory, no resizing
        val buckets = Array(n) { b -> FloatArray(counts[b] * 2) }
        val heads   = IntArray(n)
        // Pass 2: fill
        i = 0
        while (i < pts.size - 2) {
            val b = ((pts[i + 2].coerceIn(0f, 1f)) * lastIdx).toInt()
            val h = heads[b]
            buckets[b][h]     = pts[i]
            buckets[b][h + 1] = pts[i + 1]
            heads[b] = h + 2
            i += 3
        }
        return BucketedDots(buckets, lut)
    }

    /** Compute the projected dot cloud (u,v,depth triples) for a state snapshot. */
    private fun computeDots(s: UiState): FloatArray =
        renderer.getPointsDepth(
            attractorType = s.attractorType.ordinal,
            params        = s.params.toFloatArray(),
            nPts          = s.previewDensity.dots,
            yaw           = s.yaw,
            pitch         = s.pitch,
            roll          = s.roll,
            zoom          = s.zoom,
        )

    /** Flatten custom palette stops to [pos,r,g,b, ...] for native; null for built-ins. */
    private fun customStopsArray(s: UiState): FloatArray? =
        if (s.palette == PaletteType.CUSTOM) {
            FloatArray(s.customStops.size * 4).also { arr ->
                s.customStops.forEachIndexed { i, stop ->
                    arr[i * 4 + 0] = stop.pos
                    arr[i * 4 + 1] = stop.r
                    arr[i * 4 + 2] = stop.g
                    arr[i * 4 + 3] = stop.b
                }
            }
        } else null

    /** Resample the dot-preview palette LUT for the current palette/custom stops. */
    private fun rebuildPaletteLut() {
        val s = _uiState.value
        _paletteLut.value = renderer.paletteLut(
            paletteIndex = s.palette.ordinal,
            size         = DOT_LUT_SIZE,
            customStops  = customStopsArray(s),
        )
    }

    /** Called when the finger lifts – just clears dot state, no render. */
    fun finishRotation() {
        finishJob?.cancel()
        _isDragging.value = false
        _bucketedDots.value = null
    }

    /** Reset camera to default orientation and zoom — no render. */
    fun resetCamera() {
        finishJob?.cancel()
        _isDragging.value = false
        _bucketedDots.value = null
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
        val s   = _uiState.value
        val raw = s.bitmap ?: return
        // Composite themed art into the bitmap (live UI shows it via Compose layer,
        // but the saved bitmap has transparent bg pixels — we fill them here).
        val bmp = if (s.bgColor.drawsArtBehind)
            ThemeBackgroundRenderer.compositeOnBitmap(
                s.bgColor, s.customBgArgb, raw, _customBgBitmap.value)
        else raw
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

                prefs.addGalleryEntry(GalleryEntry(
                    uri       = uri.toString(),
                    timestamp = System.currentTimeMillis(),
                    preset    = lastRenderedPreset ?: s.toPreset(),
                ))
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
        val s   = _uiState.value
        val raw = s.bitmap ?: return
        val bmp = if (s.bgColor.drawsArtBehind)
            ThemeBackgroundRenderer.compositeOnBitmap(
                s.bgColor, s.customBgArgb, raw, _customBgBitmap.value)
        else raw
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
        val baseFrames         = s.animFrames.coerceAtLeast(2)
        val totalFrames        = if (s.animPingPong) baseFrames * 2 - 1 else baseFrames
        val totalFramesWithOutro = totalFrames + OUTRO_FRAMES

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
                videoExportTotal    = totalFramesWithOutro,
                videoExportError    = null,
                videoExportUri      = null,
            )
        }

        // Register the notification-cancel callback BEFORE starting the service so
        // it is always in place by the time the notification's Cancel button is live.
        VideoExportState.onCancelRequested = { cancelVideoExport() }
        VideoExportState.status.value = ExportStatus.Running(0, totalFramesWithOutro)
        VideoExportService.start(getApplication())

        videoExportJob = viewModelScope.launch(Dispatchers.Default) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            // Pre-compute the full orbit point cloud once for ORBIT_TRACE so that
            // every frame is a prefix of the same orbit (true cumulative trace).
            // The final frame draws previewIterations points, matching the per-frame
            // density used by the Morph/Sweep render path so all video modes reach the
            // same point budget at the selected render quality.
            val orbitPts: FloatArray? = if (s.animMode == AnimMode.ORBIT_TRACE) {
                val requested = s.renderQuality.previewIterations.toInt()
                val maxPts = if (ORBIT_MAX_POINTS > 0)
                                 requested.coerceAtMost(ORBIT_MAX_POINTS)
                             else requested
                renderer.getPoints(
                    attractorType = s.attractorType.ordinal,
                    params        = s.params.toFloatArray(),
                    nPts          = maxPts,
                    yaw           = s.yaw,
                    pitch         = s.pitch,
                    roll          = s.roll,
                    zoom          = s.zoom,
                )
            } else null

            // Orbit-Trace draw state. The LUT is sampled once; with incremental
            // mode the accumulation bitmap persists across frames and only the new
            // slice of dots is drawn onto it (see ORBIT_TRACE_INCREMENTAL).
            val orbitLut: IntArray =
                if (s.animMode == AnimMode.ORBIT_TRACE)
                    renderer.paletteLut(s.palette.ordinal, DOT_LUT_SIZE, customStopsArray(s))
                else IntArray(0)
            val orbitStableColor = ORBIT_TRACE_INCREMENTAL
            // Ping-pong's reverse half replays the forward frames in reverse (same t,
            // identical images). When the frame count fits the budget we render the
            // forward half incrementally, cache each frame, and re-emit the cache for
            // the reverse half — making ping-pong ~2× faster instead of redrawing.
            val orbitPingPongCache: Array<Bitmap?>? =
                if (s.animMode == AnimMode.ORBIT_TRACE && ORBIT_TRACE_INCREMENTAL &&
                    s.animPingPong && baseFrames <= MAX_PINGPONG_CACHE_FRAMES)
                    arrayOfNulls(baseFrames)
                else null
            // Incremental forward accumulation: non-ping-pong, or ping-pong with cache.
            val orbitIncremental = ORBIT_TRACE_INCREMENTAL &&
                                   (!s.animPingPong || orbitPingPongCache != null)
            // Density-normalised dot alpha — caps the finished trace's brightness so it
            // can't blow out to white. Applies to whichever colour mode is active.
            val orbitDotAlpha =
                if (ORBIT_NORMALIZE_BRIGHTNESS && orbitPts != null)
                    computeOrbitDotAlpha(orbitPts, PREVIEW_SIZE)
                else ORBIT_DOT_ALPHA
            var orbitAccum: Bitmap? = null
            var orbitPrevNPts = 0

            try {
                val uri = withContext(Dispatchers.IO) {
                    VideoExporter.export(
                        context    = context,
                        frameCount = totalFramesWithOutro,
                        fps        = 30,
                        frameSize  = PREVIEW_SIZE,
                        renderFrame = { frameIdx ->
                            if (!isActive) return@export null
                            if (frameIdx >= totalFrames) return@export renderOutroFrame(context, frameIdx - totalFrames)

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
                                    val raw = Bitmap.createBitmap(pixels, PREVIEW_SIZE, PREVIEW_SIZE,
                                                                  Bitmap.Config.ARGB_8888)
                                    if (s.bgColor.drawsArtBehind)
                                        ThemeBackgroundRenderer.compositeOnBitmap(
                                            s.bgColor, s.customBgArgb, raw, _customBgBitmap.value)
                                    else raw
                                }

                                // ── Orbit Trace: cumulative coloured dot cloud ────────
                                AnimMode.ORBIT_TRACE -> {
                                    val allPts = orbitPts!!
                                    val maxPts = allPts.size / 2
                                    val nPts   = (maxPts * t).roundToInt()
                                                     .coerceIn(1, maxPts)
                                    // Colour denominator: full orbit (stable per-dot)
                                    // or the visible trace (rescaling rainbow).
                                    val colorDenom = if (orbitStableColor) maxPts else nPts
                                    // Ping-pong reverse half = frames already drawn on
                                    // the way up (forward frame totalFrames-1-frameIdx).
                                    val isReverse  = orbitPingPongCache != null &&
                                                     frameIdx >= baseFrames

                                    if (isReverse) {
                                        // Replay the cached forward frame — no redraw.
                                        val f = totalFrames - 1 - frameIdx
                                        orbitPingPongCache[f]
                                            ?.copy(Bitmap.Config.ARGB_8888, false)
                                            ?: makeOrbitBaseBitmap(s, PREVIEW_SIZE)
                                    } else if (orbitIncremental) {
                                        // Persistent accumulation: draw only the new
                                        // dots [prevNPts, nPts) onto the kept bitmap.
                                        val accum = orbitAccum
                                            ?: makeOrbitBaseBitmap(s, PREVIEW_SIZE)
                                                .also { orbitAccum = it }
                                        drawOrbitDots(accum, allPts, orbitPrevNPts, nPts,
                                                      colorDenom, orbitLut, orbitDotAlpha,
                                                      PREVIEW_SIZE)
                                        orbitPrevNPts = nPts
                                        // Cache this forward frame for the reverse half,
                                        // then hand the exporter its own copy to recycle.
                                        if (orbitPingPongCache != null) {
                                            orbitPingPongCache[frameIdx] =
                                                accum.copy(Bitmap.Config.ARGB_8888, false)
                                        }
                                        accum.copy(Bitmap.Config.ARGB_8888, false)
                                    } else {
                                        // Full (but bucketed) redraw each frame.
                                        val bmp = makeOrbitBaseBitmap(s, PREVIEW_SIZE)
                                        drawOrbitDots(bmp, allPts, 0, nPts,
                                                      colorDenom, orbitLut, orbitDotAlpha,
                                                      PREVIEW_SIZE)
                                        bmp
                                    }
                                }
                            }
                        },
                        onProgress = { done, _ ->
                            _uiState.update { it.copy(videoExportProgress = done) }
                            VideoExportState.status.value =
                                ExportStatus.Running(done, totalFramesWithOutro)
                        },
                    )
                }

                VideoExportState.status.value   = ExportStatus.Done(uri)
                VideoExportState.onCancelRequested = null   // no longer needed
                prefs.addGalleryEntry(GalleryEntry(
                    uri       = uri,
                    timestamp = System.currentTimeMillis(),
                    preset    = s.toPreset(),
                    isVideo   = true,
                ))
                onActionCompleted()
                _uiState.update {
                    it.copy(
                        isExportingVideo    = false,
                        videoExportProgress = totalFramesWithOutro,
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
            } finally {
                // Free the persistent Orbit-Trace accumulation bitmap + ping-pong cache.
                orbitAccum?.recycle()
                orbitAccum = null
                orbitPingPongCache?.forEachIndexed { i, bmp ->
                    bmp?.recycle()
                    orbitPingPongCache[i] = null
                }
            }
        }
    }

    // ── Video export helpers ──────────────────────────────────────────────────

    private fun renderOutroFrame(context: Context, outroIdx: Int): Bitmap {
        val size   = PREVIEW_SIZE
        val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Fade in over first 8 frames (~0.27 s at 30fps)
        val alpha = if (outroIdx < 8) (outroIdx * 255 / 7).coerceIn(0, 255) else 255

        canvas.drawColor(Color.parseColor("#FF080818"))

        // Lorenz butterfly icon centred in the upper portion
        val iconSize = (size * 0.35f).toInt()
        val iconLeft = (size - iconSize) / 2
        val iconTop  = (size * 0.20f).toInt()
        ContextCompat.getDrawable(context, R.drawable.ic_launcher)?.apply {
            setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            this.alpha = alpha
            draw(canvas)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        // "Made with" — small muted label
        paint.color    = Color.parseColor("#FFAAAAAA")
        paint.alpha    = alpha
        paint.textSize = size * 0.048f
        paint.typeface = Typeface.DEFAULT
        val labelY = iconTop + iconSize + size * 0.08f
        canvas.drawText("Made with", size / 2f, labelY, paint)

        // "Chaoscope" — large bold cyan
        paint.color    = Color.parseColor("#FF4FC3F7")
        paint.alpha    = alpha
        paint.textSize = size * 0.10f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Chaoscope", size / 2f, labelY + size * 0.13f, paint)

        return bmp
    }

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

    /** Fresh Orbit-Trace bitmap with just the background (theme or solid) drawn. */
    private fun makeOrbitBaseBitmap(s: UiState, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (s.bgColor.drawsArtBehind) {
            ThemeBackgroundRenderer.drawTo(canvas, s.bgColor, s.customBgArgb,
                                           size.toFloat(), size.toFloat(), _customBgBitmap.value)
        } else {
            canvas.drawColor(s.effectiveBgArgb)
        }
        return bitmap
    }

    /**
     * Peak per-pixel dot count for the full orbit at [size] resolution, turned into
     * a per-dot alpha so the finished (all-points) trace tops out near
     * [ORBIT_PEAK_TARGET] instead of clamping to white. Binned at the draw
     * resolution — the scale that actually governs ADD-blend saturation.
     */
    private fun computeOrbitDotAlpha(pts: FloatArray, size: Int): Int {
        val n = pts.size / 2
        if (n == 0) return ORBIT_DOT_ALPHA
        val bins = IntArray(size * size)
        val half = size * 0.5f
        var maxBin = 0
        var i = 0
        while (i < n) {
            val px = (half + pts[i * 2]     * half).toInt()
            val py = (half + pts[i * 2 + 1] * half).toInt()
            if (px in 0 until size && py in 0 until size) {
                val c = ++bins[py * size + px]
                if (c > maxBin) maxBin = c
            }
            i++
        }
        if (maxBin <= 0) return ORBIT_DOT_ALPHA
        return (ORBIT_PEAK_TARGET / maxBin).coerceIn(1, ORBIT_DOT_ALPHA)
    }

    /**
     * Draw the orbit dots in index range [[fromIdx], [toIdx]) onto [bitmap] with
     * additive blending. Dots are bucketed by palette index ([lut]) so each colour
     * is a single batched [Canvas.drawPoints] call instead of one Skia call per dot.
     * Colour comes from `idx / colorDenom` — pass `maxPts` for a stable per-dot
     * colour (incremental) or the visible `nPts` for the rescaling rainbow.
     * [dotAlpha] is the additive weight per dot (see [computeOrbitDotAlpha]).
     */
    private fun drawOrbitDots(
        bitmap:     Bitmap,
        pts:        FloatArray,
        fromIdx:    Int,
        toIdx:      Int,
        colorDenom: Int,
        lut:        IntArray,
        dotAlpha:   Int,
        size:       Int,
    ) {
        if (toIdx <= fromIdx || lut.isEmpty()) return
        val nb      = lut.size
        val lastIdx = nb - 1
        val denom   = colorDenom.coerceAtLeast(1).toFloat()

        // Pass 1: count points per colour bucket.
        val counts = IntArray(nb)
        run {
            var idx = fromIdx
            while (idx < toIdx) {
                val b = ((idx / denom).coerceIn(0f, 1f) * lastIdx).toInt()
                counts[b]++
                idx++
            }
        }
        // Pass 2: fill exact-size per-bucket coordinate arrays (x0,y0,x1,y1,…).
        val buckets = Array(nb) { FloatArray(counts[it] * 2) }
        val heads   = IntArray(nb)
        val halfW   = size * 0.5f
        val halfH   = size * 0.5f
        run {
            var idx = fromIdx
            while (idx < toIdx) {
                val b = ((idx / denom).coerceIn(0f, 1f) * lastIdx).toInt()
                val h = heads[b]
                buckets[b][h]     = halfW + pts[idx * 2]     * halfW
                buckets[b][h + 1] = halfH + pts[idx * 2 + 1] * halfH
                heads[b] = h + 2
                idx++
            }
        }

        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            strokeWidth = 1.5f
            strokeCap   = Paint.Cap.ROUND
            isAntiAlias = true
            xfermode    = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.ADD
            )
        }
        for (b in 0 until nb) {
            val arr = buckets[b]
            if (arr.isEmpty()) continue
            // ADD blend with low alpha → dense pile-ups glow toward white.
            paint.color = (dotAlpha shl 24) or (lut[b] and 0x00FFFFFF)
            canvas.drawPoints(arr, paint)
        }
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

    // ── Randomize / undo ─────────────────────────────────────────────────────

    // Single-level undo: the visual state right before the last randomize or
    // preset apply, so a shape the user liked is never lost to one stray tap.
    private var undoState: UiState? = null

    private fun snapshotForUndo() {
        undoState = _uiState.value
    }

    /** Restore the visual state captured before the last randomize / preset apply. */
    fun undoLastApply() {
        val snap = undoState ?: return
        undoState = null
        _uiState.update { cur ->
            cur.copy(
                attractorType = snap.attractorType,
                params        = snap.params,
                palette       = snap.palette,
                customStops   = snap.customStops,
                renderStyle   = snap.renderStyle,
                bgColor       = snap.bgColor,
                yaw           = snap.yaw,
                pitch         = snap.pitch,
                roll          = snap.roll,
                zoom          = snap.zoom,
                gamma         = snap.gamma,
                depthCue      = snap.depthCue,
                fullRange     = snap.fullRange,
                bitmap        = null,
            )
        }
        rebuildPaletteLut()
        fetchDotPoints()
    }

    fun randomize() {
        snapshotForUndo()
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
        rebuildPaletteLut()   // palette changed — recolour the dot preview
        fetchDotPoints()
    }

    /** Randomize only params + palette, keeping the current attractor type. */
    fun randomizeParams() {
        snapshotForUndo()
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
        rebuildPaletteLut()   // palette changed — recolour the dot preview
        fetchDotPoints()
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun nativeRenderCall(s: UiState, iterations: Long, size: Int,
                                  boundsExtraPad: Float = 0f): IntArray? {
        val customStops = customStopsArray(s)
        return renderer.render(
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
            // For themed backgrounds the Compose layer draws the art; the native
            // bitmap renders with a transparent background so the theme shows through.
            bgColor        = if (s.bgColor.drawsArtBehind) 0xFF000000.toInt() else s.effectiveBgArgb,
            boundsExtraPad = boundsExtraPad,
            depthCue       = if (s.attractorType.is3D) s.depthCue else 0f,
            fullRange      = if (s.fullRange) 1 else 0,
            customStops    = customStops,
            transparentBg  = if (s.transparentBg || s.bgColor.drawsArtBehind) 1 else 0,
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
            if (!hasEverRendered.value) prefs.setHasRendered()
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
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            if (debounceMs > 0L) delay(debounceMs)
            _uiState.update { it.copy(isRendering = true, isRetrying = false) }

            // Determinate progress for HD/4K renders only — previews are too
            // quick to matter. The native CPU path counts iteration steps; a GPU
            // render never reports (stays -1), so the UI keeps its spinner.
            var progressJob: Job? = null
            if (size >= HD_SIZE) {
                ChaoscopeEngine.nativeRenderProgressReset()
                progressJob = launch {
                    while (isActive) {
                        delay(PROGRESS_POLL_MS)
                        val p = ChaoscopeEngine.nativeRenderProgress()
                        _uiState.update { it.copy(renderProgress = p) }
                    }
                }
            }
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
                    _bucketedDots.value = null
                    lastRenderedPreset = s.toPreset()
                    _uiState.update { it.copy(bitmap = bitmap) }
                    onActionCompleted()
                } else {
                    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
                    _bucketedDots.value = null
                    lastRenderedPreset = s.toPreset()
                    _uiState.update { it.copy(bitmap = bitmap) }
                    onActionCompleted()
                }
            } finally {
                progressJob?.cancel()
                _uiState.update {
                    it.copy(isRendering = false, isRetrying = false, renderProgress = -1f)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderer.close()
    }

    companion object {
        private const val DEBOUNCE_MS         = 80L
        private const val LOOK_DEBOUNCE_MS    = 300L
        private const val PERSIST_DEBOUNCE_MS = 500L
        private const val PROGRESS_POLL_MS    = 200L

        // Outro appended to every video export (1 s at 30 fps).
        private const val OUTRO_FRAMES = 30

        // Iteration counts now come from RenderQuality; only the canvas sizes are fixed.
        const val PREVIEW_SIZE       = 768
        const val HD_SIZE            = 2048
        const val HD_SIZE_4K         = 3840

        // Number of colour samples in the dot-preview palette LUT.
        private const val DOT_LUT_SIZE = 64

        // Per-dot alpha cap for the Orbit-Trace cloud (ADD blend → density glow).
        // When ORBIT_NORMALIZE_BRIGHTNESS is on, the actual alpha is scaled down from
        // this toward the measured peak density so the trace can't blow out to white.
        private const val ORBIT_DOT_ALPHA = 28

        // Target accumulated value (0–255) for the single densest pixel once the full
        // orbit is drawn. < 255 leaves a little colour headroom in the brightest core.
        // Raise toward 255 for punchier output, lower for a softer finish.
        private const val ORBIT_PEAK_TARGET = 220

        // ── Orbit-Trace tuning ─────────────────────────────────────────────────
        // Colour mode:
        //   false → ORIGINAL look: the gradient rescales to the visible trace
        //           (t = idx / nPts) so the full rainbow always spans it and the
        //           leading edge stays bright — the "building sweep". Every frame is
        //           a full (but bucketed) redraw, sped up instead by ORBIT_MAX_POINTS.
        //   true  → colour each dot by its absolute orbit position (t = idx / maxPts).
        //           Stable per-dot colour → unlocks incremental accumulation + the
        //           ping-pong frame cache (fastest), but the palette's dark start
        //           reads dim and the building sweep is lost.
        private const val ORBIT_TRACE_INCREMENTAL = false

        // Cap on the number of orbit points the trace draws (0 = use previewIterations
        // uncapped). Fewer points → less ADD-blend pile-up (tames end-of-trace
        // brightness) and a faster per-frame redraw. The lever behind the original look.
        private const val ORBIT_MAX_POINTS = 2_000_000

        // Scale per-dot alpha to the measured peak density so the finished trace tops
        // out near ORBIT_PEAK_TARGET instead of clipping to white. Safe to combine with
        // either colour mode; turn off for the exact legacy fixed-alpha brightness.
        private const val ORBIT_NORMALIZE_BRIGHTNESS = true

        // Max ping-pong base frame count to cache for reverse-half replay (incremental
        // mode only). Each cached frame is PREVIEW_SIZE² ARGB (~2.3 MB at 768). 64
        // covers all UI options (15/30/60); above this, ping-pong redraws each frame.
        private const val MAX_PINGPONG_CACHE_FRAMES = 64

        const val TUTORIAL_STEPS          = 5 // Canvas, AttractorRow, ParamSlider, RenderHD, Palette
        private const val REVIEW_TRIGGER_COUNT = 20
    }
}
