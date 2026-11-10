package com.gonzaroman.acornix.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTypeTest {
    @Test
    fun `a pasted key tells its provider`() {
        assertEquals(ProviderType.ANTHROPIC, ProviderType.forKey("sk-ant-api03-abc"))
        assertEquals(ProviderType.OPENAI, ProviderType.forKey("sk-proj-abc"))
        assertEquals(ProviderType.OPENAI, ProviderType.forKey("  sk-abc  "))
        assertEquals(ProviderType.GEMINI, ProviderType.forKey("AIzaSyabc"))
    }

    @Test
    fun `an unknown key is taken as a Google one`() {
        assertEquals(ProviderType.GEMINI, ProviderType.forKey("something-new"))
    }
}
