cmake_minimum_required(VERSION 3.22.1)
project(aetherlauncher)

# Phase 4.2: native Android OpenGL ES/EGL and Vulkan surface bridge.
add_library(aetherlauncher SHARED
    native_launcher.cpp
    renderer_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)
find_library(vulkan-lib vulkan)

target_link_libraries(
    aetherlauncher
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    ${vulkan-lib}
    dl
)
