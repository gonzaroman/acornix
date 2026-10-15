package com.gonzaroman.acornix.ui.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ui.components.ControlState
import com.gonzaroman.acornix.ui.components.closesBackgroundApps
import com.gonzaroman.acornix.ui.components.openAccessibilitySettings
import com.gonzaroman.acornix.ui.components.openAppSettings
import com.gonzaroman.acornix.ui.components.openAutostartSettings
import com.gonzaroman.acornix.ui.components.rememberControlState

/** Step-by-step guide, with screenshots, to switch Total control on and keep it on. */
@Composable
fun ControlHelpScreen(nav: NavHostController) {
    val context = LocalContext.current
    val controlState by rememberControlState()

    ToolScaffold(nav, stringResource(R.string.control_help_title)) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.control_help_intro), style = MaterialTheme.typography.bodyMedium)
            StatusCard(controlState)

            // Xiaomi & co. first on those phones: it is where Total control keeps switching itself off.
            val brandFirst = closesBackgroundApps()
            if (brandFirst) BrandSection()

            HelpSection("1️⃣", R.string.control_help_on_title) {
                Step(1, R.string.control_help_on_1, R.drawable.help_a11y_list)
                Action(R.string.control_open_accessibility) { openAccessibilitySettings(context) }
                Step(2, R.string.control_help_on_2, R.drawable.help_service_toggle)
                Step(3, R.string.control_help_on_3, R.drawable.help_allow)
            }

            HelpSection("🔒", R.string.control_help_restricted_title, R.string.control_help_restricted_intro) {
                Step(1, R.string.control_help_restricted_1, R.drawable.help_restricted_row)
                Shot(R.drawable.help_restricted_dialog)
                Step(2, R.string.control_help_restricted_2, R.drawable.help_allow_restricted)
                Action(R.string.control_app_settings) { openAppSettings(context) }
                Step(3, R.string.control_help_restricted_3)
            }

            if (!brandFirst) BrandSection()

            HelpSection("🛑", R.string.control_help_stopped_title, R.string.control_help_stopped_text) {
                Action(R.string.control_open_accessibility) { openAccessibilitySettings(context) }
            }
        }
    }
}

@Composable
private fun BrandSection() {
    val context = LocalContext.current
    HelpSection("📱", R.string.control_help_brand_title, R.string.control_help_brand_intro) {
        Step(1, R.string.control_help_brand_1, R.drawable.help_mi_autostart)
        Action(R.string.control_help_open_autostart) { openAutostartSettings(context) }
        Step(2, R.string.control_help_brand_2, R.drawable.help_mi_power)
        Action(R.string.control_app_settings) { openAppSettings(context) }
        Step(3, R.string.control_help_brand_3, R.drawable.help_mi_battery)
        Step(4, R.string.control_help_brand_4, R.drawable.help_mi_recents)
        Text(stringResource(R.string.control_help_brand_others), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(state: ControlState) {
    val (emoji, text) = when (state) {
        ControlState.ON -> "✅" to R.string.settings_control_on
        ControlState.STALLED -> "⚠️" to R.string.settings_control_stalled
        ControlState.OFF -> "⭕" to R.string.settings_control_off
    }
    Surface(
        color = if (state == ControlState.ON) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$emoji ${stringResource(text)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun HelpSection(emoji: String, @StringRes title: Int, @StringRes intro: Int? = null, content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$emoji ${stringResource(title)}", style = MaterialTheme.typography.titleMedium)
            if (intro != null) Text(stringResource(intro), style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun Step(number: Int, @StringRes text: Int, @DrawableRes image: Int? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
    if (image != null) Shot(image)
}

/** A screenshot of the system screen, with the part to tap marked in orange. */
@Composable
private fun Shot(@DrawableRes image: Int) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            painterResource(image), null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Action(@StringRes label: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
}
