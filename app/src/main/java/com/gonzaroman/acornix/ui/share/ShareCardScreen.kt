package com.gonzaroman.acornix.ui.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.web.AcornixWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val CARD_WIDTH = 1080
private const val CARD_HEIGHT = 1350
private const val APP_VIEWPORT_DP = 360f

/** What the card shows of the request: the original idea, on one line, without extra spaces. */
fun shareablePrompt(app: AppMeta): String =
    if (app.kind == AppKind.SAMPLE) "" else app.description.replace(Regex("\\s+"), " ").trim()

/** Changes asked after the first version; samples get rebuilt by updates, so they don't count. */
fun changesCount(app: AppMeta): Int = if (app.kind == AppKind.SAMPLE) 0 else (app.versionCount - 1).coerceAtLeast(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardScreen(nav: NavHostController, appId: String) {
    val context = LocalContext.current
    val app = remember(appId) { context.container.apps.get(appId) }
    if (app == null) {
        nav.popBackStack()
        return
    }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val original = remember(app.id) { shareablePrompt(app) }
    var showPrompt by rememberSaveable { mutableStateOf(original.isNotEmpty()) }
    var showApp by rememberSaveable { mutableStateOf(true) }
    var prompt by rememberSaveable { mutableStateOf(original) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    var busy by remember { mutableStateOf(false) }
    var shared by rememberSaveable { mutableStateOf(false) }
    var askStar by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var capturing by remember { mutableStateOf(false) }

    // Back from the share sheet: the right moment to ask for a GitHub star, once in a lifetime.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && shared && SupportPrefs(context).shouldAskForStar()) askStar = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_card_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShareCard(
                app = app,
                prompt = prompt.trim().takeIf { showPrompt && it.isNotEmpty() },
                showApp = showApp,
                rounded = !capturing,
                modifier = Modifier.onGloballyPositioned { cardBounds = it.boundsInWindow() },
            )
            Text(
                stringResource(R.string.share_card_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (original.isNotEmpty()) {
                ToggleRow(stringResource(R.string.share_show_prompt), showPrompt) { showPrompt = it }
                if (showPrompt) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.replace('\n', ' ') },
                        label = { Text(stringResource(R.string.share_prompt_label)) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ToggleRow(stringResource(R.string.share_show_app), showApp) { showApp = it }
            Button(
                onClick = {
                    focus.clearFocus()
                    busy = true
                    scope.launch {
                        // The card must be fully on screen to be copied: close the keyboard and scroll up first.
                        scroll.animateScrollTo(0)
                        capturing = true // square corners in the image: each network rounds them its own way
                        delay(350)
                        val file = captureCard(context, cardBounds, app.id)
                        capturing = false
                        busy = false
                        if (file == null) {
                            Toast.makeText(context, R.string.share_card_failed, Toast.LENGTH_SHORT).show()
                        } else {
                            shared = true
                            shareCard(context, app, file)
                        }
                    }
                },
                enabled = !busy && cardBounds.width > 0f,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_card_button))
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (askStar) StarDialog(onDismiss = { askStar = false })
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 0.dp),
    )
}

/**
 * The 4:5 card that gets shared. What the person made is the star: the app runs big inside a
 * phone frame, next to its name and how long it took. Acornix only signs it at the bottom. It is
 * drawn on screen and captured as is, so what you see is what you send.
 */
@Composable
fun ShareCard(app: AppMeta, prompt: String?, showApp: Boolean, modifier: Modifier = Modifier, rounded: Boolean = true) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_WIDTH / CARD_HEIGHT.toFloat())
            .clip(RoundedCornerShape(if (rounded) 18.dp else 0.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1B1464),
                    0.55f to Color(0xFF6A1B9A),
                    1f to Color(0xFFFF6E7F),
                ),
            ),
    ) {
        val w = maxWidth
        val density = LocalDensity.current
        fun sp(fraction: Float): TextUnit = with(density) { (w * fraction).toSp() }
        // Soft glows, like the promo video and AcornixOS.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0x66FFB86C), Color.Transparent),
                    center = with(density) { Offset((w * 0.9f).toPx(), (w * 0.1f).toPx()) },
                    radius = with(density) { (w * 0.55f).toPx() },
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0x554FC3F7), Color.Transparent),
                    center = with(density) { Offset((w * 0.05f).toPx(), (w * 1.15f).toPx()) },
                    radius = with(density) { (w * 0.6f).toPx() },
                ),
            ),
        )
        val pad = w * 0.05f
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (showApp) {
                    PhoneFrame(app, Modifier.fillMaxHeight())
                } else {
                    Box(
                        Modifier.fillMaxHeight().aspectRatio(0.47f).clip(RoundedCornerShape(w * 0.06f))
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(app.emoji, fontSize = sp(0.2f)) }
                }
                Spacer(Modifier.width(w * 0.045f))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Spacer(Modifier.height(w * 0.02f))
                    if (showApp) {
                        Text(app.emoji, fontSize = sp(0.08f))
                        Spacer(Modifier.height(w * 0.01f))
                    }
                    AutoSizeText(
                        app.name,
                        sizes = listOf(sp(0.07f), sp(0.06f), sp(0.052f), sp(0.045f)),
                        maxLines = 3,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(w * 0.04f))
                    if (app.buildSeconds > 0) {
                        AutoSizeText(
                            "⚡ " + stringResource(R.string.share_card_seconds, app.buildSeconds),
                            sizes = listOf(sp(0.1f), sp(0.085f), sp(0.072f), sp(0.062f)),
                            maxLines = 1,
                            color = Color(0xFFFFE082),
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            stringResource(R.string.share_card_seconds_label),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = sp(0.04f),
                            lineHeight = sp(0.05f),
                        )
                    } else {
                        Text(
                            stringResource(R.string.share_card_built),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = sp(0.045f),
                            lineHeight = sp(0.056f),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (prompt != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(w * 0.03f))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = w * 0.03f, vertical = w * 0.025f),
                        ) {
                            Text(
                                stringResource(R.string.share_card_asked),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = sp(0.03f),
                            )
                            Spacer(Modifier.height(w * 0.008f))
                            AutoSizeQuote(prompt, listOf(sp(0.04f), sp(0.035f), sp(0.031f)), maxLines = 6)
                            val changes = changesCount(app)
                            if (changes > 0) {
                                Spacer(Modifier.height(w * 0.01f))
                                Text(
                                    pluralStringResource(R.plurals.share_card_changes, changes, changes),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = sp(0.029f),
                                    lineHeight = sp(0.036f),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(w * 0.03f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🌰 " + stringResource(R.string.share_card_brand),
                    color = Color.White,
                    fontSize = sp(0.033f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "github.com/gonzaroman/acornix",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = sp(0.031f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Text that takes the biggest of [sizes] that fits in [maxLines] without splitting a word.
 */
@Composable
private fun AutoSizeText(text: String, sizes: List<TextUnit>, maxLines: Int, color: Color, fontWeight: FontWeight) {
    var step by remember(text, sizes) { mutableIntStateOf(0) }
    Text(
        text,
        color = color,
        fontSize = sizes[step],
        lineHeight = sizes[step] * 1.12f,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            // A line that ends inside a word means the word itself didn't fit at this size.
            val splitsWord = (0 until layout.lineCount - 1).any { line ->
                val end = layout.getLineEnd(line)
                end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
            }
            if ((layout.hasVisualOverflow || splitsWord) && step < sizes.lastIndex) step++
        },
    )
}

/**
 * The request in quotes: tries each size in turn, and only at the smallest one cuts it, at the
 * end of a word, closing with "…»".
 */
@Composable
private fun AutoSizeQuote(prompt: String, sizes: List<TextUnit>, maxLines: Int = 3) {
    var step by remember(prompt, sizes) { mutableIntStateOf(0) }
    var shown by remember(prompt, sizes) { mutableStateOf("«$prompt»") }
    Text(
        shown,
        color = Color.White,
        fontSize = sizes[step],
        lineHeight = sizes[step] * 1.22f,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (!layout.hasVisualOverflow) return@Text
            if (step < sizes.lastIndex) {
                step++
            } else {
                val fits = layout.getLineEnd(layout.lineCount.coerceAtMost(maxLines) - 1, visibleEnd = true)
                val head = shown.removeSuffix("»").take((fits - 2).coerceAtLeast(1))
                val cut = head.substringBeforeLast(' ', head).trimEnd(' ', ',', ';', ':', '.', '-')
                val next = "$cut…»"
                if (next != shown && cut.length > 1) shown = next
            }
        },
    )
}

/**
 * The app itself, running in a dark phone frame. The WebView keeps a normal phone width
 * ([APP_VIEWPORT_DP]) so mobile layouts don't break, and is scaled down as an Android view so
 * touches still land in the right place.
 */
@Composable
private fun PhoneFrame(app: AppMeta, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.aspectRatio(0.47f)) {
        val frameWidth: Dp = maxWidth
        val bezel: Dp = frameWidth * 0.045f
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(frameWidth * 0.16f)).background(Color(0xFF111116)).padding(bezel),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(frameWidth * 0.12f))) {
                val density = LocalDensity.current
                val screenW = with(density) { maxWidth.roundToPx() }
                val screenH = with(density) { maxHeight.roundToPx() }
                val viewport = with(density) { APP_VIEWPORT_DP.dp.roundToPx() }
                val scale = screenW / viewport.toFloat()
                val scope = rememberCoroutineScope()
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).also { frame ->
                            val webView = WebView(ctx)
                            frame.addView(webView, FrameLayout.LayoutParams(viewport, (screenH / scale).toInt()))
                            AcornixWebView.configure(webView, ctx, scope, app.id)
                            webView.loadUrl(AcornixWebView.appUrl(app.id) + "?v=${app.updatedAt}")
                        }
                    },
                    update = { frame ->
                        val webView = frame.getChildAt(0) as WebView
                        val height = (screenH / scale).toInt()
                        if (webView.layoutParams.width != viewport || webView.layoutParams.height != height) {
                            webView.layoutParams = FrameLayout.LayoutParams(viewport, height)
                        }
                        webView.pivotX = 0f
                        webView.pivotY = 0f
                        webView.scaleX = scale
                        webView.scaleY = scale
                    },
                    onRelease = { frame -> (frame.getChildAt(0) as? WebView)?.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Copies the card from the screen (WebGL and canvas included) and saves it as a 1080×1350 JPEG. */
private suspend fun captureCard(context: Context, bounds: Rect, appId: String): File? {
    val activity = context.findActivity() ?: return null
    if (bounds.width < 2f || bounds.height < 2f) return null
    val shot = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
    val rect = android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
    val ok = suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(activity.window, rect, shot, { result -> cont.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            cont.resume(false)
        }
    }
    if (!ok) return null
    return withContext(Dispatchers.IO) {
        val card = Bitmap.createScaledBitmap(shot, CARD_WIDTH, CARD_HEIGHT, true)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "acornix-${appId}-${System.currentTimeMillis()}.jpg")
        runCatching { file.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 92, it) } }.getOrNull() ?: return@withContext null
        file
    }
}

private fun shareCard(context: Context, app: AppMeta, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val text = if (app.buildSeconds > 0) context.getString(R.string.share_card_text_seconds, app.name, app.buildSeconds)
    else context.getString(R.string.share_card_text, app.name)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(app.name, uri)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_card_title)))
}
