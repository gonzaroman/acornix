package com.gonzaroman.acornix.ui.tools

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.core.Prompts
import com.gonzaroman.acornix.core.ResponseParser
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.ImportException
import com.gonzaroman.acornix.ui.components.AppIcon
import com.gonzaroman.acornix.ui.components.SectionTitle
import com.gonzaroman.acornix.ui.components.formatBytes
import com.gonzaroman.acornix.ui.importErrorMessage
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(nav: NavHostController, title: String, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        content = content,
    )
}

// ---------- Import / Export / full backups ----------

private enum class ExportMode { SHARE, SAVE }

@Composable
fun ImportExportScreen(nav: NavHostController) {
    val context = LocalContext.current
    val c = context.container
    val apps by c.apps.apps.collectAsState()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<AppMeta, ExportMode>?>(null) }
    var askData by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Pair<AppMeta, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val app = c.importExport.import(uri)
                Toast.makeText(context, context.getString(R.string.import_done, app.name), Toast.LENGTH_LONG).show()
            } catch (e: ImportException) {
                Toast.makeText(context, importErrorMessage(context, e), Toast.LENGTH_LONG).show()
            }
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val (app, withData) = saving ?: return@rememberLauncherForActivityResult
        saving = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { c.importExport.exportTo(app.id, uri, withData) }
                .onSuccess { Toast.makeText(context, R.string.export_done, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { c.backups.create(uri) }
                .onSuccess { Toast.makeText(context, context.getString(R.string.backup_done, it.apps), Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestore = uri
    }

    fun proceed(app: AppMeta, mode: ExportMode, withData: Boolean) {
        when (mode) {
            ExportMode.SHARE -> scope.launch { com.gonzaroman.acornix.ui.apps.shareAppZip(context, app, withData) }
            ExportMode.SAVE -> { saving = app to withData; saver.launch("${app.id}.zip") }
        }
    }

    fun start(app: AppMeta, mode: ExportMode) {
        scope.launch {
            if (c.data.hasData(app.id)) { pending = app to mode; askData = true } else proceed(app, mode, false)
        }
    }

    ToolScaffold(nav, stringResource(R.string.tool_import_export)) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("☁️ " + stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = {
                                val date = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                                backupSaver.launch("acornix-backup-$date.zip")
                            }) { Text(stringResource(R.string.backup_create)) }
                            OutlinedButton(enabled = !busy, onClick = {
                                backupPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            }) { Text(stringResource(R.string.backup_restore)) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.backup_auto), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("📦 " + stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.import_text), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                            Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.import_button))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.export_title))
            }
            items(apps, key = { it.id }) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    leadingContent = { AppIcon(app, 40.dp) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { start(app, ExportMode.SHARE) }) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) }
                            IconButton(onClick = { start(app, ExportMode.SAVE) }) { Icon(Icons.Rounded.FileDownload, stringResource(R.string.export_save)) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    val p = pending
    if (askData && p != null) {
        com.gonzaroman.acornix.ui.apps.IncludeDataDialog(
            p.first,
            onChoice = { withData -> askData = false; pending = null; proceed(p.first, p.second, withData) },
            onDismiss = { askData = false; pending = null },
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    busy = true
                    scope.launch {
                        runCatching { c.backups.restore(uri) }
                            .onSuccess { Toast.makeText(context, context.getString(R.string.backup_restored, it.apps), Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------- System health ----------

@Composable
fun HealthScreen(nav: NavHostController) {
    val context = LocalContext.current
    val c = context.container
    var tick by remember { mutableLongStateOf(0L) }
    var appsSize by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        appsSize = withContext(Dispatchers.IO) { c.apps.root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        while (true) {
            delay(3000)
            tick++
        }
    }
    val battery = remember(tick) { c.device.battery() }
    val memory = remember(tick) { c.device.memory() }
    val storage = remember(tick) { c.device.storage() }
    val info = remember { c.device.deviceInfo() }

    ToolScaffold(nav, stringResource(R.string.tool_health)) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MeterCard(
                emoji = if (battery.charging) "⚡" else "🔋",
                title = stringResource(R.string.health_battery),
                value = "${battery.level}%",
                progress = battery.level / 100f,
                detail = stringResource(
                    R.string.health_battery_detail,
                    stringResource(if (battery.charging) R.string.health_charging else R.string.health_not_charging),
                    battery.temperatureC,
                ),
            )
            MeterCard(
                emoji = "🧠",
                title = stringResource(R.string.health_ram),
                value = formatBytes(memory.availableBytes),
                progress = 1f - memory.availableBytes.toFloat() / memory.totalBytes.coerceAtLeast(1),
                detail = stringResource(R.string.health_free_of, formatBytes(memory.availableBytes), formatBytes(memory.totalBytes)),
            )
            MeterCard(
                emoji = "💾",
                title = stringResource(R.string.health_storage),
                value = formatBytes(storage.freeBytes),
                progress = 1f - storage.freeBytes.toFloat() / storage.totalBytes.coerceAtLeast(1),
                detail = stringResource(R.string.health_free_of, formatBytes(storage.freeBytes), formatBytes(storage.totalBytes)) +
                    " · " + stringResource(R.string.health_apps_size, formatBytes(appsSize)),
            )
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📱 " + stringResource(R.string.health_device), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    InfoLine(stringResource(R.string.health_model), "${info["manufacturer"]} ${info["model"]}")
                    InfoLine("Android", "${info["android"]} (API ${info["sdk"]})")
                    InfoLine(stringResource(R.string.health_cpu), "${info["cpuCores"]}")
                    InfoLine(stringResource(R.string.health_screen), "${info["screenWidth"]} × ${info["screenHeight"]}")
                    InfoLine(stringResource(R.string.health_flashlight), stringResource(if (info["hasFlashlight"] == true) R.string.yes else R.string.no))
                }
            }
        }
    }
}

@Composable
private fun MeterCard(emoji: String, title: String, value: String, progress: Float, detail: String) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Text("$emoji $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(8.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Help ----------

@Composable
fun HelpScreen(nav: NavHostController) {
    val sections = listOf(
        "✨" to (R.string.help_create_title to R.string.help_create_text),
        "🧬" to (R.string.help_feature_title to R.string.help_feature_text),
        "🤖" to (R.string.help_assistant_title to R.string.help_assistant_text),
        "👆" to (R.string.help_control_title to R.string.help_control_text),
        "📱" to (R.string.help_apps_title to R.string.help_apps_text),
        "🗄️" to (R.string.help_data_title to R.string.help_data_text),
        "🕰️" to (R.string.help_versions_title to R.string.help_versions_text),
        "📦" to (R.string.help_share_title to R.string.help_share_text),
        "🔑" to (R.string.help_keys_title to R.string.help_keys_text),
        "🌳" to (R.string.help_origin_title to R.string.help_origin_text),
    )
    ToolScaffold(nav, stringResource(R.string.tool_help)) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sections) { (emoji, texts) ->
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("$emoji ${stringResource(texts.first)}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(texts.second), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ---------- Manual studio (no API needed) ----------

@Composable
fun StudioScreen(nav: NavHostController) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var feature by rememberSaveable { mutableStateOf(false) }
    var idea by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var incomplete by remember { mutableStateOf<String?>(null) }
    val settings by c.settings.settings.collectAsState(initial = null)

    fun save(html: String) = scope.launch {
        val app = c.apps.create(
            name.ifBlank { idea.ifBlank { "App" }.take(30) }, html,
            if (feature) AppKind.FEATURE else AppKind.APP,
            description = idea, note = context.getString(R.string.section_studio),
        )
        AppLauncher.open(context, app.id)
        nav.popBackStack()
    }

    ToolScaffold(nav, stringResource(R.string.section_studio)) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.studio_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!feature, { feature = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.filter_apps)) }
                SegmentedButton(feature, { feature = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.filter_features)) }
            }
            SectionTitle("1 · " + stringResource(R.string.studio_step1))
            OutlinedTextField(idea, { idea = it }, label = { Text(stringResource(R.string.field_idea)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            FilledTonalButton(onClick = {
                val lang = Prompts.languageName(settings?.appLanguage ?: com.gonzaroman.acornix.data.AppLanguage.AUTO)
                c.device.copyToClipboard(Prompts.manualTemplate(idea, lang, feature))
                Toast.makeText(context, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy_prompt))
            }
            SectionTitle("2 · " + stringResource(R.string.studio_step2))
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                code, { code = it },
                label = { Text(stringResource(R.string.studio_code)) },
                minLines = 8, maxLines = 14,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { code = clipboardText(context) }) {
                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.paste))
                }
                Button(
                    onClick = {
                        val parsed = ResponseParser.parse(code)
                        val html = parsed.code
                        when {
                            html == null -> Toast.makeText(context, R.string.studio_invalid, Toast.LENGTH_LONG).show()
                            // Pieces lost while copying from the chat: warn instead of saving a broken app.
                            parsed.repaired || !ResponseParser.looksComplete(html) -> incomplete = html
                            else -> save(html)
                        }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.studio_save)) }
            }
        }
    }

    incomplete?.let { html ->
        AlertDialog(
            onDismissRequest = { incomplete = null },
            title = { Text(stringResource(R.string.studio_incomplete_title)) },
            text = { Text(stringResource(R.string.studio_incomplete_text)) },
            confirmButton = { TextButton(onClick = { incomplete = null; save(html) }) { Text(stringResource(R.string.save_anyway)) } },
            dismissButton = { TextButton(onClick = { incomplete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun clipboardText(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}
