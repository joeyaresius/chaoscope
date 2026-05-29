package com.chaoscope

import org.junit.Assert.*
import org.junit.Test

class AttractorDefsTest {

    // ── AttractorType consistency ─────────────────────────────────────────────

    @Test fun `every attractor has matching param list sizes`() {
        for (type in AttractorType.entries) {
            val n = type.paramNames.size
            assertEquals(
                "${type.name}: defaultParams size ${type.defaultParams.size} != paramNames size $n",
                n, type.defaultParams.size,
            )
            assertEquals(
                "${type.name}: paramRanges size ${type.paramRanges.size} != paramNames size $n",
                n, type.paramRanges.size,
            )
            assertEquals(
                "${type.name}: paramHints size ${type.paramHints.size} != paramNames size $n",
                n, type.paramHints.size,
            )
        }
    }

    @Test fun `every default param is within its range`() {
        for (type in AttractorType.entries) {
            type.defaultParams.forEachIndexed { i, v ->
                val range = type.paramRanges[i]
                assertTrue(
                    "${type.name} param[$i] default $v not in $range",
                    v in range,
                )
            }
        }
    }

    @Test fun `every attractor has a non-blank display name and description`() {
        for (type in AttractorType.entries) {
            assertTrue("${type.name} has blank displayName", type.displayName.isNotBlank())
            assertTrue("${type.name} has blank description", type.description.isNotBlank())
        }
    }

    // ── PaletteType ───────────────────────────────────────────────────────────

    @Test fun `CUSTOM is the last palette`() {
        // CUSTOM must stay last so adding built-in palettes never shifts the
        // ordinals that presets/serialization persist by.
        assertEquals(PaletteType.entries.size - 1, PaletteType.CUSTOM.ordinal)
    }

    @Test fun `all palettes have non-blank display names`() {
        for (p in PaletteType.entries) {
            assertTrue("${p.name} has blank displayName", p.displayName.isNotBlank())
        }
    }

    // ── defaultCustomStops ────────────────────────────────────────────────────

    @Test fun `defaultCustomStops has at least 2 stops`() {
        assertTrue(defaultCustomStops.size >= 2)
    }

    @Test fun `defaultCustomStops starts at 0 and ends at 1`() {
        assertEquals(0f, defaultCustomStops.first().pos, 0.001f)
        assertEquals(1f, defaultCustomStops.last().pos,  0.001f)
    }

    @Test fun `defaultCustomStops all channels in 0-1 range`() {
        for (stop in defaultCustomStops) {
            assertTrue("pos ${stop.pos} out of range", stop.pos in 0f..1f)
            assertTrue("r ${stop.r} out of range",     stop.r   in 0f..1f)
            assertTrue("g ${stop.g} out of range",     stop.g   in 0f..1f)
            assertTrue("b ${stop.b} out of range",     stop.b   in 0f..1f)
        }
    }

    // ── UiState defaults ──────────────────────────────────────────────────────

    @Test fun `UiState default attractor is CLIFFORD`() {
        assertEquals(AttractorType.CLIFFORD, UiState().attractorType)
    }

    @Test fun `UiState default palette is NEBULA`() {
        assertEquals(PaletteType.NEBULA, UiState().palette)
    }

    @Test fun `UiState default customStops equals defaultCustomStops`() {
        assertEquals(defaultCustomStops, UiState().customStops)
    }

    @Test fun `UiState default params match CLIFFORD defaults`() {
        val state = UiState()
        assertEquals(AttractorType.CLIFFORD.defaultParams.toList(), state.params)
    }

    // ── Share caption ─────────────────────────────────────────────────────────

    @Test fun `share caption includes attractor, palette and params`() {
        val c = buildShareCaption(
            AttractorType.LORENZ, PaletteType.NEBULA, listOf(10f, 28f, 2.667f, 0.005f),
        )
        assertTrue("missing attractor name in '$c'", c.contains("Lorenz"))
        assertTrue("missing palette name in '$c'",  c.contains("Nebula"))
        assertTrue("missing param σ=10 in '$c'",     c.contains("σ=10"))
        assertTrue("missing param β=2.667 in '$c'",  c.contains("β=2.667"))
        assertTrue("missing app tag in '$c'",        c.contains("Chaoscope"))
    }

    @Test fun `share caption trims trailing zeros but keeps significant digits`() {
        val c = buildShareCaption(
            AttractorType.LORENZ, PaletteType.NEBULA, listOf(10f, 28f, 2.667f, 0.005f),
        )
        assertFalse("should trim 10.000 to 10 in '$c'", c.contains("10.000"))
        assertTrue("should keep 0.005 in '$c'",         c.contains("0.005"))
    }

    @Test fun `share caption labels custom palette`() {
        val c = buildShareCaption(AttractorType.CLIFFORD, PaletteType.CUSTOM, listOf(1f))
        assertTrue("custom palette should be labelled in '$c'", c.contains("Custom"))
    }
}
