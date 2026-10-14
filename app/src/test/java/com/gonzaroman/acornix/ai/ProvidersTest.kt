package com.gonzaroman.acornix.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProvidersTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun sse(vararg events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { "$it\n\n" })

    private val weatherTool = tool("get_weather", "Weather") { string("city", "City") }

    // ---------- OpenAI ----------

    @Test
    fun openAiStreamsText() = runTest {
        server.enqueue(sse(
            """data: {"choices":[{"delta":{"content":"Hel"}}]}""",
            """data: {"choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}]}""",
            "data: [DONE]",
        ))
        val p = OpenAiProvider("k", "m", server.url("/v1").toString())
        val deltas = mutableListOf<String>()
        val r = p.streamText("sys", "hi", listOf(ImageData("QUJD"))) { deltas += it }
        assertEquals("Hello", r.text)
        assertEquals(listOf("Hel", "lo"), deltas)

        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer k", req.getHeader("Authorization"))
        val body = AiJson.parseToJsonElement(req.body.readUtf8()).jsonObject
        val user = body["messages"]!!.jsonArray[1].jsonObject
        assertEquals("data:image/jpeg;base64,QUJD", user["content"]!!.jsonArray[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun openAiToolLoop() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
             "tool_calls":[{"id":"c1","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"Sevilla\"}"}}]}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Sunny"}}]}"""))

        val session = OpenAiProvider("k", "m", server.url("/v1").toString()).newAgentSession("sys", listOf(weatherTool))
        val first = session.send("weather?")
        assertEquals("get_weather", first.toolCalls.single().name)
        assertEquals("Sevilla", first.toolCalls.single().arguments.str("city"))

        val second = session.sendToolResults(listOf(ToolResult(first.toolCalls.single(), "30C")))
        assertEquals("Sunny", second.text)

        server.takeRequest()
        val body = AiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val messages = body["messages"]!!.jsonArray
        assertEquals("tool", messages.last().jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("c1", messages.last().jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("function", body["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun httpErrorsAreReadable() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid key"}}"""))
        val e = runCatching { OpenAiProvider("k", "m", server.url("/v1").toString()).streamText("s", "p") }.exceptionOrNull()
        assertTrue(e is AiException)
        assertTrue(e!!.message!!.contains("Invalid key"))
        assertTrue(e.message!!.contains("401"))
    }

    // ---------- Gemini ----------

    @Test
    fun geminiStreamsTextSkippingThoughts() = runTest {
        server.enqueue(sse(
            """data: {"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"Ho"}]}}]}""",
            """data: {"candidates":[{"content":{"parts":[{"text":"la"}]},"finishReason":"STOP"}]}""",
        ))
        val p = GeminiProvider("gk", "gemini-x", base = server.url("/v1beta").toString().trimEnd('/'))
        assertEquals("Hola", p.streamText("s", "p").text)
        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-x:streamGenerateContent?alt=sse", req.path)
        assertEquals("gk", req.getHeader("x-goog-api-key"))
    }

    @Test
    fun geminiFunctionCalls() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"get_weather","args":{"city":"Cádiz"}},"thoughtSignature":"sig"}]}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"role":"model","parts":[{"text":"Windy"}]}}]}"""))
        val s = GeminiProvider("gk", "g", base = server.url("/v1beta").toString().trimEnd('/')).newAgentSession("sys", listOf(weatherTool))
        val first = s.send("weather")
        assertEquals("Cádiz", first.toolCalls.single().arguments.str("city"))
        assertEquals("Windy", s.sendToolResults(listOf(ToolResult(first.toolCalls.single(), "40C"))).text)

        server.takeRequest()
        val contents = AiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["contents"]!!.jsonArray
        // The model turn is echoed back untouched (keeps the thought signature).
        assertEquals("sig", contents[1].jsonObject["parts"]!!.jsonArray[0].jsonObject["thoughtSignature"]!!.jsonPrimitive.content)
        val response = contents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("get_weather", response["name"]!!.jsonPrimitive.content)
    }

    // ---------- Anthropic (official SDK) ----------

    @Test
    fun anthropicStreamsTextWithFallbackBeta() = runTest {
        server.enqueue(sse(
            "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"m\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":[],\"stop_reason\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}}",
            "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
            "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi \"}}",
            "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"there\"}}",
            "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}",
            "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"},\"usage\":{\"output_tokens\":2}}",
            "event: message_stop\ndata: {\"type\":\"message_stop\"}",
        ))
        val p = AnthropicProvider("ak", "claude-opus-5-5", server.url("/").toString())
        val r = p.streamText("sys", "hello")
        assertEquals("Hi there", r.text)
        assertTrue(r.truncated)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("ak", req.getHeader("x-api-key"))
        assertTrue(req.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
        val body = AiJson.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("high", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(true, body["stream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun anthropicToolLoopKeepsAssistantTurn() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
            {"id":"m1","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"tool_use",
             "usage":{"input_tokens":1,"output_tokens":1},
             "content":[{"type":"thinking","thinking":"","signature":"sig123"},
                        {"type":"text","text":"Checking"},
                        {"type":"tool_use","id":"tu1","name":"get_weather","input":{"city":"Huelva"}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
            {"id":"m2","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"end_turn",
             "usage":{"input_tokens":1,"output_tokens":1},"content":[{"type":"text","text":"Sunny in Huelva"}]}
        """.trimIndent()))

        val s = AnthropicProvider("ak", "claude-opus-5-5", server.url("/").toString()).newAgentSession("sys", listOf(weatherTool))
        val first = s.send("weather?")
        assertEquals("Checking", first.text)
        val call = first.toolCalls.single()
        assertEquals("tu1", call.id)
        assertEquals("Huelva", call.arguments.str("city"))

        val second = s.sendToolResults(listOf(ToolResult(call, "25C")))
        assertEquals("Sunny in Huelva", second.text)

        val firstBody = AiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val toolDef = firstBody["tools"]!!.jsonArray[0].jsonObject
        assertEquals("get_weather", toolDef["name"]!!.jsonPrimitive.content)
        assertEquals("string", toolDef["input_schema"]!!.jsonObject["properties"]!!.jsonObject["city"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val messages = AiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        val assistantTurn = messages[1].jsonObject["content"]!!.jsonArray
        // The thinking block goes back unchanged, with its signature.
        assertEquals("sig123", assistantTurn[0].jsonObject["signature"]!!.jsonPrimitive.content)
        val result = messages[2].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("tu1", result["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Suppress("unused")
    private fun obj(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject { pairs.forEach { put(it.first, it.second) } }
}
