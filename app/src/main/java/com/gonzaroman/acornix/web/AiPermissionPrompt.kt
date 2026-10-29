package com.gonzaroman.acornix.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.gonzaroman.acornix.MainActivity
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** "This app wants to use your AI": asked once per app, the first time it calls askAI. */
object AiPermissionPrompt {
    private val mutex = Mutex()
    /** Apps already told there's no AI, so an app that keeps calling askAI doesn't show it again and again. */
    private val toldNoAi = mutableSetOf<String>()

    /**
     * False if no AI is set up in Acornix. The first time an app finds that out, the user is offered
     * to go to Settings and connect one, instead of being asked for a permission that can't work yet.
     */
    suspend fun ensureConfigured(context: Context, appId: String): Boolean = mutex.withLock {
        if (context.container.settings.current().config().isUsable) return@withLock true
        val activity = context.findActivity() ?: return@withLock false
        if (toldNoAi.add(appId)) {
            val name = context.container.apps.get(appId)?.name ?: appId
            val connect = withContext(Dispatchers.Main) { askToConnect(activity, name) }
            if (connect) {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_OPEN_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        false
    }

    private suspend fun askToConnect(activity: Activity, appName: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.ai_missing_title, appName))
                .setMessage(R.string.ai_missing_text)
                .setPositiveButton(R.string.ai_missing_connect) { _, _ -> if (cont.isActive) cont.resume(true) }
                .setNegativeButton(R.string.ai_missing_later) { _, _ -> if (cont.isActive) cont.resume(false) }
                .setOnCancelListener { if (cont.isActive) cont.resume(false) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }

    suspend fun ensure(context: Context, appId: String): Boolean = mutex.withLock {
        val permissions = context.container.aiPermissions
        permissions.get(appId)?.let { return@withLock it }
        val activity = context.findActivity() ?: return@withLock false
        val name = context.container.apps.get(appId)?.name ?: appId
        val provider = context.container.settings.current().provider.label
        val allowed = withContext(Dispatchers.Main) { ask(activity, name, provider) } ?: return@withLock false
        permissions.set(appId, allowed)
        allowed
    }

    /** true / false, or null if the dialog was closed without answering (asked again next time). */
    private suspend fun ask(activity: Activity, appName: String, provider: String): Boolean? =
        suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.ai_permission_title, appName))
                .setMessage(activity.getString(R.string.ai_permission_text, provider))
                .setPositiveButton(R.string.ai_permission_allow) { _, _ -> if (cont.isActive) cont.resume(true) }
                .setNegativeButton(R.string.ai_permission_deny) { _, _ -> if (cont.isActive) cont.resume(false) }
                .setOnCancelListener { if (cont.isActive) cont.resume(null) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
