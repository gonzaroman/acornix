package com.gonzaroman.acornix.ui.shortcuts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
@Composable
fun ShortcutsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val c = context.container
    val apps by c.apps.apps.collectAsState()
    val gallery by c.gallery.state.collectAsState()
    var selected by remember { mutableStateOf<AppMeta?>(null) }

    LaunchedEffect(Unit) { if (c.gallery.state.value.catalog == null) c.gallery.refresh() }

    val mine = apps.filter { it.kind == AppKind.FEATURE }
    val installed = apps.mapNotNull { it.source?.gallery }.toSet()
    val discover = gallery.catalog?.apps.orEmpty()
        // Only ones that work without an AI key, like the user's own shortcuts.
        .filter { "phone" in it.tags && "ai" !in it.tags && it.id !in installed }
        .let { Gallery.sorted(it, Gallery.Companion.Sort.FEATURED) }
        .take(12)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.create(feature = true)) },
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
                    Text(stringResource(R.string.tab_shortcuts), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.shortcuts_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        // Each example creates that shortcut straight away.
                        stringArrayResource(R.array.shortcuts_examples).forEach { example ->
                            AssistChip(onClick = { nav.navigate(Routes.create(feature = true, prompt = example)) }, label = { Text(example) })
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.shortcuts_mine)) }
                items(mine, key = { it.id }) { app ->
                    ShortcutTile(app, onClick = { AppLauncher.open(context, app.id) }, onLongClick = { selected = app })
                }
            }

            if (discover.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle(stringResource(R.string.shortcuts_discover)) {
                            TextButton(onClick = { nav.navigate(Routes.explore(Gallery.SHORTCUTS)) }) { Text(stringResource(R.string.shortcuts_see_all)) }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(discover, key = { it.id }) { app ->
                                GalleryPickCard(app, c.gallery.screenshotUrl(app), Modifier.width(140.dp)) { nav.navigate(Routes.galleryApp(app.id)) }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { app -> AppActionsSheet(app, nav, onDismiss = { selected = null }) }
}

/** A big colored button: the shortcut's emoji and name. Tap runs it, long-press shows its options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(app: AppMeta, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = TILE_COLORS[abs(app.id.hashCode()) % TILE_COLORS.size]
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.35f).clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(colors))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Text(app.emoji, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopStart))
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
