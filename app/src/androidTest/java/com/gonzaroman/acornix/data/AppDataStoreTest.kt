package com.gonzaroman.acornix.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppDataStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: AppDataStore
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Before fun setUp() {
        context.deleteDatabase("test_data.db")
        store = AppDataStore(context, "test_data.db")
    }

    @After fun tearDown() {
        context.deleteDatabase("test_data.db")
        File(store.filesRoot, "t_app").deleteRecursively()
        File(store.filesRoot, "t_other").deleteRecursively()
    }

    @Test
    fun crudIsolatedPerApp() = runTest {
        val added = store.add("t_app", "tasks", obj("""{"text":"pan","done":false,"_id":"x1"}"""))
        assertEquals("x1", added["_id"]!!.jsonPrimitive.content)
        store.add("t_app", "tasks", obj("""{"text":"leche","done":true}"""))
        store.add("t_other", "tasks", obj("""{"text":"secreto"}"""))

        assertEquals(2, store.count("t_app", "tasks"))
        assertEquals(1, store.count("t_app", "tasks", obj("""{"done":false}""")))
        assertEquals(1, store.count("t_other", "tasks"))

        val updated = store.update("t_app", "tasks", "x1", obj("""{"done":true,"_created":5}"""))
        assertEquals("true", updated["done"].toString())
        assertEquals("pan", updated["text"]!!.jsonPrimitive.content)
        assertTrue(store.remove("t_app", "tasks", "x1"))
        assertNull(store.get("t_app", "tasks", "x1"))
        assertFalse(store.remove("t_app", "tasks", "x1"))
    }

    @Test
    fun keyValueFilesExportAndImport() = runTest {
        store.kvSet("t_app", "highScore", JsonPrimitive(1200))
        assertEquals("1200", store.kvGet("t_app", "highScore").toString())
        store.add("t_app", "games", obj("""{"level":3}"""))
        val f = store.saveFile("t_app", "../evil/../foto 1.jpg", byteArrayOf(1, 2, 3))
        assertEquals("foto 1.jpg", f.name)
        assertTrue(File(store.filesDir("t_app"), "foto 1.jpg").exists())

        val summary = store.summary("t_app")
        assertEquals(1, summary.collections); assertEquals(1, summary.documents); assertEquals(1, summary.keys); assertEquals(1, summary.files)
        assertTrue(store.describeForAi("t_app")!!.contains("collection \"games\""))

        val export = store.export("t_app")
        store.import("t_other", export, replace = true)
        assertEquals(1, store.count("t_other", "games"))
        assertEquals("1200", store.kvGet("t_other", "highScore").toString())

        store.deleteApp("t_app")
        assertFalse(store.hasData("t_app"))
        assertTrue(store.hasData("t_other"))
    }

    @Test
    fun fullBackupRoundTrip() = runTest {
        val root = File(context.cacheDir, "backup_test").apply { deleteRecursively(); mkdirs() }
        val repo = AppRepository(root) { store.deleteApp(it) }
        val app = repo.create("Lista Test", "<html>v1</html>")
        repo.update(app.id, "<html>v2</html>", "change")
        store.add(app.id, "items", obj("""{"name":"pan"}"""))
        store.saveFile(app.id, "a.png", byteArrayOf(9))
        val backups = BackupManager(context, repo, store)

        val out = ByteArrayOutputStream()
        assertEquals(1, backups.write(out).apps)

        repo.delete(app.id)
        assertFalse(store.hasData(app.id))

        assertEquals(1, backups.read(ByteArrayInputStream(out.toByteArray())).apps)
        repo.refresh()
        assertEquals("<html>v2</html>", repo.readCode(app.id))
        assertEquals(2, repo.versions(app.id).size)
        assertEquals(1, store.count(app.id, "items"))
        assertEquals(1, store.listFiles(app.id).size)
        store.deleteApp(app.id)
        root.deleteRecursively()
    }

    @Test
    fun appZipCarriesDataOnlyWhenAsked() = runTest {
        val root = File(context.cacheDir, "zip_test").apply { deleteRecursively(); mkdirs() }
        val repo = AppRepository(root) { store.deleteApp(it) }
        val io = ImportExport(context, repo, store)
        val app = repo.create("Juego Zip", "<!DOCTYPE html><html><body>game</body></html>")
        store.kvSet(app.id, "level", JsonPrimitive(7))
        store.saveFile(app.id, "save.png", byteArrayOf(1))

        val clean = io.export(app.id, includeData = false).inputStream().use { ImportExport.readZip(it) }
        assertEquals(setOf("index.html"), clean.keys)

        val full = io.export(app.id, includeData = true).inputStream().use { ImportExport.readZip(it) }
        assertTrue(ImportExport.DATA_FILE in full.keys)
        assertTrue("${ImportExport.DATA_FILES_DIR}/save.png" in full.keys)

        val imported = io.importEntries("Juego Copia", full)
        assertEquals("7", store.kvGet(imported.id, "level").toString())
        assertEquals(1, store.listFiles(imported.id).size)
        // Data travels in the database, never as files inside the app folder.
        assertFalse(File(repo.dir(imported.id), ImportExport.DATA_FILE).exists())

        store.deleteApp(app.id); store.deleteApp(imported.id)
        root.deleteRecursively()
    }
}
