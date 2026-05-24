#pragma once
#include <cstdint>

// Attractor type constants – ordinals must match Kotlin AttractorType enum
static constexpr int ATTRACTOR_CLIFFORD      = 0;
static constexpr int ATTRACTOR_PETER_DE_JONG = 1;
static constexpr int ATTRACTOR_GUMOWSKI_MIRA = 2;
static constexpr int ATTRACTOR_LORENZ        = 3;
static constexpr int ATTRACTOR_ROSSLER       = 4;
static constexpr int ATTRACTOR_AIZAWA        = 5;
static constexpr int ATTRACTOR_THOMAS        = 6;
static constexpr int ATTRACTOR_CHAOTIC_FLOW  = 7;  // Dadras
static constexpr int ATTRACTOR_ICON          = 8;  // Symmetry Icons (p=3)
static constexpr int ATTRACTOR_IFS           = 9;  // Barnsley Fern
static constexpr int ATTRACTOR_JULIA         = 10; // Julia inverse iteration
static constexpr int ATTRACTOR_PICKOVER      = 11; // Pickover

/**
 * Iterate n points in-place for one step.
 *
 * Parameter layout per attractor:
 *   CLIFFORD       params[0..3] : a, b, c, d
 *   PETER_DE_JONG  params[0..3] : a, b, c, d
 *   GUMOWSKI_MIRA  params[0..1] : a, mu
 *   LORENZ         params[0..3] : sigma, rho, beta, dt
 *   ROSSLER        params[0..3] : a, b, c, dt
 *   AIZAWA         params[0..6] : a, b, c, d, e, f, dt
 *   THOMAS         params[0..1] : b, dt
 *   CHAOTIC_FLOW   params[0..5] : a, b, c, d, e, dt
 *   ICON           params[0..3] : lambda, alpha, beta, omega  (p=3 fixed)
 *   IFS            params[0..2] : width, lean, twist     (Barnsley Fern, 3-D)
 *   JULIA          params[0..2] : c_re, c_im, c_j        (quaternion Julia, 3-D)
 *   PICKOVER       params[0..3] : a, b, c, d
 */
void attractorIterateN(
    int type, const float* params,
    float* xs, float* ys, float* zs,
    int n
);
