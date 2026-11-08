package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import kotlin.time.Clock

/**
 * Event types dispatched across the real-time room channel.
 */
enum class PlaybackEventType {
    PLAY,
    PAUSE,
    SEEK,
    TRACK_CHANGE,
    QUEUE_UPDATE,
    SYNC_PING,
}

/**
 * Real-time playback synchronization payload sent over WebSocket / Phoenix channel.
 */
data class PlaybackEvent(
    val roomId: String,
    val senderId: String,
    val eventType: PlaybackEventType,
    val track: PlayTogetherTrack? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val queue: List<PlayTogetherTrack> = emptyList(),
    val timestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
)

/**
 * Incoming room events received by clients.
 */
sealed interface RoomEvent {
    data class PlaybackUpdated(
        val isPlaying: Boolean,
        val positionMs: Long,
        val track: PlayTogetherTrack?,
        val timestampUtc: Long,
        val senderId: String,
    ) : RoomEvent

    data class QueueChanged(
        val queue: List<PlayTogetherTrack>,
        val currentTrack: PlayTogetherTrack?,
    ) : RoomEvent

    data class MemberPresenceChanged(
        val members: List<RoomMember>,
    ) : RoomEvent

    data class MemberJoined(
        val member: RoomMember,
    ) : RoomEvent

    data class MemberLeft(
        val memberId: String,
        val memberName: String,
    ) : RoomEvent

    data class RoomClosed(
        val reason: String,
    ) : RoomEvent

    data class Error(
        val message: String,
    ) : RoomEvent
}

/**
 * Result returned upon successfully verifying and joining a room.
 */
data class JoinRoomResult(
    val room: RoomInfo,
    val isHost: Boolean,
    val currentMemberId: String,
    val members: List<RoomMember>,
    val currentTrack: PlayTogetherTrack?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val queue: List<PlayTogetherTrack>,
    val serverTimestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
)

/**
 * Sliding-window rate limiter to prevent brute-forcing 4-digit room codes or spamming room creations.
 */
class RateLimiter(
    private val maxRequests: Int = 5,
    private val windowMs: Long = 30_000L,
) {
    private val timestamps = mutableListOf<Long>()

    /**
     * Checks if a new request is permitted under current rate-limiting policies.
     * Returns true if allowed, false if limit exceeded.
     */
    fun tryAcquire(): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        cleanOld(now)
        return if (timestamps.size < maxRequests) {
            timestamps.add(now)
            true
        } else {
            false
        }
    }

    /**
     * Seconds remaining until the rate limit window permits new attempts.
     */
    fun secondsUntilReset(): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        cleanOld(now)
        if (timestamps.isEmpty()) return 0
        val oldest = timestamps.first()
        val remainingMs = (oldest + windowMs) - now
        return ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    }

    private fun cleanOld(now: Long) {
        val cutoff = now - windowMs
        while (timestamps.isNotEmpty() && timestamps.first() < cutoff) {
            timestamps.removeAt(0)
        }
    }
}

/**
 * Security and validation rules for room creation, code validation, and host authority.
 */
object RoomSecurityPolicy {
    private val FOUR_DIGIT_REGEX = Regex("^[0-9]{4}$")

    /**
     * Validates whether a room code is strictly 4 numeric digits.
     */
    fun isValidRoomCode(code: String): Boolean {
        return FOUR_DIGIT_REGEX.matches(code.trim())
    }

    /**
     * Validates and sanitizes a user-entered room name.
     */
    fun sanitizeRoomName(name: String): String {
        val trimmed = name.trim().take(40)
        return trimmed.ifBlank { "PRVN Music Room" }
    }

    /**
     * Validates user display name.
     */
    fun sanitizeUserName(name: String): String {
        val trimmed = name.trim().take(25)
        return trimmed.ifBlank { "Listener" }
    }

    /**
     * Verifies if a member is authorized to mutate playback state.
     */
    fun canControlPlayback(isHost: Boolean, controlMode: ControlMode): Boolean {
        return when (controlMode) {
            ControlMode.HOST_ONLY -> isHost
            ControlMode.COLLABORATIVE -> true
        }
    }
}

/**
 * Invitation link generator and parser for Play Together deep links.
 */
object InviteLinkHelper {
    private const val PRVN_SCHEME = "prvn://playtogether"
    private const val WEB_BASE = "https://prvn.music/together"

    fun createDeepLink(roomCode: String, roomName: String): String {
        return "$PRVN_SCHEME?code=$roomCode"
    }

    fun createWebLink(roomCode: String, roomName: String): String {
        return "$WEB_BASE?code=$roomCode"
    }

    fun parseRoomCode(uri: String): String? {
        val codeMatch = Regex("[?&]code=([0-9]{4})").find(uri)
        return codeMatch?.groupValues?.get(1)
    }
}
