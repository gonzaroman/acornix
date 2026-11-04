package com.gonzaroman.acornix.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.gonzaroman.acornix.ai.AiException
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppDataStore
import com.gonzaroman.acornix.data.DocQuery
import com.gonzaroman.acornix.device.MediaExport
import com.gonzaroman.acornix.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest

/** An activity that can end a shortcut: Acornix.done() shows "✓ …" and closes it. */
interface ShortcutHost {
    fun shortcutDone(message: String?)

    /** Turns Bluetooth on the way Android allows (its "Allow" dialog), asking first for permission; waits for the answer. */
    suspend fun enableBluetooth(): Boolean
}

object AppLauncher {
    /**
     * [trigger] says what opened it ("tap", "home", "nfc"…); apps read it as Acornix.trigger. [input] is text shared
     * with the shortcut (a link, a note…), read as Acornix.input.
     */
    fun intent(context: Context, appId: String, trigger: String = "tap", input: String? = null): Intent =
        Intent(context, AppRunnerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("acornix://app/${Uri.encode(appId)}?trigger=${Uri.encode(trigger)}"))
            .apply { if (input != null) putExtra(EXTRA_INPUT, input.take(MAX_INPUT)) }

    const val EXTRA_INPUT = "com.gonzaroman.acornix.INPUT"
    private const val MAX_INPUT = 20_000

    /** The link an NFC sticker holds to open a shortcut. */
    fun link(appId: String): String = "acornix://app/${Uri.encode(appId)}"

