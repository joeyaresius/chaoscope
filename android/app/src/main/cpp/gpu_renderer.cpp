#include "gpu_renderer.h"
#include "attractors.h"
#include <android/log.h>
#include <algorithm>
#include <cfloat>
#include <cmath>
#include <cstring>
#include <vector>

#define TAG "ChaoscopeGPU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─────────────────────────────────────────────────────────────────────────────
// Shader sources
// ─────────────────────────────────────────────────────────────────────────────

// Fills every texel of the histogram with 0. Dispatched as (ceil(W/16), ceil(H/16), 1).
static const char* CLEAR_SHADER_SRC = R"GLSL(
#version 310 es
layout(local_size_x = 16, local_size_y = 16) in;
layout(r32ui, binding = 0) uniform writeonly uimage2D u_hist;
void main() {
    imageStore(u_hist, ivec2(gl_GlobalInvocationID.xy), uvec4(0u));
}
)GLSL";

// Each invocation runs one independent attractor trajectory.
// local_size_x = 128 (guaranteed minimum by GLES 3.1 spec).
// Dispatched as (ceil(targetThreads / 128), 1, 1).
static const char* ITER_SHADER_SRC = R"GLSL(
#version 310 es
precision highp float;
precision highp int;

layout(local_size_x = 128) in;

layout(r32ui, binding = 0) uniform uimage2D u_hist;

uniform int   u_type;
uniform float u_p[8];
// Rotation matrix rows 0 and 1 (u and v projection; row 2 used by tonemap)
uniform float u_r0, u_r1, u_r2;
uniform float u_r3, u_r4, u_r5;
// Pre-computed view bounds
uniform float u_uMin, u_uRange;
uniform float u_vMin, u_vRange;
uniform int   u_w, u_h;
uniform int   u_warmup;
uniform int   u_iters;

// ── Per-invocation xorshift RNG ──────────────────────────────────────────────
uint g_rng;
void seed_rng(uint s) { g_rng = s * 1664525u + 1013904223u; if (g_rng == 0u) g_rng = 1u; }
uint xors() { g_rng ^= g_rng<<13u; g_rng ^= g_rng>>17u; g_rng ^= g_rng<<5u; return g_rng; }
float randf() { return float(xors()>>8u) * (1.0/16777216.0); }

