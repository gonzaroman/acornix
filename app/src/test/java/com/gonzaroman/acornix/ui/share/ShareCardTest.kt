package com.gonzaroman.acornix.ui.share

import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareCardTest {
    private fun app(description: String, kind: AppKind = AppKind.APP, versions: Int = 1) =
        AppMeta(id = "a", name = "A", emoji = "🎮", kind = kind, description = description, versionCount = versions)

    @Test
    fun promptIsTheIdeaOnOneLine() {
        assertEquals("A neon Tetris with score", shareablePrompt(app("  A neon\nTetris   with score \n")))
    }

    @Test
    fun samplesShowNoPromptAndNoChanges() {
        assertEquals("", shareablePrompt(app("Bundled", AppKind.SAMPLE)))
        assertEquals(0, changesCount(app("", AppKind.SAMPLE, versions = 3)))
    }

    @Test
    fun changesAreTheVersionsAfterTheFirst() {
        assertEquals(0, changesCount(app("x")))
        assertEquals(2, changesCount(app("x", versions = 3)))
    }
}
