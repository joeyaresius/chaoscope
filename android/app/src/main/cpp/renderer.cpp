#include "renderer.h"
#include "attractors.h"
#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>
#include <cfloat>

// ────────────────────────────────────────────────────────────────────────────
// Camera / orthographic projection
// ────────────────────────────────────────────────────────────────────────────

static void buildRotationMatrix(float yawDeg, float pitchDeg, float rollDeg,
                                float R[9]) {
    const float pi = 3.14159265f;
    float yaw   = yawDeg   * (pi / 180.f);
    float pitch = pitchDeg * (pi / 180.f);
    float roll  = rollDeg  * (pi / 180.f);

    float cy = cosf(yaw),   sy = sinf(yaw);
    float cp = cosf(pitch), sp = sinf(pitch);
    float cr = cosf(roll),  sr = sinf(roll);

    // ZYX convention: R = Rz * Ry * Rx
    R[0] =  cy*cp;              R[1] = cy*sp*sr - sy*cr;  R[2] = cy*sp*cr + sy*sr;
    R[3] =  sy*cp;              R[4] = sy*sp*sr + cy*cr;  R[5] = sy*sp*cr - cy*sr;
    R[6] = -sp;                 R[7] = cp*sr;              R[8] = cp*cr;
}

static void projectBatch(const float R[9], float zoom,
                          const float* xs, const float* ys, const float* zs, int n,
                          float* us, float* vs) {
    for (int i = 0; i < n; i++) {
        float x = xs[i], y = ys[i], z = zs[i];
        us[i] = (R[0]*x + R[1]*y + R[2]*z) * zoom;
        vs[i] = (R[3]*x + R[4]*y + R[5]*z) * zoom;
    }
}

// As projectBatch, but also writes camera-axis depth (third rotation row).
static void projectBatchDepth(const float R[9], float zoom,
                               const float* xs, const float* ys, const float* zs, int n,
                               float* us, float* vs, float* ws) {
    for (int i = 0; i < n; i++) {
        float x = xs[i], y = ys[i], z = zs[i];
        us[i] = (R[0]*x + R[1]*y + R[2]*z) * zoom;
        vs[i] = (R[3]*x + R[4]*y + R[5]*z) * zoom;
        ws[i] =  R[6]*x + R[7]*y + R[8]*z;
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Palette (linear multi-stop gradient → 1 024-entry LUT)
// ────────────────────────────────────────────────────────────────────────────

struct ColorStop { float pos; uint8_t r, g, b; };

static const ColorStop NEBULA_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.25f,   0,  20,  80},
    {0.50f,  20,  80, 200}, {0.75f, 120, 180, 255}, {1.00f, 255, 255, 255},
};
static const ColorStop FIRE_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.30f,  80,   0,   0},
    {0.60f, 200,  60,   0}, {0.80f, 255, 160,  20}, {1.00f, 255, 255, 200},
};
static const ColorStop ELECTRIC_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.20f,   0,  40,  60},
    {0.50f,   0, 180, 200}, {0.80f,  80, 240, 255}, {1.00f, 255, 255, 255},
};
static const ColorStop AURORA_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.25f,  20,   0,  60},
    {0.50f,  80,   0, 160}, {0.75f, 180,  80, 240}, {1.00f, 240, 200, 255},
};
static const ColorStop MATRIX_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.30f,   0,  40,   0},
    {0.60f,   0, 160,  20}, {0.85f,  80, 240,  80}, {1.00f, 200, 255, 200},
};
static const ColorStop GREY_STOPS[] = {
    {0.00f,   0,   0,   0}, {1.00f, 255, 255, 255},
};
static const ColorStop SPECTRUM_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.15f,  80,   0, 160},
    {0.35f,   0,  40, 220}, {0.50f,   0, 200, 200},
    {0.65f,   0, 200,  60}, {0.80f, 220, 220,   0},
    {0.90f, 255, 130,   0}, {1.00f, 255, 240, 220},
};
static const ColorStop SUNSET_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.20f,  60,   0,  80},
    {0.40f, 180,   0, 120}, {0.65f, 255,  80,  20},
    {0.85f, 255, 200,  50}, {1.00f, 255, 240, 200},
};
static const ColorStop ICE_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.20f,   0,  20,  60},
    {0.45f,   0,  80, 140}, {0.70f,  80, 200, 240},
    {0.90f, 200, 240, 255}, {1.00f, 255, 255, 255},
};
static const ColorStop NEON_STOPS[] = {
    {0.00f,   0,   0,   0}, {0.25f,  60,   0,  60},
    {0.45f, 255,   0, 180}, {0.65f,   0, 255, 120},
    {0.85f, 200, 255,  80}, {1.00f, 255, 255, 255},
};

