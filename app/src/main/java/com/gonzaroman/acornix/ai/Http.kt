package com.gonzaroman.acornix.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal val AiJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

internal val JSON_MEDIA = "application/json".toMediaType()

object HttpClients {
    /** Long read timeout: big apps can take minutes and streams can pause while the model thinks. */
    val default: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}

internal fun jsonRequest(url: String, body: JsonElement, headers: Map<String, String>): Request =
    Request.Builder()
        .url(url)
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .post(body.toString().toRequestBody(JSON_MEDIA))
        .build()

/** Executes [request] on the IO dispatcher, cancelling the HTTP call if the coroutine is cancelled. */
internal suspend fun OkHttpClient.await(request: Request): Response = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWith(Result.failure(AiException(networkMessage(e), e)))
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resumeWith(Result.success(response))
        }
    })
}

private fun networkMessage(e: IOException): String =
    "Network error: ${e.message ?: e.javaClass.simpleName}"

internal suspend fun OkHttpClient.postJson(url: String, body: JsonElement, headers: Map<String, String>): JsonObject {
    val response = await(jsonRequest(url, body, headers))
    return withContext(Dispatchers.IO) {
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AiException(httpErrorMessage(it.code, text))
            AiJson.parseToJsonElement(text).jsonObject
        }
    }
}

internal suspend fun OkHttpClient.getJson(url: String, headers: Map<String, String>): JsonObject {
    val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()
    val response = await(request)
    return withContext(Dispatchers.IO) {
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AiException(httpErrorMessage(it.code, text))
            AiJson.parseToJsonElement(text).jsonObject
        }
    }
}

/**
 * Posts [body] and reads the response as Server-Sent Events, calling [onData] with the payload of
 * every `data:` event. Stops at `[DONE]` or the end of the stream.
 */
internal suspend fun OkHttpClient.streamSse(
    url: String,
    body: JsonElement,
    headers: Map<String, String>,
    onData: (String) -> Unit,
) {
    val response = await(jsonRequest(url, body, headers + ("Accept" to "text/event-stream")))
    withContext(Dispatchers.IO) {
        response.use {
            if (!it.isSuccessful) throw AiException(httpErrorMessage(it.code, it.body?.string().orEmpty()))
            val source = it.body?.source() ?: return@withContext
            val data = StringBuilder()
            while (true) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) {
                            val payload = data.toString()
                            data.clear()
                            if (payload.trim() == "[DONE]") return@withContext
                            onData(payload)
                        }
                    }
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trimStart())
                    }
                    // Ignore "event:", "id:" and comment lines.
                }
            }
            if (data.isNotEmpty() && data.toString().trim() != "[DONE]") onData(data.toString())
        }
    }
}

/** Extracts a readable message from an API error body. */
internal fun httpErrorMessage(code: Int, body: String): String {
    val detail = runCatching {
        val obj = AiJson.parseToJsonElement(body).jsonObject
        val err = obj["error"]
        when (err) {
            is JsonObject -> err.str("message") ?: err.toString()
            null -> obj.str("message") ?: body
            else -> err.toString().trim('"')
        }
    }.getOrElse { body.take(300) }
    val hint = when (code) {
        401, 403 -> " (check your API key)"
        404 -> " (check the model name or URL)"
        429 -> " (rate limit or quota reached)"
        else -> ""
    }
    return "HTTP $code$hint: $detail"
}
