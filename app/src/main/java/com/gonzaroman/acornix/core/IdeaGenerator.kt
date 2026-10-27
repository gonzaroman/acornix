package com.gonzaroman.acornix.core

import com.gonzaroman.acornix.data.AppLanguage
import com.gonzaroman.acornix.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One suggestion: a short label for the chip and the full request it fills in. */
@Serializable
data class Idea(val emoji: String = "✨", val title: String, val prompt: String)

@Serializable
private data class IdeaBatch(val createdAt: Long, val ideas: List<Idea>)

@Serializable
private data class IdeaCache(
    val batches: Map<String, IdeaBatch> = emptyMap(),
    /** Titles already shown, newest last, so the AI doesn't repeat itself. */
    val seen: List<String> = emptyList(),
)

/**
 * The "Need ideas?" section. Everyone starts with the same built-in ideas ([defaults], written for
 * Acornix in English and Spanish), so opening the screen never costs tokens. Only when the user asks,
 * the AI writes a fresh, surprising batch; the last one is kept per kind, category and language and
 * shown from then on.
 */
class IdeaGenerator(
    private val settings: SettingsRepository,
    private val file: File,
    private val defaults: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val builtIn: Map<String, Map<String, Map<String, List<Idea>>>> by lazy {
        runCatching { json.decodeFromString<Map<String, Map<String, Map<String, List<Idea>>>>>(defaults()) }.getOrDefault(emptyMap())
    }

    /** On in Settings (the default) and with an AI configured. */
    suspend fun isAvailable(): Boolean = settings.current().let { it.aiIdeas && it.config().isUsable }

    /** The last batch the AI wrote for this kind, category and language, or null. */
    suspend fun cached(feature: Boolean, category: String?): List<Idea>? =
        mutex.withLock { read() }.batches[key(feature, category, language())]?.ideas

    /**
     * The built-in ideas for this kind and category ([categoryIndex] in the categories list, or -1),
     * in Spanish when Acornix is shown in Spanish and in English otherwise.
     */
    suspend fun builtIn(feature: Boolean, categoryIndex: Int): List<Idea> {
        val lang = when (settings.current().appLanguage) {
            AppLanguage.SPANISH -> "es"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.AUTO -> if (settings.uiLocale().language == "es") "es" else "en"
        }
        val sets = builtIn[lang]?.get(if (feature) "feature" else "app") ?: return emptyList()
        return sets[if (feature || categoryIndex < 0) "" else categoryIndex.toString()] ?: sets[""].orEmpty()
    }

    /**
     * Asks the AI for a new batch and caches it. Throws on network/API errors. [onPartial] receives the
     * ideas finished so far while the answer streams in, so the first ones show up after a few seconds.
     */
    suspend fun fresh(feature: Boolean, category: String?, onPartial: (List<Idea>) -> Unit = {}): List<Idea> {
        val language = language()
        val seen = mutex.withLock { read() }.seen
        val provider = settings.activeProvider()
        val text = StringBuilder()
        var shown = 0
        val result = provider.streamText(system(feature, language), request(category, seen), quick = true) { delta ->
            text.append(delta)
            val done = parsePartial(text)
            if (done.size > shown) {
                shown = done.size
                onPartial(done)
            }
        }
        val ideas = parse(result.text)
        if (ideas.isEmpty()) throw IllegalStateException("No ideas in the response")
        mutex.withLock {
            val cache = read()
            val batches = cache.batches + (key(feature, category, language) to IdeaBatch(System.currentTimeMillis(), ideas))
            write(IdeaCache(batches, (cache.seen + ideas.map { it.title }).takeLast(MAX_SEEN)))
        }
        return ideas
    }

    private suspend fun language() = Prompts.languageName(settings.current().appLanguage, settings.uiLocale())

    private suspend fun read(): IdeaCache = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<IdeaCache>(file.readText()) }.getOrDefault(IdeaCache())
    }

    private suspend fun write(cache: IdeaCache) = withContext(Dispatchers.IO) {
        runCatching { file.writeText(json.encodeToString(IdeaCache.serializer(), cache)) }
    }

    companion object {
        const val BATCH_SIZE = 6
        private const val MAX_SEEN = 60

        fun key(feature: Boolean, category: String?, language: String) =
            "${if (feature) "feature" else "app"}|${category.orEmpty()}|$language"

        fun system(feature: Boolean, language: String) = """
            You invent ideas for Acornix, an Android app where people describe something and an AI
            builds it in seconds as a single self-contained HTML file that runs full screen on their
            phone, offline, inside a WebView. Your job is to make the user say "wow, I didn't know I
            could make that": ideas must be ORIGINAL, delightful and a little surprising, never the
            usual tutorial examples.

            What can be built: games, but also tools, sound and music toys, generative art, science
            and space, learning, health and habits, creativity, productivity, and odd little useful
            things. Available: touch and multi-touch, canvas, WebGL (three.js from a CDN), Web Audio
            synthesis, the device's motion and orientation sensors, the camera, speech, vibration and
            local storage.${if (feature) """
            These ideas are Acornix "functionalities": each one must use the phone through the
            window.Acornix bridge — flashlight, vibration, speaking out loud, notifications, battery,
            sharing, copying, opening other apps or asking the AI from inside the app.""" else ""}
            Not available: accounts, servers, other people's data, APIs that need keys or payment.

            Rules:
            - Exactly $BATCH_SIZE ideas, all different from each other. Without a requested category,
              mix at least 4 different kinds and at most 2 games.
            - Avoid clichés: plain to-do lists, calculators, tic-tac-toe, snake, Flappy Bird clones,
              plain Pomodoro timers, weather or news apps.
            - Each idea must be doable well in one HTML file on a phone.
            - "title": max 8 words, catchy, no final period. "emoji": one fitting emoji.
            - "prompt": 2–3 sentences addressed to the app builder: what it does, the key features,
              and the look and feel. Concrete and vivid.
            - Write "title" and "prompt" in $language.

            Answer ONLY with a JSON array, no markdown and no comments:
            [{"emoji": "🎹", "title": "...", "prompt": "..."}]
        """.trimIndent()

        fun request(category: String?, seen: List<String>): String = buildString {
            append("Give me $BATCH_SIZE new ideas")
            if (!category.isNullOrBlank()) append(" in the category \"$category\"")
            append('.')
            if (seen.isNotEmpty()) {
                append("\nDon't repeat or closely resemble these, already suggested: ")
                append(seen.takeLast(40).joinToString("; "))
            }
        }

        /** The ideas whose JSON object is already complete in a half-received answer. */
        fun parsePartial(text: CharSequence): List<Idea> {
            val start = text.indexOf("[")
            if (start < 0) return emptyList()
            val objects = mutableListOf<String>()
            var depth = 0
            var inString = false
            var escaped = false
            var objStart = -1
            for (i in start + 1 until text.length) {
                val ch = text[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                    continue
                }
                when (ch) {
                    '"' -> inString = true
                    '{' -> { if (depth == 0) objStart = i; depth++ }
                    '}' -> { depth--; if (depth == 0 && objStart >= 0) objects += text.substring(objStart, i + 1) }
                    ']' -> if (depth == 0) break
                }
            }
            return if (objects.isEmpty()) emptyList() else parse("[" + objects.joinToString(",") + "]")
        }

        /** Reads the JSON array even if the model wrapped it in a code fence or added text around it. */
        fun parse(text: String): List<Idea> {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start < 0 || end <= start) return emptyList()
            val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
            return runCatching { lenient.decodeFromString<List<Idea>>(text.substring(start, end + 1)) }
                .getOrDefault(emptyList())
                .map { Idea(it.emoji.trim().ifEmpty { "✨" }.take(8), it.title.trim().trimEnd('.'), it.prompt.trim()) }
                .filter { it.title.isNotEmpty() && it.prompt.isNotEmpty() }
                .take(BATCH_SIZE)
        }
    }
}
