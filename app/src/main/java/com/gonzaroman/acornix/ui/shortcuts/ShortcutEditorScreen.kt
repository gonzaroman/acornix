package com.gonzaroman.acornix.ui.shortcuts

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.core.ShortcutRecipe
import com.gonzaroman.acornix.core.ShortcutStep
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds a shortcut without AI, like iPhone Shortcuts: a name, an emoji and a list of actions picked from
 * [SHORTCUT_ACTIONS], each with its settings. [appId] edits an existing step-by-step shortcut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutEditorScreen(nav: NavHostController, appId: String?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.shortcut_name_default)
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("⚡") }
    val steps = remember { mutableStateListOf<ShortcutStep>() }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(appId) {
        if (appId == null) return@LaunchedEffect
        val meta = c.apps.get(appId) ?: return@LaunchedEffect
        val recipe = withContext(Dispatchers.IO) { runCatching { ShortcutRecipe.fromHtml(c.apps.indexFile(appId).readText()) }.getOrNull() }
        name = meta.name
        emoji = meta.emoji
        steps.clear()
        recipe?.steps?.let { steps.addAll(it) }
    }

    fun save(andRun: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            val finalName = name.trim().ifEmpty { defaultName }
            val finalEmoji = emoji.trim().ifEmpty { "⚡" }
            val html = ShortcutRecipe(steps = steps.toList()).html(finalName, finalEmoji)
            val summary = steps.mapNotNull { shortcutAction(it.action)?.let { a -> a.emoji + " " + context.getString(a.label) } }.joinToString(" · ")
            val id = if (appId == null) {
                c.apps.create(finalName, html, AppKind.FEATURE, description = summary, emoji = finalEmoji, note = "Created step by step").id
            } else {
                c.apps.update(appId, html, "Edited steps")
                c.apps.rename(appId, finalName, finalEmoji)
                appId
            }
            Toast.makeText(context, R.string.shortcut_saved, Toast.LENGTH_SHORT).show()
            nav.popBackStack()
            if (andRun) AppLauncher.open(context, id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shortcut_editor_title)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { save(false) }, enabled = steps.isNotEmpty() && !saving, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.shortcut_save))
                    }
                    Button(onClick = { save(true) }, enabled = steps.isNotEmpty() && !saving, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.shortcut_try))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = emoji, onValueChange = { emoji = it.take(8) },
                        label = { Text(stringResource(R.string.shortcut_emoji)) }, singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(40) },
                        label = { Text(stringResource(R.string.shortcut_name)) }, placeholder = { Text(defaultName) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
            if (steps.isEmpty()) {
                item {
                    Text(stringResource(R.string.shortcut_no_steps), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(steps) { index, step ->
                StepCard(
                    number = index + 1,
                    step = step,
                    onChange = { steps[index] = it },
                    onUp = if (index > 0) ({ steps.add(index - 1, steps.removeAt(index)) }) else null,
                    onDown = if (index < steps.lastIndex) ({ steps.add(index + 1, steps.removeAt(index)) }) else null,
                    onRemove = { steps.removeAt(index) },
                )
            }
            item {
                FilledTonalButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shortcut_add_step))
                }
            }
            if (steps.any { s -> shortcutAction(s.action)?.fields?.any { it.variables } == true }) {
                item {
                    Text(stringResource(R.string.shortcut_variables), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            LazyColumn(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                SHORTCUT_ACTIONS.groupBy { it.group }.forEach { (group, actions) ->
                    item {
                        Text(stringResource(group), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
                    }
                    actions.forEach { action ->
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(action.label)) },
                                leadingContent = { Text(action.emoji, fontSize = 22.sp) },
                                modifier = Modifier.clickable {
                                    steps.add(ShortcutStep(action.id, action.defaults()))
                                    picking = false
                                },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** One step: what it does and its settings, with buttons to move it or take it out. */
@Composable
private fun StepCard(
    number: Int,
    step: ShortcutStep,
    onChange: (ShortcutStep) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val action = shortcutAction(step.action) ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number.  ${action.emoji}", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(action.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.shortcut_move_up)) }
                IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.shortcut_move_down)) }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.shortcut_remove_step)) }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                action.fields.forEach { field ->
                    val value = step.params[field.key] ?: field.default
                    FieldEditor(field, value) { onChange(step.copy(params = step.params + (field.key to it))) }
                }
            }
        }
    }
}

@Composable
private fun FieldEditor(field: FieldDef, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    when (field.kind) {
        FieldKind.CHOICE -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            field.choices.forEach { (v, label) ->
                FilterChip(selected = value == v, onClick = { onChange(v) }, label = { Text(stringResource(label)) })
            }
        }
        FieldKind.PERCENT -> Column {
            Text(stringResource(field.label) + ": ${value.toIntOrNull() ?: 0} %", style = MaterialTheme.typography.bodyMedium)
            Slider(value = (value.toFloatOrNull() ?: 0f), onValueChange = { onChange(it.toInt().toString()) }, valueRange = 0f..100f)
        }
        FieldKind.TIME -> {
            val (h, m) = value.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 7) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute -> onChange("%02d:%02d".format(hour, minute)) }, h, m, true).show()
            }) { Text("🕒  " + stringResource(field.label) + ": " + "%02d:%02d".format(h, m)) }
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(field.label)) },
            singleLine = field.kind != FieldKind.TEXT,
            keyboardOptions = KeyboardOptions(keyboardType = when (field.kind) {
                FieldKind.NUMBER -> KeyboardType.Number
                FieldKind.PHONE -> KeyboardType.Phone
                else -> KeyboardType.Text
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
