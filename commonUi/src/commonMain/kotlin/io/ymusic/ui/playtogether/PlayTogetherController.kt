package io.ymusic.ui.playtogether

import io.ymusic.ui.playtogether.backend.PlaybackEvent
import io.ymusic.ui.playtogether.backend.PlaybackEventType
import io.ymusic.ui.playtogether.backend.PlayTogetherBackend
import io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider
import io.ymusic.ui.playtogether.backend.RoomEvent
import io.ymusic.ui.playtogether.backend.RoomSecurityPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Controller managing Play Together rooms, member synchronization, shared queue,
 * in-room search, and song request management.
 */
class PlayTogetherController(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    val backend: PlayTogetherBackend = PlayTogetherBackendProvider.current,
) {
    private val _state = MutableStateFlow<PlayTogetherState>(
        PlayTogetherState.Lobby(isBusy = false),
    )
    val state: StateFlow<PlayTogetherState> = _state.asStateFlow()

    private var playbackTickerJob: Job? = null
    private var roomEventsJob: Job? = null
    private var presenceJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        refreshPublicRooms()
    }

    /**
     * Refreshes the public room discovery list from the backend.
     */
    fun refreshPublicRooms() {
        scope.launch {
            backend.listPublicRooms().fold(
                onSuccess = { rooms ->
                    _state.update { current ->
                        if (current is PlayTogetherState.Lobby) {
                            current.copy(activeRooms = rooms, isBusy = false, errorMessage = null)
                        } else current
                    }
                },
                onFailure = { err ->
                    _state.update { current ->
                        if (current is PlayTogetherState.Lobby) {
                            current.copy(isBusy = false, errorMessage = err.message)
                        } else current
                    }
                },
            )
        }
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                _state.update { current ->
                    if (current is PlayTogetherState.InRoom && current.isPlaying && current.currentTrack != null) {
                        val newPos = current.positionMs + 1000L
                        if (newPos >= current.currentTrack.durationMs) {
                            // Current track finished; advance queue if host/collaborative
                            if (current.isHost || current.controlMode == ControlMode.COLLABORATIVE) {
                                if (current.queue.isNotEmpty()) {
                                    val next = current.queue.first()
                                    val remainingQueue = current.queue.drop(1)
                                    sendPlaybackEventInternal(
                                        PlaybackEventType.TRACK_CHANGE,
                                        track = next,
                                        positionMs = 0L,
                                        isPlaying = true,
                                        queue = remainingQueue,
                                    )
                                    current.copy(
                                        currentTrack = next,
                                        positionMs = 0L,
                                        queue = remainingQueue,
                                    )
                                } else {
                                    sendPlaybackEventInternal(
                                        PlaybackEventType.PAUSE,
                                        positionMs = current.currentTrack.durationMs,
                                        isPlaying = false,
                                    )
                                    current.copy(positionMs = current.currentTrack.durationMs, isPlaying = false)
                                }
                            } else {
                                current.copy(positionMs = current.currentTrack.durationMs, isPlaying = false)
                            }
                        } else {
                            current.copy(positionMs = newPos)
                        }
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun startPresenceHeartbeat(roomId: String, memberId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(8000L)
                val currentState = _state.value
                val drift = if (currentState is PlayTogetherState.InRoom) currentState.syncOffsetMs else 0L
                backend.updatePresence(
                    roomId = roomId,
                    memberId = memberId,
                    syncState = MemberSyncState.SYNCED,
                    driftMs = drift,
                )
            }
        }
    }

    private fun subscribeToRoomUpdates(roomId: String) {
        roomEventsJob?.cancel()
        presenceJob?.cancel()

        roomEventsJob = scope.launch {
            backend.observeRoomEvents(roomId).collect { event ->
                when (event) {
                    is RoomEvent.PlaybackUpdated -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                                current.copy(
                                    isPlaying = event.isPlaying,
                                    positionMs = event.positionMs,
                                    currentTrack = event.track ?: current.currentTrack,
                                )
                            } else current
                        }
                    }
                    is RoomEvent.QueueChanged -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                                current.copy(
                                    queue = event.queue,
                                    currentTrack = event.currentTrack ?: current.currentTrack,
                                )
                            } else current
                        }
                    }
                    is RoomEvent.SongRequestsChanged -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                                current.copy(songRequests = event.requests)
                            } else current
                        }
                    }
                    is RoomEvent.MemberPresenceChanged -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                                current.copy(
                                    members = event.members,
                                    room = current.room.copy(memberCount = event.members.size),
                                )
                            } else current
                        }
                    }
                    is RoomEvent.MemberJoined -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                                if (current.members.none { it.id == event.member.id }) {
                                    val updated = current.members + event.member
                                    current.copy(members = updated, room = current.room.copy(memberCount = updated.size))
                                } else current
                            } else current
                        }
                    }
                    is RoomEvent.MemberLeft -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                                val updated = current.members.filter { it.id != event.memberId }
                                current.copy(members = updated, room = current.room.copy(memberCount = updated.size))
                            } else current
                        }
                    }
                    is RoomEvent.RoomClosed -> {
                        leaveRoom()
                        _state.update { current ->
                            if (current is PlayTogetherState.Lobby) {
                                current.copy(errorMessage = event.reason)
                            } else current
                        }
                    }
                    is RoomEvent.Error -> {
                        _state.update { current ->
                            if (current is PlayTogetherState.InRoom) {
                                current.copy(errorMessage = event.message)
                            } else current
                        }
                    }
                }
            }
        }

        presenceJob = scope.launch {
            backend.observePresence(roomId).collect { members ->
                _state.update { current ->
                    if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                        current.copy(
                            members = members,
                            room = current.room.copy(memberCount = members.size),
                        )
                    } else current
                }
            }
        }
    }

    fun switchTab(tab: RoomTab) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                current.copy(activeTab = tab)
            } else current
        }
    }

    fun createRoom(
        name: String,
        hostName: String,
        code: String = Random.nextInt(1000, 9999).toString(),
        visibility: RoomVisibility = RoomVisibility.PUBLIC,
        controlMode: ControlMode = ControlMode.HOST_ONLY,
        password: String? = null,
    ) {
        val cleanCode = code.trim().ifBlank { Random.nextInt(1000, 9999).toString() }
        val cleanName = RoomSecurityPolicy.sanitizeRoomName(name)
        val cleanHost = RoomSecurityPolicy.sanitizeUserName(hostName)

        if (!RoomSecurityPolicy.isValidRoomCode(cleanCode)) {
            _state.update { current ->
                if (current is PlayTogetherState.Lobby) {
                    current.copy(errorMessage = "Room code must be exactly 4 digits.")
                } else current
            }
            return
        }

        _state.update { current ->
            if (current is PlayTogetherState.Lobby) current.copy(isBusy = true, errorMessage = null) else current
        }

        scope.launch {
            backend.createRoom(
                name = cleanName,
                code = cleanCode,
                hostName = cleanHost,
                visibility = visibility,
                controlMode = controlMode,
                password = password,
            ).fold(
                onSuccess = { room ->
                    val hostMemberId = "host_${Random.nextLong(1000, 9999)}"
                    val selfMember = RoomMember(
                        id = hostMemberId,
                        name = cleanHost,
                        isHost = true,
                        syncState = MemberSyncState.SYNCED,
                        driftMs = 0L,
                    )

                    _state.value = PlayTogetherState.InRoom(
                        room = room,
                        isHost = true,
                        currentMemberId = hostMemberId,
                        members = listOf(selfMember),
                        currentTrack = null,
                        isPlaying = false,
                        positionMs = 0L,
                        queue = emptyList(),
                        songRequests = emptyList(),
                        activeTab = RoomTab.NOW_PLAYING,
                        controlMode = controlMode,
                        syncOffsetMs = 0L,
                    )

                    subscribeToRoomUpdates(room.id)
                    startPlaybackTicker()
                    startPresenceHeartbeat(room.id, hostMemberId)
                },
                onFailure = { err ->
                    _state.update { current ->
                        if (current is PlayTogetherState.Lobby) {
                            current.copy(isBusy = false, errorMessage = err.message ?: "Failed to create room.")
                        } else current
                    }
                },
            )
        }
    }

    fun joinRoom(code: String, userName: String, password: String? = null) {
        val cleanName = RoomSecurityPolicy.sanitizeUserName(userName)
        val cleanCode = code.trim()

        if (!RoomSecurityPolicy.isValidRoomCode(cleanCode)) {
            _state.update { current ->
                if (current is PlayTogetherState.Lobby) {
                    current.copy(errorMessage = "Room code must be exactly 4 digits.")
                } else current
            }
            return
        }

        _state.update { current ->
            if (current is PlayTogetherState.Lobby) current.copy(isBusy = true, errorMessage = null) else current
        }

        scope.launch {
            backend.verifyAndJoinRoom(cleanCode, cleanName, password).fold(
                onSuccess = { result ->
                    _state.value = PlayTogetherState.InRoom(
                        room = result.room,
                        isHost = result.isHost,
                        currentMemberId = result.currentMemberId,
                        members = result.members,
                        currentTrack = result.currentTrack,
                        isPlaying = result.isPlaying,
                        positionMs = result.positionMs,
                        queue = result.queue,
                        songRequests = result.songRequests,
                        activeTab = RoomTab.NOW_PLAYING,
                        controlMode = result.room.controlMode,
                        syncOffsetMs = 0L,
                    )

                    subscribeToRoomUpdates(result.room.id)
                    startPlaybackTicker()
                    startPresenceHeartbeat(result.room.id, result.currentMemberId)
                },
                onFailure = { err ->
                    _state.update { current ->
                        if (current is PlayTogetherState.Lobby) {
                            current.copy(isBusy = false, errorMessage = err.message ?: "Failed to join room.")
                        } else current
                    }
                },
            )
        }
    }

    fun leaveRoom() {
        val current = _state.value
        playbackTickerJob?.cancel()
        playbackTickerJob = null
        roomEventsJob?.cancel()
        roomEventsJob = null
        presenceJob?.cancel()
        presenceJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        if (current is PlayTogetherState.InRoom) {
            scope.launch {
                backend.leaveRoom(current.room.id, current.currentMemberId)
            }
        }

        _state.value = PlayTogetherState.Lobby()
        refreshPublicRooms()
    }

    fun togglePlayPause() {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else {
                    val nextPlayState = !current.isPlaying
                    sendPlaybackEventInternal(
                        if (nextPlayState) PlaybackEventType.PLAY else PlaybackEventType.PAUSE,
                        positionMs = current.positionMs,
                        isPlaying = nextPlayState,
                    )
                    current.copy(isPlaying = nextPlayState)
                }
            } else current
        }
    }

    fun nextTrack() {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else if (current.queue.isNotEmpty()) {
                    val next = current.queue.first()
                    val remainingQueue = current.queue.drop(1)
                    sendPlaybackEventInternal(
                        PlaybackEventType.TRACK_CHANGE,
                        track = next,
                        positionMs = 0L,
                        isPlaying = true,
                        queue = remainingQueue,
                    )
                    current.copy(
                        currentTrack = next,
                        positionMs = 0L,
                        queue = remainingQueue,
                    )
                } else {
                    current
                }
            } else current
        }
    }

    fun previousTrack() {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else {
                    sendPlaybackEventInternal(
                        PlaybackEventType.SEEK,
                        positionMs = 0L,
                        isPlaying = current.isPlaying,
                    )
                    current.copy(positionMs = 0L)
                }
            } else current
        }
    }

    fun seekTo(positionMs: Long) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else {
                    val clamped = positionMs.coerceIn(0L, current.currentTrack?.durationMs ?: 0L)
                    sendPlaybackEventInternal(
                        PlaybackEventType.SEEK,
                        positionMs = clamped,
                        isPlaying = current.isPlaying,
                    )
                    current.copy(positionMs = clamped)
                }
            } else current
        }
    }

    fun updateLocalDrift(driftMs: Long) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                current.copy(syncOffsetMs = driftMs)
            } else current
        }
    }

    fun addToQueue(track: PlayTogetherTrack) {
        if (!track.isYouTube || track.id.startsWith("file://") || track.id.startsWith("local_")) {
            _state.update { current ->
                if (current is PlayTogetherState.InRoom) {
                    current.copy(errorMessage = "Local files cannot be shared in Play Together. Only online streams accessible to all listeners can be synchronized.")
                } else current
            }
            return
        }

        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                val newTrack = if (current.currentTrack == null) track else current.currentTrack
                val newQueue = if (current.currentTrack == null) current.queue else current.queue + track
                sendPlaybackEventInternal(
                    PlaybackEventType.QUEUE_UPDATE,
                    track = newTrack,
                    queue = newQueue,
                )
                if (current.currentTrack == null) {
                    current.copy(currentTrack = track, positionMs = 0L, isPlaying = true)
                } else {
                    current.copy(queue = newQueue)
                }
            } else current
        }
    }

    fun removeFromQueue(trackId: String) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else {
                    val newQueue = current.queue.filter { it.id != trackId }
                    sendPlaybackEventInternal(
                        PlaybackEventType.QUEUE_UPDATE,
                        queue = newQueue,
                    )
                    current.copy(queue = newQueue)
                }
            } else current
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else if (fromIndex in current.queue.indices && toIndex in current.queue.indices) {
                    val mutable = current.queue.toMutableList()
                    val item = mutable.removeAt(fromIndex)
                    mutable.add(toIndex, item)
                    sendPlaybackEventInternal(
                        PlaybackEventType.QUEUE_UPDATE,
                        queue = mutable,
                    )
                    current.copy(queue = mutable)
                } else current
            } else current
        }
    }

    fun clearQueue() {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom && current.isHost) {
                sendPlaybackEventInternal(
                    PlaybackEventType.QUEUE_UPDATE,
                    queue = emptyList(),
                )
                current.copy(queue = emptyList())
            } else current
        }
    }

    fun submitSongRequest(track: PlayTogetherTrack, requesterName: String) {
        val current = _state.value
        if (current !is PlayTogetherState.InRoom) return

        val request = SongRequest(
            id = "req_${Random.nextLong(10000, 99999)}",
            track = track.copy(addedBy = requesterName),
            requesterName = requesterName,
            requesterId = current.currentMemberId,
            requestTimeMs = Clock.System.now().toEpochMilliseconds(),
            status = SongRequestStatus.PENDING,
        )

        scope.launch {
            backend.submitSongRequest(current.room.id, request).fold(
                onSuccess = {
                    _state.update { s ->
                        if (s is PlayTogetherState.InRoom) {
                            if (s.songRequests.none { it.id == request.id }) {
                                s.copy(songRequests = s.songRequests + request)
                            } else s
                        } else s
                    }
                },
                onFailure = { err ->
                    _state.update { s ->
                        if (s is PlayTogetherState.InRoom) {
                            s.copy(errorMessage = err.message ?: "Failed to submit request.")
                        } else s
                    }
                },
            )
        }
    }

    fun approveSongRequest(requestId: String) {
        val current = _state.value
        if (current !is PlayTogetherState.InRoom || !current.isHost) return

        scope.launch {
            backend.updateSongRequestStatus(current.room.id, requestId, SongRequestStatus.APPROVED).fold(
                onSuccess = {
                    _state.update { s ->
                        if (s is PlayTogetherState.InRoom) {
                            val req = s.songRequests.firstOrNull { it.id == requestId }
                            if (req != null) {
                                val updatedRequests = s.songRequests.map {
                                    if (it.id == requestId) it.copy(status = SongRequestStatus.APPROVED) else it
                                }
                                val newTrack = if (s.currentTrack == null) req.track else s.currentTrack
                                val newQueue = if (s.currentTrack == null) s.queue else s.queue + req.track
                                s.copy(
                                    songRequests = updatedRequests,
                                    currentTrack = newTrack,
                                    queue = newQueue,
                                    isPlaying = if (s.currentTrack == null) true else s.isPlaying,
                                )
                            } else s
                        } else s
                    }
                },
                onFailure = { err ->
                    _state.update { s ->
                        if (s is PlayTogetherState.InRoom) {
                            s.copy(errorMessage = err.message)
                        } else s
                    }
                },
            )
        }
    }

    fun rejectSongRequest(requestId: String) {
        val current = _state.value
        if (current !is PlayTogetherState.InRoom || !current.isHost) return

        scope.launch {
            backend.updateSongRequestStatus(current.room.id, requestId, SongRequestStatus.REJECTED).fold(
                onSuccess = {
                    _state.update { s ->
                        if (s is PlayTogetherState.InRoom) {
                            val updatedRequests = s.songRequests.map {
                                if (it.id == requestId) it.copy(status = SongRequestStatus.REJECTED) else it
                            }
                            s.copy(songRequests = updatedRequests)
                        } else s
                    }
                },
                onFailure = { err ->
                    _state.update { s ->
                        if (s is PlayTogetherState.InRoom) {
                            s.copy(errorMessage = err.message)
                        } else s
                    }
                },
            )
        }
    }

    fun cancelSongRequest(requestId: String) {
        val current = _state.value
        if (current !is PlayTogetherState.InRoom) return

        _state.update { s ->
            if (s is PlayTogetherState.InRoom) {
                s.copy(songRequests = s.songRequests.filterNot { it.id == requestId && it.requesterId == s.currentMemberId })
            } else s
        }
    }

    fun setControlMode(controlMode: ControlMode) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom && current.isHost) {
                current.copy(controlMode = controlMode)
            } else current
        }
    }

    fun toggleAllowRequests(allow: Boolean) {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom && current.isHost) {
                current.copy(allowListenerRequests = allow)
            } else current
        }
    }

    private fun sendPlaybackEventInternal(
        eventType: PlaybackEventType,
        track: PlayTogetherTrack? = null,
        positionMs: Long = 0L,
        isPlaying: Boolean = false,
        queue: List<PlayTogetherTrack> = emptyList(),
    ) {
        val current = _state.value
        if (current is PlayTogetherState.InRoom) {
            val event = PlaybackEvent(
                roomId = current.room.id,
                senderId = current.currentMemberId,
                eventType = eventType,
                track = track ?: current.currentTrack,
                positionMs = positionMs,
                isPlaying = isPlaying,
                queue = queue.ifEmpty { current.queue },
                songRequests = current.songRequests,
                timestampUtc = Clock.System.now().toEpochMilliseconds(),
            )
            scope.launch {
                backend.sendPlaybackEvent(current.room.id, event)
            }
        }
    }

    companion object {
        val instance by lazy { PlayTogetherController() }
    }
}
