package com.gonzaroman.acornix

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.gonzaroman.acornix.data.ImportException
import com.gonzaroman.acornix.ui.AcornixApp
import com.gonzaroman.acornix.ui.ExternalRequest
import com.gonzaroman.acornix.ui.importErrorMessage
import com.gonzaroman.acornix.ui.theme.AcornixTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var external by mutableStateOf<ExternalRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings
            AcornixTheme(mode = s?.theme ?: com.gonzaroman.acornix.data.ThemeMode.SYSTEM, dynamicColor = s?.dynamicColor ?: true) {
                if (s == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    AcornixApp(
                        startOnboarding = !s.onboardingDone,
                        external = external,
                        onExternalConsumed = { external = null },
                        onReturnToOs = { moveTaskToBack(true) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_ASSISTANT -> external = ExternalRequest(openAssistant = true)
            ACTION_OPEN_SCREEN -> external = ExternalRequest(screen = intent.getStringExtra(EXTRA_SCREEN), fromOs = true)
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val image = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                external = ExternalRequest(openAssistant = true, sharedText = text, sharedImage = image)
            }
            Intent.ACTION_VIEW -> intent.data?.let { importZip(it) }
        }
    }

    private fun importZip(uri: Uri) {
        lifecycleScope.launch {
            try {
                val app = container.importExport.import(uri)
                Toast.makeText(this@MainActivity, getString(R.string.import_done, app.name), Toast.LENGTH_LONG).show()
            } catch (e: ImportException) {
                Toast.makeText(this@MainActivity, importErrorMessage(this@MainActivity, e), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_ASSISTANT = "com.gonzaroman.acornix.OPEN_ASSISTANT"

        /** Opens one screen for AcornixOS; Back on that screen returns to the desktop. */
        const val ACTION_OPEN_SCREEN = "com.gonzaroman.acornix.OPEN_SCREEN"
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_CREATE = "create"
        const val SCREEN_ASSISTANT = "assistant"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_APPS = "apps"
    }
}
