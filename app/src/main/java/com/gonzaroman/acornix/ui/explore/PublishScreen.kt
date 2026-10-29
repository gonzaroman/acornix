package com.gonzaroman.acornix.ui.explore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.GitHubAccount
import com.gonzaroman.acornix.data.GitHubException
import com.gonzaroman.acornix.data.Publisher
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Sending one of the user's apps to the community gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(nav: NavHostController, appId: String) {
    val context = LocalContext.current
    val c = context.container
    val apps by c.apps.apps.collectAsState()
    val login by c.github.login.collectAsState()
    val meta = apps.firstOrNull { it.id == appId }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.publish_title)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (meta == null) return@Scaffold
        val start = remember(meta.id) { c.publisher.draft(meta) }
        var name by rememberSaveable { mutableStateOf(start.name) }
        var emoji by rememberSaveable { mutableStateOf(start.emoji) }
        var category by rememberSaveable { mutableStateOf<String?>(null) }
        var description by rememberSaveable { mutableStateOf("") }
        var prompt by rememberSaveable { mutableStateOf(start.prompt) }
        var accepted by rememberSaveable { mutableStateOf(false) }
        var sending by remember { mutableStateOf(false) }
        var sentUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var deviceCode by remember { mutableStateOf<GitHubAccount.DeviceCode?>(null) }
        var signIn by remember { mutableStateOf<Job?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        // Checked again when coming back from opening the app, which takes its screenshot.
        var problem by remember { mutableStateOf(c.publisher.check(appId)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(appId) {
            problem = c.publisher.check(appId)
            onPauseOrDispose { }
        }
        val isUpdate = meta.source?.let { login != null && it.author.equals(login, ignoreCase = true) } == true

        // Shown under the button until the next try, so it can be read (and reported) calmly.
        fun fail(e: Exception) {
            error = when ((e as? GitHubException)?.reason) {
                GitHubException.Reason.DENIED -> context.getString(R.string.github_denied)
                GitHubException.Reason.EXPIRED -> context.getString(R.string.github_expired)
                else -> context.getString(R.string.publish_failed, e.message ?: e.javaClass.simpleName)
            }
        }

        fun send() {
            error = null
            sending = true
            scope.launch {
                try {
                    val draft = start.copy(name = name.trim(), emoji = emoji.trim().ifEmpty { meta.emoji }, category = category.orEmpty(),
                        description = description.trim(), prompt = prompt.trim())
                    sentUrl = c.publisher.publish(appId, draft)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(e)
                }
                sending = false
            }
        }

        /** Signs in with a device code first if needed, then publishes. */
        fun publish() {
            error = null
            if (c.github.token() != null) return send()
            signIn = scope.launch {
                try {
                    val code = c.github.start()
                    deviceCode = code
                    c.github.await(code)
                    com.gonzaroman.acornix.ui.share.SupportPrefs(context).askStarAfterGitHubSignIn()
                    deviceCode = null
                    send()
                } catch (e: CancellationException) {
                    deviceCode = null
                    throw e
                } catch (e: Exception) {
                    deviceCode = null
                    fail(e)
                }
            }
        }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val url = sentUrl
            when {
                url != null -> {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.publish_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.publish_done_text), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { openUrl(context, url) }) { Text(stringResource(R.string.publish_view_request)) }
                    TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.back)) }
                    return@Column
                }
                !c.github.available -> {
                    Notice(stringResource(R.string.publish_unavailable))
                    return@Column
                }
            }

            Text(stringResource(R.string.publish_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isUpdate) Notice(stringResource(R.string.publish_update))

            when (problem) {
                Publisher.Problem.NO_SCREENSHOT -> {
                    Notice(stringResource(R.string.publish_no_screenshot))
                    Button(onClick = { AppLauncher.open(context, appId) }) { Text(stringResource(R.string.publish_open_app)) }
                    return@Column
                }
                Publisher.Problem.NOT_TEXT -> { Notice(stringResource(R.string.publish_not_text)); return@Column }
                Publisher.Problem.TOO_BIG -> { Notice(stringResource(R.string.publish_too_big)); return@Column }
                null -> Unit
            }

            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = c.apps.thumbnailFile(appId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.width(96.dp).height(170.dp).clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(Publisher.MAX_NAME) },
                        label = { Text(stringResource(R.string.publish_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = emoji, onValueChange = { emoji = it.take(8) },
                        label = { Text(stringResource(R.string.publish_emoji)) }, singleLine = true, modifier = Modifier.width(110.dp),
                    )
                }
            }

            Text(stringResource(R.string.publish_category), style = MaterialTheme.typography.titleSmall)
            CategoryPicker(category) { category = it }

            OutlinedTextField(
                value = description, onValueChange = { description = it.take(Publisher.MAX_DESCRIPTION) },
                label = { Text(stringResource(R.string.publish_description)) },
                supportingText = { Text(stringResource(R.string.publish_description_hint)) },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it.take(Publisher.MAX_PROMPT) },
                label = { Text(stringResource(R.string.publish_prompt)) },
                supportingText = { Text(stringResource(R.string.publish_prompt_hint)) },
                minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Text(stringResource(R.string.publish_accept), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { openUrl(context, Publisher.RULES_URL) }) { Text(stringResource(R.string.publish_rules)) }

            val ready = accepted && name.isNotBlank() && description.isNotBlank() && category != null && !sending && deviceCode == null
            if (sending) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.publish_sending))
                }
            } else {
                Button(onClick = { publish() }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.publish_button)) }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

            val who = login
            if (who != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.publish_signed_in, who), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { c.github.signOut() }) { Text(stringResource(R.string.publish_sign_out)) }
                }
            } else {
                Text(stringResource(R.string.publish_needs_github), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }

        deviceCode?.let { code ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.github_code_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.github_code_text))
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(code.userCode, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                        Button(onClick = {
                            copy(context, code.userCode)
                            openUrl(context, code.verificationUri)
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.github_code_open)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.github_code_waiting), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { signIn?.cancel() }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("GitHub code", text))
    Toast.makeText(context, R.string.github_code_copied, Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The 19 categories don't fit in a row of chips, so they're a list that opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = selected?.let { category(it) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current?.let { "${it.emoji}  " + stringResource(it.label) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(stringResource(R.string.publish_category_pick)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CATEGORIES.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.emoji}  " + stringResource(c.label)) },
                    onClick = { onPick(c.id); open = false },
                )
            }
        }
    }
}
