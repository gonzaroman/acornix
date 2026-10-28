package com.gonzaroman.acornix.data

import com.gonzaroman.acornix.BuildConfig
import com.gonzaroman.acornix.ai.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** One app of the community gallery, as listed in the gallery repository's catalog.json. */
@Serializable
data class GalleryApp(
    val id: String,
    val name: String,
    val emoji: String = "🌰",
    val category: String = "",
    val description: String = "",
    /** The request that created it. */
    val prompt: String = "",
    /** GitHub user name. */
    val author: String = "",
    val language: String = "en",
    val version: Int = 1,
    val added: String = "",
    val updated: String = "",
    val featured: Boolean = false,
    /** What the code uses: ai, phone, camera, microphone, internet. */
    val tags: List<String> = emptyList(),
    val files: List<String> = listOf(AppRepository.INDEX),
    val size: Long = 0,
    /** SHA-256 of index.html. */
    val sha256: String = "",
)

@Serializable
data class GalleryCatalog(val version: Int = 1, val apps: List<GalleryApp> = emptyList())

data class GalleryState(val catalog: GalleryCatalog? = null, val loading: Boolean = false, val failed: Boolean = false)

class GalleryException(message: String) : IOException(message)

/**
 * The community gallery: apps other people made with Acornix, reviewed and published in the public
 * gonzaroman/acornix-apps repository. There is no server: the catalog and the apps are plain files
 * read from GitHub, and the last catalog is kept so the gallery still opens offline.
 */
