package com.chaoscope

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.chaoscope.data.ChaoscopePreferences
import com.chaoscope.ui.ThemeBackgroundRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Live wallpaper: the dot-preview point cloud slowly rotating on the home
 * screen. Reuses the CPU preview pipeline (getPointsDepth + palette LUT) —
 * never the full histogram renderer — so a frame costs the same as one drag
 * frame in the editor.
 *
 * Battery discipline:
 *  - draws only while the wallpaper is visible (launcher in front);
 *  - ~12 fps with a slow rotation, LOW preview density;
 *  - in battery saver it shows a static frame, re-checking occasionally.
 */
class ChaoscopeWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ChaosEngine()

    /** Everything one frame needs, resolved once per visibility gain. */
    private class Look(
        val attractorType:  Int,
        val params:         FloatArray,
        val pitch:          Float,
        val roll:           Float,
        val zoom:           Float,
        val baseYaw:        Float,
        val is3D:           Boolean,
        val bgColor:        BgColor,
        val customBgArgb:   Int,
        val customBgBitmap: Bitmap?,
        val lut:            IntArray,
    )

    private inner class ChaosEngine : Engine() {

        private val renderer = CpuAttractorRenderer()
        private val prefs    = ChaoscopePreferences(this@ChaoscopeWallpaperService)

        private var thread:      HandlerThread? = null
        private var drawHandler: Handler?       = null
        @Volatile private var visible = false

        private var look: Look? = null
        private var speedDegPerSec = ChaoscopePreferences.WP_DEFAULT_SPEED
        private var spinOffset     = 0f
        private var lastFrameNanos = 0L

        private val frameRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            thread = HandlerThread("chaoscope-wallpaper").also { it.start() }
            drawHandler = Handler(thread!!.looper)
        }

        override fun onDestroy() {
            drawHandler?.removeCallbacksAndMessages(null)
            drawHandler = null
            thread?.quitSafely()
            thread = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            val h = drawHandler ?: return
            h.removeCallbacks(frameRunnable)
            if (v) h.post {
                // Re-resolve on every visibility gain so editor changes, settings
                // changes and the daily rollover are picked up without restarts.
                reloadLook()
                lastFrameNanos = 0L
                drawFrame()
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?, format: Int, width: Int, height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            val h = drawHandler ?: return
            h.removeCallbacks(frameRunnable)
            if (visible) h.post { drawFrame() }
        }

        /** Resolve source state → Look. Runs on the draw thread; failures keep
         *  the previous look (or leave it null → blank frame, retried next gain). */
        private fun reloadLook() {
            runCatching {
                runBlocking {
                    speedDegPerSec = prefs.wallpaperSpeed.first()
                    val source     = prefs.wallpaperSource.first()

                    look = if (source == ChaoscopePreferences.WP_SOURCE_DAILY) {
                        val p    = DailyAttractor.preset(this@ChaoscopeWallpaperService)
                        val is3D = p.type.is3D
                        Look(
                            attractorType  = p.type.ordinal,
                            params         = p.params.toFloatArray(),
                            pitch          = if (is3D) p.pitch else 0f,
                            roll           = if (is3D) p.roll  else 0f,
                            zoom           = p.zoom,
                            baseYaw        = if (is3D) p.yaw else 0f,
                            is3D           = is3D,
                            bgColor        = p.bgColor,
                            customBgArgb   = p.bgColor.argb,
                            customBgBitmap = null,
                            lut            = renderer.paletteLut(p.palette.ordinal, LUT_SIZE),
                        )
                    } else {
                        val s = prefs.loadLastState() ?: UiState()
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
                        Look(
                            attractorType  = s.attractorType.ordinal,
                            params         = s.params.toFloatArray(),
                            pitch          = s.pitch,
                            roll           = s.roll,
                            zoom           = s.zoom,
                            baseYaw        = s.yaw,
                            is3D           = s.attractorType.is3D,
                            bgColor        = s.bgColor,
                            customBgArgb   = s.customBgArgb,
                            customBgBitmap = if (s.bgColor == BgColor.IMAGE)
                                                 s.customBgPath?.let { decodeBg(it) }
                                             else null,
                            lut            = renderer.paletteLut(
                                                 s.palette.ordinal, LUT_SIZE, customStops),
                        )
                    }
                }
            }
        }

        /** Decode the user's background photo, down-sampled to ≤ 2048 px. */
        private fun decodeBg(path: String): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > 2048) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

        private fun drawFrame() {
            if (!visible) return
            val lk     = look ?: return
            val holder = surfaceHolder ?: return

            val powerSave = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

            // Advance the spin by wall-clock time so speed is fps-independent.
            val now = System.nanoTime()
            if (lastFrameNanos != 0L && !powerSave) {
                spinOffset = (spinOffset +
                    speedDegPerSec * (now - lastFrameNanos) / 1_000_000_000f) % 360f
            }
            lastFrameNanos = now

            // 3-D attractors spin around yaw; flat 2-D ones would collapse
            // edge-on under yaw, so they spin in-plane via roll instead.
            val yaw  = if (lk.is3D) lk.baseYaw + spinOffset else lk.baseYaw
            val roll = if (lk.is3D) lk.roll else lk.roll + spinOffset

            val pts = runCatching {
                renderer.getPointsDepth(
                    attractorType = lk.attractorType,
                    params        = lk.params,
                    nPts          = DOTS,
                    yaw           = yaw,
                    pitch         = lk.pitch,
                    roll          = roll,
                    zoom          = lk.zoom,
                )
            }.getOrNull() ?: return

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                ThemeBackgroundRenderer.drawTo(
                    canvas, lk.bgColor, lk.customBgArgb, w, h, lk.customBgBitmap)
                drawDots(canvas, pts, lk.lut, w, h)
            } finally {
                canvas?.let { c -> runCatching { holder.unlockCanvasAndPost(c) } }
            }

            drawHandler?.postDelayed(
                frameRunnable,
                if (powerSave) POWER_SAVE_PERIOD_MS else FRAME_PERIOD_MS,
            )
        }

        /**
         * Bucket the (u, v, depth) triples by palette index and draw each bucket
         * with one batched drawPoints call — same scheme as the editor preview.
         * Uniform scale (fit) so the whole shape stays visible on tall screens.
         */
        private fun drawDots(
            canvas: Canvas, pts: FloatArray, lut: IntArray, w: Float, h: Float,
        ) {
            if (lut.isEmpty()) return
            val nb      = lut.size
            val lastIdx = nb - 1
            val scale   = minOf(w, h) * 0.5f * FILL_FRACTION
            val cx      = w * 0.5f
            val cy      = h * 0.5f

            val counts = IntArray(nb)
            var i = 0
            while (i < pts.size - 2) {
                counts[(pts[i + 2].coerceIn(0f, 1f) * lastIdx).toInt()]++
                i += 3
            }
            val buckets = Array(nb) { b -> FloatArray(counts[b] * 2) }
            val heads   = IntArray(nb)
            i = 0
            while (i < pts.size - 2) {
                val b = (pts[i + 2].coerceIn(0f, 1f) * lastIdx).toInt()
                val hd = heads[b]
                buckets[b][hd]     = cx + pts[i]     * scale
                buckets[b][hd + 1] = cy + pts[i + 1] * scale
                heads[b] = hd + 2
                i += 3
            }

            val paint = Paint().apply {
                isAntiAlias = true
                strokeCap   = Paint.Cap.ROUND
                // ~1 dp-ish on common wallpaper resolutions; floor keeps low-res visible.
                strokeWidth = maxOf(2f, minOf(w, h) / 400f)
            }
            for (b in 0 until nb) {
                val arr = buckets[b]
                if (arr.isEmpty()) continue
                paint.color = lut[b]
                canvas.drawPoints(arr, paint)
            }
        }
    }

    private companion object {
        const val DOTS                 = 30_000   // PreviewDensity.LOW-class load
        const val LUT_SIZE             = 64
        const val FRAME_PERIOD_MS      = 80L      // ~12 fps
        const val POWER_SAVE_PERIOD_MS = 15_000L  // static frame, occasional recheck
        const val FILL_FRACTION        = 0.92f
    }
}
