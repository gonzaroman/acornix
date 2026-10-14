package com.gonzaroman.acornix.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

/** "A new version is available": release notes, download progress and the hand-off to the installer. */
@Composable
fun UpdateCard(info: UpdateInfo, modifier: Modifier = Modifier, showLater: Boolean = true) {
    val context = LocalContext.current
    val updates = context.container.updates
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var progress by remember(info.version) { mutableStateOf<Float?>(null) }
    var apk by remember(info.version) { mutableStateOf<File?>(null) }
    var failed by remember(info.version) { mutableStateOf(false) }

    fun installOrAskPermission(file: File) {
        if (updates.needsInstallPermission()) {
            Toast.makeText(context, R.string.update_permission_hint, Toast.LENGTH_LONG).show()
            updates.openInstallPermissionSettings()
        } else {
            updates.install(file)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("🎉 " + stringResource(R.string.update_available, info.version), style = MaterialTheme.typography.titleMedium)
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(info.notes, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            val p = progress
            when {
                apk != null -> Button(onClick = { installOrAskPermission(apk!!) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_install))
                }
                p != null -> {
                    Text(stringResource(R.string.update_downloading, (p * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        failed = false
                        progress = 0f
                        scope.launch {
                            try {
                                val file = updates.download(info) { progress = it }
                                apk = file
                                installOrAskPermission(file)
                            } catch (e: Exception) {
                                failed = true
                            } finally {
                                progress = null
                            }
                        }
                    }) { Text(stringResource(R.string.update_now)) }
                    if (showLater) TextButton(onClick = { updates.dismiss(info) }) { Text(stringResource(R.string.update_later)) }
                }
            }
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { uri.openUri(info.pageUrl) }) { Text(stringResource(R.string.update_open_page)) }
            }
        }
    }
}
