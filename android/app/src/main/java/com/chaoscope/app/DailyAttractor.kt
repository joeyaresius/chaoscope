package com.chaoscope

import android.content.Context
import java.util.Calendar
import kotlin.random.Random

/**
 * Deterministic "Attractor of the Day": a date-seeded pick from the curated
 * presets with a mild parameter jitter and a freely randomised look, so every
 * device shows the same discovery on the same day — no backend involved.
 *
 * Blank-screen guard: candidates start from known-good curated presets (params
 * jittered by at most ±[PARAM_JITTER] of each slider range — look changes can't
 * blank), and each is validated by an off-screen thumbnail render before being
 * shown. A blank candidate advances the seed deterministically; the final
 * fallback is the un-jittered curated preset itself, which cannot be blank.
 */
object DailyAttractor {

    private const val PARAM_JITTER = 0.05f
    private const val MAX_ATTEMPTS = 5

    /** Stable seed for today's local date, e.g. 20260610. */
    private fun todaySeed(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 10_000 +
            (cal.get(Calendar.MONTH) + 1) * 100 +
            cal.get(Calendar.DAY_OF_MONTH)
    }

    /**
     * Today's validated preset. Runs up to [MAX_ATTEMPTS] thumbnail renders in
     * the worst case (one in the common case — the result is cached on disk by
     * [PresetThumbnails], so subsequent calls today are free).
     */
    suspend fun preset(context: Context): Preset {
        val seed = todaySeed()
        for (attempt in 0 until MAX_ATTEMPTS) {
            val candidate = generate(seed + attempt)
            if (PresetThumbnails.get(context, candidate) != null) return candidate
        }
        return pickBase(Random(seed))
    }

    private fun pickBase(rnd: Random): Preset {
        val all = CURATED_PRESETS.values.flatten()
        return all[rnd.nextInt(all.size)]
    }

    private fun generate(seed: Int): Preset {
        val rnd  = Random(seed)
        val base = pickBase(rnd)
        val type = base.type

        val params = type.paramRanges.mapIndexed { i, range ->
            val current = base.params.getOrElse(i) { type.defaultParams[i] }
            val jitter  = (range.endInclusive - range.start) * PARAM_JITTER
            (current + (rnd.nextFloat() * 2f - 1f) * jitter)
                .coerceIn(range.start, range.endInclusive)
        }
        val palettes = PaletteType.entries.filter { it != PaletteType.CUSTOM }
        return base.copy(
            params      = params,
            palette     = palettes[rnd.nextInt(palettes.size)],
            renderStyle = RenderStyle.entries[rnd.nextInt(RenderStyle.entries.size)],
            yaw         = if (type.is3D) base.yaw + rnd.nextFloat() * 40f - 20f else 0f,
            pitch       = if (type.is3D)
                              (base.pitch + rnd.nextFloat() * 20f - 10f).coerceIn(-90f, 90f)
                          else 0f,
        )
    }
}
