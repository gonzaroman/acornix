package com.gonzaroman.acornix.web

import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Runs one generated app full screen, in its own task, like a real installed app. */
class AppRunnerActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var appId: String = ""
    private var thumbnailTaken = false
    private var thumbnailScheduled = false
    private val handler = Handler(Looper.getMainLooper())
    private val thumbnailTask = Runnable { takeThumbnail() }

    private val webAccess = WebAccess(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appId = intent?.data?.lastPathSegment.orEmpty()
        val meta = container.apps.get(appId)
        if (meta == null || !container.apps.indexFile(appId).exists()) {
            Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription("${meta.emoji} ${meta.name}"))

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this)
        webView.webChromeClient = webAccess.chromeClient()
        AcornixWebView.configure(webView, this, lifecycleScope, appId) { scheduleThumbnail() }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })

        if (savedInstanceState != null) webView.restoreState(savedInstanceState) else webView.loadUrl(AcornixWebView.appUrl(appId))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        lifecycleScope.launch { container.apps.markOpened(appId) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
        // Leaving before the thumbnail is taken: the window loses its surface, so try again on return.
        if (thumbnailScheduled) {
            handler.removeCallbacks(thumbnailTask)
            thumbnailScheduled = false
            thumbnailTaken = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            if (webView.progress == 100) scheduleThumbnail()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(thumbnailTask)
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        container.device.flashlight(false)
        super.onDestroy()
    }

    /** Saves a screenshot of the running app as its thumbnail in the launcher grid. */
    private fun scheduleThumbnail() {
        if (thumbnailTaken) return
        thumbnailTaken = true
        thumbnailScheduled = true
        handler.postDelayed(thumbnailTask, 2500)
    }

    private fun takeThumbnail() {
        thumbnailScheduled = false
        if (isFinishing || isDestroyed || webView.width == 0 || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            thumbnailTaken = false
            return
        }
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2).also { webView.getLocationInWindow(it) }
        val rect = android.graphics.Rect(location[0], location[1], location[0] + webView.width, location[1] + webView.height)
        try {
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) saveThumbnail(bitmap)
            }, handler)
        } catch (e: IllegalArgumentException) {
            // "Window doesn't have a backing surface": a missing thumbnail must never crash Acornix.
            thumbnailTaken = false
        }
    }

    private fun saveThumbnail(bitmap: Bitmap) {
        val target: File = container.apps.thumbnailFile(appId)
        lifecycleScope.launch(Dispatchers.IO) {
            val scale = 360f / bitmap.width
            val small = Bitmap.createScaledBitmap(bitmap, 360, (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            runCatching { target.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 75, it) } }
            withContext(Dispatchers.Main) { container.apps.refresh() }
        }
    }
}
