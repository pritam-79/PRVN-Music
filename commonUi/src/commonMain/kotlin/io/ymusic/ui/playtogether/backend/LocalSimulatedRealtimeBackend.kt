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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/**
 * In-memory real-time backend for Play Together (used cleanly for single-process testing & offline room previews).
 * Supports full authoritative playback sequences, request lifecycle, permissions, and snapshots.
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
        var playbackSeq: Long = 1L,
        var queueVersion: Long = 1L,
        var playbackStartTimestampUtc: Long = 0L,
        var isClosed: Boolean = false,
        var lastUpdatedUtc: Long = Clock.System.now().toEpochMilliseconds(),
    )

    private val roomsLock = Any()
    private val activeRooms = mutableMapOf<String, InternalRoom>()
    private val roomEventFlow = MutableSharedFlow<Pair<String, RoomEvent>>(extraBufferCapacity = 128)
    private val presenceFlows = mutableMapOf<String, MutableStateFlow<List<RoomMember>>>()
    private val rateLimiter = RateLimiter(maxRequests = 20, windowMs = 30_000L)

    override suspend fun listPublicRooms(): Result<List<RoomInfo>> {
        return synchronized(roomsLock) {
            val publicRooms = activeRooms.values
                .filter { !it.isClosed && it.info.visibility == RoomVisibility.PUBLIC }
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
        hostId: String,
        allowListenerRequests: Boolean,
    ): Result<RoomInfo> {
        if (!rateLimiter.tryAcquire()) {
            return Result.failure(Exception("Too many room creation attempts. Please wait ${rateLimiter.secondsUntilReset()}s."))
        }

        val cleanCode = code.trim()
        val cleanName = RoomSecurityPolicy.sanitizeRoomName(name)
        val cleanHost = RoomSecurityPolicy.sanitizeUserName(hostName)
        val now = Clock.System.now().toEpochMilliseconds()
        val resolvedHostId = hostId.ifBlank { "host_${Random.nextLong(1000, 99999)}" }
        val roomId = "room_${cleanCode}_${Random.nextLong(1000, 99999)}"

        val roomInfo = RoomInfo(
            id = roomId,
            code = cleanCode,
            name = cleanName,
            hostName = cleanHost,
            hostId = resolvedHostId,
            visibility = visibility,
            controlMode = controlMode,
            memberCount = 1,
            currentTrack = initialTrack,
            password = password?.trim()?.ifBlank { null },
            isClosed = false,
            createdUtc = now,
            allowListenerRequests = allowListenerRequests,
        )

        val hostMember = RoomMember(
            id = resolvedHostId,
            name = cleanHost,
            isHost = true,
            syncState = MemberSyncState.SYNCED,
            driftMs = 0L,
            lastSeenUtc = now,
        )

        val internalRoom = InternalRoom(
            info = roomInfo,
            hostId = resolvedHostId,
            members = mutableListOf(hostMember),
            queue = initialQueue.toMutableList(),
            currentTrack = initialTrack,
            isPlaying = initialIsPlaying,
            positionMs = initialPositionMs,
            playbackStartTimestampUtc = if (initialIsPlaying) now - initialPositionMs else 0L,
            lastUpdatedUtc = now,
        )

        synchronized(roomsLock) {
            // Remove older closed/same-code rooms locally
            activeRooms.entries.removeAll { it.value.info.code == cleanCode }
            activeRooms[roomId] = internalRoom
            presenceFlows[roomId] = MutableStateFlow(listOf(hostMember))
        }

        return Result.success(roomInfo)
    }

    override suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String?,
        preferredMemberId: String?,
    ): Result<JoinRoomResult> {
        val cleanCode = code.trim()
        if (!RoomSecurityPolicy.isValidRoomCode(cleanCode)) {
            return Result.failure(Exception("Invalid room code. Please enter a 4-digit room code."))
        }

        val cleanUser = RoomSecurityPolicy.sanitizeUserName(userName)
        val now = Clock.System.now().toEpochMilliseconds()

        val result = synchronized(roomsLock) {
            val room = activeRooms.values.firstOrNull { it.info.code == cleanCode }
                ?: return@synchronized Result.failure<Triple<JoinRoomResult, RoomMember, List<RoomMember>>>(
                    Exception("Room code \"$cleanCode\" not found or has ended."),
                )
            if (room.isClosed) {
                return@synchronized Result.failure(Exception("Room \"$cleanCode\" has been closed by the host."))
            }

            if (room.info.visibility == RoomVisibility.PRIVATE && !room.info.password.isNullOrBlank()) {
                val inputPw = password?.trim().orEmpty()
                if (inputPw != room.info.password) {
                    return@synchronized Result.failure(Exception("Incorrect room passcode."))
                }
            }

            val existing = preferredMemberId?.let { id -> room.members.indexOfFirst { it.id == id } } ?: -1
            val memberId = if (existing >= 0) room.members[existing].id else "member_${Random.nextLong(10000, 99999)}"
            val isHostMember = memberId == room.hostId
            val member = RoomMember(
                id = memberId,
                name = cleanUser,
                isHost = isHostMember,
                syncState = if (room.currentTrack != null) MemberSyncState.SYNCING else MemberSyncState.SYNCED,
                driftMs = 0L,
                lastSeenUtc = now,
            )
            if (existing >= 0) {
                room.members[existing] = member
            } else {
                room.members.add(member)
            }
            val membersCopy = room.members.toList()
            room.info = room.info.copy(memberCount = membersCopy.size)
            presenceFlows.getOrPut(room.info.id) { MutableStateFlow(emptyList()) }.value = membersCopy

            val calculatedPos = if (room.isPlaying && room.currentTrack != null && room.playbackStartTimestampUtc > 0L) {
                (now - room.playbackStartTimestampUtc).coerceAtLeast(0L)
            } else {
                room.positionMs
            }

            val joinResult = JoinRoomResult(
                room = room.info,
                isHost = isHostMember,
                currentMemberId = memberId,
                members = membersCopy,
                currentTrack = room.currentTrack,
                isPlaying = room.isPlaying,
                positionMs = calculatedPos,
                queue = room.queue.toList(),
                songRequests = room.songRequests.toList(),
                playbackSeq = room.playbackSeq,
                queueVersion = room.queueVersion,
                playbackStartTimestampUtc = room.playbackStartTimestampUtc,
                serverTimestampUtc = now,
            )
            Result.success(Triple(joinResult, member, membersCopy))
        }

        val (joinRes, joinedMember, membersCopy) = result.getOrElse { return Result.failure(it) }
        scope.launch {
            roomEventFlow.emit(joinRes.room.id to RoomEvent.MemberJoined(joinedMember))
            roomEventFlow.emit(joinRes.room.id to RoomEvent.MemberPresenceChanged(membersCopy))
        }
        return Result.success(joinRes)
    }

    override suspend fun leaveRoom(roomId: String, memberId: String): Result<Unit> {
        var leftMemberName = ""
        val updated = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.success(Unit)
            val idx = room.members.indexOfFirst { it.id == memberId }
            if (idx != -1) {
                leftMemberName = room.members[idx].name
                room.members.removeAt(idx)
                val list = room.members.toList()
                room.info = room.info.copy(memberCount = list.size.coerceAtLeast(1))
                presenceFlows[roomId]?.value = list
                list
            } else {
                null
            }
        }
        if (updated != null) {
            scope.launch {
                roomEventFlow.emit(roomId to RoomEvent.MemberLeft(memberId, leftMemberName))
                roomEventFlow.emit(roomId to RoomEvent.MemberPresenceChanged(updated))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun closeRoom(roomId: String, reason: String): Result<Unit> {
        synchronized(roomsLock) {
            activeRooms[roomId]?.isClosed = true
            activeRooms.remove(roomId)
            presenceFlows.remove(roomId)
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.RoomClosed(reason, deleted = false))
        }
        return Result.success(Unit)
    }

    override suspend fun deleteRoom(roomId: String, reason: String): Result<Unit> {
        synchronized(roomsLock) {
            activeRooms[roomId]?.isClosed = true
            activeRooms.remove(roomId)
            presenceFlows.remove(roomId)
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.RoomClosed(reason, deleted = true))
        }
        return Result.success(Unit)
    }

    override suspend fun updateRoomSettings(
        roomId: String,
        hostId: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
        allowListenerRequests: Boolean,
    ): Result<Unit> {
        synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            if (room.hostId != hostId) return Result.failure(Exception("Only host can update room settings."))
            room.info = room.info.copy(
                visibility = visibility,
                controlMode = controlMode,
                allowListenerRequests = allowListenerRequests,
            )
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.RoomSettingsChanged(visibility, controlMode, allowListenerRequests, hostId))
        }
        return Result.success(Unit)
    }

    override suspend fun kickMember(roomId: String, hostId: String, targetMemberId: String): Result<Unit> {
        val updatedMembers = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            if (room.hostId != hostId) return Result.failure(Exception("Only host can remove participants."))
            room.members.removeAll { it.id == targetMemberId && !it.isHost }
            val list = room.members.toList()
            room.info = room.info.copy(memberCount = list.size)
            presenceFlows[roomId]?.value = list
            list
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.MemberKicked(targetMemberId))
            roomEventFlow.emit(roomId to RoomEvent.MemberPresenceChanged(updatedMembers))
        }
        return Result.success(Unit)
    }

    override suspend fun sendPlaybackEvent(roomId: String, event: PlaybackEvent): Result<Unit> {
        val now = Clock.System.now().toEpochMilliseconds()
        val emitted = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            when (event.eventType) {
                PlaybackEventType.PLAY,
                PlaybackEventType.PAUSE,
                PlaybackEventType.SEEK,
                PlaybackEventType.TRACK_CHANGE,
                -> {
                    if (event.playbackSeq >= room.playbackSeq) {
                        room.playbackSeq = event.playbackSeq.coerceAtLeast(room.playbackSeq + 1L)
                        room.isPlaying = event.isPlaying
                        room.positionMs = event.positionMs
                        if (event.track != null || event.eventType == PlaybackEventType.TRACK_CHANGE) {
                            room.currentTrack = event.track
                            room.info = room.info.copy(currentTrack = event.track)
                        }
                        room.playbackStartTimestampUtc = if (event.playbackStartTimestampUtc > 0L) {
                            event.playbackStartTimestampUtc
                        } else if (event.isPlaying) {
                            now - event.positionMs
                        } else {
                            0L
                        }
                        room.lastUpdatedUtc = now
                        listOf(
                            RoomEvent.PlaybackUpdated(
                                isPlaying = room.isPlaying,
                                positionMs = room.positionMs,
                                track = room.currentTrack,
                                timestampUtc = now,
                                senderId = event.senderId,
                                playbackSeq = room.playbackSeq,
                                playbackStartTimestampUtc = room.playbackStartTimestampUtc,
                                eventId = event.eventId,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                }
                PlaybackEventType.QUEUE_UPDATE -> {
                    if (event.queueVersion >= room.queueVersion) {
                        room.queueVersion = event.queueVersion.coerceAtLeast(room.queueVersion + 1L)
                        room.queue.clear()
                        room.queue.addAll(event.queue)
                        if (event.track != null) {
                            room.currentTrack = event.track
                            room.info = room.info.copy(currentTrack = event.track)
                        }
                        room.lastUpdatedUtc = now
                        listOf(
                            RoomEvent.QueueChanged(
                                queue = room.queue.toList(),
                                currentTrack = room.currentTrack,
                                queueVersion = room.queueVersion,
                                senderId = event.senderId,
                                eventId = event.eventId,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        }
        scope.launch {
            emitted.forEach { ev -> roomEventFlow.emit(roomId to ev) }
        }
        return Result.success(Unit)
    }

    override suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
        memberName: String,
        isHost: Boolean,
    ): Result<Unit> {
        val now = Clock.System.now().toEpochMilliseconds()
        val updatedMembers = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.success(Unit)
            val index = room.members.indexOfFirst { it.id == memberId }
            if (index != -1) {
                val current = room.members[index]
                room.members[index] = current.copy(
                    name = memberName.ifBlank { current.name },
                    syncState = syncState,
                    driftMs = driftMs,
                    lastSeenUtc = now,
                )
            } else {
                room.members.add(
                    RoomMember(
                        id = memberId,
                        name = memberName.ifBlank { "Listener" },
                        isHost = isHost,
                        syncState = syncState,
                        driftMs = driftMs,
                        lastSeenUtc = now,
                    ),
                )
            }
            val list = room.members.toList()
            presenceFlows[roomId]?.value = list
            list
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.MemberPresenceChanged(updatedMembers))
        }
        return Result.success(Unit)
    }

    override suspend fun submitSongRequest(roomId: String, request: SongRequest): Result<Unit> {
        val (requestsList, finalReq) = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            if (!room.info.allowListenerRequests) {
                return Result.failure(Exception("Song requests are disabled by the host."))
            }
            val pendingCount = room.songRequests.count {
                it.requesterId == request.requesterId && it.status == SongRequestStatus.PENDING
            }
            if (pendingCount >= 5) {
                return Result.failure(Exception("Maximum 5 active pending requests allowed per listener."))
            }
            val withRoom = request.copy(roomId = roomId)
            val existingIdx = room.songRequests.indexOfFirst { it.id == withRoom.id }
            if (existingIdx >= 0) {
                room.songRequests[existingIdx] = withRoom
            } else {
                room.songRequests.add(withRoom)
            }
            room.songRequests.toList() to withRoom
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.SongRequestsChanged(requestsList, addedRequest = finalReq))
        }
        return Result.success(Unit)
    }

    override suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit> {
        val now = Clock.System.now().toEpochMilliseconds()
        val (requestsList, updatedReq) = synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.failure(Exception("Room not found."))
            val idx = room.songRequests.indexOfFirst { it.id == requestId }
            if (idx == -1) return Result.failure(Exception("Request not found."))
            val next = room.songRequests[idx].copy(status = status, updatedTimeMs = now)
            room.songRequests[idx] = next
            room.songRequests.toList() to next
        }
        scope.launch {
            roomEventFlow.emit(roomId to RoomEvent.SongRequestsChanged(requestsList, updatedRequest = updatedReq))
        }
        return Result.success(Unit)
    }

    override suspend fun fetchLatestRoomSnapshot(
        roomId: String,
        requesterMemberId: String,
        requesterName: String,
        isHost: Boolean,
    ): Result<JoinRoomResult> {
        val now = Clock.System.now().toEpochMilliseconds()
        return synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return@synchronized Result.failure(Exception("Room no longer exists."))
            if (room.isClosed) return@synchronized Result.failure(Exception("Room has been closed."))
            val pos = if (room.isPlaying && room.playbackStartTimestampUtc > 0L) {
                (now - room.playbackStartTimestampUtc).coerceAtLeast(0L)
            } else {
                room.positionMs
            }
            Result.success(
                JoinRoomResult(
                    room = room.info,
                    isHost = requesterMemberId == room.hostId || isHost,
                    currentMemberId = requesterMemberId,
                    members = room.members.toList(),
                    currentTrack = room.currentTrack,
                    isPlaying = room.isPlaying,
                    positionMs = pos,
                    queue = room.queue.toList(),
                    songRequests = room.songRequests.toList(),
                    playbackSeq = room.playbackSeq,
                    queueVersion = room.queueVersion,
                    playbackStartTimestampUtc = room.playbackStartTimestampUtc,
                    serverTimestampUtc = now,
                ),
            )
        }
    }

    override suspend fun measureServerClockOffset(): Result<SyncPingResult> {
        val now = Clock.System.now().toEpochMilliseconds()
        return Result.success(SyncPingResult(serverTimestampUtc = now, rttMs = 1L, clockOffsetMs = 0L))
    }

    override fun observeRoomEvents(roomId: String): Flow<RoomEvent> =
        roomEventFlow.filter { it.first == roomId }.map { it.second }

    override fun observePresence(roomId: String): Flow<List<RoomMember>> =
        synchronized(roomsLock) {
            presenceFlows.getOrPut(roomId) { MutableStateFlow(emptyList()) }.asStateFlow()
        }

    override fun isConnected(): Boolean = true
}
