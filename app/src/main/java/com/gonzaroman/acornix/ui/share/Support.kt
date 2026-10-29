package com.gonzaroman.acornix.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gonzaroman.acornix.R

const val GITHUB_URL = "https://github.com/gonzaroman/acornix"

/**
 * Remembers that we already asked for a GitHub star: it is asked once, never again, except once more
 * after the first GitHub sign-in, since then we know they have an account to give it with.
 */
class SupportPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("support", Context.MODE_PRIVATE)

    fun shouldAskForStar(): Boolean = !prefs.getBoolean(KEY_STAR_ASKED, false)

    fun markStarAsked() = prefs.edit().putBoolean(KEY_STAR_ASKED, true).apply()

    /** Just signed in with GitHub: ask for a star the next time Acornix opens, if that wasn't done already. */
    fun askStarAfterGitHubSignIn() {
        if (!prefs.getBoolean(KEY_GITHUB_STAR_ASKED, false)) prefs.edit().putBoolean(KEY_GITHUB_STAR_PENDING, true).apply()
    }

    /** True only once: on the first launch after that sign-in. */
    fun takeStarAfterGitHubSignIn(): Boolean {
        if (!prefs.getBoolean(KEY_GITHUB_STAR_PENDING, false)) return false
        prefs.edit().putBoolean(KEY_GITHUB_STAR_PENDING, false).putBoolean(KEY_GITHUB_STAR_ASKED, true).apply()
        return true
    }

    private companion object {
        const val KEY_STAR_ASKED = "star_asked"
        const val KEY_GITHUB_STAR_PENDING = "github_star_pending"
        const val KEY_GITHUB_STAR_ASKED = "github_star_asked"
    }
}

fun openGitHub(context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Shares a short text with the link to Acornix, for the "Support Acornix" section. */
fun shareAcornixLink(context: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.support_share_text))
    context.startActivity(Intent.createChooser(send, context.getString(R.string.support_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** "Do you like Acornix? A star helps." Shown once in a lifetime; either answer counts as asked. */
@Composable
fun StarDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val close = {
        SupportPrefs(context).markStarAsked()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.star_title)) },
        text = { Text(stringResource(R.string.star_text)) },
        confirmButton = {
            Button(onClick = {
                openGitHub(context)
                close()
            }) { Text(stringResource(R.string.star_yes)) }
        },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.star_later)) } },
    )
}
