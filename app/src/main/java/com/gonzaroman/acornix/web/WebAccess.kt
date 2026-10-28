package com.gonzaroman.acornix.web

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
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
import androidx.core.content.FileProvider
import java.io.File

/**
 * Camera, microphone and file picking for generated apps, shared by every WebView of an activity
 * (the full-screen runner and each AcornixOS window). Create it while the activity is being
 * constructed: it registers activity result launchers.
 */
class WebAccess(private val activity: ComponentActivity) {

    private var pendingPermission: PermissionRequest? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    /** Where the camera app writes the photo or video it was asked for. */
    private var cameraFile: File? = null
    /** A capture input waiting for the camera permission. */
    private var pendingCapture: FileChooserRequest? = null

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        grantPending()
    }

    private val fileChooser = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        val shot = cameraFile?.takeIf { result.resultCode == Activity.RESULT_OK && it.length() > 0 }?.let { arrayOf(uriFor(it)) }
        fileCallback?.onReceiveValue(picked?.takeIf { it.isNotEmpty() } ?: shot)
        fileCallback = null
        cameraFile = null
    }

    private val cameraPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingCapture?.let { launchChooser(it) }
        pendingCapture = null
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

    private class FileChooserRequest(val params: WebChromeClient.FileChooserParams, val video: Boolean, val capture: Boolean)

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)

    /**
     * `<input type="file" accept="image/…" capture>` opens the camera, and a photo or video input without
     * capture lets the user choose between the camera and the gallery. WebView's own intent ignores
     * capture and only opens the gallery. Anything else gets the plain picker.
     */
    private fun launchChooser(request: FileChooserRequest) {
        val camera = if (hasPermission(Manifest.permission.CAMERA)) cameraIntent(request.video) else null
        val intent = when {
            camera != null && request.capture -> camera
            camera != null -> Intent.createChooser(request.params.createIntent(), null)
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
            else -> request.params.createIntent()
        }
        runCatching { fileChooser.launch(intent) }.recoverCatching {
            cameraFile = null
            fileChooser.launch(request.params.createIntent())
        }.onFailure {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
    }

    private fun cameraIntent(video: Boolean): Intent {
        val dir = File(activity.cacheDir, "camera").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, if (video) "video-${System.currentTimeMillis()}.mp4" else "photo-${System.currentTimeMillis()}.jpg")
        cameraFile = file
        val uri = uriFor(file)
        return Intent(if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(null, uri) }
    }

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
            cameraFile = null
            val types = params.acceptTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            // A bare `capture` with no accept still means "take a photo".
            val image = types.any { it.startsWith("image/") || it in IMAGE_EXTENSIONS } || (types.isEmpty() && params.isCaptureEnabled)
            val video = !image && types.any { it.startsWith("video/") || it in VIDEO_EXTENSIONS }
            if (!image && !video) {
                return runCatching { fileChooser.launch(params.createIntent()); true }.getOrElse {
                    fileCallback = null
                    false
                }
            }
            val request = FileChooserRequest(params, video, params.isCaptureEnabled)
            if (request.capture && !hasPermission(Manifest.permission.CAMERA)) {
                pendingCapture = request
                cameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                launchChooser(request)
            }
            return true
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d("AcornixApp", "${message.message()} (${message.lineNumber()})")
            return true
        }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic")
        val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".3gp")
    }
}