struct PaletteDesc { const ColorStop* stops; int n; };
static const PaletteDesc PALETTES[] = {
    {NEBULA_STOPS,    5}, {FIRE_STOPS,      5}, {ELECTRIC_STOPS,  5},
    {AURORA_STOPS,    5}, {MATRIX_STOPS,    5}, {GREY_STOPS,      2},
    {SPECTRUM_STOPS,  8}, {SUNSET_STOPS,    6}, {ICE_STOPS,       6},
    {NEON_STOPS,      6},
};
static constexpr int PALETTE_COUNT  = 10;
static constexpr int PALETTE_CUSTOM = 10;

static uint8_t interpChan(const ColorStop* s, int n, float t, int ch) {
    for (int i = 0; i < n - 1; i++) {
        if (t >= s[i].pos && t <= s[i+1].pos) {
            float f = (t - s[i].pos) / (s[i+1].pos - s[i].pos);
            float lo = (ch == 0) ? s[i].r   : (ch == 1) ? s[i].g   : s[i].b;
            float hi = (ch == 0) ? s[i+1].r : (ch == 1) ? s[i+1].g : s[i+1].b;
            return (uint8_t)(lo + (hi - lo) * f);
        }
    }
    const ColorStop& last = s[n-1];
    return (ch == 0) ? last.r : (ch == 1) ? last.g : last.b;
}

struct RGB { uint8_t r, g, b; };

