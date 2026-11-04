package com.gonzaroman.acornix.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gonzaroman.acornix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

data class BatteryInfo(val level: Int, val charging: Boolean, val temperatureC: Float, val health: String)

data class InstalledApp(val label: String, val packageName: String)

/**
 * Everything Acornix can do on the phone without touching other apps' UI. Used by the assistant's
 * tools and by the window.Acornix JavaScript bridge.
 */
class DeviceActions(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = mutableListOf<Pair<String, String?>>()

    // ---------- Intents ----------

    /** Starts an activity from any context; returns false when nothing can handle it. */
    fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** Opens a link; with [packageName] in that app (e.g. Chrome), falling back to the default one. */
    fun openUrl(url: String, packageName: String? = null): Boolean {
        val fixed = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(url)) url else "https://$url"
        if (!packageName.isNullOrBlank() && launch(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).setPackage(packageName))) return true
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
    }

    fun youtubeSearch(query: String): Boolean =
        openUrl("https://www.youtube.com/results?search_query=${enc(query)}")

    fun webSearch(query: String): Boolean =
        launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query)) ||
            openUrl("https://www.google.com/search?q=${enc(query)}")

    fun openMaps(query: String): Boolean =
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(query)}"))) ||
            openUrl("https://www.google.com/maps/search/${enc(query)}")

    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Opens an installed app by package or (fuzzy, typo-tolerant) name. Returns the matched label, or null. */
    fun openApp(name: String): String? {
        val match = bestAppMatch(name, installedApps()) ?: return null
        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName) ?: return null
        return if (launch(launchIntent)) match.label else null
    }

    /** Google Play's search for an app the phone doesn't have. */
    fun searchStore(name: String): Boolean =
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${enc(name)}&c=apps"))) ||
            openUrl("https://play.google.com/store/search?q=${enc(name)}&c=apps")

    fun setAlarm(hour: Int, minute: Int, message: String?): Boolean = launch(
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message ?: "Acornix")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    fun setTimer(seconds: Int, message: String?): Boolean = launch(
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message ?: "Acornix")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    /** Opens the SMS app with the message written; the user presses send. */
    fun composeSms(number: String, text: String): Boolean =
        launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", text))

    /** Opens the dialer with the number; the user presses call. */
    fun dial(number: String): Boolean = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))

    /**
     * Opens WhatsApp with the message written, to a number (international format) or to pick a chat. With [autoSend]
     * and Total control running, Acornix then taps Send. Returns "sent", "opened", "noControl" or "failed".
     */
    suspend fun whatsapp(number: String?, text: String, autoSend: Boolean = false): String {
        val digits = number.orEmpty().filter { it.isDigit() }
        val url = "https://api.whatsapp.com/send?" + (if (digits.isEmpty()) "" else "phone=$digits&") + "text=${enc(text)}"
        val app = WHATSAPP_PACKAGES.firstOrNull { pkg -> runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess }
        val opened = (app != null && launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(app))) || openUrl(url)
        if (!opened) return "failed"
        if (!autoSend || digits.isEmpty()) return "opened"
        val service = com.gonzaroman.acornix.control.AcornixAccessibilityService.instance ?: return "noControl"
        return if (service.pressSend(WHATSAPP_PACKAGES)) "sent" else "opened"
    }

    /** Sends an SMS straight away (needs the SEND_SMS permission, asked in the shortcut editor). */
    fun sendSms(number: String, text: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            @Suppress("DEPRECATION")
            val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(android.telephony.SmsManager::class.java) else android.telephony.SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            true
        }.getOrDefault(false)
    }

    /** Opens the calendar app with a new event filled in; the user saves it. Times are epoch milliseconds. */
    fun addCalendarEvent(title: String, start: Long, end: Long, location: String?): Boolean = launch(
        Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, title)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, end)
            .apply { if (!location.isNullOrBlank()) putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, location) },
    )

    // ---------- Sound, screen and music ----------

    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    private fun stream(name: String?) = when (name?.lowercase()) {
        "ring", "ringer" -> android.media.AudioManager.STREAM_RING
        "alarm" -> android.media.AudioManager.STREAM_ALARM
        "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
        else -> android.media.AudioManager.STREAM_MUSIC
    }

    /** Sets a volume from 0 to 100 ("media" by default, or "ring", "alarm", "notification"). */
    fun setVolume(percent: Int, streamName: String?): Boolean = runCatching {
        val s = stream(streamName)
        val max = audio.getStreamMaxVolume(s)
        audio.setStreamVolume(s, (percent.coerceIn(0, 100) * max + 50) / 100, 0)
        true
    }.getOrDefault(false)

    fun volume(streamName: String?): Int = runCatching {
        val s = stream(streamName)
        audio.getStreamVolume(s) * 100 / audio.getStreamMaxVolume(s).coerceAtLeast(1)
    }.getOrDefault(0)

    private val notifications get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Whether Acornix may change Do not disturb (and the silent mode, which needs the same access). */
    fun canChangeDnd() = notifications.isNotificationPolicyAccessGranted

    /** Opens the settings page where the user lets Acornix change Do not disturb. */
    fun askDndAccess(): Boolean = launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    /** "normal", "vibrate" or "silent". False without Do not disturb access (silent needs it). */
    fun setRinger(mode: String): Boolean = runCatching {
        val m = when (mode.lowercase()) {
            "silent", "mute" -> android.media.AudioManager.RINGER_MODE_SILENT
            "vibrate" -> android.media.AudioManager.RINGER_MODE_VIBRATE
            else -> android.media.AudioManager.RINGER_MODE_NORMAL
        }
        if (!canChangeDnd() && m != audio.ringerMode) { askDndAccess(); return false }
        audio.ringerMode = m
        true
    }.getOrDefault(false)

    fun setDoNotDisturb(on: Boolean): Boolean {
        if (!canChangeDnd()) { askDndAccess(); return false }
        notifications.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return true
    }

    /** Sets the system brightness (0-100) when Acornix may change system settings; otherwise opens that permission. */
    fun setSystemBrightness(percent: Int): Boolean {
        if (!Settings.System.canWrite(context)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return false
        }
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (percent.coerceIn(0, 100) * 255 / 100).coerceAtLeast(1))
            true
        }.getOrDefault(false)
    }

    /** Controls whatever music or video app is playing: "play", "pause", "toggle", "next", "previous". */
    fun mediaKey(action: String): Boolean {
        val code = when (action.lowercase()) {
            "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
            else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        return true
    }

    /** The clipboard's text (only readable while an Acornix screen is in front). */
    fun clipboardText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    fun shareText(text: String): Boolean = launch(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
    )

    fun openSettings(section: String?): Boolean {
        val action = when (section?.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "screen", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "data", "mobile_data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return launch(Intent(action))
    }

    // ---------- Hardware ----------

    private val cameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    private fun torchCameraId(): String? = runCatching {
        cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    fun hasFlashlight() = torchCameraId() != null

    fun flashlight(on: Boolean): Boolean {
        val id = torchCameraId() ?: return false
        return runCatching { cameraManager.setTorchMode(id, on) }.isSuccess
    }

    /** Blinks [text] in Morse code with the flashlight (defaults to SOS). Suspends while blinking. */
    suspend fun flashlightMorse(text: String = "SOS", repeat: Int = 1) {
        val unit = 200L
        repeat(repeat.coerceIn(1, 5)) {
            for (ch in text.uppercase()) {
                if (ch == ' ') { delay(unit * 7); continue }
                val code = MORSE[ch] ?: continue
                for (symbol in code) {
                    flashlight(true)
                    delay(if (symbol == '.') unit else unit * 3)
                    flashlight(false)
                    delay(unit)
                }
                delay(unit * 2)
            }
            delay(unit * 7)
        }
        flashlight(false)
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrate(pattern: List<Long>) {
        if (!vibrator.hasVibrator() || pattern.isEmpty()) return
        val clean = pattern.map { it.coerceIn(0, 10_000) }
        if (clean.size == 1) {
            vibrator.vibrate(VibrationEffect.createOneShot(clean[0].coerceAtLeast(1), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform((listOf(0L) + clean).toLongArray(), -1))
        }
    }

    // ---------- Speech ----------

    fun speak(text: String, languageTag: String? = null) {
        val engine = tts
        if (engine == null) {
            pendingSpeech += text to languageTag
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    pendingSpeech.forEach { (t, l) -> speakNow(t, l) }
                }
                pendingSpeech.clear()
            }
            return
        }
        if (ttsReady) speakNow(text, languageTag) else pendingSpeech += text to languageTag
    }

    private fun speakNow(text: String, languageTag: String?) {
        val engine = tts ?: return
        engine.language = languageTag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        engine.speak(text.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_ADD, null, "acornix-${text.hashCode()}")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    // ---------- UI feedback ----------

    suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Acornix", text))
    }

    fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Returns false if notifications are not allowed. */
    fun notify(title: String, body: String): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!canNotify()) return false
        ensureChannel(CHANNEL_GENERAL, context.getString(R.string.channel_general))
        val n = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        @SuppressLint("MissingPermission") // checked by canNotify()
        val ok = runCatching { nm.notify((System.currentTimeMillis() % 100_000).toInt(), n) }.isSuccess
        return ok
    }

    fun ensureChannel(id: String, name: String, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(NotificationChannel(id, name, importance))
    }

    // ---------- Status ----------

    fun battery(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(pct, charging, temp, health)
    }

    data class MemoryInfo(val availableBytes: Long, val totalBytes: Long)
    data class StorageInfo(val freeBytes: Long, val totalBytes: Long)

    fun memory(): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return MemoryInfo(mi.availMem, mi.totalMem)
    }

    fun storage(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(stat.availableBytes, stat.totalBytes)
    }

    fun deviceInfo(): Map<String, Any> {
        val dm = context.resources.displayMetrics
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT,
            "language" to Locale.getDefault().toLanguageTag(),
            "screenWidth" to dm.widthPixels,
            "screenHeight" to dm.heightPixels,
            "density" to dm.density,
            "cpuCores" to Runtime.getRuntime().availableProcessors(),
            "hasFlashlight" to hasFlashlight(),
            "hasTelephony" to context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /**
         * The app [query] means: its package, its exact name, a name that starts with or contains it, or else the
         * closest name with at most a couple of typos ("YouTubi" → YouTube).
         */
        fun bestAppMatch(query: String, apps: List<InstalledApp>): InstalledApp? {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return null
            apps.firstOrNull { it.packageName.equals(q, true) }?.let { return it }
            apps.firstOrNull { it.label.lowercase() == q }?.let { return it }
            apps.firstOrNull { it.label.lowercase().startsWith(q) }?.let { return it }
            apps.firstOrNull { it.label.lowercase().contains(q) }?.let { return it }
            // "YouTube Music" is the app whose name or package has every word ("YT Music", …youtube.music), not YouTube.
            val words = q.split(Regex("\\W+")).filter { it.length >= 3 }
            if (words.size > 1) {
                apps.firstOrNull { app -> (app.label + " " + app.packageName).lowercase().let { t -> words.all { it in t } } }?.let { return it }
            }
            apps.firstOrNull { q.contains(it.label.lowercase()) && it.label.length > 2 }?.let { return it }
            val allowed = if (q.length <= 4) 1 else 2
            return apps.map { it to distance(q, it.label.lowercase()) }
                .filter { it.second <= allowed }
                .minByOrNull { it.second }?.first
        }

        /** Levenshtein distance. */
        private fun distance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
            return prev[b.length]
        }

        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        const val CHANNEL_GENERAL = "general"
        const val CHANNEL_AGENT = "agent"

        private val MORSE = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.",
            'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.",
            'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-",
            'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
            '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        )
    }
}
