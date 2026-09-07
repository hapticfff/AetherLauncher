package com.example.aetherlauncher.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MinecraftRepository {
    companion object {
        private const val VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }

    suspend fun fetchVersionManifest(): Result<MinecraftVersionManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(VERSION_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AetherLauncher/0.2 Android")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    error("Minecraft metadata request failed: HTTP ${connection.responseCode}")
                }

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val latest = root.getJSONObject("latest")
                val versionsJson = root.getJSONArray("versions")

                val versions = buildList {
                    for (index in 0 until versionsJson.length()) {
                        val item = versionsJson.getJSONObject(index)
                        add(
                            MinecraftVersion(
                                id = item.getString("id"),
                                type = item.optString("type", "unknown"),
                                releaseTime = item.optString("releaseTime", ""),
                                url = item.getString("url")
                            )
                        )
                    }
                }

                MinecraftVersionManifest(
                    latestRelease = latest.optString("release").takeIf { it.isNotBlank() },
                    latestSnapshot = latest.optString("snapshot").takeIf { it.isNotBlank() },
                    versions = versions
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
