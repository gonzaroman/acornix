package com.gonzaroman.acornix.ui.shortcuts

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.data.ShortcutTrigger
import com.gonzaroman.acornix.data.TriggerType
import java.util.UUID

private val TYPES = listOf(
    TriggerType.BLUETOOTH_CONNECTED to R.string.tr_bt_on,
    TriggerType.BLUETOOTH_DISCONNECTED to R.string.tr_bt_off,
    TriggerType.CHARGER_CONNECTED to R.string.tr_charger_on,
    TriggerType.CHARGER_DISCONNECTED to R.string.tr_charger_off,
    TriggerType.BATTERY_LOW to R.string.tr_battery,
    TriggerType.WIFI_CONNECTED to R.string.tr_wifi_on,
    TriggerType.WIFI_DISCONNECTED to R.string.tr_wifi_off,
)

private fun label(type: TriggerType) = TYPES.first { it.first == type }.second

/** "When the car's Bluetooth connects…": the events that run [appId] on its own, with a button to add more. */
@Composable
fun TriggersCard(appId: String, triggers: List<ShortcutTrigger>, onAdd: (ShortcutTrigger) -> Unit, onRemove: (ShortcutTrigger) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var setting by remember { mutableStateOf<TriggerType?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text("⚡  " + stringResource(R.string.tr_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.tr_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            triggers.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(describe(t), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(t) }) { Icon(Icons.Rounded.Close, stringResource(R.string.shortcut_remove_step)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, null)
                Text("  " + stringResource(R.string.tr_add))
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.tr_title)) },
            text = {
                LazyColumn {
                    items(TYPES) { (type, label) ->
                        ListItem(headlineContent = { Text(stringResource(label)) }, modifier = Modifier.clickable {
                            adding = false
                            if (type == TriggerType.CHARGER_CONNECTED || type == TriggerType.CHARGER_DISCONNECTED) {
                                onAdd(ShortcutTrigger(UUID.randomUUID().toString(), appId, type))
                            } else {
                                setting = type
                            }
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    setting?.let { type ->
        TriggerValueDialog(type, onDone = { value ->
            setting = null
            if (value != null) onAdd(ShortcutTrigger(UUID.randomUUID().toString(), appId, type, value))
        })
    }
}

@Composable
private fun describe(t: ShortcutTrigger): String {
    val base = stringResource(label(t.type))
    return when {
        t.type == TriggerType.BATTERY_LOW -> "🔋 $base ${t.value.ifBlank { "20" }} %"
        t.type.name.startsWith("BLUETOOTH") -> "🚗 $base: " + t.value.ifBlank { stringResource(R.string.tr_any) }
        t.type.name.startsWith("WIFI") -> "📶 $base: " + t.value.ifBlank { stringResource(R.string.tr_any) }
        else -> "🔌 $base"
    }
}

/** The device, wifi name or battery level a new trigger needs. Calls [onDone] with null when cancelled. */
@Composable
private fun TriggerValueDialog(type: TriggerType, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(if (type == TriggerType.BATTERY_LOW) "20" else "") }
    var devices by remember { mutableStateOf(pairedDevices(context)) }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { devices = pairedDevices(context) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(stringResource(label(type))) },
        text = {
            Column {
                when {
                    type == TriggerType.BATTERY_LOW -> {
                        Text("${value.toIntOrNull() ?: 20} %", style = MaterialTheme.typography.headlineSmall)
                        Slider(value = (value.toFloatOrNull() ?: 20f), onValueChange = { value = it.toInt().toString() }, valueRange = 5f..95f)
                    }
                    type.name.startsWith("BLUETOOTH") -> {
                        if (devices == null) {
                            Text(stringResource(R.string.tr_bt_permission), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { if (Build.VERSION.SDK_INT >= 31) askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                                Text(stringResource(R.string.schedule_overlay_allow))
                            }
                        } else {
                            ListItem(headlineContent = { Text(stringResource(R.string.tr_any)) }, modifier = Modifier.clickable { onDone("") })
                            devices.orEmpty().forEach { name ->
                                ListItem(headlineContent = { Text("🚗 $name") }, modifier = Modifier.clickable { onDone(name) })
                            }
                        }
                    }
                    else -> OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text(stringResource(R.string.tr_wifi_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (!type.name.startsWith("BLUETOOTH")) TextButton(onClick = { onDone(value.trim()) }) { Text(stringResource(R.string.shortcut_save)) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Names of the phone's paired Bluetooth devices, or null until Acornix may read them. */
private fun pairedDevices(context: Context): List<String>? {
    if (Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return null
    return runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices.orEmpty()
            .mapNotNull { it.name }.distinct().sorted()
    }.getOrDefault(emptyList())
}
