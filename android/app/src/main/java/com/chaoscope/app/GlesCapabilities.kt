package com.chaoscope

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES31

/**
 * One-shot GPU capability check. The probe result is cached in SharedPreferences
 * so the EGL context and shader compile only happen once per install.
 */
object GlesCapabilities {

    private const val PREFS_NAME   = "gles_caps"
    private const val KEY_CHECKED  = "checked"
    private const val KEY_COMPUTE  = "supports_compute"

    fun supportsComputeShaders(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CHECKED, false)) {
            return prefs.getBoolean(KEY_COMPUTE, false)
        }
        val result = checkCompute(context)
        prefs.edit()
            .putBoolean(KEY_CHECKED, true)
            .putBoolean(KEY_COMPUTE, result)
            .apply()
        return result
    }

    private fun checkCompute(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.deviceConfigurationInfo.reqGlEsVersion < 0x00030001) return false
        return try { probeComputeShader() } catch (_: Exception) { false }
    }

    /**
     * Create a throw-away EGL context, compile a no-op compute shader, and tear
     * everything down. Returns true only if the shader compiles successfully.
     * Runtime: ~50–150 ms; called at most once per install.
     */
    private fun probeComputeShader(): Boolean {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false

        return try {
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,    EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs     = arrayOfNulls<EGLConfig>(1)
            val numConfigs  = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0,
                    configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) return false

            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE,
            )
            val ctx = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) return false

            val pbufAttribs = intArrayOf(
                EGL14.EGL_WIDTH,  1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE,
            )
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, ctx)
                return false
            }

            EGL14.eglMakeCurrent(display, surface, surface, ctx)

            val ok = try {
                compileProbeShader()
            } finally {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, ctx)
            }
            ok
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    private fun compileProbeShader(): Boolean {
        val src = """
            #version 310 es
            layout(local_size_x = 1) in;
            void main() {}
        """.trimIndent()
        val shader = GLES31.glCreateShader(GLES31.GL_COMPUTE_SHADER)
        return try {
            GLES31.glShaderSource(shader, src)
            GLES31.glCompileShader(shader)
            val status = IntArray(1)
            GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
            status[0] == GLES31.GL_TRUE
        } finally {
            GLES31.glDeleteShader(shader)
        }
    }
}
