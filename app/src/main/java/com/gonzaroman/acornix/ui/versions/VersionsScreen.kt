package com.gonzaroman.acornix.ui.versions

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.VersionInfo
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.components.AppIcon
import com.gonzaroman.acornix.ui.components.EmptyState
import com.gonzaroman.acornix.ui.components.formatBytes
import com.gonzaroman.acornix.ui.components.relativeTime
import kotlinx.coroutines.launch
import java.util.Date

/** "Time Machine": every creation, improvement and edit is a version that can be restored. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(nav: NavHostController, appId: String) {
    val context = LocalContext.current
    val repo = context.container.apps
    val scope = rememberCoroutineScope()
    val apps by repo.apps.collectAsState()
    val app = apps.firstOrNull { it.id == appId } ?: repo.get(appId)
    var versions by remember { mutableStateOf<List<VersionInfo>>(emptyList()) }
    var restoring by remember { mutableStateOf<VersionInfo?>(null) }
    var viewing by remember { mutableStateOf<Pair<VersionInfo, String>?>(null) }

    LaunchedEffect(appId, app?.versionCount, app?.updatedAt) { versions = repo.versions(appId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_versions)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
            if (app != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.name, style = MaterialTheme.typography.titleLarge)
                            Text(pluralStringResource(R.plurals.versions_count, versions.size, versions.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            itemsIndexed(versions, key = { _, v -> v.number }) { index, v ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // Timeline rail
                    Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                        if (index < versions.lastIndex) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).padding(bottom = 12.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.version_n, v.number) + if (index == 0) " · " + stringResource(R.string.version_current) else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(v.note, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${DateFormat.getMediumDateFormat(context).format(Date(v.createdAt))} ${DateFormat.getTimeFormat(context).format(Date(v.createdAt))} · ${formatBytes(v.size.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    scope.launch { viewing = v to repo.readVersion(appId, v.number) }
                                }) { Text(stringResource(R.string.view_code)) }
                                if (index != 0) {
                                    FilledTonalButton(onClick = { restoring = v }) { Text(stringResource(R.string.restore)) }
                                }
                            }
                        }
                    }
                }
            }
            if (versions.isEmpty()) {
                item { EmptyState("🕰️", stringResource(R.string.versions_empty), "") }
            }
        }
    }

    restoring?.let { v ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.restore_title, v.number)) },
            text = { Text(stringResource(R.string.restore_text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.restore(appId, v.number)
                        Toast.makeText(context, R.string.restored, Toast.LENGTH_SHORT).show()
                    }
                    restoring = null
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    viewing?.let { (v, code) ->
        ModalBottomSheet(onDismissRequest = { viewing = null }) {
            Text(stringResource(R.string.version_n, v.number), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(480.dp).padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.medium).background(Color(0xFF1B1D23))
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp),
            ) {
                Text(code, color = Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Pick an app to see its history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsHubScreen(nav: NavHostController) {
    val context = LocalContext.current
    val apps by context.container.apps.apps.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_versions)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Text(
                    stringResource(R.string.versions_hub_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(apps.sortedByDescending { it.updatedAt }, key = { it.id }) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text(stringResource(R.string.app_meta_line, app.versionCount, relativeTime(app.updatedAt))) },
                    leadingContent = { AppIcon(app, 44.dp) },
                    modifier = Modifier.clickable { nav.navigate(Routes.versions(app.id)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
