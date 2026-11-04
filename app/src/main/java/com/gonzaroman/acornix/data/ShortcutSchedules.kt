package com.gonzaroman.acornix.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

/** "Run this shortcut every day at 8:00" (or only some days). [days] are Calendar.MONDAY…SUNDAY. */
@Serializable
data class ShortcutSchedule(
    val appId: String,
    val hour: Int = 8,
    val minute: Int = 0,
    val days: List<Int> = ALL_DAYS,
    val enabled: Boolean = true,
) {
    /** When it runs next, after [now] (epoch ms). */
    fun next(now: Calendar): Long {
        val c = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val allowed = days.ifEmpty { ALL_DAYS }
        repeat(8) {
            if (c.timeInMillis > now.timeInMillis && c.get(Calendar.DAY_OF_WEEK) in allowed) return c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    companion object {
        val ALL_DAYS = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
    }
}

/**
 * Shortcuts that run on their own at a time of day. Each enabled one has an alarm-clock alarm with Android; when it
 * goes off, ScheduleReceiver runs the shortcut and the next alarm is set. They are re-set after a reboot.
 */
class ShortcutSchedules(private val context: Context) {
    private val file = File(context.filesDir, "shortcut_schedules.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<ShortcutSchedule>> = _all.asStateFlow()

    private fun load(): List<ShortcutSchedule> =
        runCatching { json.decodeFromString(ListSerializer(ShortcutSchedule.serializer()), file.readText()) }.getOrDefault(emptyList())

    fun get(appId: String): ShortcutSchedule? = _all.value.firstOrNull { it.appId == appId }

    fun set(schedule: ShortcutSchedule) {
        save(_all.value.filter { it.appId != schedule.appId } + schedule)
        arm(schedule)
    }

    fun remove(appId: String) {
        cancel(appId)
        save(_all.value.filter { it.appId != appId })
    }

    /** After a reboot, an update or a change of time zone. Shortcuts that no longer exist are dropped. */
    fun rearmAll(exists: (String) -> Boolean) {
        val (kept, gone) = _all.value.partition { exists(it.appId) }
        gone.forEach { cancel(it.appId) }
        if (gone.isNotEmpty()) save(kept)
        kept.forEach { arm(it) }
    }

    /** Called when a scheduled run starts: sets the following one. */
    fun ranNow(appId: String) { get(appId)?.let { arm(it) } }

    private fun save(list: List<ShortcutSchedule>) {
        _all.value = list
        runCatching { file.writeText(json.encodeToString(ListSerializer(ShortcutSchedule.serializer()), list)) }
    }

    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun arm(s: ShortcutSchedule) {
        if (!s.enabled) return cancel(s.appId)
        val at = s.next(Calendar.getInstance())
        val run = pending(s.appId)
        // An alarm clock is exact, survives Doze and may open the shortcut from the background.
        runCatching { alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, run), run) }
            .onFailure { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, run) }
    }

    private fun cancel(appId: String) { alarms.cancel(pending(appId)) }

    // A broadcast, not an activity: Android doesn't let an alarm open a screen by itself (ScheduleReceiver does it).
    private fun pending(appId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        appId.hashCode(),
        Intent(context, com.gonzaroman.acornix.web.ScheduleReceiver::class.java)
            .setData(Uri.parse("acornix://schedule/${Uri.encode(appId)}")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
