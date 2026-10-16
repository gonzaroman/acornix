package com.gonzaroman.acornix.ui.os

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.ui.components.appGradient
import kotlin.math.roundToInt

private val MENUBAR_HEIGHT = 30.dp
private val DOCK_AREA = 88.dp
private val HEADER_HEIGHT = 38.dp
private val WINDOW_SHAPE = RoundedCornerShape(12.dp)

/** Everything the desktop shows; the activity owns the state and the WebViews. */
class DesktopState(
    val apps: List<AppMeta>,
    val windows: List<OsWindow>,
    val dock: List<String>,
    val wallpaper: Int,
    val launchpadOpen: Boolean,
    val clock: String,
    val battery: String,
    val toast: String?,
)

interface DesktopActions {
    fun openApp(app: AppMeta)
    fun focus(window: OsWindow)
    fun close(window: OsWindow)
    fun minimize(window: OsWindow)
    fun launchpad(open: Boolean)
    fun addToDock(key: String, label: String)
    fun removeFromDock(key: String, label: String)
    fun nextWallpaper()
    fun pinToHome()
    fun exit()
    fun toastShown()
    fun areaSize(width: Float, height: Float)
    fun webViewFor(window: OsWindow): WebView
}

@Composable
fun DesktopScreen(state: DesktopState, actions: DesktopActions) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (state.launchpadOpen) Modifier.blur(24.dp) else Modifier),
        ) {
            Wallpaper(state.wallpaper)
            Column(Modifier.fillMaxSize()) {
                MenuBar(state, actions)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    val density = LocalDensity.current
                    val areaW = constraints.maxWidth.toFloat()
                    val areaH = constraints.maxHeight.toFloat() - with(density) { DOCK_AREA.toPx() }
                    SideEffect { actions.areaSize(areaW, areaH) }

                    if (state.windows.none { !it.minimized }) {
                        Text(
                            stringResource(if (state.apps.isEmpty()) R.string.os_empty else R.string.os_desktop_hint),
                            color = Color.White.copy(alpha = 0.75f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp)
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(12.dp),
                        )
                    }
                    state.windows.forEach { win ->
                        if (!win.minimized) {
                            androidx.compose.runtime.key(win.id) {
                                AppWindow(win, state.apps.firstOrNull { it.id == win.appId }, areaW, areaH, actions)
                            }
                        }
                    }
                    Dock(state, actions, Modifier.align(Alignment.BottomCenter).zIndex(Float.MAX_VALUE))
                }
            }
        }
        AnimatedVisibility(state.launchpadOpen, enter = fadeIn() + scaleIn(initialScale = 1.08f), exit = fadeOut()) {
            Launchpad(state, actions)
        }
        Toast(state.toast, actions, Modifier.align(Alignment.BottomCenter))
    }
}

// --- Wallpaper -------------------------------------------------------------------------------

private class WallpaperStyle(val base: List<Color>, val blobs: List<Pair<Color, Offset>>)

private val WALLPAPERS = listOf(
    WallpaperStyle(
        listOf(Color(0xFF1B1464), Color(0xFF6A1B9A), Color(0xFFFF6E7F)),
        listOf(Color(0xFFFFB86C) to Offset(0.85f, 0.15f), Color(0xFF4FC3F7) to Offset(0.1f, 0.85f)),
    ),
    WallpaperStyle(
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
        listOf(Color(0xFF00E5FF) to Offset(0.2f, 0.2f), Color(0xFF64FFDA) to Offset(0.9f, 0.8f)),
    ),
    WallpaperStyle(
        listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88), Color(0xFFFF99AC)),
        listOf(Color(0xFFFFF59D) to Offset(0.8f, 0.2f), Color(0xFF7C4DFF) to Offset(0.15f, 0.9f)),
    ),
    WallpaperStyle(
        listOf(Color(0xFF134E5E), Color(0xFF2E7D4F), Color(0xFF71B280)),
        listOf(Color(0xFFDCE775) to Offset(0.75f, 0.25f), Color(0xFF26C6DA) to Offset(0.2f, 0.75f)),
    ),
    WallpaperStyle(
        listOf(Color(0xFF000000), Color(0xFF1A1A2E), Color(0xFF16213E)),
        listOf(Color(0xFFE94560) to Offset(0.85f, 0.1f), Color(0xFF0F3460) to Offset(0.2f, 0.9f)),
    ),
)

val WALLPAPER_COUNT = WALLPAPERS.size

@Composable
private fun Wallpaper(index: Int) {
    val style = WALLPAPERS[index.mod(WALLPAPERS.size)]
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(style.base))
            .drawBehind {
                style.blobs.forEach { (color, at) ->
                    val center = Offset(size.width * at.x, size.height * at.y)
                    drawCircle(
                        Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center, size.maxDimension * 0.55f),
                        radius = size.maxDimension * 0.55f,
                        center = center,
                    )
                }
            },
    )
}

// --- Menu bar --------------------------------------------------------------------------------

