package com.gonzaroman.acornix.core

import android.content.ContextWrapper
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShortcutSamplesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val examples = listOf("night", "synthwave", "lowbattery", "wakeup", "driving", "morning", "markets", "acornixvideos")

    @Test
    fun examplesFollowTheLanguageUnlessChanged() = runTest {
        val repo = AppRepository(tmp.root)
        examples.mapNotNull { ShortcutTemplates.find(it, true) }.forEach { t ->
            repo.create(t.name, t.recipe.html(t.name, t.emoji), AppKind.FEATURE, description = t.description, emoji = t.emoji)
        }
        // One the user edited stays as it is.
        val edited = repo.create("Mi atajo", ShortcutTemplates.find("cinema", true)!!.recipe.html("Mi atajo", "🎬"), AppKind.FEATURE)
        val samples = ShortcutSamples(ContextWrapper(null), repo)

        samples.relocalize(spanish = false)
        val english = examples.map { ShortcutTemplates.find(it, false)!!.name }.sorted()
        assertEquals(english, repo.apps.value.filter { it.id != edited.id }.map { it.name }.sorted())
        assertEquals("Mi atajo", repo.get(edited.id)?.name)
        val night = repo.apps.value.first { it.name == "Night mode" }
        assertEquals(ShortcutTemplates.find("night", false)!!.recipe.steps, ShortcutRecipe.fromHtml(repo.readCode(night.id))!!.steps)

        samples.relocalize(spanish = true)
        val spanish = examples.map { ShortcutTemplates.find(it, true)!!.name }.sorted()
        assertEquals(spanish, repo.apps.value.filter { it.id != edited.id }.map { it.name }.sorted())
    }
}
