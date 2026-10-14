package com.gonzaroman.acornix.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : Exception(message)

/**
 * Full backup of every app: code, version history, database and files, in a single ZIP. The user
 * picks where it goes through the system file picker, so it can be saved straight to Google Drive.
 *
 * Layout: `acornix-backup.json`, `apps/<id>/…`, `data/<id>.json`, `files/<id>/<name>`.
 */
class BackupManager(
    private val context: Context,
    private val repo: AppRepository,
    private val data: AppDataStore,
) {
    data class Result(val apps: Int)

    suspend fun create(target: Uri): Result {
        val out = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(target) }
            ?: throw BackupException("Cannot write to the selected location")
        return out.use { write(it) }
    }

    suspend fun restore(source: Uri): Result {
        val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(source) }
            ?: throw BackupException("Cannot read the selected file")
        return input.use { read(it) }.also { repo.refresh() }
    }

    internal suspend fun write(out: OutputStream): Result {
        val ids = withContext(Dispatchers.IO) {
            repo.root.listFiles { f -> f.isDirectory && File(f, AppRepository.INDEX).exists() }.orEmpty().map { it.name }.sorted()
        }
        val exports = ids.associateWith { id -> if (data.hasData(id)) data.export(id) else null }
        withContext(Dispatchers.IO) {
            ZipOutputStream(out.buffered()).use { zos ->
                fun entry(path: String, bytes: ByteArray) {
                    zos.putNextEntry(ZipEntry(path)); zos.write(bytes); zos.closeEntry()
                }
                fun entry(path: String, file: File) {
                    zos.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
                }
                entry(MANIFEST, buildJsonObject {
                    put("format", "acornix-backup")
                    put("version", 1)
                    put("created", System.currentTimeMillis())
                    put("apps", JsonArray(ids.map { JsonPrimitive(it) }))
                }.toString().toByteArray())
                ids.forEach { id ->
                    val dir = repo.dir(id)
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        entry("apps/$id/${f.relativeTo(dir).invariantSeparatorsPath}", f)
                    }
                    exports[id]?.let { entry("data/$id.json", it.toString().toByteArray()) }
                    data.filesDir(id).listFiles().orEmpty().filter { it.isFile }.forEach { f -> entry("files/$id/${f.name}", f) }
                }
            }
        }
        return Result(ids.size)
    }

    /** Restores every app in the backup, replacing apps with the same id. Others are left alone. */
    internal suspend fun read(input: InputStream): Result {
        val json = Json { ignoreUnknownKeys = true }
        val prepared = mutableSetOf<String>()
        val pendingData = mutableMapOf<String, JsonObject>()
        val pendingFiles = mutableListOf<Triple<String, String, ByteArray>>()
        var sawManifest = false
        val root = repo.root.canonicalPath + File.separator

        withContext(Dispatchers.IO) {
            ZipInputStream(input.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    if (entry.isDirectory) { zis.closeEntry(); continue }
                    when {
                        path == MANIFEST -> {
                            val manifest = runCatching { json.parseToJsonElement(String(zis.readBytes())).jsonObject }.getOrNull()
                            if ((manifest?.get("format") as? JsonPrimitive)?.content != "acornix-backup") throw BackupException("Not an Acornix backup")
                            sawManifest = true
                        }
                        path.startsWith("apps/") -> {
                            val id = path.removePrefix("apps/").substringBefore('/')
                            val relative = path.removePrefix("apps/$id/")
                            if (!validId(id) || relative.isEmpty()) { zis.closeEntry(); continue }
                            if (prepared.add(id)) repo.dir(id).deleteRecursively()
                            val out = File(repo.dir(id), relative)
                            // Zip-slip protection.
                            if (out.canonicalPath.startsWith(root)) {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zis.copyTo(it) }
                            }
                        }
                        path.startsWith("data/") && path.endsWith(".json") -> {
                            val id = path.removePrefix("data/").removeSuffix(".json")
                            if (validId(id)) {
                                runCatching { json.parseToJsonElement(String(zis.readBytes())).jsonObject }.getOrNull()?.let { pendingData[id] = it }
                            }
                        }
                        path.startsWith("files/") -> {
                            val id = path.removePrefix("files/").substringBefore('/')
                            if (validId(id)) pendingFiles += Triple(id, path.substringAfterLast('/'), zis.readBytes())
                        }
                    }
                    zis.closeEntry()
                }
            }
        }
        if (!sawManifest) throw BackupException("Not an Acornix backup")
        // Data is replaced only for apps that came in the backup.
        prepared.forEach { id -> data.deleteApp(id) }
        pendingData.filterKeys { it in prepared }.forEach { (id, export) -> data.import(id, export, replace = true, source = AppDataStore.SOURCE_SYSTEM) }
        pendingFiles.filter { it.first in prepared }.forEach { (id, name, bytes) -> data.saveFile(id, name, bytes, AppDataStore.SOURCE_SYSTEM) }
        return Result(prepared.size)
    }

    private fun validId(id: String) = id.isNotBlank() && id != "." && id != ".." && !id.contains('/') && !id.contains('\\')

    companion object {
        const val MANIFEST = "acornix-backup.json"
    }
}
