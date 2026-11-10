package com.gonzaroman.acornix.ui.settings

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
        val config = ProviderConfig(type, settings.config(type).model, clean, "")
        checking = true
        error = null
        scope.launch {
            try {
                withTimeout(60_000) {
                    SettingsRepository.createProvider(config).streamText("You are a connection test.", "Reply with the single word OK.", quick = true)
                }
                repo.saveProvider(type, config.model, clean)
                checking = false
                onSaved()
            } catch (e: Exception) {
                checking = false
                error = e.message ?: e.javaClass.simpleName
            }
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
