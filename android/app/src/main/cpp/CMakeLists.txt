cmake_minimum_required(VERSION 3.22.1)
project("chaoscope")

add_library(
    chaoscope
    SHARED
    attractors.cpp
    renderer.cpp
    chaoscope_jni.cpp
)

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
target_link_libraries(chaoscope ${log-lib})

# 16 KB page-size alignment (required for Android 15+ / Play Store Nov 2025)
target_link_options(chaoscope PRIVATE -Wl,-z,max-page-size=16384)
