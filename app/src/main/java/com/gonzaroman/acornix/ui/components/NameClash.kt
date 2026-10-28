package com.gonzaroman.acornix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gonzaroman.acornix.R

/**
 * Installing an app whose name the user already has: as a copy with a numbered name ("Notes 2"),
 * or in place of theirs, which keeps their app's data and puts its current code in the history.
 */
@Composable
fun NameClashDialog(name: String, copyName: String, onCopy: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.name_clash_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.name_clash_text))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.name_clash_copy, copyName)) }
                    OutlinedButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.name_clash_replace)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