class Gallery(
    filesDir: File,
    private val apps: AppRepository,
    private val importExport: ImportExport,
    private val aiPermissions: AiPermissions,
    private val baseUrl: String = BuildConfig.GALLERY_URL.trimEnd('/'),
    private val http: OkHttpClient = HttpClients.default,
) {
    private val cacheFile = File(filesDir, "gallery/catalog.json")
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    fun screenshotUrl(app: GalleryApp) = "$baseUrl/apps/${app.id}/screenshot.jpg?v=${app.version}"
    fun pageUrl(app: GalleryApp) = "$REPO_URL/tree/main/apps/${app.id}"
    fun reportUrl(app: GalleryApp) = "$REPO_URL/issues/new?title=" + android.net.Uri.encode("Report: ${app.name} (${app.id})")

    /** Loads the catalog: the saved copy right away, then the latest one from GitHub. */
    suspend fun refresh() {
        if (_state.value.loading) return
        if (_state.value.catalog == null) {
            val cached = withContext(Dispatchers.IO) { runCatching { parse(cacheFile.readText()) }.getOrNull() }
            _state.update { it.copy(catalog = cached) }
        }
        _state.update { it.copy(loading = true, failed = false) }
        val fresh = runCatching {
            val text = String(get("$baseUrl/catalog.json"))
            parse(text).also {
                withContext(Dispatchers.IO) { cacheFile.parentFile?.mkdirs(); cacheFile.writeText(text) }
            }
        }.getOrNull()
        _state.update { GalleryState(catalog = fresh ?: it.catalog, loading = false, failed = fresh == null) }
    }

    fun find(id: String): GalleryApp? = _state.value.catalog?.apps?.firstOrNull { it.id == id }

    /** The user's copy of this gallery app, if they installed it. */
    fun installed(app: GalleryApp): AppMeta? = apps.apps.value.firstOrNull { it.source?.gallery == app.id }

    fun hasUpdate(app: GalleryApp): Boolean = installed(app)?.source?.let { it.version < app.version } == true

    /** Installs it as a new app ("Notes 2" if there is already a "Notes"). */
    suspend fun install(app: GalleryApp): AppMeta {
        val files = download(app)
        val meta = importExport.importEntries(apps.uniqueName(app.name), files)
        apps.edit(meta.id) { describe(it, app) }
        return apps.get(meta.id) ?: meta
    }

    /** Puts it in place of one of the user's apps, which keeps its name and its data. */
    suspend fun replace(app: GalleryApp, id: String): AppMeta {
        val files = download(app)
        importExport.replace(id, files, "Community gallery: ${app.name} v${app.version}")
        apps.edit(id) { describe(it, app).copy(name = it.name) }
        return apps.get(id) ?: error("App not found: $id")
    }

    /** Copies it to a hidden folder to run it without installing. Returns the id to open. */
    suspend fun prepareTrial(app: GalleryApp): String {
        val files = download(app)
        clearTrials()
        val id = AppRepository.TRIAL_PREFIX + AppRepository.slug(app.id)
        withContext(Dispatchers.IO) {
            val dir = apps.dir(id).apply { mkdirs() }
            files.forEach { (path, bytes) -> File(dir, path).apply { parentFile?.mkdirs() }.writeBytes(bytes) }
        }
        apps.adopt(id, app.name, kindOf(app))
        apps.edit(id) { describe(it, app) }
        return id
    }

    /** Deletes apps that were being tried, with their data and their AI permission. */
    suspend fun clearTrials() {
        apps.root.listFiles { f -> f.isDirectory && AppRepository.isTrial(f.name) }.orEmpty().forEach {
            apps.delete(it.name)
            aiPermissions.set(it.name, null)
        }
    }

    private fun describe(meta: AppMeta, app: GalleryApp) = meta.copy(
        emoji = app.emoji.ifBlank { meta.emoji },
        // The request is what "Improve" builds on, as with apps the user created.
        description = app.prompt.ifBlank { app.description }.take(500),
        kind = kindOf(app),
        source = AppSource(app.id, app.version, app.author),
    )

    private suspend fun download(app: GalleryApp): Map<String, ByteArray> {
        val files = app.files.ifEmpty { listOf(AppRepository.INDEX) }
        require(AppRepository.INDEX in files) { "The app has no index.html" }
        var total = 0L
        val result = linkedMapOf<String, ByteArray>()
        for (path in files) {
            if (!SAFE_PATH.matches(path) || ".." in path.split('/')) throw GalleryException("Unsafe file name: $path")
            val bytes = get("$baseUrl/apps/${app.id}/$path")
            total += bytes.size
            if (total > MAX_APP_BYTES) throw GalleryException("The app is too big")
            result[path] = bytes
        }
        if (app.sha256.isNotEmpty() && sha256(result.getValue(AppRepository.INDEX)) != app.sha256.lowercase()) {
            throw GalleryException("The download doesn't match the catalog")
        }
        return result
    }

    private suspend fun get(url: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { response ->
            if (!response.isSuccessful) throw GalleryException("HTTP ${response.code}")
            response.body?.bytes() ?: throw GalleryException("Empty response")
        }
    }

    companion object {
        const val REPO_URL = "https://github.com/gonzaroman/acornix-apps"
        private const val MAX_APP_BYTES = 5L * 1024 * 1024
        private val SAFE_PATH = Regex("""[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*""")
        private val json = Json { ignoreUnknownKeys = true }

        val CATEGORIES = listOf("games", "tools", "creative", "health", "learning")

        fun parse(text: String): GalleryCatalog = json.decodeFromString(GalleryCatalog.serializer(), text)

        /** Featured apps first, then the newest. */
        fun sorted(apps: List<GalleryApp>): List<GalleryApp> =
            apps.sortedWith(compareByDescending<GalleryApp> { it.featured }.thenByDescending { it.added }.thenBy { it.name.lowercase() })

        /** [filter] is a category, "ai" for apps that use the AI, or null for all. */
        fun search(apps: List<GalleryApp>, query: String, filter: String?): List<GalleryApp> {
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return sorted(apps).filter { app ->
                (filter == null || app.category == filter || (filter == "ai" && "ai" in app.tags)) &&
                    words.all { w -> listOf(app.name, app.description, app.prompt, app.author).any { it.lowercase().contains(w) } }
            }
        }

        fun kindOf(app: GalleryApp) = if ("phone" in app.tags) AppKind.FEATURE else AppKind.APP

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
