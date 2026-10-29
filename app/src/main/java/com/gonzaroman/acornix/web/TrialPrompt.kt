package com.gonzaroman.acornix.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gonzaroman.acornix.R

/** What the overlay on an app being tried from the gallery shows. */
data class TrialUi(
    val name: String,
    val banner: Boolean = false,
    val installing: Boolean = false,
    val installedId: String? = null,
    val askOnExit: Boolean = false,
)

/**
 * "Do you like it? Install it": a small bar over an app being tried, and a question when leaving it.
 * Trying is never cut short; this only makes keeping the app one tap away.
 */
@Composable
fun TrialPrompt(ui: TrialUi, onInstall: () -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit, onLeave: () -> Unit, onStay: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = ui.banner || ui.installing || ui.installedId != null,
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (ui.installedId != null) {
                            Text(stringResource(R.string.gallery_done, ui.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(stringResource(R.string.trial_banner_title, ui.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.trial_banner_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    when {
                        ui.installing -> CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).padding(4.dp), strokeWidth = 2.dp)
                        ui.installedId != null -> Button(onClick = onOpen) { Text(stringResource(R.string.gallery_open)) }
                        else -> Button(onClick = onInstall) { Text(stringResource(R.string.gallery_install)) }
                    }
                    if (!ui.installing) IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                }
            }
        }
    }
    if (ui.askOnExit) {
        AlertDialog(
            onDismissRequest = onStay,
            title = { Text(stringResource(R.string.trial_exit_title, ui.name)) },
            text = { Text(stringResource(R.string.trial_exit_text)) },
            confirmButton = { Button(onClick = onInstall) { Text(stringResource(R.string.gallery_install)) } },
            dismissButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.trial_exit_leave)) } },
        )
    }
}
