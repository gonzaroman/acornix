package com.gonzaroman.acornix.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.ui.Routes
import com.gonzaroman.acornix.ui.components.AppCard
import com.gonzaroman.acornix.ui.components.SectionTitle
import com.gonzaroman.acornix.ui.components.UpdateCard
import com.gonzaroman.acornix.ui.navigateTab
import com.gonzaroman.acornix.web.AcornixOsActivity
import com.gonzaroman.acornix.web.AppLauncher
import java.util.Calendar

@Composable
fun HomeScreen(nav: NavHostController) {
    val context = LocalContext.current
    val c = context.container
    val settings by c.settings.settings.collectAsState(initial = null)
    val apps by c.apps.apps.collectAsState()
    val update by c.updates.available.collectAsState()
    val config = settings?.config()

    LazyColumn(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(stringResource(greeting()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                if (config != null) {
                    if (config.isUsable) {
                        AssistChip(
                            onClick = { nav.navigateTab(Routes.SETTINGS) },
                            label = { Text("${config.type.label} · ${config.model}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3DBE6C))) },
                        )
                    } else {
                        AssistChip(
                            onClick = { nav.navigateTab(Routes.SETTINGS) },
                            label = { Text(stringResource(R.string.home_configure_ai)) },
                            leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        )
                    }
                }
            }
        }

        update?.let { info -> item(key = "update") { UpdateCard(info) } }

        item { HeroCard(onClick = { nav.navigate(Routes.create()) }) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTile("🧬", stringResource(R.string.section_feature), stringResource(R.string.section_feature_desc),
                        MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f)) { nav.navigate(Routes.create(feature = true)) }
                    SectionTile("🤖", stringResource(R.string.section_control), stringResource(R.string.section_control_desc),
                        MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f)) { nav.navigateTab(Routes.assistant(control = true)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTile("📱", stringResource(R.string.section_apps), pluralStringResource(R.plurals.section_apps_desc, apps.size, apps.size),
                        MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f)) { nav.navigateTab(Routes.APPS) }
                    SectionTile("📋", stringResource(R.string.section_studio), stringResource(R.string.section_studio_desc),
                        MaterialTheme.colorScheme.surfaceContainerHighest, Modifier.weight(1f)) { nav.navigate(Routes.STUDIO) }
                }
                ExploreBanner { nav.navigate(Routes.EXPLORE) }
            }
        }

        if (apps.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.home_recent)) {
                    androidx.compose.material3.TextButton(onClick = { nav.navigateTab(Routes.APPS) }) { Text(stringResource(R.string.see_all)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(apps.take(10), key = { it.id }) { app ->
                        AppCard(
                            app = app,
                            onClick = { AppLauncher.open(context, app.id) },
                            onLongClick = { nav.navigate(Routes.improve(app.id)) },
                            modifier = Modifier.width(128.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.home_tools))
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                ToolRow(Icons.Rounded.DesktopWindows, stringResource(R.string.tool_os), stringResource(R.string.tool_os_desc)) { AcornixOsActivity.open(context) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                ToolRow(Icons.Rounded.History, stringResource(R.string.tool_versions), stringResource(R.string.tool_versions_desc)) { nav.navigate(Routes.VERSIONS_HUB) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                ToolRow(Icons.Rounded.Inventory2, stringResource(R.string.tool_import_export), stringResource(R.string.tool_import_export_desc)) { nav.navigate(Routes.IMPORT_EXPORT) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                ToolRow(Icons.Rounded.BatteryChargingFull, stringResource(R.string.tool_health), stringResource(R.string.tool_health_desc)) { nav.navigate(Routes.HEALTH) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                ToolRow(Icons.AutoMirrored.Rounded.HelpOutline, stringResource(R.string.tool_help), stringResource(R.string.tool_help_desc)) { nav.navigate(Routes.HELP) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 6..13 -> R.string.greeting_morning
    in 14..20 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .clickable(onClick = onClick)
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxWidth(0.78f)) {
            Text(stringResource(R.string.hero_title), style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.hero_desc), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimary.copy(alpha = 0.9f))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
            ) {
                Text(stringResource(R.string.hero_button))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
            }
        }
        Text("✨", fontSize = 64.sp, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun SectionTile(emoji: String, title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = MaterialTheme.shapes.large,
        color = color,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

/** The way into the community gallery, full width under the tiles. */
@Composable
private fun ExploreBanner(onClick: () -> Unit) {
    // A light runs around the border so the community gallery doesn't go unnoticed.
    val spin = rememberInfiniteTransition(label = "explore")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "angle")
    val shape = MaterialTheme.shapes.large
    Box(
        Modifier.fillMaxWidth().clip(shape).drawBehind {
            rotate(angle) { drawCircle(Brush.sweepGradient(EXPLORE_GLOW), radius = size.maxDimension) }
        }.padding(3.dp),
    ) {
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌍", fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.explore_home_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.explore_home_desc), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
        }
    }
    }
}

private val EXPLORE_GLOW = listOf(Color.Transparent, Color.Transparent, Color(0xFF7CF0FF), Color(0xFFB18CFF), Color(0xFFFF7AD9), Color.Transparent)

@Composable
private fun ToolRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
