package com.gonzaroman.acornix.web

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.gonzaroman.acornix.MainActivity
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.ThemeMode
import com.gonzaroman.acornix.device.ShortcutHelper
import com.gonzaroman.acornix.ui.os.DesktopActions
import com.gonzaroman.acornix.ui.os.DesktopScreen
import com.gonzaroman.acornix.ui.os.DesktopState
import com.gonzaroman.acornix.ui.os.OsPrefs
import com.gonzaroman.acornix.ui.os.OsWindow
import com.gonzaroman.acornix.ui.os.SystemApp
import com.gonzaroman.acornix.ui.os.WALLPAPER_COUNT
import com.gonzaroman.acornix.ui.theme.AcornixTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * AcornixOS: a desktop like macOS's, ported from the Termux version's acornixOS plugin. Each window
 * is a real WebView set up like [AppRunnerActivity], so apps keep their own origin, data and
 * `window.Acornix` bridge. Runs in its own task so it can be pinned and used like a separate app.
 */
class AcornixOsActivity : ComponentActivity(), DesktopActions {

    private val webAccess = WebAccess(this)
    private lateinit var prefs: OsPrefs

    private val windows = mutableStateListOf<OsWindow>()
    private val webViews = mutableMapOf<Int, WebView>()
    private val scopes = mutableMapOf<Int, CoroutineScope>()
    private var nextId = 0
    private var areaW = 0f
    private var areaH = 0f

    private var dock by mutableStateOf(emptyList<String>())
    private var wallpaper by mutableIntStateOf(0)
    private var launchpadOpen by mutableStateOf(false)
    private var clock by mutableStateOf("")
    private var battery by mutableStateOf("")
    private var toast by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = OsPrefs(this)
        wallpaper = prefs.wallpaper
        dock = prefs.dock(container.apps.apps.value)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val apps by container.apps.apps.collectAsState()
            AcornixTheme(mode = ThemeMode.DARK, dynamicColor = false) {
                DesktopScreen(
                    DesktopState(apps, windows, dock, wallpaper, launchpadOpen, clock, battery, toast),
                    this,
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val top = windows.lastOrNull { !it.minimized }
                when {
                    launchpadOpen -> launchpad(false)
                    top != null && webViews[top.id]?.canGoBack() == true -> webViews[top.id]?.goBack()
                    top != null -> minimize(top)
                    else -> finish()
                }
            }
        })

        lifecycleScope.launch {
            while (isActive) {
                clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                val b = container.device.battery()
                battery = if (b.level >= 0) "${if (b.charging) "⚡" else "🔋"} ${b.level}%" else ""
                delay(20_000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViews.values.forEach { it.onResume() }
        // Apps created or deleted from the main screens while we were away.
        lifecycleScope.launch {
            container.apps.refresh()
            windows.filter { container.apps.get(it.appId) == null }.forEach { close(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        webViews.values.forEach { it.onPause() }
    }

    override fun onDestroy() {
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webViews.clear()
        scopes.values.forEach { it.cancel() }
        container.device.flashlight(false)
        super.onDestroy()
    }

    // --- DesktopActions ---

    override fun openApp(app: AppMeta) {
        windows.firstOrNull { it.appId == app.id }?.let { focus(it); return }
        val win = OsWindow(nextId++, app.id)
        place(win)
        windows += win
        lifecycleScope.launch { container.apps.markOpened(app.id) }
    }

    /** Phones get a maximized window; tablets a cascade of floating windows, like the Termux version. */
    private fun place(win: OsWindow) {
        val w = areaW.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val h = areaH.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels * 0.8f
        val tablet = w / resources.displayMetrics.density >= 600
        val step = windows.size % 5
        // On phones the window starts maximized; this is the size it takes when un-maximized.
        win.width = w * if (tablet) 0.7f else 0.92f
        win.height = h * if (tablet) 0.8f else 0.75f
        win.x = w * if (tablet) 0.04f + step * 0.04f else 0.04f
        win.y = h * if (tablet) 0.03f + step * 0.035f else 0.04f + step * 0.03f
        win.maximized = !tablet
    }

    override fun openSystem(app: SystemApp) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_SCREEN)
                .putExtra(MainActivity.EXTRA_SCREEN, app.screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun focus(window: OsWindow) {
        window.minimized = false
        val index = windows.indexOf(window)
        if (index >= 0 && index != windows.lastIndex) {
            windows.removeAt(index)
            windows += window
        }
    }

    override fun close(window: OsWindow) {
        windows.remove(window)
        webViews.remove(window.id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        scopes.remove(window.id)?.cancel()
        if (windows.isEmpty()) container.device.flashlight(false)
    }

    override fun minimize(window: OsWindow) {
        window.minimized = true
    }

    override fun launchpad(open: Boolean) {
        launchpadOpen = open
    }

    override fun addToDock(key: String, label: String) {
        if (key in dock) {
            toast = getString(R.string.os_dock_already, label)
            return
        }
        dock = dock + key
        prefs.saveDock(dock)
        toast = getString(R.string.os_dock_added, label)
    }

    override fun removeFromDock(key: String, label: String) {
        dock = dock - key
        prefs.saveDock(dock)
        toast = getString(R.string.os_dock_removed, label)
    }

    override fun nextWallpaper() {
        wallpaper = (wallpaper + 1) % WALLPAPER_COUNT
        prefs.wallpaper = wallpaper
    }

    override fun pinToHome() {
        if (!ShortcutHelper.pinOs(this)) toast = getString(R.string.os_pin_unsupported)
    }

    override fun exit() = finish()

    override fun toastShown() {
        toast = null
    }

    override fun areaSize(width: Float, height: Float) {
        areaW = width
        areaH = height
    }

    override fun webViewFor(window: OsWindow): WebView = webViews.getOrPut(window.id) {
        // Each window gets its own scope so closing it stops its data listener too.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes[window.id] = it }
        WebView(this).apply {
            webChromeClient = webAccess.chromeClient()
            AcornixWebView.configure(this, this@AcornixOsActivity, scope, window.appId)
            // MATCH_PARENT: with Compose's default WRAP_CONTENT, WebView resolves CSS vh/dvh units to 0
            // and pages sized with 100dvh (the calculator) come out blank.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            loadUrl(AcornixWebView.appUrl(window.appId))
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AcornixOsActivity::class.java).setAction(Intent.ACTION_MAIN)

        fun open(context: Context) {
            context.startActivity(intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
