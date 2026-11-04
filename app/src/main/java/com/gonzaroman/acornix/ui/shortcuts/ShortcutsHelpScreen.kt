package com.gonzaroman.acornix.ui.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.core.ShortcutTemplates
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.navigateTab

/**
 * "How do shortcuts work?": what they are, the three ways to make one, a step-by-step walkthrough, variables,
 * logic, web data, triggers and launchers, and ready-made examples that open in the editor with one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsHelpScreen(nav: NavHostController) {
    val context = LocalContext.current
    val spanish = context.resources.configuration.locales[0].language == "es"
    val templates = ShortcutTemplates.all(spanish)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_shortcuts_title)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                        .background(Brush.linearGradient(listOf(Color(0xFF4E3BB5), Color(0xFFD9468F))))
                        .padding(22.dp),
                ) {
                    Column {
                        Text("⚡", fontSize = 44.sp)
                        Text(stringResource(R.string.help_shortcuts_hero_title), color = Color.White,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.help_shortcuts_hero_text), color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Section("🧭", R.string.help_ways_title, R.array.help_ways) }
            item { Section("🧩", R.string.help_first_title, R.array.help_first, numbered = true) }
            item { Section("🏷️", R.string.help_vars_title, R.array.help_vars) }
            item { Section("🔀", R.string.help_logic_title, R.array.help_logic) }
            item { Section("🌍", R.string.help_web_title, R.array.help_web) }
            item { Section("⏰", R.string.help_when_title, R.array.help_when) }
            item { Section("🚀", R.string.help_launch_title, R.array.help_launch) }
            item {
                Text(stringResource(R.string.help_examples_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.help_examples_text), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(templates, key = { it.id }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium)
                                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // The steps, as the editor will show them.
                        val depths = t.recipe.depths()
                        t.recipe.steps.forEachIndexed { i, step ->
                            val a = shortcutAction(step.action)
                            Text(
                                "  ".repeat(depths[i] * 2) + (a?.emoji ?: "•") + " " + (a?.let { stringResource(it.label) } ?: step.action) +
                                    stepSummary(a, step.params, context),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (t.tip.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("💡 " + t.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = { nav.navigate(Routes.shortcutEditor(template = t.id)) }) {
                            Text(stringResource(R.string.help_create_this))
                        }
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { nav.popBackStack(); nav.navigateTab(Routes.SHORTCUTS) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.help_done))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** ": 07:30 · Good morning" — the step's settings as the editor shows them (choices by their name, levels in %). */
private fun stepSummary(action: ActionDef?, params: Map<String, String>, context: android.content.Context): String {
    val shown = action?.fields.orEmpty().mapNotNull { f ->
        val v = params[f.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        when (f.kind) {
            FieldKind.CHOICE -> f.choices.firstOrNull { it.first == v }?.let { context.getString(it.second) }
            FieldKind.PERCENT -> "$v %"
            FieldKind.VARNAME -> "→ {$v}"
            else -> v.replace("\n", " / ").take(40)
        }
    }
    return if (shown.isEmpty()) "" else ": " + shown.joinToString(" · ")
}

@Composable
private fun Section(emoji: String, title: Int, lines: Int, numbered: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
            stringArrayResource(lines).forEachIndexed { i, line ->
                Row {
                    Text(if (numbered) "${i + 1}." else "•", modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
