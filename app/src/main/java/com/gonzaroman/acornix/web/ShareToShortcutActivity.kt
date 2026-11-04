package com.gonzaroman.acornix.web

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.device.ShortcutLaunchers
import com.gonzaroman.acornix.ui.theme.AcornixTheme

/**
 * "Share with a shortcut" in Android's share sheet: the user picks one of their shortcuts and it runs with the
 * shared text (a link, a note…) as Acornix.input, the {input} variable of step-by-step shortcuts.
 */
class ShareToShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(intent?.getStringExtra(Intent.EXTRA_SUBJECT), intent?.getStringExtra(Intent.EXTRA_TEXT))
            .joinToString("\n").trim()
        val shortcuts = ShortcutLaunchers.shortcuts(this)
        setContent {
            AcornixTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.share_to_shortcut)) },
                    text = {
                        Column {
                            if (shortcuts.isEmpty()) Text(stringResource(R.string.share_no_shortcuts))
                            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                items(shortcuts, key = { it.id }) { app ->
                                    ListItem(
                                        headlineContent = { Text(app.name) },
                                        leadingContent = { Text(app.emoji, fontSize = 24.sp) },
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            startActivity(AppLauncher.intent(this@ShareToShortcutActivity, app.id, "share", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            finish()
                                        },
                                    )
                                }
                            }
                            if (text.isNotEmpty()) {
                                Text("“" + text.take(120) + "”", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}
