package com.gonzaroman.acornix.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Duration
import kotlin.coroutines.coroutineContext

/** Claude through the official Anthropic Java SDK. */
class AnthropicProvider(
    apiKey: String,
    private val model: String,
    baseUrl: String? = null,
) : AiProvider {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .timeout(Duration.ofMinutes(10))
        .maxRetries(2)
        .build()

    override suspend fun streamText(
        system: String,
        prompt: String,
        images: List<ImageData>,
        quick: Boolean,
        onDelta: (String) -> Unit,
    ): TextResult = guard {
        val params = baseParams(STREAM_MAX_TOKENS, if (quick) OutputConfig.Effort.LOW else OutputConfig.Effort.HIGH)
            .system(system)
            .addUserMessageOfBlockParams(userBlocks(prompt, images))
            .build()

        val out = StringBuilder()
        var stop: StopReason? = null
        coroutineScope {
            val stream = withContext(Dispatchers.IO) { client.messages().createStreaming(params) }
            // Closing the stream is what unblocks the reader when the user cancels.
            val closer = launch { try { awaitCancellation() } finally { stream.close() } }
            try {
                withContext(Dispatchers.IO) {
                    val events = stream.stream().iterator()
                    while (events.hasNext()) {
                        coroutineContext.ensureActive()
                        val event = events.next()
                        event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent {
                            out.append(it.text())
                            onDelta(it.text())
                        }
                        event.messageDelta().flatMap { it.delta().stopReason() }.ifPresent { stop = it }
                    }
                }
            } finally {
                closer.cancel()
                stream.close()
            }
        }
        TextResult(out.toString(), truncated = stop == StopReason.MAX_TOKENS, refused = stop == StopReason.REFUSAL)
    }

    override fun newAgentSession(system: String, tools: List<ToolSpec>): AgentSession = Session(system, tools)

    override suspend fun listModels(): List<String> = guard {
        runInterruptible(Dispatchers.IO) {
            client.models().list().autoPager().map { it.id() }.toList()
        }
    }

    private fun baseParams(maxTokens: Long, effort: OutputConfig.Effort): MessageCreateParams.Builder {
        val builder = MessageCreateParams.builder().model(model).maxTokens(maxTokens)
        if (supportsEffort(model)) builder.outputConfig(OutputConfig.builder().effort(effort).build())
        if (model in FALLBACK_MODELS) {
            // Server-side refusal fallback: a refused request is retried on a suitable model.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return builder
    }

    private fun userBlocks(text: String, images: List<ImageData>): List<ContentBlockParam> =
        images.map { ContentBlockParam.ofImage(imageBlock(it)) } +
            ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())

    private fun imageBlock(img: ImageData): ImageBlockParam = ImageBlockParam.builder()
        .source(
            Base64ImageSource.builder()
                .data(img.base64)
                .mediaType(Base64ImageSource.MediaType.of(img.mimeType))
                .build(),
        )
        .build()

    private inner class Session(private val system: String, tools: List<ToolSpec>) : AgentSession {
        private val history = mutableListOf<MessageParam>()
        private val sdkTools: List<Tool> = tools.map { it.toSdkTool() }

        override suspend fun send(text: String, images: List<ImageData>): AgentReply {
            history += MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(userBlocks(text, images))
                .build()
            return complete()
        }

        override suspend fun sendToolResults(results: List<ToolResult>): AgentReply {
            // All results of one assistant turn go back in a single user message.
            val blocks = results.map { r ->
                val content = buildList {
                    add(ToolResultBlockParam.Content.Block.ofText(TextBlockParam.builder().text(r.content.ifEmpty { "OK" }).build()))
                    r.image?.let { add(ToolResultBlockParam.Content.Block.ofImage(imageBlock(it))) }
                }
                ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                        .toolUseId(r.call.id)
                        .contentOfBlocks(content)
                        .isError(r.isError)
                        .build(),
                )
            }
            history += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build()
            return complete()
        }

        private suspend fun complete(): AgentReply = guard {
            val builder = baseParams(AGENT_MAX_TOKENS, OutputConfig.Effort.MEDIUM)
                .system(system)
                .messages(history.toList())
            sdkTools.forEach { builder.addTool(it) }
            val response = runInterruptible(Dispatchers.IO) { client.messages().create(builder.build()) }
            // Append the full assistant turn unchanged so thinking blocks are preserved.
            history += response.toParam()

            val text = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            response.content().forEach { block ->
                block.text().ifPresent { text.append(it.text()) }
                block.toolUse().ifPresent { use ->
                    calls += ToolCall(use.id(), use.name(), use._input().toJsonObject())
                }
            }
            val stop = response.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL && text.isEmpty()) {
                text.append("I can't help with that request.")
            }
            AgentReply(text.toString(), calls, stop?.toString())
        }
    }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: AnthropicServiceException) {
        throw AiException(httpErrorMessage(e.statusCode(), e.body().toString()), e)
    } catch (e: AnthropicIoException) {
        throw AiException("Network error: ${e.message}", e)
    }

    companion object {
        private const val STREAM_MAX_TOKENS = 64_000L
        private const val AGENT_MAX_TOKENS = 16_000L
        private val FALLBACK_MODELS = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")
        private val EFFORT_MARKERS = listOf("opus-5", "fable-5", "mythos-5", "sonnet-5", "haiku-5", "opus-4-6", "opus-4-7", "opus-4-8", "sonnet-4-6")

        fun supportsEffort(model: String) = EFFORT_MARKERS.any { model.contains(it) }
    }
}

private fun ToolSpec.toSdkTool(): Tool {
    val props = (parameters["properties"] as? JsonObject).orEmpty()
    val required = (parameters["required"] as? kotlinx.serialization.json.JsonArray)
        ?.map { it.toString().trim('"') }.orEmpty()
    val propsBuilder = Tool.InputSchema.Properties.builder()
    props.forEach { (key, schema) -> propsBuilder.putAdditionalProperty(key, JsonValue.from(schema.toPlain())) }
    return Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(Tool.InputSchema.builder().properties(propsBuilder.build()).required(required).build())
        .build()
}

private fun JsonValue.toJsonObject(): JsonObject = runCatching {
    AiJson.parseToJsonElement(jsonMapper().writeValueAsString(this)).jsonObject
}.getOrElse { JsonObject(emptyMap()) }

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
