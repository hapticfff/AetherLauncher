package com.example.aetherlauncher

import android.content.Context

/** Small, persistent launcher-side performance settings.
 * These affect launcher UI/process configuration only; they do not change Minecraft graphics settings.
 */
data class PerformanceSettings(
    val reducedAnimations: Boolean = true
)

class PerformanceSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("aether_performance", Context.MODE_PRIVATE)

    fun load(): PerformanceSettings = PerformanceSettings(
        reducedAnimations = prefs.getBoolean("reduced_animations", true)
    )

    fun setReducedAnimations(enabled: Boolean) {
        prefs.edit().putBoolean("reduced_animations", enabled).apply()
    }
}
