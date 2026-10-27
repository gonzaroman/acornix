package com.gonzaroman.acornix.agent

import android.content.Context
import com.gonzaroman.acornix.ai.ToolCall
import com.gonzaroman.acornix.ai.ToolResult
import com.gonzaroman.acornix.ai.ToolSpec
import com.gonzaroman.acornix.ai.bool
import com.gonzaroman.acornix.ai.int
import com.gonzaroman.acornix.ai.str
import com.gonzaroman.acornix.ai.tool
import com.gonzaroman.acornix.control.AcornixAccessibilityService
import com.gonzaroman.acornix.core.AppGenerator
import com.gonzaroman.acornix.core.GenerationException
import com.gonzaroman.acornix.core.GenerationProgress
import com.gonzaroman.acornix.core.GenerationRequest
import com.gonzaroman.acornix.data.AppDataStore
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.DocQuery
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.AppRepository
import com.gonzaroman.acornix.device.DeviceActions
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Callbacks the tools use to talk to the chat UI. */
interface ToolHost {
    /** Asks the user to approve a sensitive action; [message] is shown as is. */
    suspend fun confirm(message: String): Boolean
    fun appGenerationStarted(name: String, emoji: String): Long
    fun appGenerationProgress(key: Long, progress: GenerationProgress)
    fun appGenerationFinished(key: Long, app: AppMeta?, suggestion: String?, error: String?)
}

