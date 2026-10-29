package com.gonzaroman.acornix.data

import android.content.Context
import com.gonzaroman.acornix.BuildConfig
import com.gonzaroman.acornix.ai.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class GitHubException(val reason: Reason, message: String = reason.name) : IOException(message) {
    enum class Reason { DENIED, EXPIRED, SIGNED_OUT, FAILED }
}

/**
 * Signing in with GitHub to publish apps, with the device flow: Acornix shows a short code, the user
 * enters it at github.com/login/device, and GitHub hands Acornix a token for the "Acornix Gallery"
 * GitHub App. That app can only create gists and open issues in the gallery repository. Only its
 * public client id ships with Acornix; the token is kept encrypted with the Android Keystore.
 */
class GitHubAccount(
    context: Context,
    private val clientId: String = BuildConfig.GITHUB_CLIENT_ID,
    private val http: OkHttpClient = HttpClients.default,
) {
    private val prefs = context.getSharedPreferences("github", Context.MODE_PRIVATE)
    private val _login = MutableStateFlow(prefs.getString(KEY_LOGIN, null))
    /** The GitHub user name, or null when signed out. */
    val login: StateFlow<String?> = _login.asStateFlow()

    /** False until the GitHub App exists and its client id is built in. */
    val available: Boolean get() = clientId.isNotBlank()

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.let { KeyCrypto.decrypt(it) }?.takeIf { it.isNotEmpty() }

    data class DeviceCode(val userCode: String, val verificationUri: String, val deviceCode: String, val interval: Int, val expiresIn: Int)

    /** Step 1: a code for the user to enter on GitHub. */
    suspend fun start(): DeviceCode {
        val json = post("https://github.com/login/device/code", FormBody.Builder().add("client_id", clientId).build())
        return DeviceCode(
            userCode = json.str("user_code") ?: throw GitHubException(GitHubException.Reason.FAILED, json.toString()),
            verificationUri = json.str("verification_uri") ?: "https://github.com/login/device",
            deviceCode = json.str("device_code") ?: throw GitHubException(GitHubException.Reason.FAILED),
            interval = json.int("interval") ?: 5,
            expiresIn = json.int("expires_in") ?: 900,
        )
    }

    /** Step 2: waits until the user has entered the code, then saves the token. Returns the user name. */
    suspend fun await(code: DeviceCode): String {
        var interval = code.interval
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val json = post(
                "https://github.com/login/oauth/access_token",
                FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", code.deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build(),
            )
            json.str("access_token")?.let { token -> return save(token) }
            when (json.str("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = json.int("interval") ?: (interval + 5)
                "access_denied" -> throw GitHubException(GitHubException.Reason.DENIED)
                "expired_token", "token_expired" -> throw GitHubException(GitHubException.Reason.EXPIRED)
                else -> throw GitHubException(GitHubException.Reason.FAILED, json.toString())
            }
        }
        throw GitHubException(GitHubException.Reason.EXPIRED)
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).apply()
        _login.value = null
    }

    private suspend fun save(token: String): String {
        val user = withContext(Dispatchers.IO) {
            http.newCall(api(Request.Builder().url("https://api.github.com/user"), token).build()).execute().use { r ->
                if (!r.isSuccessful) throw GitHubException(GitHubException.Reason.FAILED, "HTTP ${r.code}")
                Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            }
        }
        val login = user.str("login") ?: throw GitHubException(GitHubException.Reason.FAILED)
        prefs.edit().putString(KEY_TOKEN, KeyCrypto.encrypt(token)).putString(KEY_LOGIN, login).apply()
        _login.value = login
        return login
    }

    private suspend fun post(url: String, body: FormBody): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse {
                throw GitHubException(GitHubException.Reason.FAILED, "HTTP ${r.code}")
            }
        }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_LOGIN = "login"

        fun api(builder: Request.Builder, token: String): Request.Builder = builder
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

        private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
    }
}
