package com.example.aetherlauncher.account

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MicrosoftAuthRepository(context: Context) {
    companion object {
        private const val CLIENT_ID = "00000000402b5328"
        private const val DEVICE_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val TOKEN_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        private const val XBL_ENDPOINT = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_ENDPOINT = "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MINECRAFT_LOGIN_ENDPOINT = "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile"
        private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL offline_access"
    }

    private val store = SecureTokenStore(context)

    data class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        val message: String,
        val deviceCode: String,
        val intervalSeconds: Int,
        val expiresAtMillis: Long
    )

    suspend fun beginDeviceLogin(): Result<DeviceCode> = withContext(Dispatchers.IO) {
        runCatching {
            val json = postForm(
                DEVICE_ENDPOINT,
                mapOf("client_id" to CLIENT_ID, "scope" to SCOPE)
            )
            val expires = json.optLong("expires_in", 900L)
            DeviceCode(
                userCode = json.getString("user_code"),
                verificationUri = json.optString("verification_uri", "https://microsoft.com/devicelogin"),
                message = json.optString("message", "Open the verification page and enter the code."),
                deviceCode = json.getString("device_code"),
                intervalSeconds = json.optInt("interval", 5),
                expiresAtMillis = System.currentTimeMillis() + expires * 1000L
            )
        }
    }

    suspend fun completeDeviceLogin(deviceCode: DeviceCode): Result<MicrosoftAccount> = withContext(Dispatchers.IO) {
        runCatching {
            var interval = deviceCode.intervalSeconds.coerceAtLeast(5)
            while (System.currentTimeMillis() < deviceCode.expiresAtMillis) {
                val response = postFormRaw(
                    TOKEN_ENDPOINT,
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id" to CLIENT_ID,
                        "device_code" to deviceCode.deviceCode
                    )
                )
                if (response.optString("access_token").isNotBlank()) {
                    val refreshToken = response.optString("refresh_token")
                    if (refreshToken.isNotBlank()) store.put("refresh_token", refreshToken)
                    return@runCatching finishMinecraftLogin(response.getString("access_token"))
                }
                when (response.optString("error")) {
                    "authorization_pending" -> delay(interval * 1000L)
                    "slow_down" -> { interval += 5; delay(interval * 1000L) }
                    "authorization_declined" -> error("Microsoft sign-in was declined")
                    "expired_token" -> error("Microsoft sign-in code expired")
                    else -> error(response.optString("error_description", "Microsoft sign-in failed"))
                }
            }
            error("Microsoft sign-in timed out")
        }
    }

    suspend fun restoreSession(): Result<MicrosoftAccount?> = withContext(Dispatchers.IO) {
        runCatching {
            val refreshToken = store.get("refresh_token") ?: return@runCatching null
            val response = postForm(
                TOKEN_ENDPOINT,
                mapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to CLIENT_ID,
                    "refresh_token" to refreshToken,
                    "scope" to SCOPE
                )
            )
            response.optString("refresh_token").takeIf { it.isNotBlank() }?.let { store.put("refresh_token", it) }
            finishMinecraftLogin(response.getString("access_token"))
        }
    }

    fun currentAccount(): MicrosoftAccount? {
        val uuid = store.get("minecraft_uuid") ?: return null
        val name = store.get("minecraft_name") ?: return null
        val token = store.get("minecraft_access_token") ?: return null
        val expires = store.get("minecraft_expires_at")?.toLongOrNull() ?: 0L
        return MicrosoftAccount(uuid, name, token, expires)
    }

    fun signOut() = store.clear()

    private fun finishMinecraftLogin(microsoftAccessToken: String): MicrosoftAccount {
        val xbl = postJson(XBL_ENDPOINT, JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", if (microsoftAccessToken.startsWith("d=")) microsoftAccessToken else "d=$microsoftAccessToken")
            })
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        })
        val xblToken = xbl.getString("Token")
        val uhs = xbl.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")

        val xsts = postJson(XSTS_ENDPOINT, JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("SandboxId", "RETAIL")
                put("UserTokens", org.json.JSONArray().put(xblToken))
            })
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        })
        val xstsToken = xsts.getString("Token")
        val minecraft = postJson(MINECRAFT_LOGIN_ENDPOINT, JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken"))
        val minecraftToken = minecraft.getString("access_token")
        val expiresAt = System.currentTimeMillis() + minecraft.optLong("expires_in", 86400L) * 1000L
        val profile = getJson(PROFILE_ENDPOINT, "Bearer $minecraftToken")
        val account = MicrosoftAccount(profile.getString("id"), profile.getString("name"), minecraftToken, expiresAt)
        store.put("minecraft_uuid", account.minecraftUuid)
        store.put("minecraft_name", account.minecraftName)
        store.put("minecraft_access_token", account.minecraftAccessToken)
        store.put("minecraft_expires_at", account.minecraftTokenExpiresAt.toString())
        return account
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val response = postFormRaw(url, fields)
        if (response.has("error")) error(response.optString("error_description", response.optString("error")))
        return response
    }

    private fun postFormRaw(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        return request(url, "application/x-www-form-urlencoded", body.toByteArray())
    }

    private fun postJson(url: String, json: JSONObject): JSONObject = request(url, "application/json", json.toString().toByteArray())

    private fun getJson(url: String, authorization: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", authorization)
        }
        return readResponse(connection)
    }

    private fun request(url: String, contentType: String, body: ByteArray): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AetherLauncher/0.2 Android")
        }
        connection.outputStream.use { it.write(body) }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): JSONObject {
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(text)
            if (connection.responseCode !in 200..299 && !json.has("error")) error("Authentication request failed: HTTP ${connection.responseCode}")
            json
        } finally { connection.disconnect() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
