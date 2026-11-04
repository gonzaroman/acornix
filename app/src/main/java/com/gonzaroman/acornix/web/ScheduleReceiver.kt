package com.gonzaroman.acornix.web

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container

/**
 * A scheduled shortcut's alarm went off. Android only lets an app open a screen from the background while one of
 * its windows is visible, so with "Display over other apps" allowed a small "⚡ name" chip shows for a moment and
 * the shortcut opens; without it, a notification offers to run it.
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment.orEmpty()
        val c = context.container
        val app = c.apps.get(id)
        if (app == null || !c.apps.indexFile(id).exists()) { c.schedules.remove(id); return }
        c.schedules.ranNow(id)
        val run = AppLauncher.intent(context, id, "schedule").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(context)) {
            val done = goAsync()
            val chip = showChip(context, "${app.emoji}  ${app.name}")
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({ runCatching { context.startActivity(run) } }, 300)
            handler.postDelayed({ chip?.let { runCatching { wm(context).removeView(it) } }; done.finish() }, 2500)
        } else {
            notify(context, id, "${app.emoji} ${app.name}", run)
        }
    }

    private fun wm(context: Context) = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun showChip(context: Context, text: String): TextView? = runCatching {
        val dp = context.resources.displayMetrics.density
        val view = TextView(context).apply {
            this.text = "⚡ $text"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * dp
                setColor(0xE6332A5C.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (48 * dp).toInt() }
        wm(context).addView(view, params)
        view
    }.getOrNull()

    private fun notify(context: Context, id: String, title: String, run: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.schedule_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val tap = PendingIntent.getActivity(context, id.hashCode(), run, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ $title")
            .setContentText(context.getString(R.string.schedule_tap_to_run))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), n) }
    }

    private companion object {
        const val CHANNEL = "schedules"
    }
}

/** Alarms are forgotten when the phone restarts or the clock changes: set them again. */
class ScheduleRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        c.schedules.rearmAll { id -> c.apps.indexFile(id).exists() }
    }
}
