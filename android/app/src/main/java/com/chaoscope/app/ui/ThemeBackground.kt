package com.chaoscope.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.chaoscope.BgColor
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Pre-computed element data (one instance per theme activation, held in memory)
// ─────────────────────────────────────────────────────────────────────────────

private data class StarDot(val fx: Float, val fy: Float, val r: Float, val alpha: Float)
private data class Galaxy(val fx: Float, val fy: Float, val fw: Float, val fh: Float,
                          val hue: Color)
private data class Leaf(val fx: Float, val fy: Float, val angle: Float,
                        val scaleX: Float, val scaleY: Float)
private data class WaveRow(val fyCentre: Float, val amplitude: Float,
                           val wavelength: Float, val phase: Float,
                           val color: Color, val alpha: Float, val strokeFrac: Float)
private data class AuroraRibbon(val fyCentre: Float, val height: Float,
                                val wavelength: Float, val phase: Float, val color: Color)

@Composable
private fun rememberSpaceData(): Pair<List<StarDot>, List<Galaxy>> = remember {
    val rng = Random(42)
    val stars = (1..200).map {
        StarDot(rng.nextFloat(), rng.nextFloat(),
                rng.nextFloat() * 1.2f + 0.3f,  // radius fraction
                rng.nextFloat() * 0.6f + 0.35f) // alpha
    }
    val galaxies = (1..4).map {
        val hue = if (rng.nextBoolean())
            Color(0.5f, 0.65f, 1.0f, 1f) else Color(0.7f, 0.5f, 1.0f, 1f)
        Galaxy(rng.nextFloat() * 0.8f + 0.1f, rng.nextFloat() * 0.75f + 0.1f,
               rng.nextFloat() * 0.09f + 0.06f, rng.nextFloat() * 0.04f + 0.025f, hue)
    }
    Pair(stars, galaxies)
}

@Composable
private fun rememberLeaves(): List<Leaf> = remember {
    val rng = Random(77)
    (1..28).map {
        Leaf(rng.nextFloat(), rng.nextFloat(),
             rng.nextFloat() * 180f - 90f,
             rng.nextFloat() * 0.024f + 0.012f,
             rng.nextFloat() * 0.040f + 0.018f)
    }
}

@Composable
private fun rememberWaves(): List<WaveRow> = remember {
    val rng = Random(99)
    val waveCols = listOf(
        Color(0.1f, 0.45f, 0.75f, 1f),
        Color(0.1f, 0.55f, 0.8f,  1f),
        Color(0.15f,0.60f, 0.85f, 1f),
    )
    (1..7).map { i ->
        WaveRow(
            fyCentre    = 0.15f + i * 0.11f,
            amplitude   = rng.nextFloat() * 0.015f + 0.010f,
            wavelength  = rng.nextFloat() * 0.25f + 0.35f,
            phase       = rng.nextFloat() * 2f * PI.toFloat(),
            color       = waveCols[i % waveCols.size],
            alpha       = rng.nextFloat() * 0.06f + 0.04f,
            strokeFrac  = rng.nextFloat() * 0.004f + 0.002f,
        )
    }
}

