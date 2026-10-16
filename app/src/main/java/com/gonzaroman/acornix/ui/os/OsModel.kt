package com.gonzaroman.acornix.ui.os

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzaroman.acornix.data.AppMeta

fun appKey(appId: String) = "app:$appId"

/** One app open in a window. Positions are in pixels inside the desktop area. */
class OsWindow(val id: Int, val appId: String) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    var maximized by mutableStateOf(false)
    var minimized by mutableStateOf(false)

    /** Stacking order. Raising a window changes this instead of reordering the list, which would
     *  move the window in the composition and cancel a drag that has just started on it. */
    var z by mutableFloatStateOf(0f)
}

/** Dock contents and wallpaper, remembered between sessions. */
class OsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("acornix_os", Context.MODE_PRIVATE)

    fun dock(apps: List<AppMeta>): List<String> {
        val saved = prefs.getString(KEY_DOCK, null)
        // Only apps live in the Dock (early betas also pinned shortcuts to Acornix's screens).
        if (saved != null) return saved.split('\n').filter { it.startsWith("app:") }
        // First time: the four most recently used apps.
        return apps.sortedByDescending { maxOf(it.lastOpenedAt, it.updatedAt) }.take(4).map { appKey(it.id) }
    }

    fun saveDock(keys: List<String>) = prefs.edit().putString(KEY_DOCK, keys.joinToString("\n")).apply()

    var wallpaper: Int
        get() = prefs.getInt(KEY_WALLPAPER, 0)
        set(value) = prefs.edit().putInt(KEY_WALLPAPER, value).apply()

    private companion object {
        const val KEY_DOCK = "dock"
        const val KEY_WALLPAPER = "wallpaper"
    }
}