// ── Attractor equations ───────────────────────────────────────────────────────
// Each case must use only old x/y/z values. Temporaries (nx/ny/nz or dx/dy/dz)
// are computed first, then assigned, to match the C++ array-of-trajectories layout.
void step_attractor(inout float x, inout float y, inout float z) {
    float p0=u_p[0], p1=u_p[1], p2=u_p[2], p3=u_p[3];
    float p4=u_p[4], p5=u_p[5], p6=u_p[6];
    switch (u_type) {

    case 0: { // Clifford 3-D
        float nx=sin(p0*y)+p2*cos(p0*x);
        float ny=sin(p1*x)+p3*cos(p1*y);
        float nz=sin(p4*y)+p5*cos(p4*z);
        x=nx; y=ny; z=nz; break; }

    case 1: { // Peter de Jong 3-D
        float nx=sin(p0*y)-cos(p1*x);
        float ny=sin(p2*x)-cos(p3*y);
        float nz=sin(p4*z)-cos(p5*y);
        x=nx; y=ny; z=nz; break; }

    case 2: { // Gumowski-Mira
        float fx  = p1*x + 2.0*(1.0-p1)*x*x/(1.0+x*x);
        float xn  = y + p0*(1.0-0.05*y*y)*y + fx;
        float fxn = p1*xn + 2.0*(1.0-p1)*xn*xn/(1.0+xn*xn);
        z=0.0; y=-x+fxn; x=xn; break; }

    case 3: { // Lorenz (Euler)
        float dx=p0*(y-x), dy=x*(p1-z)-y, dz=x*y-p2*z;
        x+=p3*dx; y+=p3*dy; z+=p3*dz; break; }

    case 4: { // Rössler (Euler) — z updated before x/y assignment intentionally
        float nx=x+p3*(-(y+z));
        float ny=y+p3*(x+p0*y);
        z=z+p3*(p1+z*(x-p2));   // uses old x, old z
        x=nx; y=ny; break; }

    case 5: { // Aizawa (Euler)
        float dx=(z-p1)*x-p3*y;
        float dy=p3*x+(z-p1)*y;
        float dz=p2+p0*z-z*z*z/3.0-(x*x+y*y)*(1.0+p4*z)+p5*z*x*x*x;
        x+=p6*dx; y+=p6*dy; z+=p6*dz; break; }

    case 6: { // Thomas (Euler) — needs temps to avoid using updated x in z eq.
        float nx=x+p1*(sin(y)-p0*x);
        float ny=y+p1*(sin(z)-p0*y);
        float nz=z+p1*(sin(x)-p0*z);  // uses old x
        x=nx; y=ny; z=nz; break; }

    case 7: { // Chaotic Flow / Dadras (Euler)
        float dx=y-p0*x+p1*y*z;
        float dy=p2*y-x*z+z;
        float dz=p3*x*y-p4*z;
        x+=p5*dx; y+=p5*dy; z+=p5*dz; break; }

    case 8: { // Icon (Symmetry Icons, p=3)
        float r2=x*x+y*y, rez3=x*x*x-3.0*x*y*y;
        float t=p0+p1*r2+p2*rez3;
        float nx=t*x+p3*(x*x-y*y);
        y=t*y-p3*(2.0*x*y); x=nx; z=0.0; break; }

    case 9: { // IFS Barnsley Fern (3-D)
        float ct=cos(p2), st=sin(p2);
        float r=randf(), ox=x, oy=y, oz=z;
        if (r<0.01) { x=0.0; y=0.16*oy; z=0.16*oz; }
        else if (r<0.86) {
            float cy=0.85*oy, cz=0.85*oz;
            x=(0.85*p0)*ox+(0.04+p1)*oy;
            y=-(0.04+p1)*ox+(ct*cy-st*cz)+1.6;
            z=st*cy+ct*cz; }
        else if (r<0.93) { x=0.20*ox-0.26*oy; y=0.23*ox+0.22*oy+1.6; z=0.30*oz; }
        else              { x=-0.15*ox+0.28*oy; y=0.26*ox+0.24*oy+0.44; z=0.30*oz; }
        break; }

    case 10: { // Julia quaternion inverse iteration (3-D)
        float tw=x-p0, tx=y-p1, ty=z-p2;
        float vm=sqrt(tx*tx+ty*ty);
        float mag=sqrt(tw*tw+tx*tx+ty*ty);
        float r=sqrt(mag), theta=0.5*atan(vm, tw);
        if ((xors()&1u)!=0u) theta+=3.14159265;
        float sw=r*cos(theta), svm=r*sin(theta);
        x=sw;
        if (vm>1e-6) { float k=svm/vm; y=k*tx; z=k*ty; }
        else { y=svm; z=0.0; }
        break; }

    case 11: { // Pickover — nz must use old x, not the newly assigned value
        float nx=sin(p0*y)-z*cos(p1*x);
        float ny=z*sin(p2*x)-cos(p3*y);
        float nz=sin(x);               // old x
        x=nx; y=ny; z=nz; break; }

    case 12: { // Halvorsen (Euler)
        float dx=-p0*x-4.0*y-4.0*z-y*y;
        float dy=-p0*y-4.0*z-4.0*x-z*z;
        float dz=-p0*z-4.0*x-4.0*y-x*x;
        x+=p1*dx; y+=p1*dy; z+=p1*dz; break; }

    case 13: { // Burke-Shaw (Euler)
        float dx=-p0*(x+y), dy=-y-p0*x*z, dz=p0*x*y+p1;
        x+=p2*dx; y+=p2*dy; z+=p2*dz; break; }

    default: { // Sprott-B (14, Euler)
        float dx=p0*y*z, dy=x-y, dz=1.0-p1*x*y;
        x+=p2*dx; y+=p2*dy; z+=p2*dz; break; }
    }
}

