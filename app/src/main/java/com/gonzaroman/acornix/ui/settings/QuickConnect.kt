package com.gonzaroman.acornix.ui.settings

import com.gonzaroman.acornix.ai.AiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ai.ProviderType
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.ProviderConfig
import com.gonzaroman.acornix.data.Settings
import com.gonzaroman.acornix.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val FREE_KEY_URL = "https://aistudio.google.com/apikey"

/**
 * The short way to connect an AI, for someone who has never seen an API key: get a free one from Google,
 * paste it, done. The provider is worked out from the key, and the key is checked before it is saved.
 * "Other providers and models" opens the full [ProviderEditor].
 */
@Composable
fun QuickConnect(settings: Settings, modifier: Modifier = Modifier, onSaved: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.settings
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var advanced by rememberSaveable { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Chosen after a failure: one model may be busy or not included in a free key while another works.
    var model by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }

    if (advanced) {
        Column(modifier) {
            ProviderEditor(settings, onSaved = onSaved)
            TextButton(onClick = { advanced = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.quick_back))
            }
        }
        return
    }

    fun connect() {
        val clean = key.trim()
        val type = ProviderType.forKey(clean)
        // A model picked by hand is tried alone; otherwise the usual one and then the fallbacks, each with
        // a short wait, and the first that answers is the one saved.
        val candidates = model?.let { listOf(it) } ?: (listOf(settings.config(type).model) + type.fallbackModels).distinct()
        checking = true
        error = null
        scope.launch {
            var last: Exception? = null
            for (candidate in candidates) {
                val config = ProviderConfig(type, candidate, clean, "")
                try {
                    withTimeout(if (candidates.size > 1) 25_000 else 60_000) {
                        SettingsRepository.createProvider(config).streamText("You are a connection test.", "Reply with the single word OK.", quick = true)
                    }
                    repo.saveProvider(type, candidate, clean)
                    checking = false
                    onSaved()
                    return@launch
                } catch (e: TimeoutCancellationException) {
                    last = e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                    // A key the provider rejects won't work with another model either.
                    if ((e as? AiException)?.status in setOf(400, 401, 403)) break
                }
            }
            error = if (last is TimeoutCancellationException) context.getString(R.string.quick_timeout) else last?.message ?: last?.javaClass?.simpleName
            models = runCatching {
                chatModels(SettingsRepository.createProvider(ProviderConfig(type, candidates.first(), clean, "")).listModels())
            }.getOrDefault(emptyList())
            checking = false
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.quick_step1), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.quick_step1_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FREE_KEY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.quick_get_key))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.quick_step2), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; error = null },
            label = { Text(stringResource(R.string.api_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { key = it; error = null } }) {
                    Text(stringResource(R.string.quick_paste))
                }
            },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(stringResource(R.string.quick_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (error != null && models.isNotEmpty()) {
            Text(stringResource(R.string.quick_try_model), style = MaterialTheme.typography.bodyMedium)
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(model ?: settings.config(ProviderType.forKey(key)).model, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { model = m; menu = false; connect() }) }
                }
            }
        }
        Button(onClick = { connect() }, enabled = key.isNotBlank() && !checking, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.quick_checking))
            } else {
                Text(stringResource(R.string.quick_connect), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(stringResource(R.string.quick_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { advanced = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.quick_other))
        }
    }
}

/** Models that write text, without the image, voice, music and embedding ones a provider also lists. */
fun chatModels(all: List<String>): List<String> =
    all.filterNot { name -> NOT_CHAT.any { it in name.lowercase() } }

private val NOT_CHAT = listOf(
    "tts", "image", "embed", "lyria", "banana", "veo", "audio", "live", "research", "computer-use", "robotics",
    "whisper", "dall-e", "sora", "moderation", "transcribe", "realtime", "speech",
)

/**
 * "Try another model", for when the AI is connected but the chosen model fails. Picking one saves it
 * for the active provider and calls [onPicked].
 */
@Composable
fun ChangeModelDialog(onDismiss: () -> Unit, onPicked: () -> Unit) {
    val repo = LocalContext.current.container.settings
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>?>(null) }
    var current by remember { mutableStateOf("") }
    var gemini by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val config = repo.current().config()
        current = config.model
        gemini = config.type == ProviderType.GEMINI
        val all = runCatching { chatModels(SettingsRepository.createProvider(config).listModels()) }.getOrDefault(emptyList())
        // The ones known to work with a free key come first.
        val first = config.type.fallbackModels.filter { it in all || all.isEmpty() }
        models = first + all.filter { it !in first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_model_title)) },
        text = {
            val list = models
            when {
                list == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                list.isEmpty() -> Text(stringResource(R.string.models_unavailable))
                else -> Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.change_model_text) + if (gemini) " " + stringResource(R.string.change_model_gemini_hint) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    list.forEach { m ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val c = repo.current().config()
                                    repo.saveProvider(c.type, m, c.apiKey, if (c.type == ProviderType.CUSTOM) c.baseUrl else null)
                                    onPicked()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text((if (m == current) "✓ " else "") + m, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
