package com.gonzaroman.acornix.ui.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.agent.ChatItem
import com.gonzaroman.acornix.agent.StepStatus
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.ui.ExternalRequest
import com.gonzaroman.acornix.ui.components.ControlState
import com.gonzaroman.acornix.ui.components.closesBackgroundApps
import com.gonzaroman.acornix.ui.components.manufacturerName
import com.gonzaroman.acornix.ui.components.openAccessibilitySettings
import com.gonzaroman.acornix.ui.components.openAppSettings
import com.gonzaroman.acornix.ui.components.rememberControlState
import com.gonzaroman.acornix.ui.components.AppIcon
import com.gonzaroman.acornix.ui.components.ImageUtils
import com.gonzaroman.acornix.ui.components.PromptBar
import com.gonzaroman.acornix.ui.components.formatBytes
import com.gonzaroman.acornix.ui.components.rememberAttachments
import com.gonzaroman.acornix.web.AppLauncher

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    controlMode: Boolean,
    external: ExternalRequest?,
    onExternalConsumed: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val assistant = context.container.assistant
    val state by assistant.state.collectAsState()
    val attachments = rememberAttachments()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val controlState by rememberControlState()
    val controlEnabled = controlState == ControlState.ON

    // Text or images shared from other apps land in the prompt bar.
    LaunchedEffect(external) {
        external ?: return@LaunchedEffect
        external.sharedText?.let { input = it }
        external.sharedImage?.let { uri -> ImageUtils.load(context, uri)?.let { attachments.items += it } }
        onExternalConsumed()
    }
    LaunchedEffect(state.items.size, state.items.lastOrNull()) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.size)
    }

    fun send(text: String = input) {
        assistant.send(text, attachments.images())
        input = ""
        attachments.clear()
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_assistant), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(
                        when (controlState) {
                            ControlState.ON -> R.string.control_on_short
                            ControlState.STALLED -> R.string.control_stalled_title
                            ControlState.OFF -> R.string.control_off_short
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (controlEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { assistant.newChat() }) { Icon(Icons.Rounded.AddComment, stringResource(R.string.new_chat)) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (controlState == ControlState.STALLED || (!controlEnabled && (controlMode || state.items.isEmpty()))) {
                item { ControlBanner(controlState) }
            }
            if (state.items.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (controlMode) "🤖" else "✨", fontSize = 56.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (controlMode) R.string.assistant_control_title else R.string.assistant_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.assistant_empty_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            stringArrayResource(if (controlMode) R.array.control_examples else R.array.assistant_examples).forEach { ex ->
                                AssistChip(onClick = { send(ex) }, label = { Text(ex) })
                            }
                        }
                    }
                }
            }
            items(state.items, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(item)
                    is ChatItem.Assistant -> AssistantBubble(item.text)
                    is ChatItem.Step -> StepRow(item)
                    is ChatItem.AppCard -> AppCardRow(item)
                    is ChatItem.Error -> NoticeRow("⚠️ " + item.text, MaterialTheme.colorScheme.errorContainer)
                    is ChatItem.Notice -> NoticeRow(item.text, MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            }
            if (state.running && state.items.lastOrNull() !is ChatItem.Step && state.items.lastOrNull() !is ChatItem.AppCard) {
                item { ThinkingRow() }
            }
        }

        PromptBar(
            value = input,
            onValueChange = { input = it },
            placeholder = stringResource(R.string.assistant_placeholder),
            onSend = { send() },
            attachments = attachments,
            running = state.running,
            onStop = assistant::stop,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    state.pendingConfirm?.let { what ->
        AlertDialog(
            onDismissRequest = { assistant.answerConfirm(false) },
            title = { Text(stringResource(R.string.confirm_title)) },
            text = { Text(what) },
            confirmButton = { TextButton(onClick = { assistant.answerConfirm(true) }) { Text(stringResource(R.string.confirm_yes)) } },
            dismissButton = { TextButton(onClick = { assistant.answerConfirm(false) }) { Text(stringResource(R.string.confirm_no)) } },
        )
    }
}

@Composable
fun ControlBanner(controlState: ControlState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val stalled = controlState == ControlState.STALLED
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (stalled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (stalled) Icons.Rounded.Error else Icons.Rounded.TouchApp, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (stalled) R.string.control_stalled_title else R.string.control_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(if (stalled) R.string.control_stalled_text else R.string.control_banner_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (closesBackgroundApps()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.control_oem_tip, manufacturerName()), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (closesBackgroundApps()) {
                    TextButton(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.control_app_settings)) }
                }
                FilledTonalButton(onClick = { openAccessibilitySettings(context) }) {
                    Text(stringResource(if (stalled) R.string.control_open_accessibility else R.string.enable))
                }
            }
        }
    }
}

