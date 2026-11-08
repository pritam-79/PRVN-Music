package projectcommon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncMathTest {

    @Test
    fun testDriftCompensationThreshold() {
        val toleranceMs = 500L

        // Drift within tolerance -> no seek needed
        val localPos1 = 30000L
        val expectedPos1 = 30150L
        val drift1 = abs(localPos1 - expectedPos1)
        assertEquals(150L, drift1)
        assertFalse(drift1 > toleranceMs, "150ms drift should be within 500ms tolerance")

        // Drift exceeding tolerance -> compensation seek triggered
        val localPos2 = 30000L
        val expectedPos2 = 30750L
        val drift2 = abs(localPos2 - expectedPos2)
        assertEquals(750L, drift2)
        assertTrue(drift2 > toleranceMs, "750ms drift should exceed 500ms tolerance")
    }

    @Test
    fun testMidSongElapsedPositionCalculation() {
        val serverTimestamp = 1700000000000L
        val serverPosition = 45000L
        val clientNow = 1700000003500L // 3.5 seconds later

        val elapsed = clientNow - serverTimestamp
        assertEquals(3500L, elapsed)

        val isPlaying = true
        val targetHead = serverPosition + if (isPlaying) elapsed else 0L
        assertEquals(48500L, targetHead)

        val isPaused = false
        val targetHeadPaused = serverPosition + if (isPaused) elapsed else 0L
        assertEquals(45000L, targetHeadPaused)
    }

    @Test
    fun testYouTubeOnlyTrackValidation() {
        fun isValidSyncableTrack(trackId: String, isYouTube: Boolean): Boolean {
            val isLocal = !isYouTube ||
                trackId.startsWith("file://") ||
                trackId.startsWith("local_") ||
                trackId.startsWith("/") ||
                trackId.contains("content://")
            return !isLocal
        }

        assertTrue(isValidSyncableTrack("dQw4w9WgXcQ", isYouTube = true))
        assertTrue(isValidSyncableTrack("track_seed_1234", isYouTube = true))

        assertFalse(isValidSyncableTrack("file:///sdcard/Music/song.mp3", isYouTube = true))
        assertFalse(isValidSyncableTrack("local_4920", isYouTube = true))
        assertFalse(isValidSyncableTrack("/storage/emulated/0/Music/track.flac", isYouTube = true))
        assertFalse(isValidSyncableTrack("content://media/external/audio/media/123", isYouTube = true))
        assertFalse(isValidSyncableTrack("dQw4w9WgXcQ", isYouTube = false))
    }
}
