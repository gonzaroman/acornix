package com.gonzaroman.acornix.ui.shortcuts

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.device.NfcStickers
import com.gonzaroman.acornix.web.AppLauncher

/** "Hold a sticker to the back of the phone": writes [app] onto an NFC sticker. */
@Composable
fun NfcWriteDialog(app: AppMeta, onDismiss: () -> Unit) {
    val activity = LocalContext.current as? Activity ?: return onDismiss()
    var result by remember { mutableStateOf<NfcStickers.Result?>(null) }
    val enabled = remember { NfcStickers.isEnabled(activity) }

    DisposableEffect(app.id) {
        if (enabled) NfcStickers.startWriting(activity, AppLauncher.link(app.id)) { r -> activity.runOnUiThread { result = r } }
        onDispose { NfcStickers.stopWriting(activity) }
    }

    val (emoji, text) = when {
        !enabled -> "📴" to stringResource(R.string.nfc_off)
        result == NfcStickers.Result.WRITTEN -> "✅" to stringResource(R.string.nfc_written, app.name)
        result == NfcStickers.Result.READ_ONLY -> "🔒" to stringResource(R.string.nfc_read_only)
        result == NfcStickers.Result.TOO_SMALL -> "📏" to stringResource(R.string.nfc_too_small)
        result == NfcStickers.Result.FAILED -> "⚠️" to stringResource(R.string.nfc_failed)
        else -> "🏷️" to stringResource(R.string.nfc_hold)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_nfc)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 56.sp)
                Text(text, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (result == NfcStickers.Result.WRITTEN) R.string.shortcut_done else R.string.cancel))
            }
        },
    )
}
