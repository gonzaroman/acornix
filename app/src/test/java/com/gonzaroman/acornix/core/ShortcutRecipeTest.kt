package com.gonzaroman.acornix.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutRecipeTest {
    private val recipe = ShortcutRecipe(steps = listOf(
        ShortcutStep("dnd", mapOf("on" to "on")),
        ShortcutStep("whatsapp", mapOf("number" to "+34600000000", "text" to "Llegué a las {hora} </script><b>")),
    ))

    @Test
    fun theRecipeTravelsInsideTheHtml() {
        val html = recipe.html("He llegado", "🏠")
        assertEquals(recipe, ShortcutRecipe.fromHtml(html))
        assertTrue(html.contains("<title>🏠 He llegado</title>"))
        // A step's text can't close the script tag early.
        assertEquals(1, Regex("</script>").findAll(html.substringAfter("id=\"acornix-recipe\">").substringBefore("<script>")).count())
    }

    @Test
    fun namesAreEscaped() {
        val html = recipe.html("<img onerror=x>", "⚡")
        assertFalse(html.contains("<img onerror"))
    }

    @Test
    fun otherAppsHaveNoRecipe() {
        assertNull(ShortcutRecipe.fromHtml("<html><script>Acornix.ringer('silent')</script></html>"))
    }
}
