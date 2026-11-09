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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Local in-memory and local-network relay for Play Together.
 * Contains no fake bots, simulated listeners, or mock rooms.
 * Manages genuinely created user rooms, presence, shared queue, and song requests.
 */
class LocalSimulatedRealtimeBackend(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : PlayTogetherBackend {

    private data class InternalRoom(
        var info: RoomInfo,
        val hostId: String,
        val members: MutableList<RoomMember> = mutableListOf(),
        val queue: MutableList<PlayTogetherTrack> = mutableListOf(),
        val songRequests: MutableList<SongRequest> = mutableListOf(),
        var currentTrack: PlayTogetherTrack? = null,
        var isPlaying: Boolean = false,
        var positionMs: Long = 0L,
        var lastUpdatedUtc: Long = Clock.System.now().toEpochMilliseconds(),
    )

    private val roomsLock = Any()
    private val activeRooms = mutableMapOf<String, InternalRoom>()
    private val roomEventFlow = MutableSharedFlow<Pair<String, RoomEvent>>(extraBufferCapacity = 64)
    private val presenceFlows = mutableMapOf<String, MutableStateFlow<List<RoomMember>>>()
    private val rateLimiter = RateLimiter(maxRequests = 10, windowMs = 30_000L)

    override suspend fun listPublicRooms(): Result<List<RoomInfo>> {
        return synchronized(roomsLock) {
            val publicRooms = activeRooms.values
                .filter { it.info.visibility == RoomVisibility.PUBLIC }
                .map { room ->
                    room.info.copy(
                        memberCount = room.members.size,
                        currentTrack = room.currentTrack,
                    )
                }
            Result.success(publicRooms)
        }
    }

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
    ): Result<RoomInfo> {
        if (!rateLimiter.tryAcquire()) {
            return Result.failure(Exception("Too many room creation attempts. Please wait ${rateLimiter.secondsUntilReset()}s."))
        }

        val cleanCode = code.trim()
        val cleanName = RoomSecurityPolicy.sanitizeRoomName(name)
        val cleanHost = RoomSecurityPolicy.sanitizeUserName(hostName)

        val roomId = "room_${cleanCode}_${Random.nextLong(1000, 9999)}"
        val hostId = "host_${Random.nextLong(1000, 9999)}"

        val roomInfo = RoomInfo(
            id = roomId,
            code = cleanCode,
            name = cleanName,
            hostName = cleanHost,
            visibility = visibility,
            controlMode = controlMode,
            memberCount = 1,
            currentTrack = initialTrack,
            password = password?.trim()?.ifBlank { null },
        )

        val hostMember = RoomMember(
            id = hostId,
            name = cleanHost,
            isHost = true,
            syncState = MemberSyncState.SYNCED,
            driftMs = 0L,
        )

        val internalRoom = InternalRoom(
            info = roomInfo,
            hostId = hostId,
            members = mutableListOf(hostMember),
            queue = initialQueue.toMutableList(),
            songRequests = mutableListOf(),
            currentTrack = initialTrack,
            isPlaying = initialIsPlaying,
            positionMs = initialPositionMs,
        )

        synchronized(roomsLock) {
            activeRooms[roomId] = internalRoom
            presenceFlows[roomId] = MutableStateFlow(listOf(hostMember))
        }

        return Result.success(roomInfo)
    }

    override suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String?,
    ): Result<JoinRoomResult> {
        if (!rateLimiter.tryAcquire()) {
            return Result.failure(Exception("Too many join attempts. Please wait ${rateLimiter.secondsUntilReset()}s."))
        }

        val cleanCode = code.trim()
        val cleanUser = RoomSecurityPolicy.sanitizeUserName(userName)

        val room = synchronized(roomsLock) {
            activeRooms.values.firstOrNull { it.info.code == cleanCode }
        } ?: return Result.failure(Exception("Room code '$cleanCode' not found. Verify the code with the host."))

        if (!room.info.password.isNullOrBlank()) {
            val suppliedPass = password?.trim()
            if (suppliedPass != room.info.password) {
                return Result.failure(Exception("Incorrect password for private room."))
            }
        }

        val memberId = "member_${Random.nextLong(1000, 9999)}"
        val newMember = RoomMember(
            id = memberId,
            name = cleanUser,
            isHost = false,
            syncState = MemberSyncState.SYNCING,
            driftMs = 0L,
        )

        synchronized(roomsLock) {
            room.members.add(newMember)
            room.info = room.info.copy(memberCount = room.members.size)
            presenceFlows[room.info.id]?.value = room.members.toList()
        }

        scope.launch {
            roomEventFlow.emit(room.info.id to RoomEvent.MemberJoined(newMember))
            roomEventFlow.emit(room.info.id to RoomEvent.MemberPresenceChanged(room.members.toList()))
        }

        return Result.success(
            JoinRoomResult(
                room = room.info,
                isHost = false,
                currentMemberId = memberId,
                members = room.members.toList(),
                currentTrack = room.currentTrack,
                isPlaying = room.isPlaying,
                positionMs = room.positionMs,
                queue = room.queue.toList(),
                songRequests = room.songRequests.toList(),
            ),
        )
    }

    override suspend fun leaveRoom(roomId: String, memberId: String): Result<Unit> {
        val (wasHost, leftMember) = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.success(Unit)
            val member = room.members.firstOrNull { it.id == memberId }
            val isHost = member?.isHost == true
            room.members.removeAll { it.id == memberId }
            room.info = room.info.copy(memberCount = room.members.size)
            presenceFlows[roomId]?.value = room.members.toList()

            if (isHost || room.members.isEmpty()) {
                activeRooms.remove(roomId)
                presenceFlows.remove(roomId)
            }
            Pair(isHost, member)
        }

        scope.launch {
            if (wasHost) {
                roomEventFlow.emit(roomId to RoomEvent.RoomClosed("The host closed the room."))
            } else if (leftMember != null) {
                roomEventFlow.emit(roomId to RoomEvent.MemberLeft(leftMember.id, leftMember.name))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun closeRoom(roomId: String, reason: String): Result<Unit> {
        synchronized(roomsLock) {
            activeRooms.remove(roomId)
            presenceFlows.remove(roomId)
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.RoomClosed(reason))
        }
        return Result.success(Unit)
    }

    override suspend fun sendPlaybackEvent(roomId: String, event: PlaybackEvent): Result<Unit> {
        val room = synchronized(roomsLock) { activeRooms[roomId] } ?: return Result.failure(Exception("Room not found."))

        synchronized(roomsLock) {
            when (event.eventType) {
                PlaybackEventType.PLAY -> {
                    room.isPlaying = true
                    room.positionMs = event.positionMs
                }
                PlaybackEventType.PAUSE -> {
                    room.isPlaying = false
                    room.positionMs = event.positionMs
                }
                PlaybackEventType.SEEK -> {
                    room.positionMs = event.positionMs
                }
                PlaybackEventType.TRACK_CHANGE -> {
                    room.currentTrack = event.track
                    room.positionMs = event.positionMs
                    room.isPlaying = event.isPlaying
                    if (event.queue.isNotEmpty()) {
                        room.queue.clear()
                        room.queue.addAll(event.queue)
                    }
                }
                PlaybackEventType.QUEUE_UPDATE -> {
                    room.queue.clear()
                    room.queue.addAll(event.queue)
                    if (event.track != null) {
                        room.currentTrack = event.track
                    }
                }
                else -> Unit
            }
            room.lastUpdatedUtc = event.timestampUtc
        }

        scope.launch {
            when (event.eventType) {
                PlaybackEventType.PLAY, PlaybackEventType.PAUSE, PlaybackEventType.SEEK, PlaybackEventType.TRACK_CHANGE -> {
                    roomEventFlow.emit(
                        roomId to RoomEvent.PlaybackUpdated(
                            isPlaying = room.isPlaying,
                            positionMs = room.positionMs,
                            track = room.currentTrack,
                            timestampUtc = event.timestampUtc,
                            senderId = event.senderId,
                        ),
                    )
                }
                PlaybackEventType.QUEUE_UPDATE -> {
                    roomEventFlow.emit(
                        roomId to RoomEvent.QueueChanged(
                            queue = room.queue.toList(),
                            currentTrack = room.currentTrack,
                        ),
                    )
                }
                else -> Unit
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
    ): Result<Unit> {
        val updatedMembers = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.success(Unit)
            val index = room.members.indexOfFirst { it.id == memberId }
            if (index != -1) {
                val current = room.members[index]
                room.members[index] = current.copy(syncState = syncState, driftMs = driftMs)
                presenceFlows[roomId]?.value = room.members.toList()
                room.members.toList()
            } else null
        }

        if (updatedMembers != null) {
            scope.launch {
                roomEventFlow.emit(roomId to RoomEvent.MemberPresenceChanged(updatedMembers))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun submitSongRequest(roomId: String, request: SongRequest): Result<Unit> {
        val requestsList = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            // Rate limit pending requests per user: max 5
            val pendingCount = room.songRequests.count { it.requesterId == request.requesterId && it.status == SongRequestStatus.PENDING }
            if (pendingCount >= 5) {
                return Result.failure(Exception("You have reached the maximum limit of 5 pending requests. Please wait for the host to review."))
            }
            room.songRequests.add(request)
            room.songRequests.toList()
        }

        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.SongRequestsChanged(requestsList))
        }
        return Result.success(Unit)
    }

    override suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit> {
        val (requestsList, approvedTrack, queueList) = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            val idx = room.songRequests.indexOfFirst { it.id == requestId }
            if (idx == -1) return Result.failure(Exception("Request not found."))

            val updatedReq = room.songRequests[idx].copy(status = status)
            room.songRequests[idx] = updatedReq

            var trackAdded: PlayTogetherTrack? = null
            if (status == SongRequestStatus.APPROVED) {
                trackAdded = updatedReq.track
                if (room.currentTrack == null) {
                    room.currentTrack = trackAdded
                    room.isPlaying = true
                    room.positionMs = 0L
                } else {
                    room.queue.add(trackAdded)
                }
            }
            Triple(room.songRequests.toList(), trackAdded, room.queue.toList())
        }

        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.SongRequestsChanged(requestsList))
            if (approvedTrack != null) {
                roomEventFlow.emit(roomId to RoomEvent.QueueChanged(queueList, null))
            }
        }
        return Result.success(Unit)
    }

    override fun observeRoomEvents(roomId: String): Flow<RoomEvent> {
        return roomEventFlow
            .filter { it.first == roomId }
            .map { it.second }
    }

    override fun observePresence(roomId: String): Flow<List<RoomMember>> {
        return synchronized(roomsLock) {
            presenceFlows.getOrPut(roomId) { MutableStateFlow(emptyList()) }.asStateFlow()
        }
    }

    override fun isConnected(): Boolean = true
}
