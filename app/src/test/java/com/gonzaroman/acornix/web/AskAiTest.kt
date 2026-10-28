package com.gonzaroman.acornix.web

import com.gonzaroman.acornix.ai.AiJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAiTest {
    private fun opts(json: String?) = AskAiOptions.from(json?.let { AiJson.parseToJsonElement(it) })

    @Test
    fun theOldFormIsJustTheSystemPrompt() {
        val o = AskAiOptions.from(JsonPrimitive("You are a pirate"))
        assertEquals("You are a pirate", o.system)
        assertTrue(o.images.isEmpty())
        assertFalse(o.json)
        assertTrue(o.quick)
        assertEquals(AskAi.DEFAULT_SYSTEM, AskAi.system(opts(null)))
    }

    @Test
    fun optionsObject() {
        val o = opts("""{"system":"Chef","json":true,"quick":false,"images":["data:image/jpeg;base64,QUJD","https://x/y.png",5]}""")
        assertEquals("Chef", o.system)
        assertTrue(o.json)
        assertFalse(o.quick)
        assertEquals(listOf("data:image/jpeg;base64,QUJD"), o.images) // only data:image URLs are accepted
        assertTrue(AskAi.system(o).startsWith("Chef") && AskAi.system(o).contains("ONLY with valid JSON"))
    }

    @Test
    fun atMostFourImages() {
        val urls = (1..6).joinToString(",") { "\"data:image/png;base64,QQ==\"" }
        assertEquals(AskAiOptions.MAX_IMAGES, opts("""{"images":[$urls]}""").images.size)
    }

    @Test
    fun jsonIsFoundEvenWhenWrapped() {
        assertEquals("Tea", AskAi.extractJson("""{"name":"Tea"}""")!!.jsonObject["name"]!!.jsonPrimitive.content)
        val fenced = "Sure!\n```json\n{\"recipes\":[{\"name\":\"Soup\"}]}\n```\nEnjoy"
        assertEquals("Soup", AskAi.extractJson(fenced)!!.jsonObject["recipes"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(3, AskAi.extractJson("Here: [1, 2, 3]")!!.jsonArray.size)
        assertNull(AskAi.extractJson("I can't do that"))
    }

    @Test
    fun dataUrlParts() {
        assertEquals("image/png" to "QUJD", AskAi.splitDataUrl("data:image/png;base64,QUJD"))
        assertNull(AskAi.splitDataUrl("data:image/png,raw"))
        assertNull(AskAi.splitDataUrl("https://example.com/a.png"))
        assertNull(AskAi.splitDataUrl("data:image/png;base64,"))
    }
}
