package com.gonzaroman.acornix.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.core.AppReviewResult
import com.gonzaroman.acornix.data.ImportException
import com.gonzaroman.acornix.data.ImportPreview
import com.gonzaroman.acornix.ui.importErrorMessage
import kotlinx.coroutines.launch

/**
 * Before installing an app someone shared: it's the user's responsibility, and their AI can review the
 * code first. Shown for ZIPs opened from other apps and from the import screen.
 */
@Composable
fun ImportReviewDialog(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<AppReviewResult?>(null) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var canReview by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            preview = c.importExport.preview(uri)
            canReview = c.settings.current().config().isUsable
        } catch (e: ImportException) {
            Toast.makeText(context, importErrorMessage(context, e), Toast.LENGTH_LONG).show()
            onDone()
        }
    }
    val p = preview ?: return

    fun install() {
        installing = true
        scope.launch {
            try {
                val app = c.importExport.install(p)
                Toast.makeText(context, context.getString(R.string.import_done, app.name), Toast.LENGTH_LONG).show()
            } catch (e: ImportException) {
                Toast.makeText(context, importErrorMessage(context, e), Toast.LENGTH_LONG).show()
            }
            onDone()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!reviewing && !installing) onDone() },
        title = { Text(stringResource(R.string.import_review_title, p.name)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.import_review_warning))
                when {
                    reviewing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.import_review_running), style = MaterialTheme.typography.bodyMedium)
                    }
                    review != null -> ReviewCard(review!!)
                    reviewError != null -> Text(reviewError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    !canReview -> Text(stringResource(R.string.import_review_no_ai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canReview && review == null && !reviewing) {
                    FilledTonalButton(onClick = {
                        reviewing = true
                        reviewError = null
                        scope.launch {
                            try {
                                review = c.appReview.review(p.name, p.code)
                            } catch (e: Exception) {
                                reviewError = context.getString(R.string.import_review_failed)
                            }
                            reviewing = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_review_button)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { install() }, enabled = !reviewing && !installing) {
                Text(stringResource(if (review?.verdict == "danger") R.string.import_install_anyway else R.string.import_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDone, enabled = !installing) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReviewCard(r: AppReviewResult) {
    val (emoji, title, color) = when (r.verdict) {
        "safe" -> Triple("✅", R.string.import_review_safe, MaterialTheme.colorScheme.secondaryContainer)
        "danger" -> Triple("⛔", R.string.import_review_danger, MaterialTheme.colorScheme.errorContainer)
        else -> Triple("⚠️", R.string.import_review_caution, MaterialTheme.colorScheme.tertiaryContainer)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$emoji  " + stringResource(title), style = MaterialTheme.typography.titleSmall)
            if (r.summary.isNotBlank()) Text(r.summary, style = MaterialTheme.typography.bodyMedium)
            r.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.import_review_disclaimer), style = MaterialTheme.typography.labelSmall)
        }
    }
}