/** The assistant's tools: one definition, translated by each provider into its own format. */
class AgentTools(
    private val context: Context,
    private val device: DeviceActions,
    private val repo: AppRepository,
    private val generator: AppGenerator,
    private val data: AppDataStore,
) {
    private val control get() = AcornixAccessibilityService.instance

    fun specs(controlEnabled: Boolean): List<ToolSpec> = APP_TOOLS + DATA_TOOLS + DIRECT_TOOLS + if (controlEnabled) CONTROL_TOOLS else emptyList()

    suspend fun execute(call: ToolCall, host: ToolHost): ToolResult {
        val a = call.arguments
        fun ok(text: String) = ToolResult(call, text)
        fun fail(text: String) = ToolResult(call, text, isError = true)
        fun done(success: Boolean, okText: String, failText: String) = if (success) ok(okText) else fail(failText)

        return try {
            when (call.name) {
                // ----- Apps -----
                "create_web_app" -> {
                    val description = a.str("description").orEmpty()
                    if (description.isBlank()) return fail("description is required")
                    val kind = if (a.str("kind") == "functionality") AppKind.FEATURE else AppKind.APP
                    val name = a.str("name").orEmpty().ifBlank { AppGenerator.suggestName(description) }
                    val key = host.appGenerationStarted(name, AppRepository.guessEmoji("$name $description", kind))
                    try {
                        val result = generator.generate(GenerationRequest(description, name, kind)) { host.appGenerationProgress(key, it) }
                        host.appGenerationFinished(key, result.app, result.suggestion, null)
                        ok("Created app \"${result.app.name}\" (id: ${result.app.id})." +
                            (result.suggestion?.let { " Suggested next improvement: $it" } ?: ""))
                    } catch (e: GenerationException) {
                        host.appGenerationFinished(key, null, null, e.message)
                        fail("Could not create the app: ${e.message}")
                    }
                }
                "improve_app" -> {
                    val id = a.str("app_id").orEmpty()
                    val app = repo.get(id) ?: return fail("No app with id '$id'. Call list_my_apps.")
                    val change = a.str("change").orEmpty()
                    val key = host.appGenerationStarted(app.name, app.emoji)
                    try {
                        val result = generator.generate(GenerationRequest(change, improveAppId = id)) { host.appGenerationProgress(key, it) }
                        host.appGenerationFinished(key, result.app, result.suggestion, null)
                        ok("Updated \"${app.name}\" (now version ${result.app.versionCount})." +
                            (result.suggestion?.let { " Suggested next improvement: $it" } ?: ""))
                    } catch (e: GenerationException) {
                        host.appGenerationFinished(key, null, null, e.message)
                        fail("Could not update the app: ${e.message}")
                    }
                }
                "list_my_apps" -> ok(appListText(repo.apps.value))
                "open_my_app" -> {
                    val q = a.str("app_id").orEmpty()
                    val app = repo.get(q) ?: repo.apps.value.firstOrNull { it.name.equals(q, true) }
                        ?: return fail("No app '$q'. Call list_my_apps.")
                    AppLauncher.open(context, app.id)
                    ok("Opened \"${app.name}\" full screen.")
                }

                // ----- Data of the user's apps -----
                "app_data_overview" -> {
                    val app = findApp(a.str("app_id")) ?: return fail("No app '${a.str("app_id")}'. Call list_my_apps.")
                    ok(data.describeForAi(app.id)?.let { "Data of \"${app.name}\":\n$it" } ?: "\"${app.name}\" has no saved data yet.")
                }
                "query_app_data" -> {
                    val app = findApp(a.str("app_id")) ?: return fail("No app '${a.str("app_id")}'.")
                    val collection = a.str("collection").orEmpty()
                    if (collection == "keys") {
                        return ok(data.kvAll(app.id).entries.joinToString("\n") { "${it.key} = ${it.value}" }.ifEmpty { "(no keys)" })
                    }
                    val where = a.str("where_json")?.takeIf { it.isNotBlank() }?.let { parseObject(it) ?: return fail("where_json is not a JSON object") }
                    val docs = data.list(app.id, collection, DocQuery(where = where, orderBy = a.str("order_by"), limit = (a.int("limit") ?: 50).coerceIn(1, 200)))
                    ok(if (docs.isEmpty()) "(no documents)" else docs.joinToString("\n").take(12_000))
                }
                "add_app_data" -> {
                    val app = findApp(a.str("app_id")) ?: return fail("No app '${a.str("app_id")}'.")
                    val doc = parseObject(a.str("document_json").orEmpty()) ?: return fail("document_json must be a JSON object")
                    ok("Added: " + data.add(app.id, a.str("collection").orEmpty(), doc, AppDataStore.SOURCE_ASSISTANT))
                }
                "update_app_data" -> {
                    val app = findApp(a.str("app_id")) ?: return fail("No app '${a.str("app_id")}'.")
                    val patch = parseObject(a.str("changes_json").orEmpty()) ?: return fail("changes_json must be a JSON object")
                    val collection = a.str("collection").orEmpty()
                    val id = a.str("document_id").orEmpty()
                    if (collection == "keys") {
                        patch.forEach { (k, v) -> data.kvSet(app.id, k, v, AppDataStore.SOURCE_ASSISTANT) }
                        ok("Keys updated.")
                    } else {
                        ok("Updated: " + data.update(app.id, collection, id, patch, AppDataStore.SOURCE_ASSISTANT))
                    }
                }
                "delete_app_data" -> {
                    val app = findApp(a.str("app_id")) ?: return fail("No app '${a.str("app_id")}'.")
                    val collection = a.str("collection").orEmpty()
                    val id = a.str("document_id").orEmpty()
                    val doc = data.get(app.id, collection, id) ?: return fail("Document not found")
                    val label = context.getString(com.gonzaroman.acornix.R.string.confirm_delete_data, doc.toString().take(120), app.name)
                    if (!host.confirm(label)) return fail("The user did NOT approve deleting this document.")
                    data.remove(app.id, collection, id, AppDataStore.SOURCE_ASSISTANT)
                    ok("Deleted.")
                }

                // ----- Direct actions -----
                "open_url" -> afterLaunch(call, device.openUrl(a.str("url").orEmpty()), "Opened the link.", "No app can open that link.")
                "youtube_search" -> afterLaunch(call, device.youtubeSearch(a.str("query").orEmpty()), "Opened YouTube search results.", "Could not open YouTube.")
                "web_search" -> afterLaunch(call, device.webSearch(a.str("query").orEmpty()), "Opened web search.", "Could not open a browser.")
                "open_maps" -> afterLaunch(call, device.openMaps(a.str("query").orEmpty()), "Opened maps.", "Could not open maps.")
                "open_app" -> {
                    val label = device.openApp(a.str("name").orEmpty())
                    if (label == null) fail("App not found. Use list_installed_apps to see the exact names.")
                    else afterLaunch(call, true, "Opened $label.", "")
                }
                "list_installed_apps" -> ok(device.installedApps().joinToString("\n") { "${it.label} (${it.packageName})" })
                "flashlight" -> {
                    val on = a.bool("on") ?: true
                    done(device.flashlight(on), "Flashlight ${if (on) "on" else "off"}.", "This phone has no usable flashlight.")
                }
                "flashlight_morse" -> {
                    if (!device.hasFlashlight()) return fail("This phone has no usable flashlight.")
                    val text = a.str("text").orEmpty().ifBlank { "SOS" }
                    device.flashlightMorse(text, a.int("repeat") ?: 1)
                    ok("Blinked \"$text\" in Morse code.")
                }
                "vibrate" -> {
                    device.vibrate(listOf((a.int("duration_ms") ?: 500).toLong()))
                    ok("Vibrated.")
                }
                "speak" -> {
                    device.speak(a.str("text").orEmpty(), a.str("language"))
                    ok("Speaking.")
                }
                "set_alarm" -> done(
                    device.setAlarm(a.int("hour") ?: 7, a.int("minute") ?: 0, a.str("label")),
                    "Alarm set.", "No clock app accepted the alarm.",
                )
                "set_timer" -> done(
                    device.setTimer(a.int("seconds") ?: 60, a.str("label")),
                    "Timer started.", "No clock app accepted the timer.",
                )
                "show_notification" -> done(
                    device.notify(a.str("title").orEmpty(), a.str("text").orEmpty()),
                    "Notification shown.", "Notifications are disabled for Acornix.",
                )
                "share_text" -> done(device.shareText(a.str("text").orEmpty()), "Share sheet opened.", "Could not share.")
                "copy_to_clipboard" -> {
                    device.copyToClipboard(a.str("text").orEmpty())
                    ok("Copied.")
                }
                "compose_sms" -> done(
                    device.composeSms(a.str("phone_number").orEmpty(), a.str("message").orEmpty()),
                    "The SMS app is open with the message written. The user must press send.", "No SMS app available.",
                )
                "dial" -> done(device.dial(a.str("phone_number").orEmpty()), "The dialer is open with the number. The user must press call.", "No phone app available.")
                "open_settings" -> afterLaunch(call, device.openSettings(a.str("section")), "Settings opened.", "Could not open settings.")
                "battery_status" -> device.battery().let {
                    ok("Battery ${it.level}%, ${if (it.charging) "charging" else "not charging"}, ${it.temperatureC}°C, health ${it.health}.")
                }
                "device_info" -> ok(device.deviceInfo().entries.joinToString("\n") { "${it.key}: ${it.value}" })
                "wait" -> {
                    delay(((a.int("seconds") ?: 2).coerceIn(1, 15)) * 1000L)
                    control?.let { ok(it.readScreen()) } ?: ok("Waited.")
                }

                // ----- Total control -----
                else -> executeControl(call, host)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            fail("Error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun executeControl(call: ToolCall, host: ToolHost): ToolResult {
        val a = call.arguments
        val svc = control ?: return ToolResult(call, "Total control is off. Ask the user to enable it in Acornix settings.", isError = true)
        suspend fun after(success: Boolean, what: String): ToolResult =
            if (success) ToolResult(call, "$what\n\n${svc.settleAndRead()}")
            else ToolResult(call, "Could not $what. Current screen:\n${svc.readScreen()}", isError = true)

        return when (call.name) {
            "read_screen" -> ToolResult(call, svc.readScreen())
            "screenshot" -> {
                val img = svc.screenshot().getOrElse { return ToolResult(call, it.message.orEmpty(), isError = true) }
                ToolResult(call, "Screenshot attached.", image = img)
            }
            "tap", "long_press" -> {
                val id = a.int("element_id") ?: return ToolResult(call, "element_id is required", isError = true)
                if (!svc.hasElement(id)) return ToolResult(call, "Unknown element [$id]. Call read_screen first.", isError = true)
                val label = svc.elementText(id).orEmpty()
                if (isSensitive(label) && !host.confirm(context.getString(com.gonzaroman.acornix.R.string.confirm_message, label))) {
                    return ToolResult(call, "The user did NOT approve tapping \"$label\". Stop and ask what to do.", isError = true)
                }
                after(svc.tap(id, longPress = call.name == "long_press"), "tap [$id] \"$label\"")
            }
            "tap_text" -> {
                val text = a.str("text").orEmpty()
                if (isSensitive(text) && !host.confirm(context.getString(com.gonzaroman.acornix.R.string.confirm_message, text))) {
                    return ToolResult(call, "The user did NOT approve tapping \"$text\". Stop and ask what to do.", isError = true)
                }
                after(svc.tapText(text), "tap \"$text\"")
            }
            "type_text" -> after(
                svc.typeText(a.int("element_id"), a.str("text").orEmpty(), a.bool("submit") ?: false),
                "type the text",
            )
            "scroll" -> after(svc.scroll(a.str("direction") ?: "down", a.int("element_id")), "scroll ${a.str("direction") ?: "down"}")
            "go_back" -> after(svc.global("back"), "go back")
            "go_home" -> after(svc.global("home"), "go to the home screen")
            "open_recents" -> after(svc.global("recents"), "open recent apps")
            "open_notifications" -> after(svc.global("notifications"), "open the notification shade")
            else -> ToolResult(call, "Unknown tool ${call.name}", isError = true)
        }
    }

    private fun findApp(q: String?): AppMeta? {
        val query = q.orEmpty()
        return repo.get(query) ?: repo.apps.value.firstOrNull { it.name.equals(query, true) }
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    /** After opening something, give the model the new screen when total control is on. */
    private suspend fun afterLaunch(call: ToolCall, success: Boolean, okText: String, failText: String): ToolResult {
        if (!success) return ToolResult(call, failText, isError = true)
        val svc = control ?: return ToolResult(call, okText)
        return ToolResult(call, "$okText\n\n${svc.settleAndRead(1500)}")
    }

    companion object {
        private val SENSITIVE = Regex(
            "\\b(send|enviar|envía|envia|pay|pagar|paga|buy|comprar|compra|purchase|order|pedir|confirm order|delete|borrar|eliminar|remove|quitar|call|llamar|llamada|transfer|transferir|uninstall|desinstalar|factory reset|restablecer|post|publicar|submit)\\b",
            RegexOption.IGNORE_CASE,
        )

        fun isSensitive(label: String) = SENSITIVE.containsMatchIn(label)

        fun appListText(apps: List<AppMeta>): String =
            if (apps.isEmpty()) "(no apps yet)"
            else apps.take(60).joinToString("\n") { "${it.id}: ${it.emoji} ${it.name}" + if (it.kind == AppKind.FEATURE) " (functionality)" else "" }

        val APP_TOOLS = listOf(
            tool("create_web_app", "Creates a new mobile web app or game with AI and saves it in the user's Acornix apps. Takes 30s to a few minutes.") {
                string("description", "Detailed description of what to build: purpose, screens, controls, style.")
                string("name", "Short name for the app", required = false)
                string("kind", "'functionality' if it must use phone features (vibration, flashlight, battery, speech, notifications) through the Acornix bridge", required = false, enum = listOf("app", "functionality"))
            },
            tool("improve_app", "Changes one of the user's existing apps (fix, add features, restyle). A new version is saved.") {
                string("app_id", "The app id from list_my_apps")
                string("change", "What to change, in detail")
            },
            tool("list_my_apps", "Lists the apps the user has created in Acornix."),
            tool("open_my_app", "Opens one of the user's Acornix apps full screen.") {
                string("app_id", "The app id (or exact name)")
            },
        )

        val DATA_TOOLS = listOf(
            tool("app_data_overview", "Shows what data one of the user's apps has saved: collections, fields and keys.") {
                string("app_id", "The app id from list_my_apps")
            },
            tool("query_app_data", "Reads documents saved by one of the user's apps (e.g. tasks in a to-do app, scores in a game). Use collection 'keys' to read its single saved values.") {
                string("app_id", "The app id")
                string("collection", "Collection name from app_data_overview, or 'keys'")
                string("where_json", "Optional filter as a JSON object, e.g. {\"done\":false} or {\"score\":{\"\$gt\":100}}", required = false)
                string("order_by", "Optional field to sort by; prefix with - for descending, e.g. -_created", required = false)
                integer("limit", "Max documents (default 50)", required = false)
            },
            tool("add_app_data", "Adds a document to a collection of one of the user's apps, with the same fields the app already uses (check app_data_overview first). The app refreshes automatically.") {
                string("app_id", "The app id")
                string("collection", "Collection name")
                string("document_json", "The document as a JSON object, e.g. {\"text\":\"Buy bread\",\"done\":false}")
            },
            tool("update_app_data", "Changes fields of a document in one of the user's apps. With collection 'keys', sets single saved values instead.") {
                string("app_id", "The app id")
                string("collection", "Collection name, or 'keys'")
                string("document_id", "The _id of the document (ignored for 'keys')", required = false)
                string("changes_json", "Fields to change as a JSON object, e.g. {\"done\":true}")
            },
            tool("delete_app_data", "Deletes one document from one of the user's apps (asks the user to confirm).") {
                string("app_id", "The app id")
                string("collection", "Collection name")
                string("document_id", "The _id of the document")
            },
        )

        val DIRECT_TOOLS = listOf(
            tool("open_url", "Opens a web page or deep link.") { string("url", "The URL") },
            tool("youtube_search", "Opens YouTube with search results for a query.") { string("query", "What to search for") },
            tool("web_search", "Opens a web search for a query.") { string("query", "What to search for") },
            tool("open_maps", "Opens maps searching for a place or address.") { string("query", "Place, address or route") },
            tool("open_app", "Opens an installed app by its name (e.g. 'WhatsApp', 'Camera', 'Spotify').") { string("name", "App name") },
            tool("list_installed_apps", "Lists the apps installed on the phone."),
            tool("flashlight", "Turns the flashlight on or off.") { boolean("on", "true to turn on, false to turn off") },
            tool("flashlight_morse", "Blinks a message in Morse code with the flashlight (SOS by default).") {
                string("text", "Text to blink, e.g. SOS", required = false)
                integer("repeat", "Times to repeat (1-5)", required = false)
            },
            tool("vibrate", "Vibrates the phone.") { integer("duration_ms", "Duration in milliseconds", required = false) },
            tool("speak", "Says a text out loud with text-to-speech.") {
                string("text", "What to say")
                string("language", "BCP-47 language tag such as es-ES or en-US", required = false)
            },
            tool("set_alarm", "Creates an alarm in the clock app.") {
                integer("hour", "Hour 0-23")
                integer("minute", "Minute 0-59")
                string("label", "Alarm label", required = false)
            },
            tool("set_timer", "Starts a countdown timer in the clock app.") {
                integer("seconds", "Length in seconds")
                string("label", "Timer label", required = false)
            },
            tool("show_notification", "Shows a notification on the phone.") {
                string("title", "Title")
                string("text", "Body text")
            },
            tool("share_text", "Opens the Android share sheet with a text.") { string("text", "Text to share") },
            tool("copy_to_clipboard", "Copies a text to the clipboard.") { string("text", "Text to copy") },
            tool("compose_sms", "Opens the SMS app with a message ready; the user presses send.") {
                string("phone_number", "Phone number")
                string("message", "Message text")
            },
            tool("dial", "Opens the phone dialer with a number; the user presses call.") { string("phone_number", "Phone number") },
            tool("open_settings", "Opens a section of Android settings.") {
                string("section", "Section", required = false, enum = listOf("main", "wifi", "bluetooth", "display", "sound", "battery", "location", "apps", "accessibility", "airplane", "storage"))
            },
            tool("battery_status", "Returns battery level, charging state and temperature."),
            tool("device_info", "Returns phone model, Android version, language and screen size."),
            tool("wait", "Waits a few seconds (e.g. for a page to load) and returns the screen when total control is on.") {
                integer("seconds", "Seconds to wait (1-15)", required = false)
            },
        )

        val CONTROL_TOOLS = listOf(
            tool("read_screen", "Describes the elements on the current screen. Each element has an [id] to use with tap, long_press, type_text and scroll."),
            tool("screenshot", "Takes a screenshot of the current screen so you can see it."),
            tool("tap", "Taps an element from read_screen.") { integer("element_id", "The [id] of the element") },
            tool("tap_text", "Taps the visible element that shows a text.") { string("text", "Visible text of the element") },
            tool("long_press", "Long-presses an element from read_screen.") { integer("element_id", "The [id] of the element") },
            tool("type_text", "Types text into a text field (the focused one if element_id is omitted).") {
                string("text", "Text to type")
                integer("element_id", "The [id] of the text field", required = false)
                boolean("submit", "Press enter/search after typing", required = false)
            },
            tool("scroll", "Scrolls the screen or a list.") {
                string("direction", "Direction", enum = listOf("up", "down", "left", "right"))
                integer("element_id", "The [id] of a scrollable element", required = false)
            },
            tool("go_back", "Presses the Android back button."),
            tool("go_home", "Goes to the home screen."),
            tool("open_recents", "Opens the recent apps screen."),
            tool("open_notifications", "Pulls down the notification shade."),
        )
    }
}