void main() {
    seed_rng(gl_GlobalInvocationID.x ^ 0xDEADBEEFu);
    float x=(randf()-0.5)*0.1, y=(randf()-0.5)*0.1, z=(randf()-0.5)*0.1;

    for (int i=0; i<u_warmup; i++) step_attractor(x,y,z);

    float scaleX = (u_uRange>0.0) ? float(u_w-1)/u_uRange : 0.0;
    float scaleY = (u_vRange>0.0) ? float(u_h-1)/u_vRange : 0.0;

    for (int i=0; i<u_iters; i++) {
        step_attractor(x,y,z);
        float u = u_r0*x + u_r1*y + u_r2*z;
        float v = u_r3*x + u_r4*y + u_r5*z;
        int px = int((u - u_uMin)*scaleX);
        int py = int((v - u_vMin)*scaleY);
        if (px>=0 && px<u_w && py>=0 && py<u_h)
            imageAtomicAdd(u_hist, ivec2(px,py), 1u);
    }
}
)GLSL";

// ─────────────────────────────────────────────────────────────────────────────
// Shader compilation
// ─────────────────────────────────────────────────────────────────────────────

static GLuint compileComputeProgram(const char* src) {
    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        LOGE("Shader compile error:\n%s", log);
        glDeleteShader(shader);
        return 0;
    }
    GLuint prog = glCreateProgram();
    glAttachShader(prog, shader);
    glLinkProgram(prog);
    glDeleteShader(shader);
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetProgramInfoLog(prog, sizeof(log), nullptr, log);
        LOGE("Program link error:\n%s", log);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tone-map shader
// Reads the histogram (r32ui image), applies log scale / render style / gamma,
// samples the palette LUT, and writes RGBA8 pixels.
//
// GPU path handles render styles: STANDARD(0), GAS(1), PLASMA(3), SOLID(4).
// LIQUID(2), LIGHT(5), depth cue, and full-range all fall back to CPU.
// ─────────────────────────────────────────────────────────────────────────────

static const char* TONEMAP_SHADER_SRC = R"GLSL(
#version 310 es
precision highp float;
precision highp int;

layout(local_size_x = 16, local_size_y = 16) in;

layout(r32ui, binding = 0) uniform readonly uimage2D u_hist;
layout(rgba8,  binding = 1) uniform writeonly image2D  u_out;

uniform sampler2D u_palette;   // 1024×1 RGBA8 LUT

uniform int   u_w, u_h;
uniform uint  u_maxCount;
uniform float u_gamma;
uniform int   u_style;         // 0=STANDARD, 1=GAS, 3=PLASMA, 4=SOLID
uniform int   u_bgColor;       // ARGB_8888
uniform int   u_transparentBg;

vec4 samplePalette(float t) {
    int idx = clamp(int(t * 1023.0 + 0.5), 0, 1023);
    return texelFetch(u_palette, ivec2(idx, 0), 0);
}

void main() {
    ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
    if (coord.x >= u_w || coord.y >= u_h) return;

    uint count = imageLoad(u_hist, coord).r;

    if (count == 0u) {
        vec4 bg;
        if (u_transparentBg != 0) {
            bg = vec4(0.0);
        } else {
            uint argb = uint(u_bgColor);
            bg = vec4(
                float((argb >> 16u) & 0xFFu) / 255.0,
                float((argb >>  8u) & 0xFFu) / 255.0,
                float( argb         & 0xFFu) / 255.0,
                1.0
            );
        }
        imageStore(u_out, coord, bg);
        return;
    }

    float density;
    float logMax = log(1.0 + float(u_maxCount));

    if (u_style == 1) {
        // GAS: 4th-root density, no gamma
        density = pow(float(count) / float(u_maxCount), 0.25);
    } else {
        density = log(1.0 + float(count)) / logMax;
        if (u_style == 3) {
            // PLASMA: cyclic colour bands
            density = mod(density * 4.0, 1.0);
        } else if (u_style == 4) {
            // SOLID: binary threshold
            density = (density > 0.15) ? 1.0 : 0.0;
        } else {
            // STANDARD (0): log + gamma
            if (u_gamma != 1.0) density = pow(max(density, 0.0), u_gamma);
        }
    }

    density = clamp(density, 0.0, 1.0);
    imageStore(u_out, coord, vec4(samplePalette(density).rgb, 1.0));
}
)GLSL";

