package com.gonzaroman.acornix.ui.create

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.ui.share.StarDialog
import com.gonzaroman.acornix.ui.share.SupportPrefs
import kotlinx.coroutines.delay
import com.gonzaroman.acornix.ui.Routes
import androidx.compose.material.icons.rounded.Share
import com.gonzaroman.acornix.device.ShortcutHelper
import com.gonzaroman.acornix.ui.components.AppIcon
import com.gonzaroman.acornix.ui.errorMessage
import com.gonzaroman.acornix.ui.components.AttachmentRow
import com.gonzaroman.acornix.ui.components.PromptBar
import com.gonzaroman.acornix.ui.components.formatBytes
import com.gonzaroman.acornix.ui.components.rememberAttachments
import com.gonzaroman.acornix.ui.components.rememberVoiceInput
import com.gonzaroman.acornix.web.AcornixWebView
import com.gonzaroman.acornix.web.AppLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(nav: NavHostController, feature: Boolean, improveAppId: String?) {
    val context = LocalContext.current
    val vm: CreateViewModel = viewModel { CreateViewModel(context.container, feature, improveAppId) }
    val ui by vm.ui.collectAsState()

    val title = when {
        improveAppId != null -> stringResource(R.string.improve_title, vm.improveTarget?.name.orEmpty())
        feature -> stringResource(R.string.create_feature_title)
        else -> stringResource(R.string.create_app_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = ui,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it::class },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "create",
        ) { state ->
            when (state) {
                is CreateUi.Form -> FormContent(vm, feature)
                is CreateUi.Generating -> GeneratingContent(state, onCancel = vm::cancel)
                is CreateUi.Done -> DoneContent(state, vm, onShare = { nav.navigate(Routes.share(it.id)) })
                is CreateUi.Failed -> FailedContent(state, onRetry = vm::retry, onEdit = vm::backToForm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(vm: CreateViewModel, feature: Boolean) {
    val attachments = rememberAttachments()
    val target = vm.improveTarget
    var idea by rememberSaveable { mutableStateOf(vm.lastIdea) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val voice = rememberVoiceInput { idea = if (idea.isBlank()) it else "$idea $it" }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // The AI idea last picked: its emoji becomes the app's icon, as long as its text is still there.
    var picked by remember { mutableStateOf<com.gonzaroman.acornix.core.Idea?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (target != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(target, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(target.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.improve_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text(
                stringResource(if (feature) R.string.create_feature_intro else R.string.create_app_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = idea,
            onValueChange = { idea = it },
            label = { Text(stringResource(if (target != null) R.string.field_change else R.string.field_idea)) },
            placeholder = { Text(stringResource(if (target != null) R.string.field_change_hint else if (feature) R.string.field_feature_hint else R.string.field_idea_hint)) },
            minLines = 4,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = voice) { Icon(Icons.Rounded.Mic, stringResource(R.string.dictate)) } },
        )

        AttachmentRow(attachments)
        OutlinedButton(onClick = attachments::pick) {
            Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.attach_reference))
        }

        // Right under the request, so nobody has to scroll past the ideas to find it.
        Button(
            onClick = { vm.generate(idea, name, category, attachments.images(), picked?.takeIf { it.prompt == idea }?.emoji) },
            enabled = idea.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (target != null) R.string.apply_change else R.string.create_button), style = MaterialTheme.typography.titleMedium)
        }

        if (target == null && !feature) {
            Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.categories).forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                }
            }
        }

        if (target != null) {
            Text(stringResource(R.string.inspiration), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.improve_examples).forEach { ex -> AssistChip(onClick = { idea = ex }, label = { Text(ex) }) }
            }
        } else {
            IdeasSection(feature, category) { chosen ->
                idea = chosen.prompt
                picked = chosen.takeIf { it.title.isNotBlank() }
                if (name.isBlank() && chosen.title.isNotBlank() && chosen.title.length <= 32) name = chosen.title
                scope.launch { scroll.animateScrollTo(0) }
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun GeneratingContent(state: CreateUi.Generating, onCancel: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    val scroll = rememberScrollState()
    LaunchedEffect(state.code.length) { scroll.scrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (state.codeFinished) "✨" else "🌰", fontSize = 64.sp, modifier = Modifier.scale(scale))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                when {
                    state.chars == 0 -> R.string.gen_thinking
                    state.codeFinished -> R.string.gen_finishing
                    else -> R.string.gen_writing
                },
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.gen_size, formatBytes(state.chars.toLong()), state.code.lines().size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small))
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Color(0xFF1B1D23))
                .verticalScroll(scroll)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                state.code.takeLast(6000).ifEmpty { "…" },
                color = Color(0xFFB5E8C4),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun DoneContent(state: CreateUi.Done, vm: CreateViewModel, onShare: (AppMeta) -> Unit) {
    val context = LocalContext.current
    val attachments = rememberAttachments()
    var change by rememberSaveable { mutableStateOf("") }
    var askStar by remember { mutableStateOf(false) }

    // Third app of their own, freshly created: they are enjoying it, so ask for a star (once ever).
    LaunchedEffect(state.app.id) {
        val own = context.container.apps.apps.value.count { it.kind != AppKind.SAMPLE }
        if (state.app.versionCount == 1 && own >= 3 && SupportPrefs(context).shouldAskForStar()) {
            delay(1500)
            askStar = true
        }
    }
    if (askStar) StarDialog(onDismiss = { askStar = false })

    // The preview takes all the free space; the actions stay visible below it.
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(state.app, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.app.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(stringResource(R.string.gen_done_version, state.app.versionCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Labelled buttons on their own row, so the name keeps its space and nobody has to guess.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val compact = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                FilledTonalButton(
                    onClick = {
                        if (!ShortcutHelper.pin(context, state.app, context.container.apps)) {
                            Toast.makeText(context, R.string.pin_unsupported, Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = compact,
                    modifier = Modifier.weight(1.2f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pin_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = { onShare(state.app) }, contentPadding = compact, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_card_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = { AppLauncher.open(context, state.app.id) }, contentPadding = compact, modifier = Modifier.weight(0.85f)) {
                    Icon(Icons.Rounded.OpenInFull, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.truncated) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(R.string.gen_truncated), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            key(state.app.updatedAt) { AppPreview(state.app, Modifier.weight(1f)) }
            state.suggestion?.let { suggestion ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "💡 $suggestion",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { vm.improve(suggestion, emptyList()) }) { Text(stringResource(R.string.apply_suggestion_short)) }
                    }
                }
            }
        }
        PromptBar(
            value = change,
            onValueChange = { change = it },
            placeholder = stringResource(R.string.improve_placeholder),
            onSend = {
                vm.improve(change, attachments.images())
                change = ""
                attachments.clear()
            },
            attachments = attachments,
            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
        )
    }
}

@Composable
private fun FailedContent(state: CreateUi.Failed, onRetry: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🥀", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.gen_failed), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        val context = LocalContext.current
        Text(state.error?.let { errorMessage(context, it) } ?: state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit_request)) }
            Button(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/** Live, interactive preview of a generated app inside a card. */
@Composable
fun AppPreview(app: AppMeta, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    // AndroidView would leave WRAP_CONTENT, which makes Chromium size the page from its
                    // content: height:100% / 100vh collapse to 0 and only a thin strip gets painted.
                    wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    AcornixWebView.configure(wv, ctx, scope, app.id)
                    wv.loadUrl(AcornixWebView.appUrl(app.id) + "?v=${app.updatedAt}")
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