static void buildLUT(int palIdx, RGB* lut, int size,
                     const float* customStops = nullptr, int numCustomStops = 0) {
    if (palIdx == PALETTE_CUSTOM && customStops != nullptr && numCustomStops >= 2) {
        for (int i = 0; i < size; i++) {
            float t = (float)i / (float)(size - 1);
            int lo = 0, hi = numCustomStops - 1;
            for (int s = 0; s < numCustomStops - 1; s++) {
                if (t >= customStops[s * 4] && t <= customStops[(s + 1) * 4]) {
                    lo = s; hi = s + 1; break;
                }
            }
            float loPos = customStops[lo * 4], hiPos = customStops[hi * 4];
            float f = (hiPos > loPos) ? (t - loPos) / (hiPos - loPos) : 0.f;
            f = (f < 0.f) ? 0.f : (f > 1.f ? 1.f : f);
            lut[i].r = (uint8_t)((customStops[lo*4+1] + (customStops[hi*4+1] - customStops[lo*4+1]) * f) * 255.f);
            lut[i].g = (uint8_t)((customStops[lo*4+2] + (customStops[hi*4+2] - customStops[lo*4+2]) * f) * 255.f);
            lut[i].b = (uint8_t)((customStops[lo*4+3] + (customStops[hi*4+3] - customStops[lo*4+3]) * f) * 255.f);
        }
        return;
    }
    if (palIdx < 0 || palIdx >= PALETTE_COUNT) palIdx = 0;
    const PaletteDesc& pd = PALETTES[palIdx];
    for (int i = 0; i < size; i++) {
        float t = (float)i / (float)(size - 1);
        lut[i].r = interpChan(pd.stops, pd.n, t, 0);
        lut[i].g = interpChan(pd.stops, pd.n, t, 1);
        lut[i].b = interpChan(pd.stops, pd.n, t, 2);
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Histogram accumulation
// ────────────────────────────────────────────────────────────────────────────

static void accumulateBatch(const float* us, const float* vs, int n,
                             uint32_t* hist, int width, int height,
                             float xMin, float xMax, float yMin, float yMax) {
    float xScale = (float)(width  - 1) / (xMax - xMin);
    float yScale = (float)(height - 1) / (yMax - yMin);
    for (int i = 0; i < n; i++) {
        int px = (int)((us[i] - xMin) * xScale);
        int py = (int)((vs[i] - yMin) * yScale);
        if ((unsigned)px < (unsigned)width && (unsigned)py < (unsigned)height) {
            hist[py * width + px]++;
        }
    }
}

static void accumulateBatchDepth(const float* us, const float* vs, const float* ws, int n,
                                  uint32_t* hist, float* depthAccum, int width, int height,
                                  float xMin, float xMax, float yMin, float yMax) {
    float xScale = (float)(width  - 1) / (xMax - xMin);
    float yScale = (float)(height - 1) / (yMax - yMin);
    for (int i = 0; i < n; i++) {
        int px = (int)((us[i] - xMin) * xScale);
        int py = (int)((vs[i] - yMin) * yScale);
        if ((unsigned)px < (unsigned)width && (unsigned)py < (unsigned)height) {
            int idx = py * width + px;
            hist[idx]++;
            depthAccum[idx] += ws[i];
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Main render function
// ────────────────────────────────────────────────────────────────────────────

static constexpr int BATCH_SIZE   = 65536;
static constexpr int WARMUP_STEPS = 1000;
static constexpr int BOUNDS_STEPS = 16; // × BATCH_SIZE pts used for auto-bounds

bool renderAttractor(const RenderParams& rp, int* outPixels) {
    const int W = rp.width, H = rp.height;

    // ── Rotation matrix ─────────────────────────────────────────────────────
    float R[9];
    buildRotationMatrix(rp.yaw, rp.pitch, rp.roll, R);

    // ── Work buffers ─────────────────────────────────────────────────────────
    // LIQUID (style 2) always uses depth regardless of the depthCue slider.
    const bool isLiquid = (rp.renderStyle == 2);
    const bool isLight  = (rp.renderStyle == 5);
    const bool useDepth = (rp.depthCue > 0.f) || isLiquid;

    std::vector<float>    xs(BATCH_SIZE), ys(BATCH_SIZE), zs(BATCH_SIZE);
    std::vector<float>    us(BATCH_SIZE), vs(BATCH_SIZE);
    std::vector<float>    ws(useDepth || isLight ? BATCH_SIZE : 0);
    std::vector<uint32_t> hist(static_cast<size_t>(W * H), 0u);
    std::vector<float>    depthAccum(useDepth ? static_cast<size_t>(W * H) : 0, 0.f);
    float wMin = FLT_MAX, wMax = -FLT_MAX;

    // ── Seed initial positions (deterministic LCG) ───────────────────────────
    uint32_t seed = 0xDEADBEEFu;
    auto lcg = [&]() -> float {
        seed = seed * 1664525u + 1013904223u;
        return ((float)(seed >> 8) / (float)(1u << 24)) - 0.5f;
    };
    for (int i = 0; i < BATCH_SIZE; i++) {
        xs[i] = lcg() * 0.1f;
        ys[i] = lcg() * 0.1f;
        zs[i] = lcg() * 0.1f;
    }

    // ── Warm-up: discard transients ──────────────────────────────────────────
    for (int w = 0; w < WARMUP_STEPS; w++) {
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_SIZE);
    }

    // ── Auto-bounds + (for LIGHT) mean speed estimation ─────────────────────
    float uMin = FLT_MAX, uMax = -FLT_MAX;
    float vMin = FLT_MAX, vMax = -FLT_MAX;

    // For LIGHT: accumulate mean 3-D step length to normalise speed later.
    double speedSum   = 0.0;
    int    speedCount = 0;
    std::vector<float> bprev_xs, bprev_ys, bprev_zs;
    if (isLight) {
        bprev_xs.assign(xs.begin(), xs.end());
        bprev_ys.assign(ys.begin(), ys.end());
        bprev_zs.assign(zs.begin(), zs.end());
    }

    for (int s = 0; s < BOUNDS_STEPS; s++) {
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_SIZE);

        if (isLight) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                float dx = xs[i] - bprev_xs[i];
                float dy = ys[i] - bprev_ys[i];
                float dz = zs[i] - bprev_zs[i];
                speedSum += sqrtf(dx*dx + dy*dy + dz*dz);
            }
            speedCount += BATCH_SIZE;
            bprev_xs.assign(xs.begin(), xs.end());
            bprev_ys.assign(ys.begin(), ys.end());
            bprev_zs.assign(zs.begin(), zs.end());
        }

        projectBatch(R, 1.0f, xs.data(), ys.data(), zs.data(), BATCH_SIZE,
                     us.data(), vs.data());
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (us[i] < uMin) uMin = us[i];
            if (us[i] > uMax) uMax = us[i];
            if (vs[i] < vMin) vMin = vs[i];
            if (vs[i] > vMax) vMax = vs[i];
        }
        if (useDepth) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                float w = R[6]*xs[i] + R[7]*ys[i] + R[8]*zs[i];
                if (w < wMin) wMin = w;
                if (w > wMax) wMax = w;
            }
        }
    }

    // Mean 3-D step length — used as tanh scale for LIGHT speed normalisation.
    // tanh(1) ≈ 0.76, so the mean speed maps near the 3/4 point of the palette.
    float speedScale = (speedCount > 0)
        ? (float)(speedSum / speedCount) : 1.f;
    if (speedScale < 1e-12f) speedScale = 1.f;

    float extraPad = (rp.boundsExtraPad > 0.f) ? rp.boundsExtraPad : 0.f;
    float padU = (uMax - uMin) * (0.05f + extraPad) + 1e-6f;
    float padV = (vMax - vMin) * (0.05f + extraPad) + 1e-6f;
    uMin -= padU; uMax += padU;
    vMin -= padV; vMax += padV;

    float zoom = (rp.zoom > 0.f) ? rp.zoom : 1.f;
    {
        float cu = (uMin + uMax) * 0.5f, hu = (uMax - uMin) * 0.5f / zoom;
        float cv = (vMin + vMax) * 0.5f, hv = (vMax - vMin) * 0.5f / zoom;
        uMin = cu - hu; uMax = cu + hu;
        vMin = cv - hv; vMax = cv + hv;
    }

    // ── Build palette LUT (shared by all modes) ──────────────────────────────
    static constexpr int LUT_SIZE = 1024;
    RGB lut[LUT_SIZE];
    buildLUT(rp.paletteIndex, lut, LUT_SIZE,
             rp.numCustomStops > 0 ? rp.customStops : nullptr, rp.numCustomStops);

    const float gamma = (rp.gamma > 0.f) ? rp.gamma : 1.f;

    // ════════════════════════════════════════════════════════════════════════
    // LIGHT mode (style 5) — per-point orbit-speed × curvature coloring
    // ════════════════════════════════════════════════════════════════════════
    //
    // Each accumulated point carries two properties:
    //   speed     = 3-D step length (prev → current), normalised via tanh.
    //               Maps to palette LUT index: slow = dark end, fast = bright end.
    //   curvature = cos of the angle at the previous point formed by the two
    //               consecutive displacement vectors.
    //               Sharp bend → bright multiplier; straight path → dim.
    //
    // Per-pixel colour = palette_color(speed) × curvature_brightness × log_density.
    if (isLight) {
        std::vector<float>    rAccum(static_cast<size_t>(W * H), 0.f);
        std::vector<float>    gAccum(static_cast<size_t>(W * H), 0.f);
        std::vector<float>    bAccum(static_cast<size_t>(W * H), 0.f);
        std::vector<uint32_t> lightCount(static_cast<size_t>(W * H), 0u);

        // Previous and pre-previous positions for each parallel trajectory.
        std::vector<float> prev_xs(xs), prev_ys(ys), prev_zs(zs);
        std::vector<float> pprev_xs(xs), pprev_ys(ys), pprev_zs(zs);

        // Advance once so prev ≠ pprev from the start.
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_SIZE);

        const float xScale = (float)(W - 1) / (uMax - uMin);
        const float yScale = (float)(H - 1) / (vMax - vMin);

        long long accumulated = 0;
        while (accumulated < rp.iterations) {
            const int batchN = (int)std::min((long long)BATCH_SIZE,
                                              rp.iterations - accumulated);

            // pprev ← prev ← xs before this iteration
            pprev_xs.assign(prev_xs.begin(), prev_xs.end());
            pprev_ys.assign(prev_ys.begin(), prev_ys.end());
            pprev_zs.assign(prev_zs.begin(), prev_zs.end());
            prev_xs.assign(xs.begin(), xs.end());
            prev_ys.assign(ys.begin(), ys.end());
            prev_zs.assign(zs.begin(), zs.end());

            attractorIterateN(rp.attractorType, rp.params,
                              xs.data(), ys.data(), zs.data(), batchN);

            for (int i = 0; i < batchN; i++) {
                // Step vectors in world space
                const float ax = xs[i]     - prev_xs[i];
                const float ay = ys[i]     - prev_ys[i];
                const float az = zs[i]     - prev_zs[i];
                const float bx = prev_xs[i] - pprev_xs[i];
                const float by = prev_ys[i] - pprev_ys[i];
                const float bz = prev_zs[i] - pprev_zs[i];

                const float lenA = sqrtf(ax*ax + ay*ay + az*az);
                const float lenB = sqrtf(bx*bx + by*by + bz*bz);

                // ── Speed → LUT index ─────────────────────────────────────
                // tanh(lenA / speedScale): mean speed ≈ 0.76 on the palette.
                const float speedNorm = (lenA > 0.f)
                    ? tanhf(lenA / speedScale)
                    : 0.f;
                const int lutIdx = (int)(speedNorm * (float)(LUT_SIZE - 1));

                // ── Curvature → brightness multiplier ─────────────────────
                // Sharp turn (cos → +1, 0°) = bright.
                // Straight path (cos → −1, 180°) = dim.
                // Range [0.08, 1.0] so even straight segments remain visible.
                float curvatureBrightness = 0.08f;
                if (lenA > 1e-12f && lenB > 1e-12f) {
                    float cosA = (ax*bx + ay*by + az*bz) / (lenA * lenB);
                    if (cosA >  1.f) cosA =  1.f;
                    if (cosA < -1.f) cosA = -1.f;
                    curvatureBrightness = 0.08f + 0.92f * (1.f + cosA) * 0.5f;
                }

                // ── Project prev position → pixel ────────────────────────
                const float u = (R[0]*prev_xs[i] + R[1]*prev_ys[i] + R[2]*prev_zs[i]);
                const float v = (R[3]*prev_xs[i] + R[4]*prev_ys[i] + R[5]*prev_zs[i]);
                const int px = (int)((u - uMin) * xScale);
                const int py = (int)((v - vMin) * yScale);

                if ((unsigned)px < (unsigned)W && (unsigned)py < (unsigned)H) {
                    const int idx = py * W + px;
                    rAccum[idx] += (float)lut[lutIdx].r * curvatureBrightness;
                    gAccum[idx] += (float)lut[lutIdx].g * curvatureBrightness;
                    bAccum[idx] += (float)lut[lutIdx].b * curvatureBrightness;
                    lightCount[idx]++;
                }
            }
            accumulated += batchN;
        }

        // ── Tone-map: log-brightness × per-pixel mean colour ────────────────
        uint32_t maxHit = *std::max_element(lightCount.begin(), lightCount.end());
        if (maxHit == 0) {
            const int bg = (rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u);
            for (int i = 0; i < W * H; i++) outPixels[i] = bg;
            return false;
        }
        const float logMax = logf(1.f + (float)maxHit);

        for (int i = 0; i < W * H; i++) {
            if (lightCount[i] == 0) {
                outPixels[i] = rp.transparentBg
                    ? 0
                    : ((rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u));
                continue;
            }
            float bright = logf(1.f + (float)lightCount[i]) / logMax;
            if (gamma != 1.f) bright = powf(bright, gamma);

            const float inv = 1.f / (float)lightCount[i];
            float fr = rAccum[i] * inv * bright;
            float fg = gAccum[i] * inv * bright;
            float fb = bAccum[i] * inv * bright;

            // Clamp to [0, 255]
            auto clamp255 = [](float v) -> uint8_t {
                return (v < 0.f) ? 0u : (v > 255.f) ? 255u : (uint8_t)v;
            };
            outPixels[i] = static_cast<int>(
                (0xFFu << 24) |
                ((uint32_t)clamp255(fr) << 16) |
                ((uint32_t)clamp255(fg) <<  8) |
                 (uint32_t)clamp255(fb)
            );
        }
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Histogram path (Standard, Sparse, Liquid, Plasma, Solid)
    // ════════════════════════════════════════════════════════════════════════

    long long accumulated = 0;
    while (accumulated < rp.iterations) {
        int batchN = (int)std::min((long long)BATCH_SIZE,
                                   rp.iterations - accumulated);
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), batchN);
        if (useDepth) {
            projectBatchDepth(R, 1.0f, xs.data(), ys.data(), zs.data(), batchN,
                              us.data(), vs.data(), ws.data());
            accumulateBatchDepth(us.data(), vs.data(), ws.data(), batchN,
                                 hist.data(), depthAccum.data(), W, H,
                                 uMin, uMax, vMin, vMax);
        } else {
            projectBatch(R, 1.0f, xs.data(), ys.data(), zs.data(), batchN,
                         us.data(), vs.data());
            accumulateBatch(us.data(), vs.data(), batchN, hist.data(), W, H,
                            uMin, uMax, vMin, vMax);
        }
        accumulated += batchN;
    }

    // Per-pixel mean depth, from raw counts.
    std::vector<float> meanW;
    if (useDepth) {
        meanW.assign(static_cast<size_t>(W * H), 0.f);
        for (int i = 0; i < W * H; i++)
            if (hist[i] > 0) meanW[i] = depthAccum[i] / (float)hist[i];
    }

    // ── Tone-map ─────────────────────────────────────────────────────────────
    uint32_t maxCount = *std::max_element(hist.begin(), hist.end());
    if (maxCount == 0) {
        int bgFill = (rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u);
        for (int i = 0; i < W * H; i++) outPixels[i] = bgFill;
        return false;
    }
    float logMax = logf(1.f + (float)maxCount);

    // ── Pass 1: per-pixel density ─────────────────────────────────────────────
    std::vector<float> dens(static_cast<size_t>(W * H), 0.f);
    for (int i = 0; i < W * H; i++) {
        if (hist[i] == 0) continue;
        float density;
        // SPARSE (style 1): 4th-root gives diffuse starfield / dust-cloud look.
        if (rp.renderStyle == 1) {
            density = powf((float)hist[i] / (float)maxCount, 0.25f);
        } else {
            density = logf(1.f + (float)hist[i]) / logMax;
        }

        switch (rp.renderStyle) {
        case 1: // SPARSE — no extra transform after 4th-root
            break;
        case 2: // LIQUID — log density; depth modulation applied in Pass 2
            if (gamma != 1.f) density = powf(density, gamma);
            break;
        case 3: // PLASMA — cyclic colour bands
            density = fmodf(density * 4.0f, 1.0f);
            break;
        case 4: // SOLID — binary threshold
            density = (density > 0.15f) ? 1.0f : 0.0f;
            break;
        default: // STANDARD (0) — log + gamma
            if (gamma != 1.f) density = powf(density, gamma);
            break;
        }
        dens[i] = density;
    }

    // ── Full-range equalisation ──────────────────────────────────────────────
    static constexpr int CDF_BINS = 1024;
    const bool equalize = (rp.fullRange != 0);
    std::vector<float> cdf;
    if (equalize) {
        std::vector<double> bins(CDF_BINS, 0.0);
        double pop = 0.0;
        for (int i = 0; i < W * H; i++) {
            if (hist[i] == 0) continue;
            float d = dens[i];
            int b = (int)(d * (float)(CDF_BINS - 1));
            if (b < 0) b = 0; else if (b >= CDF_BINS) b = CDF_BINS - 1;
            bins[b] += 1.0; pop += 1.0;
        }
        cdf.assign(CDF_BINS, 0.f);
        double acc = 0.0;
        for (int b = 0; b < CDF_BINS; b++) {
            acc += bins[b];
            cdf[b] = (pop > 0.0) ? (float)(acc / pop) : 0.f;
        }
    }

    // ── Pass 2: depth shade + colorize → ARGB_8888 ───────────────────────────
    for (int i = 0; i < W * H; i++) {
        if (hist[i] == 0) {
            if (rp.transparentBg) {
                outPixels[i] = 0;
            } else {
                outPixels[i] = (rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u);
            }
            continue;
        }
        float density = dens[i];
        if (equalize) {
            int b = (int)(density * (float)(CDF_BINS - 1));
            if (b < 0) b = 0; else if (b >= CDF_BINS) b = CDF_BINS - 1;
            density = cdf[b];
        }

        // Depth shading.
        if (useDepth && wMax > wMin) {
            float dn = (meanW[i] - wMin) / (wMax - wMin);
            if (dn < 0.f) dn = 0.f; else if (dn > 1.f) dn = 1.f;
            if (isLiquid) {
                // Original Liquid: z-buffer depth gives strong near-bright / far-dim.
                // Near (dn=0) unchanged; far (dn=1) dimmed to ~22% brightness.
                density *= 1.f - 0.78f * dn;
            } else if (rp.depthCue > 0.f) {
                density *= 1.f - rp.depthCue * 0.5f * dn;
            }
        }

        if (density < 0.f) density = 0.f;
        if (density > 1.f) density = 1.f;
        int idx = (int)(density * (float)(LUT_SIZE - 1));
        if (idx >= LUT_SIZE) idx = LUT_SIZE - 1;
        const RGB& c = lut[idx];
        outPixels[i] = static_cast<int>(
            (0xFFu << 24) | ((uint32_t)c.r << 16) |
            ((uint32_t)c.g << 8) | (uint32_t)c.b
        );
    }
    return true;
}

