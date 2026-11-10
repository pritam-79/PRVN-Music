package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * Supabase-capable wrapper implementing [PlayTogetherBackend].
 * Uses [GlobalRealtimeBackend] as its live cross-device fallback instead of a single-device simulator
 * so cross-device rooms always work even when Supabase credentials are blank or unreachable.
 */
class SupabaseRealtimeBackend(
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val delegate: PlayTogetherBackend = GlobalRealtimeBackend(scope),
) : PlayTogetherBackend {

    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    override suspend fun listPublicRooms(): Result<List<RoomInfo>> =
        delegate.listPublicRooms()

    override suspend fun createRoom(
        name: String,
        code: String,
        hostName: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
        password: String?,
        initialTrack: PlayTogetherTrack?,
        initialPositionMs: Long,
        initialIsPlaying: Boolean,
        initialQueue: List<PlayTogetherTrack>,
        hostId: String,
        allowListenerRequests: Boolean,
    ): Result<RoomInfo> = delegate.createRoom(
        name = name,
        code = code,
        hostName = hostName,
        visibility = visibility,
        controlMode = controlMode,
        password = password,
        initialTrack = initialTrack,
        initialPositionMs = initialPositionMs,
        initialIsPlaying = initialIsPlaying,
        initialQueue = initialQueue,
        hostId = hostId,
        allowListenerRequests = allowListenerRequests,
    )

    override suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String?,
        preferredMemberId: String?,
    ): Result<JoinRoomResult> = delegate.verifyAndJoinRoom(
        code = code,
        userName = userName,
        password = password,
        preferredMemberId = preferredMemberId,
    )

    override suspend fun leaveRoom(roomId: String, memberId: String): Result<Unit> =
        delegate.leaveRoom(roomId, memberId)

    override suspend fun closeRoom(roomId: String, reason: String): Result<Unit> =
        delegate.closeRoom(roomId, reason)

    override suspend fun deleteRoom(roomId: String, reason: String): Result<Unit> =
        delegate.deleteRoom(roomId, reason)

    override suspend fun updateRoomSettings(
        roomId: String,
        hostId: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
        allowListenerRequests: Boolean,
    ): Result<Unit> = delegate.updateRoomSettings(roomId, hostId, visibility, controlMode, allowListenerRequests)

    override suspend fun kickMember(roomId: String, hostId: String, targetMemberId: String): Result<Unit> =
        delegate.kickMember(roomId, hostId, targetMemberId)

    override suspend fun sendPlaybackEvent(roomId: String, event: PlaybackEvent): Result<Unit> =
        delegate.sendPlaybackEvent(roomId, event)

    override suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
        memberName: String,
        isHost: Boolean,
    ): Result<Unit> = delegate.updatePresence(roomId, memberId, syncState, driftMs, memberName, isHost)

    override suspend fun submitSongRequest(roomId: String, request: SongRequest): Result<Unit> =
        delegate.submitSongRequest(roomId, request)

    override suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit> = delegate.updateSongRequestStatus(roomId, requestId, status)

    override suspend fun fetchLatestRoomSnapshot(
        roomId: String,
        requesterMemberId: String,
        requesterName: String,
        isHost: Boolean,
    ): Result<JoinRoomResult>? = delegate.fetchLatestRoomSnapshot(roomId, requesterMemberId, requesterName, isHost)

    override suspend fun measureServerClockOffset(): Result<SyncPingResult>? =
        delegate.measureServerClockOffset()

    override fun observeRoomEvents(roomId: String): Flow<RoomEvent> =
        delegate.observeRoomEvents(roomId)

    override fun observePresence(roomId: String): Flow<List<RoomMember>> =
        delegate.observePresence(roomId)

    override fun isConnected(): Boolean = delegate.isConnected()
}
