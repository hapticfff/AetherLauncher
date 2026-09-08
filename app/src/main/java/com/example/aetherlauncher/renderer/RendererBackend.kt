package com.example.aetherlauncher.renderer

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Graphics backend explicitly selected by the user or chosen automatically. */
enum class RendererBackend(val label: String) {
    AUTO("Auto"),
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    companion object {
        fun fromLabel(value: String?): RendererBackend = entries.firstOrNull { it.label == value } ?: AUTO
    }
}

/** Persists only the renderer preference; it does not change any other launcher settings. */
class RendererSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("aether_settings", Context.MODE_PRIVATE)

    fun get(): RendererBackend = RendererBackend.fromLabel(preferences.getString(KEY_RENDERER, null))

    fun set(renderer: RendererBackend) {
        preferences.edit().putString(KEY_RENDERER, renderer.label).apply()
    }

    companion object {
        private const val KEY_RENDERER = "renderer_backend"
    }
}

data class RendererAvailability(
    val openGl: Boolean,
    val vulkan: Boolean
)

/** Detects Android graphics API availability before Minecraft is launched. */
object RendererManager {
    fun availability(context: Context): RendererAvailability {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val glVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val openGl = glVersion >= 0x30000

        val vulkan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val packageManager = context.packageManager
            packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        } else {
            false
        }

        return RendererAvailability(openGl = openGl, vulkan = vulkan)
    }

    fun resolve(context: Context, requested: RendererBackend): RendererBackend {
        val available = availability(context)
        return when (requested) {
            RendererBackend.AUTO -> when {
                available.vulkan -> RendererBackend.VULKAN
                available.openGl -> RendererBackend.OPENGL
                else -> error("This device does not expose OpenGL ES 3.x or Vulkan")
            }
            RendererBackend.OPENGL -> {
                check(available.openGl) { "OpenGL ES 3.x is not available on this device" }
                RendererBackend.OPENGL
            }
            RendererBackend.VULKAN -> {
                check(available.vulkan) { "Vulkan is not available on this device" }
                RendererBackend.VULKAN
            }
        }
    }
}
