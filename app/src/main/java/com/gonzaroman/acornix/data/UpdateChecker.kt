package com.gonzaroman.acornix.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gonzaroman.acornix.BuildConfig
import com.gonzaroman.acornix.ai.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

/** A newer release published on GitHub. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/**
 * Checks GitHub Releases for a newer Acornix, downloads its APK and hands it to the Android
 * installer. Updates install over the current app because releases are signed with the same key.
 */
class UpdateChecker(
    private val context: Context,
    private val http: OkHttpClient = HttpClients.default,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    /**
     * Returns the newer release, or null when up to date. Automatic checks run at most every
     * [MIN_INTERVAL_MS] and stay quiet about a version the user dismissed; [force] skips both.
     */
    suspend fun check(force: Boolean = false): UpdateInfo? {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < MIN_INTERVAL_MS) return _available.value
        val info = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw IllegalStateException("GitHub answered ${res.code}")
                parseRelease(res.body?.string().orEmpty())
            }
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val newer = info?.takeIf { isNewer(it.version, currentVersion) }
        _available.value = newer?.takeIf { force || prefs.getString(KEY_DISMISSED, null) != it.version }
        return newer
    }

    /** "Later": hides this version until the next one comes out (Settings can still find it). */
    fun dismiss(info: UpdateInfo) {
        prefs.edit().putString(KEY_DISMISSED, info.version).apply()
        _available.value = null
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "acornix-${info.version}.apk")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Download failed (${res.code})")
            val body = res.body ?: throw IllegalStateException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target
    }

    /** True while Android still needs the user to allow installing apps from Acornix. */
    fun needsInstallPermission(): Boolean = !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val REPO = "gonzaroman/acornix"
        const val RELEASES_URL = "https://github.com/$REPO/releases"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed"
        private const val MIN_INTERVAL_MS = 20L * 60 * 60 * 1000
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads GitHub's "latest release" answer; null when it has no APK attached. */
        fun parseRelease(body: String): UpdateInfo? {
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val tag = obj.s("tag_name") ?: return null
            val apk = (obj["assets"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.s("name")?.endsWith(".apk", ignoreCase = true) == true }
                ?: return null
            return UpdateInfo(
                version = tag.removePrefix("v").removePrefix("V"),
                notes = plainNotes(obj.s("body").orEmpty()),
                apkUrl = apk.s("browser_download_url") ?: return null,
                apkSize = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0,
                pageUrl = obj.s("html_url") ?: RELEASES_URL,
            )
        }

        /** Markdown release notes as short plain text for the in-app card. */
        fun plainNotes(md: String): String = md.lines()
            .map { it.trim().replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "• ").replace("**", "").replace("`", "") }
            .filter { it.isNotBlank() && !it.startsWith("<") && !it.startsWith("![") }
            .take(8)
            .joinToString("\n")

        /** Numeric comparison of "2.10.1" style versions; anything after a '-' is ignored. */
        fun isNewer(remote: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(remote)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            // Same numbers: the final release is newer than its beta ("2.2.0" > "2.2.0-beta1").
            return !remote.contains('-') && current.contains('-')
        }
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
