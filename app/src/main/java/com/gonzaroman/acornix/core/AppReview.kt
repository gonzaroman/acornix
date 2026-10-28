package com.gonzaroman.acornix.core

import com.gonzaroman.acornix.data.SettingsRepository
import kotlinx.serialization.Serializable

/** What the AI thinks of an app someone shared, before installing it. */
@Serializable
data class AppReviewResult(
    /** "safe", "caution" or "danger". */
    val verdict: String = "caution",
    val summary: String = "",
    val findings: List<String> = emptyList(),
)

/**
 * Asks the user's AI to read a shared app's code and look for anything harmful. It helps, but it isn't a
 * guarantee: the UI always says installing is the user's decision.
 */
class AppReview(private val settings: SettingsRepository) {

    suspend fun review(name: String, code: String): AppReviewResult {
        val language = Prompts.languageName(settings.current().appLanguage, settings.uiLocale())
        val text = settings.activeProvider().streamText(system(language), prompt(name, code)).text
        return parse(text) ?: throw IllegalStateException("The AI didn't return a review")
    }

    companion object {
        const val MAX_CODE_CHARS = 150_000

        fun system(language: String) = """
            You review web apps that someone shared before a user installs them in Acornix, an Android app
            that runs each app in a sandboxed WebView with its own origin and a JavaScript bridge called
            window.Acornix (vibration, flashlight, speech, notifications, opening links and apps, sharing,
            the clipboard, a private database, files, and asking the user's AI, which spends their tokens).

            Look for anything a normal user would not expect or want:
            - Sending data, text the user types, photos, the clipboard or anything stored to an external
              server (fetch, XMLHttpRequest, WebSocket, sendBeacon, images or forms pointing to other sites).
            - Phishing: asking for passwords, card numbers, API keys or personal data.
            - Hidden or obfuscated code (eval, new Function, long base64 or hex strings, atob of code),
              crypto mining, endless loops meant to drain the battery.
            - Abuse of the bridge: opening links or apps on its own, spam notifications, copying to the
              clipboard without being asked, or calling askAI in a loop or far more than the app needs.
            - Code loaded from unknown sites (well-known CDNs like cdnjs, jsdelivr or unpkg are fine).
            Normal things are NOT a problem: games, saving data in Acornix.db, using the camera or the AI for
            what the app clearly says it does, libraries from well-known CDNs.

            Be concrete and calm; don't invent problems. Write "summary" and "findings" in $language, short and
            in plain words for someone who doesn't code. "summary": one or two sentences saying what the app
            does and your conclusion. "findings": the concrete concerns (empty if none).
            Answer ONLY with JSON: {"verdict":"safe|caution|danger","summary":"…","findings":["…"]}
        """.trimIndent()

        fun prompt(name: String, code: String): String {
            val shown = if (code.length > MAX_CODE_CHARS) code.take(MAX_CODE_CHARS) + "\n\n[… the rest of the code was cut]" else code
            return "App name: $name\n\nCode:\n\n$shown"
        }

        fun parse(text: String): AppReviewResult? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            val r = runCatching { lenient.decodeFromString<AppReviewResult>(text.substring(start, end + 1)) }.getOrNull() ?: return null
            val verdict = r.verdict.lowercase().takeIf { it in setOf("safe", "caution", "danger") } ?: "caution"
            return r.copy(verdict = verdict, findings = r.findings.map { it.trim() }.filter { it.isNotEmpty() })
        }
    }
}

