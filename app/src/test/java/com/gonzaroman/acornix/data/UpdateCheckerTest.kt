package com.gonzaroman.acornix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("2.0.1", "2.0.0"))
        assertTrue(UpdateChecker.isNewer("v2.10.0", "2.9.9"))
        assertTrue(UpdateChecker.isNewer("3", "2.9"))
        assertFalse(UpdateChecker.isNewer("2.0.0", "2.0.0"))
        assertFalse(UpdateChecker.isNewer("2.0", "2.0.0"))
        assertFalse(UpdateChecker.isNewer("1.9.9", "2.0.0"))
        assertFalse(UpdateChecker.isNewer("2.0.0-beta", "2.0.0"))
        assertTrue(UpdateChecker.isNewer("2.2.0", "2.2.0-beta1"))
        assertFalse(UpdateChecker.isNewer("2.1.0", "2.2.0-beta1"))
    }

    @Test
    fun readsGithubLatestRelease() {
        val body = """
            {"tag_name":"v2.1.0","html_url":"https://github.com/gonzaroman/acornix/releases/tag/v2.1.0",
             "body":"## What's new\r\n- **Faster** apps\r\n- Fixes `bug`\r\n![shot](x.png)",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
                       {"name":"acornix-2.1.0.apk","browser_download_url":"https://x/acornix-2.1.0.apk","size":26000000}]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(body)!!
        assertEquals("2.1.0", info.version)
        assertEquals("https://x/acornix-2.1.0.apk", info.apkUrl)
        assertEquals(26000000L, info.apkSize)
        assertEquals("What's new\n• Faster apps\n• Fixes bug", info.notes)
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v9.0.0","assets":[]}"""))
        assertNull(UpdateChecker.parseRelease("not json"))
    }
}
