package com.gonzaroman.acornix.core

import com.gonzaroman.acornix.ui.shortcuts.shortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShortcutTemplatesTest {
    private val builtIn = setOf("hora", "fecha", "ubicación", "batería", "portapapeles", "entrada", "repetición",
        "time", "date", "location", "battery", "clipboard", "input", "repeat")

    @Test
    fun galleryVersionsSpeakBothLanguages() {
        val es = ShortcutTemplates.all(true)
        ShortcutTemplates.all(false).forEach { en ->
            val bilingual = en.recipe.copy(translations = mapOf("es" to es.first { it.id == en.id }.recipe.steps))
            val html = bilingual.html(en.name, en.emoji)
            assertEquals(bilingual, ShortcutRecipe.fromHtml(html))
            assertEquals(es.first { it.id == en.id }.recipe.steps, bilingual.stepsFor("es"))
            assertEquals(en.recipe.steps, bilingual.stepsFor("fr"))
            // Kept in app/build/shortcut-gallery for the community gallery.
            File("build/shortcut-gallery").apply { mkdirs() }.resolve("${en.id}.html").writeText(html)
        }
    }

    @Test
    fun everyExampleIsWellFormed() {
        listOf(true, false).forEach { spanish ->
            val all = ShortcutTemplates.all(spanish)
            assertEquals(23, all.size)
            all.forEach { t ->
                t.recipe.steps.forEach { assertNotNull("${t.id}: unknown action ${it.action}", shortcutAction(it.action)) }
                // Blocks close: the nesting after the last step is back to zero.
                val opens = t.recipe.steps.count { it.action == "if" || it.action == "repeat" }
                val closes = t.recipe.steps.count { it.action == "endif" || it.action == "endrepeat" }
                assertEquals(t.id, opens, closes)
                // Every {variable} is built in or set by an earlier step.
                t.recipe.steps.forEachIndexed { i, step ->
                    val known = builtIn + t.recipe.variablesBefore(i)
                    step.params.values.forEach { v ->
                        Regex("""\{([^{}]+)\}""").findAll(v).forEach { m ->
                            assertTrue("${t.id}: {${m.groupValues[1]}} before it exists", m.groupValues[1] in known)
                        }
                    }
                }
                assertEquals(t.recipe, ShortcutRecipe.fromHtml(t.recipe.html(t.name, t.emoji)))
                // Kept in app/build/shortcut-templates to try them in a browser.
                File("build/shortcut-templates").apply { mkdirs() }
                    .resolve("${if (spanish) "es" else "en"}-${t.id}.html").writeText(t.recipe.html(t.name, t.emoji))
            }
        }
    }
}
