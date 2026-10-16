package com.gonzaroman.acornix.web

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Camera, microphone and file picking for generated apps, shared by every WebView of an activity
 * (the full-screen runner and each AcornixOS window). Create it while the activity is being
 * constructed: it registers activity result launchers.
 */
class WebAccess(private val activity: ComponentActivity) {

    private var pendingPermission: PermissionRequest? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        grantPending()
    }

    private val fileChooser = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    fun chromeClient(): WebChromeClient = Client()

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

    private fun hasPermission(p: String) = ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED

    private inner class Client : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            activity.runOnUiThread {
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
