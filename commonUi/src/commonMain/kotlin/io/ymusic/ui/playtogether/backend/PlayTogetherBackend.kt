package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
import kotlinx.coroutines.flow.Flow

/**
 * Common backend contract for Play Together real-time room sessions, presence, and sync.
 */
interface PlayTogetherBackend {

    /**
     * Queries available public active rooms for the discovery feed.
     * Excludes PRIVATE, INVITE_ONLY, and closed/deleted rooms.
     */
    suspend fun listPublicRooms(): Result<List<RoomInfo>>

    /**
     * Creates a new synchronized room with the designated visibility and host controls.
     */
    suspend fun createRoom(
        name: String,
        code: String,
        hostName: String,
        visibility: RoomVisibility = RoomVisibility.PUBLIC,
        controlMode: ControlMode = ControlMode.HOST_ONLY,
        password: String? = null,
        initialTrack: PlayTogetherTrack? = null,
        initialPositionMs: Long = 0L,
        initialIsPlaying: Boolean = false,
        initialQueue: List<PlayTogetherTrack> = emptyList(),
        hostId: String = "",
        allowListenerRequests: Boolean = true,
    ): Result<RoomInfo>

    /**
     * Verifies room code, checks closed/deleted status, checks password (if private), and establishes session.
     */
    suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String? = null,
        preferredMemberId: String? = null,
    ): Result<JoinRoomResult>

    /**
     * Gracefully disconnects the member and updates presence on the server.
     */
    suspend fun leaveRoom(
        roomId: String,
        memberId: String,
    ): Result<Unit>

    /**
     * Closes the room session and notifies all connected participants (host action).
     */
    suspend fun closeRoom(
        roomId: String,
        reason: String = "Host ended the room session.",
    ): Result<Unit>

    /**
     * Permanently deletes the room and purges it from public discovery (host action).
     */
    suspend fun deleteRoom(
        roomId: String,
        reason: String = "Room deleted by host.",
    ): Result<Unit> = closeRoom(roomId, reason)

    /**
     * Updates live room settings (visibility, controlMode, allowListenerRequests).
     */
    suspend fun updateRoomSettings(
        roomId: String,
        hostId: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
        allowListenerRequests: Boolean,
    ): Result<Unit> = Result.success(Unit)

    /**
     * Kicks a participant out of the room (host action).
     */
    suspend fun kickMember(
        roomId: String,
        hostId: String,
        targetMemberId: String,
    ): Result<Unit> = Result.success(Unit)

    /**
     * Dispatches a playback event (play, pause, seek, track change, queue mutate) to peers.
     */
    suspend fun sendPlaybackEvent(
        roomId: String,
        event: PlaybackEvent,
    ): Result<Unit>

    /**
     * Updates local heartbeat, member name, sync status, and measured clock drift on the server.
     */
    suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
        memberName: String = "",
        isHost: Boolean = false,
    ): Result<Unit>

    /**
     * Submits a new song request to the room.
     */
    suspend fun submitSongRequest(
        roomId: String,
        request: SongRequest,
    ): Result<Unit>

    /**
     * Updates status of a song request (ACCEPTED, ADDED_TO_QUEUE, PLAYED, REJECTED, CANCELLED).
     */
    suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit>

    /**
     * Re-fetches the latest authoritative state snapshot for reconnection recovery.
     */
    suspend fun fetchLatestRoomSnapshot(
        roomId: String,
        requesterMemberId: String,
        requesterName: String,
        isHost: Boolean,
    ): Result<JoinRoomResult>? = null

    /**
     * Measures server clock offset and round-trip time for accurate multi-device sync.
     */
    suspend fun measureServerClockOffset(): Result<SyncPingResult>? = null

    /**
     * Subscribes to real-time incoming room events (playback, queue changes, song requests, member joins/leaves).
     */
    fun observeRoomEvents(roomId: String): Flow<RoomEvent>

    /**
     * Subscribes to the live member presence list for an active room.
     */
    fun observePresence(roomId: String): Flow<List<RoomMember>>

    /**
     * Current connection status indicator.
     */
    fun isConnected(): Boolean
}
