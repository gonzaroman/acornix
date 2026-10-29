package com.gonzaroman.acornix.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PublisherTest {
    private val draft = PublishDraft(
        id = "pocket-chef", name = "Pocket Chef", emoji = "🍳", category = "tools",
        description = "Recipes from a photo of your fridge", prompt = "A chef that suggests \"recipes\"\nfrom a photo",
    )

    @Test
    fun issueBodyCarriesTheMarkerAndTheData() {
        val body = Publisher.issueBody(draft, "abc123", "https://gist.github.com/maria/abc123")
        assertTrue(body.startsWith("<!-- acornix-submission v1 -->\n"))
        val json = Regex("```json\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL).find(body)!!.groupValues[1]
        val data = Json.parseToJsonElement(json).jsonObject
        assertEquals("pocket-chef", data["id"]!!.jsonPrimitive.content)
        assertEquals("abc123", data["gist"]!!.jsonPrimitive.content)
        assertEquals(draft.prompt, data["prompt"]!!.jsonPrimitive.content)
        // For the cross-check with the gallery's review bot (scripts/acornix_bot.py).
        File("build/publisher-issue-body.md").writeText(body)
    }

    @Test
    fun galleryIdsUseDashes() {
        assertEquals("mi-piano", Publisher.galleryId("Mi Piano"))
        assertEquals("cafe-con-leche", Publisher.galleryId("Café con leche!"))
    }
}
