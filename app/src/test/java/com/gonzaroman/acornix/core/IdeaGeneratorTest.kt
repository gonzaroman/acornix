package com.gonzaroman.acornix.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaGeneratorTest {
    @Test
    fun parsesAFencedArrayAndCleansIt() {
        val text = """
            Here you go:
            ```json
            [{"emoji": "🎹", "title": "Tilt theremin.", "prompt": "A theremin that sounds as you tilt the phone."},
             {"emoji": "", "title": "Mood sky", "prompt": "A diary that paints your month as a sky.", "extra": 1}]
            ```
        """.trimIndent()
        val ideas = IdeaGenerator.parse(text)
        assertEquals(2, ideas.size)
        assertEquals("Tilt theremin", ideas[0].title)
        assertEquals("✨", ideas[1].emoji)
    }

    @Test
    fun dropsIncompleteIdeasAndToleratesGarbage() {
        assertTrue(IdeaGenerator.parse("no json here").isEmpty())
        assertTrue(IdeaGenerator.parse("[{\"title\": \"Only a title\", \"prompt\": \"\"}]").isEmpty())
    }

    @Test
    fun partialAnswerYieldsOnlyFinishedIdeas() {
        val half = """[{"emoji": "🎹", "title": "Tilt {theremin}", "prompt": "Tilt to play \"notes\"."},
            {"emoji": "🌙", "title": "Moon", "prompt": "A moon that"""
        val ideas = IdeaGenerator.parsePartial(half)
        assertEquals(1, ideas.size)
        assertEquals("Tilt {theremin}", ideas[0].title) // braces and quotes inside strings don't confuse it
        assertTrue(IdeaGenerator.parsePartial("Sure! Here are").isEmpty())
        assertEquals(2, IdeaGenerator.parsePartial(half + " glows.\"}]").size)
    }

    @Test
    fun requestNamesTheCategoryAndWhatWasAlreadyShown() {
        val r = IdeaGenerator.request("Music", listOf("Tilt theremin"))
        assertTrue(r.contains("\"Music\""))
        assertTrue(r.contains("Tilt theremin"))
    }

    @Test
    fun cacheKeySeparatesKindCategoryAndLanguage() {
        assertEquals("app||Spanish", IdeaGenerator.key(false, null, "Spanish"))
        assertEquals("feature|Music|English", IdeaGenerator.key(true, "Music", "English"))
    }
}
