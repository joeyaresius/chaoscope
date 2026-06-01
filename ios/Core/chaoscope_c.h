// Plain-C interface to the Chaoscope render engine.
// Imported by the Swift bridging header — must stay pure C (no C++).
#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ChaoscopeRenderParams {
    int32_t   attractorType;
    float     params[8];
    int32_t   width;
    int32_t   height;
    int64_t   iterations;
    float     yaw;
    float     pitch;
    float     roll;
    float     zoom;
    int32_t   paletteIndex;
    float     gamma;
    int32_t   renderStyle;
    int32_t   bgColor;
    float     boundsExtraPad;
    float     depthCue;
    int32_t   fullRange;
    int32_t   transparentBg;
    float     customStops[32];  // flattened [pos, r, g, b, ...], up to 8 stops
    int32_t   numCustomStops;
} ChaoscopeRenderParams;

// Returns a malloc'd ARGB_8888 buffer of (width * height) int32_t values.
// Returns NULL if the orbit diverged or produced no visible pixels.
// Caller must free with chaoscope_free().
int32_t* chaoscope_render(const ChaoscopeRenderParams* rp);
void     chaoscope_free(int32_t* buf);

#ifdef __cplusplus
}
#endif
