package com.gonzaroman.acornix.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppMatchTest {
    private val apps = listOf(
        InstalledApp("YouTube", "com.google.android.youtube"),
        InstalledApp("YouTube Music", "com.google.android.apps.youtube.music"),
        InstalledApp("Spotify", "com.spotify.music"),
        InstalledApp("WhatsApp", "com.whatsapp"),
        InstalledApp("Maps", "com.google.android.apps.maps"),
    )

    private fun match(q: String) = DeviceActions.bestAppMatch(q, apps)?.packageName

    @Test
    fun findsAppsByPackageNameAndTypos() {
        assertEquals("com.spotify.music", match("com.spotify.music"))
        assertEquals("com.google.android.youtube", match("youtube"))
        assertEquals("com.google.android.youtube", match("YouTubi"))
        assertEquals("com.spotify.music", match("Spotfy"))
        assertEquals("com.whatsapp", match("whatsap"))
        assertEquals("com.google.android.apps.maps", match("Mapa"))
    }

    @Test
    fun doesNotGuessWildly() {
        assertNull(match("Telegram"))
        assertNull(match(""))
    }
}
