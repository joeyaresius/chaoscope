#include "renderer.h"
#include "attractors.h"
#include <atomic>
#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>
#include <cfloat>
#include <thread>

// ────────────────────────────────────────────────────────────────────────────
// Render progress — polled from Kotlin while an HD render runs
// ────────────────────────────────────────────────────────────────────────────
// Only renders at PROGRESS_MIN_W or wider track progress, so a concurrent
// thumbnail/preview render can't corrupt the bar. The UI runs at most one HD
// render at a time.

static constexpr int PROGRESS_MIN_W = 1024;
static std::atomic<long long> g_progressDone{0};
static std::atomic<long long> g_progressTotal{0};

void renderProgressReset() {
    g_progressDone.store(0, std::memory_order_relaxed);
    g_progressTotal.store(0, std::memory_order_relaxed);
}

float renderProgress() {
    const long long total = g_progressTotal.load(std::memory_order_relaxed);
    if (total <= 0) return -1.f;
    const long long done = g_progressDone.load(std::memory_order_relaxed);
    const float p = (float)done / (float)total;
    return p > 1.f ? 1.f : p;
}

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
// Parallel helper
//
// The BATCH_SIZE independent trajectories never interact, so warmup, bounds and
// histogram accumulation are embarrassingly parallel across trajectory slices.
// runParallel runs fn(0..T-1); fn(0) executes on the calling thread so a 1-wide
// run carries no thread-spawn overhead.
// ────────────────────────────────────────────────────────────────────────────

template <typename F>
static void runParallel(int T, F&& fn) {
    if (T <= 1) { fn(0); return; }
    std::vector<std::thread> pool;
    pool.reserve(static_cast<size_t>(T - 1));
    for (int t = 1; t < T; t++) pool.emplace_back([&fn, t] { fn(t); });
    fn(0);
    for (auto& th : pool) th.join();
}

// Pick a worker count bounded by hardware concurrency (≤ 8) and a memory budget
// for the per-thread private accumulation buffers. Large canvases (HD / 4K) fall
// back to fewer threads so transient memory stays bounded; tiny jobs run 1-wide.
static int chooseThreadCount(long long perThreadBytes, long long iterations) {
    unsigned hw = std::thread::hardware_concurrency();
    int t = (hw == 0) ? 4 : static_cast<int>(hw);
    if (t > 8) t = 8;

    constexpr long long budget = 96LL * 1024 * 1024; // 96 MB for private buffers
    long long denom = (perThreadBytes > 0) ? perThreadBytes : 1;
    int maxByMem = static_cast<int>(budget / denom);
    if (maxByMem < 1) maxByMem = 1;
    if (t > maxByMem) t = maxByMem;

    if (iterations < 200000) t = 1; // thread-spawn overhead not worth it
    if (t < 1) t = 1;
    return t;
}

// ────────────────────────────────────────────────────────────────────────────
// Main render function
// ────────────────────────────────────────────────────────────────────────────

static constexpr int BATCH_SIZE   = 65536;
static constexpr int WARMUP_STEPS = 1000; // discard transients. Slow-settling
                                          // attractors (Aizawa, Thomas, Halvorsen,
                                          // Barnsley IFS) need this many to converge
                                          // before bounds detection — fewer breaks
                                          // their bounds on small (preview) renders.
                                          // Multithreading parallelises this cost.
static constexpr int BOUNDS_STEPS = 16;   // × BATCH_SIZE pts used for auto-bounds