    fun open(context: Context, appId: String) {
        context.startActivity(intent(context, appId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Serves each app from its own origin, `https://<app>.appassets.androidplatform.net`, straight from
 * `filesDir/my_apps/<app>` — no local HTTP server needed. A separate origin per app keeps their
 * localStorage/IndexedDB apart and scopes the window.Acornix bridge to the app that is running.
 */
object AcornixWebView {
    private const val BASE_DOMAIN = "appassets.androidplatform.net"
    private const val TAG = "AcornixWeb"
    const val FILES_PATH = "/_files/"

    /** DNS-safe host label for an app id ("castle_defense" -> "castle-defense"). */
    fun hostLabel(appId: String): String {
        val label = appId.lowercase().replace('_', '-')
        val valid = Regex("^[a-z0-9_]+$").matches(appId) && Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$").matches(label)
        if (valid) return label
        val digest = MessageDigest.getInstance("SHA-1").digest(appId.toByteArray())
        return "x0" + digest.take(10).joinToString("") { "%02x".format(it) }
    }

    fun host(appId: String) = "${hostLabel(appId)}.$BASE_DOMAIN"
    fun origin(appId: String) = "https://${host(appId)}"
    fun appUrl(appId: String) = "${origin(appId)}/index.html"
    fun fileUrl(appId: String, name: String) = "${origin(appId)}$FILES_PATH${Uri.encode(name)}"

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        context: Context,
        scope: CoroutineScope,
        appId: String,
        /** What opened the app ("tap", "nfc", "home"…), for Acornix.trigger. */
        trigger: String = "tap",
        /** Text shared with it, for Acornix.input. */
        input: String? = null,
        onPageFinished: () -> Unit = {},
    ) {
        val c = context.container
        val loader = WebViewAssetLoader.Builder()
            .setDomain(host(appId))
            .addPathHandler(FILES_PATH, WebViewAssetLoader.InternalStoragePathHandler(context, c.data.filesDir(appId).apply { mkdirs() }))
            .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(context, c.apps.dir(appId)))
            .build()
        val appHost = host(appId)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
        }
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                // Browsers ask for /favicon.ico on their own; answer quietly when the app has none.
                if (url.host == appHost && url.path == "/favicon.ico" && !java.io.File(c.apps.dir(appId), "favicon.ico").exists()) {
                    return WebResourceResponse("image/x-icon", null, 404, "Not Found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return loader.shouldInterceptRequest(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == appHost) return false
                // Links to the outside world open in the browser (or the matching app).
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && url?.startsWith(origin(appId)) == true) {
                    view.evaluateJavascript(AcornixJsBridge.script(context, trigger, input), null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) = onPageFinished()
        }
        if (webView.webChromeClient == null) {
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(TAG, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
        }
        AcornixJsBridge(context, scope, appId, trigger, input).attach(webView)

        // When someone else (the assistant, the Data screen) changes this app's data, tell the page.
        scope.launch {
            c.data.changes.filter { it.appId == appId && it.source != AppDataStore.SOURCE_APP }.collect { change ->
                val detail = buildJsonObject { put("collection", change.collection); put("source", change.source) }
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('acornixdata',{detail:$detail}))", null)
            }
        }
    }
}

/**
 * `window.Acornix`: the phone bridge for generated apps — device features, the per-app database
 * (`Acornix.db`) and file store (`Acornix.files`). It uses an origin-restricted WebMessageListener,
 * so only the page of [appId], served from its own origin, can call it and only touch its own data.
 */
class AcornixJsBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appId: String,
    private val trigger: String = "tap",
    private val input: String? = null,
) {
    private val origin = AcornixWebView.origin(appId)

    fun attach(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(webView, "AcornixNative", setOf(origin)) { _, message, _, isMainFrame, reply ->
            if (!isMainFrame) return@addWebMessageListener
            val req = runCatching { com.gonzaroman.acornix.ai.AiJson.parseToJsonElement(message.data.orEmpty()).jsonObject }.getOrNull()
                ?: return@addWebMessageListener
            val id = req["id"] ?: JsonNull
            val method = (req["method"] as? JsonPrimitive)?.content.orEmpty()
            val args = (req["args"] as? JsonArray) ?: JsonArray(emptyList())
            scope.launch {
                // Text that streams in (askAI with onText) goes to the page as it arrives, at most every 80 ms.
                var lastSent = 0L
                val partial: (String, Boolean) -> Unit = { text, force ->
                    val now = System.currentTimeMillis()
                    if (force || now - lastSent >= 80) {
                        lastSent = now
                        val msg = buildJsonObject { put("id", id); put("partial", text) }.toString()
                        scope.launch { runCatching { reply.postMessage(msg) } }
                    }
                }
                val response = try {
                    buildJsonObject { put("id", id); put("ok", true); put("result", handle(method, args, partial)) }
                } catch (e: Exception) {
                    buildJsonObject { put("id", id); put("ok", false); put("error", e.message ?: "Error") }
                }
                runCatching { reply.postMessage(response.toString()) }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script(context, trigger, input), setOf(origin))
        }
    }

    /** A file to hand to the phone: the bytes sent by the page, or the app's own saved file with that name. */
    private suspend fun fileArgs(rawName: String, payload: String?): Pair<String, ByteArray> {
        val name = AppDataStore.cleanFileName(rawName)
        val bytes = if (payload != null) {
            Base64.decode(payload.substringAfter("base64,", payload), Base64.DEFAULT)
        } else {
            val file = java.io.File(context.container.data.filesDir(appId), name)
            if (!file.isFile) throw IllegalArgumentException("No saved file called $name")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.readBytes() }
        }
        if (bytes.size > MAX_EXPORT_BYTES) throw IllegalArgumentException("File too large (max ${MAX_EXPORT_BYTES / 1024 / 1024} MB)")
        return name to bytes
    }

