package com.gonzaroman.acornix.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gonzaroman.acornix.control.AcornixAccessibilityService
import kotlinx.coroutines.delay

enum class ControlState {
    ON,
    OFF,

    /** Switched on in Accessibility but not running: it crashed or the system closed Acornix. */
    STALLED,
}

/** Live state of Total control: follows the service connecting and re-reads the setting on resume. */
@Composable
fun rememberControlState(): State<ControlState> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val connected = AcornixAccessibilityService.connected.collectAsState()
    val switchedOn = remember { mutableStateOf(AcornixAccessibilityService.isEnabled(context)) }
    // Android may take a moment to connect the service; don't call it stopped before then.
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1500); settled.value = true }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) switchedOn.value = AcornixAccessibilityService.isEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return remember {
        derivedStateOf {
            when {
                connected.value -> ControlState.ON
                switchedOn.value && settled.value -> ControlState.STALLED
                else -> ControlState.OFF
            }
        }
    }
}

/** Brands whose battery manager closes apps unless "Autostart" is on, which also stops Total control. */
fun closesBackgroundApps(): Boolean = Build.MANUFACTURER.lowercase() in setOf(
    "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo", "huawei", "honor", "meizu",
)

fun manufacturerName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

/** Accessibility settings, with Acornix highlighted on phones that support it. */
fun openAccessibilitySettings(context: Context) {
    val key = AcornixAccessibilityService.componentName(context).flattenToString()
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Acornix's own page in system settings, where Autostart and battery restrictions live. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Xiaomi's "Background autostart" list; other phones get Acornix's settings page instead. */
fun openAutostartSettings(context: Context) {
    runCatching {
        context.startActivity(Intent("miui.intent.action.OP_AUTO_START").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { openAppSettings(context) }
}
