package com.example.aetherlauncher.runtime

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

class JavaRuntimeManager(private val context: Context) {
    private val root = File(context.filesDir, "runtimes")

    fun supportedArchitecture(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    fun runtimeDirectory(majorVersion: Int): File =
        File(root, "java$majorVersion/${supportedArchitecture()}")

    fun findInstalled(majorVersion: Int): JavaRuntime? {
        val directory = runtimeDirectory(majorVersion)
        val java = File(directory, "bin/java")
        if (!java.isFile) return null
        return JavaRuntime(majorVersion, supportedArchitecture(), directory.absolutePath, java.absolutePath, true)
    }

    suspend fun installFromZip(
        majorVersion: Int,
        archiveUrl: String,
        expectedSha256: String? = null,
        onProgress: (JavaRuntimeProgress) -> Unit = {}
    ): Result<JavaRuntime> = withContext(Dispatchers.IO) {
        runCatching {
            require(archiveUrl.startsWith("https://")) { "Runtime URL must use HTTPS" }
            val target = runtimeDirectory(majorVersion)
            target.parentFile?.mkdirs()
            val archive = File(target.parentFile, "java$majorVersion-${supportedArchitecture()}.zip.part")
            onProgress(JavaRuntimeProgress("Downloading Java $majorVersion"))
            download(archiveUrl, archive, expectedSha256) { downloaded, total ->
                onProgress(JavaRuntimeProgress("Downloading Java $majorVersion", downloaded, total))
            }

            val staging = File(target.parentFile, ".java$majorVersion-${supportedArchitecture()}-staging")
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()
            onProgress(JavaRuntimeProgress("Extracting Java $majorVersion"))
            extractZipSafely(archive, staging)
            archive.delete()

            val java = locateJavaExecutable(staging)
                ?: error("Java runtime archive does not contain bin/java")
            if (target.exists()) target.deleteRecursively()
            if (!staging.renameTo(target)) error("Unable to install Java runtime")
            val installedJava = locateJavaExecutable(target)
                ?: error("Installed Java runtime is missing bin/java")
            installedJava.setExecutable(true, false)
            onProgress(JavaRuntimeProgress("Java $majorVersion ready"))
            JavaRuntime(majorVersion, supportedArchitecture(), target.absolutePath, installedJava.absolutePath, true)
        }
    }

    fun installedRuntimes(): List<JavaRuntime> = root.listFiles().orEmpty()
        .flatMap { versionDir ->
            val major = versionDir.name.removePrefix("java").toIntOrNull() ?: return@flatMap emptyList()
            versionDir.listFiles().orEmpty().mapNotNull { archDir ->
                val java = File(archDir, "bin/java")
                if (java.isFile) JavaRuntime(major, archDir.name, archDir.absolutePath, java.absolutePath, true) else null
            }
        }
        .sortedBy { it.majorVersion }

    private fun locateJavaExecutable(root: File): File? {
        val direct = File(root, "bin/java")
        if (direct.isFile) return direct
        val candidates = root.walkTopDown().filter { it.isFile && it.name == "java" }
        return candidates.firstOrNull { it.parentFile?.name == "bin" }
    }

    private fun extractZipSafely(archive: File, destination: File) {
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val output = File(destination, entry.name).canonicalFile
                val base = destination.canonicalFile
                require(output.path == base.path || output.path.startsWith(base.path + File.separator)) {
                    "Unsafe runtime archive path"
                }
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    private fun download(
        url: String,
        target: File,
        expectedSha256: String?,
        progress: (Long, Long) -> Unit
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "AetherLauncher/0.2 Android")
        }
        try {
            if (connection.responseCode !in 200..299) error("Runtime download failed: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            var downloaded = 0L
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        progress(downloaded, total)
                    }
                }
            }
            if (expectedSha256 != null && sha256(target) != expectedSha256.lowercase()) {
                target.delete()
                error("Java runtime SHA-256 verification failed")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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
}
