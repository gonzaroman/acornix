package com.gonzaroman.acornix.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.ui.settings.QuickConnect

/**
 * Connects an AI without leaving the screen: shown when something needs the AI and there is no key yet,
 * so what the user was doing (the app they just described) is still there when they come back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectAiSheet(onDismiss: () -> Unit, onConnected: () -> Unit, onStudio: (() -> Unit)? = null) {
    val settings by LocalContext.current.container.settings.settings.collectAsState(initial = null)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Text(stringResource(R.string.connect_ai_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.connect_ai_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            settings?.let { QuickConnect(it, onSaved = onConnected) }
            if (onStudio != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onStudio, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.connect_ai_studio))
                }
            }
        }
    }
}

/** Home, while no AI is connected: says what is missing and what it costs, instead of a warning. */
@Composable
fun ConnectAiCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔑", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.connect_ai_card_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.connect_ai_card_text), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text(stringResource(R.string.connect_ai_button)) }
        }
    }
}