bool renderAttractor(const RenderParams& rp, int* outPixels) {
    const int W = rp.width, H = rp.height;
    const size_t NPX = static_cast<size_t>(W) * static_cast<size_t>(H);

    // ── Rotation matrix ─────────────────────────────────────────────────────
    float R[9];
    buildRotationMatrix(rp.yaw, rp.pitch, rp.roll, R);

    // LIQUID (style 2) always uses depth regardless of the depthCue slider.
    const bool isLiquid = (rp.renderStyle == 2);
    const bool isLight  = (rp.renderStyle == 5);
    const bool useDepth = (rp.depthCue > 0.f) || isLiquid;

    // ── Shared trajectory state (each worker owns a contiguous slice) ────────
    std::vector<float> xs(BATCH_SIZE), ys(BATCH_SIZE), zs(BATCH_SIZE);

    // Seed initial positions (deterministic LCG; single-threaded so the seed
    // sequence — and therefore the render — is reproducible run to run).
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

    // ── Worker count + partition ─────────────────────────────────────────────
    // Private per-thread accumulation buffers: histogram (uint32) + depth (float)
    // for the standard path; r/g/b (float×3) + count (uint32) for LIGHT.
    const long long perThreadBytes = isLight
        ? static_cast<long long>(NPX) * 16
        : static_cast<long long>(NPX) * (4 + (useDepth ? 4 : 0));
    const int T = chooseThreadCount(perThreadBytes, rp.iterations);
    auto sliceLo = [&](int t) -> int {
        return static_cast<int>(static_cast<long long>(t) * BATCH_SIZE / T);
    };

    // Progress: each thread walks warmup + bounds + accumulation steps; one
    // atomic tick per step is negligible next to the per-step batch math.
    const bool trackProgress = (W >= PROGRESS_MIN_W);
    if (trackProgress) {
        const long long perThread =
            WARMUP_STEPS + BOUNDS_STEPS + rp.iterations / BATCH_SIZE + 1;
        g_progressDone.store(0, std::memory_order_relaxed);
        g_progressTotal.store(perThread * T, std::memory_order_relaxed);
    }

    // ── Phase A: warmup + auto-bounds (parallel over trajectory slices) ──────
    struct Stat {
        float uMin = FLT_MAX, uMax = -FLT_MAX;
        float vMin = FLT_MAX, vMax = -FLT_MAX;
        float wMin = FLT_MAX, wMax = -FLT_MAX;
        double speedSum = 0.0; long long speedCount = 0; // LIGHT only
    };
    std::vector<Stat> stats(static_cast<size_t>(T));

    runParallel(T, [&](int t) {
        const int lo = sliceLo(t), hi = sliceLo(t + 1), n = hi - lo;
        if (n <= 0) return;
        float* X = xs.data() + lo;
        float* Y = ys.data() + lo;
        float* Z = zs.data() + lo;

        for (int w = 0; w < WARMUP_STEPS; w++) {
            attractorIterateN(rp.attractorType, rp.params, X, Y, Z, n);
            if (trackProgress) g_progressDone.fetch_add(1, std::memory_order_relaxed);
        }

        Stat st;
        // LIGHT: mean 3-D step length, to normalise speed later.
        std::vector<float> px, py, pz;
        if (isLight) { px.assign(X, X + n); py.assign(Y, Y + n); pz.assign(Z, Z + n); }

        for (int s = 0; s < BOUNDS_STEPS; s++) {
            attractorIterateN(rp.attractorType, rp.params, X, Y, Z, n);
            if (trackProgress) g_progressDone.fetch_add(1, std::memory_order_relaxed);
            if (isLight) {
                for (int i = 0; i < n; i++) {
                    float dx = X[i] - px[i], dy = Y[i] - py[i], dz = Z[i] - pz[i];
                    st.speedSum += sqrtf(dx*dx + dy*dy + dz*dz);
                }
                st.speedCount += n;
                px.assign(X, X + n); py.assign(Y, Y + n); pz.assign(Z, Z + n);
            }
            for (int i = 0; i < n; i++) {
                float u = R[0]*X[i] + R[1]*Y[i] + R[2]*Z[i];
                float v = R[3]*X[i] + R[4]*Y[i] + R[5]*Z[i];
                if (u < st.uMin) st.uMin = u;
                if (u > st.uMax) st.uMax = u;
                if (v < st.vMin) st.vMin = v;
                if (v > st.vMax) st.vMax = v;
            }
            if (useDepth) {
                for (int i = 0; i < n; i++) {
                    float w = R[6]*X[i] + R[7]*Y[i] + R[8]*Z[i];
                    if (w < st.wMin) st.wMin = w;
                    if (w > st.wMax) st.wMax = w;
                }
            }
        }
        stats[static_cast<size_t>(t)] = st;
    });

    // Reduce per-thread bounds (min/max are order-independent → identical to the
    // single-threaded result for deterministic attractors).
    float uMin = FLT_MAX, uMax = -FLT_MAX, vMin = FLT_MAX, vMax = -FLT_MAX;
    float wMin = FLT_MAX, wMax = -FLT_MAX;
    double speedSum = 0.0; long long speedCount = 0;
    for (const Stat& st : stats) {
        uMin = std::min(uMin, st.uMin); uMax = std::max(uMax, st.uMax);
        vMin = std::min(vMin, st.vMin); vMax = std::max(vMax, st.vMax);
        wMin = std::min(wMin, st.wMin); wMax = std::max(wMax, st.wMax);
        speedSum += st.speedSum; speedCount += st.speedCount;
    }

    // Mean 3-D step length — tanh scale for LIGHT speed normalisation.
    float speedScale = (speedCount > 0) ? (float)(speedSum / speedCount) : 1.f;
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

    const float xScale = (uMax > uMin) ? (float)(W - 1) / (uMax - uMin) : 0.f;
    const float yScale = (vMax > vMin) ? (float)(H - 1) / (vMax - vMin) : 0.f;

    // ── Build palette LUT (shared by all modes) ──────────────────────────────
    static constexpr int LUT_SIZE = 1024;
    RGB lut[LUT_SIZE];
    buildLUT(rp.paletteIndex, lut, LUT_SIZE,
             rp.numCustomStops > 0 ? rp.customStops : nullptr, rp.numCustomStops);

    const float gamma = (rp.gamma > 0.f) ? rp.gamma : 1.f;

    // Per-trajectory step counts. Trajectories [0, rem) take one extra step so
    // the total accumulated point count matches rp.iterations exactly.
    const long long baseSteps = rp.iterations / BATCH_SIZE;
    const int       rem       = static_cast<int>(rp.iterations % BATCH_SIZE);

    auto pixelRange = [&](int t, size_t& lo, size_t& hi) {
        lo = NPX * static_cast<size_t>(t) / static_cast<size_t>(T);
        hi = NPX * static_cast<size_t>(t + 1) / static_cast<size_t>(T);
    };

    // ════════════════════════════════════════════════════════════════════════
    // LIGHT mode (style 5) — per-point orbit-speed × curvature coloring
    // ════════════════════════════════════════════════════════════════════════
    //   speed     = 3-D step length (prev → current), tanh-normalised → LUT idx.
    //   curvature = cos of the bend angle at prev; sharp turn = bright.
    // Per-pixel colour = palette_color(speed) × curvature × log_density.
    if (isLight) {
        std::vector<std::vector<float>>    tR(T), tG(T), tB(T);
        std::vector<std::vector<uint32_t>> tCount(T);

        runParallel(T, [&](int t) {
            tR[t].assign(NPX, 0.f);
            tG[t].assign(NPX, 0.f);
            tB[t].assign(NPX, 0.f);
            tCount[t].assign(NPX, 0u);

            const int lo = sliceLo(t), hi = sliceLo(t + 1), n = hi - lo;
            if (n <= 0) return;
            float* X = xs.data() + lo;
            float* Y = ys.data() + lo;
            float* Z = zs.data() + lo;
            float*    rAcc = tR[t].data();
            float*    gAcc = tG[t].data();
            float*    bAcc = tB[t].data();
            uint32_t* cAcc = tCount[t].data();

            // prev / pprev trajectories for this slice (pointer-rotated, no copy).
            std::vector<float> a_x(X, X + n), a_y(Y, Y + n), a_z(Z, Z + n);
            std::vector<float> b_x(X, X + n), b_y(Y, Y + n), b_z(Z, Z + n);
            float* prev_x = a_x.data(); float* prev_y = a_y.data(); float* prev_z = a_z.data();
            float* pprev_x = b_x.data(); float* pprev_y = b_y.data(); float* pprev_z = b_z.data();

            // Advance once so prev ≠ pprev from the start.
            attractorIterateN(rp.attractorType, rp.params, X, Y, Z, n);

            auto lightStep = [&](int m) {
                // pprev ← prev ← current  (rotate the two history buffers, then
                // copy the live positions into prev — one memcpy instead of three
                // vector assigns).
                std::swap(prev_x, pprev_x); std::swap(prev_y, pprev_y); std::swap(prev_z, pprev_z);
                std::memcpy(prev_x, X, sizeof(float) * static_cast<size_t>(n));
                std::memcpy(prev_y, Y, sizeof(float) * static_cast<size_t>(n));
                std::memcpy(prev_z, Z, sizeof(float) * static_cast<size_t>(n));

                attractorIterateN(rp.attractorType, rp.params, X, Y, Z, m);

                for (int i = 0; i < m; i++) {
                    const float ax = X[i]      - prev_x[i];
                    const float ay = Y[i]      - prev_y[i];
                    const float az = Z[i]      - prev_z[i];
                    const float bx = prev_x[i] - pprev_x[i];
                    const float by = prev_y[i] - pprev_y[i];
                    const float bz = prev_z[i] - pprev_z[i];

                    const float lenA = sqrtf(ax*ax + ay*ay + az*az);
                    const float lenB = sqrtf(bx*bx + by*by + bz*bz);

                    const float speedNorm = (lenA > 0.f) ? tanhf(lenA / speedScale) : 0.f;
                    const int lutIdx = (int)(speedNorm * (float)(LUT_SIZE - 1));

                    float curvatureBrightness = 0.08f;
                    if (lenA > 1e-12f && lenB > 1e-12f) {
                        float cosA = (ax*bx + ay*by + az*bz) / (lenA * lenB);
                        if (cosA >  1.f) cosA =  1.f;
                        if (cosA < -1.f) cosA = -1.f;
                        curvatureBrightness = 0.08f + 0.92f * (1.f + cosA) * 0.5f;
                    }

                    const float u = R[0]*prev_x[i] + R[1]*prev_y[i] + R[2]*prev_z[i];
                    const float v = R[3]*prev_x[i] + R[4]*prev_y[i] + R[5]*prev_z[i];
                    const int px = (int)((u - uMin) * xScale);
                    const int py = (int)((v - vMin) * yScale);

                    if ((unsigned)px < (unsigned)W && (unsigned)py < (unsigned)H) {
                        const int idx = py * W + px;
                        rAcc[idx] += (float)lut[lutIdx].r * curvatureBrightness;
                        gAcc[idx] += (float)lut[lutIdx].g * curvatureBrightness;
                        bAcc[idx] += (float)lut[lutIdx].b * curvatureBrightness;
                        cAcc[idx]++;
                    }
                }
            };

            for (long long s = 0; s < baseSteps; s++) {
                lightStep(n);
                if (trackProgress) g_progressDone.fetch_add(1, std::memory_order_relaxed);
            }
            int exCount = std::min(hi, rem) - lo;   // extra step for global idx < rem
            if (exCount > n) exCount = n;
            if (exCount > 0) lightStep(exCount);
        });

        // Merge per-thread buffers (parallel over pixel ranges).
        std::vector<float>    rAccum(NPX, 0.f), gAccum(NPX, 0.f), bAccum(NPX, 0.f);
        std::vector<uint32_t> lightCount(NPX, 0u);
        runParallel(T, [&](int t) {
            size_t lo, hi; pixelRange(t, lo, hi);
            for (size_t i = lo; i < hi; i++) {
                float fr = 0.f, fg = 0.f, fb = 0.f; uint32_t c = 0;
                for (int k = 0; k < T; k++) {
                    fr += tR[k][i]; fg += tG[k][i]; fb += tB[k][i]; c += tCount[k][i];
                }
                rAccum[i] = fr; gAccum[i] = fg; bAccum[i] = fb; lightCount[i] = c;
            }
        });

        uint32_t maxHit = *std::max_element(lightCount.begin(), lightCount.end());
        if (maxHit == 0) {
            const int bg = (rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u);
            for (size_t i = 0; i < NPX; i++) outPixels[i] = bg;
            return false;
        }
        const float logMax = logf(1.f + (float)maxHit);

        runParallel(T, [&](int t) {
            size_t lo, hi; pixelRange(t, lo, hi);
            for (size_t i = lo; i < hi; i++) {
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
        });
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Histogram path (Standard, Sparse, Liquid, Plasma, Solid)
    // ════════════════════════════════════════════════════════════════════════

    std::vector<std::vector<uint32_t>> tHist(T);
    std::vector<std::vector<float>>    tDepth(T);

    runParallel(T, [&](int t) {
        tHist[t].assign(NPX, 0u);
        if (useDepth) tDepth[t].assign(NPX, 0.f);

        const int lo = sliceLo(t), hi = sliceLo(t + 1), n = hi - lo;
        if (n <= 0) return;
        float* X = xs.data() + lo;
        float* Y = ys.data() + lo;
        float* Z = zs.data() + lo;
        uint32_t* HH = tHist[t].data();
        float*    DD = useDepth ? tDepth[t].data() : nullptr;

        auto stepAccum = [&](int m) {
            attractorIterateN(rp.attractorType, rp.params, X, Y, Z, m);
            if (useDepth) {
                for (int i = 0; i < m; i++) {
                    float u = R[0]*X[i] + R[1]*Y[i] + R[2]*Z[i];
                    float v = R[3]*X[i] + R[4]*Y[i] + R[5]*Z[i];
                    float w = R[6]*X[i] + R[7]*Y[i] + R[8]*Z[i];
                    int px = (int)((u - uMin) * xScale);
                    int py = (int)((v - vMin) * yScale);
                    if ((unsigned)px < (unsigned)W && (unsigned)py < (unsigned)H) {
                        int idx = py * W + px;
                        HH[idx]++;
                        DD[idx] += w;
                    }
                }
            } else {
                for (int i = 0; i < m; i++) {
                    float u = R[0]*X[i] + R[1]*Y[i] + R[2]*Z[i];
                    float v = R[3]*X[i] + R[4]*Y[i] + R[5]*Z[i];
                    int px = (int)((u - uMin) * xScale);
                    int py = (int)((v - vMin) * yScale);
                    if ((unsigned)px < (unsigned)W && (unsigned)py < (unsigned)H)
                        HH[py * W + px]++;
                }
            }
        };

        for (long long s = 0; s < baseSteps; s++) {
            stepAccum(n);
            if (trackProgress) g_progressDone.fetch_add(1, std::memory_order_relaxed);
        }
        int exCount = std::min(hi, rem) - lo;
        if (exCount > n) exCount = n;
        if (exCount > 0) stepAccum(exCount);
    });

    // Merge per-thread histograms (and depth) — parallel over pixel ranges.
    std::vector<uint32_t> hist(NPX, 0u);
    std::vector<float>    depthAccum(useDepth ? NPX : 0, 0.f);
    runParallel(T, [&](int t) {
        size_t lo, hi; pixelRange(t, lo, hi);
        for (size_t i = lo; i < hi; i++) {
            uint32_t s = 0;
            for (int k = 0; k < T; k++) s += tHist[k][i];
            hist[i] = s;
            if (useDepth) {
                float d = 0.f;
                for (int k = 0; k < T; k++) d += tDepth[k][i];
                depthAccum[i] = d;
            }
        }
    });

    // Per-pixel mean depth, from raw counts.
    std::vector<float> meanW;
    if (useDepth) {
        meanW.assign(NPX, 0.f);
        for (size_t i = 0; i < NPX; i++)
            if (hist[i] > 0) meanW[i] = depthAccum[i] / (float)hist[i];
    }

    // ── Tone-map ─────────────────────────────────────────────────────────────
    uint32_t maxCount = *std::max_element(hist.begin(), hist.end());
    if (maxCount == 0) {
        int bgFill = (rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u);
        for (size_t i = 0; i < NPX; i++) outPixels[i] = bgFill;
        return false;
    }
    float logMax = logf(1.f + (float)maxCount);

    // ── Pass 1: per-pixel density (parallel) ──────────────────────────────────
    std::vector<float> dens(NPX, 0.f);
    runParallel(T, [&](int t) {
        size_t lo, hi; pixelRange(t, lo, hi);
        for (size_t i = lo; i < hi; i++) {
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
    });

    // ── Full-range equalisation (CDF build is a reduction → single-threaded) ──
    static constexpr int CDF_BINS = 1024;
    const bool equalize = (rp.fullRange != 0);
    std::vector<float> cdf;
    if (equalize) {
        std::vector<double> bins(CDF_BINS, 0.0);
        double pop = 0.0;
        for (size_t i = 0; i < NPX; i++) {
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

    // ── Pass 2: depth shade + colorize → ARGB_8888 (parallel) ────────────────
    runParallel(T, [&](int t) {
        size_t lo, hi; pixelRange(t, lo, hi);
        for (size_t i = lo; i < hi; i++) {
            if (hist[i] == 0) {
                outPixels[i] = rp.transparentBg
                    ? 0
                    : ((rp.bgColor != 0) ? rp.bgColor : static_cast<int>(0xFF000000u));
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
    });
    return true;
}

// ────────────────────────────────────────────────────────────────────────────
// Dot-preview: fast point cloud for real-time rotation feedback
// ────────────────────────────────────────────────────────────────────────────

std::vector<float> getProjectedPoints(const RenderParams& rp, int n_pts) {
    static constexpr int BATCH_DOT  = 4096;
    static constexpr int WARMUP_DOT = WARMUP_STEPS; // match the render warmup so
                                            // preview and render show the same region
    // 8 batches × 4096 = 32 768 bounds-detection samples — enough for attractors
    // with a large z-extent (e.g. Barnsley Fern with high twist) that need more
    // points to reliably establish their bounding box.
    static constexpr int BOUNDS_DOT = 8;

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
    static constexpr int WARMUP_DOT = WARMUP_STEPS; // match the render warmup
    static constexpr int BOUNDS_DOT = 8;   // 32 768 samples — robust for large z-extent

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
