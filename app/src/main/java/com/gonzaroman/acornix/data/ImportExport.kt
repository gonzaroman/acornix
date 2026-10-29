package com.gonzaroman.acornix.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlinx.serialization.json.jsonObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_ZIP, PYTHON_PLUGIN, NO_INDEX }
}

/**
 * ZIP import/export compatible with the original acornix "Smart Import-Export Hub":
 * an exported app is a ZIP with `index.html` (and any assets) at its root.
 */
class ImportExport(
    private val context: Context,
    private val repo: AppRepository,
    private val data: AppDataStore,
) {

    /**
     * Writes the app to a ZIP in the cache directory and returns it (share it with a FileProvider).
     * With [includeData] the ZIP also carries the app's database ([DATA_FILE]) and stored files.
     */
    suspend fun export(id: String, includeData: Boolean = false): File {
        val meta = repo.get(id) ?: error("App not found: $id")
        val extras = mutableMapOf<String, ByteArray>()
        val extraFiles = mutableMapOf<String, File>()
        if (includeData) {
            extras[DATA_FILE] = data.export(id).toString().toByteArray()
            data.listFiles(id).forEach { extraFiles["$DATA_FILES_DIR/${it.name}"] = it.file }
        }
        return withContext(Dispatchers.IO) {
            val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val zip = File(outDir, "${AppRepository.slug(meta.name)}.zip")
            writeZip(repo.dir(id), zip, extras, extraFiles)
            zip
        }
    }

    /** Copies the exported ZIP to a user-chosen location (Storage Access Framework). */
    suspend fun exportTo(id: String, target: Uri, includeData: Boolean = false) = withContext(Dispatchers.IO) {
        val zip = export(id, includeData)
        context.contentResolver.openOutputStream(target)?.use { out -> zip.inputStream().use { it.copyTo(out) } }
            ?: error("Cannot write to the selected file")
    }

    suspend fun import(uri: Uri): AppMeta = install(preview(uri))

    /** Reads a shared app without installing it, so the user can review it first. */
    suspend fun preview(uri: Uri): ImportPreview {
        val fileName = displayName(uri) ?: "imported.zip"
        val name = AppRepository.prettyName(fileName.substringBeforeLast('.'))
        val entries = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { readZip(it) }
                ?: throw ImportException(ImportException.Reason.NOT_A_ZIP)
        }
        val prefix = appRoot(entries)
        // The code the AI reviews: every text file of the app, index.html first.
        val code = entries.filterKeys { it.startsWith(prefix) && TEXT_FILE.containsMatchIn(it) && !it.endsWith(DATA_FILE) }
            .entries.sortedBy { if (it.key.endsWith("index.html")) 0 else 1 }
            .joinToString("\n\n") { (path, bytes) -> "=== ${path.removePrefix(prefix)} ===\n" + String(bytes) }
        return ImportPreview(name, entries, code)
    }

    /** Installs the shared app as a new app called [name] (by default the ZIP's name). */
    suspend fun install(preview: ImportPreview, name: String = preview.name): AppMeta = importEntries(name, preview.entries)

    /**
     * Puts the shared app's code in place of an existing app's. Its data stays, and its current code
     * stays in the version history.
     */
    suspend fun replace(id: String, entries: Map<String, ByteArray>, note: String): AppMeta {
        val prefix = appRoot(entries)
        // index.html goes through update(), which keeps the version history.
        val code = String(entries.getValue("${prefix}index.html"))
        writeEntries(id, entries.filterKeys { it != "${prefix}index.html" }, prefix)
        return repo.update(id, code, note)
    }

    internal suspend fun importEntries(name: String, entries: Map<String, ByteArray>): AppMeta {
        val prefix = appRoot(entries)
        val id = repo.uniqueId(name)
        writeEntries(id, entries, prefix)
        val meta = repo.adopt(id, name, AppKind.APP)
        // Data exported together with the app (a shared game save, a list…).
        entries["${prefix}$DATA_FILE"]?.let { bytes ->
            runCatching { AiJsonLite.parseToJsonElement(String(bytes)).jsonObject }.getOrNull()
                ?.let { data.import(id, it, replace = true) }
        }
        entries.filterKeys { it.startsWith("$prefix$DATA_FILES_DIR/") }.forEach { (path, bytes) ->
            data.saveFile(id, path.substringAfterLast('/'), bytes)
        }
        return meta
    }

    private fun appRoot(entries: Map<String, ByteArray>): String {
        if (entries.isEmpty()) throw ImportException(ImportException.Reason.NOT_A_ZIP)
        return findAppRoot(entries.keys) ?: run {
            val isPlugin = entries.any { (path, bytes) ->
                path.endsWith("main.py") && String(bytes).let { "config =" in it || "config=" in it }
            }
            throw ImportException(if (isPlugin) ImportException.Reason.PYTHON_PLUGIN else ImportException.Reason.NO_INDEX)
        }
    }

    /** Writes the app's own files into its folder: not Acornix's internal files, not its exported data. */
    private suspend fun writeEntries(id: String, entries: Map<String, ByteArray>, prefix: String) = withContext(Dispatchers.IO) {
        val dir = repo.dir(id).apply { mkdirs() }
        val canonicalRoot = dir.canonicalPath + File.separator
        entries.forEach { (path, bytes) ->
            if (!path.startsWith(prefix)) return@forEach
            val relative = path.removePrefix(prefix)
            val first = relative.split('/').first()
            if (relative.isEmpty() || first in AppRepository.INTERNAL_FILES || first == DATA_FILE || first == DATA_FILES_DIR) return@forEach
            val out = File(dir, relative)
            // Zip-slip protection.
            if (!out.canonicalPath.startsWith(canonicalRoot)) return@forEach
            out.parentFile?.mkdirs()
            out.writeBytes(bytes)
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        private val TEXT_FILE = Regex("""\.(html?|js|mjs|css|json|svg|txt|md)$""", RegexOption.IGNORE_CASE)
        private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
        const val DATA_FILE = "acornix-data.json"
        const val DATA_FILES_DIR = "acornix-files"
        private val AiJsonLite = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun readZip(input: InputStream): Map<String, ByteArray> {
            val result = linkedMapOf<String, ByteArray>()
            var total = 0L
            try {
                ZipInputStream(input.buffered()).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val bytes = zis.readBytes()
                            total += bytes.size
                            if (total > MAX_TOTAL_BYTES) throw ImportException(ImportException.Reason.NOT_A_ZIP)
                            result[entry.name.replace('\\', '/').trimStart('/')] = bytes
                        }
                        zis.closeEntry()
                    }
                }
            } catch (e: ImportException) {
                throw e
            } catch (e: Exception) {
                throw ImportException(ImportException.Reason.NOT_A_ZIP)
            }
            return result
        }

        /** Returns the folder prefix that contains index.html ("" for the root), or null. */
        fun findAppRoot(paths: Collection<String>): String? {
            if ("index.html" in paths) return ""
            return paths.filter { it.endsWith("/index.html") }
                .minByOrNull { it.count { c -> c == '/' } }
                ?.removeSuffix("index.html")
        }

        fun writeZip(
            sourceDir: File,
            zip: File,
            extras: Map<String, ByteArray> = emptyMap(),
            extraFiles: Map<String, File> = emptyMap(),
        ) {
            ZipOutputStream(zip.outputStream().buffered()).use { zos ->
                extras.forEach { (path, bytes) ->
                    zos.putNextEntry(ZipEntry(path)); zos.write(bytes); zos.closeEntry()
                }
                extraFiles.forEach { (path, file) ->
                    zos.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
                }
                sourceDir.walkTopDown()
                    .onEnter { it == sourceDir || it.name !in AppRepository.INTERNAL_FILES }
                    .filter { it.isFile && it.name !in AppRepository.INTERNAL_FILES }
                    .forEach { file ->
                        zos.putNextEntry(ZipEntry(file.relativeTo(sourceDir).invariantSeparatorsPath))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
            }
        }
    }
}

