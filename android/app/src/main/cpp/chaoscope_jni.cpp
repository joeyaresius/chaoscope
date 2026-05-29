#include <jni.h>
#include <cstring>
#include "renderer.h"

extern "C" {

/**
 * JNI bridge for ChaoscopeEngine.nativeRender().
 * Returns an int[] of width*height ARGB_8888 pixels.
 */
JNIEXPORT jintArray JNICALL
Java_com_chaoscope_ChaoscopeEngine_nativeRender(
    JNIEnv*     env,
    jobject     /* this */,
    jint        attractorType,
    jfloatArray jParams,
    jint        width,
    jint        height,
    jlong       iterations,
    jfloat      yaw,
    jfloat      pitch,
    jfloat      roll,
    jfloat      zoom,
    jint        paletteIndex,
    jfloat      gamma,
    jint        renderStyle,
    jint        bgColor,
    jfloat      boundsExtraPad,
    jfloat      depthCue,
    jint        fullRange,
    jfloatArray jCustomStops,
    jint        transparentBg
) {
    RenderParams rp{};
    rp.attractorType  = static_cast<int>(attractorType);
    rp.width          = static_cast<int>(width);
    rp.height         = static_cast<int>(height);
    rp.iterations     = static_cast<long long>(iterations);
    rp.yaw            = yaw;
    rp.pitch          = pitch;
    rp.roll           = roll;
    rp.zoom           = zoom;
    rp.paletteIndex   = static_cast<int>(paletteIndex);
    rp.gamma          = gamma;
    rp.renderStyle    = static_cast<int>(renderStyle);
    rp.bgColor        = static_cast<int>(bgColor);
    rp.boundsExtraPad = boundsExtraPad;
    rp.depthCue       = depthCue;
    rp.fullRange      = static_cast<int>(fullRange);
    rp.transparentBg  = static_cast<int>(transparentBg);

    // Copy attractor params (max 8 floats)
    memset(rp.params, 0, sizeof(rp.params));
    jsize paramLen  = env->GetArrayLength(jParams);
    jfloat* pData   = env->GetFloatArrayElements(jParams, nullptr);
    int copyLen = (paramLen < 8) ? (int)paramLen : 8;
    for (int i = 0; i < copyLen; i++) rp.params[i] = pData[i];
    env->ReleaseFloatArrayElements(jParams, pData, JNI_ABORT);

    // Copy custom palette stops if provided (max 8 stops × 4 floats)
    memset(rp.customStops, 0, sizeof(rp.customStops));
    rp.numCustomStops = 0;
    if (jCustomStops != nullptr) {
        jsize stopLen = env->GetArrayLength(jCustomStops);
        jfloat* sData = env->GetFloatArrayElements(jCustomStops, nullptr);
        int copyStops = (stopLen / 4 < 8) ? (int)(stopLen / 4) : 8;
        for (int i = 0; i < copyStops * 4; i++) rp.customStops[i] = sData[i];
        rp.numCustomStops = copyStops;
        env->ReleaseFloatArrayElements(jCustomStops, sData, JNI_ABORT);
    }

    // Allocate output array and render
    jintArray result = env->NewIntArray(width * height);
    if (result == nullptr) return nullptr; // OOM

    jint* pixels = env->GetIntArrayElements(result, nullptr);
    bool hasContent = renderAttractor(rp, reinterpret_cast<int*>(pixels));
    env->ReleaseIntArrayElements(result, pixels, hasContent ? 0 : JNI_ABORT);

    // Return null to Kotlin to signal empty render (orbit diverged)
    return hasContent ? result : nullptr;
}

/**
 * Return n_pts projected (u, v) pairs normalised to [-1, 1] for dot-preview.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_chaoscope_ChaoscopeEngine_nativeGetPoints(
    JNIEnv*     env,
    jobject     /* this */,
    jint        attractorType,
    jfloatArray jParams,
    jint        nPts,
    jfloat      yaw,
    jfloat      pitch,
    jfloat      roll,
    jfloat      zoom
) {
    RenderParams rp{};
    rp.attractorType = static_cast<int>(attractorType);
    rp.yaw   = yaw;   rp.pitch = pitch;
    rp.roll  = roll;  rp.zoom  = zoom;
    rp.width = 1;     rp.height = 1;  // unused by getProjectedPoints

    memset(rp.params, 0, sizeof(rp.params));
    jsize   paramLen = env->GetArrayLength(jParams);
    jfloat* pData    = env->GetFloatArrayElements(jParams, nullptr);
    int     copyLen  = (paramLen < 8) ? (int)paramLen : 8;
    for (int i = 0; i < copyLen; i++) rp.params[i] = pData[i];
    env->ReleaseFloatArrayElements(jParams, pData, JNI_ABORT);

    auto pts = getProjectedPoints(rp, static_cast<int>(nPts));
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(pts.size()));
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(pts.size()), pts.data());
    return result;
}

