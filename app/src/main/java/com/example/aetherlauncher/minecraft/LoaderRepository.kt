package com.example.aetherlauncher.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class LoaderRepository {
    suspend fun fetchProfiles(gameVersion: String): Result<List<LoaderProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val profiles = mutableListOf<LoaderProfile>()
            profiles += LoaderProfile("Vanilla", gameVersion, "Official", true, "Mojang")

            val fabric = jsonArray("https://meta.fabricmc.net/v2/versions/loader/${URLEncoder.encode(gameVersion, "UTF-8")}")
            for (i in 0 until fabric.length()) {
                val item = fabric.getJSONObject(i)
                val loader = item.getJSONObject("loader")
                profiles += LoaderProfile("Fabric", gameVersion, loader.getString("version"), loader.optBoolean("stable", false), "Fabric Loader")
            }

            val quilt = runCatching { jsonArray("https://meta.quiltmc.org/v3/versions/loader/${URLEncoder.encode(gameVersion, "UTF-8")}") }.getOrDefault(JSONArray())
            for (i in 0 until quilt.length()) {
                val item = quilt.getJSONObject(i)
                val loader = item.optJSONObject("loader") ?: item
                profiles += LoaderProfile("Quilt", gameVersion, loader.optString("version"), loader.optBoolean("stable", false), "Quilt Loader")
            }

            val forgeVersions = parseMavenVersions("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml")
            forgeVersions.filter { it.startsWith("$gameVersion-") }.forEach { version ->
                profiles += LoaderProfile("Forge", gameVersion, version.removePrefix("$gameVersion-"), !version.contains("alpha", true) && !version.contains("beta", true), "Minecraft Forge")
            }

            val neoVersions = parseMavenVersions("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml")
            neoVersions.filter { it.startsWith(gameVersion) || it.startsWith(gameVersion.replace(".", "_")) }.forEach { version ->
                profiles += LoaderProfile("NeoForge", gameVersion, version, !version.contains("alpha", true) && !version.contains("beta", true), "NeoForge")
            }

            profiles.distinctBy { "${it.loader}:${it.gameVersion}:${it.loaderVersion}" }
        }
    }

    private fun jsonArray(url: String): JSONArray = JSONArray(open(url))

    private fun parseMavenVersions(url: String): List<String> {
        val xml = open(url)
        val matcher = Pattern.compile("<version>([^<]+)</version>").matcher(xml)
        val result = mutableListOf<String>()
        while (matcher.find()) result += matcher.group(1)
        return result
    }

    private fun open(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json, application/xml, text/xml")
            setRequestProperty("User-Agent", "hapticfff/AetherLauncher/0.4")
        }
        return try {
            if (connection.responseCode !in 200..299) error("Loader metadata request failed: HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}

data class LoaderProfile(
    val loader: String,
    val gameVersion: String,
    val loaderVersion: String,
    val stable: Boolean,
    val source: String
)
