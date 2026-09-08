package com.example.aetherlauncher.account

/** Local testing profile only. It does not provide Microsoft authentication or ownership. */
data class OfflineTestAccount(
    val username: String = "Offline account"
)