// ────────────────────────────────────────────────────────────────────────────
// Dot-preview: fast point cloud for real-time rotation feedback
// ────────────────────────────────────────────────────────────────────────────

std::vector<float> getProjectedPoints(const RenderParams& rp, int n_pts) {
    static constexpr int BATCH_DOT  = 4096;
    static constexpr int WARMUP_DOT = 100;
    static constexpr int BOUNDS_DOT = 2;

    float R[9];
    buildRotationMatrix(rp.yaw, rp.pitch, rp.roll, R);

    std::vector<float> xs(BATCH_DOT), ys(BATCH_DOT), zs(BATCH_DOT);
    std::vector<float> us(BATCH_DOT), vs(BATCH_DOT);

    uint32_t seed = 0xCAFEBABEu;
    auto lcg = [&]() -> float {
        seed = seed * 1664525u + 1013904223u;
        return ((float)(seed >> 8) / (float)(1u << 24)) - 0.5f;
    };
    for (int i = 0; i < BATCH_DOT; i++) {
        xs[i] = lcg() * 0.1f;
        ys[i] = lcg() * 0.1f;
        zs[i] = lcg() * 0.1f;
    }

    for (int w = 0; w < WARMUP_DOT; w++)
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_DOT);

    float uMin = FLT_MAX, uMax = -FLT_MAX;
    float vMin = FLT_MAX, vMax = -FLT_MAX;
    for (int s = 0; s < BOUNDS_DOT; s++) {
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_DOT);
        projectBatch(R, 1.0f, xs.data(), ys.data(), zs.data(), BATCH_DOT,
                     us.data(), vs.data());
        for (int i = 0; i < BATCH_DOT; i++) {
            if (us[i] < uMin) uMin = us[i];  if (us[i] > uMax) uMax = us[i];
            if (vs[i] < vMin) vMin = vs[i];  if (vs[i] > vMax) vMax = vs[i];
        }
    }
    float padU = (uMax - uMin) * 0.05f + 1e-6f;
    float padV = (vMax - vMin) * 0.05f + 1e-6f;
    uMin -= padU;  uMax += padU;
    vMin -= padV;  vMax += padV;
    float uRange = uMax - uMin;
    float vRange = vMax - vMin;
    float zoom = (rp.zoom > 0.f) ? rp.zoom : 1.f;

    std::vector<float> result;
    result.reserve(n_pts * 2);
    long long acc = 0;
    while (acc < n_pts) {
        int bn = (int)std::min((long long)BATCH_DOT, (long long)n_pts - acc);
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), bn);
        projectBatch(R, 1.0f, xs.data(), ys.data(), zs.data(), bn,
                     us.data(), vs.data());
        for (int i = 0; i < bn; i++) {
            result.push_back(((us[i] - uMin) / uRange * 2.f - 1.f) * zoom);
            result.push_back(((vs[i] - vMin) / vRange * 2.f - 1.f) * zoom);
        }
        acc += bn;
    }
    return result;
}