static bool buildPrograms(GpuContext& ctx) {
    if (ctx.compileFailed) return false; // don't retry after a known failure
    if (ctx.iterProgram && ctx.clearProgram && ctx.tonemapProgram) return true;

    ctx.clearProgram = compileComputeProgram(CLEAR_SHADER_SRC);
    if (!ctx.clearProgram) {
        LOGE("Clear shader failed — marking GPU as unavailable");
        ctx.compileFailed = true; return false;
    }

    ctx.iterProgram = compileComputeProgram(ITER_SHADER_SRC);
    if (!ctx.iterProgram) {
        LOGE("Iter shader failed — marking GPU as unavailable");
        glDeleteProgram(ctx.clearProgram); ctx.clearProgram = 0;
        ctx.compileFailed = true; return false;
    }

    ctx.tonemapProgram = compileComputeProgram(TONEMAP_SHADER_SRC);
    if (!ctx.tonemapProgram) {
        LOGE("Tonemap shader failed — marking GPU as unavailable");
        glDeleteProgram(ctx.iterProgram);  ctx.iterProgram  = 0;
        glDeleteProgram(ctx.clearProgram); ctx.clearProgram = 0;
        ctx.compileFailed = true; return false;
    }

    // Iter uniform locations
    auto& L = ctx.iterLoc;
    L.type   = glGetUniformLocation(ctx.iterProgram, "u_type");
    L.params = glGetUniformLocation(ctx.iterProgram, "u_p");
    L.r0     = glGetUniformLocation(ctx.iterProgram, "u_r0");
    L.r1     = glGetUniformLocation(ctx.iterProgram, "u_r1");
    L.r2     = glGetUniformLocation(ctx.iterProgram, "u_r2");
    L.r3     = glGetUniformLocation(ctx.iterProgram, "u_r3");
    L.r4     = glGetUniformLocation(ctx.iterProgram, "u_r4");
    L.r5     = glGetUniformLocation(ctx.iterProgram, "u_r5");
    L.uMin   = glGetUniformLocation(ctx.iterProgram, "u_uMin");
    L.uRange = glGetUniformLocation(ctx.iterProgram, "u_uRange");
    L.vMin   = glGetUniformLocation(ctx.iterProgram, "u_vMin");
    L.vRange = glGetUniformLocation(ctx.iterProgram, "u_vRange");
    L.w      = glGetUniformLocation(ctx.iterProgram, "u_w");
    L.h      = glGetUniformLocation(ctx.iterProgram, "u_h");
    L.warmup = glGetUniformLocation(ctx.iterProgram, "u_warmup");
    L.iters  = glGetUniformLocation(ctx.iterProgram, "u_iters");

    // Tonemap uniform locations
    auto& T = ctx.tonemapLoc;
    T.w           = glGetUniformLocation(ctx.tonemapProgram, "u_w");
    T.h           = glGetUniformLocation(ctx.tonemapProgram, "u_h");
    T.maxCount    = glGetUniformLocation(ctx.tonemapProgram, "u_maxCount");
    T.gamma       = glGetUniformLocation(ctx.tonemapProgram, "u_gamma");
    T.style       = glGetUniformLocation(ctx.tonemapProgram, "u_style");
    T.bgColor     = glGetUniformLocation(ctx.tonemapProgram, "u_bgColor");
    T.transparentBg = glGetUniformLocation(ctx.tonemapProgram, "u_transparentBg");
    T.palette     = glGetUniformLocation(ctx.tonemapProgram, "u_palette");

    LOGI("All shaders compiled and linked");
    return true;
}

// ─────────────────────────────────────────────────────────────────────────────
// Histogram texture
// ─────────────────────────────────────────────────────────────────────────────

static bool ensureHistogram(GpuContext& ctx, int w, int h) {
    if (ctx.histogramTex && ctx.texWidth == w && ctx.texHeight == h) return true;
    if (ctx.histogramTex) glDeleteTextures(1, &ctx.histogramTex);
    glGenTextures(1, &ctx.histogramTex);
    glBindTexture(GL_TEXTURE_2D, ctx.histogramTex);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_R32UI, w, h);
    glBindTexture(GL_TEXTURE_2D, 0);
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        LOGE("Histogram texture error: 0x%x (size %dx%d)", err, w, h);
        ctx.histogramTex = 0;
        return false;
    }
    ctx.texWidth  = w;
    ctx.texHeight = h;
    return true;
}

