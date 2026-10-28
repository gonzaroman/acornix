package com.gonzaroman.acornix.web

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.gonzaroman.acornix.ai.AiJson
import com.gonzaroman.acornix.ai.ImageData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream

/**
 * Options of `Acornix.askAI(prompt, options)`. The second argument used to be just the system prompt,
 * and that form keeps working.
 */
data class AskAiOptions(
    val system: String? = null,
    /** data:image/… URLs; the page already turned files, <img>, <canvas> and camera <video> into these. */
    val images: List<String> = emptyList(),
    /** The answer is returned as parsed JSON instead of text. */
    val json: Boolean = false,
    /** Less deep thinking: apps want answers in seconds. On unless the app asks for quick: false. */
    val quick: Boolean = true,
) {
    companion object {
        const val MAX_IMAGES = 4

        fun from(arg: JsonElement?): AskAiOptions = when (arg) {
            is JsonPrimitive -> AskAiOptions(system = arg.contentOrNull?.takeIf { it.isNotBlank() })
            is JsonObject -> AskAiOptions(
                system = (arg["system"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                images = (arg["images"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .filter { it.startsWith("data:image/") }
                    .take(MAX_IMAGES),
                json = (arg["json"] as? JsonPrimitive)?.booleanOrNull == true,
                quick = (arg["quick"] as? JsonPrimitive)?.booleanOrNull != false,
            )
            else -> AskAiOptions()
        }
    }
}

object AskAi {
    const val DEFAULT_SYSTEM = "You are a helpful assistant inside a mobile app. Answer concisely in the user's language."
    const val JSON_RULE = "\n\nAnswer ONLY with valid JSON: no markdown, no code fences, no comments, no text around it."
    private const val MAX_SIDE = 1568

    fun system(options: AskAiOptions): String = (options.system ?: DEFAULT_SYSTEM) + if (options.json) JSON_RULE else ""

    /** The JSON value in a model's answer, even if it came wrapped in a code fence or with text around it. */
    fun extractJson(text: String): JsonElement? {
        val trimmed = text.trim()
        runCatching { return AiJson.parseToJsonElement(trimmed) }
        val start = trimmed.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val end = trimmed.lastIndexOf(if (trimmed[start] == '{') '}' else ']')
        if (end <= start) return null
        return runCatching { AiJson.parseToJsonElement(trimmed.substring(start, end + 1)) }.getOrNull()
    }

    /** "data:image/png;base64,AAAA" -> ("image/png", "AAAA"), or null if it isn't a base64 image. */
    fun splitDataUrl(url: String): Pair<String, String>? {
        if (!url.startsWith("data:image/")) return null
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val header = url.substring(5, comma)
        if (!header.endsWith(";base64")) return null
        val payload = url.substring(comma + 1)
        return if (payload.isBlank()) null else header.removeSuffix(";base64") to payload
    }

    /** An image for the AI: decoded, scaled down to [MAX_SIDE] and re-encoded as JPEG, like attachments. */
    fun toImage(dataUrl: String): ImageData? {
        val (_, payload) = splitDataUrl(dataUrl) ?: return null
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val long = maxOf(bitmap.width, bitmap.height)
        val scaled = if (long > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / long
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return ImageData(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg")
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
