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
        depthCue: Float = 0f,
        fullRange: Int = 0,
        customStops: FloatArray? = null,
        transparentBg: Int = 0,
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

    /**
     * Like [nativeGetPoints] but returns interleaved [u0,v0,d0, u1,v1,d1, ...]
     * triples, where each `d` is camera-axis depth in [0, 1]. Lets the caller
     * colour the dot preview through a palette LUT.
     */
    external fun nativeGetPointsDepth(
        attractorType: Int,
        params: FloatArray,
        nPts: Int,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
    ): FloatArray

    /**
     * Sample [size] ARGB_8888 colours evenly across a palette (or [customStops]
     * when [paletteIndex] is the CUSTOM ordinal). Used to colour the dot preview.
     */
    external fun nativePaletteLut(
        paletteIndex: Int,
        size: Int,
        customStops: FloatArray? = null,
    ): IntArray

    // ── GPU renderer ────────────────────────────────────────────────────────────

    /** Create the EGL offscreen context. Returns false if GLES 3.1 is unavailable. */
    external fun nativeGpuInit(): Boolean

    /** Release EGL context and all GPU resources. */
    external fun nativeGpuDestroy()

    /**
     * GPU render path — same contract as [nativeRender] but runs the histogram
     * accumulation on the GPU via GLES 3.1 compute shaders.
     * Returns null when the render produced no visible pixels or the GPU path
     * is not yet implemented (stub returns null until Steps 4-6 are complete).
     */
    external fun nativeRenderGpu(
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
        depthCue: Float = 0f,
        fullRange: Int = 0,
        customStops: FloatArray? = null,
        transparentBg: Int = 0,
    ): IntArray?
}