// ─────────────────────────────────────────────────────────────────────────────
// CPU-side bounds detection (fast: 2048 pts × (1000 warmup + 16 bounds batches))
// Avoids doing a GPU min/max reduction; ~3 ms on ARM.
// ─────────────────────────────────────────────────────────────────────────────

static void buildRotMatrix(float yawDeg, float pitchDeg, float rollDeg, float R[9]) {
    const float pi = 3.14159265f;
    float yaw   = yawDeg   * (pi / 180.f);
    float pitch = pitchDeg * (pi / 180.f);
    float roll  = rollDeg  * (pi / 180.f);
    float cy=cosf(yaw),   sy=sinf(yaw);
    float cp=cosf(pitch), sp=sinf(pitch);
    float cr=cosf(roll),  sr=sinf(roll);
    // ZYX convention: R = Rz * Ry * Rx  (matches renderer.cpp)
    R[0]=cy*cp;        R[1]=cy*sp*sr-sy*cr; R[2]=cy*sp*cr+sy*sr;
    R[3]=sy*cp;        R[4]=sy*sp*sr+cy*cr; R[5]=sy*sp*cr-cy*sr;
    R[6]=-sp;          R[7]=cp*sr;          R[8]=cp*cr;
}

static bool cpuBoundsDetect(const RenderParams& rp, const float R[9],
                             float& uMin, float& uMax,
                             float& vMin, float& vMax) {
    static constexpr int N       = 2048;
    static constexpr int WARMUP  = 1000;
    static constexpr int BATCHES = 16;

    std::vector<float> xs(N), ys(N), zs(N);
    uint32_t seed = 0xDEADBEEFu;
    auto lcg = [&]() -> float {
        seed = seed * 1664525u + 1013904223u;
        return ((float)(seed >> 8) / (float)(1u << 24)) - 0.5f;
    };
    for (int i = 0; i < N; i++) {
        xs[i] = lcg() * 0.1f;
        ys[i] = lcg() * 0.1f;
        zs[i] = lcg() * 0.1f;
    }
    for (int w = 0; w < WARMUP; w++)
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), N);

    uMin=FLT_MAX; uMax=-FLT_MAX; vMin=FLT_MAX; vMax=-FLT_MAX;
    for (int b = 0; b < BATCHES; b++) {
        attractorIterateN(rp.attractorType, rp.params,
                          xs.data(), ys.data(), zs.data(), N);
        for (int i = 0; i < N; i++) {
            float u = R[0]*xs[i] + R[1]*ys[i] + R[2]*zs[i];
            float v = R[3]*xs[i] + R[4]*ys[i] + R[5]*zs[i];
            if (u < uMin) uMin=u; if (u > uMax) uMax=u;
            if (v < vMin) vMin=v; if (v > vMax) vMax=v;
        }
    }

    if (uMax - uMin < 1e-6f || vMax - vMin < 1e-6f) return false; // orbit diverged

    float extraPad = (rp.boundsExtraPad > 0.f) ? rp.boundsExtraPad : 0.f;
    float padU = (uMax - uMin) * (0.05f + extraPad) + 1e-6f;
    float padV = (vMax - vMin) * (0.05f + extraPad) + 1e-6f;
    uMin -= padU; uMax += padU; vMin -= padV; vMax += padV;

    float zoom = (rp.zoom > 0.f) ? rp.zoom : 1.f;
    float cu = (uMin+uMax)*0.5f, hu = (uMax-uMin)*0.5f / zoom;
    float cv = (vMin+vMax)*0.5f, hv = (vMax-vMin)*0.5f / zoom;
    uMin=cu-hu; uMax=cu+hu; vMin=cv-hv; vMax=cv+hv;
    return true;
}

// Reads the histogram texture into a CPU buffer via a temporary FBO and returns
// the maximum bin value. Returns 0 if the histogram is empty (orbit diverged).
// Overhead: ~2 ms for 768 px, ~12 ms for 2048 px — acceptable relative to the
// GPU iteration time.
static uint32_t readHistogramMax(GpuContext& ctx) {
    const int n = ctx.texWidth * ctx.texHeight;
    std::vector<uint32_t> buf(static_cast<size_t>(n), 0u);

    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, ctx.histogramTex, 0);
    glReadPixels(0, 0, ctx.texWidth, ctx.texHeight,
                 GL_RED_INTEGER, GL_UNSIGNED_INT, buf.data());
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &fbo);

    uint32_t mx = 0;
    for (int i = 0; i < n; i++) if (buf[i] > mx) mx = buf[i];
    return mx;
}