@Composable
private fun rememberAuroraRibbons(): List<AuroraRibbon> = remember {
    val cols = listOf(
        Color(0.20f, 0.85f, 0.55f, 1f), // green
        Color(0.15f, 0.90f, 0.80f, 1f), // cyan
        Color(0.55f, 0.30f, 0.90f, 1f), // purple
        Color(0.25f, 0.80f, 0.65f, 1f), // teal-green
        Color(0.70f, 0.40f, 1.00f, 1f), // violet
    )
    val rng = Random(13)
    (0..4).map { i ->
        AuroraRibbon(
            fyCentre   = 0.10f + i * 0.09f + rng.nextFloat() * 0.04f,
            height     = rng.nextFloat() * 0.07f + 0.05f,
            wavelength = rng.nextFloat() * 0.4f + 0.5f,
            phase      = rng.nextFloat() * 2f * PI.toFloat(),
            color      = cols[i],
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws the background for the attractor canvas.
 * For solid [BgColor] entries it fills a plain rectangle.
 * For themed entries ([BgColor.isTheme]) it renders procedural art.
 */
@Composable
fun ThemedBackground(bgColor: BgColor, customBgArgb: Int, modifier: Modifier = Modifier) {
    val baseArgb = if (bgColor == BgColor.CUSTOM) customBgArgb else bgColor.argb
    val baseColor = Color((baseArgb.toLong() and 0xFFFFFFFFL))

    when (bgColor) {
        BgColor.STARS -> {
            val (stars, galaxies) = rememberSpaceData()
            Canvas(modifier) { drawSpaceTheme(baseColor, stars, galaxies) }
        }
        BgColor.FOREST_BG -> {
            val leaves = rememberLeaves()
            Canvas(modifier) { drawForestTheme(baseColor, leaves) }
        }
        BgColor.OCEAN_BG -> {
            val waves = rememberWaves()
            Canvas(modifier) { drawOceanTheme(baseColor, waves) }
        }
        BgColor.AURORA_BG -> {
            val ribbons = rememberAuroraRibbons()
            Canvas(modifier) { drawAuroraTheme(baseColor, ribbons) }
        }
        else -> Canvas(modifier) { drawRect(baseColor) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Theme DrawScope implementations
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSpaceTheme(base: Color, stars: List<StarDot>, galaxies: List<Galaxy>) {
    drawRect(base)

    // Soft galaxy blobs — 5 concentric ellipses per galaxy
    for (g in galaxies) {
        val cx = g.fx * size.width
        val cy = g.fy * size.height
        val bw = g.fw * size.width
        val bh = g.fh * size.height
        for (layer in 5 downTo 1) {
            val s = layer / 5f
            drawOval(
                color   = g.hue.copy(alpha = (1f - s) * 0.055f + 0.005f),
                topLeft = Offset(cx - bw * s, cy - bh * s),
                size    = Size(bw * s * 2f, bh * s * 2f),
            )
        }
    }

    // Stars of varying size and brightness
    val maxR = size.minDimension * 0.0040f
    for (s in stars) {
        val r = s.r * maxR
        drawCircle(Color.White.copy(alpha = s.alpha), r, Offset(s.fx * size.width, s.fy * size.height))
    }

    // A few feature stars with a tiny cross-glow
    val rng = Random(7)
    repeat(5) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height
        val r = maxR * 2.2f
        drawCircle(Color.White.copy(alpha = 0.85f), r, Offset(x, y))
        // four-pointed glow
        for (len in listOf(r * 3f, r * 1.8f)) {
            val a = if (len == r * 3f) 0.15f else 0.08f
            drawLine(Color.White.copy(alpha = a), Offset(x - len, y), Offset(x + len, y), r * 0.6f)
            drawLine(Color.White.copy(alpha = a), Offset(x, y - len), Offset(x, y + len), r * 0.6f)
        }
    }
}

private fun DrawScope.drawForestTheme(base: Color, leaves: List<Leaf>) {
    drawRect(base)

    // Vertical gradient overlay — slightly lighter at top (canopy opening)
    val canopyH = size.height * 0.45f
    for (row in 0..20) {
        val frac = row / 20f
        val y = frac * canopyH
        drawRect(
            color   = Color(0.15f, 0.7f, 0.2f, (1f - frac) * 0.04f),
            topLeft = Offset(0f, y),
            size    = Size(size.width, canopyH / 20f),
        )
    }

    // Tree trunks — thin dark vertical lines
    val rng = Random(33)
    val trunkColor = Color(0.04f, 0.10f, 0.04f, 0.55f)
    repeat(4) {
        val x  = rng.nextFloat() * size.width
        val w  = size.width * (rng.nextFloat() * 0.007f + 0.004f)
        val h  = size.height * (rng.nextFloat() * 0.45f + 0.35f)
        drawRect(trunkColor, topLeft = Offset(x - w / 2f, size.height - h), size = Size(w, h))
    }

    // Leaf silhouettes — scaled ovals at varied angles
    val leafBase = Color(0.05f, 0.28f, 0.05f, 0.28f)
    for (l in leaves) {
        withTransform({
            translate(l.fx * size.width, l.fy * size.height)
            rotate(l.angle, pivot = Offset.Zero)
        }) {
            val lw = l.scaleX * size.width
            val lh = l.scaleY * size.height
            drawOval(leafBase, topLeft = Offset(-lw / 2f, -lh / 2f), size = Size(lw, lh))
        }
    }

    // Dappled light: small soft cream circles near top of canvas
    val lightColor = Color(0.85f, 0.95f, 0.7f, 0.045f)
    val rng2 = Random(55)
    repeat(12) {
        val x = rng2.nextFloat() * size.width
        val y = rng2.nextFloat() * size.height * 0.35f
        val r = size.minDimension * (rng2.nextFloat() * 0.025f + 0.010f)
        drawCircle(lightColor, r, Offset(x, y))
    }
}

private fun DrawScope.drawOceanTheme(base: Color, waves: List<WaveRow>) {
    drawRect(base)

    // Depth gradient — lighter blue near surface (top)
    for (row in 0..30) {
        val frac = row / 30f
        drawRect(
            color   = Color(0.05f, 0.45f, 0.80f, (1f - frac) * 0.04f),
            topLeft = Offset(0f, frac * size.height),
            size    = Size(size.width, size.height / 30f),
        )
    }

    // Wave bands
    val steps = (size.width / 6f).toInt().coerceAtLeast(60)
    for (w in waves) {
        val yC = w.fyCentre * size.height
        val amp = w.amplitude * size.height
        val wl  = w.wavelength * size.width
        val sw  = w.strokeFrac * size.width

        val path = Path().apply {
            moveTo(0f, yC + amp * sin(w.phase))
            for (s in 1..steps) {
                val x = s.toFloat() / steps * size.width
                val y = yC + amp * sin((x / wl) * 2f * PI.toFloat() + w.phase)
                lineTo(x, y)
            }
        }
        drawPath(path, w.color.copy(alpha = w.alpha),
                 style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
    }

    // Light shimmer near surface
    val shimmer = Color(0.85f, 0.95f, 1.0f, 0.06f)
    val rng = Random(88)
    repeat(18) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height * 0.25f
        val r = size.minDimension * (rng.nextFloat() * 0.012f + 0.004f)
        drawCircle(shimmer, r, Offset(x, y))
    }
}

private fun DrawScope.drawAuroraTheme(base: Color, ribbons: List<AuroraRibbon>) {
    drawRect(base)

    val steps = (size.width / 8f).toInt().coerceAtLeast(50)

    for (ribbon in ribbons) {
        val yC  = ribbon.fyCentre * size.height
        val rH  = ribbon.height   * size.height
        val wl  = ribbon.wavelength * size.width
        val ph  = ribbon.phase

        // Each ribbon = filled wavy band (top edge + bottom edge path)
        val path = Path().apply {
            // Top edge
            moveTo(0f, yC - rH / 2f + (rH * 0.3f) * sin(ph))
            for (s in 1..steps) {
                val x   = s.toFloat() / steps * size.width
                val top = yC - rH / 2f + (rH * 0.3f) * sin((x / wl) * 2f * PI.toFloat() + ph)
                lineTo(x, top)
            }
            // Bottom edge (reversed)
            lineTo(size.width, yC + rH / 2f)
            for (s in steps downTo 0) {
                val x  = s.toFloat() / steps * size.width
                val bot = yC + rH / 2f + (rH * 0.2f) * sin((x / wl) * 2f * PI.toFloat() + ph + 0.8f)
                lineTo(x, bot)
            }
            close()
        }

        // Outer glow (wider, fainter)
        val glowPath = Path().apply {
            moveTo(0f, yC - rH + (rH * 0.4f) * sin(ph))
            for (s in 1..steps) {
                val x   = s.toFloat() / steps * size.width
                val top = yC - rH + (rH * 0.4f) * sin((x / wl) * 2f * PI.toFloat() + ph)
                lineTo(x, top)
            }
            lineTo(size.width, yC + rH)
            for (s in steps downTo 0) {
                val x  = s.toFloat() / steps * size.width
                val bot = yC + rH + (rH * 0.3f) * sin((x / wl) * 2f * PI.toFloat() + ph + 0.8f)
                lineTo(x, bot)
            }
            close()
        }
        drawPath(glowPath, ribbon.color.copy(alpha = 0.030f))
        drawPath(path,     ribbon.color.copy(alpha = 0.090f))
    }
}
