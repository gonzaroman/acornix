package com.gonzaroman.acornix.core

import com.gonzaroman.acornix.ai.AiException
import com.gonzaroman.acornix.ai.ImageData
import com.gonzaroman.acornix.data.AppDataStore
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.AppRepository
import com.gonzaroman.acornix.data.SettingsRepository

/** What to build: a new app/functionality, or a change to an existing app. */
data class GenerationRequest(
    val idea: String,
    val name: String = "",
    val kind: AppKind = AppKind.APP,
    val category: String? = null,
    val images: List<ImageData> = emptyList(),
    /** When set, the existing app is improved instead of creating a new one. */
    val improveAppId: String? = null,
    /** The emoji of the AI idea it comes from, if any; otherwise one is guessed from the words. */
    val emoji: String? = null,
)

data class GenerationProgress(val chars: Int, val code: String, val codeFinished: Boolean)

data class GenerationResult(val app: AppMeta, val suggestion: String?, val truncated: Boolean)

class GenerationException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NO_CODE, REFUSED, API }
}

/** Shared by the Create screen and the assistant's create_web_app / improve_app tools. */
class AppGenerator(
    private val settings: SettingsRepository,
    private val repo: AppRepository,
    private val data: AppDataStore,
) {
    suspend fun generate(
        request: GenerationRequest,
        onProgress: (GenerationProgress) -> Unit = {},
    ): GenerationResult {
        val s = settings.current()
        val language = Prompts.languageName(s.appLanguage, settings.uiLocale())
        val provider = settings.activeProvider()

        val existing = request.improveAppId?.let { repo.get(it) ?: throw AiException("App not found: $it") }
        val hasImage = request.images.isNotEmpty()
        val (system, prompt) = if (existing != null) {
            Prompts.improve(language, existing.kind == AppKind.FEATURE) to
                Prompts.improvePrompt(repo.readCode(existing.id), request.idea, hasImage, data.describeForAi(existing.id))
        } else {
            val sys = if (request.kind == AppKind.FEATURE) Prompts.createFeature(language) else Prompts.createApp(language)
            sys to Prompts.createPrompt(request.idea, request.category, hasImage)
        }

        val startedAt = System.currentTimeMillis()
        val buffer = StringBuilder()
        var lastEmit = 0
        val result = try {
            provider.streamText(system, prompt, request.images) { delta ->
                buffer.append(delta)
                // Throttle UI updates to roughly every 400 characters.
                if (buffer.length - lastEmit >= 400 || ResponseParser.codeFinished(buffer.toString())) {
                    lastEmit = buffer.length
                    val text = buffer.toString()
                    onProgress(GenerationProgress(text.length, ResponseParser.streamingCode(text), ResponseParser.codeFinished(text)))
                }
            }
        } catch (e: AiException) {
            throw GenerationException(GenerationException.Reason.API, e.message ?: "AI error")
        }
        if (result.refused) throw GenerationException(GenerationException.Reason.REFUSED, "The model declined this request")

        val parsed = ResponseParser.parse(result.text)
        val code = parsed.code
            ?: throw GenerationException(GenerationException.Reason.NO_CODE, "The response did not contain an app")
        onProgress(GenerationProgress(result.text.length, code, true))

        val app = if (existing != null) {
            repo.update(existing.id, code, request.idea)
        } else {
            val name = request.name.ifBlank { suggestName(request.idea) }
            val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
            repo.create(name, code, request.kind, description = request.idea, emoji = request.emoji, note = request.idea, buildSeconds = seconds)
        }
        return GenerationResult(app, parsed.suggestion, result.truncated)
    }

    companion object {
        /** "crea un piano para móvil" -> "Piano Para Móvil" (the user can rename it later). */
        fun suggestName(idea: String): String {
            val stop = setOf(
                "create", "make", "build", "a", "an", "the", "for", "app", "game", "me", "please",
                "crea", "créame", "creame", "haz", "hazme", "un", "una", "el", "la", "de", "para", "que", "con", "juego", "aplicación", "aplicacion", "app",
            )
            // "a habit tracker with daily streaks" -> "Habit Tracker", not "Habit Tracker With".
            val breaks = setOf("with", "and", "that", "which", "where", "using", "con", "y", "que", "donde", "usando")
            val all = idea.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
            val cut = all.indexOfFirst { it in breaks }
            val head = if (cut > 0 && all.take(cut).any { it !in stop }) all.take(cut) else all
            val words = head.filter { it !in stop }.take(3)
            return words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifBlank { "App" }
        }
    }
}
