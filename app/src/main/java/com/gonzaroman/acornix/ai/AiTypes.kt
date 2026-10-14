package com.gonzaroman.acornix.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The AI backends the user can configure. */
enum class ProviderType(val id: String, val label: String, val defaultModel: String) {
    ANTHROPIC("anthropic", "Anthropic (Claude)", "claude-opus-5-5"),
    OPENAI("openai", "OpenAI", "gpt-5"),
    GEMINI("gemini", "Google Gemini", "gemini-2.5-flash"),
    CUSTOM("custom", "OpenAI-compatible", "");

    companion object {
        fun fromId(id: String?): ProviderType = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}

/** An image attached to a prompt or returned by a tool (e.g. a screenshot). */
data class ImageData(val base64: String, val mimeType: String = "image/jpeg")

/** Provider-neutral tool definition. [parameters] is a JSON schema object. */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class ToolCall(val id: String, val name: String, val arguments: JsonObject)

data class ToolResult(
    val call: ToolCall,
    val content: String,
    val isError: Boolean = false,
    val image: ImageData? = null,
)

data class AgentReply(
    val text: String,
    val toolCalls: List<ToolCall>,
    val stopReason: String? = null,
)

/** Result of a streamed text generation. */
data class TextResult(val text: String, val truncated: Boolean, val refused: Boolean = false)

open class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** No usable provider yet; the UI shows its own translated message for this one. */
class ProviderNotConfiguredException : AiException("AI provider not configured. Add your API key in Settings.")

interface AiProvider {
    /** Streams a single-turn completion; [onDelta] receives text chunks as they arrive. */
    suspend fun streamText(
        system: String,
        prompt: String,
        images: List<ImageData> = emptyList(),
        onDelta: (String) -> Unit = {},
    ): TextResult

    /** Starts a multi-turn tool-calling conversation. The session keeps the provider-native history. */
    fun newAgentSession(system: String, tools: List<ToolSpec>): AgentSession

    suspend fun listModels(): List<String>
}

interface AgentSession {
    suspend fun send(text: String, images: List<ImageData> = emptyList()): AgentReply
    suspend fun sendToolResults(results: List<ToolResult>): AgentReply
}

// ---------- Small helpers for building JSON schemas ----------

class SchemaBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = true, enum: List<String>? = null) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enum != null) put("enum", buildJsonArray { enum.forEach { add(JsonPrimitive(it)) } })
        }
        if (required) this.required += name
    }

    fun integer(name: String, description: String, required: Boolean = true) {
        properties[name] = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
        if (required) this.required += name
    }

    fun boolean(name: String, description: String, required: Boolean = true) {
        properties[name] = buildJsonObject {
            put("type", "boolean")
            put("description", description)
        }
        if (required) this.required += name
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
}

fun tool(name: String, description: String, block: SchemaBuilder.() -> Unit = {}): ToolSpec =
    ToolSpec(name, description, SchemaBuilder().apply(block).build())

/** Converts kotlinx JSON into plain Kotlin values (Map/List/String/Number/Boolean/null). */
fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
}

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.longOrNull?.toInt() ?: it.doubleOrNull?.toInt() ?: it.content.toIntOrNull() }
fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.toBooleanStrictOrNull() }
