package com.chaoscope

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders and caches small preview thumbnails for curated/user presets.
 *
 * Thumbnails are generated lazily on first request through the same native
 * engine the live preview uses, kept in a small in-memory [LruCache], and
 * persisted as PNGs under `cacheDir/preset_thumbs/` so they survive restarts.
 * Only the handful of presets for the currently-selected attractor are ever
 * requested at once, so generation cost is negligible and one-time.
 */
object PresetThumbnails {

    private const val SIZE        = 128
    private const val ITERATIONS  = 1_500_000L
    private const val DIR         = "preset_thumbs"

    private val mem  = LruCache<String, Bitmap>(64)
    private val lock = Mutex()

    /** Stable cache key from every field that affects the rendered image. */
    private fun keyOf(p: Preset): String = buildString {
        append(p.type.ordinal); append('_')
        append(p.params.joinToString("_")); append('_')
        append(p.palette.ordinal); append('_')
        append(p.renderStyle.ordinal); append('_')
        append(p.bgColor.ordinal); append('_')
        append(p.yaw); append('_'); append(p.pitch); append('_')
        append(p.roll); append('_'); append(p.zoom)
    }.hashCode().toString()

    /**
     * Returns a cached thumbnail for [preset], rendering it if necessary.
     * Returns null only if the orbit failed to converge even on retry.
     */
    suspend fun get(context: Context, preset: Preset): Bitmap? = withContext(Dispatchers.Default) {
        val key = keyOf(preset)
        mem.get(key)?.let { return@withContext it }

        // Serialize generation so concurrent requests for the same attractor's
        // presets don't render the same image twice.
        lock.withLock {
            mem.get(key)?.let { return@withContext it }

            val file = File(File(context.cacheDir, DIR), "$key.png")
            if (file.exists()) {
                BitmapFactory.decodeFile(file.path)?.let {
                    mem.put(key, it)
                    return@withContext it
                }
            }

            val bitmap = render(preset) ?: return@withContext null
            mem.put(key, bitmap)
            runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap
        }
    }

    /** Renders a single preset, mirroring the ViewModel's blank-render retry. */
    private fun render(p: Preset): Bitmap? {
        val is3D = p.type.is3D
        fun attempt(iterations: Long, pad: Float): IntArray? =
            ChaoscopeEngine.nativeRender(
                attractorType  = p.type.ordinal,
                params         = p.params.toFloatArray(),
                width          = SIZE,
                height         = SIZE,
                iterations     = iterations,
                yaw            = if (is3D) p.yaw   else 0f,
                pitch          = if (is3D) p.pitch else 0f,
                roll           = if (is3D) p.roll  else 0f,
                zoom           = p.zoom,
                paletteIndex   = p.palette.ordinal,
                gamma          = 1f,
                renderStyle    = p.renderStyle.ordinal,
                bgColor        = p.bgColor.argb,
                boundsExtraPad = pad,
                depthCue       = if (is3D) 0.5f else 0f,
                fullRange      = 1,
                customStops    = null,
                transparentBg  = 0,
            )

        val pixels = attempt(ITERATIONS, 0f)
            ?: attempt(ITERATIONS * 4, 0.15f)
            ?: return null
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }
}