// ─────────────────────────────────────────────────────────────────────────────
// Iteration dispatch: clear histogram then run the attractor shader.
// Uses 65 536 threads (512 groups × 128) regardless of iteration count;
// iters-per-thread scales to hit the total budget.
// ─────────────────────────────────────────────────────────────────────────────

static void dispatchIter(GpuContext& ctx, const RenderParams& rp,
                          const float R[9],
                          float uMin, float uMax, float vMin, float vMax) {
    static constexpr int LOCAL_SIZE    = 128;
    static constexpr int TARGET_THREADS = 65536;
    static constexpr int WARMUP_STEPS  = 1000;

    long long ipt = std::max(1LL, rp.iterations / (long long)TARGET_THREADS);
    int threads   = (int)((rp.iterations + ipt - 1) / ipt);
    int groups    = (threads + LOCAL_SIZE - 1) / LOCAL_SIZE;

    // ── Clear histogram ───────────────────────────────────────────────────────
    glUseProgram(ctx.clearProgram);
    glBindImageTexture(0, ctx.histogramTex, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_R32UI);
    glDispatchCompute((ctx.texWidth+15)/16, (ctx.texHeight+15)/16, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

    // ── Iteration pass ────────────────────────────────────────────────────────
    glUseProgram(ctx.iterProgram);
    glBindImageTexture(0, ctx.histogramTex, 0, GL_FALSE, 0, GL_READ_WRITE, GL_R32UI);

    auto& L = ctx.iterLoc;
    glUniform1i (L.type,   rp.attractorType);
    glUniform1fv(L.params, 8, rp.params);
    glUniform1f (L.r0,  R[0]); glUniform1f(L.r1, R[1]); glUniform1f(L.r2, R[2]);
    glUniform1f (L.r3,  R[3]); glUniform1f(L.r4, R[4]); glUniform1f(L.r5, R[5]);
    glUniform1f (L.uMin,   uMin); glUniform1f(L.uRange, uMax - uMin);
    glUniform1f (L.vMin,   vMin); glUniform1f(L.vRange, vMax - vMin);
    glUniform1i (L.w,      rp.width);
    glUniform1i (L.h,      rp.height);
    glUniform1i (L.warmup, WARMUP_STEPS);
    glUniform1i (L.iters,  (int)ipt);

    glDispatchCompute(groups, 1, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

    LOGI("Iter: %d groups × %d threads × %lld iters ≈ %lld total",
         groups, LOCAL_SIZE, ipt, (long long)groups * LOCAL_SIZE * ipt);
}

static bool ensureOutputTex(GpuContext& ctx, int w, int h) {
    if (ctx.outputTex && ctx.texWidth == w && ctx.texHeight == h) return true;
    if (ctx.outputTex) glDeleteTextures(1, &ctx.outputTex);
    glGenTextures(1, &ctx.outputTex);
    glBindTexture(GL_TEXTURE_2D, ctx.outputTex);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, w, h);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glBindTexture(GL_TEXTURE_2D, 0);
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        LOGE("Output texture error: 0x%x (%dx%d)", err, w, h);
        ctx.outputTex = 0;
        return false;
    }
    return true;
}

