package com.example.aetherlauncher.minecraft

data class MinecraftVersion(
    val id: String,
    val type: String,
    val releaseTime: String,
    val url: String
)

data class MinecraftVersionManifest(
    val latestRelease: String?,
    val latestSnapshot: String?,
    val versions: List<MinecraftVersion>
)
