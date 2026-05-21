package com.chaoscope

import org.junit.Assert.*
import org.junit.Test

class ColorStopSerializerTest {

    private val twoStops = listOf(
        ColorStop(0f,   1f, 0f, 0f),
        ColorStop(1f,   0f, 0f, 1f),
    )

    private val fiveStops = listOf(
        ColorStop(0.0f, 1f, 0f,  0f),
        ColorStop(0.25f, 1f, 0.5f, 0f),
        ColorStop(0.5f, 0f, 1f,  0f),
        ColorStop(0.75f, 0f, 0.5f, 1f),
        ColorStop(1.0f, 0f, 0f,  1f),
    )

    // ── colorStopsToString ────────────────────────────────────────────────────

    @Test fun `two stops serialize to 8 comma-separated values`() {
        val raw = colorStopsToString(twoStops)
        assertEquals(8, raw.split(',').size)
    }

    @Test fun `five stops serialize to 20 comma-separated values`() {
        val raw = colorStopsToString(fiveStops)
        assertEquals(20, raw.split(',').size)
    }

    // ── stringToColorStops ────────────────────────────────────────────────────

    @Test fun `roundtrip two stops`() {
        val raw = colorStopsToString(twoStops)
        val back = stringToColorStops(raw)
        assertNotNull(back)
        assertEquals(2, back!!.size)
        assertEquals(twoStops[0].pos, back[0].pos, 0.0001f)
        assertEquals(twoStops[0].r,   back[0].r,   0.0001f)
        assertEquals(twoStops[1].b,   back[1].b,   0.0001f)
    }

    @Test fun `roundtrip five stops preserves all fields`() {
        val raw = colorStopsToString(fiveStops)
        val back = stringToColorStops(raw)!!
        assertEquals(fiveStops.size, back.size)
        fiveStops.forEachIndexed { i, s ->
            assertEquals(s.pos, back[i].pos, 0.0001f)
            assertEquals(s.r,   back[i].r,   0.0001f)
            assertEquals(s.g,   back[i].g,   0.0001f)
            assertEquals(s.b,   back[i].b,   0.0001f)
        }
    }

    @Test fun `blank string returns null`() {
        assertNull(stringToColorStops(""))
        assertNull(stringToColorStops("   "))
    }

    @Test fun `malformed string returns null`() {
        assertNull(stringToColorStops("not,a,number,here"))
        assertNull(stringToColorStops("0.5,1.0"))
        assertNull(stringToColorStops("garbage"))
    }

    @Test fun `single stop (4 values) returns null — need at least 2`() {
        assertNull(stringToColorStops("0.0,1.0,0.0,0.0"))
    }

    @Test fun `trailing partial group is ignored`() {
        // 8 values (2 stops) + 2 extra orphan values — result should still be 2 stops
        val raw = colorStopsToString(twoStops) + ",0.5,0.5"
        val back = stringToColorStops(raw)
        assertNotNull(back)
        assertEquals(2, back!!.size)
    }
}
