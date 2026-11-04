package com.gonzaroman.acornix.ui.shortcuts

import androidx.annotation.StringRes
import com.gonzaroman.acornix.R

/** How a setting of an action is edited. */
enum class FieldKind { TEXT, NUMBER, PERCENT, TIME, PHONE, CHOICE, APP }

data class FieldDef(
    val key: String,
    @StringRes val label: Int,
    val kind: FieldKind,
    val default: String = "",
    /** For [FieldKind.CHOICE]: stored value to its label. */
    val choices: List<Pair<String, Int>> = emptyList(),
    /** Texts can use {time}, {date}, {location} and {battery}. */
    val variables: Boolean = false,
)

data class ActionDef(val id: String, val emoji: String, @StringRes val label: Int, @StringRes val group: Int, val fields: List<FieldDef> = emptyList()) {
    fun defaults(): Map<String, String> = fields.associate { it.key to it.default }
}

/** Every action a step-by-step shortcut can use, in the order the picker shows them. */
val SHORTCUT_ACTIONS = listOf(
    ActionDef("volume", "🔊", R.string.sa_volume, R.string.sg_sound, listOf(
        FieldDef("level", R.string.sf_level, FieldKind.PERCENT, "50"),
        FieldDef("stream", R.string.sf_stream, FieldKind.CHOICE, "media", listOf(
            "media" to R.string.sc_media, "ring" to R.string.sc_ring, "alarm" to R.string.sc_alarm,
        )),
    )),
    ActionDef("ringer", "🔕", R.string.sa_ringer, R.string.sg_sound, listOf(
        FieldDef("mode", R.string.sf_mode, FieldKind.CHOICE, "silent", listOf(
            "normal" to R.string.sc_sound_on, "vibrate" to R.string.sc_vibrate_only, "silent" to R.string.sc_silent,
        )),
    )),
    ActionDef("dnd", "🌙", R.string.sa_dnd, R.string.sg_sound, listOf(
        FieldDef("on", R.string.sf_mode, FieldKind.CHOICE, "on", listOf("on" to R.string.sc_on, "off" to R.string.sc_off)),
    )),
    ActionDef("media", "🎵", R.string.sa_media, R.string.sg_sound, listOf(
        FieldDef("action", R.string.sf_mode, FieldKind.CHOICE, "play", listOf(
            "play" to R.string.sc_play, "pause" to R.string.sc_pause, "next" to R.string.sc_next, "previous" to R.string.sc_previous,
        )),
    )),
    ActionDef("brightness", "☀️", R.string.sa_brightness, R.string.sg_screen, listOf(FieldDef("level", R.string.sf_level, FieldKind.PERCENT, "30"))),
    ActionDef("flashlight", "🔦", R.string.sa_flashlight, R.string.sg_screen, listOf(
        FieldDef("mode", R.string.sf_mode, FieldKind.CHOICE, "on", listOf("on" to R.string.sc_on, "off" to R.string.sc_off, "sos" to R.string.sc_sos)),
    )),
    ActionDef("vibrate", "📳", R.string.sa_vibrate, R.string.sg_screen),
    ActionDef("alarm", "⏰", R.string.sa_alarm, R.string.sg_time, listOf(
        FieldDef("time", R.string.sf_time, FieldKind.TIME, "07:00"),
        FieldDef("label", R.string.sf_label, FieldKind.TEXT),
    )),
    ActionDef("timer", "⏲️", R.string.sa_timer, R.string.sg_time, listOf(
        FieldDef("minutes", R.string.sf_minutes, FieldKind.NUMBER, "10"),
        FieldDef("label", R.string.sf_label, FieldKind.TEXT),
    )),
    ActionDef("wait", "⏳", R.string.sa_wait, R.string.sg_time, listOf(FieldDef("seconds", R.string.sf_seconds, FieldKind.NUMBER, "5"))),
    ActionDef("whatsapp", "💬", R.string.sa_whatsapp, R.string.sg_messages, listOf(
        FieldDef("number", R.string.sf_number_optional, FieldKind.PHONE),
        FieldDef("text", R.string.sf_message, FieldKind.TEXT, variables = true),
    )),
    ActionDef("sms", "✉️", R.string.sa_sms, R.string.sg_messages, listOf(
        FieldDef("number", R.string.sf_number, FieldKind.PHONE),
        FieldDef("text", R.string.sf_message, FieldKind.TEXT, variables = true),
    )),
    ActionDef("call", "📞", R.string.sa_call, R.string.sg_messages, listOf(FieldDef("number", R.string.sf_number, FieldKind.PHONE))),
    ActionDef("speak", "🗣️", R.string.sa_speak, R.string.sg_messages, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
    ActionDef("notify", "🔔", R.string.sa_notify, R.string.sg_messages, listOf(
        FieldDef("title", R.string.sf_title, FieldKind.TEXT, variables = true),
        FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true),
    )),
    ActionDef("launchApp", "📱", R.string.sa_launch_app, R.string.sg_apps, listOf(FieldDef("name", R.string.sf_app_name, FieldKind.APP))),
    ActionDef("maps", "🗺️", R.string.sa_maps, R.string.sg_apps, listOf(FieldDef("query", R.string.sf_place, FieldKind.TEXT, variables = true))),
    ActionDef("openUrl", "🌐", R.string.sa_open_url, R.string.sg_apps, listOf(FieldDef("url", R.string.sf_url, FieldKind.TEXT))),
    ActionDef("copy", "📋", R.string.sa_copy, R.string.sg_apps, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
    ActionDef("share", "📤", R.string.sa_share, R.string.sg_apps, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
)

fun shortcutAction(id: String): ActionDef? = SHORTCUT_ACTIONS.firstOrNull { it.id == id }
