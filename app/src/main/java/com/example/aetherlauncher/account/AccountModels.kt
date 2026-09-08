package com.example.aetherlauncher.account

enum class AccountType {
    MICROSOFT,
    OFFLINE_DEMO
}

data class LauncherAccount(
    val id: String,
    val name: String,
    val type: AccountType
)
