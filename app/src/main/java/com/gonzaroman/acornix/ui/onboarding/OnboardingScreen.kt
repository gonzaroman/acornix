package com.gonzaroman.acornix.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.ui.assistant.rememberControlEnabled
import com.gonzaroman.acornix.ui.settings.ProviderEditor
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.settings
    val settings by repo.settings.collectAsState(initial = null)
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    fun finish() = scope.launch {
        repo.setOnboardingDone()
        onDone()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = false) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> {
                        PageHeader("🔑", stringResource(R.string.onb_ai_title), stringResource(R.string.onb_ai_text))
                        settings?.let { s ->
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                ProviderEditor(s, Modifier.padding(16.dp), onSaved = { scope.launch { pager.animateScrollToPage(2) } })
                            }
                        }
                    }
                    else -> PermissionsPage()
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(3) { i ->
                    val color by animateColorAsState(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "dot",
                    )
                    Box(Modifier.size(width = if (pager.currentPage == i) 24.dp else 8.dp, height = 8.dp).clip(CircleShape).background(color))
                }
            }
            when (pager.currentPage) {
                0 -> Button(onClick = { scope.launch { pager.animateScrollToPage(1) } }) { Text(stringResource(R.string.onb_start)) }
                1 -> TextButton(onClick = { scope.launch { pager.animateScrollToPage(2) } }) { Text(stringResource(R.string.onb_later)) }
                else -> Button(onClick = { finish() }) { Text(stringResource(R.string.onb_finish)) }
            }
        }
    }
}

@Composable
private fun PageHeader(emoji: String, title: String, text: String) {
    Text(emoji, fontSize = 56.sp)
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomePage() {
    Spacer(Modifier.height(24.dp))
    PageHeader("🌰", stringResource(R.string.onb_welcome_title), stringResource(R.string.onb_welcome_text))
    Feature("✨", stringResource(R.string.onb_feature_create))
    Feature("🧬", stringResource(R.string.onb_feature_feature))
    Feature("🤖", stringResource(R.string.onb_feature_control))
    Feature("📱", stringResource(R.string.onb_feature_apps))
}

@Composable
private fun Feature(emoji: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    var notificationsAsked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAsked = true }
    val controlEnabled by rememberControlEnabled()

    PageHeader("🛡️", stringResource(R.string.onb_perm_title), stringResource(R.string.onb_perm_text))

    if (Build.VERSION.SDK_INT >= 33) {
        PermissionCard(
            emoji = "🔔",
            title = stringResource(R.string.onb_perm_notif),
            text = stringResource(R.string.onb_perm_notif_text),
            done = notificationsAsked,
        ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        Spacer(Modifier.height(12.dp))
    }
    PermissionCard(
        emoji = "👆",
        title = stringResource(R.string.settings_control),
        text = stringResource(R.string.onb_perm_control_text),
        done = controlEnabled,
    ) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun PermissionCard(emoji: String, title: String, text: String, done: Boolean, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (done) Text("✅")
            }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!done) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.enable)) }
            }
        }
    }
}