/**
 * Like nativeGetPoints, but returns interleaved (u, v, depth) triples for a
 * depth-shaded / palette-coloured dot preview.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_chaoscope_ChaoscopeEngine_nativeGetPointsDepth(
    JNIEnv*     env,
    jobject     /* this */,
    jint        attractorType,
    jfloatArray jParams,
    jint        nPts,
    jfloat      yaw,
    jfloat      pitch,
    jfloat      roll,
    jfloat      zoom
) {
    RenderParams rp{};
    rp.attractorType = static_cast<int>(attractorType);
    rp.yaw   = yaw;   rp.pitch = pitch;
    rp.roll  = roll;  rp.zoom  = zoom;
    rp.width = 1;     rp.height = 1;

    memset(rp.params, 0, sizeof(rp.params));
    jsize   paramLen = env->GetArrayLength(jParams);
    jfloat* pData    = env->GetFloatArrayElements(jParams, nullptr);
    int     copyLen  = (paramLen < 8) ? (int)paramLen : 8;
    for (int i = 0; i < copyLen; i++) rp.params[i] = pData[i];
    env->ReleaseFloatArrayElements(jParams, pData, JNI_ABORT);

    auto pts = getProjectedPointsDepth(rp, static_cast<int>(nPts));
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(pts.size()));
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(pts.size()), pts.data());
    return result;
}

/**
 * Sample `size` ARGB colours across a palette (or custom stops) for colouring
 * the dot preview. Returns an int[] of `size` ARGB_8888 values.
 */
JNIEXPORT jintArray JNICALL
Java_com_chaoscope_ChaoscopeEngine_nativePaletteLut(
    JNIEnv*     env,
    jobject     /* this */,
    jint        paletteIndex,
    jint        size,
    jfloatArray jCustomStops
) {
    int n = static_cast<int>(size);
    if (n <= 0) return nullptr;

    float  stops[8 * 4];
    float* stopsPtr  = nullptr;
    int    numStops  = 0;
    memset(stops, 0, sizeof(stops));
    if (jCustomStops != nullptr) {
        jsize stopLen = env->GetArrayLength(jCustomStops);
        jfloat* sData = env->GetFloatArrayElements(jCustomStops, nullptr);
        int copyStops = (stopLen / 4 < 8) ? (int)(stopLen / 4) : 8;
        for (int i = 0; i < copyStops * 4; i++) stops[i] = sData[i];
        numStops = copyStops;
        stopsPtr = stops;
        env->ReleaseFloatArrayElements(jCustomStops, sData, JNI_ABORT);
    }

    jintArray result = env->NewIntArray(n);
    if (result == nullptr) return nullptr;
    jint* data = env->GetIntArrayElements(result, nullptr);
    getPaletteLutARGB(static_cast<int>(paletteIndex),
                      reinterpret_cast<int*>(data), n, stopsPtr, numStops);
    env->ReleaseIntArrayElements(result, data, 0);
    return result;
}

} // extern "C"
