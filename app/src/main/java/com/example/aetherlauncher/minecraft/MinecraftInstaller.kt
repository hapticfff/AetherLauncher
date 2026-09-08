package com.example.aetherlauncher.minecraft

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class MinecraftInstaller(private val context: Context) {
    companion object {
        private const val ROOT_DIR = "minecraft"
        private const val INSTALL_COMPLETE_FILE = ".installation-complete"
        private const val VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        private const val MAX_PARALLEL_DOWNLOADS = 6
    }

    suspend fun install(
        version: MinecraftVersion,
        onProgress: (InstallationProgress) -> Unit = {}
    ): Result<MinecraftInstallation> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.filesDir, ROOT_DIR).apply { mkdirs() }
            val versionDir = File(root, "versions/${version.id}").apply { mkdirs() }
            val librariesDir = File(root, "libraries").apply { mkdirs() }
            val assetsDir = File(root, "assets").apply { mkdirs() }

            onProgress(InstallationProgress("Fetching version metadata", 0, 0, 0, 0))
            val metadata = getJson(version.url)
            val downloads = metadata.optJSONObject("downloads")
            val client = downloads?.optJSONObject("client")
                ?: error("Minecraft ${version.id} has no client download")
            val clientUrl = client.getString("url")
            val clientSha1 = client.optString("sha1").takeIf { it.isNotBlank() }
            val clientFile = File(versionDir, "${version.id}.jar")

            val libraries = collectLibraries(metadata)
            val assetIndex = metadata.optJSONObject("assetIndex")
            val assetObjects = if (assetIndex != null) {
                val indexId = assetIndex.getString("id")
                val indexUrl = assetIndex.getString("url")
                val indexFile = File(assetsDir, "indexes/$indexId.json")
                download(indexUrl, indexFile, assetIndex.optString("sha1").takeIf { it.isNotBlank() })
                parseAssetObjects(JSONObject(indexFile.readText()))
            } else emptyList()

            val totalFiles = 1 + libraries.size + assetObjects.size
            val completed = AtomicInteger(0)
            val downloadedBytes = AtomicLong(0L)
            val totalBytes = client.optLong("size", 0L) +
                libraries.sumOf { it.size } + assetObjects.sumOf { it.size }

            fun progress(stage: String) {
                onProgress(
                    InstallationProgress(
                        stage,
                        completed.get(),
                        totalFiles,
                        downloadedBytes.get(),
                        totalBytes
                    )
                )
            }

            progress("Downloading Minecraft ${version.id}")
            download(clientUrl, clientFile, clientSha1)
            downloadedBytes.addAndGet(client.optLong("size", clientFile.length()))
            completed.incrementAndGet()
            progress("Downloading libraries")

            val semaphore = Semaphore(MAX_PARALLEL_DOWNLOADS)
            coroutineScope {
                libraries.map { library ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val target = safeChild(librariesDir, library.path)
                            download(library.url, target, library.sha1)
                            downloadedBytes.addAndGet(if (library.size > 0) library.size else target.length())
                            completed.incrementAndGet()
                            progress("Downloading libraries")
                        }
                    }
                }.awaitAll()
            }

            progress("Downloading assets")
            coroutineScope {
                assetObjects.map { asset ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val prefix = asset.hash.take(2)
                            val target = safeChild(File(assetsDir, "objects"), "$prefix/${asset.hash}")
                            download(
                                "https://resources.download.minecraft.net/$prefix/${asset.hash}",
                                target,
                                asset.hash
                            )
                            downloadedBytes.addAndGet(if (asset.size > 0) asset.size else target.length())
                            completed.incrementAndGet()
                            progress("Downloading assets")
                        }
                    }
                }.awaitAll()
            }

            File(versionDir, INSTALL_COMPLETE_FILE).writeText("complete")
            progress("Installation complete")
            MinecraftInstallation(
                version = version.id,
                gameDirectory = root.absolutePath,
                clientJar = clientFile.absolutePath,
                installedFiles = completed.get(),
                downloadedBytes = downloadedBytes.get()
            )
        }
    }

    fun installationDirectory(): File = File(context.filesDir, ROOT_DIR)

    fun isInstalled(version: String): Boolean =
        File(installationDirectory(), "versions/$version/$version.jar").isFile &&
            File(installationDirectory(), "versions/$version/$INSTALL_COMPLETE_FILE").isFile

    private data class LibraryFile(
        val path: String,
        val url: String,
        val sha1: String?,
        val size: Long
    )

    private data class AssetObject(val hash: String, val size: Long)

    private fun collectLibraries(metadata: JSONObject): List<LibraryFile> {
        val result = mutableListOf<LibraryFile>()
        val libraries = metadata.optJSONArray("libraries") ?: return result
        for (i in 0 until libraries.length()) {
            val library = libraries.getJSONObject(i)
            if (!isAllowedByRules(library.optJSONArray("rules"))) continue
            val downloads = library.optJSONObject("downloads") ?: continue
            downloads.optJSONObject("artifact")?.let { artifact ->
                val url = artifact.optString("url")
                val path = artifact.optString("path")
                if (url.isNotBlank() && path.isNotBlank()) {
                    result += LibraryFile(
                        path,
                        url,
                        artifact.optString("sha1").takeIf { it.isNotBlank() },
                        artifact.optLong("size", 0L)
                    )
                }
            }
        }
        return result.distinctBy { it.path }
    }

    private fun parseAssetObjects(index: JSONObject): List<AssetObject> {
        val objects = index.optJSONObject("objects") ?: return emptyList()
        val result = ArrayList<AssetObject>(objects.length())
        val keys = objects.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = objects.getJSONObject(key)
            val hash = item.getString("hash")
            result += AssetObject(hash, item.optLong("size", 0L))
        }
        return result
    }

    private fun isAllowedByRules(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val action = rule.optString("action", "allow")
            val os = rule.optJSONObject("os")?.optString("name")
            val matches = os == null || os == "linux"
            if (matches) allowed = action == "allow"
        }
        return allowed
    }

    private fun getJson(url: String): JSONObject {
        val connection = open(url)
        return try {
            if (connection.responseCode !in 200..299) error("Metadata request failed: HTTP ${connection.responseCode}")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File, expectedSha1: String?) {
        target.parentFile?.mkdirs()
        if (target.isFile && expectedSha1 != null && sha1(target) == expectedSha1) return
        if (target.isFile && expectedSha1 == null && target.length() > 0) return

        val temp = File(target.parentFile, ".${target.name}.part")
        if (temp.exists()) temp.delete()
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) error("Download failed: HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (expectedSha1 != null && sha1(temp) != expectedSha1) {
                temp.delete()
                error("SHA-1 verification failed for ${target.name}")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) error("Unable to move downloaded file into place")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json, application/octet-stream")
            setRequestProperty("User-Agent", "AetherLauncher/0.2 Android")
        }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeChild(parent: File, relativePath: String): File {
        val root = parent.canonicalFile
        val child = File(root, relativePath).canonicalFile
        require(child.path == root.path || child.path.startsWith(root.path + File.separator)) {
            "Unsafe download path"
        }
        return child
    }
}
