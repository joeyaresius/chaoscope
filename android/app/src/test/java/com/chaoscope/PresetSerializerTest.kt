package com.chaoscope

import org.junit.Assert.*
import org.junit.Test

class PresetSerializerTest {

    private val lorenz = Preset(
        name        = "My Lorenz",
        type        = AttractorType.LORENZ,
        params      = listOf(10f, 28f, 2.667f, 0.005f),
        yaw         = 25f,
        pitch       = 15f,
        roll        = 0f,
        zoom        = 1.2f,
        palette     = PaletteType.FIRE,
        renderStyle = RenderStyle.PLASMA,
        bgColor     = BgColor.STARS,
    )

    @Test fun `roundtrip preserves all fields`() {
        val back = stringToPresets(presetsToString(listOf(lorenz)))
        assertEquals(1, back.size)
        val p = back[0]
        assertEquals("My Lorenz", p.name)
        assertEquals(AttractorType.LORENZ, p.type)
        assertEquals(listOf(10f, 28f, 2.667f, 0.005f), p.params)
        assertEquals(25f, p.yaw, 0.0001f)
        assertEquals(15f, p.pitch, 0.0001f)
        assertEquals(1.2f, p.zoom, 0.0001f)
        assertEquals(PaletteType.FIRE, p.palette)
        assertEquals(RenderStyle.PLASMA, p.renderStyle)
        assertEquals(BgColor.STARS, p.bgColor)
    }

    @Test fun `multiple presets roundtrip in order`() {
        val a = lorenz.copy(name = "A")
        val b = lorenz.copy(name = "B", type = AttractorType.AIZAWA, params = listOf(0.95f, 0.7f, 0.6f, 3.5f, 0.25f, 0.1f, 0.01f))
        val back = stringToPresets(presetsToString(listOf(a, b)))
        assertEquals(listOf("A", "B"), back.map { it.name })
        assertEquals(AttractorType.AIZAWA, back[1].type)
    }

    @Test fun `pipe and newline in name are sanitized`() {
        val weird = lorenz.copy(name = "a|b\nc")
        val back = stringToPresets(presetsToString(listOf(weird)))
        assertEquals(1, back.size)
        assertFalse(back[0].name.contains('|'))
        assertFalse(back[0].name.contains('\n'))
    }

    @Test fun `blank input yields empty list`() {
        assertTrue(stringToPresets("").isEmpty())
        assertTrue(stringToPresets("   ").isEmpty())
    }

    @Test fun `malformed lines are skipped`() {
        val good = presetToString(lorenz)
        val raw = "garbage|too|few\n$good\nalso bad"
        val back = stringToPresets(raw)
        assertEquals(1, back.size)
        assertEquals("My Lorenz", back[0].name)
    }

    @Test fun `unknown enum name skips that preset`() {
        val tampered = presetToString(lorenz).replace("LORENZ", "NOT_A_TYPE")
        assertTrue(stringToPresets(tampered).isEmpty())
    }
}
