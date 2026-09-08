package com.example.aetherlauncher.runtime

data class JavaRuntime(
    val majorVersion: Int,
    val architecture: String,
    val directory: String,
    val javaExecutable: String,
    val installed: Boolean
)

data class JavaRuntimeProgress(
    val stage: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

object MinecraftJavaRequirements {
    fun requiredMajor(javaVersionMajor: Int?): Int = when {
        javaVersionMajor != null -> javaVersionMajor
        else -> 8
    }
}
