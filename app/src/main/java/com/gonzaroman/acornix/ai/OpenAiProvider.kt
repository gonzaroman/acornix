package com.gonzaroman.acornix.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * OpenAI Chat Completions. Also used for any OpenAI-compatible endpoint (OpenRouter, Groq, Ollama…)
 * by passing a different [baseUrl].
 */
class OpenAiProvider(
    private val apiKey: String,
    private val model: String,
    baseUrl: String = DEFAULT_BASE_URL,
    private val http: OkHttpClient = HttpClients.default,
) : AiProvider {

    private val base = baseUrl.trim().trimEnd('/')

    private val headers: Map<String, String>
        get() = if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")

    override suspend fun streamText(
        system: String,
        prompt: String,
        images: List<ImageData>,
        onDelta: (String) -> Unit,
    ): TextResult {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(userMessage(prompt, images))
            })
        }
        val out = StringBuilder()
        var finish: String? = null
        http.streamSse("$base/chat/completions", body, headers) { data ->
            val chunk = runCatching { AiJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@streamSse
            chunk["error"]?.let { throw AiException("API error: ${(it as? JsonObject)?.str("message") ?: it}") }
            val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@streamSse
            val delta = choice["delta"] as? JsonObject
            delta?.str("content")?.let { if (it.isNotEmpty()) { out.append(it); onDelta(it) } }
            choice.str("finish_reason")?.let { finish = it }
        }
        return TextResult(out.toString(), truncated = finish == "length", refused = finish == "content_filter")
    }

    override fun newAgentSession(system: String, tools: List<ToolSpec>): AgentSession = Session(system, tools)

    override suspend fun listModels(): List<String> {
        val res = http.getJson("$base/models", headers)
        return (res["data"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("id") }
            .sorted()
    }

    private fun userMessage(text: String, images: List<ImageData>): JsonObject = buildJsonObject {
        put("role", "user")
        if (images.isEmpty()) {
            put("content", text)
        } else {
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", text) })
                images.forEach { img ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", "data:${img.mimeType};base64,${img.base64}") })
                    })
                }
            })
        }
    }

    private inner class Session(system: String, tools: List<ToolSpec>) : AgentSession {
        private val messages = mutableListOf<JsonElement>(
            buildJsonObject { put("role", "system"); put("content", system) },
        )
        private val toolsJson = buildJsonArray {
            tools.forEach { t ->
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", t.parameters)
                    })
                })
            }
        }

        override suspend fun send(text: String, images: List<ImageData>): AgentReply {
            messages += userMessage(text, images)
            return complete()
        }

        override suspend fun sendToolResults(results: List<ToolResult>): AgentReply {
            results.forEach { r ->
                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", r.call.id)
                    put("content", if (r.isError) "ERROR: ${r.content}" else r.content)
                }
            }
            // Tool messages cannot carry images, so screenshots go in a follow-up user message.
            val shots = results.mapNotNull { it.image }
            if (shots.isNotEmpty()) messages += userMessage("Screenshot from the tool call above.", shots)
            return complete()
        }

        private suspend fun complete(): AgentReply {
            val body = buildJsonObject {
                put("model", model)
                put("messages", JsonArray(messages))
                if (toolsJson.isNotEmpty()) put("tools", toolsJson)
            }
            val res = http.postJson("$base/chat/completions", body, headers)
            val choice = (res["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: throw AiException("Empty response from the model")
            val message = choice["message"] as? JsonObject ?: throw AiException("Empty response from the model")
            messages += message
            val calls = (message["tool_calls"] as? JsonArray).orEmpty().mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val fn = obj["function"] as? JsonObject ?: return@mapNotNull null
                val args = runCatching {
                    AiJson.parseToJsonElement(fn.str("arguments").orEmpty().ifBlank { "{}" }).jsonObject
                }.getOrElse { JsonObject(emptyMap()) }
                ToolCall(obj.str("id") ?: fn.str("name").orEmpty(), fn.str("name").orEmpty(), args)
            }
            return AgentReply(message.contentText(), calls, choice.str("finish_reason"))
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}

private fun JsonObject.contentText(): String = when (val c = this["content"]) {
    is JsonPrimitive -> if (c.isString) c.content else ""
    is JsonArray -> c.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }
    else -> ""
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

@Suppress("unused")
private fun JsonElement.asArrayOrEmpty(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())