@Composable
private fun MenuBar(state: DesktopState, actions: DesktopActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .height(MENUBAR_HEIGHT)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Text(
                "🍏 acornixOS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { menu = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("🚀  " + stringResource(R.string.os_launchpad)) }, onClick = { menu = false; actions.launchpad(true) })
                DropdownMenuItem(text = { Text("🖼️  " + stringResource(R.string.os_wallpaper)) }, onClick = { menu = false; actions.nextWallpaper() })
                DropdownMenuItem(text = { Text("📌  " + stringResource(R.string.os_pin)) }, onClick = { menu = false; actions.pinToHome() })
                DropdownMenuItem(text = { Text("✖️  " + stringResource(R.string.os_close)) }, onClick = { menu = false; actions.exit() })
            }
        }
        Spacer(Modifier.weight(1f))
        Text("🖼️", fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable { actions.nextWallpaper() }.padding(6.dp))
        Spacer(Modifier.width(8.dp))
        Text(state.battery, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(state.clock, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// --- Windows ---------------------------------------------------------------------------------

@Composable
private fun AppWindow(win: OsWindow, app: AppMeta?, areaW: Float, areaH: Float, actions: DesktopActions) {
    val density = LocalDensity.current
    val headerPx = with(density) { HEADER_HEIGHT.toPx() }
    val minW = with(density) { 220.dp.toPx() }
    val minH = with(density) { 180.dp.toPx() }
    val x = if (win.maximized) 0f else win.x
    val y = if (win.maximized) 0f else win.y
    val w = if (win.maximized) areaW else win.width
    val h = if (win.maximized) areaH else win.height

    Box(
        Modifier
            .zIndex(win.z)
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .shadow(24.dp, WINDOW_SHAPE)
            .clip(WINDOW_SHAPE)
            .background(Color.White)
            // Any touch inside a window brings it to the front, without stealing the touch from the app.
            .pointerInput(win.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    actions.focus(win)
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .background(Color(0xFFE9E9EC))
                    .pointerInput(win.id) {
                        detectTapGestures(onDoubleTap = { win.maximized = !win.maximized })
                    }
                    .pointerInput(win.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            if (win.maximized) return@detectDragGestures
                            win.x = (win.x + drag.x).coerceIn(-win.width + 120f, areaW - 120f)
                            win.y = (win.y + drag.y).coerceIn(0f, areaH - headerPx)
                        }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrafficLight(Color(0xFFFF5F56), stringResource(R.string.os_close)) { actions.close(win) }
                Spacer(Modifier.width(8.dp))
                TrafficLight(Color(0xFFFFBD2E), stringResource(R.string.os_minimize)) { actions.minimize(win) }
                Spacer(Modifier.width(8.dp))
                TrafficLight(Color(0xFF27C93F), stringResource(R.string.os_maximize)) { win.maximized = !win.maximized }
                Text(
                    listOfNotNull(app?.emoji, app?.name ?: win.appId).joinToString(" "),
                    color = Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(end = 52.dp),
                )
            }
            AndroidView(
                factory = { actions.webViewFor(win).also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        if (!win.maximized) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .pointerInput(win.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            win.width = (win.width + drag.x).coerceIn(minW, areaW - win.x.coerceAtLeast(0f))
                            win.height = (win.height + drag.y).coerceIn(minH, areaH - win.y)
                        }
                    }
                    .drawBehind {
                        val c = Color(0x99666666)
                        val s = 2.dp.toPx()
                        drawLine(c, Offset(size.width * 0.75f, size.height * 0.35f), Offset(size.width * 0.35f, size.height * 0.75f), s)
                        drawLine(c, Offset(size.width * 0.75f, size.height * 0.58f), Offset(size.width * 0.58f, size.height * 0.75f), s)
                    },
            )
        }
    }
}

@Composable
private fun TrafficLight(color: Color, label: String, onClick: () -> Unit) {
    // A 14dp dot with a bigger invisible touch area.
    Box(
        Modifier.size(26.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
    }
}

// --- Dock ------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(state: DesktopState, actions: DesktopActions, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val openIds = state.windows.map { it.appId }.toSet()
    val pinnedApps = state.dock.mapNotNull { key -> key.removePrefix("app:").takeIf { key.startsWith("app:") } }.toSet()
    val runningOnly = state.windows.map { it.appId }.distinct().filter { it !in pinnedApps }

    Row(
        modifier
            .padding(bottom = 12.dp)
            .widthIn(max = 900.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockIcon(
            emoji = "🚀",
            background = Brush.linearGradient(listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB))),
            running = false,
            onClick = { actions.launchpad(true) },
            onLongClick = null,
        )
        state.dock.forEach { key ->
            val app = state.apps.firstOrNull { appKey(it.id) == key } ?: return@forEach
            DockIcon(
                emoji = app.emoji,
                background = appGradient(app.id),
                running = app.id in openIds,
                onClick = { actions.openApp(app) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.removeFromDock(key, app.name)
                },
            )
        }
        if (runningOnly.isNotEmpty()) {
            Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.35f)))
            runningOnly.forEach { id ->
                val app = state.apps.firstOrNull { it.id == id } ?: return@forEach
                DockIcon(
                    emoji = app.emoji,
                    background = appGradient(app.id),
                    running = true,
                    onClick = { actions.openApp(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.addToDock(appKey(app.id), app.name)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIcon(emoji: String, background: Brush, running: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (running) Color.White else Color.Transparent))
    }
}

// --- Launchpad -------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Launchpad(state: DesktopState, actions: DesktopActions) {
    var query by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val items = remember(state.apps, query) {
        val q = query.trim().lowercase()
        state.apps.sortedBy { it.name.lowercase() }.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = null, indication = null) { actions.launchpad(false) }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("🔎  " + stringResource(R.string.os_search), color = Color.White.copy(alpha = 0.6f)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
            ),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.85f),
        )
        Text(
            stringResource(R.string.os_hint),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        ) {
            items(items, key = { it.id }) { app ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                actions.launchpad(false)
                                actions.openApp(app)
                            },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                actions.addToDock(appKey(app.id), app.name)
                            },
                        )
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(appGradient(app.id)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(app.emoji, fontSize = 30.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(app.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// --- Toast -----------------------------------------------------------------------------------

@Composable
private fun Toast(message: String?, actions: DesktopActions, modifier: Modifier) {
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            actions.toastShown()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = modifier.padding(bottom = DOCK_AREA + 16.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            message.orEmpty(),
            color = Color(0xFF222222),
            fontSize = 13.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