/**
 * Installs the bundled example apps on first launch, and later replaces their code when a new
 * release ships rebuilt versions ([SAMPLES_VERSION]). The previous code stays in each app's version
 * history; samples the user deleted are not brought back. Samples no longer bundled stay on phones
 * that already have them.
 */
class SampleAppsInstaller(private val context: Context, private val repo: AppRepository) {

    suspend fun installIfNeeded() {
        val prefs = context.getSharedPreferences("samples", Context.MODE_PRIVATE)
        val installed = prefs.getBoolean(KEY_INSTALLED, false)
        val version = prefs.getInt(KEY_VERSION, 1)
        if (installed && version >= SAMPLES_VERSION) return
        repo.refresh()
        val assets = context.assets
        val folders = withContext(Dispatchers.IO) {
            assets.list(ASSET_DIR).orEmpty().filter { "index.html" in assets.list("$ASSET_DIR/$it").orEmpty() }
        }
        folders.forEach { folder ->
            val name = NAMES[folder] ?: AppRepository.prettyName(folder)
            if (!installed) {
                val id = repo.uniqueId(name)
                withContext(Dispatchers.IO) {
                    val dir = repo.dir(id).apply { mkdirs() }
                    assets.list("$ASSET_DIR/$folder").orEmpty().forEach { f ->
                        assets.open("$ASSET_DIR/$folder/$f").use { input -> File(dir, f).outputStream().use { input.copyTo(it) } }
                    }
                }
                repo.adopt(id, name, AppKind.SAMPLE)
            } else {
                val existing = repo.apps.value.firstOrNull { it.kind == AppKind.SAMPLE && it.name == name } ?: return@forEach
                val code = withContext(Dispatchers.IO) { assets.open("$ASSET_DIR/$folder/index.html").use { String(it.readBytes()) } }
                if (code != repo.readCode(existing.id)) repo.update(existing.id, code, UPDATE_NOTE)
            }
        }
        prefs.edit().putBoolean(KEY_INSTALLED, true).putInt(KEY_VERSION, SAMPLES_VERSION).apply()
        repo.refresh()
    }

    private companion object {
        const val ASSET_DIR = "sample_apps"
        const val KEY_INSTALLED = "installed_v1"
        const val KEY_VERSION = "samples_version"
        /** Bump when the bundled samples change so existing installs get the new code. */
        const val SAMPLES_VERSION = 2
        const val UPDATE_NOTE = "Acornix 2.0: rebuilt example (responsive, saves data)"
        val NAMES = mapOf(
            "castle defense" to "Castle Defense",
            "imc" to "IMC",
            "droneRace" to "Neon Drone Race",
            "notes" to "Notes",
            "kaleidoscope" to "Neon Kaleidoscope",
            "aquarium" to "Reef Aquarium",
            "pocketCoach" to "Pocket Coach",
            "pixelCity" to "Pixel City",
        )
    }
}

/** A shared app read from its ZIP, not installed yet. */
class ImportPreview(val name: String, internal val entries: Map<String, ByteArray>, val code: String)
