package com.chaoscope

class CpuAttractorRenderer : AttractorRenderer {

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
    ): IntArray? = ChaoscopeEngine.nativeRender(
        attractorType  = attractorType,
        params         = params,
        width          = width,
        height         = height,
        iterations     = iterations,
        yaw            = yaw,
        pitch          = pitch,
        roll           = roll,
        zoom           = zoom,
        paletteIndex   = paletteIndex,
        gamma          = gamma,
        renderStyle    = renderStyle,
        bgColor        = bgColor,
        boundsExtraPad = boundsExtraPad,
        depthCue       = depthCue,
        fullRange      = fullRange,
        customStops    = customStops,
        transparentBg  = transparentBg,
    )

    override fun getPoints(
        attractorType: Int,
        params: FloatArray,
        nPts: Int,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
    ): FloatArray = ChaoscopeEngine.nativeGetPoints(
        attractorType = attractorType,
        params        = params,
        nPts          = nPts,
        yaw           = yaw,
        pitch         = pitch,
        roll          = roll,
        zoom          = zoom,
    )

    override fun getPointsDepth(
        attractorType: Int,
        params: FloatArray,
        nPts: Int,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
    ): FloatArray = ChaoscopeEngine.nativeGetPointsDepth(
        attractorType = attractorType,
        params        = params,
        nPts          = nPts,
        yaw           = yaw,
        pitch         = pitch,
        roll          = roll,
        zoom          = zoom,
    )

    override fun paletteLut(
        paletteIndex: Int,
        size: Int,
        customStops: FloatArray?,
    ): IntArray = ChaoscopeEngine.nativePaletteLut(
        paletteIndex = paletteIndex,
        size         = size,
        customStops  = customStops,
    )
}
