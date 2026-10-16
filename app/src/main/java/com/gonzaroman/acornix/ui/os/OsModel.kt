package com.gonzaroman.acornix.ui.os

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gonzaroman.acornix.MainActivity
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.data.AppMeta

/** Built-in entries of the desktop: they open Acornix's own screens. */
enum class SystemApp(val key: String, val emoji: String, @StringRes val label: Int, val screen: String) {
    CREATE("sys:create", "✨", R.string.os_new_app, MainActivity.SCREEN_CREATE),
    ASSISTANT("sys:assistant", "🤖", R.string.os_assistant, MainActivity.SCREEN_ASSISTANT),
    APPS("sys:apps", "📱", R.string.os_my_apps, MainActivity.SCREEN_APPS),
    SETTINGS("sys:settings", "⚙️", R.string.os_settings, MainActivity.SCREEN_SETTINGS);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

fun appKey(appId: String) = "app:$appId"

/** One app open in a window. Positions are in pixels inside the desktop area. */
class OsWindow(val id: Int, val appId: String) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    var maximized by mutableStateOf(false)
    var minimized by mutableStateOf(false)
}

/** Dock contents and wallpaper, remembered between sessions. */
class OsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("acornix_os", Context.MODE_PRIVATE)

    fun dock(apps: List<AppMeta>): List<String> {
        val saved = prefs.getString(KEY_DOCK, null)
        if (saved != null) return saved.split('\n').filter { it.isNotBlank() }
        // First time: the essentials plus the two most recently opened apps.
        val recent = apps.sortedByDescending { maxOf(it.lastOpenedAt, it.updatedAt) }.take(2).map { appKey(it.id) }
        return listOf(SystemApp.CREATE.key, SystemApp.ASSISTANT.key) + recent
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
