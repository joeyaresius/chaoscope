package com.chaoscope

/**
 * Thin Kotlin wrapper around the native C++ library.
 * All heavy work runs on Dispatchers.Default in the ViewModel.
 */
object ChaoscopeEngine {

    init {
        System.loadLibrary("chaoscope")
    }

    /**
     * Render an attractor to a flat ARGB_8888 pixel array.
     *
     * @param attractorType  ordinal of [AttractorType]
     * @param params         attractor-specific float parameters
     * @param width / height canvas size in pixels
     * @param iterations     total iteration count
     * @param yaw/pitch/roll camera rotation in degrees (3-D attractors)
     * @param zoom           camera zoom factor
     * @param paletteIndex   ordinal of [PaletteType]
     * @param gamma          density gamma correction
     * @return               IntArray of width*height ARGB integers
     */
    /**
     * Returns null when the orbit diverged / produced no visible histogram hits.
     * In that case the caller should retry with more iterations or warn the user.
     */
    external fun nativeRender(
        attractorType: Int,
        params: FloatArray,
        width: Int,
        height: Int,
        iterations: Long,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
        paletteIndex: Int,
        gamma: Float,
        renderStyle: Int,
        bgColor: Int,
        boundsExtraPad: Float = 0f,
        customStops: FloatArray? = null,
    ): IntArray?

    /**
     * Fast dot-preview: returns an interleaved FloatArray [u0,v0, u1,v1, ...]
     * with each value in [-1, 1].  No histogram, no colours, ~5 ms on ARM.
     */
    external fun nativeGetPoints(
        attractorType: Int,
        params: FloatArray,
        nPts: Int,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
    ): FloatArray
}
