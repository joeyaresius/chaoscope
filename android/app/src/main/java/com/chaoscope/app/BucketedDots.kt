package com.chaoscope

/**
 * Pre-bucketed dot cloud produced on [kotlinx.coroutines.Dispatchers.Default].
 *
 * [buckets]  — one [FloatArray] per palette entry, interleaved [u0,v0, u1,v1, …]
 *              in normalised [-1, 1] space.  Empty arrays for unpopulated buckets.
 * [colors]   — ARGB_8888 color for each bucket index (matches paletteLut).
 *
 * The Canvas composable only needs to scale u/v → screen pixels and call
 * nativeCanvas.drawPoints() per bucket — no Offset boxing, no ArrayList, no
 * per-dot heap allocation on the draw thread.
 */
class BucketedDots(
    val buckets: Array<FloatArray>,
    val colors:  IntArray,
)
