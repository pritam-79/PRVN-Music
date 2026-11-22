package org.simpmusic.listentogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VotingQueueTest {

    @Test
    fun testPendingSuggestionVoteCountAndTally() {
        val track = TrackInfo(
            id = "test-1",
            title = "Hood Talk",
            artist = "Yung Dsa",
            duration = 185_000L,
        )
        val initial = PendingSuggestion(
            suggestionId = "sug-1",
            fromUsername = "User1",
            track = track,
            votes = emptySet(),
        )
        assertEquals(0, initial.voteCount)

        // User A votes
        val afterUserAVote = initial.copy(votes = initial.votes + "userA")
        assertEquals(1, afterUserAVote.voteCount)
        assertTrue(afterUserAVote.votes.contains("userA"))

        // User B votes
        val afterUserBVote = afterUserAVote.copy(votes = afterUserAVote.votes + "userB")
        assertEquals(2, afterUserBVote.voteCount)
        assertTrue(afterUserBVote.votes.contains("userB"))

        // User A unvotes (toggles off)
        val afterUserAUnvote = afterUserBVote.copy(votes = afterUserBVote.votes - "userA")
        assertEquals(1, afterUserAUnvote.voteCount)
        assertFalse(afterUserAUnvote.votes.contains("userA"))
        assertTrue(afterUserAUnvote.votes.contains("userB"))
    }

    @Test
    fun testVotingPriorityOrdering() {
        val track1 = TrackInfo(id = "1", title = "Song 1", artist = "Artist 1")
        val track2 = TrackInfo(id = "2", title = "Song 2", artist = "Artist 2")
        val track3 = TrackInfo(id = "3", title = "Song 3", artist = "Artist 3")

        val list = listOf(
            PendingSuggestion("s1", "User1", track1, votes = setOf("u1")),
            PendingSuggestion("s2", "User2", track2, votes = setOf("u1", "u2", "u3")),
            PendingSuggestion("s3", "User3", track3, votes = setOf("u1", "u2")),
        )

        val sorted = list.sortedByDescending { it.voteCount }
        assertEquals("s2", sorted[0].suggestionId)
        assertEquals(3, sorted[0].voteCount)

        assertEquals("s3", sorted[1].suggestionId)
        assertEquals(2, sorted[1].voteCount)

        assertEquals("s1", sorted[2].suggestionId)
        assertEquals(1, sorted[2].voteCount)
    }
}
