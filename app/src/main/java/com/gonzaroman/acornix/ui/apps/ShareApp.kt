package com.gonzaroman.acornix.ui.apps

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppMeta

/** Exports the app as a ZIP and opens the share sheet. */
suspend fun shareAppZip(context: Context, app: AppMeta, includeData: Boolean) {
    val zip = context.container.importExport.export(app.id, includeData)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
    val send = Intent(Intent.ACTION_SEND).setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, app.name))
}

/** "Share with my data or clean?" — asked only when the app has saved something. */
@Composable
fun IncludeDataDialog(app: AppMeta, onChoice: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_data_title, app.name)) },
        text = { Column { Text(stringResource(R.string.share_data_text)) } },
        confirmButton = { TextButton(onClick = { onChoice(true) }) { Text(stringResource(R.string.share_with_data)) } },
        dismissButton = { TextButton(onClick = { onChoice(false) }) { Text(stringResource(R.string.share_without_data)) } },
    )
}
