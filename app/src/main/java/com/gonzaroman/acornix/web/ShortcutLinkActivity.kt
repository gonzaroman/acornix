package com.gonzaroman.acornix.web

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container

/**
 * Opens a shortcut from an NFC sticker written by Acornix (acornix://app/<id>). It only starts one of the
 * user's own apps, so no other app or website can do anything through it but open them.
 */
class ShortcutLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val id = data?.takeIf { it.scheme == "acornix" && it.host == "app" }?.lastPathSegment.orEmpty()
        if (id.isNotEmpty() && container.apps.get(id) != null) {
            startActivity(AppLauncher.intent(this, id, "nfc").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
