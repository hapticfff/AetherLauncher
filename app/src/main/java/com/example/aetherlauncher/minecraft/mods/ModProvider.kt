package com.example.aetherlauncher.minecraft.mods

interface ModProvider {
    suspend fun search(query: String, gameVersion: String, loader: String): Result<List<ModProject>>
    suspend fun getCompatibleFile(projectId: String, gameVersion: String, loader: String): Result<ModFile?>
}
