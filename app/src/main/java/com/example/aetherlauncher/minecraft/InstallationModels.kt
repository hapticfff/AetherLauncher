package com.example.aetherlauncher.minecraft

data class InstallationProgress(
    val stage: String,
    val completedFiles: Int,
    val totalFiles: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
)

data class MinecraftInstallation(
    val version: String,
    val gameDirectory: String,
    val clientJar: String,
    val installedFiles: Int,
    val downloadedBytes: Long
)
