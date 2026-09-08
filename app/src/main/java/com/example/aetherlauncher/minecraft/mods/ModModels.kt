package com.example.aetherlauncher.minecraft.mods

enum class ModProviderType { MODRINTH, CURSEFORGE }

data class ModProject(
    val provider: ModProviderType,
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val projectUrl: String?,
    val downloads: Long
)

data class ModDependency(
    val projectId: String?,
    val versionId: String?,
    val type: String
)

data class ModFile(
    val provider: ModProviderType,
    val id: String,
    val projectId: String,
    val versionName: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val downloadUrl: String,
    val fileName: String,
    val sha1: String?,
    val sizeBytes: Long,
    val dependencies: List<ModDependency>
)
