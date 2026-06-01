#pragma once
#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include "renderer.h"

struct GpuContext {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    bool       ready   = false;

    // Set to true if shader compilation fails so we don't retry on every render.
    bool compileFailed = false;

    // Shader programs
    GLuint clearProgram   = 0;  // zero-fill the histogram texture
    GLuint iterProgram    = 0;  // attractor iteration → atomic histogram
    GLuint tonemapProgram = 0;  // histogram → RGBA pixels (Step 5)

    // Cached uniform locations for iterProgram
    struct {
        GLint type=-1, params=-1;
        GLint r0=-1, r1=-1, r2=-1, r3=-1, r4=-1, r5=-1;
        GLint uMin=-1, uRange=-1, vMin=-1, vRange=-1;
        GLint w=-1, h=-1, warmup=-1, iters=-1;
    } iterLoc;

    // Cached uniform locations for tonemapProgram
    struct {
        GLint w=-1, h=-1, maxCount=-1;
        GLint gamma=-1, style=-1;
        GLint bgColor=-1, transparentBg=-1;
        GLint palette=-1;
    } tonemapLoc;

    // GPU textures
    GLuint histogramTex = 0;   // GL_R32UI,  width × height
    GLuint outputTex    = 0;   // GL_RGBA8,  width × height (Step 5)
    GLuint paletteTex   = 0;   // GL_RGBA8,  1024 × 1       (Step 5)
    int    texWidth     = 0;
    int    texHeight    = 0;
};

// One-time EGL offscreen context creation. Returns true on success.
bool gpuInit(GpuContext& ctx);

// Release all EGL and GL resources.
void gpuDestroy(GpuContext& ctx);

// Full GPU render pipeline. outPixels must be pre-allocated width*height ints.
// Returns true if the render produced any visible pixels, false on failure or stub.
bool gpuRenderAttractor(GpuContext& ctx, const RenderParams& rp, int* outPixels);
