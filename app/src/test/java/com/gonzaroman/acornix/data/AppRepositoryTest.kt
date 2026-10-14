package com.gonzaroman.acornix.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun createUpdateAndRestoreVersions() = runTest {
        val repo = AppRepository(tmp.root)
        val app = repo.create("Mi Piano", "<html>v1</html>")
        assertEquals("mi_piano", app.id)
        assertEquals("🎹", app.emoji)

        repo.update(app.id, "<html>v2</html>", "louder")
        assertEquals("<html>v2</html>", repo.readCode(app.id))
        assertEquals(listOf(2, 1), repo.versions(app.id).map { it.number })

        val restored = repo.restore(app.id, 1)
        assertEquals("<html>v1</html>", repo.readCode(app.id))
        assertEquals(3, restored.versionCount)
        assertEquals("Restored version 1", repo.versions(app.id).first().note)
    }

    @Test
    fun uniqueIdsAndListing() = runTest {
        val repo = AppRepository(tmp.root)
        repo.create("Juego", "<html>a</html>")
        val second = repo.create("Juego", "<html>b</html>")
        assertEquals("juego_2", second.id)
        assertEquals(2, repo.apps.value.size)
        repo.delete(second.id)
        assertEquals(1, repo.apps.value.size)
        assertNull(repo.get("juego_2"))
    }

    @Test
    fun foldersWithoutMetadataAreStillListed() = runTest {
        val repo = AppRepository(tmp.root)
        File(repo.root, "castle defense").apply { mkdirs() }.resolve("index.html").writeText("<html></html>")
        repo.refresh()
        assertEquals("Castle Defense", repo.apps.value.single().name)
    }

    @Test
    fun slugRemovesAccents() {
        assertEquals("cancion_del_ano", AppRepository.slug("Canción del Año!"))
    }

    @Test
    fun zipRoundTripSkipsInternalFiles() = runTest {
        val repo = AppRepository(tmp.root)
        val app = repo.create("Calc", "<html>calc</html>")
        File(repo.dir(app.id), "assets").mkdirs()
        File(repo.dir(app.id), "assets/style.css").writeText("body{}")
        val zip = tmp.newFile("calc.zip")
        ImportExport.writeZip(repo.dir(app.id), zip)

        val entries = zip.inputStream().use { ImportExport.readZip(it) }
        assertEquals(setOf("index.html", "assets/style.css"), entries.keys)
        assertEquals("", ImportExport.findAppRoot(entries.keys))
    }

    @Test
    fun findsAppRootInsideSingleFolder() {
        assertEquals("piano/", ImportExport.findAppRoot(listOf("piano/index.html", "piano/a.js")))
        assertNull(ImportExport.findAppRoot(listOf("plugin/main.py")))
        assertTrue(ImportExport.findAppRoot(listOf("index.html", "x/index.html")) == "")
    }

    @Test
    fun datesAreStableAndOrderFollowsActivity() = runTest {
        val repo = AppRepository(tmp.root)
        val old = repo.create("Old", "<html>o</html>")
        // Metadata written by older versions lacked updatedAt; it must not jump to "now" on every read.
        File(repo.dir(old.id), "acornix.json").writeText("""{"id":"old","name":"Old","emoji":"🌰"}""")
        File(repo.dir(old.id), "index.html").setLastModified(1_000_000L)
        repo.refresh()
        assertEquals(1_000_000L, repo.get(old.id)!!.updatedAt)
        Thread.sleep(5)
        val fresh = repo.create("Fresh", "<html>f</html>")
        assertTrue(fresh.updatedAt > 1_000_000L)
        assertEquals(listOf("fresh", "old"), repo.apps.value.map { it.id })
        assertTrue(File(repo.dir(fresh.id), "acornix.json").readText().contains("updatedAt"))
    }
}
