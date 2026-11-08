package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.random.Random

/**
 * Stateful in-memory and simulated network backend for Play Together.
 * Provides immediate out-of-the-box real-time presence, 4-digit code verification,
 * rate limiting, and playback broadcast without requiring external cloud credentials.
 */
class LocalSimulatedRealtimeBackend(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : PlayTogetherBackend {

    private data class InternalRoom(
        val info: RoomInfo,
        val hostId: String,
        val members: MutableList<RoomMember> = mutableListOf(),
        val queue: MutableList<PlayTogetherTrack> = mutableListOf(),
        var currentTrack: PlayTogetherTrack? = null,
        var isPlaying: Boolean = false,
        var positionMs: Long = 0L,
        var lastUpdatedUtc: Long = Clock.System.now().toEpochMilliseconds(),
    )

    private val roomsLock = Any()
    private val activeRooms = mutableMapOf<String, InternalRoom>()
    private val roomEventFlow = MutableSharedFlow<Pair<String, RoomEvent>>(extraBufferCapacity = 64)
    private val presenceFlows = mutableMapOf<String, MutableStateFlow<List<RoomMember>>>()
    private val rateLimiter = RateLimiter(maxRequests = 5, windowMs = 30_000L)
    private var simulatedPeerJob: Job? = null

    init {
        seedInitialRooms()
        startSimulatedPeerHeartbeat()
    }

    private fun seedInitialRooms() {
        synchronized(roomsLock) {
            val room1 = InternalRoom(
                info = RoomInfo(
                    id = "room_cosmic",
                    code = "4821",
                    name = "PRVN Cosmic Lounge",
                    hostName = "Aria",
                    visibility = RoomVisibility.PUBLIC,
                    controlMode = ControlMode.COLLABORATIVE,
                    memberCount = 3,
                    currentTrack = PlayTogetherTrack(
                        id = "track_1",
                        title = "Midnight City Lights",
                        artist = "PRVN Sound Collective",
                        durationMs = 240000L,
                        addedBy = "Aria",
                    ),
                ),
                hostId = "host_aria",
                members = mutableListOf(
                    RoomMember("host_aria", "Aria", isHost = true, syncState = MemberSyncState.SYNCED, driftMs = 0L),
                    RoomMember("peer_kai", "Kai", isHost = false, syncState = MemberSyncState.SYNCED, driftMs = 12L),
                    RoomMember("peer_sora", "Sora", isHost = false, syncState = MemberSyncState.BUFFERING, driftMs = 45L),
                ),
                queue = mutableListOf(
                    PlayTogetherTrack("queue_1", "Cosmic Voyager", "Solaris", durationMs = 195000L, addedBy = "Aria"),
                    PlayTogetherTrack("queue_2", "Violet Haze", "Echo Station", durationMs = 230000L, addedBy = "Kai"),
                ),
                currentTrack = PlayTogetherTrack(
                    id = "track_1",
                    title = "Midnight City Lights",
                    artist = "PRVN Sound Collective",
                    durationMs = 240000L,
                    addedBy = "Aria",
                ),
                isPlaying = true,
                positionMs = 42000L,
            )

            val room2 = InternalRoom(
                info = RoomInfo(
                    id = "room_synth",
                    code = "7294",
                    name = "Synthwave & Lo-Fi Waves",
                    hostName = "NeonK",
                    visibility = RoomVisibility.PUBLIC,
                    controlMode = ControlMode.HOST_ONLY,
                    memberCount = 2,
                    currentTrack = PlayTogetherTrack(
                        id = "track_2",
                        title = "Retrograde Horizon",
                        artist = "Kavinsky & The Midnight",
                        durationMs = 195000L,
                        addedBy = "NeonK",
                    ),
                ),
                hostId = "host_neonk",
                members = mutableListOf(
                    RoomMember("host_neonk", "NeonK", isHost = true, syncState = MemberSyncState.SYNCED, driftMs = 0L),
                    RoomMember("peer_mira", "Mira", isHost = false, syncState = MemberSyncState.SYNCED, driftMs = 18L),
                ),
                queue = mutableListOf(
                    PlayTogetherTrack("queue_3", "Neon Drive", "Perturbator", durationMs = 210000L, addedBy = "NeonK"),
                ),
                currentTrack = PlayTogetherTrack(
                    id = "track_2",
                    title = "Retrograde Horizon",
                    artist = "Kavinsky & The Midnight",
                    durationMs = 195000L,
                    addedBy = "NeonK",
                ),
                isPlaying = true,
                positionMs = 15000L,
            )

            activeRooms[room1.info.id] = room1
            activeRooms[room2.info.id] = room2
        }
    }

    private fun startSimulatedPeerHeartbeat() {
        simulatedPeerJob?.cancel()
        simulatedPeerJob = scope.launch {
            while (isActive) {
                delay(6000L)
                synchronized(roomsLock) {
                    activeRooms.values.forEach { room ->
                        val updated = room.members.map { m ->
                            if (!m.isHost) {
                                val jitter = Random.nextLong(-4L, 5L)
                                val newDrift = (m.driftMs + jitter).coerceIn(4L, 85L)
                                m.copy(driftMs = newDrift)
                            } else m
                        }
                        room.members.clear()
                        room.members.addAll(updated)
                        presenceFlows[room.info.id]?.value = updated
                    }
                }
            }
        }
    }

    override suspend fun listPublicRooms(): Result<List<RoomInfo>> {
        delay(120L) // Simulate minimal network roundtrip
        synchronized(roomsLock) {
            val publicRooms = activeRooms.values
                .filter { it.info.visibility == RoomVisibility.PUBLIC }
                .map { it.info.copy(memberCount = it.members.size, currentTrack = it.currentTrack) }
            return Result.success(publicRooms)
        }
    }

    override suspend fun createRoom(
        name: String,
        code: String,
        hostName: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
    ): Result<RoomInfo> {
        delay(150L)
        if (!rateLimiter.tryAcquire()) {
            val waitSec = rateLimiter.secondsUntilReset()
            return Result.failure(IllegalStateException("Rate limit exceeded. Please wait $waitSec seconds."))
        }

        if (!RoomSecurityPolicy.isValidRoomCode(code)) {
            return Result.failure(IllegalArgumentException("Room code must be exactly 4 numeric digits (e.g. 1234)."))
        }

        val cleanName = RoomSecurityPolicy.sanitizeRoomName(name)
        val cleanHost = RoomSecurityPolicy.sanitizeUserName(hostName)

        synchronized(roomsLock) {
            if (activeRooms.values.any { it.info.code == code }) {
                return Result.failure(IllegalStateException("Room code $code is already in use. Please select a different 4-digit code."))
            }

            val roomId = "room_${Random.nextLong(100000, 999999)}"
            val hostMemberId = "host_${Random.nextLong(1000, 9999)}"

            val seedTrack = PlayTogetherTrack(
                id = "track_seed_${Random.nextLong(100, 999)}",
                title = "PRVN Resonance",
                artist = "PRVN Sound Collective",
                durationMs = 214000L,
                addedBy = cleanHost,
            )

            val roomInfo = RoomInfo(
                id = roomId,
                code = code,
                name = cleanName,
                hostName = cleanHost,
                visibility = visibility,
                controlMode = controlMode,
                memberCount = 1,
                currentTrack = seedTrack,
            )

            val hostMember = RoomMember(
                id = hostMemberId,
                name = cleanHost,
                isHost = true,
                syncState = MemberSyncState.SYNCED,
                driftMs = 0L,
            )

            val internalRoom = InternalRoom(
                info = roomInfo,
                hostId = hostMemberId,
                members = mutableListOf(hostMember),
                queue = mutableListOf(),
                currentTrack = seedTrack,
                isPlaying = true,
                positionMs = 0L,
            )

            activeRooms[roomId] = internalRoom
            getOrCreatePresenceFlow(roomId).value = listOf(hostMember)

            return Result.success(roomInfo)
        }
    }

    override suspend fun verifyAndJoinRoom(code: String, userName: String): Result<JoinRoomResult> {
        delay(180L)
        if (!rateLimiter.tryAcquire()) {
            val waitSec = rateLimiter.secondsUntilReset()
            return Result.failure(IllegalStateException("Too many join attempts. Please wait $waitSec seconds."))
        }

        if (!RoomSecurityPolicy.isValidRoomCode(code)) {
            return Result.failure(IllegalArgumentException("Invalid room code: code must be exactly 4 numeric digits."))
        }

        val cleanUser = RoomSecurityPolicy.sanitizeUserName(userName)

        synchronized(roomsLock) {
            val room = activeRooms.values.firstOrNull { it.info.code == code }
                ?: return Result.failure(NoSuchElementException("No room found with code '$code'. Check the 4-digit code and try again."))

            // Check room capacity limit (max 20 members per synchronized session)
            if (room.members.size >= 20) {
                return Result.failure(IllegalStateException("Room '${room.info.name}' is currently at maximum capacity (20 listeners)."))
            }

            val newMemberId = "listener_${Random.nextLong(10000, 99999)}"
            val newMember = RoomMember(
                id = newMemberId,
                name = cleanUser,
                isHost = false,
                syncState = MemberSyncState.SYNCED,
                driftMs = Random.nextLong(8L, 25L),
            )

            room.members.add(newMember)
            val updatedList = room.members.toList()
            getOrCreatePresenceFlow(room.info.id).value = updatedList

            scope.launch {
                roomEventFlow.emit(room.info.id to RoomEvent.MemberJoined(newMember))
                roomEventFlow.emit(room.info.id to RoomEvent.MemberPresenceChanged(updatedList))
            }

            val elapsedSinceUpdate = Clock.System.now().toEpochMilliseconds() - room.lastUpdatedUtc
            val adjustedPosition = if (room.isPlaying && room.currentTrack != null) {
                (room.positionMs + elapsedSinceUpdate).coerceAtMost(room.currentTrack!!.durationMs)
            } else {
                room.positionMs
            }

            return Result.success(
                JoinRoomResult(
                    room = room.info.copy(memberCount = updatedList.size, currentTrack = room.currentTrack),
                    isHost = false,
                    currentMemberId = newMemberId,
                    members = updatedList,
                    currentTrack = room.currentTrack,
                    isPlaying = room.isPlaying,
                    positionMs = adjustedPosition,
                    queue = room.queue.toList(),
                )
            )
        }
    }

    override suspend fun leaveRoom(roomId: String, memberId: String): Result<Unit> {
        synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.success(Unit)
            val member = room.members.firstOrNull { it.id == memberId }
            room.members.removeAll { it.id == memberId }
            val updatedList = room.members.toList()
            getOrCreatePresenceFlow(roomId).value = updatedList

            if (member != null) {
                scope.launch {
                    roomEventFlow.emit(roomId to RoomEvent.MemberLeft(memberId, member.name))
                    roomEventFlow.emit(roomId to RoomEvent.MemberPresenceChanged(updatedList))
                }
            }

            // If host left and no members remain, close room
            if (room.members.isEmpty() || member?.isHost == true && room.members.none { it.isHost }) {
                activeRooms.remove(roomId)
                scope.launch {
                    roomEventFlow.emit(roomId to RoomEvent.RoomClosed("The host closed the room session."))
                }
            }

            return Result.success(Unit)
        }
    }

    override suspend fun sendPlaybackEvent(roomId: String, event: PlaybackEvent): Result<Unit> {
        synchronized(roomsLock) {
            val room = activeRooms[roomId]
                ?: return Result.failure(NoSuchElementException("Room $roomId no longer exists."))

            // Check host authority if HOST_ONLY
            if (!RoomSecurityPolicy.canControlPlayback(event.senderId == room.hostId, room.info.controlMode)) {
                return Result.failure(IllegalAccessException("Only the room host is authorized to control playback."))
            }

            room.lastUpdatedUtc = event.timestampUtc
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
                    room.positionMs = 0L
                    room.isPlaying = true
                }
                PlaybackEventType.QUEUE_UPDATE -> {
                    room.queue.clear()
                    room.queue.addAll(event.queue)
                    if (event.track != null) {
                        room.currentTrack = event.track
                    }
                }
                PlaybackEventType.SYNC_PING -> Unit
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
                            )
                        )
                    }
                    PlaybackEventType.QUEUE_UPDATE -> {
                        roomEventFlow.emit(
                            roomId to RoomEvent.QueueChanged(
                                queue = room.queue.toList(),
                                currentTrack = room.currentTrack,
                            )
                        )
                    }
                    PlaybackEventType.SYNC_PING -> Unit
                }
            }

            return Result.success(Unit)
        }
    }

    override suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
    ): Result<Unit> {
        synchronized(roomsLock) {
            val room = activeRooms[roomId] ?: return Result.success(Unit)
            val index = room.members.indexOfFirst { it.id == memberId }
            if (index >= 0) {
                val current = room.members[index]
                room.members[index] = current.copy(syncState = syncState, driftMs = driftMs)
                val updated = room.members.toList()
                getOrCreatePresenceFlow(roomId).value = updated
            }
            return Result.success(Unit)
        }
    }

    override fun observeRoomEvents(roomId: String): Flow<RoomEvent> {
        return roomEventFlow.asSharedFlow()
            .filter { it.first == roomId }
            .let { shared ->
                kotlinx.coroutines.flow.flow {
                    shared.collect { pair -> emit(pair.second) }
                }
            }
    }

    override fun observePresence(roomId: String): Flow<List<RoomMember>> {
        return getOrCreatePresenceFlow(roomId).asStateFlow()
    }

    override fun isConnected(): Boolean = true

    private fun getOrCreatePresenceFlow(roomId: String): MutableStateFlow<List<RoomMember>> {
        return presenceFlows.getOrPut(roomId) {
            val currentMembers = synchronized(roomsLock) {
                activeRooms[roomId]?.members?.toList() ?: emptyList()
            }
            MutableStateFlow(currentMembers)
        }
    }
}
