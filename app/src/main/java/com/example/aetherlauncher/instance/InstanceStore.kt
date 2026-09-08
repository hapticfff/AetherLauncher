package com.example.aetherlauncher.instance

import android.content.Context
import java.util.UUID

data class LauncherInstance(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: String,
    val javaMajor: Int,
    val renderer: String,
    val maxRamMb: Int,
    val gameArgs: String = "",
    val jvmArgs: String = ""
)

class InstanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("aether_instances", Context.MODE_PRIVATE)
    private val key = "instances_v2"

    fun list(): List<LauncherInstance> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull(::decode).sortedBy { it.name.lowercase() }

    fun save(instance: LauncherInstance) {
        val all = list().filterNot { it.id == instance.id }.toMutableList().apply { add(instance) }
        prefs.edit().putStringSet(key, all.map(::encode).toSet()).apply()
    }

    fun delete(id: String) = prefs.edit().putStringSet(key, list().filterNot { it.id == id }.map(::encode).toSet()).apply()

    fun ensureDefault(version: String = "1.21.8"): LauncherInstance {
        list().firstOrNull()?.let { return it }
        return LauncherInstance(UUID.randomUUID().toString(), "Survival", version, "Vanilla", 21, "Auto", 2048).also(::save)
    }

    private fun encode(i: LauncherInstance) = listOf(i.id,i.name,i.minecraftVersion,i.loader,i.javaMajor,i.renderer,i.maxRamMb,i.gameArgs,i.jvmArgs).joinToString("\u001f")
    private fun decode(s: String): LauncherInstance? = runCatching {
        val p = s.split("\u001f")
        if (p.size < 9) return null
        LauncherInstance(p[0],p[1],p[2],p[3],p[4].toInt(),p[5],p[6].toInt(),p[7],p[8])
    }.getOrNull()
}
