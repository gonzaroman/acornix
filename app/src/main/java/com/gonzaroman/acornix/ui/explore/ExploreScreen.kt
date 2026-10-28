package com.gonzaroman.acornix.ui.explore

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.Gallery
import com.gonzaroman.acornix.data.GalleryApp
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.components.EmptyState
import com.gonzaroman.acornix.ui.components.NameClashDialog
import com.gonzaroman.acornix.ui.components.formatBytes
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.launch

private val CATEGORY_LABELS = mapOf(
    "games" to R.string.explore_cat_games,
    "tools" to R.string.explore_cat_tools,
    "creative" to R.string.explore_cat_creative,
    "health" to R.string.explore_cat_health,
    "learning" to R.string.explore_cat_learning,
)

/** The community gallery: search, filter by category and open an app's page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val state by c.gallery.state.collectAsState()
    val installed by c.apps.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { c.gallery.refresh() }

    val all = state.catalog?.apps.orEmpty()
    // Only the categories that have apps, plus "with AI" when some app uses it.
    val filters = Gallery.CATEGORIES.filter { cat -> all.any { it.category == cat } } +
        listOfNotNull("ai".takeIf { all.any { "ai" in it.tags } })
    val shown = Gallery.search(all, query, filter)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explore_title)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            when {
                state.catalog == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.catalog == null -> EmptyState("📡", stringResource(R.string.explore_failed), "", Modifier.fillMaxSize()) {
                    Button(onClick = { scope.launch { c.gallery.refresh() } }) { Text(stringResource(R.string.explore_retry)) }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.failed) {
                                Text(stringResource(R.string.explore_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.explore_search)) },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, stringResource(R.string.cancel)) }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.explore_all)) })
                                filters.forEach { f ->
                                    FilterChip(
                                        selected = filter == f,
                                        onClick = { filter = if (filter == f) null else f },
                                        label = { Text(stringResource(CATEGORY_LABELS[f] ?: R.string.explore_cat_ai)) },
                                    )
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.explore_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                    items(shown, key = { it.id }) { app ->
                        val mine = installed.firstOrNull { it.source?.gallery == app.id }
                        GalleryCard(app, c.gallery.screenshotUrl(app), mine, onClick = { nav.navigate(Routes.galleryApp(app.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCard(app: GalleryApp, screenshot: String, mine: AppMeta?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            Screenshot(screenshot, Modifier.fillMaxWidth().aspectRatio(0.8f))
            val badge = when {
                mine == null -> null
                (mine.source?.version ?: 0) < app.version -> "⬆ " + stringResource(R.string.explore_has_update)
                else -> "✓ " + stringResource(R.string.explore_installed)
            }
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if ("ai" in app.tags) Badge("🧠 AI")
                badge?.let { Badge(it) }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("${app.emoji} ${app.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.explore_by, app.author), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Screenshots are portrait phones or landscape games: crop to the top, where the app's title usually is. */
@Composable
private fun Screenshot(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** One app's page: what it is, who made it, what it uses, and install / try / remix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryAppScreen(nav: NavHostController, id: String) {
    val context = LocalContext.current
    val c = context.container
    val state by c.gallery.state.collectAsState()
    val apps by c.apps.apps.collectAsState()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var clash by remember { mutableStateOf<AppMeta?>(null) }

    LaunchedEffect(Unit) { if (state.catalog == null) c.gallery.refresh() }
    val app = state.catalog?.apps?.firstOrNull { it.id == id }
    val mine = app?.let { a -> apps.firstOrNull { it.source?.gallery == a.id } }
    val hasUpdate = app != null && mine != null && (mine.source?.version ?: 0) < app.version

    fun run(block: suspend () -> Unit) {
        working = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.gallery_failed, Toast.LENGTH_LONG).show()
            }
            working = false
        }
    }

    fun installNew(a: GalleryApp, openImprove: Boolean) = run {
        val meta = c.gallery.install(a)
        Toast.makeText(context, context.getString(R.string.gallery_done, meta.name), Toast.LENGTH_SHORT).show()
        if (openImprove) nav.navigate(Routes.improve(meta.id))
    }

    fun replace(a: GalleryApp, target: AppMeta) = run {
        val meta = c.gallery.replace(a, target.id)
        Toast.makeText(context, context.getString(R.string.gallery_updated, meta.name), Toast.LENGTH_SHORT).show()
    }

    /** Installing a second copy for a remix is fine; otherwise a name the user already has asks first. */
    fun install(a: GalleryApp, forRemix: Boolean) {
        val existing = c.apps.findByName(a.name)
        if (existing != null && !forRemix) {
            clash = existing
        } else {
            installNew(a, forRemix)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (app == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator()
                else Text(stringResource(R.string.gallery_not_found), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(app.emoji, fontSize = 34.sp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.explore_by, app.author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    CATEGORY_LABELS[app.category]?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Main action: install, or open / update the copy already installed.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    working -> Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.gallery_working))
                    }
                    mine != null -> {
                        Button(onClick = { AppLauncher.open(context, mine.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gallery_open)) }
                        if (hasUpdate) FilledTonalButton(onClick = { replace(app, mine) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gallery_update)) }
                    }
                    else -> Button(onClick = { install(app, forRemix = false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gallery_install)) }
                }
                if (!working) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { run { AppLauncher.open(context, c.gallery.prepareTrial(app)) } },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.gallery_try), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        OutlinedButton(
                            onClick = { install(app, forRemix = true) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.gallery_remix), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text(stringResource(R.string.gallery_remix_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Screenshot(c.gallery.screenshotUrl(app), Modifier.fillMaxWidth().aspectRatio(0.75f).clip(MaterialTheme.shapes.large))

            Text(app.description, style = MaterialTheme.typography.bodyLarge)

            if (app.prompt.isNotBlank()) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("💬 " + stringResource(R.string.gallery_prompt_title), style = MaterialTheme.typography.titleSmall)
                        Text("“${app.prompt}”", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { nav.navigate(Routes.create(prompt = app.prompt)) }, contentPadding = PaddingValues(0.dp)) {
                            Text("✨ " + stringResource(R.string.gallery_prompt_use))
                        }
                    }
                }
            }

            val uses = app.tags.mapNotNull { TAG_LABELS[it] }
            if (uses.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.gallery_uses_title), style = MaterialTheme.typography.titleSmall)
                    uses.forEach { (emoji, label) ->
                        Text("$emoji  " + stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column {
                Text(
                    stringResource(R.string.gallery_footer, formatBytes(app.size)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { openUrl(context, c.gallery.pageUrl(app)) }, contentPadding = PaddingValues(end = 16.dp)) {
                        Text(stringResource(R.string.gallery_view_github))
                    }
                    TextButton(onClick = { openUrl(context, c.gallery.reportUrl(app)) }) { Text("🚩 " + stringResource(R.string.gallery_report)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    clash?.let { existing ->
        app?.let { a ->
            NameClashDialog(
                name = existing.name,
                copyName = c.apps.uniqueName(a.name),
                onCopy = { clash = null; installNew(a, openImprove = false) },
                onReplace = { clash = null; replace(a, existing) },
                onDismiss = { clash = null },
            )
        }
    }
}

private val TAG_LABELS = mapOf(
    "ai" to ("🧠" to R.string.gallery_tag_ai),
    "phone" to ("🧬" to R.string.gallery_tag_phone),
    "camera" to ("📷" to R.string.gallery_tag_camera),
    "microphone" to ("🎙️" to R.string.gallery_tag_microphone),
    "internet" to ("🌐" to R.string.gallery_tag_internet),
)

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
