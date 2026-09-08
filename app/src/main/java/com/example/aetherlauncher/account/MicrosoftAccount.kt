package com.example.aetherlauncher.account

data class MicrosoftAccount(
    val minecraftUuid: String,
    val minecraftName: String,
    val minecraftAccessToken: String,
    val minecraftTokenExpiresAt: Long
)
