package com.gonzaroman.acornix.ui.shortcuts

import androidx.annotation.StringRes
import com.gonzaroman.acornix.R

/** How a setting of an action is edited. */
enum class FieldKind { TEXT, NUMBER, PERCENT, TIME, PHONE, CHOICE, APP, MULTILINE, VARNAME }

data class FieldDef(
    val key: String,
    @StringRes val label: Int,
    val kind: FieldKind,
    val default: String = "",
    /** For [FieldKind.CHOICE]: stored value to its label. */
    val choices: List<Pair<String, Int>> = emptyList(),
    /** Texts can use variables: {time}, {date}… and the ones earlier steps define. */
    val variables: Boolean = false,
    /** A default in the user's language (e.g. a variable called "answer" or "respuesta"). */
    @StringRes val defaultRes: Int = 0,
    @StringRes val hint: Int = 0,
)

data class ActionDef(
    val id: String,
    val emoji: String,
    @StringRes val label: Int,
    @StringRes val group: Int,
    val fields: List<FieldDef> = emptyList(),
    /** Block ends ("End if") are added with their block, not from the list. */
    val hidden: Boolean = false,
) {
    fun defaults(context: android.content.Context): Map<String, String> =
        fields.associate { it.key to if (it.defaultRes != 0) context.getString(it.defaultRes) else it.default }
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
    ActionDef("strobe", "🔦", R.string.sa_strobe, R.string.sg_screen, listOf(
        FieldDef("seconds", R.string.sf_seconds, FieldKind.NUMBER, "15", variables = true),
        FieldDef("speed", R.string.sf_speed, FieldKind.CHOICE, "slow", listOf("slow" to R.string.sc_slow, "fast" to R.string.sc_fast)),
    )),
    ActionDef("vibrate", "📳", R.string.sa_vibrate, R.string.sg_screen),
    ActionDef("bluetooth", "🔵", R.string.sa_bluetooth, R.string.sg_screen, listOf(
        FieldDef("on", R.string.sf_mode, FieldKind.CHOICE, "on", listOf("on" to R.string.sc_on, "off" to R.string.sc_off)),
    )),
    ActionDef("alarm", "⏰", R.string.sa_alarm, R.string.sg_time, listOf(
        FieldDef("time", R.string.sf_time, FieldKind.TIME, "07:00"),
        FieldDef("label", R.string.sf_label, FieldKind.TEXT, variables = true),
    )),
    ActionDef("alarmSound", "🚨", R.string.sa_alarm_sound, R.string.sg_time, listOf(
        FieldDef("seconds", R.string.sf_seconds, FieldKind.NUMBER, "30", variables = true),
    )),
    ActionDef("timer", "⏲️", R.string.sa_timer, R.string.sg_time, listOf(
        FieldDef("minutes", R.string.sf_minutes, FieldKind.NUMBER, "10"),
        FieldDef("label", R.string.sf_label, FieldKind.TEXT, variables = true),
    )),
    ActionDef("wait", "⏳", R.string.sa_wait, R.string.sg_time, listOf(FieldDef("seconds", R.string.sf_seconds, FieldKind.NUMBER, "5"))),
    ActionDef("whatsapp", "💬", R.string.sa_whatsapp, R.string.sg_messages, listOf(
        FieldDef("number", R.string.sf_number_optional, FieldKind.PHONE),
        FieldDef("text", R.string.sf_message, FieldKind.TEXT, variables = true),
        FieldDef("send", R.string.sf_send, FieldKind.CHOICE, "no", listOf("no" to R.string.sc_leave_written, "yes" to R.string.sc_send_it)),
    )),
    ActionDef("sms", "✉️", R.string.sa_sms, R.string.sg_messages, listOf(
        FieldDef("number", R.string.sf_number, FieldKind.PHONE),
        FieldDef("text", R.string.sf_message, FieldKind.TEXT, variables = true),
        FieldDef("send", R.string.sf_send, FieldKind.CHOICE, "no", listOf("no" to R.string.sc_leave_written, "yes" to R.string.sc_send_it)),
    )),
    ActionDef("call", "📞", R.string.sa_call, R.string.sg_messages, listOf(FieldDef("number", R.string.sf_number, FieldKind.PHONE))),
    ActionDef("speak", "🗣️", R.string.sa_speak, R.string.sg_messages, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
    ActionDef("notify", "🔔", R.string.sa_notify, R.string.sg_messages, listOf(
        FieldDef("title", R.string.sf_title, FieldKind.TEXT, variables = true),
        FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true),
    )),
    ActionDef("launchApp", "📱", R.string.sa_launch_app, R.string.sg_apps, listOf(FieldDef("name", R.string.sf_app_name, FieldKind.APP))),
    ActionDef("maps", "🗺️", R.string.sa_maps, R.string.sg_apps, listOf(FieldDef("query", R.string.sf_place, FieldKind.TEXT, variables = true))),
    ActionDef("youtube", "▶️", R.string.sa_youtube, R.string.sg_apps, listOf(FieldDef("query", R.string.sf_search, FieldKind.TEXT, variables = true))),
    ActionDef("openUrl", "🌐", R.string.sa_open_url, R.string.sg_apps, listOf(
        FieldDef("url", R.string.sf_url, FieldKind.TEXT, variables = true),
        FieldDef("app", R.string.sf_open_with, FieldKind.APP),
    )),
    ActionDef("copy", "📋", R.string.sa_copy, R.string.sg_apps, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
    ActionDef("share", "📤", R.string.sa_share, R.string.sg_apps, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
    ActionDef("ask", "❓", R.string.sa_ask, R.string.sg_logic, listOf(
        FieldDef("question", R.string.sf_question, FieldKind.TEXT, variables = true),
        FieldDef("type", R.string.sf_answer_type, FieldKind.CHOICE, "text", listOf("text" to R.string.sc_text, "number" to R.string.sc_number)),
        FieldDef("var", R.string.sf_var, FieldKind.VARNAME, defaultRes = R.string.sv_answer),
        FieldDef("remember", R.string.sf_remember, FieldKind.CHOICE, "no", listOf("no" to R.string.sc_every_time, "yes" to R.string.sc_only_once)),
    )),
    ActionDef("menu", "📋", R.string.sa_menu, R.string.sg_logic, listOf(
        FieldDef("question", R.string.sf_question, FieldKind.TEXT, variables = true),
        FieldDef("options", R.string.sf_options, FieldKind.MULTILINE, variables = true, hint = R.string.sf_options_hint),
        FieldDef("var", R.string.sf_var, FieldKind.VARNAME, defaultRes = R.string.sv_option),
    )),
    ActionDef("if", "🔀", R.string.sa_if, R.string.sg_logic, listOf(
        FieldDef("left", R.string.sf_left, FieldKind.TEXT, variables = true),
        FieldDef("op", R.string.sf_condition, FieldKind.CHOICE, "equals", listOf(
            "equals" to R.string.sc_equals, "notEquals" to R.string.sc_not_equals, "contains" to R.string.sc_contains,
            "greater" to R.string.sc_greater, "less" to R.string.sc_less, "empty" to R.string.sc_empty, "notEmpty" to R.string.sc_not_empty,
        )),
        FieldDef("right", R.string.sf_right, FieldKind.TEXT, variables = true),
    )),
    ActionDef("else", "↪️", R.string.sa_else, R.string.sg_logic),
    ActionDef("endif", "🔚", R.string.sa_endif, R.string.sg_logic, hidden = true),
    ActionDef("repeat", "🔁", R.string.sa_repeat, R.string.sg_logic, listOf(FieldDef("times", R.string.sf_times, FieldKind.NUMBER, "3", variables = true))),
    ActionDef("endrepeat", "🔚", R.string.sa_endrepeat, R.string.sg_logic, hidden = true),
    ActionDef("stop", "⏹️", R.string.sa_stop, R.string.sg_logic),
    ActionDef("getUrl", "🌍", R.string.sa_get_url, R.string.sg_data, listOf(
        FieldDef("url", R.string.sf_url, FieldKind.TEXT, variables = true),
        FieldDef("path", R.string.sf_json_path, FieldKind.TEXT, hint = R.string.sf_json_path_hint),
        FieldDef("var", R.string.sf_var, FieldKind.VARNAME, defaultRes = R.string.sv_result),
    )),
    ActionDef("setVar", "🏷️", R.string.sa_set_var, R.string.sg_data, listOf(
        FieldDef("var", R.string.sf_var, FieldKind.VARNAME, defaultRes = R.string.sv_text),
        FieldDef("value", R.string.sf_value, FieldKind.TEXT, variables = true),
    )),
    ActionDef("calc", "🧮", R.string.sa_calc, R.string.sg_data, listOf(
        FieldDef("expr", R.string.sf_expr, FieldKind.TEXT, variables = true, hint = R.string.sf_expr_hint),
        FieldDef("var", R.string.sf_var, FieldKind.VARNAME, defaultRes = R.string.sv_result),
    )),
    ActionDef("show", "💬", R.string.sa_show, R.string.sg_data, listOf(FieldDef("text", R.string.sf_text, FieldKind.TEXT, variables = true))),
)

fun shortcutAction(id: String): ActionDef? = SHORTCUT_ACTIONS.firstOrNull { it.id == id }
