package com.example.aetherlauncher.renderer

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14

/** Small, dependency-free renderer capability probe used by launcher settings. */
object RendererManager {
    data class Availability(
        val openGl: Boolean,
        val vulkan: Boolean,
    )

    fun availability(context: Context): Availability {
        val openGl = runCatching {
            EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY) != EGL14.EGL_NO_DISPLAY
        }.getOrDefault(true)
        val vulkan = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        return Availability(openGl = openGl, vulkan = vulkan)
    }
}
