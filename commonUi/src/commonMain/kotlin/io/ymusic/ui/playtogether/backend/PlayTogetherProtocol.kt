package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
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
    SONG_REQUEST_ADD,
    SONG_REQUEST_STATUS,
    ROOM_SETTINGS_UPDATE,
    MEMBER_KICK,
}

/**
 * Real-time playback synchronization payload sent over the relay channel.
 */
data class PlaybackEvent(
    val roomId: String,
    val senderId: String,
    val eventType: PlaybackEventType,
    val track: PlayTogetherTrack? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val queue: List<PlayTogetherTrack> = emptyList(),
    val songRequests: List<SongRequest> = emptyList(),
    val playbackSeq: Long = 0L,
    val queueVersion: Long = 0L,
    val playbackStartTimestampUtc: Long = 0L,
    val visibility: RoomVisibility? = null,
    val controlMode: ControlMode? = null,
    val allowListenerRequests: Boolean? = null,
    val targetMemberId: String? = null,
    val eventId: String = "",
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
        val playbackSeq: Long = 0L,
        val playbackStartTimestampUtc: Long = 0L,
        val eventId: String = "",
    ) : RoomEvent

    data class QueueChanged(
        val queue: List<PlayTogetherTrack>,
        val currentTrack: PlayTogetherTrack?,
        val queueVersion: Long = 0L,
        val senderId: String = "",
        val eventId: String = "",
    ) : RoomEvent

    data class SongRequestsChanged(
        val requests: List<SongRequest>,
        val addedRequest: SongRequest? = null,
        val updatedRequest: SongRequest? = null,
        val eventId: String = "",
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

    data class MemberKicked(
        val targetMemberId: String,
        val reason: String = "You were removed from the room by the host.",
    ) : RoomEvent

    data class RoomSettingsChanged(
        val visibility: RoomVisibility,
        val controlMode: ControlMode,
        val allowListenerRequests: Boolean,
        val hostId: String = "",
    ) : RoomEvent

    data class RoomClosed(
        val reason: String,
        val deleted: Boolean = false,
    ) : RoomEvent

    data class Error(
        val message: String,
    ) : RoomEvent
}

/**
 * Clock synchronization probe result.
 */
data class SyncPingResult(
    val serverTimestampUtc: Long,
    val rttMs: Long,
    val clockOffsetMs: Long,
)

/**
 * Result returned upon successfully verifying and joining (or reconnecting to) a room.
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
    val songRequests: List<SongRequest> = emptyList(),
    val playbackSeq: Long = 0L,
    val queueVersion: Long = 0L,
    val playbackStartTimestampUtc: Long = 0L,
    val serverTimestampUtc: Long = Clock.System.now().toEpochMilliseconds(),
    val measuredRttMs: Long = 0L,
)

/**
 * Sliding-window rate limiter to prevent brute-forcing 4-digit room codes or spamming requests.
 */
class RateLimiter(
    private val maxRequests: Int = 8,
    private val windowMs: Long = 30_000L,
) {
    private val timestamps = mutableListOf<Long>()

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

    fun isValidRoomCode(code: String): Boolean {
        return FOUR_DIGIT_REGEX.matches(code.trim())
    }

    fun sanitizeRoomName(name: String): String {
        val trimmed = name.trim().take(40)
        return trimmed.ifBlank { "PRVN Music Lounge" }
    }

    fun sanitizeUserName(name: String): String {
        val trimmed = name.trim().take(25)
        return trimmed.ifBlank { "Listener" }
    }

    fun canControlPlayback(isHost: Boolean, controlMode: ControlMode): Boolean {
        return when (controlMode) {
            ControlMode.HOST_ONLY -> isHost
            ControlMode.COLLABORATIVE -> true
        }
    }
}

/**
 * Invitation link generator and parser for Play Together deep links (`prvn://` & `ymusic://` & https).
 */
object InviteLinkHelper {
    private const val PRVN_SCHEME = "prvn://playtogether"
    private const val WEB_BASE = "https://prvn.music/together"

    fun createDeepLink(roomCode: String, @Suppress("UNUSED_PARAMETER") roomName: String): String {
        return "$PRVN_SCHEME?code=${roomCode.trim()}"
    }

    fun createWebLink(roomCode: String, @Suppress("UNUSED_PARAMETER") roomName: String): String {
        return "$WEB_BASE?code=${roomCode.trim()}"
    }

    fun parseRoomCode(uri: String): String? {
        val trimmed = uri.trim()
        val queryMatch = Regex("[?&]code=([0-9]{4})(?:&|$)").find(trimmed)
        if (queryMatch != null) return queryMatch.groupValues[1]
        val pathMatch = Regex("(?:playtogether|together|room)/([0-9]{4})(?:[/?#]|$)").find(trimmed)
        if (pathMatch != null) return pathMatch.groupValues[1]
        val rawCodeMatch = Regex("^([0-9]{4})$").find(trimmed)
        return rawCodeMatch?.groupValues?.get(1)
    }
}

/** Parse status string including legacy v0.06–v0.10 `"APPROVED"`. */
fun parseSongRequestStatus(raw: String?): SongRequestStatus {
    val clean = raw?.trim()?.uppercase() ?: return SongRequestStatus.PENDING
    return when (clean) {
        "PENDING" -> SongRequestStatus.PENDING
        "ACCEPTED" -> SongRequestStatus.ACCEPTED
        "APPROVED", "ADDED_TO_QUEUE", "QUEUED" -> SongRequestStatus.ADDED_TO_QUEUE
        "PLAYED" -> SongRequestStatus.PLAYED
        "REJECTED" -> SongRequestStatus.REJECTED
        "CANCELLED", "CANCELED" -> SongRequestStatus.CANCELLED
        else -> SongRequestStatus.PENDING
    }
}