    private suspend fun handle(method: String, args: JsonArray, partial: (String, Boolean) -> Unit = { _, _ -> }): JsonElement {
        val c = context.container
        val device = c.device
        fun arg(i: Int): JsonElement? = args.getOrNull(i)?.takeIf { it !is JsonNull }
        fun str(i: Int) = (arg(i) as? JsonPrimitive)?.content
        fun int(i: Int) = (arg(i) as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
        fun obj(i: Int) = arg(i) as? JsonObject ?: throw IllegalArgumentException("Argument ${i + 1} must be an object")
        fun req(i: Int, what: String) = str(i)?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("$what is required")
        val data = c.data
        return when (method) {
            // ----- Database: documents -----
            "db.add" -> data.add(appId, req(0, "collection"), obj(1))
            "db.get" -> data.get(appId, req(0, "collection"), req(1, "id")) ?: JsonNull
            "db.set" -> data.set(appId, req(0, "collection"), req(1, "id"), obj(2))
            "db.update" -> data.update(appId, req(0, "collection"), req(1, "id"), obj(2))
            "db.remove" -> JsonPrimitive(data.remove(appId, req(0, "collection"), req(1, "id")))
            "db.list" -> JsonArray(data.list(appId, req(0, "collection"), DocQuery.from(arg(1))))
            "db.count" -> JsonPrimitive(data.count(appId, req(0, "collection"), arg(1) as? JsonObject))
            "db.clear" -> { data.clear(appId, req(0, "collection")); JsonPrimitive(true) }
            "db.collections" -> buildJsonArray {
                data.collections(appId).forEach { add(buildJsonObject { put("name", it.name); put("count", it.count) }) }
            }
            // ----- Database: key-value -----
            "kv.get" -> data.kvGet(appId, req(0, "key")) ?: JsonNull
            "kv.set" -> { data.kvSet(appId, req(0, "key"), args.getOrNull(1) ?: JsonNull); JsonPrimitive(true) }
            "kv.remove" -> JsonPrimitive(data.kvRemove(appId, req(0, "key")))
            // ----- Files -----
            "files.save" -> {
                val name = req(0, "name")
                val payload = req(1, "data")
                val bytes = Base64.decode(payload.substringAfter("base64,", payload), Base64.DEFAULT)
                val f = data.saveFile(appId, name, bytes)
                buildJsonObject { put("name", f.name); put("size", f.size); put("url", AcornixWebView.fileUrl(appId, f.name)) }
            }
            "files.list" -> buildJsonArray {
                data.listFiles(appId).forEach { f ->
                    add(buildJsonObject {
                        put("name", f.name); put("size", f.size); put("url", AcornixWebView.fileUrl(appId, f.name))
                        put("modified", f.file.lastModified())
                    })
                }
            }
            "files.remove" -> JsonPrimitive(data.deleteFile(appId, req(0, "name")))
            // ----- Files for the rest of the phone: the gallery, Downloads, other apps -----
            "files.export" -> {
                val (name, bytes) = fileArgs(req(0, "name"), str(1))
                if (!c.media.canSave) {
                    // Android 8 and 9: the share sheet lets the user pick where it goes.
                    buildJsonObject { put("name", name); put("shared", c.media.shareBytes(name, bytes)) }
                } else {
                    val f = c.media.save(name, bytes)
                    val where = when (MediaExport.folderFor(MediaExport.mimeFor(f.name))) {
                        MediaExport.PICTURES, MediaExport.MOVIES -> R.string.saved_to_gallery
                        MediaExport.MUSIC -> R.string.saved_to_music
                        else -> R.string.saved_to_downloads
                    }
                    device.toast(context.getString(where, f.name))
                    buildJsonObject { put("name", f.name); put("folder", f.folder); put("uri", f.uri.toString()) }
                }
            }
            "files.share" -> {
                val (name, bytes) = fileArgs(req(0, "name"), str(1))
                JsonPrimitive(c.media.shareBytes(name, bytes))
            }

            "vibrate" -> {
                val a = arg(0)
                val pattern = when (a) {
                    is JsonArray -> a.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                    is JsonPrimitive -> listOf(a.longOrNull ?: 300L)
                    else -> listOf(300L)
                }
                device.vibrate(pattern); JsonPrimitive(true)
            }
            "flashlight" -> {
                val on = (arg(0) as? JsonPrimitive)?.booleanOrNull ?: true
                if (!device.flashlight(on)) throw IllegalStateException("No flashlight available")
                JsonPrimitive(true)
            }
            "speak" -> { device.speak(str(0).orEmpty(), str(1)); JsonPrimitive(true) }
            "toast" -> { device.toast(str(0).orEmpty()); JsonPrimitive(true) }
            "notify" -> JsonPrimitive(device.notify(str(0).orEmpty(), str(1).orEmpty()))
            "openUrl" -> JsonPrimitive(device.openUrl(str(0).orEmpty(), str(1)))
            "youtube" -> JsonPrimitive(device.youtubeSearch(str(0).orEmpty()))
            "share" -> JsonPrimitive(device.shareText(str(0).orEmpty()))
            "copy" -> { device.copyToClipboard(str(0).orEmpty()); JsonPrimitive(true) }
            "battery" -> device.battery().let {
                buildJsonObject { put("level", it.level); put("charging", it.charging); put("temperature", it.temperatureC) }
            }
            "deviceInfo" -> buildJsonObject {
                device.deviceInfo().forEach { (k, v) ->
                    when (v) {
                        is Number -> put(k, v)
                        is Boolean -> put(k, v)
                        else -> put(k, v.toString())
                    }
                }
            }
            "listApps" -> buildJsonArray {
                c.apps.apps.value.forEach { a ->
                    add(buildJsonObject { put("id", a.id); put("name", a.name); put("emoji", a.emoji) })
                }
            }
            "openApp" -> {
                val q = str(0).orEmpty()
                val app = c.apps.get(q) ?: c.apps.apps.value.firstOrNull { it.name.equals(q, true) }
                    ?: throw IllegalArgumentException("App not found: $q")
                AppLauncher.open(context, app.id); JsonPrimitive(true)
            }
            // Shortcuts: what the phone does for you.
            "alarm" -> JsonPrimitive(device.setAlarm(int(0) ?: 7, int(1) ?: 0, str(2)))
            "timer" -> JsonPrimitive(device.setTimer((int(0) ?: 60).coerceAtLeast(1), str(1)))
            "call" -> JsonPrimitive(device.dial(str(0).orEmpty()))
            // With "send" true the SMS goes straight away when Acornix may send SMS; otherwise it opens ready to send.
            "sms" -> {
                val send = (arg(2) as? JsonPrimitive)?.booleanOrNull == true
                if (send && device.sendSms(str(0).orEmpty(), str(1).orEmpty())) JsonPrimitive("sent")
                else JsonPrimitive(device.composeSms(str(0).orEmpty(), str(1).orEmpty()))
            }
            "whatsapp" -> JsonPrimitive(device.whatsapp(str(0), str(1).orEmpty(), (arg(2) as? JsonPrimitive)?.booleanOrNull == true))
            "maps" -> JsonPrimitive(device.openMaps(str(0).orEmpty()))
            // Opened → true. Not installed → Google Play's search for it, and "store".
            "launchApp" -> {
                val q = str(0).orEmpty()
                when {
                    device.openApp(q) != null -> JsonPrimitive(true)
                    device.searchStore(str(1)?.takeIf { it.isNotBlank() } ?: q) -> JsonPrimitive("store")
                    else -> JsonPrimitive(false)
                }
            }
            "volume" -> {
                val level = int(0)
                if (level != null) JsonPrimitive(device.setVolume(level, str(1))) else JsonPrimitive(device.volume(str(1)))
            }
            "ringer" -> JsonPrimitive(device.setRinger(str(0) ?: "normal"))
            "doNotDisturb" -> JsonPrimitive(device.setDoNotDisturb((arg(0) as? JsonPrimitive)?.booleanOrNull ?: true))
            "brightness" -> {
                val level = (int(0) ?: 50).coerceIn(0, 100)
                // Without permission to change the system's brightness, at least this app's screen changes.
                val system = device.setSystemBrightness(level)
                (context as? android.app.Activity)?.let { a ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        a.window.attributes = a.window.attributes.apply { screenBrightness = if (system) -1f else level / 100f }
                    }
                }
                JsonPrimitive(system)
            }
            "keepAwake" -> {
                val on = (arg(0) as? JsonPrimitive)?.booleanOrNull ?: true
                (context as? android.app.Activity)?.let { a ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (on) a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                JsonPrimitive(true)
            }
            "media" -> JsonPrimitive(device.mediaKey(str(0) ?: "toggle"))
            "calendar" -> {
                val o = arg(0) as? JsonObject ?: throw IllegalArgumentException("calendar needs { title, start, end }")
                val start = o["start"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val end = o["end"]?.jsonPrimitive?.longOrNull ?: (start + 3_600_000)
                JsonPrimitive(device.addCalendarEvent(o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), start, end, o["location"]?.jsonPrimitive?.contentOrNull))
            }
            "clipboard" -> JsonPrimitive(device.clipboardText())
            "alarmSound" -> JsonPrimitive(device.playAlarm(int(0) ?: 30))
            "stopAlarm" -> { device.stopAlarm(); JsonPrimitive(true) }
            "bluetooth" -> {
                val on = (arg(0) as? JsonPrimitive)?.booleanOrNull ?: true
                val host = context as? ShortcutHost
                // In a shortcut on screen, wait for the user's answer so the next steps (Maps…) don't cover the dialog.
                if (on && host != null && device.bluetooth(true, dryRun = true) != "done") {
                    JsonPrimitive(if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { host.enableBluetooth() }) "done" else "failed")
                } else {
                    JsonPrimitive(device.bluetooth(on))
                }
            }
            // A web page or API, read by Acornix so the app isn't blocked by CORS. Text only, at most 1 MB.
            "http" -> {
                val url = str(0).orEmpty()
                require(url.startsWith("https://") || url.startsWith("http://")) { "Only http and https addresses" }
                JsonPrimitive(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.gonzaroman.acornix.ai.HttpClients.default.newCall(
                        okhttp3.Request.Builder().url(url).header("User-Agent", "Acornix").build(),
                    ).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                        val source = r.body?.source() ?: return@use ""
                        source.request(1_048_577)
                        if (source.buffer.size > 1_048_576) throw IllegalStateException("The answer is too big")
                        source.buffer.readUtf8()
                    }
                })
            }
            "done" -> {
                val host = context as? ShortcutHost
                if (host != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { host.shortcutDone(str(0)) }
                else device.toast("✓ " + (str(0) ?: ""))
                JsonPrimitive(true)
            }
            "askAI" -> {
                if (!AiPermissionPrompt.ensureConfigured(context, appId)) {
                    throw IllegalStateException("No AI is set up yet. Connect your AI in Acornix › Settings.")
                }
                if (!AiPermissionPrompt.ensure(context, appId)) {
                    throw IllegalStateException("You haven't allowed this app to use your AI. You can allow it in its options in Acornix (long-press the app).")
                }
                val options = AskAiOptions.from(arg(1))
                val images = options.images.map { AskAi.toImage(it) ?: throw IllegalArgumentException("Could not read the image") }
                val sofar = StringBuilder()
                val text = try {
                    c.settings.activeProvider().streamText(AskAi.system(options), AskAi.prompt(str(0).orEmpty(), options), images, quick = options.quick) { delta ->
                        if (options.stream) partial(sofar.append(delta).toString(), false)
                    }.text.also { if (options.stream) partial(it, true) }
                } catch (e: AiException) {
                    throw IllegalStateException(e.message)
                }
                if (options.json) AskAi.extractJson(text) ?: throw IllegalStateException("The AI didn't answer with valid JSON")
                else JsonPrimitive(text)
            }
            else -> throw IllegalArgumentException("Unknown method: $method")
        }
    }

    companion object {
        private const val MAX_EXPORT_BYTES = 50 * 1024 * 1024

        /** The language chosen in Acornix ("es", "en"…), or the phone's when it is set to automatic. */
        fun language(context: Context): String {
            val setting = runCatching { kotlinx.coroutines.runBlocking { context.container.settings.current().appLanguage } }.getOrNull()
            return when (setting) {
                com.gonzaroman.acornix.data.AppLanguage.SPANISH -> "es"
                com.gonzaroman.acornix.data.AppLanguage.ENGLISH -> "en"
                else -> java.util.Locale.getDefault().language
            }
        }

        /** The shim plus Acornix.language (to show the app in the language picked in Acornix) and Acornix.trigger. */
        fun script(context: Context, trigger: String = "tap", input: String? = null): String = SHIM +
            "\nif (window.Acornix) { window.Acornix.language = '" + language(context).filter { it.isLetter() }.take(8) + "';" +
            " window.Acornix.trigger = '" + trigger.filter { it.isLetterOrDigit() }.take(16) + "';" +
            // JSON-encoded, so any shared text is a safe JS string.
            " window.Acornix.input = " + kotlinx.serialization.json.JsonPrimitive(input.orEmpty()).toString().replace("</", "<\\/") + "; }"

        /** Injected before any page script runs; builds the Promise-based API on top of AcornixNative. */
        val SHIM = """
            (function () {
              if (window.Acornix || !window.AcornixNative) return;
              var nativeBridge = window.AcornixNative, pending = {}, seq = 0;
              nativeBridge.onmessage = function (e) {
                var m; try { m = JSON.parse(e.data); } catch (err) { return; }
                var p = pending[m.id]; if (!p) return;
                if (m.partial !== undefined) { if (p.onText) { try { p.onText(m.partial); } catch (err) {} } return; }
                delete pending[m.id];
                if (m.ok) p.resolve(m.result); else p.reject(new Error(m.error));
              };
              function call(method, args, onText) {
                return new Promise(function (resolve, reject) {
                  var id = ++seq; pending[id] = { resolve: resolve, reject: reject, onText: onText };
                  nativeBridge.postMessage(JSON.stringify({ id: id, method: method, args: args || [] }));
                });
              }
              // Any picture an app may have, as a JPEG data URL no larger than the AI needs.
              function imageToDataUrl(x) {
                return new Promise(function (resolve, reject) {
                  function draw(src, w, h) {
                    try {
                      var scale = Math.min(1, 1568 / Math.max(w, h)), c = document.createElement('canvas');
                      c.width = Math.max(1, Math.round(w * scale)); c.height = Math.max(1, Math.round(h * scale));
                      c.getContext('2d').drawImage(src, 0, 0, c.width, c.height);
                      resolve(c.toDataURL('image/jpeg', 0.85));
                    } catch (e) { reject(new Error('Could not read the image')); }
                  }
                  function load(url, revoke) {
                    var img = new Image();
                    img.onload = function () { draw(img, img.naturalWidth, img.naturalHeight); if (revoke) URL.revokeObjectURL(url); };
                    img.onerror = function () { reject(new Error('Could not read the image')); };
                    img.src = url;
                  }
                  if (typeof x === 'string') return load(x, false);
                  if (typeof Blob !== 'undefined' && x instanceof Blob) return load(URL.createObjectURL(x), true);
                  if (x && x.videoWidth) return draw(x, x.videoWidth, x.videoHeight);
                  if (x && x.naturalWidth) return draw(x, x.naturalWidth, x.naturalHeight);
                  if (x && x.width && x.height) return draw(x, x.width, x.height);
                  reject(new Error('Could not read the image'));
                });
              }
              // Files travel as data URLs; a Blob is read first. No data means the app's saved file of that name.
              function withFile(method, name, data) {
                var send = function (d) { return call(method, [String(name), d]); };
                if (data === undefined || data === null) return send(null);
                if (typeof Blob !== 'undefined' && data instanceof Blob) {
                  return new Promise(function (resolve, reject) {
                    var r = new FileReader();
                    r.onload = function () { send(r.result).then(resolve, reject); };
                    r.onerror = function () { reject(r.error); };
                    r.readAsDataURL(data);
                  });
                }
                if (data && typeof data.toDataURL === 'function') return send(data.toDataURL(/\.png$/i.test(name) ? 'image/png' : 'image/jpeg', 0.92));
                return send(String(data));
              }
              // askAI(prompt, system) still works; options: { system, images, json, quick, history, onText }.
              function askAI(p, o) {
                if (!o || typeof o !== 'object') return call('askAI', [String(p), o || null]);
                var copy = {}, k;
                for (k in o) if (k !== 'onText' && k !== 'images') copy[k] = o[k];
                var onText = typeof o.onText === 'function' ? o.onText : null;
                if (onText) copy.stream = true;
                var images = o.images ? [].concat(o.images).slice(0, 4) : [];
                return Promise.all(images.map(imageToDataUrl)).then(function (urls) {
                  if (urls.length) copy.images = urls;
                  return call('askAI', [String(p), copy], onText);
                });
              }
              window.Acornix = {
                vibrate: function (p) { return call('vibrate', [p]); },
                flashlight: function (on) { return call('flashlight', [on !== false]); },
                speak: function (t, l) { return call('speak', [String(t), l || null]); },
                toast: function (m) { return call('toast', [String(m)]); },
                notify: function (t, b) { return call('notify', [String(t), String(b || '')]); },
                openUrl: function (u, app) { return call('openUrl', [String(u), app ? String(app) : null]); },
                youtube: function (q) { return call('youtube', [String(q)]); },
                share: function (t) { return call('share', [String(t)]); },
                copy: function (t) { return call('copy', [String(t)]); },
                battery: function () { return call('battery'); },
                deviceInfo: function () { return call('deviceInfo'); },
                listApps: function () { return call('listApps'); },
                openApp: function (n) { return call('openApp', [String(n)]); },
                // Shortcuts
                alarm: function (h, m, label) { return call('alarm', [h, m || 0, label || null]); },
                timer: function (sec, label) { return call('timer', [sec, label || null]); },
                call: function (n) { return call('call', [String(n)]); },
                sms: function (n, t, send) { return call('sms', [String(n), String(t || ''), send === true]); },
                whatsapp: function (n, t, send) { return call('whatsapp', [n ? String(n) : null, String(t || ''), send === true]); },
                maps: function (q) { return call('maps', [String(q)]); },
                launchApp: function (n, label) { return call('launchApp', [String(n), label ? String(label) : null]); },
                volume: function (level, stream) { return call('volume', [level === undefined ? null : level, stream || 'media']); },
                ringer: function (mode) { return call('ringer', [String(mode)]); },
                doNotDisturb: function (on) { return call('doNotDisturb', [on !== false]); },
                brightness: function (level) { return call('brightness', [level]); },
                keepAwake: function (on) { return call('keepAwake', [on !== false]); },
                media: function (action) { return call('media', [String(action || 'toggle')]); },
                calendar: function (ev) {
                  ev = ev || {};
                  var t = function (d) { return d instanceof Date ? d.getTime() : (typeof d === 'string' ? Date.parse(d) : d); };
                  return call('calendar', [{ title: String(ev.title || ''), start: t(ev.start), end: t(ev.end), location: ev.location || null }]);
                },
                clipboard: function () { return call('clipboard'); },
                alarmSound: function (seconds) { return call('alarmSound', [seconds || 30]); },
                stopAlarm: function () { return call('stopAlarm'); },
                bluetooth: function (on) { return call('bluetooth', [on !== false]); },
                http: function (url) { return call('http', [String(url)]); },
                done: function (msg) { return call('done', [msg ? String(msg) : null]); },
                trigger: 'tap',
                askAI: askAI,
                ai: {
                  // A conversation that remembers its own turns while the app is open (never saved, trimmed by Acornix).
                  chat: function (base) {
                    base = base || {};
                    // A conversation saved by the app can be picked up again: chat({ history: saved }).
                    var history = Array.isArray(base.history) ? base.history.slice() : [];
                    return {
                      ask: function (text, extra) {
                        var o = {}, k;
                        for (k in base) if (k !== 'history') o[k] = base[k];
                        for (k in (extra || {})) o[k] = extra[k];
                        o.history = history.slice(-40);
                        return askAI(text, o).then(function (answer) {
                          history.push({ role: 'user', text: String(text) });
                          history.push({ role: 'assistant', text: typeof answer === 'string' ? answer : JSON.stringify(answer) });
                          return answer;
                        });
                      },
                      get history() { return history.slice(); },
                      reset: function () { history = []; }
                    };
                  }
                },
                db: {
                  collection: function (name) {
                    name = String(name);
                    return {
                      add: function (doc) { return call('db.add', [name, doc || {}]); },
                      get: function (id) { return call('db.get', [name, String(id)]); },
                      set: function (id, doc) { return call('db.set', [name, String(id), doc || {}]); },
                      update: function (id, patch) { return call('db.update', [name, String(id), patch || {}]); },
                      remove: function (id) { return call('db.remove', [name, String(id)]); },
                      list: function (q) { return call('db.list', [name, q || {}]); },
                      count: function (where) { return call('db.count', [name, where || null]); },
                      clear: function () { return call('db.clear', [name]); }
                    };
                  },
                  collections: function () { return call('db.collections'); },
                  get: function (k) { return call('kv.get', [String(k)]); },
                  set: function (k, v) { return call('kv.set', [String(k), v === undefined ? null : v]); },
                  remove: function (k) { return call('kv.remove', [String(k)]); }
                },
                files: {
                  save: function (name, data) { return withFile('files.save', name, data); },
                  list: function () { return call('files.list'); },
                  remove: function (name) { return call('files.remove', [String(name)]); },
                  url: function (name) { return '/_files/' + encodeURIComponent(name); },
                  // To the phone's gallery (photos, videos), Music or Downloads; without data, a file saved with save().
                  export: function (name, data) { return withFile('files.export', name, data); },
                  share: function (name, data) { return withFile('files.share', name, data); }
                }
              };
              window.dispatchEvent(new Event('acornixready'));
            })();
        """.trimIndent()
    }
}
