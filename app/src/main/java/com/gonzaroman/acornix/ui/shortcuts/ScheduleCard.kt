package com.gonzaroman.acornix.ui.shortcuts

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.ShortcutSchedule
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * "Run it on its own every day at 8:00": a switch, the time and the days. [schedule] is null while it's off;
 * [appId] is only used to build a new schedule.
 */
@Composable
fun ScheduleCard(appId: String, schedule: ShortcutSchedule?, onChange: (ShortcutSchedule?) -> Unit) {
    val context = LocalContext.current
    val on = schedule?.enabled == true
    val s = schedule ?: ShortcutSchedule(appId)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("⏰  " + stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.schedule_text), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = on, onCheckedChange = { onChange(if (it) s.copy(enabled = true) else null) })
            }
            if (on) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> onChange(s.copy(hour = h, minute = m)) }, s.hour, s.minute, true).show()
                }) { Text("🕗  " + "%02d:%02d".format(s.hour, s.minute)) }
                if (!canOverlay(context)) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.schedule_overlay), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")))
                        }
                    }) { Text(stringResource(R.string.schedule_overlay_allow)) }
                }
                Spacer(Modifier.height(8.dp))
                val names = remember { DateFormatSymbols(Locale.getDefault()).shortWeekdays }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShortcutSchedule.ALL_DAYS.forEach { day ->
                        val selected = day in s.days
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val days = if (selected) s.days - day else (s.days + day).sortedBy { ShortcutSchedule.ALL_DAYS.indexOf(it) }
                                if (days.isNotEmpty()) onChange(s.copy(days = days))
                            },
                            label = { Text(names[day].replace(".", "").replaceFirstChar { it.titlecase() }) },
                        )
                    }
                }
            }
        }
    }
}

/** The same card in a dialog, for shortcuts made by the AI (long-press › Run at a time). */
@Composable
fun ScheduleDialog(app: AppMeta, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    var schedule by remember { mutableStateOf(c.schedules.get(app.id)) }
    val triggers by c.triggers.all.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${app.emoji} ${app.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScheduleCard(app.id, schedule) { schedule = it }
                TriggersCard(app.id, triggers.filter { it.appId == app.id }, onAdd = { c.triggers.add(it) }, onRemove = { c.triggers.remove(it.id) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                schedule?.let { c.schedules.set(it) } ?: c.schedules.remove(app.id)
                onDismiss()
            }) { Text(stringResource(R.string.shortcut_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun canOverlay(context: android.content.Context) = android.provider.Settings.canDrawOverlays(context)

/** "⏰ 08:00" for a tile, or null when it isn't scheduled. */
fun scheduleBadge(schedule: ShortcutSchedule?): String? =
    schedule?.takeIf { it.enabled }?.let { "⏰ %02d:%02d".format(it.hour, it.minute) }
