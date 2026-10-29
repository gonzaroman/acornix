package com.gonzaroman.acornix.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.gonzaroman.acornix.BuildConfig
import com.gonzaroman.acornix.ai.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File

/** What the author fills in before publishing. */
data class PublishDraft(
    val id: String,
    val name: String,
    val emoji: String,
    val category: String,
    val description: String,
    val prompt: String,
)

/**
 * Sends one of the user's apps to the community gallery: its files go to a secret gist of the user,
 * and an issue in the gallery repository asks to publish it. A GitHub Action reviews it there and the
 * maintainer approves it. Only the app's code and its screenshot are sent, never its data.
 */
class Publisher(
    private val apps: AppRepository,
    private val account: GitHubAccount,
    private val http: OkHttpClient = HttpClients.default,
) {
    enum class Problem { NO_SCREENSHOT, NOT_TEXT, TOO_BIG }

    /** A starting draft: the app's name, emoji and request; an update keeps the id it was published with. */
    fun draft(meta: AppMeta): PublishDraft {
        val login = account.login.value
        val mine = meta.source?.takeIf { login != null && it.author.equals(login, ignoreCase = true) }
        return PublishDraft(
            id = mine?.gallery ?: galleryId(meta.name),
            name = meta.name,
            emoji = meta.emoji,
            category = "",
            description = "",
            prompt = meta.description.take(MAX_PROMPT),
        )
    }

    /** Why the app can't be published yet, or null. */
    fun check(appId: String): Problem? {
        val files = appFiles(apps.dir(appId))
        return when {
            files.any { !TEXT_FILE.containsMatchIn(it.name) || it.parentFile != apps.dir(appId) } -> Problem.NOT_TEXT
            files.sumOf { it.length() } > MAX_APP_BYTES -> Problem.TOO_BIG
            !apps.thumbnailFile(appId).exists() -> Problem.NO_SCREENSHOT
            else -> null
        }
    }

    /** Publishes it and returns the address of the request on GitHub. */
    suspend fun publish(appId: String, draft: PublishDraft): String = withContext(Dispatchers.IO) {
        val token = account.token() ?: throw GitHubException(GitHubException.Reason.SIGNED_OUT)
        val dir = apps.dir(appId)
        val gist = send(token, "https://api.github.com/gists", buildJsonObject {
            put("description", "${draft.emoji} ${draft.name}, made with Acornix")
            put("public", false)
            putJsonObject("files") {
                appFiles(dir).forEach { f -> putJsonObject(f.name) { put("content", f.readText()) } }
                putJsonObject(SCREENSHOT) { put("content", screenshot(apps.thumbnailFile(appId))) }
            }
        })
        val gistId = gist.str("id") ?: throw GitHubException(GitHubException.Reason.FAILED)
        val issue = send(token, "https://api.github.com/repos/$GALLERY_REPO/issues", buildJsonObject {
            put("title", "Publish: ${draft.emoji} ${draft.name}")
            put("body", issueBody(draft, gistId, gist.str("html_url").orEmpty()))
        })
        issue.str("html_url") ?: throw GitHubException(GitHubException.Reason.FAILED)
    }

    private fun send(token: String, url: String, body: JsonObject): JsonObject {
        val request = GitHubAccount.api(Request.Builder().url(url), token)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { r ->
            if (r.code == 401) {
                account.signOut()
                throw GitHubException(GitHubException.Reason.SIGNED_OUT)
            }
            if (!r.isSuccessful) throw GitHubException(GitHubException.Reason.FAILED, "HTTP ${r.code}: ${r.body?.string()?.take(300)}")
            Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
        }
    }

    private fun appFiles(dir: File): List<File> = dir.walkTopDown()
        .onEnter { it == dir || it.name !in AppRepository.INTERNAL_FILES }
        .filter { it.isFile && it.name !in AppRepository.INTERNAL_FILES }
        .toList()

    /** The thumbnail Acornix took of the running app, at most 540 px wide, as base64 JPEG. */
    private fun screenshot(file: File): String {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: throw GitHubException(GitHubException.Reason.FAILED, "No screenshot")
        val scaled = if (bitmap.width > SHOT_WIDTH) {
            Bitmap.createScaledBitmap(bitmap, SHOT_WIDTH, bitmap.height * SHOT_WIDTH / bitmap.width, true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        const val GALLERY_REPO = "gonzaroman/acornix-apps"
        const val RULES_URL = "https://github.com/$GALLERY_REPO#rules"
        const val MAX_NAME = 40
        const val MAX_DESCRIPTION = 300
        const val MAX_PROMPT = 2000
        private const val MAX_APP_BYTES = 2L * 1024 * 1024
        private const val SHOT_WIDTH = 540
        private const val SCREENSHOT = "screenshot.jpg.b64"
        // Must match the review bot in the gallery repository (scripts/acornix_bot.py).
        private const val MARKER = "<!-- acornix-submission v1 -->"
        private val TEXT_FILE = Regex("""\.(html?|js|mjs|css|json|svg|txt|md)$""", RegexOption.IGNORE_CASE)
        private val json = Json { prettyPrint = true }

        /** "Mi Piano" -> "mi-piano": the gallery's ids use dashes. */
        fun galleryId(name: String) = AppRepository.slug(name).replace('_', '-')

        /** The issue: readable for people, with the details as JSON for the review bot. */
        fun issueBody(d: PublishDraft, gistId: String, gistUrl: String): String {
            val data = buildJsonObject {
                put("id", d.id)
                put("name", d.name)
                put("emoji", d.emoji)
                put("category", d.category)
                put("description", d.description)
                put("prompt", d.prompt)
                put("language", java.util.Locale.getDefault().language)
                put("gist", gistId)
                put("acornix", BuildConfig.VERSION_NAME)
            }
            return buildString {
                appendLine(MARKER)
                appendLine("### ${d.emoji} ${d.name}")
                appendLine()
                appendLine(d.description)
                if (d.prompt.isNotBlank()) {
                    appendLine()
                    appendLine("**The request that created it:** ${d.prompt}")
                }
                appendLine()
                appendLine("**Category:** ${d.category} · **Code:** $gistUrl")
                appendLine()
                appendLine("Sent from Acornix ${BuildConfig.VERSION_NAME}. It will be reviewed before it appears in the gallery.")
                appendLine()
                appendLine("<details><summary>Details for the review bot</summary>")
                appendLine()
                appendLine("```json")
                appendLine(json.encodeToString(JsonObject.serializer(), data))
                appendLine("```")
                appendLine()
                append("</details>")
            }
        }

        private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    }
}
