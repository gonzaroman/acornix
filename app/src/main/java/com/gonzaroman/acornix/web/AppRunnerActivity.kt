package com.gonzaroman.acornix.web

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private var pendingPermission: PermissionRequest? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var thumbnailTaken = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        grantPending()
    }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

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
        webView.webChromeClient = RunnerChromeClient()
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
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
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
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed || webView.width == 0) return@postDelayed
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val location = IntArray(2).also { webView.getLocationInWindow(it) }
            val rect = android.graphics.Rect(location[0], location[1], location[0] + webView.width, location[1] + webView.height)
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) saveThumbnail(bitmap)
            }, Handler(Looper.getMainLooper()))
        }, 2500)
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

    private fun grantPending() {
        val request = pendingPermission ?: return
        pendingPermission = null
        val granted = request.resources.filter { res ->
            requiredPermission(res)?.let { hasPermission(it) } ?: false
        }
        if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
    }

    private fun requiredPermission(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        else -> null
    }

    private fun hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private inner class RunnerChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                val needed = request.resources.mapNotNull { requiredPermission(it) }
                pendingPermission?.deny()
                pendingPermission = request
                val missing = needed.filterNot { hasPermission(it) }
                if (missing.isEmpty()) grantPending() else permissionLauncher.launch(missing.toTypedArray())
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingPermission == request) pendingPermission = null
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            return runCatching { fileChooser.launch(params.createIntent()); true }.getOrElse {
                fileCallback = null
                false
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d("AcornixApp", "${message.message()} (${message.lineNumber()})")
            return true
        }
    }
}