std::vector<float> getProjectedPointsDepth(const RenderParams& rp, int n_pts) {
    static constexpr int BATCH_DOT  = 4096;
    static constexpr int WARMUP_DOT = 100;
    static constexpr int BOUNDS_DOT = 2;

    float R[9];
    buildRotationMatrix(rp.yaw, rp.pitch, rp.roll, R);

    std::vector<float> xs(BATCH_DOT), ys(BATCH_DOT), zs(BATCH_DOT);
    std::vector<float> us(BATCH_DOT), vs(BATCH_DOT), ws(BATCH_DOT);

    uint32_t seed = 0xCAFEBABEu;
    auto lcg = [&]() -> float {
        seed = seed * 1664525u + 1013904223u;
        return ((float)(seed >> 8) / (float)(1u << 24)) - 0.5f;
    };
    for (int i = 0; i < BATCH_DOT; i++) {
        xs[i] = lcg() * 0.1f;
        ys[i] = lcg() * 0.1f;
        zs[i] = lcg() * 0.1f;
    }

    for (int w = 0; w < WARMUP_DOT; w++)
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_DOT);

    float uMin = FLT_MAX, uMax = -FLT_MAX;
    float vMin = FLT_MAX, vMax = -FLT_MAX;
    float wMin = FLT_MAX, wMax = -FLT_MAX;
    for (int s = 0; s < BOUNDS_DOT; s++) {
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), BATCH_DOT);
        projectBatchDepth(R, 1.0f, xs.data(), ys.data(), zs.data(), BATCH_DOT,
                          us.data(), vs.data(), ws.data());
        for (int i = 0; i < BATCH_DOT; i++) {
            if (us[i] < uMin) uMin = us[i];  if (us[i] > uMax) uMax = us[i];
            if (vs[i] < vMin) vMin = vs[i];  if (vs[i] > vMax) vMax = vs[i];
            if (ws[i] < wMin) wMin = ws[i];  if (ws[i] > wMax) wMax = ws[i];
        }
    }
    float padU = (uMax - uMin) * 0.05f + 1e-6f;
    float padV = (vMax - vMin) * 0.05f + 1e-6f;
    uMin -= padU;  uMax += padU;
    vMin -= padV;  vMax += padV;
    float uRange = uMax - uMin;
    float vRange = vMax - vMin;
    // Flat depth (2-D attractors, or a perfectly head-on view) has no usable
    // gradient — colour every dot at the palette midpoint so they stay visible.
    bool  flatDepth = (wMax - wMin) <= 1e-4f;
    float wRange    = flatDepth ? 1.f : (wMax - wMin);
    float zoom = (rp.zoom > 0.f) ? rp.zoom : 1.f;

    std::vector<float> result;
    result.reserve(n_pts * 3);
    long long acc = 0;
    while (acc < n_pts) {
        int bn = (int)std::min((long long)BATCH_DOT, (long long)n_pts - acc);
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), bn);
        projectBatchDepth(R, 1.0f, xs.data(), ys.data(), zs.data(), bn,
                          us.data(), vs.data(), ws.data());
        for (int i = 0; i < bn; i++) {
            result.push_back(((us[i] - uMin) / uRange * 2.f - 1.f) * zoom);
            result.push_back(((vs[i] - vMin) / vRange * 2.f - 1.f) * zoom);
            result.push_back(flatDepth ? 0.5f : (ws[i] - wMin) / wRange);  // depth in [0,1]
        }
        acc += bn;
    }
    return result;
}

void getPaletteLutARGB(int palIdx, int* out, int size,
                       const float* customStops, int numCustomStops) {
    if (size <= 0) return;
    std::vector<RGB> lut(static_cast<size_t>(size));
    buildLUT(palIdx, lut.data(), size, customStops, numCustomStops);
    for (int i = 0; i < size; i++) {
        out[i] = (0xFF << 24) | (lut[i].r << 16) | (lut[i].g << 8) | lut[i].b;
    }
}