static void uploadPaletteTex(GpuContext& ctx, const RenderParams& rp) {
    static constexpr int LUT_SIZE = 1024;
    // Reuse getPaletteLutARGB from renderer.h (returns ARGB_8888 ints)
    std::vector<int> argbLut(LUT_SIZE);
    getPaletteLutARGB(rp.paletteIndex, argbLut.data(), LUT_SIZE,
                      rp.numCustomStops > 0 ? rp.customStops : nullptr,
                      rp.numCustomStops);

    // Convert ARGB (Android order) → RGBA bytes (OpenGL order)
    std::vector<uint8_t> rgba(LUT_SIZE * 4);
    for (int i = 0; i < LUT_SIZE; i++) {
        uint32_t argb = static_cast<uint32_t>(argbLut[i]);
        rgba[i*4 + 0] = static_cast<uint8_t>((argb >> 16) & 0xFF); // R
        rgba[i*4 + 1] = static_cast<uint8_t>((argb >>  8) & 0xFF); // G
        rgba[i*4 + 2] = static_cast<uint8_t>( argb         & 0xFF); // B
        rgba[i*4 + 3] = 0xFF;                                         // A
    }

    if (!ctx.paletteTex) glGenTextures(1, &ctx.paletteTex);
    glBindTexture(GL_TEXTURE_2D, ctx.paletteTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, LUT_SIZE, 1, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
}

static void dispatchTonemap(GpuContext& ctx, const RenderParams& rp,
                             uint32_t maxCount) {
    glUseProgram(ctx.tonemapProgram);

    // Image bindings
    glBindImageTexture(0, ctx.histogramTex, 0, GL_FALSE, 0, GL_READ_ONLY,  GL_R32UI);
    glBindImageTexture(1, ctx.outputTex,    0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

    // Palette texture → unit 0
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, ctx.paletteTex);

    auto& L = ctx.tonemapLoc;
    glUniform1i (L.w,           rp.width);
    glUniform1i (L.h,           rp.height);
    glUniform1ui(L.maxCount,    maxCount);
    glUniform1f (L.gamma,       (rp.gamma > 0.f) ? rp.gamma : 1.f);
    glUniform1i (L.style,       rp.renderStyle);
    glUniform1i (L.bgColor,     rp.bgColor != 0 ? rp.bgColor
                                                 : static_cast<int>(0xFF000000u));
    glUniform1i (L.transparentBg, rp.transparentBg);
    glUniform1i (L.palette,     0); // texture unit 0

    int gx = (rp.width  + 15) / 16;
    int gy = (rp.height + 15) / 16;
    glDispatchCompute(gx, gy, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT |
                    GL_TEXTURE_FETCH_BARRIER_BIT      |
                    GL_FRAMEBUFFER_BARRIER_BIT);
}

// ─────────────────────────────────────────────────────────────────────────────
// EGL lifecycle
// ─────────────────────────────────────────────────────────────────────────────

bool gpuInit(GpuContext& ctx) {
    if (ctx.ready) return true;

    ctx.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (ctx.display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }

    EGLint major, minor;
    if (!eglInitialize(ctx.display, &major, &minor)) {
        LOGE("eglInitialize failed");
        return false;
    }

    const EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE,    EGL_PBUFFER_BIT,
        EGL_NONE,
    };
    EGLConfig config;
    EGLint    numConfigs;
    if (!eglChooseConfig(ctx.display, configAttribs, &config, 1, &numConfigs)
            || numConfigs == 0) {
        LOGE("eglChooseConfig failed");
        eglTerminate(ctx.display);
        ctx.display = EGL_NO_DISPLAY;
        return false;
    }

    const EGLint ctxAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    ctx.context = eglCreateContext(ctx.display, config, EGL_NO_CONTEXT, ctxAttribs);
    if (ctx.context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        eglTerminate(ctx.display);
        ctx.display = EGL_NO_DISPLAY;
        return false;
    }

    // 1×1 pbuffer — compute shaders don't render to the surface but EGL requires
    // a current surface before any GL calls are accepted.
    const EGLint pbufAttribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    ctx.surface = eglCreatePbufferSurface(ctx.display, config, pbufAttribs);
    if (ctx.surface == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface failed");
        eglDestroyContext(ctx.display, ctx.context);
        eglTerminate(ctx.display);
        ctx.context = EGL_NO_CONTEXT;
        ctx.display = EGL_NO_DISPLAY;
        return false;
    }

    // Do NOT call eglMakeCurrent here — gpuRenderAttractor binds the context
    // on the render thread (Dispatchers.Default) before issuing any GL calls.
    ctx.ready = true;
    LOGI("GPU context ready (EGL %d.%d)", major, minor);
    return true;
}

