package com.chaoscope.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.chaoscope.BgColor
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Android-Canvas mirror of [ThemedBackground].
 *
 * Used to composite procedural theme art into export bitmaps (PNG / video frames)
 * where the Compose layer is absent.  Uses the same deterministic seeds as
 * ThemeBackground.kt, so the exported image exactly matches the on-screen preview.
 *
 * Two entry points:
 *  - [compositeOnBitmap]  — wraps [src] with the theme art (new Bitmap).
 *  - [drawTo]             — draws directly onto an existing Canvas (used by
 *                           orbit-trace so we avoid an extra Bitmap allocation).
 */
object ThemeBackgroundRenderer {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a new ARGB_8888 [Bitmap] with the theme background drawn first and
     * [src] composited on top via SRC_OVER, so the attractor's transparent pixels
     * reveal the procedural art underneath.
     */
    fun compositeOnBitmap(
        bgColor: BgColor, customBgArgb: Int, src: Bitmap,
        customBgBitmap: Bitmap? = null,
    ): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        drawTo(canvas, bgColor, customBgArgb, src.width.toFloat(), src.height.toFloat(), customBgBitmap)
        canvas.drawBitmap(src, 0f, 0f, null)
        return result
    }

    /**
     * Draws the background art for [bgColor] onto [canvas].
     * For solid colours this is a plain rect fill; for themed entries it renders
     * the same procedural art as the Compose [ThemedBackground] composable; for
     * [BgColor.IMAGE] it draws [customBgBitmap] centre-cropped to fill.
     */
    fun drawTo(
        canvas: Canvas, bgColor: BgColor, customBgArgb: Int, w: Float, h: Float,
        customBgBitmap: Bitmap? = null,
    ) {
        val baseArgb = if (bgColor == BgColor.CUSTOM) customBgArgb else bgColor.argb
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        paint.color = baseArgb
        canvas.drawRect(0f, 0f, w, h, paint)
        when (bgColor) {
            BgColor.STARS     -> drawSpaceTheme(canvas, w, h, paint)
            BgColor.FOREST_BG -> drawForestTheme(canvas, w, h, paint)
            BgColor.OCEAN_BG  -> drawOceanTheme(canvas, w, h, paint)
            BgColor.AURORA_BG -> drawAuroraTheme(canvas, w, h, paint)
            BgColor.IMAGE     -> customBgBitmap?.let { drawImageCentreCrop(canvas, it, w, h) }
            else              -> { /* base rect already filled above */ }
        }
    }

    /** Draws [bmp] scaled to cover the w×h canvas, centre-cropped (no distortion). */
    private fun drawImageCentreCrop(canvas: Canvas, bmp: Bitmap, w: Float, h: Float) {
        val scale = maxOf(w / bmp.width, h / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val left = (w - dw) / 2f
        val top  = (h - dh) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh),
                          Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Compose Color(r,g,b,a) float components → Android ARGB int. */
    private fun argb(r: Float, g: Float, b: Float, a: Float): Int =
        Color.argb(
            (a * 255f).toInt().coerceIn(0, 255),
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255),
        )

    // ── Pre-computed element data (same seeds as ThemeBackground.kt) ──────────

    private data class StarDot(val fx: Float, val fy: Float, val r: Float, val alpha: Float)
    private data class Galaxy(val fx: Float, val fy: Float, val fw: Float, val fh: Float,
                               val cr: Float, val cg: Float, val cb: Float)
    private data class Leaf(val fx: Float, val fy: Float, val angle: Float,
                             val scaleX: Float, val scaleY: Float)
    private data class WaveRow(val fyCentre: Float, val amplitude: Float,
                                val wavelength: Float, val phase: Float,
                                val cr: Float, val cg: Float, val cb: Float,
                                val alpha: Float, val strokeFrac: Float)
    private data class AuroraRibbon(val fyCentre: Float, val height: Float,
                                     val wavelength: Float, val phase: Float,
                                     val cr: Float, val cg: Float, val cb: Float)

    // Seed 42 — matches rememberSpaceData()
    private val spaceData: Pair<List<StarDot>, List<Galaxy>> by lazy {
        val rng = Random(42)
        val stars = (1..200).map {
            StarDot(rng.nextFloat(), rng.nextFloat(),
                    rng.nextFloat() * 1.2f + 0.3f,
                    rng.nextFloat() * 0.6f + 0.35f)
        }
        val galaxies = (1..4).map {
            val blue = rng.nextBoolean()
            // Color(0.5f, 0.65f, 1.0f) or Color(0.7f, 0.5f, 1.0f)
            val (r, g, b) = if (blue) Triple(0.5f, 0.65f, 1.0f) else Triple(0.7f, 0.5f, 1.0f)
            Galaxy(rng.nextFloat() * 0.8f + 0.1f, rng.nextFloat() * 0.75f + 0.1f,
                   rng.nextFloat() * 0.09f + 0.06f, rng.nextFloat() * 0.04f + 0.025f,
                   r, g, b)
        }
        Pair(stars, galaxies)
    }

    // Seed 77 — matches rememberLeaves()
    private val leavesData: List<Leaf> by lazy {
        val rng = Random(77)
        (1..28).map {
            Leaf(rng.nextFloat(), rng.nextFloat(),
                 rng.nextFloat() * 180f - 90f,
                 rng.nextFloat() * 0.024f + 0.012f,
                 rng.nextFloat() * 0.040f + 0.018f)
        }
    }

    // Seed 99 — matches rememberWaves()
    private val wavesData: List<WaveRow> by lazy {
        val rng = Random(99)
        val cols = listOf(
            Triple(0.1f, 0.45f, 0.75f),
            Triple(0.1f, 0.55f, 0.8f),
            Triple(0.15f, 0.60f, 0.85f),
        )
        (1..7).map { i ->
            val (r, g, b) = cols[i % cols.size]
            WaveRow(
                fyCentre   = 0.15f + i * 0.11f,
                amplitude  = rng.nextFloat() * 0.015f + 0.010f,
                wavelength = rng.nextFloat() * 0.25f + 0.35f,
                phase      = rng.nextFloat() * 2f * PI.toFloat(),
                cr = r, cg = g, cb = b,
                alpha      = rng.nextFloat() * 0.06f + 0.04f,
                strokeFrac = rng.nextFloat() * 0.004f + 0.002f,
            )
        }
    }

    // Seed 13 — matches rememberAuroraRibbons()
    private val auroraData: List<AuroraRibbon> by lazy {
        val cols = listOf(
            Triple(0.20f, 0.85f, 0.55f),
            Triple(0.15f, 0.90f, 0.80f),
            Triple(0.55f, 0.30f, 0.90f),
            Triple(0.25f, 0.80f, 0.65f),
            Triple(0.70f, 0.40f, 1.00f),
        )
        val rng = Random(13)
        (0..4).map { i ->
            val (r, g, b) = cols[i]
            AuroraRibbon(
                fyCentre   = 0.10f + i * 0.09f + rng.nextFloat() * 0.04f,
                height     = rng.nextFloat() * 0.07f + 0.05f,
                wavelength = rng.nextFloat() * 0.4f + 0.5f,
                phase      = rng.nextFloat() * 2f * PI.toFloat(),
                cr = r, cg = g, cb = b,
            )
        }
    }

    // ── Stars / Space ─────────────────────────────────────────────────────────

    private fun drawSpaceTheme(canvas: Canvas, w: Float, h: Float, paint: Paint) {
        val (stars, galaxies) = spaceData

        // Soft galaxy blobs — 5 concentric ellipses per galaxy
        paint.style = Paint.Style.FILL
        for (g in galaxies) {
            val cx = g.fx * w;  val cy = g.fy * h
            val bw = g.fw * w;  val bh = g.fh * h
            for (layer in 5 downTo 1) {
                val s = layer / 5f
                paint.color = argb(g.cr, g.cg, g.cb, (1f - s) * 0.055f + 0.005f)
                canvas.drawOval(RectF(cx - bw * s, cy - bh * s, cx + bw * s, cy + bh * s), paint)
            }
        }

        // Stars of varying size and brightness
        val maxR = min(w, h) * 0.0040f
        paint.style = Paint.Style.FILL
        for (s in stars) {
            paint.color = argb(1f, 1f, 1f, s.alpha)
            canvas.drawCircle(s.fx * w, s.fy * h, s.r * maxR, paint)
        }

        // A few feature stars with a tiny cross-glow (seed 7 — inline, same as Compose)
        val rng = Random(7)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style     = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }
        repeat(5) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h
            val r = maxR * 2.2f
            paint.color = argb(1f, 1f, 1f, 0.85f)
            canvas.drawCircle(x, y, r, paint)
            for ((len, a) in listOf(r * 3f to 0.15f, r * 1.8f to 0.08f)) {
                strokePaint.color       = argb(1f, 1f, 1f, a)
                strokePaint.strokeWidth = r * 0.6f
                canvas.drawLine(x - len, y, x + len, y, strokePaint)
                canvas.drawLine(x, y - len, x, y + len, strokePaint)
            }
        }
    }

    // ── Forest ────────────────────────────────────────────────────────────────

    private fun drawForestTheme(canvas: Canvas, w: Float, h: Float, paint: Paint) {
        val leaves = leavesData

        // Canopy gradient — same 20 bands as Compose
        val canopyH = h * 0.45f
        paint.style = Paint.Style.FILL
        for (row in 0..20) {
            val frac = row / 20f
            paint.color = argb(0.15f, 0.7f, 0.2f, (1f - frac) * 0.04f)
            canvas.drawRect(0f, frac * canopyH, w, frac * canopyH + canopyH / 20f, paint)
        }

        // Tree trunks — seed 33 inline, matches Compose
        val trunkRng = Random(33)
        paint.style = Paint.Style.FILL
        paint.color = argb(0.04f, 0.10f, 0.04f, 0.55f)
        repeat(4) {
            val tx  = trunkRng.nextFloat() * w
            val tw  = w * (trunkRng.nextFloat() * 0.007f + 0.004f)
            val th  = h * (trunkRng.nextFloat() * 0.45f + 0.35f)
            canvas.drawRect(tx - tw / 2f, h - th, tx + tw / 2f, h, paint)
        }

        // Leaf silhouettes
        val leafColor = argb(0.05f, 0.28f, 0.05f, 0.28f)
        for (l in leaves) {
            canvas.save()
            canvas.translate(l.fx * w, l.fy * h)
            canvas.rotate(l.angle)
            val lw = l.scaleX * w
            val lh = l.scaleY * h
            paint.color = leafColor
            canvas.drawOval(RectF(-lw / 2f, -lh / 2f, lw / 2f, lh / 2f), paint)
            canvas.restore()
        }

        // Dappled light — seed 55
        val lightRng = Random(55)
        val lightColor = argb(0.85f, 0.95f, 0.7f, 0.045f)
        repeat(12) {
            val x = lightRng.nextFloat() * w
            val y = lightRng.nextFloat() * h * 0.35f
            val r = min(w, h) * (lightRng.nextFloat() * 0.025f + 0.010f)
            paint.color = lightColor
            canvas.drawCircle(x, y, r, paint)
        }
    }

    // ── Ocean ─────────────────────────────────────────────────────────────────

    private fun drawOceanTheme(canvas: Canvas, w: Float, h: Float, paint: Paint) {
        val waves = wavesData

        // Depth gradient
        paint.style = Paint.Style.FILL
        for (row in 0..30) {
            val frac = row / 30f
            paint.color = argb(0.05f, 0.45f, 0.80f, (1f - frac) * 0.04f)
            canvas.drawRect(0f, frac * h, w, frac * h + h / 30f, paint)
        }

        // Wave bands — drawn as stroked paths
        val steps = (w / 6f).toInt().coerceAtLeast(60)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        for (wave in waves) {
            val yC  = wave.fyCentre * h
            val amp = wave.amplitude * h
            val wl  = wave.wavelength * w
            strokePaint.color       = argb(wave.cr, wave.cg, wave.cb, wave.alpha)
            strokePaint.strokeWidth = wave.strokeFrac * w
            val path = Path()
            path.moveTo(0f, yC + amp * sin(wave.phase))
            for (s in 1..steps) {
                val x = s.toFloat() / steps * w
                path.lineTo(x, yC + amp * sin((x / wl) * 2f * PI.toFloat() + wave.phase))
            }
            canvas.drawPath(path, strokePaint)
        }

        // Surface shimmer — seed 88
        val shimmerRng = Random(88)
        val shimmerColor = argb(0.85f, 0.95f, 1.0f, 0.06f)
        paint.style = Paint.Style.FILL
        repeat(18) {
            val x = shimmerRng.nextFloat() * w
            val y = shimmerRng.nextFloat() * h * 0.25f
            val r = min(w, h) * (shimmerRng.nextFloat() * 0.012f + 0.004f)
            paint.color = shimmerColor
            canvas.drawCircle(x, y, r, paint)
        }
    }

    // ── Aurora ────────────────────────────────────────────────────────────────

    private fun drawAuroraTheme(canvas: Canvas, w: Float, h: Float, paint: Paint) {
        val ribbons = auroraData
        val steps = (w / 8f).toInt().coerceAtLeast(50)
        paint.style = Paint.Style.FILL

        for (ribbon in ribbons) {
            val yC = ribbon.fyCentre * h
            val rH = ribbon.height * h
            val wl = ribbon.wavelength * w
            val ph = ribbon.phase

            // Main filled wavy band
            val path = Path()
            path.moveTo(0f, yC - rH / 2f + (rH * 0.3f) * sin(ph))
            for (s in 1..steps) {
                val x = s.toFloat() / steps * w
                path.lineTo(x, yC - rH / 2f + (rH * 0.3f) * sin((x / wl) * 2f * PI.toFloat() + ph))
            }
            path.lineTo(w, yC + rH / 2f)
            for (s in steps downTo 0) {
                val x = s.toFloat() / steps * w
                path.lineTo(x, yC + rH / 2f + (rH * 0.2f) * sin((x / wl) * 2f * PI.toFloat() + ph + 0.8f))
            }
            path.close()

            // Outer glow (wider, fainter)
            val glowPath = Path()
            glowPath.moveTo(0f, yC - rH + (rH * 0.4f) * sin(ph))
            for (s in 1..steps) {
                val x = s.toFloat() / steps * w
                glowPath.lineTo(x, yC - rH + (rH * 0.4f) * sin((x / wl) * 2f * PI.toFloat() + ph))
            }
            glowPath.lineTo(w, yC + rH)
            for (s in steps downTo 0) {
                val x = s.toFloat() / steps * w
                glowPath.lineTo(x, yC + rH + (rH * 0.3f) * sin((x / wl) * 2f * PI.toFloat() + ph + 0.8f))
            }
            glowPath.close()

            paint.color = argb(ribbon.cr, ribbon.cg, ribbon.cb, 0.030f)
            canvas.drawPath(glowPath, paint)
            paint.color = argb(ribbon.cr, ribbon.cg, ribbon.cb, 0.090f)
            canvas.drawPath(path, paint)
        }
    }
}
