package com.gonzaroman.acornix.ui.onboarding

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.ui.components.ControlState
import com.gonzaroman.acornix.ui.components.rememberControlState
import kotlinx.coroutines.launch

/** What a page's button does. */
private enum class TourAction { NONE, CREATE, IDEAS, APPS, FEATURE, ASSISTANT, CONTROL, OS }

private data class TourPage(
    val emoji: String,
    val colors: List<Color>,
    @StringRes val title: Int,
    @StringRes val text: Int,
    @ArrayRes val examples: Int? = null,
    @StringRes val hint: Int? = null,
    @StringRes val button: Int? = null,
    val action: TourAction = TourAction.NONE,
)

private val PAGES = listOf(
    TourPage("🌰", listOf(Color(0xFF6A3F1E), Color(0xFFD08A3C)), R.string.tour_welcome_title, R.string.tour_welcome_text),
    TourPage("✨", listOf(Color(0xFF3A2B8C), Color(0xFFB04BD6)), R.string.tour_create_title, R.string.tour_create_text,
        examples = R.array.tour_create_examples, button = R.string.tour_create_button, action = TourAction.CREATE),
    TourPage("💡", listOf(Color(0xFFB8860B), Color(0xFFF2C94C)), R.string.tour_ideas_title, R.string.tour_ideas_text,
        button = R.string.tour_ideas_button, action = TourAction.IDEAS),
    TourPage("📱", listOf(Color(0xFF1E6B4E), Color(0xFF55C59B)), R.string.tour_apps_title, R.string.tour_apps_text,
        button = R.string.tour_apps_button, action = TourAction.APPS),
    TourPage("🧬", listOf(Color(0xFF0E5E86), Color(0xFF4FC3F7)), R.string.tour_feature_title, R.string.tour_feature_text,
        examples = R.array.tour_feature_examples, button = R.string.tour_feature_button, action = TourAction.FEATURE),
    TourPage("🧠", listOf(Color(0xFF00695C), Color(0xFF26C6DA)), R.string.tour_ai_title, R.string.tour_ai_text,
        examples = R.array.tour_ai_examples, button = R.string.tour_ai_button, action = TourAction.CREATE),
    TourPage("🤖", listOf(Color(0xFF8C2F5C), Color(0xFFF06292)), R.string.tour_assistant_title, R.string.tour_assistant_text,
        examples = R.array.tour_assistant_examples, button = R.string.tour_assistant_button, action = TourAction.ASSISTANT),
    TourPage("🖐️", listOf(Color(0xFF263238), Color(0xFF607D8B)), R.string.tour_control_title, R.string.tour_control_text,
        examples = R.array.tour_control_examples, hint = R.string.tour_control_hint, button = R.string.tour_control_button,
        action = TourAction.CONTROL),
    TourPage("🖥️", listOf(Color(0xFF1B2A6B), Color(0xFF6A7BE0)), R.string.tour_os_title, R.string.tour_os_text,
        button = R.string.tour_os_button, action = TourAction.OS),
    TourPage("🚀", listOf(Color(0xFF6A1B9A), Color(0xFFFF7043)), R.string.tour_end_title, R.string.tour_end_text),
)

private val COMMUNITY_PAGES = listOf(
    TourPage("🌍", listOf(Color(0xFF0D47A1), Color(0xFF26A69A)), R.string.community_tour_welcome_title, R.string.community_tour_welcome_text),
    TourPage("🔍", listOf(Color(0xFF4527A0), Color(0xFF7E57C2)), R.string.community_tour_find_title, R.string.community_tour_find_text,
        hint = R.string.community_tour_find_hint),
    TourPage("⬇️", listOf(Color(0xFF1E6B4E), Color(0xFF55C59B)), R.string.community_tour_get_title, R.string.community_tour_get_text,
        hint = R.string.community_tour_get_hint),
    TourPage("📤", listOf(Color(0xFFB8860B), Color(0xFFF2C94C)), R.string.community_tour_share_title, R.string.community_tour_share_text,
        hint = R.string.community_tour_share_hint, button = R.string.community_tour_share_button, action = TourAction.APPS),
    TourPage("🛡️", listOf(Color(0xFF263238), Color(0xFF607D8B)), R.string.community_tour_safe_title, R.string.community_tour_safe_text),
)

/**
 * "What Acornix can do": a short swipeable tour shown once after onboarding (and once after updating),
 * and any time from Settings. Each page has a button that goes straight to trying it.
 */
@Composable
fun TourScreen(
    onClose: () -> Unit,
    onCreate: (feature: Boolean) -> Unit,
    onApps: () -> Unit,
    onAssistant: (prompt: String) -> Unit,
    onControlHelp: () -> Unit,
    onOs: () -> Unit,
) {
    val context = LocalContext.current
    val assistantExamples = stringArrayResource(R.array.tour_assistant_examples)

    // Seen once it opens, so leaving with "Try it" or Back doesn't bring it back.
    LaunchedEffect(Unit) { context.container.settings.setTourSeen() }

    Tour(PAGES, R.string.tour_start, onClose) { action ->
        when (action) {
            TourAction.CREATE, TourAction.IDEAS -> onCreate(false)
            TourAction.FEATURE -> onCreate(true)
            TourAction.APPS -> onApps()
            TourAction.ASSISTANT -> onAssistant(assistantExamples.first())
            TourAction.CONTROL -> onControlHelp()
            TourAction.OS -> onOs()
            TourAction.NONE -> Unit
        }
    }
}

/**
 * How the community gallery works: finding, trying and installing apps, and publishing your own.
 * Shown the first time Explore opens, and from its ⓘ button.
 */
@Composable
fun CommunityTourScreen(onClose: () -> Unit, onApps: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { context.container.settings.setCommunityTourSeen() }
    Tour(COMMUNITY_PAGES, R.string.community_tour_start, onClose) { action -> if (action == TourAction.APPS) onApps() }
}

/** The swipeable pages, with a counter, Skip, dots, Back / Next and [finish] on the last page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tour(pages: List<TourPage>, @StringRes finish: Int, onClose: () -> Unit, onAction: (TourAction) -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val controlOn = rememberControlState().value == ControlState.ON
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tour_counter, pager.currentPage + 1, pages.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            if (!last) TextButton(onClick = onClose) { Text(stringResource(R.string.tour_skip)) }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(Brush.linearGradient(page.colors)),
                    contentAlignment = Alignment.Center,
                ) { Text(page.emoji, fontSize = 64.sp) }
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                page.examples?.let { res ->
                    Spacer(Modifier.height(20.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stringArrayResource(res).forEach { Bubble(it) }
                    }
                }
                page.hint?.let {
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                page.button?.let { label ->
                    Spacer(Modifier.height(24.dp))
                    if (page.action == TourAction.CONTROL && controlOn) {
                        Text(stringResource(R.string.tour_control_on), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        FilledTonalButton(onClick = { onAction(page.action) }) { Text(stringResource(label)) }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(pages.size) { i ->
                    val color by animateColorAsState(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "dot",
                    )
                    val width by animateDpAsState(if (pager.currentPage == i) 20.dp else 7.dp, label = "dotWidth")
                    Box(Modifier.size(width = width, height = 7.dp).clip(CircleShape).background(color))
                }
            }
            if (pager.currentPage > 0 && !last) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(stringResource(R.string.tour_back))
                }
            }
            if (last) {
                Button(onClick = onClose) { Text(stringResource(finish)) }
            } else {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(R.string.tour_next))
                }
            }
        }
    }
}

/** An example request, drawn like a chat bubble. */
@Composable
private fun Bubble(text: String) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            "“$text”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
