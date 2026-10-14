package com.gonzaroman.acornix.core

/**
 * The code + improvement suggestion extracted from an AI response.
 * [repaired] is true when the code was only a fragment (missing <html>, <style> or <script> openers)
 * and had to be wrapped to run — usually a sign that part of the answer was lost.
 */
data class ParsedResponse(val code: String?, val suggestion: String?, val repaired: Boolean = false)

/**
 * Port of acornix's `process_and_execute` / `clean_html_code`: the model answers with
 * `---CODIGO---` (the full file) followed by `---SUGERENCIA---` (one improvement idea).
 * English markers and plain markdown code fences are accepted too.
 */
object ResponseParser {
    private val CODE_MARKERS = listOf("---CODIGO---", "---CÓDIGO---", "---CODE---")
    private val SUGGESTION_MARKERS = listOf("---SUGERENCIA---", "---SUGGESTION---")

    fun parse(text: String): ParsedResponse {
        val (codePart, suggestionPart) = split(text)
        val (code, repaired) = extractHtml(codePart)
        val suggestion = suggestionPart
            ?.let { stripFences(it) }
            ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.trim('"', ' ')
            ?.takeIf { it.isNotEmpty() }
            ?.take(400)
        return ParsedResponse(code, suggestion, repaired)
    }

    /** What to show as "code so far" while the response is still streaming. */
    fun streamingCode(partial: String): String {
        val (codePart, _) = split(partial)
        return codePart
            .replace(Regex("^\\s*```[a-zA-Z]*\\s*\\n?"), "")
            .substringBefore("\n```")
            .trimStart()
    }

    /** True once the model has moved on from the code to the suggestion. */
    fun codeFinished(partial: String): Boolean = SUGGESTION_MARKERS.any { partial.contains(it) }

    private fun split(text: String): Pair<String, String?> {
        var body = text
        CODE_MARKERS.firstOrNull { body.contains(it) }?.let { marker -> body = body.substringAfter(marker) }
        val sMarker = SUGGESTION_MARKERS.firstOrNull { body.contains(it) }
        return if (sMarker != null) body.substringBefore(sMarker) to body.substringAfter(sMarker) else body to null
    }

    /** Joins every fenced block in order (chat UIs often split one file into several blocks). */
    private fun stripFences(s: String): String {
        val fence = Regex("```[a-zA-Z0-9]*[ \\t]*\\n([\\s\\S]*?)(?:```|$)")
        val blocks = fence.findAll(s).map { it.groupValues[1].trimEnd() }.filter { it.isNotBlank() }.toList()
        return if (blocks.isEmpty()) s.replace("```", "") else blocks.joinToString("\n")
    }

    private fun extractHtml(raw: String): Pair<String?, Boolean> {
        var s = raw.trim()
        if (s.contains("```")) s = stripFences(s).trim()
        val lower = s.lowercase()
        val start = listOf(lower.indexOf("<!doctype"), lower.indexOf("<html")).filter { it >= 0 }.minOrNull()
        if (start != null) {
            val end = lower.lastIndexOf("</html>")
            s = if (end > start) s.substring(start, end + "</html>".length) else s.substring(start)
            return s.trim().takeIf { it.length > 20 } to false
        }
        if (!Regex("</?(body|div|canvas|script|style|main|section|button)", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return null to false
        }
        return repair(s).takeIf { s.length > 20 } to true
    }

    /** Turns an HTML fragment into a runnable document, re-adding lost <style>/<script> openers. */
    private fun repair(fragment: String): String {
        var s = fragment
        if (s.contains("</style>", true) && !s.contains("<style", true)) s = "<style>\n$s"
        if (s.contains("</script>", true) && !s.contains("<script", true)) s = "<script>\n$s"
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">")
            appendLine("</head>")
            appendLine("<body>")
            appendLine(s)
            appendLine("</body>")
            append("</html>")
        }
    }

    /** Rough check that a pasted file is whole: document wrapper present and tags balanced. */
    fun looksComplete(code: String): Boolean {
        val lower = code.lowercase()
        fun count(tag: String) = Regex(tag).findAll(lower).count()
        return (lower.contains("<!doctype") || lower.contains("<html")) &&
            lower.contains("</html>") &&
            count("<script[\\s>]") == count("</script>") &&
            count("<style[\\s>]") == count("</style>")
    }
}
