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
import kotlinx.serialization.json.put
import java.security.MessageDigest

object AppLauncher {
    fun intent(context: Context, appId: String): Intent =
        Intent(context, AppRunnerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("acornix://app/${Uri.encode(appId)}"))

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
                    view.evaluateJavascript(AcornixJsBridge.SHIM, null)
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
        AcornixJsBridge(context, scope, appId).attach(webView)

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
            WebViewCompat.addDocumentStartJavaScript(webView, SHIM, setOf(origin))
        }
    }

    private suspend fun handle(method: String, args: JsonArray, partial: (String, Boolean) -> Unit = { _, _ -> }): JsonElement {
        val c = context.container
        val device = c.device
        fun arg(i: Int): JsonElement? = args.getOrNull(i)?.takeIf { it !is JsonNull }
        fun str(i: Int) = (arg(i) as? JsonPrimitive)?.content
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
            "openUrl" -> JsonPrimitive(device.openUrl(str(0).orEmpty()))
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
            "askAI" -> {
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
                openUrl: function (u) { return call('openUrl', [String(u)]); },
                share: function (t) { return call('share', [String(t)]); },
                copy: function (t) { return call('copy', [String(t)]); },
                battery: function () { return call('battery'); },
                deviceInfo: function () { return call('deviceInfo'); },
                listApps: function () { return call('listApps'); },
                openApp: function (n) { return call('openApp', [String(n)]); },
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
                  save: function (name, data) {
                    var send = function (d) { return call('files.save', [String(name), d]); };
                    if (typeof Blob !== 'undefined' && data instanceof Blob) {
                      return new Promise(function (resolve, reject) {
                        var r = new FileReader();
                        r.onload = function () { send(r.result).then(resolve, reject); };
                        r.onerror = function () { reject(r.error); };
                        r.readAsDataURL(data);
                      });
                    }
                    return send(String(data));
                  },
                  list: function () { return call('files.list'); },
                  remove: function (name) { return call('files.remove', [String(name)]); },
                  url: function (name) { return '/_files/' + encodeURIComponent(name); }
                }
              };
              window.dispatchEvent(new Event('acornixready'));
            })();
        """.trimIndent()
    }
}
