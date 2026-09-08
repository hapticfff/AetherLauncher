package com.example.aetherlauncher.minecraft.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ModrinthProvider : ModProvider {
    companion object { private const val BASE = "https://api.modrinth.com/v2" }

    override suspend fun search(query: String, gameVersion: String, loader: String): Result<List<ModProject>> = withContext(Dispatchers.IO) {
        runCatching {
            val facets = JSONArray().apply {
                put(JSONArray().put("project_type:mod"))
                put(JSONArray().put("versions:$gameVersion"))
                if (loader != "Vanilla") put(JSONArray().put("categories:$loader"))
            }
            val index = if (query.isBlank()) "downloads" else "relevance"
            val url = "$BASE/search?query=${URLEncoder.encode(query, "UTF-8")}&facets=${URLEncoder.encode(facets.toString(), "UTF-8")}&limit=30&index=$index"
            val root = getJson(url)
            val hits = root.getJSONArray("hits")
            buildList {
                for (i in 0 until hits.length()) {
                    val item = hits.getJSONObject(i)
                    add(ModProject(ModProviderType.MODRINTH, item.getString("project_id"), item.optString("slug"), item.getString("title"), item.optString("description"), item.optString("icon_url").takeIf { it.isNotBlank() }, "https://modrinth.com/mod/${item.optString("slug")}", item.optLong("downloads")))
                }
            }
        }
    }

    override suspend fun getCompatibleFile(projectId: String, gameVersion: String, loader: String): Result<ModFile?> = withContext(Dispatchers.IO) {
        runCatching {
            val loaders = JSONArray().put(loader.lowercase())
            val games = JSONArray().put(gameVersion)
            val url = "$BASE/project/$projectId/version?loaders=${URLEncoder.encode(loaders.toString(), "UTF-8")}&game_versions=${URLEncoder.encode(games.toString(), "UTF-8")}&include_changelog=false"
            val versions = getJsonArray(url)
            for (i in 0 until versions.length()) {
                val version = versions.getJSONObject(i)
                val files = version.getJSONArray("files")
                for (j in 0 until files.length()) {
                    val file = files.getJSONObject(j)
                    if (file.optString("filename").endsWith(".jar", true)) {
                        val hashes = file.optJSONObject("hashes")
                        val deps = version.optJSONArray("dependencies") ?: JSONArray()
                        val dependencies = buildList {
                            for (d in 0 until deps.length()) {
                                val dep = deps.getJSONObject(d)
                                add(ModDependency(dep.optString("project_id").takeIf { it.isNotBlank() }, dep.optString("version_id").takeIf { it.isNotBlank() }, dep.optString("dependency_type", "optional")))
                            }
                        }
                        return@runCatching ModFile(ModProviderType.MODRINTH, version.getString("id"), projectId, version.optString("version_number"), jsonStringList(version, "game_versions"), jsonStringList(version, "loaders"), file.getString("url"), file.getString("filename"), hashes?.optString("sha1"), file.optLong("size"), dependencies)
                    }
                }
            }
            null
        }
    }

    private fun getJson(url: String): JSONObject = JSONObject(open(url))
    private fun getJsonArray(url: String): JSONArray = JSONArray(open(url))

    private fun open(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "hapticfff/AetherLauncher/0.4 (launcher)")
        }
        return try {
            if (connection.responseCode !in 200..299) error("Modrinth request failed: HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun jsonStringList(obj: JSONObject, key: String): List<String> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        return buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    }
}
