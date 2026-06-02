// C bridge between chaoscope_c.h (plain C) and renderer.h (C++).
#include "chaoscope_c.h"
#include "renderer.h"
#include <cstdlib>
#include <cstring>
#include <algorithm>

// ── helper ────────────────────────────────────────────────────────────────────

static RenderParams toRenderParams(const ChaoscopeRenderParams* cp) {
    RenderParams rp{};
    rp.attractorType  = cp->attractorType;
    rp.width          = cp->width;
    rp.height         = cp->height;
    rp.iterations     = static_cast<long long>(cp->iterations);
    rp.yaw            = cp->yaw;
    rp.pitch          = cp->pitch;
    rp.roll           = cp->roll;
    rp.zoom           = cp->zoom;
    rp.paletteIndex   = cp->paletteIndex;
    rp.gamma          = cp->gamma;
    rp.renderStyle    = cp->renderStyle;
    rp.bgColor        = cp->bgColor;
    rp.boundsExtraPad = cp->boundsExtraPad;
    rp.depthCue       = cp->depthCue;
    rp.fullRange      = cp->fullRange;
    rp.transparentBg  = cp->transparentBg;
    memcpy(rp.params, cp->params, sizeof(rp.params));
    int copyStops = (cp->numCustomStops < 8) ? cp->numCustomStops : 8;
    memcpy(rp.customStops, cp->customStops,
           static_cast<size_t>(copyStops) * 4 * sizeof(float));
    rp.numCustomStops = copyStops;
    return rp;
}

extern "C" {

// ── Full histogram render ─────────────────────────────────────────────────────

int32_t* chaoscope_render(const ChaoscopeRenderParams* cp) {
    RenderParams rp = toRenderParams(cp);
    size_t pixelCount = static_cast<size_t>(cp->width) *
                        static_cast<size_t>(cp->height);
    int32_t* buf = static_cast<int32_t*>(malloc(pixelCount * sizeof(int32_t)));
    if (!buf) return nullptr;
    bool ok = renderAttractor(rp, buf);
    if (!ok) { free(buf); return nullptr; }
    return buf;
}

void chaoscope_free(int32_t* buf) {
    free(buf);
}

// ── Dot preview ───────────────────────────────────────────────────────────────

float* chaoscope_get_points_depth(const ChaoscopeRenderParams* cp, int32_t n_pts) {
    RenderParams rp = toRenderParams(cp);
    // width/height unused by getProjectedPointsDepth, but set defensively
    rp.width = 1; rp.height = 1;

    auto pts = getProjectedPointsDepth(rp, static_cast<int>(n_pts));
    if (pts.empty()) return nullptr;

    float* buf = static_cast<float*>(malloc(pts.size() * sizeof(float)));
    if (!buf) return nullptr;
    memcpy(buf, pts.data(), pts.size() * sizeof(float));
    return buf;
}

void chaoscope_free_float(float* buf) {
    free(buf);
}

// ── Palette LUT ───────────────────────────────────────────────────────────────

int32_t* chaoscope_palette_lut(int32_t palette_index, int32_t size,
                                const float* custom_stops, int32_t num_custom_stops) {
    if (size <= 0) return nullptr;
    int32_t* buf = static_cast<int32_t*>(
        malloc(static_cast<size_t>(size) * sizeof(int32_t)));
    if (!buf) return nullptr;
    getPaletteLutARGB(static_cast<int>(palette_index), buf,
                      static_cast<int>(size),
                      custom_stops, static_cast<int>(num_custom_stops));
    return buf;
}

} // extern "C"
