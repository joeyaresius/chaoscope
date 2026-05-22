#pragma once
#include <cstdint>
#include <vector>

// Palette indices – ordinals must match Kotlin PaletteType enum
static constexpr int PALETTE_NEBULA    = 0;
static constexpr int PALETTE_FIRE      = 1;
static constexpr int PALETTE_ELECTRIC  = 2;
static constexpr int PALETTE_AURORA    = 3;
static constexpr int PALETTE_MATRIX    = 4;
static constexpr int PALETTE_GREYSCALE = 5;
static constexpr int PALETTE_SPECTRUM  = 6;
static constexpr int PALETTE_SUNSET    = 7;
static constexpr int PALETTE_ICE       = 8;
static constexpr int PALETTE_NEON      = 9;

struct RenderParams {
    int       attractorType;
    float     params[8];      // attractor-specific parameters
    int       width;
    int       height;
    long long iterations;
    float     yaw;            // camera rotation, degrees
    float     pitch;
    float     roll;
    float     zoom;
    int       paletteIndex;
    float     gamma;
    int       renderStyle; // 0=standard 1=gas 2=liquid 3=plasma 4=solid
    int       bgColor;     // ARGB_8888 background colour (default 0xFF000000)
    float     boundsExtraPad; // extra fraction added to auto-bounds (0 = default 5%)
    float     depthCue;    // 0 = flat, 1 = full depth shading (3-D attractors)
    int       fullRange;   // 1 = stretch density across the full palette (min..max)
    int       transparentBg; // 1 = emit 0x00000000 for empty pixels (PNG transparency)
    // Custom palette stops: flattened [pos, r, g, b, ...] with r/g/b in 0..1
    float     customStops[8 * 4];
    int       numCustomStops;
};

/**
 * Full render pipeline:
 *   1. Auto-detect world bounds
 *   2. Iterate attractor in batches, accumulate histogram
 *   3. Log tone-map
 *   4. Colorize with the chosen palette
 *
 * outPixels must be pre-allocated: width * height ints (ARGB_8888).
 * Returns true if the histogram had at least one hit, false if the orbit
 * diverged or produced no visible points (caller should retry or warn).
 */
bool renderAttractor(const RenderParams& rp, int* outPixels);

/**
 * Fast dot-preview: iterate the attractor and return n_pts projected
 * (u, v) pairs, each normalised to [-1, 1].  No histogram, no colours.
 * Intended for real-time rotation feedback (~50 K points, < 5 ms on ARM).
 */
std::vector<float> getProjectedPoints(const RenderParams& rp, int n_pts = 50000);
