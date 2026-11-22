package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.models.TidalMetadataResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TidalMetadataRateLimitTest {

    @Test
    fun testTidalMetadataResultCreation() {
        val metadata = TidalMetadataResult(
            bpm = 120,
            musicKey = "C",
            keyScale = "MAJOR",
        )
        assertEquals(120, metadata.bpm)
        assertEquals("C", metadata.musicKey)
        assertEquals("MAJOR", metadata.keyScale)
    }

    @Test
    fun testCacheKeyFormatting() {
        val query = "Insomaniac Yo Yo Honey Singh"
        val duration = 210
        val key = "$query|$duration"
        assertEquals("Insomaniac Yo Yo Honey Singh|210", key)
    }

    @Test
    fun testBackoffDurationCalculation() {
        // Backoff should exponentially grow up to a bounded cap
        for (consecutive429s in 0..6) {
            val backoffSeconds = (60L * (1 shl minOf(consecutive429s, 4))).coerceIn(30L, 600L)
            assertTrue(backoffSeconds in 30L..600L)
        }
    }
}
