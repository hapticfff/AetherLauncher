package com.example.aetherlauncher.minecraft.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CurseForgeProvider(private val apiKey: String) : ModProvider {
    companion object {
        private const val BASE = "https://api.curseforge.com/v1"
        private const val MINECRAFT_GAME_ID = 432
    }

    override suspend fun search(query: String, gameVersion: String, loader: String): Result<List<ModProject>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "CurseForge API key is not configured" }
            val params = buildString {
                append("gameId=$MINECRAFT_GAME_ID")
                append("&pageSize=30")
                append("&searchFilter=${URLEncoder.encode(query, "UTF-8")}")
                append("&gameVersion=${URLEncoder.encode(gameVersion, "UTF-8")}")
                loaderType(loader)?.let { append("&modLoaderType=$it") }
            }
            val root = getJson("$BASE/mods/search?$params")
            val data = root.optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    add(ModProject(ModProviderType.CURSEFORGE, item.getString("id"), item.optString("slug"), item.optString("name"), item.optString("summary"), item.optString("logo", "").takeIf { it.isNotBlank() }, item.optJSONObject("links")?.optString("websiteUrl"), item.optLong("downloadCount")))
                }
            }
        }
    }

    override suspend fun getCompatibleFile(projectId: String, gameVersion: String, loader: String): Result<ModFile?> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "CurseForge API key is not configured" }
            val params = "gameVersion=${URLEncoder.encode(gameVersion, "UTF-8")}" + (loaderType(loader)?.let { "&modLoaderType=$it" } ?: "") + "&pageSize=50"
            val data = getJson("$BASE/mods/$projectId/files?$params").optJSONArray("data") ?: JSONArray()
            for (i in 0 until data.length()) {
                val file = data.getJSONObject(i)
                if (!file.optBoolean("isAvailable", true)) continue
                val name = file.optString("fileName")
                if (!name.endsWith(".jar", true)) continue
                val hashes = file.optJSONArray("hashes") ?: JSONArray()
                var sha1: String? = null
                for (h in 0 until hashes.length()) {
                    val hash = hashes.getJSONObject(h)
                    if (hash.optInt("algo") == 1) { sha1 = hash.optString("value"); break }
                }
                return@runCatching ModFile(ModProviderType.CURSEFORGE, file.getString("id"), projectId, file.optString("displayName"), jsonStringList(file, "gameVersions"), listOf(loader.lowercase()), file.optString("downloadUrl"), name, sha1, file.optLong("fileLength"), emptyList())
            }
            null
        }
    }

    private fun loaderType(loader: String): Int? = when (loader.lowercase()) {
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> null
    }

    private fun getJson(url: String): JSONObject = JSONObject(open(url))

    private fun open(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("User-Agent", "hapticfff/AetherLauncher/0.4")
        }
        return try {
            if (connection.responseCode !in 200..299) error("CurseForge request failed: HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun jsonStringList(obj: JSONObject, key: String): List<String> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        return buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    }
}
