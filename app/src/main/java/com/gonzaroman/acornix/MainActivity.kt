package com.gonzaroman.acornix

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.gonzaroman.acornix.ui.AcornixApp
import com.gonzaroman.acornix.ui.ExternalRequest
import com.gonzaroman.acornix.ui.components.ImportReviewDialog
import com.gonzaroman.acornix.ui.theme.AcornixTheme

class MainActivity : ComponentActivity() {

    private var external by mutableStateOf<ExternalRequest?>(null)
    /** A ZIP opened from another app, waiting for the user to review and install it. */
    private var pendingImport by mutableStateOf<Uri?>(null)

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
                        startTour = s.onboardingDone && !s.tourSeen,
                        external = external,
                        onExternalConsumed = { external = null },
                    )
                    pendingImport?.let { uri -> ImportReviewDialog(uri) { pendingImport = null } }
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
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val image = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                external = ExternalRequest(openAssistant = true, sharedText = text, sharedImage = image)
            }
            Intent.ACTION_VIEW -> intent.data?.let { pendingImport = it }
        }
    }

    companion object {
        const val ACTION_OPEN_ASSISTANT = "com.gonzaroman.acornix.OPEN_ASSISTANT"
    }
}
