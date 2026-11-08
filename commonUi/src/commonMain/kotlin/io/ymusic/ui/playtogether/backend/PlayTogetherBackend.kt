package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
import kotlinx.coroutines.flow.Flow

/**
 * Common backend contract for Play Together real-time room sessions, presence, and sync.
 * Compatible with Supabase Realtime (WebSockets) and local in-memory relay.
 */
interface PlayTogetherBackend {

    /**
     * Queries available public rooms for the discovery feed.
     * Excludes PRIVATE and INVITE_ONLY rooms.
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
    ): Result<RoomInfo>

    /**
     * Verifies room code, checks capacity, checks password (if private), and establishes session.
     */
    suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String? = null,
    ): Result<JoinRoomResult>

    /**
     * Gracefully disconnects the member and updates presence on the server.
     */
    suspend fun leaveRoom(
        roomId: String,
        memberId: String,
    ): Result<Unit>

    /**
     * Dispatches a playback event (play, pause, seek, track change, queue mutate) to peers.
     */
    suspend fun sendPlaybackEvent(
        roomId: String,
        event: PlaybackEvent,
    ): Result<Unit>

    /**
     * Updates local heartbeat, sync status, and measured clock drift on the server.
     */
    suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
    ): Result<Unit>

    /**
     * Submits a new song request to the room.
     */
    suspend fun submitSongRequest(
        roomId: String,
        request: SongRequest,
    ): Result<Unit>

    /**
     * Updates status of a song request (APPROVED, REJECTED).
     */
    suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit>

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
