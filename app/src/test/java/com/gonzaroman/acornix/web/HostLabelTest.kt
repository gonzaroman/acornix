package com.gonzaroman.acornix.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLabelTest {
    @Test
    fun slugIdsBecomeReadableHostLabels() {
        assertEquals("castle-defense", AcornixWebView.hostLabel("castle_defense"))
        assertEquals("piano", AcornixWebView.hostLabel("piano"))
    }

    @Test
    fun otherIdsAreHashedIntoValidDistinctLabels() {
        val legacy = AcornixWebView.hostLabel("castle defense")
        assertTrue(Regex("^x0[0-9a-f]{20}$").matches(legacy))
        assertNotEquals(legacy, AcornixWebView.hostLabel("castle-defense"))
        assertNotEquals(AcornixWebView.hostLabel("a_b"), AcornixWebView.hostLabel("a-b"))
        assertTrue(AcornixWebView.hostLabel("_edge").startsWith("x0"))
    }
}
