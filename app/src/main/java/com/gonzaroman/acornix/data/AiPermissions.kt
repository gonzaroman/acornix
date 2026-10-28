package com.gonzaroman.acornix.data

import android.content.Context

/**
 * Whether each app may use the user's AI (it spends tokens of their key). Every app asks once, the first
 * time it calls Acornix.askAI, including the user's own: an app doesn't always say it uses the AI.
 */
class AiPermissions(context: Context) {
    private val prefs = context.getSharedPreferences("ai_permissions", Context.MODE_PRIVATE)

    /** true = allowed, false = denied, null = not asked yet. */
    fun get(appId: String): Boolean? = if (prefs.contains(appId)) prefs.getBoolean(appId, false) else null

    fun set(appId: String, allowed: Boolean?) {
        prefs.edit().apply { if (allowed == null) remove(appId) else putBoolean(appId, allowed) }.apply()
    }
}