void gpuDestroy(GpuContext& ctx) {
    if (!ctx.ready) return;

    eglMakeCurrent(ctx.display, ctx.surface, ctx.surface, ctx.context);

    if (ctx.clearProgram)   { glDeleteProgram(ctx.clearProgram);       ctx.clearProgram   = 0; }
    if (ctx.iterProgram)    { glDeleteProgram(ctx.iterProgram);        ctx.iterProgram    = 0; }
    if (ctx.tonemapProgram) { glDeleteProgram(ctx.tonemapProgram);     ctx.tonemapProgram = 0; }
    if (ctx.histogramTex)   { glDeleteTextures(1, &ctx.histogramTex);  ctx.histogramTex   = 0; }
    if (ctx.outputTex)      { glDeleteTextures(1, &ctx.outputTex);     ctx.outputTex      = 0; }
    if (ctx.paletteTex)     { glDeleteTextures(1, &ctx.paletteTex);    ctx.paletteTex     = 0; }

    eglMakeCurrent(ctx.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(ctx.display, ctx.surface);
    eglDestroyContext(ctx.display, ctx.context);
    eglTerminate(ctx.display);

    ctx.surface       = EGL_NO_SURFACE;
    ctx.context       = EGL_NO_CONTEXT;
    ctx.display       = EGL_NO_DISPLAY;
    ctx.ready         = false;
    ctx.compileFailed = false;
    ctx.texWidth      = ctx.texHeight = 0;
    LOGI("GPU context destroyed");
}

// Reads the RGBA8 output texture back to CPU and writes Android ARGB_8888 ints.
static void readOutputPixels(GpuContext& ctx, int* outPixels) {
    const int n = ctx.texWidth * ctx.texHeight;
    std::vector<uint8_t> buf(static_cast<size_t>(n) * 4u);

    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, ctx.outputTex, 0);
    glReadPixels(0, 0, ctx.texWidth, ctx.texHeight,
                 GL_RGBA, GL_UNSIGNED_BYTE, buf.data());
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &fbo);

    // RGBA bytes → Android ARGB_8888 integer (alpha always 0xFF from shader)
    for (int i = 0; i < n; i++) {
        uint32_t r = buf[i*4 + 0];
        uint32_t g = buf[i*4 + 1];
        uint32_t b = buf[i*4 + 2];
        outPixels[i] = static_cast<int>((0xFFu << 24) | (r << 16) | (g << 8) | b);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full GPU render pipeline
// ─────────────────────────────────────────────────────────────────────────────

bool gpuRenderAttractor(GpuContext& ctx, const RenderParams& rp, int* outPixels) {
    if (!ctx.ready) return false;

    // Render styles / modes not yet handled by the GPU path → CPU fallback.
    const bool needsDepth = (rp.depthCue > 0.f) || (rp.renderStyle == 2 /*LIQUID*/);
    const bool isLight    = (rp.renderStyle == 5);
    if (needsDepth || isLight || rp.fullRange != 0) return false;

    // Bind the EGL context to the current thread (Dispatchers.Default).
    // gpuInit intentionally skips this so it can be called from any thread.
    if (!eglMakeCurrent(ctx.display, ctx.surface, ctx.surface, ctx.context)) {
        LOGE("eglMakeCurrent failed on render thread");
        return false;
    }

    if (!buildPrograms(ctx)) return false;
    if (!ensureHistogram(ctx, rp.width, rp.height)) return false;
    if (!ensureOutputTex(ctx, rp.width, rp.height)) return false;

    float R[9];
    buildRotMatrix(rp.yaw, rp.pitch, rp.roll, R);

    float uMin, uMax, vMin, vMax;
    if (!cpuBoundsDetect(rp, R, uMin, uMax, vMin, vMax)) return false;

    // ── Step 4: fill histogram ────────────────────────────────────────────────
    dispatchIter(ctx, rp, R, uMin, uMax, vMin, vMax);

    // ── Step 5a: max bin count via CPU histogram readback ─────────────────────
    uint32_t maxCount = readHistogramMax(ctx);
    if (maxCount == 0) return false; // orbit diverged, no visible points

    // ── Step 5b: tone-map histogram → RGBA8 output texture ───────────────────
    uploadPaletteTex(ctx, rp);
    dispatchTonemap(ctx, rp, maxCount);

    // ── Step 6: readback RGBA8 output → Android ARGB_8888 pixel array ────────
    readOutputPixels(ctx, outPixels);
    return true;
}
