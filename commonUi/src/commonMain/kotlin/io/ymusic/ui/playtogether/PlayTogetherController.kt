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

    var syncEngine: PlayTogetherSyncEngine? = null

    fun bindSyncEngine(engine: PlayTogetherSyncEngine) {
        this.syncEngine = engine
    }

    private var playbackTickerJob: Job? = null
    private var roomEventsJob: Job? = null
    private var presenceJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        refreshPublicRooms()
    }

    val radioCatalog = listOf(
        PlayTogetherTrack(
            id = "4NRXx6U8ABQ",
            title = "Blinding Lights",
            artist = "The Weeknd",
            artworkUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            durationMs = 200000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "34Na4j8AVgA",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            artworkUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg",
            durationMs = 230000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "H5v3k2nn9pc",
            title = "As It Was",
            artist = "Harry Styles",
            artworkUrl = "https://i.ytimg.com/vi/H5v3k2nn9pc/hqdefault.jpg",
            durationMs = 167000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "kTJczUoc56U",
            title = "Stay",
            artist = "The Kid LAROI, Justin Bieber",
            artworkUrl = "https://i.ytimg.com/vi/kTJczUoc56U/hqdefault.jpg",
            durationMs = 141000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "TUVcZfQe-Kw",
            title = "Levitating",
            artist = "Dua Lipa",
            artworkUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
            durationMs = 203000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "JGwWNGJdvx8",
            title = "Shape of You",
            artist = "Ed Sheeran",
            artworkUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            durationMs = 233000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "DyDfgMOUjCI",
            title = "bad guy",
            artist = "Billie Eilish",
            artworkUrl = "https://i.ytimg.com/vi/DyDfgMOUjCI/hqdefault.jpg",
            durationMs = 194000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "XXYlFuWEuKI",
            title = "Save Your Tears",
            artist = "The Weeknd",
            artworkUrl = "https://i.ytimg.com/vi/XXYlFuWEuKI/hqdefault.jpg",
            durationMs = 215000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "mRD0-GxqHVo",
            title = "Heat Waves",
            artist = "Glass Animals",
            artworkUrl = "https://i.ytimg.com/vi/mRD0-GxqHVo/hqdefault.jpg",
            durationMs = 239000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "ApXoWvfEYVU",
            title = "Sunflower",
            artist = "Post Malone, Swae Lee",
            artworkUrl = "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg",
            durationMs = 158000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "syFZfO_wfMQ",
            title = "Night Changes",
            artist = "One Direction",
            artworkUrl = "https://i.ytimg.com/vi/syFZfO_wfMQ/hqdefault.jpg",
            durationMs = 227000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "G7KNmW9a75Y",
            title = "Flowers",
            artist = "Miley Cyrus",
            artworkUrl = "https://i.ytimg.com/vi/G7KNmW9a75Y/hqdefault.jpg",
            durationMs = 200000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "7wtfhZwyrcc",
            title = "Believer",
            artist = "Imagine Dragons",
            artworkUrl = "https://i.ytimg.com/vi/7wtfhZwyrcc/hqdefault.jpg",
            durationMs = 204000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
        PlayTogetherTrack(
            id = "UTHLK93Hio0",
            title = "INDUSTRY BABY",
            artist = "Lil Nas X, Jack Harlow",
            artworkUrl = "https://i.ytimg.com/vi/UTHLK93Hio0/hqdefault.jpg",
            durationMs = 212000L,
            isYouTube = true,
            addedBy = "PRVN Auto-Radio",
            isAutoRadio = true,
        ),
    )

    fun pickRandomRadioTrack(excludeId: String? = null): PlayTogetherTrack {
        val candidates = radioCatalog.filter { it.id != excludeId }
        return if (candidates.isNotEmpty()) {
            candidates[Random.nextInt(candidates.size)]
        } else {
            radioCatalog[Random.nextInt(radioCatalog.size)]
        }
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
                val engine = syncEngine
                _state.update { current ->
                    if (current is PlayTogetherState.InRoom && current.isPlaying && current.currentTrack != null) {
                        val realPos = engine?.getPlayerPositionMs()?.takeIf { it > 0L }
                            ?: (current.positionMs + 1000L)
                        val totalDur = current.currentTrack.durationMs
                        if (totalDur > 0L && realPos >= totalDur) {
                            if (current.isHost || current.controlMode == ControlMode.COLLABORATIVE) {
                                if (current.queue.isNotEmpty()) {
                                    val next = current.queue.first()
                                    val remainingQueue = current.queue.drop(1)
                                    engine?.syncToTrack(next, 0L, playWhenReady = true)
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
                                        isAutoRadio = next.isAutoRadio,
                                        isPlaying = true,
                                    )
                                } else {
                                    val randomRadio = pickRandomRadioTrack(excludeId = current.currentTrack.id)
                                    engine?.syncToTrack(randomRadio, 0L, playWhenReady = true)
                                    sendPlaybackEventInternal(
                                        PlaybackEventType.TRACK_CHANGE,
                                        track = randomRadio,
                                        positionMs = 0L,
                                        isPlaying = true,
                                        queue = emptyList(),
                                    )
                                    current.copy(
                                        currentTrack = randomRadio,
                                        positionMs = 0L,
                                        queue = emptyList(),
                                        isAutoRadio = true,
                                        isPlaying = true,
                                    )
                                }
                            } else {
                                current.copy(positionMs = totalDur, isPlaying = false)
                            }
                        } else {
                            current.copy(positionMs = realPos)
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
        initialTrack: PlayTogetherTrack? = null,
        initialPositionMs: Long = 0L,
        initialIsPlaying: Boolean = false,
        initialQueue: List<PlayTogetherTrack> = emptyList(),
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
                initialTrack = initialTrack,
                initialPositionMs = initialPositionMs,
                initialIsPlaying = initialIsPlaying,
                initialQueue = initialQueue,
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
                        currentTrack = initialTrack,
                        isPlaying = initialIsPlaying,
                        positionMs = initialPositionMs,
                        queue = initialQueue,
                        songRequests = emptyList(),
                        activeTab = RoomTab.NOW_PLAYING,
                        controlMode = controlMode,
                        syncOffsetMs = 0L,
                    )

                    if (initialTrack != null && initialIsPlaying) {
                        syncEngine?.syncToTrack(initialTrack, initialPositionMs, playWhenReady = true)
                    }

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

                    result.currentTrack?.let { tr ->
                        syncEngine?.syncToTrack(tr, result.positionMs, playWhenReady = result.isPlaying)
                    }

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

        syncEngine?.pause()

        if (current is PlayTogetherState.InRoom) {
            scope.launch {
                backend.leaveRoom(current.room.id, current.currentMemberId)
            }
        }

        _state.value = PlayTogetherState.Lobby()
        refreshPublicRooms()
    }

    fun closeRoom() {
        val current = _state.value
        playbackTickerJob?.cancel()
        playbackTickerJob = null
        roomEventsJob?.cancel()
        roomEventsJob = null
        presenceJob?.cancel()
        presenceJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        syncEngine?.pause()

        if (current is PlayTogetherState.InRoom) {
            scope.launch {
                backend.closeRoom(current.room.id, "Host ended the room session.")
            }
        }

        _state.value = PlayTogetherState.Lobby()
        refreshPublicRooms()
    }

    fun kickMember(memberId: String) {
        val current = _state.value
        if (current is PlayTogetherState.InRoom && current.isHost) {
            scope.launch {
                backend.leaveRoom(current.room.id, memberId)
            }
            _state.update { s ->
                if (s is PlayTogetherState.InRoom) {
                    val updated = s.members.filter { it.id != memberId }
                    s.copy(members = updated, room = s.room.copy(memberCount = updated.size))
                } else s
            }
        }
    }

    fun togglePlayPause() {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else if (current.currentTrack == null) {
                    val track = if (current.queue.isNotEmpty()) current.queue.first() else pickRandomRadioTrack()
                    val remQueue = if (current.queue.isNotEmpty()) current.queue.drop(1) else emptyList()
                    syncEngine?.syncToTrack(track, 0L, playWhenReady = true)
                    sendPlaybackEventInternal(
                        PlaybackEventType.TRACK_CHANGE,
                        track = track,
                        positionMs = 0L,
                        isPlaying = true,
                        queue = remQueue,
                    )
                    current.copy(
                        currentTrack = track,
                        positionMs = 0L,
                        isPlaying = true,
                        queue = remQueue,
                        isAutoRadio = current.queue.isEmpty(),
                    )
                } else {
                    val nextPlayState = !current.isPlaying
                    if (nextPlayState) {
                        syncEngine?.play()
                    } else {
                        syncEngine?.pause()
                    }
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
                    syncEngine?.syncToTrack(next, 0L, playWhenReady = true)
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
                        isAutoRadio = next.isAutoRadio,
                        isPlaying = true,
                    )
                } else {
                    // Queue empty: advance to random radio track!
                    val randomRadio = pickRandomRadioTrack(excludeId = current.currentTrack?.id)
                    syncEngine?.syncToTrack(randomRadio, 0L, playWhenReady = true)
                    sendPlaybackEventInternal(
                        PlaybackEventType.TRACK_CHANGE,
                        track = randomRadio,
                        positionMs = 0L,
                        isPlaying = true,
                        queue = emptyList(),
                    )
                    current.copy(
                        currentTrack = randomRadio,
                        positionMs = 0L,
                        queue = emptyList(),
                        isAutoRadio = true,
                        isPlaying = true,
                    )
                }
            } else current
        }
    }

    fun onTrackEnded() {
        nextTrack()
    }

    fun previousTrack() {
        _state.update { current ->
            if (current is PlayTogetherState.InRoom) {
                if (!current.isHost && current.controlMode == ControlMode.HOST_ONLY) {
                    current
                } else {
                    syncEngine?.seekTo(0L)
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
                    syncEngine?.seekTo(clamped)
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

    fun playNow(track: PlayTogetherTrack) {
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
                syncEngine?.syncToTrack(track, 0L, playWhenReady = true)
                sendPlaybackEventInternal(
                    PlaybackEventType.TRACK_CHANGE,
                    track = track,
                    positionMs = 0L,
                    isPlaying = true,
                    queue = current.queue,
                )
                current.copy(
                    currentTrack = track,
                    positionMs = 0L,
                    isPlaying = true,
                    isAutoRadio = false,
                )
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
                // If there's no current track or if playing an auto-radio filler,
                // immediately transition to the user-queued song!
                val shouldPlayImmediately = current.currentTrack == null || current.isAutoRadio
                if (shouldPlayImmediately) {
                    sendPlaybackEventInternal(
                        PlaybackEventType.TRACK_CHANGE,
                        track = track,
                        positionMs = 0L,
                        isPlaying = true,
                        queue = current.queue,
                    )
                    current.copy(
                        currentTrack = track,
                        positionMs = 0L,
                        isPlaying = true,
                        isAutoRadio = false,
                    )
                } else {
                    // Current track was specifically chosen: queue to play next!
                    val newQueue = current.queue + track
                    sendPlaybackEventInternal(
                        PlaybackEventType.QUEUE_UPDATE,
                        track = current.currentTrack,
                        queue = newQueue,
                    )
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
