package com.gonzaroman.acornix.ui.shortcuts

import com.gonzaroman.acornix.ui.components.ConnectAiBanner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.Gallery
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.apps.AppActionsSheet
import com.gonzaroman.acornix.ui.components.GalleryPickCard
import com.gonzaroman.acornix.ui.components.SectionTitle
import com.gonzaroman.acornix.web.AppLauncher
import kotlin.math.abs

/**
 * Shortcuts for Android: the user's shortcuts as big tiles that run with one tap, a way to create one by saying
 * what it should do, and ready-made ones from the community gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val c = context.container
    val apps by c.apps.apps.collectAsState()
    val gallery by c.gallery.state.collectAsState()
    val schedules by c.schedules.all.collectAsState()
    var selected by remember { mutableStateOf<AppMeta?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val settings by c.settings.settings.collectAsState(initial = null)
    val hasAi = settings?.config()?.isUsable == true
    // Without an AI there is only one way to make a shortcut: step by step.
    fun newShortcut() = if (hasAi) choosing = true else nav.navigate(Routes.shortcutEditor())

    LaunchedEffect(Unit) { if (c.gallery.state.value.catalog == null) c.gallery.refresh() }

    // Gallery apps installed before shortcuts had their own tag may be marked as shortcuts: trust the catalog.
    val catalog = gallery.catalog?.apps.orEmpty().associateBy { it.id }
    val templates = com.gonzaroman.acornix.core.ShortcutTemplates.all(context.resources.configuration.locales[0].language == "es")
    val mine = apps.filter { app ->
        app.kind == AppKind.FEATURE && app.source?.gallery?.let { id -> catalog[id]?.let { "shortcut" in it.tags } ?: true } != false
    }
    val installed = apps.mapNotNull { it.source?.gallery }.toSet()
    val discover = gallery.catalog?.apps.orEmpty()
        // Only ones that work without an AI key, like the user's own shortcuts.
        .filter { "shortcut" in it.tags && "ai" !in it.tags && it.id !in installed }
        .let { Gallery.sorted(it, Gallery.Companion.Sort.FEATURED) }
        .take(12)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newShortcut() },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.shortcuts_new)) },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tab_shortcuts), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        // A big, obvious way into the tutorial.
                        Surface(
                            onClick = { nav.navigate(Routes.SHORTCUTS_HELP) },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                    Text("?", color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.help_shortcuts_button), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.shortcuts_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (settings != null && !hasAi) {
                item(key = "connect", span = { GridItemSpan(maxLineSpan) }) { ConnectAiBanner(title = R.string.connect_ai_banner_shortcuts) }
            }

            if (mine.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
                    ) {
                        Text("⚡", fontSize = 40.sp)
                        Text(stringResource(R.string.shortcuts_empty_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.shortcuts_empty_text), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        // With an AI, each example creates that shortcut straight away.
                        if (hasAi) {
                            stringArrayResource(R.array.shortcuts_examples).forEach { example ->
                                AssistChip(onClick = { nav.navigate(Routes.create(feature = true, prompt = example)) }, label = { Text(example) })
                            }
                        }
                        FilledTonalButton(onClick = { nav.navigate(Routes.shortcutEditor()) }) {
                            Text("🧩  " + stringResource(R.string.shortcut_new_manual))
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.shortcuts_mine)) }
                items(mine, key = { it.id }) { app ->
                    ShortcutTile(app, scheduleBadge(schedules.firstOrNull { it.appId == app.id }),
                        onClick = { AppLauncher.open(context, app.id) }, onLongClick = { selected = app })
                }
            }

            // Shortcuts from the community; until there are any, the built-in examples.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionTitle(stringResource(R.string.shortcuts_discover)) {
                        if (discover.isNotEmpty()) {
                            TextButton(onClick = { nav.navigate(Routes.explore(Gallery.SHORTCUTS)) }) { Text(stringResource(R.string.shortcuts_see_all)) }
                        } else {
                            TextButton(onClick = { nav.navigate(Routes.SHORTCUTS_HELP) }) { Text(stringResource(R.string.shortcuts_see_all)) }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (discover.isNotEmpty()) {
                            items(discover, key = { it.id }) { app ->
                                GalleryPickCard(app, c.gallery.screenshotUrl(app), Modifier.width(140.dp)) { nav.navigate(Routes.galleryApp(app.id)) }
                            }
                        } else {
                            items(templates, key = { it.id }) { t ->
                                TemplateCard(t) { nav.navigate(Routes.shortcutEditor(template = t.id)) }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { app -> AppActionsSheet(app, nav, onDismiss = { selected = null }) }

    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shortcut_new_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                NewShortcutOption("✨", stringResource(R.string.shortcut_new_ai), stringResource(R.string.shortcut_new_ai_desc)) {
                    choosing = false; nav.navigate(Routes.create(feature = true))
                }
                NewShortcutOption("🧩", stringResource(R.string.shortcut_new_manual), stringResource(R.string.shortcut_new_manual_desc)) {
                    choosing = false; nav.navigate(Routes.shortcutEditor())
                }
            }
        }
    }
}

@Composable
private fun NewShortcutOption(emoji: String, title: String, text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A ready-made example: tapping it opens it in the editor. */
@Composable
private fun TemplateCard(t: com.gonzaroman.acornix.core.ShortcutTemplate, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(160.dp).height(150.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(t.emoji, fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A big colored button: the shortcut's emoji and name. Tap runs it, long-press shows its options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(app: AppMeta, badge: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = TILE_COLORS[abs(app.id.hashCode()) % TILE_COLORS.size]
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.35f).clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(colors))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Text(app.emoji, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopStart))
        badge?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text(
            app.name,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private val TILE_COLORS = listOf(
    listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)),
    listOf(Color(0xFF4E54C8), Color(0xFF8F94FB)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
    listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
    listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
    listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
    listOf(Color(0xFF3A1C71), Color(0xFFD76D77)),
)
