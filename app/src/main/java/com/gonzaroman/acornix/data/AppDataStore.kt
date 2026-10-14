package com.gonzaroman.acornix.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

data class CollectionInfo(val name: String, val count: Int, val fields: String)

data class StoredFile(val name: String, val size: Long, val file: File)

/** Who changed the data; open apps reload when someone else (e.g. the assistant) did. */
data class DataChange(val appId: String, val collection: String?, val source: String)

class DataException(message: String) : Exception(message)

/**
 * The per-app database behind `window.Acornix.db` and `window.Acornix.files`.
 *
 * Documents are JSON objects stored in one SQLite table keyed by (app, collection, id). Every app
 * only ever sees its own rows. Small key-value pairs live in the reserved collection [KV].
 * Files go to `filesDir/app_data/<appId>/files`.
 */
class AppDataStore(context: Context, dbName: String = DB_NAME) {

    private val helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE docs (
                    app TEXT NOT NULL,
                    col TEXT NOT NULL,
                    id TEXT NOT NULL,
                    data TEXT NOT NULL,
                    created INTEGER NOT NULL,
                    updated INTEGER NOT NULL,
                    PRIMARY KEY (app, col, id)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX docs_by_created ON docs (app, col, created)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val json = Json { ignoreUnknownKeys = true }
    val filesRoot = File(context.filesDir, FILES_DIR)

    private val _changes = MutableSharedFlow<DataChange>(extraBufferCapacity = 32)
    val changes: SharedFlow<DataChange> = _changes.asSharedFlow()

    private val db get() = helper.writableDatabase

    // ---------- Documents ----------

    suspend fun add(app: String, col: String, doc: JsonObject, source: String = SOURCE_APP): JsonObject = io {
        checkName(col)
        val id = (doc["_id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: newId()
        val now = System.currentTimeMillis()
        val body = strip(doc)
        db.insertWithOnConflict("docs", null, values(app, col, id, body, now, now), SQLiteDatabase.CONFLICT_REPLACE)
        notify(app, col, source)
        withMeta(body, id, now, now)
    }

    suspend fun get(app: String, col: String, id: String): JsonObject? = io {
        db.query("docs", COLUMNS, "app=? AND col=? AND id=?", arrayOf(app, col, id), null, null, null).use { c ->
            if (c.moveToFirst()) readRow(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3)) else null
        }
    }

    /** Creates or replaces the document [id]. */
    suspend fun set(app: String, col: String, id: String, doc: JsonObject, source: String = SOURCE_APP): JsonObject {
        checkName(col)
        return write(app, col, id, doc, source)
    }

    /** Upsert without the collection-name check, so the reserved key-value collection can be used. */
    private suspend fun write(app: String, col: String, id: String, doc: JsonObject, source: String): JsonObject = io {
        val now = System.currentTimeMillis()
        val created = createdAt(app, col, id) ?: now
        val body = strip(doc)
        db.insertWithOnConflict("docs", null, values(app, col, id, body, created, now), SQLiteDatabase.CONFLICT_REPLACE)
        notify(app, col, source)
        withMeta(body, id, created, now)
    }

    /** Merges [patch] into the top-level fields of an existing document. */
    suspend fun update(app: String, col: String, id: String, patch: JsonObject, source: String = SOURCE_APP): JsonObject = io {
        val current = get(app, col, id) ?: throw DataException("Document '$id' not found in '$col'")
        val merged = JsonObject(strip(current) + strip(patch))
        val now = System.currentTimeMillis()
        val created = (current["_created"] as? JsonPrimitive)?.content?.toLongOrNull() ?: now
        db.insertWithOnConflict("docs", null, values(app, col, id, merged, created, now), SQLiteDatabase.CONFLICT_REPLACE)
        notify(app, col, source)
        withMeta(merged, id, created, now)
    }

    suspend fun remove(app: String, col: String, id: String, source: String = SOURCE_APP): Boolean = io {
        val n = db.delete("docs", "app=? AND col=? AND id=?", arrayOf(app, col, id))
        if (n > 0) notify(app, col, source)
        n > 0
    }

    suspend fun list(app: String, col: String, query: DocQuery = DocQuery()): List<JsonObject> = io {
        DocQueryEngine.apply(all(app, col), query)
    }

    suspend fun count(app: String, col: String, where: JsonObject? = null): Int = io {
        if (where == null) {
            db.rawQuery("SELECT COUNT(*) FROM docs WHERE app=? AND col=?", arrayOf(app, col)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        } else {
            all(app, col).count { DocQueryEngine.matches(it, where) }
        }
    }

    suspend fun clear(app: String, col: String?, source: String = SOURCE_APP) = io {
        if (col == null) db.delete("docs", "app=?", arrayOf(app)) else db.delete("docs", "app=? AND col=?", arrayOf(app, col))
        notify(app, col, source)
    }

    suspend fun collections(app: String): List<CollectionInfo> = io {
        val names = mutableListOf<Pair<String, Int>>()
        db.rawQuery("SELECT col, COUNT(*) FROM docs WHERE app=? AND col<>? GROUP BY col ORDER BY col", arrayOf(app, KV)).use { c ->
            while (c.moveToNext()) names += c.getString(0) to c.getInt(1)
        }
        names.map { (name, count) -> CollectionInfo(name, count, DocQueryEngine.describeFields(all(app, name, limit = 50))) }
    }

    // ---------- Key-value ----------

    suspend fun kvGet(app: String, key: String): JsonElement? = get(app, KV, key)?.get("value")

    suspend fun kvSet(app: String, key: String, value: JsonElement, source: String = SOURCE_APP) {
        write(app, KV, key, buildJsonObject { put("value", value) }, source)
    }

    suspend fun kvRemove(app: String, key: String, source: String = SOURCE_APP) = remove(app, KV, key, source)

    suspend fun kvAll(app: String): Map<String, JsonElement> = io {
        all(app, KV).associate { (it["_id"] as JsonPrimitive).content to (it["value"] ?: JsonPrimitive("")) }
    }

    // ---------- Files ----------

    fun filesDir(app: String) = File(filesRoot, "$app/files")

    suspend fun saveFile(app: String, name: String, bytes: ByteArray, source: String = SOURCE_APP): StoredFile = io {
        if (bytes.size > MAX_FILE_BYTES) throw DataException("File too large (max ${MAX_FILE_BYTES / 1024 / 1024} MB)")
        val clean = cleanFileName(name)
        val file = File(filesDir(app).apply { mkdirs() }, clean)
        file.writeBytes(bytes)
        notify(app, null, source)
        StoredFile(clean, file.length(), file)
    }

    suspend fun listFiles(app: String): List<StoredFile> = io {
        filesDir(app).listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.lastModified() }
            .map { StoredFile(it.name, it.length(), it) }
    }

    suspend fun deleteFile(app: String, name: String, source: String = SOURCE_APP): Boolean = io {
        val ok = File(filesDir(app), cleanFileName(name)).delete()
        if (ok) notify(app, null, source)
        ok
    }

    // ---------- Whole-app operations ----------

    suspend fun hasData(app: String): Boolean = io {
        val rows = db.rawQuery("SELECT 1 FROM docs WHERE app=? LIMIT 1", arrayOf(app)).use { it.moveToFirst() }
        rows || filesDir(app).listFiles().orEmpty().isNotEmpty()
    }

    data class Summary(val collections: Int, val documents: Int, val keys: Int, val files: Int, val fileBytes: Long)

    suspend fun summary(app: String): Summary = io {
        var cols = 0
        var docs = 0
        var keys = 0
        db.rawQuery("SELECT col, COUNT(*) FROM docs WHERE app=? GROUP BY col", arrayOf(app)).use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == KV) keys = c.getInt(1) else { cols++; docs += c.getInt(1) }
            }
        }
        val files = filesDir(app).listFiles().orEmpty().filter { it.isFile }
        Summary(cols, docs, keys, files.size, files.sumOf { it.length() })
    }

    /** All documents and keys of an app as one JSON object (files are not included). */
    suspend fun export(app: String): JsonObject = io {
        val byCol = linkedMapOf<String, MutableList<JsonObject>>()
        db.query("docs", arrayOf("col", "data", "id", "created", "updated"), "app=?", arrayOf(app), null, null, "col, created").use { c ->
            while (c.moveToNext()) byCol.getOrPut(c.getString(0)) { mutableListOf() } += readRow(c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
        }
        buildJsonObject {
            put("format", "acornix-data")
            put("version", 1)
            put("collections", JsonObject(byCol.filterKeys { it != KV }.mapValues { JsonArray(it.value) }))
            put("keys", JsonObject(byCol[KV].orEmpty().associate { (it["_id"] as JsonPrimitive).content to (it["value"] ?: JsonPrimitive("")) }))
        }
    }

    /** Loads an [export] into [app]; with [replace] the app's current documents are removed first. */
    suspend fun import(app: String, data: JsonObject, replace: Boolean, source: String = SOURCE_APP) = io {
        val database = db
        database.beginTransaction()
        try {
            if (replace) database.delete("docs", "app=?", arrayOf(app))
            val now = System.currentTimeMillis()
            (data["collections"] as? JsonObject)?.forEach { (col, docs) ->
                (docs as? JsonArray)?.forEach { el ->
                    val doc = el as? JsonObject ?: return@forEach
                    val id = (doc["_id"] as? JsonPrimitive)?.content ?: newId()
                    val created = (doc["_created"] as? JsonPrimitive)?.content?.toLongOrNull() ?: now
                    val updated = (doc["_updated"] as? JsonPrimitive)?.content?.toLongOrNull() ?: now
                    database.insertWithOnConflict("docs", null, values(app, col, id, strip(doc), created, updated), SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            (data["keys"] as? JsonObject)?.forEach { (key, value) ->
                database.insertWithOnConflict("docs", null, values(app, KV, key, buildJsonObject { put("value", value) }, now, now), SQLiteDatabase.CONFLICT_REPLACE)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        notify(app, null, source)
    }

    suspend fun deleteApp(app: String) = io {
        db.delete("docs", "app=?", arrayOf(app))
        File(filesRoot, app).deleteRecursively()
        notify(app, null, SOURCE_SYSTEM)
    }

    /** Text for the AI so improvements keep using the same collections and fields. */
    suspend fun describeForAi(app: String): String? {
        val cols = collections(app)
        val keys = kvAll(app).keys
        val files = listFiles(app)
        if (cols.isEmpty() && keys.isEmpty() && files.isEmpty()) return null
        return buildString {
            cols.forEach { appendLine("- collection \"${it.name}\": ${it.count} documents, fields: ${it.fields.ifBlank { "(none)" }}") }
            if (keys.isNotEmpty()) appendLine("- key-value keys: ${keys.joinToString(", ")}")
            if (files.isNotEmpty()) appendLine("- ${files.size} stored files (Acornix.files)")
        }.trim()
    }

    // ---------- Internals ----------

    private fun all(app: String, col: String, limit: Int? = null): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        db.query("docs", COLUMNS, "app=? AND col=?", arrayOf(app, col), null, null, "created", limit?.toString()).use { c ->
            while (c.moveToNext()) out += readRow(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3))
        }
        return out
    }

    private fun createdAt(app: String, col: String, id: String): Long? =
        db.rawQuery("SELECT created FROM docs WHERE app=? AND col=? AND id=?", arrayOf(app, col, id)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    private fun readRow(data: String, id: String, created: Long, updated: Long): JsonObject {
        val body = runCatching { json.parseToJsonElement(data).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        return withMeta(body, id, created, updated)
    }

    private fun values(app: String, col: String, id: String, body: JsonObject, created: Long, updated: Long): ContentValues {
        val text = body.toString()
        if (text.length > MAX_DOC_CHARS) throw DataException("Document too large (max ${MAX_DOC_CHARS / 1024} KB); store big content with Acornix.files")
        return ContentValues().apply {
            put("app", app); put("col", col); put("id", id); put("data", text)
            put("created", created); put("updated", updated)
        }
    }

    private fun notify(app: String, col: String?, source: String) {
        _changes.tryEmit(DataChange(app, col, source))
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val DB_NAME = "app_data.db"
        const val FILES_DIR = "app_data"
        const val KV = "__kv"
        const val SOURCE_APP = "app"
        const val SOURCE_ASSISTANT = "assistant"
        const val SOURCE_SYSTEM = "system"
        private const val MAX_DOC_CHARS = 1_000_000
        private const val MAX_FILE_BYTES = 25 * 1024 * 1024
        private val COLUMNS = arrayOf("data", "id", "created", "updated")

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

        /** Fields starting with "_" are reserved for the store's own metadata. */
        fun strip(doc: JsonObject) = JsonObject(doc.filterKeys { !it.startsWith("_") })

        fun withMeta(body: JsonObject, id: String, created: Long, updated: Long) = JsonObject(
            linkedMapOf<String, JsonElement>(
                "_id" to JsonPrimitive(id),
                "_created" to JsonPrimitive(created),
                "_updated" to JsonPrimitive(updated),
            ) + body,
        )

        fun checkName(col: String) {
            if (col.isBlank() || col.length > 100 || col == KV) throw DataException("Invalid collection name '$col'")
        }

        fun cleanFileName(name: String): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
            val clean = base.replace(Regex("[^A-Za-z0-9._ -]"), "_").trimStart('.').take(120)
            return clean.ifBlank { "file_${newId()}" }
        }
    }
}
