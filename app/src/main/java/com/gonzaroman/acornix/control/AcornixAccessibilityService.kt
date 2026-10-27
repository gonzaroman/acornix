package com.gonzaroman.acornix.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Display
import androidx.annotation.RequiresApi
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.gonzaroman.acornix.ai.ImageData
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * "Total control": lets the assistant read the screen and operate other apps. It does nothing on its
 * own; it only acts when the assistant calls one of its methods.
 */
class AcornixAccessibilityService : AccessibilityService() {

    /** Elements from the last [readScreen], addressed by the [id] numbers shown to the model. */
    private var elements: Map<Int, AccessibilityNodeInfo> = emptyMap()

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
    }

    override fun onDestroy() {
        instance = null
        _connected.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    // ---------- Reading ----------

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "The screen cannot be read right now (no active window)."
        val lines = mutableListOf<String>()
        val map = mutableMapOf<Int, AccessibilityNodeInfo>()
        var next = 1

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (map.size >= MAX_ELEMENTS) return
            if (!node.isVisibleToUser) return
            val label = labelOf(node)
            val interesting = label.isNotEmpty() || node.isClickable || node.isEditable || node.isScrollable || node.isCheckable
            if (interesting) {
                val id = next++
                map[id] = node
                lines += "  ".repeat(depth.coerceAtMost(6)) + "[$id] " + describe(node, label)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { visit(it, if (interesting) depth + 1 else depth) }
            }
        }
        visit(root, 0)
        elements = map
        val header = "App on screen: ${root.packageName ?: "unknown"}" +
            (if (map.size >= MAX_ELEMENTS) " (list truncated to $MAX_ELEMENTS elements)" else "")
        return if (lines.isEmpty()) "$header\n(no readable elements; try screenshot)" else header + "\n" + lines.joinToString("\n")
    }

    /** Text the user would see on an element, including its children, used for confirmations. */
    fun elementText(id: Int): String? {
        val node = elements[id] ?: return null
        val parts = mutableListOf<String>()
        fun collect(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 3) return
            labelOf(n).takeIf { it.isNotEmpty() }?.let { parts += it }
            for (i in 0 until n.childCount) n.getChild(i)?.let { collect(it, depth + 1) }
        }
        collect(node, 0)
        return parts.joinToString(" ").take(200)
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        if (node.isPassword) return "(password field — hidden)"
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val hint = node.hintText?.toString().orEmpty()
        return listOf(text, desc, if (node.isEditable && text.isEmpty()) hint else "")
            .filter { it.isNotBlank() }.distinct()
            .joinToString(" | ").replace('\n', ' ').take(100)
    }

    private fun describe(node: AccessibilityNodeInfo, label: String): String {
        val type = node.className?.toString()?.substringAfterLast('.')?.let { shortType(it, node) } ?: "View"
        val flags = buildList {
            if (node.isClickable) add("tap")
            if (node.isLongClickable) add("long-press")
            if (node.isEditable) add("editable")
            if (node.isScrollable) add("scroll")
            if (node.isCheckable) add(if (node.isChecked) "checked" else "unchecked")
            if (node.isSelected) add("selected")
            if (node.isFocused) add("focused")
            if (!node.isEnabled) add("disabled")
        }
        return buildString {
            append(type)
            if (label.isNotEmpty()) append(" \"").append(label).append('"')
            if (flags.isNotEmpty()) append(" (").append(flags.joinToString(", ")).append(')')
        }
    }

    private fun shortType(cls: String, node: AccessibilityNodeInfo): String = when {
        node.isEditable -> "TextField"
        cls.contains("Button") -> "Button"
        cls.contains("CheckBox") -> "CheckBox"
        cls.contains("Switch") -> "Switch"
        cls.contains("Image") -> "Image"
        cls.contains("TextView") || cls == "Text" -> "Text"
        cls.contains("RecyclerView") || cls.contains("ListView") || cls.contains("ScrollView") -> "List"
        else -> cls.ifBlank { "View" }
    }

    // ---------- Acting ----------

    fun hasElement(id: Int) = elements.containsKey(id)

    suspend fun tap(id: Int, longPress: Boolean = false): Boolean {
        val node = elements[id] ?: return false
        node.refresh()
        val action = if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        var target: AccessibilityNodeInfo? = node
        while (target != null) {
            val can = if (longPress) target.isLongClickable else target.isClickable
            if (can && target.performAction(action)) return true
            target = target.parent
        }
        // Nothing clickable in the hierarchy: tap the element's coordinates instead.
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        return tapAt(r.exactCenterX(), r.exactCenterY(), if (longPress) 700 else 60)
    }

    /** Finds a visible element whose text contains [text] and taps it. */
    suspend fun tapText(text: String): Boolean {
        readScreen()
        val q = text.lowercase()
        val match = elements.entries
            .filter { labelOf(it.value).lowercase().contains(q) }
            .minByOrNull { labelOf(it.value).length }
            ?: return false
        return tap(match.key)
    }

    fun typeText(id: Int?, text: String, submit: Boolean): Boolean {
        val node = id?.let { elements[it] }
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: elements.values.firstOrNull { it.isEditable }
            ?: return false
        node.refresh()
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && submit && Build.VERSION.SDK_INT >= 30) {
            node.performAction(AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return ok
    }

    suspend fun scroll(direction: String, id: Int?): Boolean {
        val node = id?.let { elements[it] }
            ?: elements.values.filter { it.isScrollable }.maxByOrNull { n -> Rect().also { n.getBoundsInScreen(it) }.let { it.width() * it.height() } }
        val dir = direction.lowercase()
        if (node != null) {
            node.refresh()
            val specific = when (dir) {
                "up" -> AccessibilityAction.ACTION_SCROLL_UP
                "left" -> AccessibilityAction.ACTION_SCROLL_LEFT
                "right" -> AccessibilityAction.ACTION_SCROLL_RIGHT
                else -> AccessibilityAction.ACTION_SCROLL_DOWN
            }
            if (node.performAction(specific.id)) return true
            val generic = if (dir == "up" || dir == "left") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            if (node.performAction(generic)) return true
        }
        // Fallback: a swipe across the middle of the screen.
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        return when (dir) {
            "up" -> swipe(w / 2, h * 0.3f, w / 2, h * 0.75f)
            "left" -> swipe(w * 0.2f, h / 2, w * 0.8f, h / 2)
            "right" -> swipe(w * 0.8f, h / 2, w * 0.2f, h / 2)
            else -> swipe(w / 2, h * 0.75f, w / 2, h * 0.3f)
        }
    }

    suspend fun tapAt(x: Float, y: Float, durationMs: Long = 60): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return gesture(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    private suspend fun gesture(stroke: GestureDescription.StrokeDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                },
                null,
            )
            if (!ok && cont.isActive) cont.resume(false)
        }

    fun global(action: String): Boolean {
        val code = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(code)
    }

    /** Captures the screen as a JPEG (Android 11+), or says why it couldn't. */
    suspend fun screenshot(): Result<ImageData> {
        if (Build.VERSION.SDK_INT < 30) return Result.failure(IllegalStateException("Screenshots need Android 11 or newer."))
        var capture = captureScreen()
        // Android refuses screenshots taken less than a third of a second apart: wait and try again.
        repeat(2) {
            if (capture.second != ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) return@repeat
            delay(400)
            capture = captureScreen()
        }
        val bitmap = capture.first ?: return Result.failure(IllegalStateException(screenshotError(capture.second)))
        val scale = SCREENSHOT_WIDTH.toFloat() / bitmap.width
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, SCREENSHOT_WIDTH, (bitmap.height * scale).toInt(), true) else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Result.success(ImageData(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg"))
    }

    private fun screenshotError(code: Int?): String = when (code) {
        ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "The app on screen blocks screenshots. Use read_screen instead."
        ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Screenshots are being taken too fast. Wait a moment and try again."
        else -> "The screenshot failed (error $code). Use read_screen instead."
    }

    /** The bitmap, or null and Android's error code. */
    @RequiresApi(30)
    private suspend fun captureScreen(): Pair<Bitmap?, Int?> =
        suspendCancellableCoroutine { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        if (cont.isActive) cont.resume(soft to null)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null to errorCode)
                    }
                },
            )
        }

    /**
     * Shows a confirmation card on top of whatever app is on screen (accessibility overlays need no
     * extra permission) and suspends until the user answers.
     */
    suspend fun confirmOverlay(title: String, message: String, yes: String, no: String): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                val density = resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()
                val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val bg = if (night) 0xFF2B2A26.toInt() else 0xFFFFFBF5.toInt()
                val fg = if (night) 0xFFF2EFE8.toInt() else 0xFF1E1C18.toInt()
                val accent = 0xFF2E7D4F.toInt()

                val card = LinearLayout(this@AcornixAccessibilityService).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24), dp(20), dp(24), dp(12))
                    background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(28).toFloat() }
                    elevation = dp(12).toFloat()
                }
                card.addView(TextView(this@AcornixAccessibilityService).apply {
                    text = title; textSize = 20f; setTextColor(fg); setTypeface(typeface, Typeface.BOLD)
                })
                card.addView(TextView(this@AcornixAccessibilityService).apply {
                    text = message; textSize = 16f; setTextColor(fg); setPadding(0, dp(12), 0, dp(12))
                })
                val buttons = LinearLayout(this@AcornixAccessibilityService).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.CENTER
                    dimAmount = 0.5f
                    horizontalMargin = 0.05f
                }
                val container = FrameLayout(this@AcornixAccessibilityService).apply {
                    setPadding(dp(20), 0, dp(20), 0)
                    addView(card)
                }
                fun finish(answer: Boolean) {
                    runCatching { wm.removeView(container) }
                    if (cont.isActive) cont.resume(answer)
                }
                fun button(label: String, primary: Boolean, answer: Boolean) = Button(this@AcornixAccessibilityService).apply {
                    text = label
                    isAllCaps = false
                    setTextColor(if (primary) 0xFFFFFFFF.toInt() else accent)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(24).toFloat()
                        setColor(if (primary) accent else 0x00000000)
                    }
                    setPadding(dp(20), 0, dp(20), 0)
                    setOnClickListener { finish(answer) }
                }
                buttons.addView(button(no, primary = false, answer = false))
                buttons.addView(button(yes, primary = true, answer = true), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) })
                card.addView(buttons)
                cont.invokeOnCancellation { runCatching { wm.removeView(container) } }
                runCatching { wm.addView(container, params) }.onFailure { if (cont.isActive) cont.resume(false) }
            }
        }

    /** Waits for the UI to settle after an action and returns the new screen description. */
    suspend fun settleAndRead(ms: Long = 900): String {
        delay(ms)
        return readScreen()
    }

    companion object {
        private const val MAX_ELEMENTS = 180
        private const val SCREENSHOT_WIDTH = 720

        @Volatile
        var instance: AcornixAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)

        /** True while Android has the service running (not just switched on in settings). */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        fun componentName(context: Context) = ComponentName(context, AcornixAccessibilityService::class.java)

        /** Switched on in Accessibility settings; it may still not be running (see [connected]). */
        fun isEnabled(context: Context): Boolean {
            val expected = componentName(context).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
