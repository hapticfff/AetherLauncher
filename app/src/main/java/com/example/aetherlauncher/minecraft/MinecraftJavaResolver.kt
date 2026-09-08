package com.example.aetherlauncher.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MinecraftLaunchMetadata(
    val version: String,
    val mainClass: String,
    val javaMajorVersion: Int,
    val assetIndex: String?,
    val gameArguments: List<String>
)

class MinecraftJavaResolver {
    suspend fun resolve(version: MinecraftVersion): Result<MinecraftLaunchMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(version.url).openConnection() as HttpURLConnection).apply {
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
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val javaMajor = root.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8
                val mainClass = root.optString("mainClass").ifBlank { "net.minecraft.client.main.Main" }
                val assetIndex = root.optJSONObject("assetIndex")?.optString("id")?.takeIf { it.isNotBlank() }
                val arguments = root.optJSONObject("arguments")?.optJSONArray("game")
                val gameArgs = buildList {
                    if (arguments != null) {
                        for (i in 0 until arguments.length()) {
                            val item = arguments.get(i)
                            when (item) {
                                is String -> add(item)
                                is JSONObject -> {
                                    if (rulesAllow(item.optJSONArray("rules"))) {
                                        when (val value = item.opt("value")) {
                                            is String -> add(value)
                                            is org.json.JSONArray -> for (j in 0 until value.length()) add(value.getString(j))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                MinecraftLaunchMetadata(version.id, mainClass, javaMajor, assetIndex, gameArgs)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun rulesAllow(rules: org.json.JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val action = rule.optString("action", "allow")
            val features = rule.optJSONObject("features")
            val matches = features == null || features.keys().asSequence().all { key ->
                when (key) {
                    "is_demo_user", "has_custom_resolution" -> false
                    else -> true
                }
            }
            if (matches) allowed = action == "allow"
        }
        return allowed
    }
}
