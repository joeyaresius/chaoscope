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

// ── Full render ───────────────────────────────────────────────────────────────
// Returns a malloc'd ARGB_8888 buffer of (width * height) int32_t values.
// Returns NULL if the orbit diverged or produced no visible pixels.
// Free with chaoscope_free().
int32_t* chaoscope_render(const ChaoscopeRenderParams* rp);
void     chaoscope_free(int32_t* buf);

// ── Dot preview ───────────────────────────────────────────────────────────────
// Returns n_pts * 3 floats: [u0, v0, depth0, u1, v1, depth1, ...].
// u, v in [-1, 1];  depth in [0, 1].
// Returns NULL if the orbit diverged.
// Free with chaoscope_free_float().
float*   chaoscope_get_points_depth(const ChaoscopeRenderParams* rp, int32_t n_pts);
void     chaoscope_free_float(float* buf);

// Returns `size` ARGB_8888 int32 colors sampled evenly across the palette.
// Pass custom_stops (flattened [pos,r,g,b,...]) and num_custom_stops for
// palette_index == 10 (CUSTOM). Returns NULL on failure. Free with chaoscope_free().
int32_t* chaoscope_palette_lut(int32_t palette_index, int32_t size,
                                const float* custom_stops, int32_t num_custom_stops);

#ifdef __cplusplus
}
#endif