@Composable
private fun UserBubble(item: ChatItem.User) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (item.images > 0) Text("🖼️ ×${item.images}", color = MaterialTheme.colorScheme.onPrimary)
                if (item.text.isNotBlank()) Text(item.text, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("🌰", fontSize = 22.sp, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun StepRow(item: ChatItem.Step) {
    Row(
        Modifier
            .padding(start = 30.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.status) {
            StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            StepStatus.DONE -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            StepStatus.FAILED -> Icon(Icons.Rounded.Error, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stepEmoji(item.tool) + " " + stepLabel(item.tool), style = MaterialTheme.typography.labelLarge)
            val sub = if (item.status == StepStatus.FAILED) item.result ?: item.detail else item.detail
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppCardRow(item: ChatItem.AppCard) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.padding(start = 30.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val app = item.app
                if (app != null) AppIcon(app, 44.dp) else Text(item.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.app?.name ?: item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            item.error != null -> stringResource(R.string.gen_failed)
                            item.app != null -> stringResource(R.string.gen_done_version, item.app.versionCount)
                            item.chars == 0 -> stringResource(R.string.gen_thinking)
                            else -> stringResource(R.string.gen_writing) + " · " + formatBytes(item.chars.toLong())
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (item.app == null && item.error == null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small))
            }
            item.app?.let { app ->
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { AppLauncher.open(context, app.id) }) { Text(stringResource(R.string.action_open)) }
            }
        }
    }
}

@Composable
private fun NoticeRow(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Text("🌰", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.assistant_thinking), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun stepEmoji(tool: String) = when (tool) {
    "create_web_app", "improve_app" -> "🛠️"
    "list_my_apps", "open_my_app" -> "📱"
    "open_url", "web_search" -> "🌐"
    "youtube_search" -> "▶️"
    "open_maps" -> "🗺️"
    "open_app", "list_installed_apps" -> "🚀"
    "flashlight", "flashlight_morse" -> "🔦"
    "vibrate" -> "📳"
    "speak" -> "🗣️"
    "set_alarm", "set_timer" -> "⏰"
    "show_notification" -> "🔔"
    "share_text", "copy_to_clipboard" -> "📤"
    "compose_sms" -> "💬"
    "dial" -> "📞"
    "open_settings" -> "⚙️"
    "battery_status", "device_info" -> "🔋"
    "read_screen", "screenshot" -> "👀"
    "tap", "tap_text", "long_press" -> "👆"
    "type_text" -> "⌨️"
    "scroll" -> "↕️"
    "wait" -> "⏳"
    "app_data_overview", "query_app_data" -> "🗄️"
    "add_app_data", "update_app_data" -> "✍️"
    "delete_app_data" -> "🗑️"
    else -> "🧭"
}

@Composable
private fun stepLabel(tool: String): String {
    val id = when (tool) {
        "create_web_app" -> R.string.step_create_web_app
        "improve_app" -> R.string.step_improve_app
        "list_my_apps" -> R.string.step_list_my_apps
        "open_my_app" -> R.string.step_open_my_app
        "open_url" -> R.string.step_open_url
        "youtube_search" -> R.string.step_youtube_search
        "web_search" -> R.string.step_web_search
        "open_maps" -> R.string.step_open_maps
        "open_app" -> R.string.step_open_app
        "list_installed_apps" -> R.string.step_list_installed_apps
        "flashlight" -> R.string.step_flashlight
        "flashlight_morse" -> R.string.step_flashlight_morse
        "vibrate" -> R.string.step_vibrate
        "speak" -> R.string.step_speak
        "set_alarm" -> R.string.step_set_alarm
        "set_timer" -> R.string.step_set_timer
        "show_notification" -> R.string.step_show_notification
        "share_text" -> R.string.step_share_text
        "copy_to_clipboard" -> R.string.step_copy_to_clipboard
        "compose_sms" -> R.string.step_compose_sms
        "dial" -> R.string.step_dial
        "open_settings" -> R.string.step_open_settings
        "battery_status" -> R.string.step_battery_status
        "device_info" -> R.string.step_device_info
        "wait" -> R.string.step_wait
        "app_data_overview" -> R.string.step_app_data_overview
        "query_app_data" -> R.string.step_query_app_data
        "add_app_data" -> R.string.step_add_app_data
        "update_app_data" -> R.string.step_update_app_data
        "delete_app_data" -> R.string.step_delete_app_data
        "read_screen" -> R.string.step_read_screen
        "screenshot" -> R.string.step_screenshot
        "tap", "tap_text" -> R.string.step_tap
        "long_press" -> R.string.step_long_press
        "type_text" -> R.string.step_type_text
        "scroll" -> R.string.step_scroll
        "go_back" -> R.string.step_go_back
        "go_home" -> R.string.step_go_home
        "open_recents" -> R.string.step_open_recents
        "open_notifications" -> R.string.step_open_notifications
        else -> return tool
    }
    return stringResource(id)
}
