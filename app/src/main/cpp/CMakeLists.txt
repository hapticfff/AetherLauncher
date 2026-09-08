cmake_minimum_required(VERSION 3.22.1)
project(aetherlauncher)

# Phase 4.2: native Android OpenGL ES/EGL and Vulkan surface bridge.
# Vulkan is loaded dynamically in renderer_bridge.cpp because the app keeps
# minSdk 23 while the Android Vulkan API starts at API 24.
add_library(aetherlauncher SHARED
    native_launcher.cpp
    renderer_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(
    aetherlauncher
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl
)
