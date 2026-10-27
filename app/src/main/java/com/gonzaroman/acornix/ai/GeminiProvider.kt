package com.gonzaroman.acornix.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/** Google Gemini through the Generative Language REST API. */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
    private val http: OkHttpClient = HttpClients.default,
    private val base: String = "https://generativelanguage.googleapis.com/v1beta",
) : AiProvider {

    private val headers get() = mapOf("x-goog-api-key" to apiKey)
    private val modelPath get() = if (model.startsWith("models/")) model else "models/$model"

    override suspend fun streamText(
        system: String,
        prompt: String,
        images: List<ImageData>,
        quick: Boolean, // thinking options differ between Gemini versions; flash models are fast anyway
        onDelta: (String) -> Unit,
    ): TextResult {
        val body = buildJsonObject {
            put("systemInstruction", textContent(system))
            put("contents", buildJsonArray { add(userContent(prompt, images)) })
        }
        val out = StringBuilder()
        var finish: String? = null
        http.streamSse("$base/$modelPath:streamGenerateContent?alt=sse", body, headers) { data ->
            val chunk = runCatching { AiJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@streamSse
            chunk["error"]?.let { throw AiException("API error: ${(it as? JsonObject)?.str("message") ?: it}") }
            val candidate = (chunk["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@streamSse
            val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray) ?: JsonArray(emptyList())
            parts.forEach { p ->
                val part = p as? JsonObject ?: return@forEach
                if (part.bool("thought") == true) return@forEach
                part.str("text")?.let { if (it.isNotEmpty()) { out.append(it); onDelta(it) } }
            }
            candidate.str("finishReason")?.let { finish = it }
        }
        return TextResult(
            out.toString(),
            truncated = finish == "MAX_TOKENS",
            refused = finish == "SAFETY" || finish == "PROHIBITED_CONTENT",
        )
    }

    override fun newAgentSession(system: String, tools: List<ToolSpec>): AgentSession = Session(system, tools)

    override suspend fun listModels(): List<String> {
        val res = http.getJson("$base/models?pageSize=200", headers)
        return (res["models"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val methods = (obj["supportedGenerationMethods"] as? JsonArray)?.map { it.toString().trim('"') }.orEmpty()
            if ("generateContent" !in methods) return@mapNotNull null
            obj.str("name")?.removePrefix("models/")
        }.sorted()
    }

    private fun textContent(text: String) = buildJsonObject {
        put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) })
    }

    private fun userContent(text: String, images: List<ImageData>) = buildJsonObject {
        put("role", "user")
        put("parts", buildJsonArray {
            images.forEach { add(inlineImage(it)) }
            add(buildJsonObject { put("text", text) })
        })
    }

    private fun inlineImage(img: ImageData) = buildJsonObject {
        put("inline_data", buildJsonObject {
            put("mime_type", img.mimeType)
            put("data", img.base64)
        })
    }

    private inner class Session(private val system: String, tools: List<ToolSpec>) : AgentSession {
        private val contents = mutableListOf<JsonElement>()
        private val toolsJson = buildJsonArray {
            if (tools.isNotEmpty()) add(buildJsonObject {
                put("functionDeclarations", buildJsonArray {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("parameters", t.parameters)
                        })
                    }
                })
            })
        }

        override suspend fun send(text: String, images: List<ImageData>): AgentReply {
            contents += userContent(text, images)
            return complete()
        }

        override suspend fun sendToolResults(results: List<ToolResult>): AgentReply {
            contents += buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray {
                    results.forEach { r ->
                        add(buildJsonObject {
                            put("functionResponse", buildJsonObject {
                                put("name", r.call.name)
                                if (r.call.id.startsWith("gemini-").not()) put("id", r.call.id)
                                put("response", buildJsonObject {
                                    if (r.isError) put("error", r.content) else put("result", r.content)
                                })
                            })
                        })
                    }
                    results.mapNotNull { it.image }.forEach { add(inlineImage(it)) }
                })
            }
            return complete()
        }

        private suspend fun complete(): AgentReply {
            val body = buildJsonObject {
                put("systemInstruction", textContent(system))
                put("contents", JsonArray(contents))
                if (toolsJson.isNotEmpty()) put("tools", toolsJson)
            }
            val res = http.postJson("$base/$modelPath:generateContent", body, headers)
            val candidate = (res["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: throw AiException("Empty response from the model")
            val content = candidate["content"] as? JsonObject ?: buildJsonObject {
                put("role", "model"); put("parts", JsonArray(emptyList()))
            }
            // Keep the model turn exactly as returned (it may carry thought signatures).
            contents += content
            val parts = (content["parts"] as? JsonArray) ?: JsonArray(emptyList())
            val text = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            parts.forEachIndexed { i, p ->
                val part = p as? JsonObject ?: return@forEachIndexed
                if (part.bool("thought") == true) return@forEachIndexed
                part.str("text")?.let { text.append(it) }
                (part["functionCall"] as? JsonObject)?.let { fc ->
                    val args = (fc["args"] as? JsonObject) ?: JsonObject(emptyMap())
                    calls += ToolCall(fc.str("id") ?: "gemini-$i", fc.str("name").orEmpty(), args)
                }
            }
            return AgentReply(text.toString(), calls, candidate.str("finishReason"))
        }
    }
}
