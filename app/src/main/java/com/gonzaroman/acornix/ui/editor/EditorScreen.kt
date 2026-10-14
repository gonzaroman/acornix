package com.gonzaroman.acornix.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.web.AppLauncher
import kotlinx.coroutines.launch

/** The original "FILES (edit your apps)" plugin: a plain code editor for index.html. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(nav: NavHostController, appId: String) {
    val context = LocalContext.current
    val repo = context.container.apps
    val scope = rememberCoroutineScope()
    val app = remember(appId) { repo.get(appId) }
    var original by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf(TextFieldValue("")) }
    var confirmExit by remember { mutableStateOf(false) }
    val dirty = original != null && code.text != original

    LaunchedEffect(appId) {
        val text = repo.readCode(appId)
        original = text
        code = TextFieldValue(text)
    }

    fun save(then: () -> Unit = {}) = scope.launch {
        repo.update(appId, code.text, context.getString(R.string.edited_by_hand))
        original = code.text
        Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
        then()
    }

    BackHandler(enabled = dirty) { confirmExit = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.name ?: appId) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmExit = true else nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { if (dirty) save { AppLauncher.open(context, appId) } else AppLauncher.open(context, appId) }) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.action_open))
                    }
                    IconButton(onClick = { save() }, enabled = dirty) { Icon(Icons.Rounded.Save, stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF1B1D23))
                .imePadding(),
        ) {
            if (original == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it },
                    textStyle = TextStyle(color = Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_text)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; save { nav.popBackStack() } }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false; nav.popBackStack() }) { Text(stringResource(R.string.discard)) } },
        )
    }
}
