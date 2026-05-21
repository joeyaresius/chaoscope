package com.chaoscope

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {

    private fun assertNear(expected: Float, actual: Float, delta: Float = 0.001f) {
        assertEquals(expected, actual, delta)
    }

    // ── rgbToHsv ─────────────────────────────────────────────────────────────

    @Test fun `pure red is hue 0`() {
        val (h, s, v) = rgbToHsv(1f, 0f, 0f)
        assertNear(0f, h)
        assertNear(1f, s)
        assertNear(1f, v)
    }

    @Test fun `pure green is hue 120`() {
        val (h, s, v) = rgbToHsv(0f, 1f, 0f)
        assertNear(120f, h)
        assertNear(1f, s)
        assertNear(1f, v)
    }

    @Test fun `pure blue is hue 240`() {
        val (h, s, v) = rgbToHsv(0f, 0f, 1f)
        assertNear(240f, h)
        assertNear(1f, s)
        assertNear(1f, v)
    }

    @Test fun `white has saturation 0 and value 1`() {
        val (h, s, v) = rgbToHsv(1f, 1f, 1f)
        assertNear(0f, s)
        assertNear(1f, v)
    }

    @Test fun `black has saturation 0 and value 0`() {
        val (h, s, v) = rgbToHsv(0f, 0f, 0f)
        assertNear(0f, s)
        assertNear(0f, v)
    }

    @Test fun `grey has saturation 0`() {
        val (_, s, v) = rgbToHsv(0.5f, 0.5f, 0.5f)
        assertNear(0f, s)
        assertNear(0.5f, v)
    }

    @Test fun `cyan is hue 180`() {
        val (h, s, v) = rgbToHsv(0f, 1f, 1f)
        assertNear(180f, h)
        assertNear(1f, s)
        assertNear(1f, v)
    }

    @Test fun `hue is never negative`() {
        // All hue combos should produce h >= 0
        val colors = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(1f, 1f, 0f),
            floatArrayOf(0f, 1f, 1f),
            floatArrayOf(1f, 0f, 1f),
        )
        for (c in colors) {
            val h = rgbToHsv(c[0], c[1], c[2])[0]
            assert(h >= 0f) { "Hue $h < 0 for rgb(${c[0]}, ${c[1]}, ${c[2]})" }
        }
    }

    // ── hsvToRgb ─────────────────────────────────────────────────────────────

    @Test fun `hue 0 sat 1 val 1 is red`() {
        val (r, g, b) = hsvToRgb(0f, 1f, 1f)
        assertNear(1f, r); assertNear(0f, g); assertNear(0f, b)
    }

    @Test fun `hue 120 sat 1 val 1 is green`() {
        val (r, g, b) = hsvToRgb(120f, 1f, 1f)
        assertNear(0f, r); assertNear(1f, g); assertNear(0f, b)
    }

    @Test fun `hue 240 sat 1 val 1 is blue`() {
        val (r, g, b) = hsvToRgb(240f, 1f, 1f)
        assertNear(0f, r); assertNear(0f, g); assertNear(1f, b)
    }

    @Test fun `zero saturation produces grey`() {
        val (r, g, b) = hsvToRgb(180f, 0f, 0.7f)
        assertNear(0.7f, r); assertNear(0.7f, g); assertNear(0.7f, b)
    }

    @Test fun `zero value produces black`() {
        val (r, g, b) = hsvToRgb(90f, 1f, 0f)
        assertNear(0f, r); assertNear(0f, g); assertNear(0f, b)
    }

    // ── Roundtrip ────────────────────────────────────────────────────────────

    @Test fun `rgb-to-hsv-to-rgb roundtrip`() {
        val cases = listOf(
            Triple(0.8f, 0.2f, 0.5f),
            Triple(0.1f, 0.9f, 0.3f),
            Triple(0.6f, 0.6f, 0.6f),
            Triple(0f,   0f,   0f),
            Triple(1f,   1f,   1f),
        )
        for ((r0, g0, b0) in cases) {
            val (h, s, v) = rgbToHsv(r0, g0, b0)
            val (r1, g1, b1) = hsvToRgb(h, s, v)
            assertNear(r0, r1, 0.002f)
            assertNear(g0, g1, 0.002f)
            assertNear(b0, b1, 0.002f)
        }
    }

    // Helper: destructure FloatArray for readability
    private operator fun FloatArray.component1() = this[0]
    private operator fun FloatArray.component2() = this[1]
    private operator fun FloatArray.component3() = this[2]
}
