cmake_minimum_required(VERSION 3.22.1)
project("chaoscope")

# Portable render engine lives in the top-level core/ dir, shared with iOS.
# (android/app/src/main/cpp -> repo root is five levels up)
get_filename_component(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../core ABSOLUTE)

add_library(
    chaoscope
    SHARED
    ${CORE_DIR}/attractors.cpp
    ${CORE_DIR}/renderer.cpp
    gpu_renderer.cpp        # Android-only (EGL/GLESv3)
    chaoscope_jni.cpp       # Android-only (JNI bridge)
)

# Core headers (attractors.h / renderer.h) are included by the Android-only sources.
target_include_directories(chaoscope PRIVATE ${CORE_DIR})

# Fast-math + vectorisation, arch flag only where it applies
target_compile_options(chaoscope PRIVATE
    -O3
    -ffast-math
    -std=c++17
)

# ARM64 devices: enable NEON / AArch64 baseline
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(chaoscope PRIVATE -march=armv8-a)
endif()

# x86_64 emulator: use a safe modern baseline
if(ANDROID_ABI STREQUAL "x86_64")
    target_compile_options(chaoscope PRIVATE -march=x86-64-v2)
endif()

find_library(log-lib log)
find_library(egl-lib  EGL)
find_library(gles-lib GLESv3)
target_link_libraries(chaoscope ${log-lib} ${egl-lib} ${gles-lib})

# 16 KB page-size alignment (required for Android 15+ / Play Store Nov 2025)
target_link_options(chaoscope PRIVATE -Wl,-z,max-page-size=16384)
