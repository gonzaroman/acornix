package com.gonzaroman.acornix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** What makes a shortcut run on its own, besides a time of day (see [ShortcutSchedule]). */
@Serializable
enum class TriggerType(val needsService: Boolean) {
    BLUETOOTH_CONNECTED(false),
    BLUETOOTH_DISCONNECTED(false),
    CHARGER_CONNECTED(true),
    CHARGER_DISCONNECTED(true),
    BATTERY_LOW(true),
    WIFI_CONNECTED(true),
    WIFI_DISCONNECTED(true),
}

/**
 * "When my car's Bluetooth connects, run Car mode". [value] is the Bluetooth device or wifi name (empty means any)
 * or, for [TriggerType.BATTERY_LOW], the percentage.
 */
@Serializable
data class ShortcutTrigger(val id: String, val appId: String, val type: TriggerType, val value: String = "") {
    fun matches(type: TriggerType, value: String?): Boolean =
        this.type == type && (this.value.isBlank() || this.type == TriggerType.BATTERY_LOW ||
            this.value.trim().equals(value?.trim()?.removeSurrounding("\""), ignoreCase = true))
}

class ShortcutTriggers(private val context: Context) {
    private val file = File(context.filesDir, "shortcut_triggers.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<ShortcutTrigger>> = _all.asStateFlow()

    private fun load(): List<ShortcutTrigger> =
        runCatching { json.decodeFromString(ListSerializer(ShortcutTrigger.serializer()), file.readText()) }.getOrDefault(emptyList())

    fun forApp(appId: String) = _all.value.filter { it.appId == appId }

    fun add(trigger: ShortcutTrigger) = save(_all.value + trigger)

    fun remove(id: String) = save(_all.value.filter { it.id != id })

    fun removeApp(appId: String) = save(_all.value.filter { it.appId != appId })

    /** Shortcuts to run for this event, e.g. (BLUETOOTH_CONNECTED, "Car Multimedia"). */
    fun matching(type: TriggerType, value: String?): List<ShortcutTrigger> = _all.value.filter { it.matches(type, value) }

    /** Charger, battery and wifi events only reach a running app, so they need the automations service. */
    val needsService: Boolean get() = _all.value.any { it.type.needsService }

    private fun save(list: List<ShortcutTrigger>) {
        _all.value = list
        runCatching { file.writeText(json.encodeToString(ListSerializer(ShortcutTrigger.serializer()), list)) }
        com.gonzaroman.acornix.device.AutomationService.sync(context)
    }
}
