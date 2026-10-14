package com.gonzaroman.acornix.ui.apps

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.device.ShortcutHelper
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.components.AppCard
import com.gonzaroman.acornix.ui.components.AppIcon
import com.gonzaroman.acornix.ui.components.EmptyState
import com.gonzaroman.acornix.ui.components.relativeTime
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.launch

private enum class Filter(val label: Int) { ALL(R.string.filter_all), APPS(R.string.filter_apps), FEATURES(R.string.filter_features), SAMPLES(R.string.filter_samples) }

@Composable
fun AppsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val apps by context.container.apps.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var selected by remember { mutableStateOf<AppMeta?>(null) }

    val visible = apps.filter { app ->
        val matchesFilter = when (filter) {
            Filter.ALL -> true
            Filter.APPS -> app.kind == AppKind.APP
            Filter.FEATURES -> app.kind == AppKind.FEATURE
            Filter.SAMPLES -> app.kind == AppKind.SAMPLE
        }
        matchesFilter && (query.isBlank() || app.name.contains(query, true) || app.description.contains(query, true))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.create()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.new_app)) },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.tab_apps), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Filter.entries) { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) })
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        emoji = if (apps.isEmpty()) "🌱" else "🔍",
                        title = stringResource(if (apps.isEmpty()) R.string.apps_empty_title else R.string.apps_no_results),
                        text = stringResource(if (apps.isEmpty()) R.string.apps_empty_text else R.string.apps_no_results_text),
                    ) {
                        if (apps.isEmpty()) Button(onClick = { nav.navigate(Routes.create()) }) { Text(stringResource(R.string.hero_button)) }
                    }
                }
            }
            items(visible, key = { it.id }) { app ->
                AppCard(
                    app = app,
                    onClick = { AppLauncher.open(context, app.id) },
                    onLongClick = { selected = app },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    selected?.let { app -> AppActionsSheet(app, nav, onDismiss = { selected = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(app: AppMeta, nav: NavHostController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var askData by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.app_meta_line, app.versionCount, relativeTime(app.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SheetAction(Icons.Rounded.PlayArrow, stringResource(R.string.action_open)) { onDismiss(); AppLauncher.open(context, app.id) }
        SheetAction(Icons.Rounded.AutoFixHigh, stringResource(R.string.action_improve)) { onDismiss(); nav.navigate(Routes.improve(app.id)) }
        SheetAction(Icons.Rounded.Code, stringResource(R.string.action_edit_code)) { onDismiss(); nav.navigate(Routes.editor(app.id)) }
        SheetAction(Icons.Rounded.History, stringResource(R.string.action_versions)) { onDismiss(); nav.navigate(Routes.versions(app.id)) }
        SheetAction(Icons.Rounded.Storage, stringResource(R.string.action_data)) { onDismiss(); nav.navigate(Routes.data(app.id)) }
        SheetAction(Icons.AutoMirrored.Rounded.AddToHomeScreen, stringResource(R.string.action_pin)) {
            val ok = ShortcutHelper.pin(context, app, c.apps)
            if (!ok) Toast.makeText(context, R.string.pin_unsupported, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        SheetAction(Icons.Rounded.Share, stringResource(R.string.action_share)) {
            scope.launch {
                if (c.data.hasData(app.id)) askData = true else { shareAppZip(context, app, false); onDismiss() }
            }
        }
        SheetAction(Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.action_rename)) { renaming = true }
        SheetAction(Icons.Rounded.Delete, stringResource(R.string.action_delete), MaterialTheme.colorScheme.error) { deleting = true }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(16.dp))
    }

    if (askData) {
        IncludeDataDialog(
            app,
            onChoice = { withData ->
                askData = false
                scope.launch { shareAppZip(context, app, withData); onDismiss() }
            },
            onDismiss = { askData = false },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(app.name) }
        var emoji by remember { mutableStateOf(app.emoji) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text(stringResource(R.string.field_emoji)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.apps.rename(app.id, name, emoji) }
                    renaming = false
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_title, app.name)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.apps.delete(app.id) }
                    deleting = false
                    onDismiss()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(icon, null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
