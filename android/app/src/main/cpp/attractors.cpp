#include "attractors.h"
#include <cmath>

void attractorIterateN(
    int type, const float* p,
    float* xs, float* ys, float* zs,
    int n
) {
    switch (type) {

    // ── Clifford 3-D ───────────────────────────────────────────────────────
    // x' = sin(a*y) + c*cos(a*x)
    // y' = sin(b*x) + d*cos(b*y)
    // z' = sin(e*y) + f*cos(e*z)
    case ATTRACTOR_CLIFFORD: {
        const float a = p[0], b = p[1], c = p[2], d = p[3], e = p[4], f = p[5];
        for (int i = 0; i < n; i++) {
            float xn = sinf(a * ys[i]) + c * cosf(a * xs[i]);
            float yn = sinf(b * xs[i]) + d * cosf(b * ys[i]);
            float zn = sinf(e * ys[i]) + f * cosf(e * zs[i]);
            xs[i] = xn; ys[i] = yn; zs[i] = zn;
        }
        break;
    }

    // ── Peter de Jong 3-D ────────────────────────────────────────────────
    // x' = sin(a*y) - cos(b*x)
    // y' = sin(c*x) - cos(d*y)
    // z' = sin(e*z) - cos(f*y)
    case ATTRACTOR_PETER_DE_JONG: {
        const float a = p[0], b = p[1], c = p[2], d = p[3], e = p[4], f = p[5];
        for (int i = 0; i < n; i++) {
            float xn = sinf(a * ys[i]) - cosf(b * xs[i]);
            float yn = sinf(c * xs[i]) - cosf(d * ys[i]);
            float zn = sinf(e * zs[i]) - cosf(f * ys[i]);
            xs[i] = xn; ys[i] = yn; zs[i] = zn;
        }
        break;
    }

    // ── Gumowski-Mira ────────────────────────────────────────────────────────
    // f(x) = mu*x + 2*(1-mu)*x²/(1+x²)
    // x' = y + a*(1 - 0.05*y²)*y + f(x)
    // y' = -x + f(x')
    case ATTRACTOR_GUMOWSKI_MIRA: {
        const float a = p[0], mu = p[1];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i];
            float fx  = mu * x  + 2.f * (1.f - mu) * x  * x  / (1.f + x  * x );
            float xn  = y + a * (1.f - 0.05f * y * y) * y + fx;
            float fxn = mu * xn + 2.f * (1.f - mu) * xn * xn / (1.f + xn * xn);
            xs[i] = xn;
            ys[i] = -x + fxn;
            zs[i] = 0.f;
        }
        break;
    }

    // ── Lorenz (Euler) ───────────────────────────────────────────────────────
    // dx/dt = sigma*(y - x)
    // dy/dt = x*(rho - z) - y
    // dz/dt = x*y - beta*z
    case ATTRACTOR_LORENZ: {
        const float sigma = p[0], rho = p[1], beta = p[2], dt = p[3];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i], z = zs[i];
            xs[i] = x + dt * sigma * (y - x);
            ys[i] = y + dt * (x * (rho - z) - y);
            zs[i] = z + dt * (x * y - beta * z);
        }
        break;
    }

    // ── Rössler (Euler) ──────────────────────────────────────────────────────
    // dx/dt = -(y + z)
    // dy/dt = x + a*y
    // dz/dt = b + z*(x - c)
    case ATTRACTOR_ROSSLER: {
        const float a = p[0], b = p[1], c = p[2], dt = p[3];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i], z = zs[i];
            xs[i] = x + dt * (-(y + z));
            ys[i] = y + dt * (x + a * y);
            zs[i] = z + dt * (b + z * (x - c));
        }
        break;
    }

    // ── Aizawa (Euler) ───────────────────────────────────────────────────────
    // dx/dt = (z-b)*x - d*y
    // dy/dt = d*x + (z-b)*y
    // dz/dt = c + a*z - z³/3 - (x²+y²)*(1+e*z) + f*z*x³
    case ATTRACTOR_AIZAWA: {
        const float a = p[0], b = p[1], c = p[2], d = p[3];
        const float e = p[4], f = p[5], dt = p[6];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i], z = zs[i];
            float dx = (z - b) * x - d * y;
            float dy = d * x + (z - b) * y;
            float dz = c + a * z - (z * z * z) / 3.f
                      - (x * x + y * y) * (1.f + e * z)
                      + f * z * x * x * x;
            xs[i] = x + dt * dx;
            ys[i] = y + dt * dy;
            zs[i] = z + dt * dz;
        }
        break;
    }

    // ── Thomas (Euler) ───────────────────────────────────────────────────────
    // dx/dt = sin(y) - b*x
    // dy/dt = sin(z) - b*y
    // dz/dt = sin(x) - b*z
    case ATTRACTOR_THOMAS: {
        const float b = p[0], dt = p[1];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i], z = zs[i];
            xs[i] = x + dt * (sinf(y) - b * x);
            ys[i] = y + dt * (sinf(z) - b * y);
            zs[i] = z + dt * (sinf(x) - b * z);
        }
        break;
    }

    // ── Chaotic Flow / Dadras (Euler) ────────────────────────────────────────
    // dx/dt = y - a*x + b*y*z
    // dy/dt = c*y - x*z + z
    // dz/dt = d*x*y - e*z
    case ATTRACTOR_CHAOTIC_FLOW: {
        const float a = p[0], b = p[1], c = p[2];
        const float d = p[3], e = p[4], dt = p[5];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i], z = zs[i];
            xs[i] = x + dt * (y - a*x + b*y*z);
            ys[i] = y + dt * (c*y - x*z + z);
            zs[i] = z + dt * (d*x*y - e*z);
        }
        break;
    }

    // ── Icon / Symmetry Icons (p=3, map-based) ───────────────────────────────
    // z_{n+1} = (λ + α|z|² + β·Re(z³))·z + ω·conj(z)²
    // real: t = λ + α·r² + β·(x³-3xy²)
    //       x' = t·x + ω·(x²-y²)
    //       y' = t·y - ω·2xy
    case ATTRACTOR_ICON: {
        const float lam = p[0], alp = p[1], bet = p[2], omg = p[3];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i];
            float r2   = x*x + y*y;
            float rez3 = x*x*x - 3.f*x*y*y;
            float t    = lam + alp*r2 + bet*rez3;
            xs[i] = t*x + omg*(x*x - y*y);
            ys[i] = t*y - omg*(2.f*x*y);
            zs[i] = 0.f;
        }
        break;
    }

    // ── IFS: Barnsley Fern (3-D) ──────────────────────────────────────────────
    // Four affine transforms with fixed probabilities. The dominant frond map
    // rotates the y-z plane by `twist` each application, so repeated fronds spiral
    // out of the plane into a true 3-D fern. twist = 0 recovers the flat 2-D fern.
    // params: width (stem x-scale), lean (stem shear offset), twist (3-D angle).
    case ATTRACTOR_IFS: {
        const float wd = p[0]; // stem width multiplier (default 1.0)
        const float ln = p[1]; // stem lean/shear offset (default 0.0)
        const float tw = p[2]; // 3-D twist angle in radians (default ~0.2)
        const float ct = cosf(tw), st = sinf(tw);
        uint32_t rng = (uint32_t)((xs[0] + 1.f) * 1e5f) ^ 0xABCD1234u;
        if (rng == 0) rng = 0xDEADu;
        for (int i = 0; i < n; i++) {
            rng ^= rng << 13; rng ^= rng >> 17; rng ^= rng << 5;
            float r = (float)(rng >> 8) * (1.f / (float)(1u << 24));
            float x = xs[i], y = ys[i], z = zs[i];
            if (r < 0.01f) {
                xs[i] = 0.f;
                ys[i] = 0.16f * y;
                zs[i] = 0.16f * z;
            } else if (r < 0.86f) {
                // contract y,z by 0.85 then rotate the y-z plane by `twist`
                float cy = 0.85f * y, cz = 0.85f * z;
                xs[i] =  (0.85f * wd) * x + (0.04f + ln) * y;
                ys[i] = -(0.04f + ln) * x + (ct * cy - st * cz) + 1.6f;
                zs[i] =  st * cy + ct * cz;
            } else if (r < 0.93f) {
                xs[i] =  0.20f * x - 0.26f * y;
                ys[i] =  0.23f * x + 0.22f * y + 1.6f;
                zs[i] =  0.30f * z;
            } else {
                xs[i] = -0.15f * x + 0.28f * y;
                ys[i] =  0.26f * x + 0.24f * y + 0.44f;
                zs[i] =  0.30f * z;
            }
        }
        break;
    }

    // ── Julia: quaternion inverse iteration (3-D) ────────────────────────────
    // Plots the boundary of the quaternion Julia set q' = q² + c in the k = 0
    // slice (q = w + x·i + y·j), via the inverse map q = sqrt(q_prev - c) with a
    // random ± branch. Render coords are (w, x, y) → a genuine 3-D surface.
    // params: c_re (scalar), c_im (i part), c_j (j part — drives the 3-D depth;
    // 0 collapses to the flat 2-D Julia).
    case ATTRACTOR_JULIA: {
        const float cw = p[0], cx = p[1], cy = p[2];
        uint32_t rng = (uint32_t)((xs[0] + 2.f) * 1e5f) ^ 0x9E3779B9u;
        if (rng == 0) rng = 1;
        const float pi = 3.14159265f;
        for (int i = 0; i < n; i++) {
            // T = q - c   (quaternion, k = 0)
            float tw = xs[i] - cw;
            float tx = ys[i] - cx;
            float ty = zs[i] - cy;
            float vm  = sqrtf(tx*tx + ty*ty);          // |vector part|
            float mag = sqrtf(tw*tw + tx*tx + ty*ty);  // |T|
            float r     = sqrtf(mag);                  // |sqrt(T)|
            float theta = 0.5f * atan2f(vm, tw);
            rng ^= rng << 13; rng ^= rng >> 17; rng ^= rng << 5;
            if (rng & 1u) theta += pi;                 // random ± branch
            float sw  = r * cosf(theta);               // new scalar
            float svm = r * sinf(theta);               // new vector magnitude
            xs[i] = sw;
            if (vm > 1e-6f) {
                float k = svm / vm;
                ys[i] = k * tx;
                zs[i] = k * ty;
            } else {
                ys[i] = svm;
                zs[i] = 0.f;
            }
        }
        break;
    }

    // ── Pickover ─────────────────────────────────────────────────────────────
    // x' = sin(a·y) - z·cos(b·x)
    // y' = z·sin(c·x) - cos(d·y)
    // z' = sin(x)   (canonical Pickover; z feeds back into x and y → 3-D cloud)
    case ATTRACTOR_PICKOVER: {
        const float a = p[0], b = p[1], c = p[2], d = p[3];
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i], z = zs[i];
            xs[i] = sinf(a*y) - z*cosf(b*x);
            ys[i] = z*sinf(c*x) - cosf(d*y);
            zs[i] = sinf(x);
        }
        break;
    }

    default:
        break;
    }
}
