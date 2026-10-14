package com.gonzaroman.acornix.ui.data

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppDataStore
import com.gonzaroman.acornix.data.CollectionInfo
import com.gonzaroman.acornix.data.StoredFile
import com.gonzaroman.acornix.ui.components.AppIcon
import com.gonzaroman.acornix.ui.components.EmptyState
import com.gonzaroman.acornix.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

private val pretty = Json { prettyPrint = true }

private data class DataUi(
    val summary: AppDataStore.Summary,
    val collections: List<CollectionInfo>,
    val keys: Map<String, JsonElement>,
    val files: List<StoredFile>,
)

/** Everything an app has saved: collections, single values and files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(nav: NavHostController, appId: String) {
    val context = LocalContext.current
    val c = context.container
    val store = c.data
    val app = remember(appId) { c.apps.get(appId) }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<DataUi?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var docs by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    suspend fun reload() {
        ui = DataUi(store.summary(appId), store.collections(appId), store.kvAll(appId), store.listFiles(appId))
        expanded?.let { docs = store.list(appId, it, com.gonzaroman.acornix.data.DocQuery(orderBy = "_created", desc = true, limit = 200)) }
    }
    LaunchedEffect(appId) {
        reload()
        store.changes.filter { it.appId == appId }.collect { reload() }
    }
    LaunchedEffect(expanded) {
        docs = expanded?.let { store.list(appId, it, com.gonzaroman.acornix.data.DocQuery(orderBy = "_created", desc = true, limit = 200)) }.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_title)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    val hasData = ui?.summary?.let { it.documents + it.keys + it.files > 0 } == true
                    IconButton(enabled = hasData, onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                File(File(context.cacheDir, "exports").apply { mkdirs() }, "${appId}-data.json").apply {
                                    writeText(pretty.encodeToString(JsonElement.serializer(), store.export(appId)))
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    null,
                                ),
                            )
                        }
                    }) { Icon(Icons.Rounded.IosShare, stringResource(R.string.data_export)) }
                    IconButton(enabled = hasData, onClick = { confirmClear = true }) {
                        Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.data_clear))
                    }
                },
            )
        },
    ) { padding ->
        val state = ui
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app != null) AppIcon(app, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app?.name ?: appId, style = MaterialTheme.typography.titleLarge)
                        if (state != null) {
                            Text(
                                listOf(
                                    pluralStringResource(R.plurals.data_records, state.summary.documents, state.summary.documents),
                                    pluralStringResource(R.plurals.data_collections, state.summary.collections, state.summary.collections),
                                    pluralStringResource(R.plurals.data_values, state.summary.keys, state.summary.keys),
                                    pluralStringResource(R.plurals.data_files, state.summary.files, state.summary.files) +
                                        " (${formatBytes(state.summary.fileBytes)})",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.data_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state != null && state.collections.isEmpty() && state.keys.isEmpty() && state.files.isEmpty()) {
                item { EmptyState("🗄️", stringResource(R.string.data_empty_title), stringResource(R.string.data_empty_text)) }
            }
            if (state != null) {
                state.collections.forEach { col ->
                    item(key = "col-${col.name}") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth().clickable { expanded = if (expanded == col.name) null else col.name },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📚", fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(col.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.data_collection_line, pluralStringResource(R.plurals.data_records, col.count, col.count), col.fields.ifBlank { "—" }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(if (expanded == col.name) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                            }
                        }
                    }
                    if (expanded == col.name) {
                        items(docs, key = { "doc-${col.name}-${(it["_id"] as? JsonPrimitive)?.content}" }) { doc ->
                            DocCard(doc) {
                                val id = (doc["_id"] as? JsonPrimitive)?.content ?: return@DocCard
                                scope.launch { store.remove(appId, col.name, id, AppDataStore.SOURCE_SYSTEM) }
                            }
                        }
                    }
                }
                if (state.keys.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.data_keys), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(state.keys.entries.toList(), key = { "kv-${it.key}" }) { (key, value) ->
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(key, style = MaterialTheme.typography.labelLarge)
                                    Text(value.toString(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { scope.launch { store.kvRemove(appId, key, AppDataStore.SOURCE_SYSTEM) } }) {
                                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
                if (state.files.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.data_files), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(state.files, key = { "file-${it.name}" }) { f ->
                        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = f.file, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatBytes(f.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { scope.launch { store.deleteFile(appId, f.name, AppDataStore.SOURCE_SYSTEM) } }) {
                                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.data_clear_title)) },
            text = { Text(stringResource(R.string.data_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        store.clear(appId, null, AppDataStore.SOURCE_SYSTEM)
                        store.listFiles(appId).forEach { store.deleteFile(appId, it.name, AppDataStore.SOURCE_SYSTEM) }
                    }
                }) { Text(stringResource(R.string.data_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DocCard(doc: JsonObject, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp).animateContentSize().clickable { open = !open },
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            val shown = JsonObject(doc.filterKeys { !it.startsWith("_") })
            Text(
                pretty.encodeToString(JsonElement.serializer(), shown),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = if (open) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete)) }
        }
    }
}
