package com.gonzaroman.acornix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryTest {
    private val catalog = Gallery.parse(
        """
        {"version": 1, "future": "ignored", "apps": [
          {"id": "notes", "name": "Notes", "category": "tools", "description": "Checklists and pinned notes",
           "prompt": "A professional notes app", "author": "gonzaroman", "added": "2026-10-28", "featured": true},
          {"id": "chef", "name": "Pocket Chef", "category": "tools", "description": "Recipes from a photo of your fridge",
           "author": "maria", "added": "2026-11-02", "tags": ["ai", "camera"], "version": 3},
          {"id": "bowling", "name": "Bowling", "category": "games", "description": "Swipe to throw",
           "author": "gonzaroman", "added": "2026-10-20", "tags": ["phone"]}
        ]}
        """.trimIndent(),
    )

    @Test
    fun readsTheCatalogWithDefaults() {
        val chef = catalog.apps.first { it.id == "chef" }
        assertEquals(3, chef.version)
        assertEquals(listOf("index.html"), chef.files)
        assertEquals("🌰", chef.emoji)
        assertEquals("", chef.sha256)
    }

    @Test
    fun featuredFirstThenNewest() {
        assertEquals(listOf("notes", "chef", "bowling"), Gallery.sorted(catalog.apps).map { it.id })
    }

    @Test
    fun searchAndFilters() {
        assertEquals(listOf("chef"), Gallery.search(catalog.apps, "", "ai").map { it.id })
        assertEquals(listOf("notes", "chef"), Gallery.search(catalog.apps, "", "tools").map { it.id })
        assertEquals(listOf("chef"), Gallery.search(catalog.apps, "FRIDGE photo", null).map { it.id })
        assertEquals(listOf("notes", "bowling"), Gallery.search(catalog.apps, "gonzaroman", null).map { it.id })
        assertTrue(Gallery.search(catalog.apps, "spaceship", null).isEmpty())
    }

    @Test
    fun phoneAppsInstallAsFunctionalities() {
        assertEquals(AppKind.FEATURE, Gallery.kindOf(catalog.apps.first { it.id == "bowling" }))
        assertEquals(AppKind.APP, Gallery.kindOf(catalog.apps.first { it.id == "chef" }))
    }

    @Test
    fun sha256MatchesTheCatalogScript() {
        // python3 -c "import hashlib; print(hashlib.sha256(b'<html></html>').hexdigest())"
        assertEquals("b633a587c652d02386c4f16f8c6f6aab7352d97f16367c3c40576214372dd628", Gallery.sha256("<html></html>".toByteArray()))
    }
}
