package com.example.aetherlauncher.account

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LauncherAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("aether_accounts", Context.MODE_PRIVATE)

    fun accounts(): List<LauncherAccount> {
        val raw = prefs.getString("accounts", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    LauncherAccount(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        type = runCatching { AccountType.valueOf(item.optString("type")) }
                            .getOrDefault(AccountType.OFFLINE_DEMO)
                    )
                )
            }
        }
    }

    fun selected(): LauncherAccount? {
        val id = prefs.getString("selected", null) ?: return null
        return accounts().firstOrNull { it.id == id }
    }

    fun select(accountId: String) {
        if (accounts().any { it.id == accountId }) {
            prefs.edit().putString("selected", accountId).apply()
        }
    }

    fun addOfflineDemo(name: String): LauncherAccount {
        val safeName = name.trim().ifBlank { "AetherDemo" }.take(16)
        val account = LauncherAccount(
            id = "offline-demo-${safeName.lowercase()}-${System.currentTimeMillis()}",
            name = safeName,
            type = AccountType.OFFLINE_DEMO
        )
        save(accounts() + account)
        select(account.id)
        return account
    }

    private fun save(items: List<LauncherAccount>) {
        val array = JSONArray()
        items.forEach { account ->
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("name", account.name)
                    .put("type", account.type.name)
            )
        }
        prefs.edit().putString("accounts", array.toString()).apply()
    }
}
