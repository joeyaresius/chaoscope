package com.chaoscope

/**
 * GPU-accelerated renderer backed by GLES 3.1 compute shaders.
 *
 * [render] tries the GPU path first. If the GPU path returns null (unsupported
 * render style, diverged orbit, or any driver error), it transparently falls
 * through to the CPU path. All other operations (dot preview, palette LUT) run
 * on the CPU — they're already fast enough to not need GPU acceleration.
 */
class GpuAttractorRenderer : AttractorRenderer {

    private val cpu = CpuAttractorRenderer()
    private val gpuReady: Boolean = ChaoscopeEngine.nativeGpuInit()

    // Set to true on any JNI exception (e.g. driver crash, OOM). Once broken,
    // all subsequent renders go straight to CPU without retrying the GPU path.
    @Volatile private var broken = false

    override fun render(
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
        boundsExtraPad: Float,
        depthCue: Float,
        fullRange: Int,
        customStops: FloatArray?,
        transparentBg: Int,
    ): IntArray? {
        if (gpuReady && !broken) {
            // null return is expected for CPU-fallback styles (LIQUID, LIGHT, etc.)
            // and diverged orbits — don't set broken. Only exceptions are fatal.
            val gpuResult = try {
                ChaoscopeEngine.nativeRenderGpu(
                    attractorType, params, width, height, iterations,
                    yaw, pitch, roll, zoom,
                    paletteIndex, gamma, renderStyle, bgColor,
                    boundsExtraPad, depthCue, fullRange, customStops, transparentBg,
                )
            } catch (e: Exception) {
                broken = true
                null
            }
            if (gpuResult != null) return gpuResult
        }
        // GPU returned null (fallback style, diverged orbit, driver error) → CPU
        return cpu.render(
            attractorType, params, width, height, iterations,
            yaw, pitch, roll, zoom,
            paletteIndex, gamma, renderStyle, bgColor,
            boundsExtraPad, depthCue, fullRange, customStops, transparentBg,
        )
    }

    override fun close() {
        if (gpuReady) ChaoscopeEngine.nativeGpuDestroy()
    }

    override fun getPoints(
        attractorType: Int, params: FloatArray, nPts: Int,
        yaw: Float, pitch: Float, roll: Float, zoom: Float,
    ): FloatArray = cpu.getPoints(attractorType, params, nPts, yaw, pitch, roll, zoom)

    override fun getPointsDepth(
        attractorType: Int, params: FloatArray, nPts: Int,
        yaw: Float, pitch: Float, roll: Float, zoom: Float,
    ): FloatArray = cpu.getPointsDepth(attractorType, params, nPts, yaw, pitch, roll, zoom)

    override fun paletteLut(
        paletteIndex: Int, size: Int, customStops: FloatArray?,
    ): IntArray = cpu.paletteLut(paletteIndex, size, customStops)
}
