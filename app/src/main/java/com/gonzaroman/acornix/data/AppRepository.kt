package com.gonzaroman.acornix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer

@Serializable
enum class AppKind { APP, FEATURE, SAMPLE }

@Serializable
data class AppMeta(
    val id: String,
    val name: String,
    val emoji: String,
    val kind: AppKind = AppKind.APP,
    val description: String = "",
    /** 0 = unknown (older metadata); [AppRepository] fills it from the file date when reading. */
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastOpenedAt: Long = 0,
    val versionCount: Int = 1,
    /** How long the AI took to write the first version, for the share card. 0 = unknown. */
    val buildSeconds: Int = 0,
    /** The code calls the user's AI (read from index.html, never saved). */
    @kotlinx.serialization.Transient val usesAi: Boolean = false,
)

@Serializable
data class VersionInfo(
    val number: Int,
    val createdAt: Long,
    val note: String,
    val size: Int,
)

/**
 * Stores generated web apps in `filesDir/my_apps/<id>/index.html`, the same layout as the original
 * acornix `my_apps/` folder, plus `acornix.json` metadata and a `.versions/` history.
 */
class AppRepository(
    filesDir: File,
    /** Called after an app folder is deleted, so its database rows and files go too. */
    private val onDeleted: suspend (String) -> Unit = {},
) {

    val root: File = File(filesDir, APPS_DIR).apply { mkdirs() }
    // encodeDefaults: without it, fields equal to their default are silently left out of acornix.json.
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _apps = MutableStateFlow<List<AppMeta>>(emptyList())
    val apps: StateFlow<List<AppMeta>> = _apps.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _apps.value = root.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { readMeta(it) }
            .sortedByDescending { maxOf(it.updatedAt, it.lastOpenedAt) }
    }

    fun get(id: String): AppMeta? = _apps.value.firstOrNull { it.id == id } ?: readMeta(dir(id))

    fun dir(id: String) = File(root, id)
    fun indexFile(id: String) = File(dir(id), INDEX)
    fun thumbnailFile(id: String) = File(dir(id), THUMBNAIL)
    private fun metaFile(id: String) = File(dir(id), META)
    private fun versionsDir(id: String) = File(dir(id), VERSIONS)
    private fun versionsIndex(id: String) = File(versionsDir(id), "versions.json")

    suspend fun readCode(id: String): String = withContext(Dispatchers.IO) {
        indexFile(id).takeIf { it.exists() }?.readText().orEmpty()
    }

    /** Creates a new app folder and returns its metadata. */
    suspend fun create(
        name: String,
        code: String,
        kind: AppKind = AppKind.APP,
        description: String = "",
        emoji: String? = null,
        note: String = "Created",
        buildSeconds: Int = 0,
    ): AppMeta = mutex.withLock {
        withContext(Dispatchers.IO) {
            val id = uniqueId(name)
            dir(id).mkdirs()
            indexFile(id).writeText(code)
            val now = System.currentTimeMillis()
            val meta = AppMeta(
                id = id,
                createdAt = now,
                updatedAt = now,
                name = name.trim().ifEmpty { id },
                emoji = emoji ?: guessEmoji("$name $description", kind),
                kind = kind,
                description = description.take(500),
                buildSeconds = buildSeconds,
            )
            writeMeta(meta)
            saveVersion(id, code, note)
            meta
        }
    }.also { refresh() }

    /** Replaces the app's code, keeping the previous code in the version history. */
    suspend fun update(id: String, code: String, note: String): AppMeta = mutex.withLock {
        withContext(Dispatchers.IO) {
            indexFile(id).writeText(code)
            // The old screenshot no longer matches; a fresh one is taken the next time the app runs.
            thumbnailFile(id).delete()
            val count = saveVersion(id, code, note)
            val meta = (readMeta(dir(id)) ?: error("App not found: $id"))
                .copy(updatedAt = System.currentTimeMillis(), versionCount = count)
            writeMeta(meta)
            meta
        }
    }.also { refresh() }

    suspend fun versions(id: String): List<VersionInfo> = withContext(Dispatchers.IO) {
        readVersions(id).sortedByDescending { it.number }
    }

    suspend fun readVersion(id: String, number: Int): String = withContext(Dispatchers.IO) {
        File(versionsDir(id), "$number.html").takeIf { it.exists() }?.readText().orEmpty()
    }

    /** Restoring creates a new version, so nothing is ever lost. */
    suspend fun restore(id: String, number: Int): AppMeta {
        val code = readVersion(id, number)
        require(code.isNotEmpty()) { "Version $number not found" }
        return update(id, code, "Restored version $number")
    }

    suspend fun rename(id: String, name: String, emoji: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                readMeta(dir(id))?.let { writeMeta(it.copy(name = name.trim().ifEmpty { it.name }, emoji = emoji.ifBlank { it.emoji })) }
            }
        }
        refresh()
    }

    suspend fun markOpened(id: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                readMeta(dir(id))?.let { writeMeta(it.copy(lastOpenedAt = System.currentTimeMillis())) }
            }
        }
        refresh()
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { dir(id).deleteRecursively() }
        onDeleted(id)
        refresh()
    }

    /** Registers an existing folder (e.g. after an import) that has an index.html but no metadata. */
    suspend fun adopt(id: String, name: String, kind: AppKind) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val code = indexFile(id).readText()
            val now = System.currentTimeMillis()
            val meta = AppMeta(id = id, name = name, emoji = guessEmoji(name, kind), kind = kind, createdAt = now, updatedAt = now)
            writeMeta(meta)
            if (readVersions(id).isEmpty()) saveVersion(id, code, "Imported")
            meta
        }
    }.also { refresh() }

    fun uniqueId(name: String): String {
        val base = slug(name)
        if (!dir(base).exists()) return base
        var i = 2
        while (dir("${base}_$i").exists()) i++
        return "${base}_$i"
    }

    private fun readMeta(folder: File): AppMeta? {
        val index = File(folder, INDEX)
        if (!index.exists()) return null
        val file = File(folder, META)
        val meta = runCatching { json.decodeFromString(AppMeta.serializer(), file.readText()) }.getOrNull()
            ?: AppMeta(id = folder.name, name = prettyName(folder.name), emoji = guessEmoji(folder.name, AppKind.APP))
        // Unknown dates come from the file itself, so they stay stable between reads.
        val modified = index.lastModified()
        return meta.copy(
            createdAt = meta.createdAt.takeIf { it > 0 } ?: modified,
            updatedAt = meta.updatedAt.takeIf { it > 0 } ?: modified,
            usesAi = runCatching { usesAi(index.readText()) }.getOrDefault(false),
        )
    }

    private fun writeMeta(meta: AppMeta) = metaFile(meta.id).writeText(json.encodeToString(AppMeta.serializer(), meta))

    private fun readVersions(id: String): List<VersionInfo> = runCatching {
        json.decodeFromString(ListSerializer(VersionInfo.serializer()), versionsIndex(id).readText())
    }.getOrDefault(emptyList())

    /** Returns the new number of versions. */
    private fun saveVersion(id: String, code: String, note: String): Int {
        val dir = versionsDir(id).apply { mkdirs() }
        val list = readVersions(id).toMutableList()
        val number = (list.maxOfOrNull { it.number } ?: 0) + 1
        File(dir, "$number.html").writeText(code)
        list += VersionInfo(number, System.currentTimeMillis(), note.take(300), code.length)
        // Keep the history bounded.
        while (list.size > MAX_VERSIONS) {
            val oldest = list.minBy { it.number }
            File(dir, "${oldest.number}.html").delete()
            list.remove(oldest)
        }
        versionsIndex(id).writeText(json.encodeToString(ListSerializer(VersionInfo.serializer()), list))
        return list.size
    }

    companion object {
        /** Whether a page asks the user's AI (Acornix.askAI or Acornix.ai.chat). */
        fun usesAi(code: String): Boolean = AI_CALL.containsMatchIn(code)
        private val AI_CALL = Regex("""\baskAI\b|\bai\.chat\b""")

        const val APPS_DIR = "my_apps"
        const val INDEX = "index.html"
        const val META = "acornix.json"
        const val THUMBNAIL = "thumbnail.jpg"
        const val VERSIONS = ".versions"
        private const val MAX_VERSIONS = 30

        /** Internal files that are not part of the app itself (excluded from exports). */
        val INTERNAL_FILES = setOf(META, THUMBNAIL, VERSIONS)

        fun slug(name: String): String {
            val ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            val clean = ascii.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40)
            return clean.ifEmpty { "app" }
        }

        fun prettyName(id: String) = id.replace('_', ' ').replace('-', ' ').trim()
            .split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        private val EMOJI_KEYWORDS = listOf(
            listOf("piano", "music", "música", "musica", "song", "canción") to "🎹",
            listOf("drum", "bater", "tambor") to "🥁",
            listOf("calc", "calcul") to "🧮",
            listOf("camera", "cámara", "camara", "photo", "foto") to "📷",
            listOf("bowling", "bolos") to "🎳",
            listOf("castle", "castillo", "defense", "defensa", "tower") to "🏰",
            listOf("mario", "platform", "plataforma", "super") to "🍄",
            listOf("imc", "bmi", "weight", "peso", "health", "salud") to "⚖️",
            listOf("paint", "draw", "pintar", "dibuj", "canvas") to "🎨",
            listOf("plane", "avión", "avion", "flight") to "✈️",
            listOf("bird", "pájaro", "pajaro", "flappy") to "🐦",
            listOf("solar", "planet", "planeta", "space", "espacio") to "🪐",
            listOf("snake", "serpiente") to "🐍",
            listOf("weather", "tiempo", "clima") to "🌦️",
            listOf("todo", "task", "tarea", "list", "lista", "notes", "notas") to "📝",
            listOf("timer", "clock", "reloj", "cronó", "crono", "alarm") to "⏱️",
            listOf("battery", "batería", "bateria") to "🔋",
            listOf("flash", "linterna", "torch") to "🔦",
            listOf("chat", "bot", "asistente", "assistant") to "🤖",
            listOf("coche", "racing", "race", "carrera") to "🏎️",
            listOf("ball", "pelota", "arkanoid", "breakout", "pong") to "🏓",
            listOf("matrix", "code", "código") to "💻",
            listOf("game", "juego") to "🎮",
        )

        fun guessEmoji(text: String, kind: AppKind): String {
            val t = text.lowercase()
            // The longest matching keyword wins: "planetas" is a planet (🪐), not a plane (✈️).
            EMOJI_KEYWORDS
                .mapNotNull { (keys, emoji) -> keys.filter { t.contains(it) }.maxOfOrNull { it.length }?.let { it to emoji } }
                .maxByOrNull { it.first }
                ?.let { return it.second }
            return if (kind == AppKind.FEATURE) "🧬" else "🌰"
        }
    }
}
