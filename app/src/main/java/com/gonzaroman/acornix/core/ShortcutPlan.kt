package com.gonzaroman.acornix.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The AI's plan for a shortcut: its name, emoji and steps, or no steps when it needs its own screen. */
@Serializable
data class ShortcutPlan(val name: String = "", val emoji: String = "", val steps: List<ShortcutStep>? = null) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The plan in the AI's answer, if it is one the runtime can run: known actions, closed blocks. */
        fun parse(text: String): ShortcutPlan? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val plan = runCatching { json.decodeFromString(serializer(), text.substring(start, end + 1)) }.getOrNull() ?: return null
            val steps = plan.steps ?: return null
            if (steps.isEmpty() || steps.size > 60 || steps.any { it.action !in ShortcutRecipe.ACTIONS }) return null
            var depth = 0
            for (s in steps) {
                when (s.action) {
                    "if", "repeat" -> depth++
                    "endif", "endrepeat" -> if (--depth < 0) return null
                }
            }
            return if (depth == 0) plan else null
        }
    }
}
