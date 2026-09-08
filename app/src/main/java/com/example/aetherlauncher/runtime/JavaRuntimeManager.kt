package com.example.aetherlauncher.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
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

    fun runtimeDirectory(majorVersion: Int): File = File(root, "java$majorVersion/${supportedArchitecture()}")

    fun findInstalled(majorVersion: Int): JavaRuntime? {
        val directory = runtimeDirectory(majorVersion)
        val java = File(directory, "bin/java")
        if (!java.isFile) return null
        return try {
            ensureExecutable(java)
            if (!java.canExecute()) return null
            JavaRuntime(majorVersion, supportedArchitecture(), directory.absolutePath, java.absolutePath, true)
        } catch (_: Exception) {
            null
        }
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
            installStaging(majorVersion, target, staging, onProgress)
        }
    }

    suspend fun installFromTarXz(
        majorVersion: Int,
        archiveUrl: String,
        expectedSha256: String? = null,
        onProgress: (JavaRuntimeProgress) -> Unit = {}
    ): Result<JavaRuntime> = withContext(Dispatchers.IO) {
        runCatching {
            require(archiveUrl.startsWith("https://")) { "Runtime URL must use HTTPS" }
            val target = runtimeDirectory(majorVersion)
            target.parentFile?.mkdirs()
            val archive = File(target.parentFile, "java$majorVersion-${supportedArchitecture()}.tar.xz.part")
            onProgress(JavaRuntimeProgress("Downloading Java $majorVersion"))
            download(archiveUrl, archive, expectedSha256) { downloaded, total ->
                onProgress(JavaRuntimeProgress("Downloading Java $majorVersion", downloaded, total))
            }
            val staging = File(target.parentFile, ".java$majorVersion-${supportedArchitecture()}-staging")
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()
            onProgress(JavaRuntimeProgress("Extracting Java $majorVersion"))
            extractTarXzSafely(archive, staging)
            archive.delete()
            installStaging(majorVersion, target, staging, onProgress)
        }
    }

    fun installedRuntimes(): List<JavaRuntime> = root.listFiles().orEmpty()
        .flatMap { versionDir ->
            val major = versionDir.name.removePrefix("java").toIntOrNull() ?: return@flatMap emptyList()
            versionDir.listFiles().orEmpty().mapNotNull { archDir ->
                val java = File(archDir, "bin/java")
                if (java.isFile) {
                    runCatching { ensureExecutable(java) }.getOrNull()
                    if (java.canExecute()) JavaRuntime(major, archDir.name, archDir.absolutePath, java.absolutePath, true) else null
                } else null
            }
        }
        .sortedBy { it.majorVersion }

    private fun installStaging(
        majorVersion: Int,
        target: File,
        staging: File,
        onProgress: (JavaRuntimeProgress) -> Unit
    ): JavaRuntime {
        locateJavaExecutable(staging) ?: error("Java runtime archive does not contain bin/java")
        if (target.exists()) target.deleteRecursively()
        if (!staging.renameTo(target)) error("Unable to install Java runtime")
        val installedJava = locateJavaExecutable(target) ?: error("Installed Java runtime is missing bin/java")
        ensureExecutable(installedJava)
        if (!installedJava.canExecute()) error("Java runtime executable permission could not be enabled")
        onProgress(JavaRuntimeProgress("Java $majorVersion ready"))
        return JavaRuntime(majorVersion, supportedArchitecture(), target.absolutePath, installedJava.absolutePath, true)
    }

    private fun ensureExecutable(file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
        Os.chmod(file.absolutePath, 0x1ED)
    }

    private fun locateJavaExecutable(root: File): File? {
        val direct = File(root, "bin/java")
        if (direct.isFile) return direct
        return root.walkTopDown().firstOrNull { it.isFile && it.name == "java" && it.parentFile?.name == "bin" }
    }

    private fun extractZipSafely(archive: File, destination: File) {
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val output = safeArchivePath(destination, entry.name)
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    private fun extractTarXzSafely(archive: File, destination: File) {
        FileInputStream(archive).use { fileInput ->
            XZCompressorInputStream(fileInput).use { xzInput ->
                TarArchiveInputStream(xzInput).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val output = safeArchivePath(destination, entry.name)
                        if (entry.isDirectory) output.mkdirs() else {
                            output.parentFile?.mkdirs()
                            output.outputStream().use { tar.copyTo(it) }
                        }
                    }
                }
            }
        }
    }

    private fun safeArchivePath(destination: File, entryName: String): File {
        val base = destination.canonicalFile
        val output = File(base, entryName).canonicalFile
        require(output.path == base.path || output.path.startsWith(base.path + File.separator)) { "Unsafe runtime archive path" }
        return output
    }

    private fun download(url: String, target: File, expectedSha256: String?, progress: (Long, Long) -> Unit) {
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