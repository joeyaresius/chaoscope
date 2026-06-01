package com.chaoscope

interface AttractorRenderer {

    fun render(
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

    fun getPoints(
        attractorType: Int,
        params: FloatArray,
        nPts: Int,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
    ): FloatArray

    fun getPointsDepth(
        attractorType: Int,
        params: FloatArray,
        nPts: Int,
        yaw: Float,
        pitch: Float,
        roll: Float,
        zoom: Float,
    ): FloatArray

    fun paletteLut(
        paletteIndex: Int,
        size: Int,
        customStops: FloatArray? = null,
    ): IntArray

    /** Release any native resources held by this renderer. No-op by default. */
    fun close() {}
}
