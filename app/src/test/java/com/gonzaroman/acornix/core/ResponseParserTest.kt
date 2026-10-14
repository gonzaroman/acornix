package com.gonzaroman.acornix.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

    private val html = "<!DOCTYPE html>\n<html><head><title>T</title></head><body><h1>Hi</h1></body></html>"

    @Test
    fun parsesOriginalAcornixFormat() {
        val r = ResponseParser.parse("---CODIGO---\n$html\n---SUGERENCIA---\nAdd a dark mode.")
        assertEquals(html, r.code)
        assertEquals("Add a dark mode.", r.suggestion)
    }

    @Test
    fun stripsMarkdownFencesInsideCodeSection() {
        val r = ResponseParser.parse("---CODIGO---\n```html\n$html\n```\n---SUGERENCIA---\n\"Add sounds\"")
        assertEquals(html, r.code)
        assertEquals("Add sounds", r.suggestion)
    }

    @Test
    fun acceptsPlainFencedResponseWithoutMarkers() {
        val r = ResponseParser.parse("Here is your app:\n```html\n$html\n```\nEnjoy!")
        assertEquals(html, r.code)
        assertNull(r.suggestion)
    }

    @Test
    fun trimsChatterAroundRawHtml() {
        val r = ResponseParser.parse("Sure! $html Hope you like it")
        assertEquals(html, r.code)
    }

    @Test
    fun englishMarkersWork() {
        val r = ResponseParser.parse("---CODE---\n$html\n---SUGGESTION---\nMore levels")
        assertEquals(html, r.code)
        assertEquals("More levels", r.suggestion)
    }

    @Test
    fun noCodeReturnsNull() {
        assertNull(ResponseParser.parse("I cannot do that.").code)
    }

    @Test
    fun streamingCodeShowsPartialFile() {
        val partial = "---CODIGO---\n```html\n<!DOCTYPE html>\n<html><bo"
        assertEquals("<!DOCTYPE html>\n<html><bo", ResponseParser.streamingCode(partial))
        assertFalse(ResponseParser.codeFinished(partial))
        assertTrue(ResponseParser.codeFinished("$partial</html>\n---SUGERENCIA---\nx"))
    }

    @Test
    fun suggestNameDropsFillerWords() {
        assertEquals("Piano Móvil", AppGenerator.suggestName("crea un piano para móvil"))
        assertEquals("Space Shooter", AppGenerator.suggestName("Make me a space shooter game"))
        assertEquals("Habit Tracker", AppGenerator.suggestName("A habit tracker with daily streaks and a weekly chart"))
        assertEquals("Memory Card", AppGenerator.suggestName("A memory card game with emoji pairs"))
        assertEquals("Lista Compra", AppGenerator.suggestName("una lista de la compra con categorías"))
    }

    @Test
    fun joinsFileSplitAcrossSeveralCodeBlocks() {
        val text = "```html\n<!DOCTYPE html>\n<html><head><style>body{}</style></head>\n```\nand then\n```html\n<body><p>x</p></body></html>\n```"
        val r = ResponseParser.parse(text)
        assertEquals("<!DOCTYPE html>\n<html><head><style>body{}</style></head>\n<body><p>x</p></body></html>", r.code)
        assertFalse(r.repaired)
        assertTrue(ResponseParser.looksComplete(r.code!!))
    }

    @Test
    fun repairsFragmentWhoseTagsWereEatenByTheChatUi() {
        // Shape of a Gemini answer copied after the web UI swallowed <!DOCTYPE>, <head> and <style>.
        val text = "```\n    body { color: red; }\n</style>\n```\n\n```\n    <canvas id=\"c\"></canvas>\n<script>\n  const ok = a\n    || b;\n</script>\n```"
        val r = ResponseParser.parse(text)
        assertTrue(r.repaired)
        val code = r.code!!
        assertTrue(code.startsWith("<!DOCTYPE html>"))
        assertTrue(code.contains("<style>\nbody { color: red; }\n</style>"))
        assertTrue("JS lines starting with || must survive", code.contains("\n    || b;"))
        assertTrue(code.trimEnd().endsWith("</html>"))
    }

    @Test
    fun looksCompleteDetectsMissingPieces() {
        assertTrue(ResponseParser.looksComplete(html))
        assertFalse(ResponseParser.looksComplete("<html><body><script>x()</body></html>"))
        assertFalse(ResponseParser.looksComplete("<!DOCTYPE html><html><body>"))
    }
}
