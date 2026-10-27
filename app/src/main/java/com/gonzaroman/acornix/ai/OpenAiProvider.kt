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
 *
 * With [responsesApi] (OpenAI itself), the assistant uses the Responses API instead: OpenAI's reasoning models
 * can only think between tool calls there. On Chat Completions they must run with reasoning off, and some
 * (gpt-6.1-sol) refuse that entirely.
 */
class OpenAiProvider(
    private val apiKey: String,
    private val model: String,
    baseUrl: String = DEFAULT_BASE_URL,
    private val http: OkHttpClient = HttpClients.default,
    private val responsesApi: Boolean = false,
) : AiProvider {

    private val base = baseUrl.trim().trimEnd('/')
    private val noReasoningKey = "$base|$model"

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

    override fun newAgentSession(system: String, tools: List<ToolSpec>): AgentSession =
        if (responsesApi) ResponsesSession(system, tools) else Session(system, tools)

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
            fun body(noReasoning: Boolean) = buildJsonObject {
                put("model", model)
                put("messages", JsonArray(messages))
                if (toolsJson.isNotEmpty()) put("tools", toolsJson)
                if (noReasoning) put("reasoning_effort", "none")
            }
            val res = try {
                http.postJson("$base/chat/completions", body(noReasoningKey in toolsNeedNoReasoning), headers)
            } catch (e: AiException) {
                // Some reasoning models (gpt-6-luna) only take tools on this endpoint with reasoning off.
                if (toolsJson.isEmpty() || noReasoningKey in toolsNeedNoReasoning || !e.asksForNoReasoning()) throw e
                toolsNeedNoReasoning += noReasoningKey
                http.postJson("$base/chat/completions", body(true), headers)
            }
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

    /**
     * The Responses API, with reasoning on between tool calls. OpenAI keeps the conversation
     * (previous_response_id), so each turn only sends what is new.
     */
    private inner class ResponsesSession(private val system: String, tools: List<ToolSpec>) : AgentSession {
        private val toolsJson = buildJsonArray {
            tools.forEach { t ->
                add(buildJsonObject {
                    put("type", "function")
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", t.parameters)
                })
            }
        }
        private var previousId: String? = null

        /** call_ids still waiting for a result (after a stop or an error); the API rejects a turn without them. */
        private var pending: List<String> = emptyList()

        override suspend fun send(text: String, images: List<ImageData>): AgentReply {
            val input = buildJsonArray {
                pending.forEach { add(callOutput(it, "Stopped by the user.")) }
                add(userInput(text, images))
            }
            return complete(input)
        }

        override suspend fun sendToolResults(results: List<ToolResult>): AgentReply {
            val input = buildJsonArray {
                results.forEach { r -> add(callOutput(r.call.id, if (r.isError) "ERROR: ${r.content}" else r.content, r.image)) }
                val answered = results.map { it.call.id }.toSet()
                pending.filter { it !in answered }.forEach { add(callOutput(it, "Skipped.")) }
            }
            return complete(input)
        }

        /**
         * A screenshot goes inside the tool's own output. Sent as a separate user message, reasoning models
         * took it for "make an app like this" and answered with an HTML page.
         */
        private fun callOutput(id: String, output: String, image: ImageData? = null) = buildJsonObject {
            put("type", "function_call_output")
            put("call_id", id)
            if (image == null) {
                put("output", output)
            } else {
                put("output", buildJsonArray {
                    add(buildJsonObject { put("type", "input_text"); put("text", output) })
                    add(buildJsonObject {
                        put("type", "input_image")
                        put("image_url", "data:${image.mimeType};base64,${image.base64}")
                    })
                })
            }
        }

        private fun userInput(text: String, images: List<ImageData>) = buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "input_text"); put("text", text) })
                images.forEach { img ->
                    add(buildJsonObject {
                        put("type", "input_image")
                        put("image_url", "data:${img.mimeType};base64,${img.base64}")
                    })
                }
            })
        }

        private suspend fun complete(input: JsonArray): AgentReply {
            val body = buildJsonObject {
                put("model", model)
                put("instructions", system)
                put("input", input)
                put("reasoning", buildJsonObject { put("effort", "medium") })
                put("store", true)
                if (toolsJson.isNotEmpty()) put("tools", toolsJson)
                previousId?.let { put("previous_response_id", it) }
            }
            val res = http.postJson("$base/responses", body, headers)
            previousId = res.str("id")
            val text = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            (res["output"] as? JsonArray).orEmpty().forEach { el ->
                val item = el as? JsonObject ?: return@forEach
                when (item.str("type")) {
                    "message" -> (item["content"] as? JsonArray).orEmpty().forEach { part ->
                        val p = part as? JsonObject ?: return@forEach
                        (p.str("text") ?: p.str("refusal"))?.let { text.append(it) }
                    }
                    "function_call" -> {
                        val id = item.str("call_id") ?: return@forEach
                        val args = runCatching {
                            AiJson.parseToJsonElement(item.str("arguments").orEmpty().ifBlank { "{}" }).jsonObject
                        }.getOrElse { JsonObject(emptyMap()) }
                        calls += ToolCall(id, item.str("name").orEmpty(), args)
                    }
                }
            }
            pending = calls.map { it.id }
            val stop = (res["incomplete_details"] as? JsonObject)?.str("reason") ?: res.str("status")
            return AgentReply(text.toString(), calls, stop)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

        /** Models that rejected tools while reasoning; remembered so later turns skip the failed call. */
        private val toolsNeedNoReasoning: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

        private fun AiException.asksForNoReasoning(): Boolean {
            val m = message.orEmpty()
            return m.startsWith("HTTP 400") && m.contains("reasoning_effort") && m.contains("'none'")
        }
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
