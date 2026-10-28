package com.gonzaroman.acornix.core

import com.gonzaroman.acornix.data.AppRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReviewTest {
    @Test
    fun readsTheVerdictEvenWhenWrapped() {
        val r = AppReview.parse("Here you go:\n```json\n{\"verdict\":\"DANGER\",\"summary\":\"Sends your notes to a server\",\"findings\":[\" fetch to evil.example \",\"\"]}\n```")!!
        assertEquals("danger", r.verdict)
        assertEquals(listOf("fetch to evil.example"), r.findings)
        assertEquals("caution", AppReview.parse("""{"verdict":"maybe","summary":"?"}""")!!.verdict) // unknown → caution
        assertNull(AppReview.parse("I can't review this"))
    }

    @Test
    fun longCodeIsCut() {
        val p = AppReview.prompt("Big", "x".repeat(AppReview.MAX_CODE_CHARS + 10))
        assertTrue(p.endsWith("[… the rest of the code was cut]"))
    }

    @Test
    fun detectsAppsThatUseTheAi() {
        assertTrue(AppRepository.usesAi("const r = await Acornix.askAI('hi')"))
        assertTrue(AppRepository.usesAi("const chat = window.Acornix.ai.chat({})"))
        assertFalse(AppRepository.usesAi("const tasks = Acornix.db.collection('askAIdeas')"))
        assertFalse(AppRepository.usesAi("<h1>Calculator</h1>"))
    }
}
