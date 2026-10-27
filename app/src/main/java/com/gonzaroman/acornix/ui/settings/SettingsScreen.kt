package com.gonzaroman.acornix.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.components.rememberControlState
import com.gonzaroman.acornix.ui.components.openAccessibilitySettings
import com.gonzaroman.acornix.ui.components.manufacturerName
import com.gonzaroman.acornix.ui.components.closesBackgroundApps
import com.gonzaroman.acornix.ui.components.ControlState
import com.gonzaroman.acornix.BuildConfig
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ai.ProviderType
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppLanguage
import com.gonzaroman.acornix.data.ProviderConfig
import com.gonzaroman.acornix.data.Settings as AppSettings
import com.gonzaroman.acornix.data.SettingsRepository
import com.gonzaroman.acornix.data.ThemeMode
import com.gonzaroman.acornix.ui.components.SectionTitle
import com.gonzaroman.acornix.ui.components.UpdateCard
import com.gonzaroman.acornix.ui.share.openGitHub
import com.gonzaroman.acornix.ui.share.shareAcornixLink
import com.gonzaroman.acornix.data.UpdateInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val repo = context.container.settings
    val settings by repo.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val s = settings ?: return
    val controlState by rememberControlState()
    val controlEnabled = controlState == ControlState.ON

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)

        SectionTitle(stringResource(R.string.settings_ai))
        SettingsCard { ProviderEditor(s, modifier = Modifier.padding(16.dp)) }

        SectionTitle(stringResource(R.string.settings_generated_apps))
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_app_language), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(AppLanguage.AUTO to R.string.lang_auto, AppLanguage.SPANISH to R.string.lang_es, AppLanguage.ENGLISH to R.string.lang_en)
                    options.forEachIndexed { i, (lang, label) ->
                        SegmentedButton(
                            selected = s.appLanguage == lang,
                            onClick = { scope.launch { repo.setAppLanguage(lang) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            SwitchRow(stringResource(R.string.settings_ai_ideas), stringResource(R.string.settings_ai_ideas_desc), s.aiIdeas) {
                scope.launch { repo.setAiIdeas(it) }
            }
        }

        SectionTitle(stringResource(R.string.settings_assistant))
        SettingsCard {
            SwitchRow(stringResource(R.string.settings_speak), stringResource(R.string.settings_speak_desc), s.speakReplies) {
                scope.launch { repo.setSpeakReplies(it) }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_control)) },
                supportingContent = {
                    Text(
                        stringResource(
                            when (controlState) {
                                ControlState.ON -> R.string.settings_control_on
                                ControlState.STALLED -> R.string.settings_control_stalled
                                ControlState.OFF -> R.string.settings_control_off
                            },
                        ),
                    )
                },
                leadingContent = {
                    Icon(
                        if (controlEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                        tint = when (controlState) {
                            ControlState.ON -> MaterialTheme.colorScheme.primary
                            ControlState.STALLED -> MaterialTheme.colorScheme.error
                            ControlState.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingContent = {
                    TextButton(onClick = { openAccessibilitySettings(context) }) {
                        Text(stringResource(if (controlEnabled) R.string.manage else R.string.enable))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (!controlEnabled && closesBackgroundApps()) {
                Text(
                    stringResource(R.string.control_oem_tip, manufacturerName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            TextButton(onClick = { nav.navigate(Routes.CONTROL_HELP) }, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.control_help))
            }
        }

        SectionTitle(stringResource(R.string.settings_appearance))
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.theme == mode,
                            onClick = { scope.launch { repo.setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_desc), s.dynamicColor) {
                    scope.launch { repo.setDynamicColor(it) }
                }
            }
        }

        SectionTitle(stringResource(R.string.support_title))
        SettingsCard {
            ListItem(
                leadingContent = { Text("⭐", fontSize = 22.sp) },
                headlineContent = { Text(stringResource(R.string.support_star)) },
                supportingContent = { Text(stringResource(R.string.support_star_desc)) },
                modifier = Modifier.clickable { openGitHub(context) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                leadingContent = { Text("📣", fontSize = 22.sp) },
                headlineContent = { Text(stringResource(R.string.support_share)) },
                supportingContent = { Text(stringResource(R.string.support_share_desc)) },
                modifier = Modifier.clickable { shareAcornixLink(context) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        SectionTitle(stringResource(R.string.settings_about))
        SettingsCard {
            ListItem(
                headlineContent = { Text("Acornix ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text(stringResource(R.string.settings_about_text)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gonzaroman/acornix")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            UpdateRow()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data object Ok : TestState
    data class Failed(val message: String) : TestState
}

/** Provider + model + API key form, shared by Settings and onboarding. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderEditor(settings: AppSettings, modifier: Modifier = Modifier, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = context.container.settings
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(settings.provider) }
    var model by remember { mutableStateOf(settings.config(type).model) }
    var key by remember { mutableStateOf(settings.config(type).apiKey) }
    var baseUrl by remember { mutableStateOf(settings.customBaseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val latest by rememberUpdatedState(settings)
    LaunchedEffect(type) {
        val c = latest.config(type)
        model = c.model
        key = c.apiKey
        models = emptyList()
        test = TestState.Idle
    }
    fun current() = ProviderConfig(type, model.trim(), key.trim(), if (type == ProviderType.CUSTOM) baseUrl.trim() else "")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.provider), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderType.entries.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t; saved = false },
                    label = { Text(if (t == ProviderType.CUSTOM) stringResource(R.string.provider_custom) else t.label) },
                )
            }
        }

        if (type == ProviderType.CUSTOM) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                label = { Text(stringResource(R.string.base_url)) },
                placeholder = { Text("https://openrouter.ai/api/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = key,
            onValueChange = { key = it; saved = false },
            label = { Text(stringResource(if (type == ProviderType.CUSTOM) R.string.api_key_optional else R.string.api_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, stringResource(R.string.show_key))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        keyHelp(type)?.let { (label, url) ->
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }

        Box {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                label = { Text(stringResource(R.string.model)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            models = runCatching { SettingsRepository.createProvider(current()).listModels() }.getOrDefault(emptyList())
                            menuOpen = true
                        }
                    }) { Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.load_models)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                if (models.isEmpty()) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.models_unavailable)) }, onClick = { menuOpen = false })
                }
                models.forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { model = m; menuOpen = false; saved = false })
                }
            }
        }

        when (val t = test) {
            TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.testing))
            }
            TestState.Ok -> Text("✅ " + stringResource(R.string.test_ok), color = MaterialTheme.colorScheme.primary)
            is TestState.Failed -> Text("❌ " + t.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TestState.Idle -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    test = TestState.Running
                    scope.launch {
                        test = try {
                            withTimeout(90_000) {
                                SettingsRepository.createProvider(current()).streamText("You are a connection test.", "Reply with the single word OK.", quick = true)
                            }
                            TestState.Ok
                        } catch (e: Exception) {
                            TestState.Failed(e.message ?: e.javaClass.simpleName)
                        }
                    }
                },
                enabled = current().isUsable && test != TestState.Running,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.test_connection)) }
            Button(
                onClick = {
                    scope.launch {
                        val c = current()
                        repo.saveProvider(c.type, c.model, c.apiKey, if (c.type == ProviderType.CUSTOM) c.baseUrl else null)
                        saved = true
                        onSaved()
                    }
                },
                enabled = current().isUsable,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(if (saved) R.string.saved else R.string.save)) }
        }
    }
}

@Composable
private fun keyHelp(type: ProviderType): Pair<String, String>? = when (type) {
    ProviderType.ANTHROPIC -> stringResource(R.string.get_key, "console.anthropic.com") to "https://console.anthropic.com/settings/keys"
    ProviderType.OPENAI -> stringResource(R.string.get_key, "platform.openai.com") to "https://platform.openai.com/api-keys"
    ProviderType.GEMINI -> stringResource(R.string.get_key, "aistudio.google.com") to "https://aistudio.google.com/apikey"
    ProviderType.CUSTOM -> null
}

@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val updates = context.container.updates
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        OutlinedButton(
            enabled = !checking,
            onClick = {
                checking = true; message = null
                scope.launch {
                    try {
                        found = updates.check(force = true)
                        if (found == null) message = context.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
                    } catch (e: Exception) {
                        message = context.getString(R.string.update_check_failed)
                    }
                    checking = false
                }
            },
        ) { Text(stringResource(if (checking) R.string.update_checking else R.string.update_check)) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        found?.let { UpdateCard(it, Modifier.padding(top = 10.dp), showLater = false) }
    }
}
