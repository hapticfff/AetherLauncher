package com.example.aetherlauncher.minecraft.mods

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModInstaller(private val context: Context) {
    suspend fun install(file: ModFile): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = File(context.filesDir, "instances/default/mods").apply { mkdirs() }
            val target = File(modsDir, file.fileName)
            val temp = File(modsDir, ".${file.fileName}.download")
            val connection = (URL(file.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("User-Agent", "AetherLauncher/0.3 Android")
            }
            try {
                if (connection.responseCode !in 200..299) error("Mod download failed: HTTP ${connection.responseCode}")
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            } finally { connection.disconnect() }

            file.sha1?.let { expected ->
                val actual = sha1(temp)
                if (!actual.equals(expected, ignoreCase = true)) { temp.delete(); error("SHA-1 verification failed") }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) error("Unable to finalize mod installation")
            target
        }
    }

    fun installedMods(): List<File> = File(context.filesDir, "instances/default/mods").listFiles { f -> f.extension.equals("jar", true) }?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun remove(file: File): Boolean = file.delete()

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) { val count = input.read(buffer); if (count <= 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
