package com.gonzaroman.acornix.device

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gonzaroman.acornix.MainActivity
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.web.AppLauncher

/**
 * The places outside Acornix where shortcuts can be run from, like on an iPhone: the menu that appears when the
 * Acornix icon is long-pressed, a home screen widget, and a Quick Settings tile.
 */
object ShortcutLaunchers {
    private const val MAX_ICON_SHORTCUTS = 4
    private const val PREFS = "shortcut_launchers"
    private const val KEY_TILE = "tile_app"

    /** The user's shortcuts, the ones opened most recently first. */
    fun shortcuts(context: Context): List<AppMeta> =
        context.container.apps.apps.value.filter { it.kind == AppKind.FEATURE }.sortedByDescending { maxOf(it.lastOpenedAt, it.createdAt) }

    /** Call when the list of apps changes. */
    fun refresh(context: Context) {
        val list = shortcuts(context)
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, list.take(MAX_ICON_SHORTCUTS).map { app ->
                ShortcutInfoCompat.Builder(context, "run_${app.id}")
                    .setShortLabel(app.name.take(24))
                    .setLongLabel(app.name)
                    .setIcon(IconCompat.createWithAdaptiveBitmap(ShortcutHelper.iconBitmap(app, context.container.apps)))
                    .setIntent(AppLauncher.intent(context, app.id, "icon"))
                    .build()
            })
        }
        ShortcutsWidget.updateAll(context)
        runCatching { TileService.requestListeningState(context, ComponentName(context, ShortcutTileService::class.java)) }
    }

    fun tileApp(context: Context): AppMeta? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TILE, null)?.let { context.container.apps.get(it) }

    fun setTileApp(context: Context, appId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TILE, appId).apply()
        runCatching { TileService.requestListeningState(context, ComponentName(context, ShortcutTileService::class.java)) }
    }
}

/** A Quick Settings tile that runs the shortcut chosen with "Put in Quick Settings". */
class ShortcutTileService : TileService() {
    override fun onStartListening() {
        val app = ShortcutLaunchers.tileApp(this)
        qsTile?.apply {
            label = app?.let { "${it.emoji} ${it.name}" } ?: getString(R.string.tab_shortcuts)
            state = if (app != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val app = ShortcutLaunchers.tileApp(this)
        val intent = if (app != null) AppLauncher.intent(this, app.id, "tile") else Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

/** A home screen widget with the user's four most recent shortcuts, each one a button. */
class ShortcutsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private val CELLS = listOf(
            Triple(R.id.widget_cell_0, R.id.widget_emoji_0, R.id.widget_name_0),
            Triple(R.id.widget_cell_1, R.id.widget_emoji_1, R.id.widget_name_1),
            Triple(R.id.widget_cell_2, R.id.widget_emoji_2, R.id.widget_name_2),
            Triple(R.id.widget_cell_3, R.id.widget_emoji_3, R.id.widget_name_3),
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, ShortcutsWidget::class.java)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_shortcuts)
            val list = ShortcutLaunchers.shortcuts(context).take(CELLS.size)
            CELLS.forEachIndexed { i, (cell, emoji, name) ->
                val app = list.getOrNull(i)
                if (app == null && i > 0) {
                    views.setViewVisibility(cell, android.view.View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(cell, android.view.View.VISIBLE)
                views.setTextViewText(emoji, app?.emoji ?: "⚡")
                views.setTextViewText(name, app?.name ?: context.getString(R.string.shortcuts_new))
                val intent = (if (app != null) AppLauncher.intent(context, app.id, "widget") else Intent(context, MainActivity::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(cell, PendingIntent.getActivity(context, 100 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            return views
        }
    }
}
