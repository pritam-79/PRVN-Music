package io.ymusic.ui.playtogether

import io.ymusic.player.PlayableItem
import io.ymusic.player.PlaybackState
import io.ymusic.settings.AppSettings
import io.ymusic.settings.SettingsStore
import io.ymusic.settings.flow
import io.ymusic.settings.put
import io.ymusic.ui.PlaybackCoordinator
import io.ymusic.ui.playtogether.backend.PlaybackEvent
import io.ymusic.ui.playtogether.backend.PlaybackEventType
import io.ymusic.ui.playtogether.backend.PlayTogetherBackend
import io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider
import io.ymusic.ui.playtogether.backend.RateLimiter
import io.ymusic.ui.playtogether.backend.RoomEvent
import io.ymusic.ui.playtogether.backend.RoomSecurityPolicy
import io.ymusic.ui.playtogether.backend.InviteLinkHelper
import io.ymusic.ui.playtogether.storage.PlayTogetherStore
import kmpcommon.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * Central application-lifetime controller for PRVN Play Together.
 *
 * Unifies:
 * - Centralized PRVN Music playback pipeline ([PlaybackCoordinator] + [PlayTogetherSyncEngine])
 * - Centralized Discover / Search / YouTube catalog ([TrackResolver])
 * - Persistent SQLite room, queue, request & deduplication storage ([PlayTogetherStore])
 * - Cross-device real-time state synchronization & recovery ([PlayTogetherBackend])
 */
class PlayTogetherController(
    private val coordinator: PlaybackCoordinator,
    val trackResolver: TrackResolver,
    private val store: PlayTogetherStore? = null,
    private val settings: SettingsStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val backendProvider: () -> PlayTogetherBackend = { PlayTogetherBackendProvider.current },
    private val logger: Logger? = null,
) {
    val backend: PlayTogetherBackend
        get() = backendProvider()

    private val _state = MutableStateFlow<PlayTogetherState>(PlayTogetherState.Lobby())
    val state: StateFlow<PlayTogetherState> = _state.asStateFlow()

    private val joinRateLimiter = RateLimiter(maxRequests = 8, windowMs = 30_000L)
    private val playedHistory = ArrayDeque<PlayTogetherTrack>()

    private var activeRoomJob: Job? = null
    private var presenceJob: Job? = null
    private var tickerJob: Job? = null
    private var wasOfflineInRoom = false

    private inline fun updateInRoom(
        crossinline transform: (PlayTogetherState.InRoom) -> PlayTogetherState.InRoom,
    ): PlayTogetherState.InRoom? {
        var nextState: PlayTogetherState.InRoom? = null
        _state.update { current ->
            val inRoom = current as? PlayTogetherState.InRoom ?: return@update current
            transform(inRoom).also { nextState = it }
        }
        return nextState
    }

    val syncEngine: PlayTogetherSyncEngine = PlayTogetherSyncEngine(
        coordinator = coordinator,
        trackResolver = trackResolver,
        settings = settings,
        scope = scope,
        callbacks = object : PlayTogetherSyncCallbacks {
            override fun currentRoomSnapshot(): PlayTogetherState.InRoom? = _state.value as? PlayTogetherState.InRoom

            override fun onTelemetryUpdate(telemetry: SyncTelemetry) {
                updateInRoom { cur ->
                    val effectivePos = if (telemetry.trackId != null && telemetry.trackId == cur.currentTrack?.id) {
                        telemetry.localPositionMs
                    } else {
                        cur.positionMs
                    }
                    val durationFromPlayer = telemetry.durationMs
                    val updatedTrack = if (cur.currentTrack != null && cur.currentTrack.durationMs <= 0L && durationFromPlayer > 0L) {
                        cur.currentTrack.copy(durationMs = durationFromPlayer)
                    } else {
                        cur.currentTrack
                    }
                    val newConnState = when {
                        !backend.isConnected() -> PlaybackConnectionState.RECONNECTING
                        else -> PlaybackConnectionState.CONNECTED
                    }
                    val updatedMembers = cur.members.map { m ->
                        if (m.id == cur.currentMemberId) {
                            val st = when {
                                newConnState != PlaybackConnectionState.CONNECTED -> MemberSyncState.RECONNECTING
                                telemetry.isBuffering -> MemberSyncState.BUFFERING
                                telemetry.isActuallySynced -> MemberSyncState.SYNCED
                                else -> MemberSyncState.SYNCING
                            }
                            m.copy(syncState = st, driftMs = telemetry.driftMs)
                        } else {
                            m
                        }
                    }
                    cur.copy(
                        currentTrack = updatedTrack,
                        positionMs = effectivePos,
                        syncOffsetMs = telemetry.driftMs,
                        isBuffering = telemetry.isBuffering,
                        isActuallySynced = telemetry.isActuallySynced && newConnState == PlaybackConnectionState.CONNECTED,
                        connectionState = newConnState,
                        members = updatedMembers,
                        errorMessage = telemetry.playerError ?: cur.errorMessage,
                    )
                }
            }

            override fun onTrackEnded(finishedTrackId: String) {
                this@PlayTogetherController.onTrackEnded(finishedTrackId)
            }

            override fun onHostTransportPlayPause(isPlaying: Boolean, positionMs: Long) {
                handleExternalHostTransportPlayPause(isPlaying, positionMs)
            }

            override fun onHostTransportSeek(positionMs: Long) {
                handleExternalHostSeek(positionMs)
            }

            override fun onHostPlayedNewTrack(track: PlayTogetherTrack, positionMs: Long, isPlaying: Boolean) {
                handleExternalHostNewTrack(track, positionMs, isPlaying)
            }
        },
    )

    init {
        restoreSavedSessionAndRefresh()
    }

    private fun restoreSavedSessionAndRefresh() {
        scope.launch {
            val recent = runCatching { store?.listRecentRooms() }.getOrNull().orEmpty()
            val saved = runCatching { store?.getActiveSession() }.getOrNull()
            if (saved != null && (_state.value is PlayTogetherState.Lobby)) {
                val room = saved.room
                val res = backend.fetchLatestRoomSnapshot(
                    roomId = room.id,
                    requesterMemberId = saved.memberId,
                    requesterName = saved.memberName,
                    isHost = saved.isHost,
                )?.getOrNull()

                if (res != null && !res.room.isClosed) {
                    enterRoomState(res, res.currentMemberId, saved.memberName)
                    return@launch
                }
                // Clear stale closed session or present Lobby with recent rooms
                if (res == null) {
                    // Attempt code join reconnect if room is still alive
                    val rejoined = backend.verifyAndJoinRoom(
                        code = room.code,
                        userName = saved.memberName,
                        password = room.password,
                        preferredMemberId = saved.memberId,
                    ).getOrNull()
                    if (rejoined != null) {
                        enterRoomState(rejoined, rejoined.currentMemberId, saved.memberName)
                        return@launch
                    }
                }
                store?.clearActiveSession(room.id)
            }
            val cur = _state.value as? PlayTogetherState.Lobby ?: return@launch
            _state.value = cur.copy(recentRooms = recent)
        }
    }

    fun refreshLobby() {
        val cur = _state.value
        if (cur !is PlayTogetherState.Lobby) return
        _state.value = cur.copy(isBusy = true, errorMessage = null)
        scope.launch {
            val recent = runCatching { store?.listRecentRooms() }.getOrNull().orEmpty()
            refreshLobbyWithRecent(recent)
        }
    }

    private suspend fun refreshLobbyWithRecent(recentRooms: List<RoomInfo>) {
        val result = backend.listPublicRooms()
        val cur = _state.value as? PlayTogetherState.Lobby ?: return
        result.fold(
            onSuccess = { rooms ->
                _state.value = cur.copy(
                    activeRooms = rooms,
                    recentRooms = recentRooms.filter { r -> rooms.none { it.code == r.code } },
                    isBusy = false,
                    errorMessage = null,
                    connectionState = PlaybackConnectionState.CONNECTED,
                )
            },
            onFailure = { err ->
                _state.value = cur.copy(
                    activeRooms = emptyList(),
                    recentRooms = recentRooms,
                    isBusy = false,
                    errorMessage = err.message ?: "Unable to fetch active rooms.",
                    connectionState = if (backend.isConnected()) PlaybackConnectionState.CONNECTED else PlaybackConnectionState.OFFLINE,
                )
            },
        )
    }

    fun createRoom(
        roomName: String,
        hostName: String,
        visibility: RoomVisibility = RoomVisibility.PUBLIC,
        controlMode: ControlMode = ControlMode.HOST_ONLY,
        password: String? = null,
        transferCurrentMusic: Boolean = true,
    ) {
        val lobby = _state.value as? PlayTogetherState.Lobby
        _state.value = (lobby ?: PlayTogetherState.Lobby()).copy(isBusy = true, errorMessage = null)

        scope.launch {
            val cleanHostName = RoomSecurityPolicy.sanitizeUserName(hostName)
            val cleanRoomName = RoomSecurityPolicy.sanitizeRoomName(roomName)
            val code = Random.nextInt(1000, 9999).toString()
            val hostId = "host_${Random.nextLong(10000, 99999)}"
            settings.put(AppSettings.playTogetherUserName, cleanHostName)

            var initialTrack: PlayTogetherTrack? = null
            var initialPosMs = 0L
            var initialPlaying = false
            var initialQueue = emptyList<PlayTogetherTrack>()
            var transferNote: String? = null

            if (transferCurrentMusic) {
                val pQueue = coordinator.player.queue.value
                val curItem = pQueue.currentItem
                val curStatus = coordinator.player.status.value
                val curProgress = coordinator.player.progress.value
                if (curItem != null) {
                    val directTrack = TrackResolver.fromPlayableItem(curItem, addedBy = cleanHostName)
                    if (directTrack != null) {
                        initialTrack = trackResolver.resolveAndValidate(directTrack).getOrNull() ?: directTrack
                        initialPosMs = curProgress.positionMs.coerceAtLeast(0L)
                        initialPlaying = curStatus.playWhenReady
                        if (pQueue.currentIndex >= 0 && pQueue.currentIndex + 1 < pQueue.items.size) {
                            initialQueue = pQueue.items
                                .drop(pQueue.currentIndex + 1)
                                .take(25)
                                .mapNotNull { TrackResolver.fromPlayableItem(it, addedBy = cleanHostName) }
                        }
                    } else if (curItem is PlayableItem.LocalMedia) {
                        val matched = trackResolver.resolvePlayableFromLocal(curItem, addedBy = cleanHostName)
                        if (matched.isSuccess) {
                            initialTrack = matched.getOrThrow()
                            initialPosMs = curProgress.positionMs.coerceAtLeast(0L)
                            initialPlaying = curStatus.playWhenReady
                            transferNote = "Matched local song \"${curItem.title}\" online for friends."
                        } else {
                            transferNote = "Local file cannot be streamed across devices — pick an online song in Search."
                        }
                    }
                }
            }

            val result = backend.createRoom(
                name = cleanRoomName,
                code = code,
                hostName = cleanHostName,
                visibility = visibility,
                controlMode = controlMode,
                password = password,
                initialTrack = initialTrack,
                initialPositionMs = initialPosMs,
                initialIsPlaying = initialPlaying,
                initialQueue = initialQueue,
                hostId = hostId,
                allowListenerRequests = true,
            )

            result.fold(
                onSuccess = { roomInfo ->
                    playedHistory.clear()
                    val now = Clock.System.now().toEpochMilliseconds()
                    val ping = backend.measureServerClockOffset()?.getOrNull()
                    val hostMember = RoomMember(
                        id = hostId,
                        name = cleanHostName,
                        isHost = true,
                        syncState = MemberSyncState.SYNCED,
                        driftMs = 0L,
                        lastSeenUtc = now,
                    )
                    val inRoom = PlayTogetherState.InRoom(
                        room = roomInfo,
                        isHost = true,
                        members = listOf(hostMember),
                        currentTrack = initialTrack,
                        isPlaying = initialPlaying,
                        positionMs = initialPosMs,
                        queue = initialQueue,
                        songRequests = emptyList(),
                        activeTab = RoomTab.NOW_PLAYING,
                        controlMode = controlMode,
                        syncOffsetMs = 0L,
                        networkRttMs = ping?.rttMs ?: 0L,
                        clockOffsetMs = ping?.clockOffsetMs ?: 0L,
                        isBuffering = false,
                        isActuallySynced = true,
                        connectionState = PlaybackConnectionState.CONNECTED,
                        statusBanner = transferNote,
                        currentMemberId = hostId,
                        currentUserName = cleanHostName,
                        allowListenerRequests = true,
                        isAutoRadio = false,
                        playbackSeq = 1L,
                        queueVersion = 1L,
                        playbackStartTimestampUtc = if (initialPlaying && initialTrack != null) now - initialPosMs else 0L,
                        serverTimestampUtc = ping?.serverTimestampUtc ?: now,
                    )
                    _state.value = inRoom
                    persistState(inRoom)
                    syncEngine.attachRoom(roomInfo.id, initialTrack, initialPosMs, initialPlaying)
                    startRoomObserversAndHeartbeats(roomInfo.id, hostId, cleanHostName, isHost = true)
                },
                onFailure = { err ->
                    _state.value = PlayTogetherState.Lobby(
                        activeRooms = lobby?.activeRooms.orEmpty(),
                        recentRooms = lobby?.recentRooms.orEmpty(),
                        isBusy = false,
                        errorMessage = err.message ?: "Failed to create room.",
                    )
                },
            )
        }
    }

    fun joinRoom(
        code: String,
        userName: String,
        password: String? = null,
    ) {
        val lobby = _state.value as? PlayTogetherState.Lobby
        if (!joinRateLimiter.tryAcquire()) {
            val wait = joinRateLimiter.secondsUntilReset()
            _state.value = (lobby ?: PlayTogetherState.Lobby()).copy(
                errorMessage = "Too many join attempts. Please wait ${wait}s before trying again.",
            )
            return
        }

        val cleanCode = code.trim()
        if (!RoomSecurityPolicy.isValidRoomCode(cleanCode)) {
            _state.value = (lobby ?: PlayTogetherState.Lobby()).copy(
                errorMessage = "Invalid code. Please enter a valid 4-digit room code.",
            )
            return
        }

        val cleanUserName = RoomSecurityPolicy.sanitizeUserName(userName)
        _state.value = (lobby ?: PlayTogetherState.Lobby()).copy(isBusy = true, errorMessage = null)

        scope.launch {
            settings.put(AppSettings.playTogetherUserName, cleanUserName)
            val result = backend.verifyAndJoinRoom(cleanCode, cleanUserName, password)
            result.fold(
                onSuccess = { joinRes ->
                    enterRoomState(joinRes, joinRes.currentMemberId, cleanUserName)
                },
                onFailure = { err ->
                    _state.value = PlayTogetherState.Lobby(
                        activeRooms = lobby?.activeRooms.orEmpty(),
                        recentRooms = lobby?.recentRooms.orEmpty(),
                        isBusy = false,
                        errorMessage = err.message ?: "Room code \"$cleanCode\" not found.",
                    )
                },
            )
        }
    }

    fun joinByDeepLink(uriOrCode: String, userName: String? = null) {
        val code = InviteLinkHelper.parseRoomCode(uriOrCode)
        if (code == null) {
            val lobby = _state.value as? PlayTogetherState.Lobby ?: return
            _state.value = lobby.copy(errorMessage = "Invalid Play Together invite link.")
            return
        }
        val storedName = settings.flow(AppSettings.playTogetherUserName).value
        val resolvedName = RoomSecurityPolicy.sanitizeUserName(userName?.takeIf { it.isNotBlank() } ?: storedName)
        joinRoom(code, resolvedName)
    }

    private suspend fun enterRoomState(
        joinRes: io.ymusic.ui.playtogether.backend.JoinRoomResult,
        memberId: String,
        userName: String,
    ) {
        playedHistory.clear()
        val ping = backend.measureServerClockOffset()?.getOrNull()
        val now = Clock.System.now().toEpochMilliseconds()
        val rttCompensation = ((ping?.rttMs ?: joinRes.measuredRttMs) / 2L).coerceIn(0L, 2000L)
        val compensatedPos = if (joinRes.isPlaying && joinRes.currentTrack != null) {
            val raw = joinRes.positionMs + rttCompensation
            if (joinRes.currentTrack.durationMs > 0L) raw.coerceIn(0L, joinRes.currentTrack.durationMs) else raw
        } else {
            joinRes.positionMs
        }

        // Merge any offline requests previously saved locally for this room
        val localRequests = runCatching { store?.listRequests(joinRes.room.id) }.getOrNull().orEmpty()
        val mergedRequests = mergeRequestLists(joinRes.songRequests, localRequests)

        val inRoom = PlayTogetherState.InRoom(
            room = joinRes.room,
            isHost = joinRes.isHost,
            members = joinRes.members,
            currentTrack = joinRes.currentTrack,
            isPlaying = joinRes.isPlaying,
            positionMs = compensatedPos,
            queue = joinRes.queue,
            songRequests = mergedRequests,
            activeTab = RoomTab.NOW_PLAYING,
            controlMode = joinRes.room.controlMode,
            syncOffsetMs = 0L,
            networkRttMs = ping?.rttMs ?: joinRes.measuredRttMs,
            clockOffsetMs = ping?.clockOffsetMs ?: 0L,
            isBuffering = joinRes.currentTrack != null,
            isActuallySynced = joinRes.currentTrack == null,
            connectionState = PlaybackConnectionState.CONNECTED,
            currentMemberId = memberId,
            currentUserName = userName,
            allowListenerRequests = joinRes.room.allowListenerRequests,
            isAutoRadio = joinRes.currentTrack?.isAutoRadio == true,
            playbackSeq = joinRes.playbackSeq.coerceAtLeast(1L),
            queueVersion = joinRes.queueVersion.coerceAtLeast(1L),
            playbackStartTimestampUtc = joinRes.playbackStartTimestampUtc,
            serverTimestampUtc = ping?.serverTimestampUtc ?: now,
        )
        _state.value = inRoom
        persistState(inRoom)
        syncEngine.attachRoom(joinRes.room.id, joinRes.currentTrack, compensatedPos, joinRes.isPlaying)
        startRoomObserversAndHeartbeats(joinRes.room.id, memberId, userName, joinRes.isHost)
        flushUnsyncedRequests(joinRes.room.id)
    }

    fun selectTab(tab: RoomTab) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        _state.value = cur.copy(activeTab = tab)
    }

    fun dismissError() {
        when (val cur = _state.value) {
            is PlayTogetherState.Lobby -> _state.value = cur.copy(errorMessage = null)
            is PlayTogetherState.InRoom -> _state.value = cur.copy(errorMessage = null, statusBanner = null)
        }
    }

    fun clearStatusBanner() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        _state.value = cur.copy(statusBanner = null)
    }

    fun dismissIncomingRequestAlert() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        _state.value = cur.copy(incomingRequestAlert = null)
    }

    // ---- Centralized Playback & Queue Controls ----

    fun playNow(track: PlayTogetherTrack) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return

        scope.launch {
            val validated = trackResolver.resolveAndValidate(track).getOrElse { err ->
                setStatusOrError(error = err.message ?: "Track unavailable")
                return@launch
            }
            applyTrackTransition(validated, isPlaying = true, positionMs = 0L, removeFromQueueIfPresent = true)
        }
    }

    fun playPause() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        val track = cur.currentTrack ?: run {
            val first = cur.queue.firstOrNull()
            if (first != null) {
                playNow(first)
            } else {
                setStatusOrError(banner = "Search or request a song to start playing.")
            }
            return
        }

        val nextPlaying = !cur.isPlaying
        val currentPos = syncEngine.currentPlayerPositionMs().takeIf { it > 0L } ?: cur.positionMs
        val now = Clock.System.now().toEpochMilliseconds()
        val nextSeq = cur.playbackSeq + 1L
        val nextStartUtc = if (nextPlaying) (now + cur.clockOffsetMs - currentPos) else 0L

        val updated = cur.copy(
            isPlaying = nextPlaying,
            positionMs = currentPos,
            playbackSeq = nextSeq,
            playbackStartTimestampUtc = nextStartUtc,
            isAutoRadio = false,
            errorMessage = null,
        )
        _state.value = updated
        persistState(updated)
        syncEngine.applyAuthoritativePlayback(track, currentPos, nextPlaying, forceSeek = false)

        dispatchPlaybackEvent(
            PlaybackEvent(
                roomId = cur.room.id,
                senderId = cur.currentMemberId,
                eventType = if (nextPlaying) PlaybackEventType.PLAY else PlaybackEventType.PAUSE,
                track = track,
                positionMs = currentPos,
                isPlaying = nextPlaying,
                playbackSeq = nextSeq,
                playbackStartTimestampUtc = nextStartUtc,
            ),
        )
    }

    fun seekTo(positionMs: Long) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        val track = cur.currentTrack ?: return
        val clampedPos = if (track.durationMs > 0L) positionMs.coerceIn(0L, track.durationMs) else positionMs.coerceAtLeast(0L)
        val now = Clock.System.now().toEpochMilliseconds()
        val nextSeq = cur.playbackSeq + 1L
        val nextStartUtc = if (cur.isPlaying) (now + cur.clockOffsetMs - clampedPos) else 0L

        val updated = cur.copy(
            positionMs = clampedPos,
            playbackSeq = nextSeq,
            playbackStartTimestampUtc = nextStartUtc,
        )
        _state.value = updated
        persistState(updated)
        syncEngine.applyAuthoritativePlayback(track, clampedPos, cur.isPlaying, forceSeek = true)

        dispatchPlaybackEvent(
            PlaybackEvent(
                roomId = cur.room.id,
                senderId = cur.currentMemberId,
                eventType = PlaybackEventType.SEEK,
                track = track,
                positionMs = clampedPos,
                isPlaying = cur.isPlaying,
                playbackSeq = nextSeq,
                playbackStartTimestampUtc = nextStartUtc,
            ),
        )
    }

    fun addToQueue(track: PlayTogetherTrack) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return

        scope.launch {
            val validated = trackResolver.resolveAndValidate(track).getOrElse { err ->
                setStatusOrError(error = err.message ?: "Track unavailable")
                return@launch
            }
            val latest = _state.value as? PlayTogetherState.InRoom ?: return@launch

            if (latest.currentTrack?.id == validated.id || latest.queue.any { it.id == validated.id }) {
                setStatusOrError(banner = "\"${validated.title}\" is already in the room queue.")
                return@launch
            }

            if (latest.currentTrack == null || latest.isAutoRadio) {
                applyTrackTransition(validated.copy(isAutoRadio = false), isPlaying = true, positionMs = 0L)
                setStatusOrError(banner = "Playing \"${validated.title}\" now")
                return@launch
            }

            val nextQueue = latest.queue + validated.copy(isAutoRadio = false)
            commitQueueChange(latest, nextQueue, banner = "Added \"${validated.title}\" to queue")
        }
    }

    fun playNextInRoom(track: PlayTogetherTrack) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return

        scope.launch {
            val validated = trackResolver.resolveAndValidate(track).getOrElse { err ->
                setStatusOrError(error = err.message ?: "Track unavailable")
                return@launch
            }
            val latest = _state.value as? PlayTogetherState.InRoom ?: return@launch
            if (latest.currentTrack == null || latest.isAutoRadio) {
                applyTrackTransition(validated.copy(isAutoRadio = false), isPlaying = true, positionMs = 0L)
                return@launch
            }
            val filtered = latest.queue.filterNot { it.id == validated.id }
            val nextQueue = listOf(validated.copy(isAutoRadio = false)) + filtered
            commitQueueChange(latest, nextQueue, banner = "\"${validated.title}\" will play next")
        }
    }

    fun moveQueueUp(index: Int) {
        if (index > 0) moveQueueItem(index, index - 1)
    }

    fun moveQueueDown(index: Int) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (index in 0 until cur.queue.lastIndex) moveQueueItem(index, index + 1)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        if (fromIndex !in cur.queue.indices || toIndex !in cur.queue.indices || fromIndex == toIndex) return

        val mutable = cur.queue.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        commitQueueChange(cur, mutable)
    }

    fun removeFromQueue(index: Int) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        if (index !in cur.queue.indices) return
        val removed = cur.queue[index]
        val mutable = cur.queue.toMutableList().apply { removeAt(index) }
        commitQueueChange(cur, mutable, banner = "Removed \"${removed.title}\" from queue")
    }

    fun clearQueue() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        if (cur.queue.isEmpty()) return
        commitQueueChange(cur, emptyList(), banner = "Queue cleared")
    }

    fun skipTrack() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        cur.currentTrack?.id?.let { markTrackRequestsPlayed(cur, it) }
        advanceToNextTrack(_state.value as? PlayTogetherState.InRoom ?: cur)
    }

    fun previousTrack() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return
        val localPos = syncEngine.currentPlayerPositionMs()
        if (localPos > 3000L && cur.currentTrack != null) {
            seekTo(0L)
            return
        }
        val prev = if (playedHistory.isNotEmpty()) playedHistory.removeLast() else null
        if (prev != null) {
            // Put current back on front of queue so forward brings it right back!
            val nextQueue = cur.currentTrack?.let { listOf(it) + cur.queue.filterNot { q -> q.id == prev.id } } ?: cur.queue
            scope.launch {
                val latest = (_state.value as? PlayTogetherState.InRoom)?.copy(queue = nextQueue) ?: return@launch
                _state.value = latest
                applyTrackTransition(prev, isPlaying = true, positionMs = 0L, pushToHistory = false, explicitQueue = nextQueue)
            }
        } else {
            seekTo(0L)
        }
    }

    private fun markTrackRequestsPlayed(cur: PlayTogetherState.InRoom, finishedTrackId: String) {
        val updatedRequests = cur.songRequests.map { req ->
            if (req.track.id == finishedTrackId &&
                (req.status == SongRequestStatus.ADDED_TO_QUEUE || req.status == SongRequestStatus.ACCEPTED)
            ) {
                val played = req.copy(status = SongRequestStatus.PLAYED, updatedTimeMs = Clock.System.now().toEpochMilliseconds())
                scope.launch {
                    store?.saveOrUpdateRequest(played)
                    backend.updateSongRequestStatus(cur.room.id, req.id, SongRequestStatus.PLAYED)
                }
                played
            } else {
                req
            }
        }
        if (updatedRequests != cur.songRequests) {
            _state.value = cur.copy(songRequests = updatedRequests)
        }
    }

    private fun onTrackEnded(finishedTrackId: String) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!RoomSecurityPolicy.canControlPlayback(cur.isHost, cur.controlMode)) return
        if (cur.currentTrack?.id != finishedTrackId) return
        markTrackRequestsPlayed(cur, finishedTrackId)
        advanceToNextTrack(_state.value as? PlayTogetherState.InRoom ?: cur)
    }

    private fun advanceToNextTrack(cur: PlayTogetherState.InRoom) {
        if (cur.queue.isNotEmpty()) {
            val next = cur.queue.first()
            val remaining = cur.queue.drop(1)
            scope.launch {
                applyTrackTransition(
                    track = next.copy(isAutoRadio = false),
                    isPlaying = true,
                    positionMs = 0L,
                    explicitQueue = remaining,
                )
            }
            return
        }

        // Queue is empty -> query real YouTube Music radio / watch-next instead of hardcoded mock songs!
        scope.launch {
            val exclude = buildSet {
                cur.currentTrack?.id?.let { add(it) }
                playedHistory.takeLast(12).forEach { add(it.id) }
            }
            val radioNext = trackResolver.nextRadioTrack(cur.currentTrack?.id, exclude)
            if (radioNext != null) {
                applyTrackTransition(radioNext.copy(isAutoRadio = true), isPlaying = true, positionMs = 0L, explicitQueue = emptyList())
            } else {
                // Clean stop at end of queue if offline and no next radio track is available
                val latest = _state.value as? PlayTogetherState.InRoom ?: return@launch
                val paused = latest.copy(isPlaying = false, positionMs = 0L, statusBanner = "Queue ended")
                _state.value = paused
                persistState(paused)
                syncEngine.applyAuthoritativePlayback(latest.currentTrack, 0L, playWhenReady = false)
            }
        }
    }

    fun transferCurrentMusicToRoom() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!ensurePlaybackControl(cur)) return

        scope.launch {
            val pQueue = coordinator.player.queue.value
            val curItem = pQueue.currentItem
            if (curItem == null) {
                setStatusOrError(banner = "Nothing is currently playing in PRVN Music.")
                return@launch
            }
            val pos = coordinator.player.progress.value.positionMs.coerceAtLeast(0L)
            val isPlaying = coordinator.player.status.value.playWhenReady
            val resolved = when (curItem) {
                is PlayableItem.YtMedia -> {
                    val base = TrackResolver.fromPlayableItem(curItem, addedBy = cur.currentUserName)!!
                    trackResolver.resolveAndValidate(base).getOrDefault(base)
                }
                is PlayableItem.LocalMedia -> {
                    trackResolver.resolvePlayableFromLocal(curItem, addedBy = cur.currentUserName).getOrElse { err ->
                        setStatusOrError(error = err.message ?: "Cannot transfer local track")
                        return@launch
                    }
                }
            }
            applyTrackTransition(resolved, isPlaying = isPlaying, positionMs = pos)
            setStatusOrError(banner = "Synced \"${resolved.title}\" to room")
        }
    }

    fun resyncPlayback() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        scope.launch {
            val fresh = backend.fetchLatestRoomSnapshot(
                roomId = cur.room.id,
                requesterMemberId = cur.currentMemberId,
                requesterName = cur.currentUserName,
                isHost = cur.isHost,
            )?.getOrNull()
            if (fresh != null) {
                applyRemoteSnapshotRecovery(fresh)
            } else {
                syncEngine.forceResyncToRoomHead()
            }
        }
    }

    // ---- End-to-End Song Requests ----

    fun requestSong(track: PlayTogetherTrack) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.allowListenerRequests && !cur.isHost) {
            setStatusOrError(error = "Song requests are currently paused by the host.")
            return
        }

        scope.launch {
            val validated = trackResolver.resolveAndValidate(track.copy(addedBy = cur.currentUserName)).getOrElse { err ->
                setStatusOrError(error = err.message ?: "Selected song cannot be requested")
                return@launch
            }
            val latest = _state.value as? PlayTogetherState.InRoom ?: return@launch

            // Duplicate prevention: playing now, in queue, or already active request
            if (latest.currentTrack?.id == validated.id) {
                setStatusOrError(banner = "\"${validated.title}\" is already playing now.")
                return@launch
            }
            if (latest.queue.any { it.id == validated.id }) {
                setStatusOrError(banner = "\"${validated.title}\" is already in the shared queue.")
                return@launch
            }
            val existingActive = latest.songRequests.any {
                it.track.id == validated.id && !it.status.isTerminal
            }
            if (existingActive) {
                setStatusOrError(banner = "\"${validated.title}\" has already been requested.")
                return@launch
            }

            val myPendingCount = latest.songRequests.count {
                it.requesterId == latest.currentMemberId && it.status == SongRequestStatus.PENDING
            }
            if (!latest.isHost && myPendingCount >= 5) {
                setStatusOrError(error = "Maximum 5 active pending requests allowed.")
                return@launch
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val req = SongRequest(
                id = "req_${now}_${Random.nextInt(100, 999)}",
                roomId = latest.room.id,
                track = validated,
                requesterName = latest.currentUserName,
                requesterId = latest.currentMemberId,
                requestTimeMs = now,
                updatedTimeMs = now,
                status = SongRequestStatus.PENDING,
            )

            val nextState = updateInRoom { current ->
                current.copy(
                    songRequests = listOf(req) + current.songRequests.filterNot { it.id == req.id },
                    statusBanner = "Requested \"${validated.title}\"",
                )
            } ?: return@launch
            persistState(nextState)

            val sendRes = backend.submitSongRequest(latest.room.id, req)
            if (sendRes.isSuccess) {
                store?.saveOrUpdateRequest(req, syncedRemote = true)
            } else {
                // Keep offline pending so when connection restores it syncs automatically!
                store?.saveOrUpdateRequest(req, syncedRemote = false)
                setStatusOrError(banner = "Offline: saved request for \"${validated.title}\" — will send automatically.")
            }
        }
    }

    fun cancelSongRequest(requestId: String) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        val target = cur.songRequests.firstOrNull { it.id == requestId } ?: return
        if (!cur.isHost && target.requesterId != cur.currentMemberId) return
        updateRequestStatusInternal(cur, target, SongRequestStatus.CANCELLED, banner = "Request cancelled")
    }

    fun acceptSongRequest(requestId: String) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost && cur.controlMode != ControlMode.COLLABORATIVE) return
        val target = cur.songRequests.firstOrNull { it.id == requestId } ?: return
        updateRequestStatusInternal(cur, target, SongRequestStatus.ACCEPTED, banner = "Accepted \"${target.track.title}\"")
    }

    fun rejectSongRequest(requestId: String) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost && cur.controlMode != ControlMode.COLLABORATIVE) return
        val target = cur.songRequests.firstOrNull { it.id == requestId } ?: return
        updateRequestStatusInternal(cur, target, SongRequestStatus.REJECTED, banner = "Rejected \"${target.track.title}\"")
    }

    fun approveSongRequest(requestId: String, playNow: Boolean = false) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost && cur.controlMode != ControlMode.COLLABORATIVE) return
        val target = cur.songRequests.firstOrNull { it.id == requestId } ?: return

        scope.launch {
            val validated = trackResolver.resolveAndValidate(target.track.copy(addedBy = target.requesterName))
                .getOrElse { err ->
                    setStatusOrError(error = err.message ?: "Requested song is unavailable")
                    return@launch
                }

            val latest = _state.value as? PlayTogetherState.InRoom ?: return@launch
            val now = Clock.System.now().toEpochMilliseconds()
            val shouldPlayNow = playNow || latest.currentTrack == null || latest.isAutoRadio
            val targetStatus = SongRequestStatus.ADDED_TO_QUEUE
            val stateWithRequests = updateInRoom { current ->
                val updatedReqs = current.songRequests.map { r ->
                    if (r.id == requestId) r.copy(status = targetStatus, updatedTimeMs = now, track = validated) else r
                }
                val nextAlert = if (current.incomingRequestAlert?.id == requestId) null else current.incomingRequestAlert
                current.copy(songRequests = updatedReqs, incomingRequestAlert = nextAlert)
            } ?: return@launch
            store?.saveOrUpdateRequest(stateWithRequests.songRequests.first { it.id == requestId }, syncedRemote = true)

            backend.updateSongRequestStatus(stateWithRequests.room.id, requestId, targetStatus)

            if (shouldPlayNow) {
                applyTrackTransition(validated.copy(isAutoRadio = false), isPlaying = true, positionMs = 0L, removeFromQueueIfPresent = true)
                setStatusOrError(banner = "Playing request \"${validated.title}\"")
            } else {
                val filteredQueue = stateWithRequests.queue.filterNot { it.id == validated.id }
                val nextQueue = filteredQueue + validated.copy(isAutoRadio = false)
                commitQueueChange(stateWithRequests, nextQueue, banner = "Added \"${validated.title}\" to queue")
            }
        }
    }

    private fun updateRequestStatusInternal(
        cur: PlayTogetherState.InRoom,
        target: SongRequest,
        newStatus: SongRequestStatus,
        banner: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updatedReq = target.copy(status = newStatus, updatedTimeMs = now)
        val updatedList = cur.songRequests.map { if (it.id == target.id) updatedReq else it }
        val nextAlert = if (cur.incomingRequestAlert?.id == target.id) null else cur.incomingRequestAlert
        val updatedState = cur.copy(songRequests = updatedList, incomingRequestAlert = nextAlert, statusBanner = banner)
        _state.value = updatedState
        scope.launch {
            val res = backend.updateSongRequestStatus(cur.room.id, target.id, newStatus)
            store?.saveOrUpdateRequest(updatedReq, syncedRemote = res.isSuccess)
            persistState(updatedState)
        }
    }

    // ---- Host Room Management & Settings ----

    fun setRoomVisibility(visibility: RoomVisibility) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost) return
        val next = cur.copy(
            room = cur.room.copy(visibility = visibility),
            statusBanner = "Room is now ${visibility.name.replace('_', ' ').lowercase()}",
        )
        _state.value = next
        persistState(next)
        scope.launch {
            backend.updateRoomSettings(cur.room.id, cur.room.hostId.ifBlank { cur.currentMemberId }, visibility, cur.controlMode, cur.allowListenerRequests)
        }
    }

    fun toggleControlMode() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost) return
        val nextMode = if (cur.controlMode == ControlMode.HOST_ONLY) ControlMode.COLLABORATIVE else ControlMode.HOST_ONLY
        val next = cur.copy(
            controlMode = nextMode,
            room = cur.room.copy(controlMode = nextMode),
            statusBanner = if (nextMode == ControlMode.COLLABORATIVE) "Anyone can control queue & playback" else "Host-only control enabled",
        )
        _state.value = next
        persistState(next)
        syncEngine.applyAuthoritativePlayback(cur.currentTrack, cur.positionMs, cur.isPlaying, forceSeek = false)
        scope.launch {
            backend.updateRoomSettings(cur.room.id, cur.room.hostId.ifBlank { cur.currentMemberId }, cur.room.visibility, nextMode, cur.allowListenerRequests)
        }
    }

    fun toggleAllowRequests() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost) return
        val nextAllow = !cur.allowListenerRequests
        val next = cur.copy(
            allowListenerRequests = nextAllow,
            room = cur.room.copy(allowListenerRequests = nextAllow),
            statusBanner = if (nextAllow) "Song requests enabled" else "Song requests paused",
        )
        _state.value = next
        persistState(next)
        scope.launch {
            backend.updateRoomSettings(cur.room.id, cur.room.hostId.ifBlank { cur.currentMemberId }, cur.room.visibility, cur.controlMode, nextAllow)
        }
    }

    fun kickMember(memberId: String) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost || memberId == cur.currentMemberId) return
        val target = cur.members.firstOrNull { it.id == memberId } ?: return
        val remaining = cur.members.filterNot { it.id == memberId }
        val next = cur.copy(
            members = remaining,
            room = cur.room.copy(memberCount = remaining.size),
            statusBanner = "Removed ${target.name} from room",
        )
        _state.value = next
        persistState(next)
        scope.launch {
            backend.kickMember(cur.room.id, cur.room.hostId.ifBlank { cur.currentMemberId }, memberId)
        }
    }

    fun leaveRoom() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        val roomId = cur.room.id
        val memberId = cur.currentMemberId
        stopRoomJobs()
        syncEngine.detachRoom()
        playedHistory.clear()

        scope.launch {
            store?.clearActiveSession(roomId)
            backend.leaveRoom(roomId, memberId)
            _state.value = PlayTogetherState.Lobby()
            refreshLobby()
        }
    }

    fun closeRoom() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost) {
            leaveRoom()
            return
        }
        val roomId = cur.room.id
        stopRoomJobs()
        syncEngine.detachRoom()
        playedHistory.clear()

        scope.launch {
            store?.markRoomClosed(roomId)
            backend.closeRoom(roomId, "Host ended the session.")
            _state.value = PlayTogetherState.Lobby()
            refreshLobby()
        }
    }

    fun deleteRoom() {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!cur.isHost) return
        val roomId = cur.room.id
        stopRoomJobs()
        syncEngine.detachRoom()
        playedHistory.clear()

        scope.launch {
            store?.deleteRoom(roomId)
            backend.deleteRoom(roomId, "Host deleted the room.")
            _state.value = PlayTogetherState.Lobby()
            refreshLobby()
        }
    }

    // ---- Discover / Shared Catalog Search ----

    suspend fun searchSongs(
        query: String,
        source: PlayTogetherSearchSource = PlayTogetherSearchSource.MUSIC_SONGS,
        filterTokenOrParams: String? = null,
        continuation: String? = null,
    ): PlayTogetherSearchResult {
        val sender = (_state.value as? PlayTogetherState.InRoom)?.currentUserName
            ?: settings.flow(AppSettings.playTogetherUserName).value
        return trackResolver.searchCatalog(
            query = query,
            source = source,
            filterTokenOrParams = filterTokenOrParams,
            continuation = continuation,
            addedBy = sender,
        )
    }

    // ---- Internal Sync, Event Loop & Persistence ----

    private fun handleExternalHostTransportPlayPause(isPlaying: Boolean, positionMs: Long) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!RoomSecurityPolicy.canControlPlayback(cur.isHost, cur.controlMode)) return
        val track = cur.currentTrack ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val nextSeq = cur.playbackSeq + 1L
        val nextStartUtc = if (isPlaying) (now + cur.clockOffsetMs - positionMs) else 0L

        val next = cur.copy(
            isPlaying = isPlaying,
            positionMs = positionMs,
            playbackSeq = nextSeq,
            playbackStartTimestampUtc = nextStartUtc,
        )
        _state.value = next
        persistState(next)
        dispatchPlaybackEvent(
            PlaybackEvent(
                roomId = cur.room.id,
                senderId = cur.currentMemberId,
                eventType = if (isPlaying) PlaybackEventType.PLAY else PlaybackEventType.PAUSE,
                track = track,
                positionMs = positionMs,
                isPlaying = isPlaying,
                playbackSeq = nextSeq,
                playbackStartTimestampUtc = nextStartUtc,
            ),
        )
    }

    private fun handleExternalHostSeek(positionMs: Long) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!RoomSecurityPolicy.canControlPlayback(cur.isHost, cur.controlMode)) return
        val track = cur.currentTrack ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val nextSeq = cur.playbackSeq + 1L
        val nextStartUtc = if (cur.isPlaying) (now + cur.clockOffsetMs - positionMs) else 0L

        val next = cur.copy(positionMs = positionMs, playbackSeq = nextSeq, playbackStartTimestampUtc = nextStartUtc)
        _state.value = next
        persistState(next)
        dispatchPlaybackEvent(
            PlaybackEvent(
                roomId = cur.room.id,
                senderId = cur.currentMemberId,
                eventType = PlaybackEventType.SEEK,
                track = track,
                positionMs = positionMs,
                isPlaying = cur.isPlaying,
                playbackSeq = nextSeq,
                playbackStartTimestampUtc = nextStartUtc,
            ),
        )
    }

    private fun handleExternalHostNewTrack(track: PlayTogetherTrack, positionMs: Long, isPlaying: Boolean) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (!RoomSecurityPolicy.canControlPlayback(cur.isHost, cur.controlMode)) return
        scope.launch {
            val validated = trackResolver.resolveAndValidate(track).getOrDefault(track)
            val latest = _state.value as? PlayTogetherState.InRoom ?: return@launch
            if (latest.currentTrack?.id == validated.id) return@launch
            latest.currentTrack?.let { prev ->
                if (!prev.isAutoRadio) {
                    playedHistory.addLast(prev)
                    while (playedHistory.size > 30) playedHistory.removeFirst()
                }
            }
            val now = Clock.System.now().toEpochMilliseconds()
            val nextSeq = latest.playbackSeq + 1L
            val nextStartUtc = if (isPlaying) (now + latest.clockOffsetMs - positionMs) else 0L
            val next = latest.copy(
                currentTrack = validated,
                isPlaying = isPlaying,
                positionMs = positionMs,
                playbackSeq = nextSeq,
                playbackStartTimestampUtc = nextStartUtc,
                room = latest.room.copy(currentTrack = validated),
                isAutoRadio = false,
            )
            _state.value = next
            persistState(next)
            dispatchPlaybackEvent(
                PlaybackEvent(
                    roomId = latest.room.id,
                    senderId = latest.currentMemberId,
                    eventType = PlaybackEventType.TRACK_CHANGE,
                    track = validated,
                    positionMs = positionMs,
                    isPlaying = isPlaying,
                    playbackSeq = nextSeq,
                    playbackStartTimestampUtc = nextStartUtc,
                ),
            )
        }
    }

    private fun applyTrackTransition(
        track: PlayTogetherTrack,
        isPlaying: Boolean,
        positionMs: Long,
        removeFromQueueIfPresent: Boolean = false,
        pushToHistory: Boolean = true,
        explicitQueue: List<PlayTogetherTrack>? = null,
    ) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (pushToHistory && cur.currentTrack != null && cur.currentTrack.id != track.id && !cur.currentTrack.isAutoRadio) {
            playedHistory.addLast(cur.currentTrack)
            while (playedHistory.size > 30) playedHistory.removeFirst()
        }

        syncEngine.applyAuthoritativePlayback(track, positionMs, isPlaying, forceSeek = true)
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = updateInRoom { latest ->
            val qChanged = when {
                explicitQueue != null -> explicitQueue != latest.queue
                removeFromQueueIfPresent -> latest.queue.any { it.id == track.id }
                else -> false
            }
            val nextQueue = when {
                explicitQueue != null -> explicitQueue
                removeFromQueueIfPresent -> latest.queue.filterNot { it.id == track.id }
                else -> latest.queue
            }
            val nextSeq = latest.playbackSeq + 1L
            val nextQueueVer = if (qChanged) latest.queueVersion + 1L else latest.queueVersion
            val nextStartUtc = if (isPlaying) (now + latest.clockOffsetMs - positionMs) else 0L
            latest.copy(
                currentTrack = track,
                isPlaying = isPlaying,
                positionMs = positionMs,
                queue = nextQueue,
                room = latest.room.copy(currentTrack = track),
                isAutoRadio = track.isAutoRadio,
                playbackSeq = nextSeq,
                queueVersion = nextQueueVer,
                playbackStartTimestampUtc = nextStartUtc,
                errorMessage = null,
            )
        } ?: return
        persistState(updated)
        val queueChanged = updated.queueVersion > cur.queueVersion

        dispatchPlaybackEvent(
            PlaybackEvent(
                roomId = updated.room.id,
                senderId = updated.currentMemberId,
                eventType = PlaybackEventType.TRACK_CHANGE,
                track = track,
                positionMs = positionMs,
                isPlaying = isPlaying,
                playbackSeq = updated.playbackSeq,
                playbackStartTimestampUtc = updated.playbackStartTimestampUtc,
            ),
        )
        if (queueChanged) {
            dispatchPlaybackEvent(
                PlaybackEvent(
                    roomId = updated.room.id,
                    senderId = updated.currentMemberId,
                    eventType = PlaybackEventType.QUEUE_UPDATE,
                    track = track,
                    queue = updated.queue,
                    queueVersion = updated.queueVersion,
                ),
            )
        }
    }

    private fun commitQueueChange(
        cur: PlayTogetherState.InRoom,
        nextQueue: List<PlayTogetherTrack>,
        banner: String? = null,
    ) {
        val next = updateInRoom { latest ->
            latest.copy(
                queue = nextQueue,
                queueVersion = latest.queueVersion + 1L,
                statusBanner = banner ?: latest.statusBanner,
            )
        } ?: return
        persistState(next)
        dispatchPlaybackEvent(
            PlaybackEvent(
                roomId = next.room.id,
                senderId = next.currentMemberId,
                eventType = PlaybackEventType.QUEUE_UPDATE,
                track = next.currentTrack,
                queue = next.queue,
                queueVersion = next.queueVersion,
            ),
        )
    }

    private fun startRoomObserversAndHeartbeats(
        roomId: String,
        memberId: String,
        userName: String,
        isHost: Boolean,
    ) {
        stopRoomJobs()
        wasOfflineInRoom = false

        activeRoomJob = scope.launch {
            backend.observeRoomEvents(roomId).collect { event ->
                val cur = _state.value as? PlayTogetherState.InRoom ?: return@collect
                if (cur.room.id != roomId) return@collect
                handleIncomingRoomEvent(event, cur)
            }
        }

        presenceJob = scope.launch {
            while (isActive) {
                delay(4000L)
                val cur = _state.value as? PlayTogetherState.InRoom ?: break
                if (cur.room.id != roomId) break

                val mySyncState = when {
                    cur.isBuffering -> MemberSyncState.BUFFERING
                    cur.isActuallySynced -> MemberSyncState.SYNCED
                    else -> MemberSyncState.SYNCING
                }

                val res = backend.updatePresence(
                    roomId = roomId,
                    memberId = memberId,
                    syncState = mySyncState,
                    driftMs = cur.syncOffsetMs,
                    memberName = userName,
                    isHost = isHost,
                )

                if (res.isFailure) {
                    wasOfflineInRoom = true
                    updateInRoom { latest ->
                        latest.copy(
                            connectionState = PlaybackConnectionState.RECONNECTING,
                            isActuallySynced = false,
                        )
                    }
                } else {
                    if (wasOfflineInRoom) {
                        wasOfflineInRoom = false
                        val snap = backend.fetchLatestRoomSnapshot(roomId, memberId, userName, isHost)?.getOrNull()
                        if (snap != null) applyRemoteSnapshotRecovery(snap)
                        flushUnsyncedRequests(roomId)
                    } else if (cur.connectionState != PlaybackConnectionState.CONNECTED) {
                        updateInRoom { latest -> latest.copy(connectionState = PlaybackConnectionState.CONNECTED) }
                    }
                }
            }
        }

        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val cur = _state.value as? PlayTogetherState.InRoom ?: break
                if (cur.room.id != roomId) break
                val track = cur.currentTrack ?: continue
                if (cur.isPlaying && !syncEngine.isPlayerBuffering()) {
                    val livePlayerPos = syncEngine.currentPlayerPositionMs()
                    if (livePlayerPos > 0L) {
                        updateInRoom { latest ->
                            val tr = latest.currentTrack ?: return@updateInRoom latest
                            val updated = if (tr.durationMs > 0L) livePlayerPos.coerceIn(0L, tr.durationMs) else livePlayerPos
                            latest.copy(positionMs = updated)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingRoomEvent(event: RoomEvent, cur: PlayTogetherState.InRoom) {
        when (event) {
            is RoomEvent.PlaybackUpdated -> {
                if (event.senderId == cur.currentMemberId) return
                if (event.playbackSeq > 0L && event.playbackSeq <= cur.playbackSeq) return
                if (event.eventId.isNotBlank()) {
                    val isNew = store?.recordEventIfNew(cur.room.id, event.eventId) ?: true
                    if (!isNew) return
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val adjustedNow = now + cur.clockOffsetMs
                val halfRtt = (cur.networkRttMs / 2L).coerceIn(0L, 1500L)
                val elapsedSinceStart = if (event.isPlaying && event.playbackStartTimestampUtc > 0L) {
                    (adjustedNow - event.playbackStartTimestampUtc).coerceAtLeast(event.positionMs.coerceAtLeast(0L))
                } else if (event.isPlaying) {
                    event.positionMs + halfRtt
                } else {
                    event.positionMs
                }

                val trackChanged = event.track?.id != cur.currentTrack?.id
                val precomputedTrack = event.track ?: cur.currentTrack
                val precomputedPos = if (precomputedTrack != null && precomputedTrack.durationMs > 0L) {
                    elapsedSinceStart.coerceIn(0L, precomputedTrack.durationMs)
                } else {
                    elapsedSinceStart.coerceAtLeast(0L)
                }
                syncEngine.applyAuthoritativePlayback(
                    track = precomputedTrack,
                    targetPositionMs = precomputedPos,
                    playWhenReady = event.isPlaying,
                    forceSeek = trackChanged || kotlin.math.abs(syncEngine.currentPlayerPositionMs() - precomputedPos) > 1500L,
                )
                val next = updateInRoom { latest ->
                    if (event.playbackSeq > 0L && event.playbackSeq <= latest.playbackSeq) return@updateInRoom latest
                    val targetTrack = event.track ?: latest.currentTrack
                    val clampedPos = if (targetTrack != null && targetTrack.durationMs > 0L) {
                        elapsedSinceStart.coerceIn(0L, targetTrack.durationMs)
                    } else {
                        elapsedSinceStart.coerceAtLeast(0L)
                    }
                    val tChanged = targetTrack?.id != latest.currentTrack?.id
                    val nextQueue = if (tChanged && targetTrack != null) {
                        latest.queue.filterNot { it.id == targetTrack.id }
                    } else {
                        latest.queue
                    }
                    latest.copy(
                        currentTrack = targetTrack,
                        isPlaying = event.isPlaying,
                        positionMs = clampedPos,
                        queue = nextQueue,
                        room = latest.room.copy(currentTrack = targetTrack),
                        isAutoRadio = targetTrack?.isAutoRadio == true,
                        playbackSeq = if (event.playbackSeq > 0L) event.playbackSeq else latest.playbackSeq + 1L,
                        playbackStartTimestampUtc = event.playbackStartTimestampUtc,
                        connectionState = PlaybackConnectionState.CONNECTED,
                    )
                } ?: return
                persistState(next)
            }

            is RoomEvent.QueueChanged -> {
                if (event.senderId == cur.currentMemberId) return
                if (event.queueVersion > 0L && event.queueVersion <= cur.queueVersion) return
                if (event.eventId.isNotBlank()) {
                    val isNew = store?.recordEventIfNew(cur.room.id, event.eventId) ?: true
                    if (!isNew) return
                }
                val next = updateInRoom { latest ->
                    if (event.queueVersion > 0L && event.queueVersion <= latest.queueVersion) return@updateInRoom latest
                    latest.copy(
                        queue = event.queue,
                        currentTrack = event.currentTrack ?: latest.currentTrack,
                        queueVersion = if (event.queueVersion > 0L) event.queueVersion else latest.queueVersion + 1L,
                    )
                } ?: return
                persistState(next)
            }

            is RoomEvent.SongRequestsChanged -> {
                val next = updateInRoom { latest ->
                    val merged = mergeRequestLists(event.requests, latest.songRequests)
                    val incomingAlert = if (latest.isHost && event.addedRequest != null &&
                        event.addedRequest.status == SongRequestStatus.PENDING &&
                        event.addedRequest.requesterId != latest.currentMemberId
                    ) {
                        event.addedRequest
                    } else {
                        latest.incomingRequestAlert
                    }
                    latest.copy(songRequests = merged, incomingRequestAlert = incomingAlert)
                } ?: return
                next.songRequests.forEach { req -> store?.saveOrUpdateRequest(req.copy(roomId = next.room.id), syncedRemote = true) }
                persistState(next)
            }

            is RoomEvent.MemberPresenceChanged -> {
                updateInRoom { latest ->
                    val mergedMembers = event.members.map { remote ->
                        if (remote.id == latest.currentMemberId) {
                            remote.copy(
                                syncState = if (latest.isBuffering) MemberSyncState.BUFFERING else if (latest.isActuallySynced) MemberSyncState.SYNCED else MemberSyncState.SYNCING,
                                driftMs = latest.syncOffsetMs,
                            )
                        } else {
                            remote
                        }
                    }
                    latest.copy(
                        members = mergedMembers,
                        room = latest.room.copy(memberCount = mergedMembers.size.coerceAtLeast(1)),
                    )
                }
            }

            is RoomEvent.MemberJoined -> {
                updateInRoom { latest ->
                    val exists = latest.members.any { it.id == event.member.id }
                    val updated = if (exists) latest.members.map { if (it.id == event.member.id) event.member else it } else latest.members + event.member
                    val banner = if (event.member.id != latest.currentMemberId) "${event.member.name} joined" else latest.statusBanner
                    latest.copy(
                        members = updated,
                        room = latest.room.copy(memberCount = updated.size),
                        statusBanner = banner,
                    )
                }
            }

            is RoomEvent.MemberLeft -> {
                updateInRoom { latest ->
                    val updated = latest.members.filterNot { it.id == event.memberId }
                    latest.copy(
                        members = updated,
                        room = latest.room.copy(memberCount = updated.size.coerceAtLeast(1)),
                        statusBanner = "${event.memberName} left",
                    )
                }
            }

            is RoomEvent.MemberKicked -> {
                if (event.targetMemberId == cur.currentMemberId) {
                    stopRoomJobs()
                    syncEngine.detachRoom()
                    store?.clearActiveSession(cur.room.id)
                    _state.value = PlayTogetherState.Lobby(errorMessage = event.reason)
                    refreshLobby()
                }
            }

            is RoomEvent.RoomSettingsChanged -> {
                val next = cur.copy(
                    room = cur.room.copy(
                        visibility = event.visibility,
                        controlMode = event.controlMode,
                        allowListenerRequests = event.allowListenerRequests,
                    ),
                    controlMode = event.controlMode,
                    allowListenerRequests = event.allowListenerRequests,
                )
                _state.value = next
                persistState(next)
            }

            is RoomEvent.RoomClosed -> {
                stopRoomJobs()
                syncEngine.detachRoom()
                playedHistory.clear()
                if (event.deleted) {
                    store?.deleteRoom(cur.room.id)
                } else {
                    store?.markRoomClosed(cur.room.id)
                }
                _state.value = PlayTogetherState.Lobby(errorMessage = event.reason)
                refreshLobby()
            }

            is RoomEvent.Error -> {
                _state.value = cur.copy(errorMessage = event.message)
            }
        }
    }

    private suspend fun applyRemoteSnapshotRecovery(snap: io.ymusic.ui.playtogether.backend.JoinRoomResult) {
        val cur = _state.value as? PlayTogetherState.InRoom ?: return
        if (snap.room.isClosed) {
            stopRoomJobs()
            syncEngine.detachRoom()
            store?.markRoomClosed(cur.room.id)
            _state.value = PlayTogetherState.Lobby(errorMessage = "Room has ended.")
            refreshLobby()
            return
        }
        val mergedRequests = mergeRequestLists(snap.songRequests, cur.songRequests)
        val useRemotePlayback = !cur.isHost && snap.playbackSeq > cur.playbackSeq
        val nextTrack = if (useRemotePlayback) snap.currentTrack else cur.currentTrack
        val nextPlaying = if (useRemotePlayback) snap.isPlaying else cur.isPlaying
        val nextPos = if (useRemotePlayback) snap.positionMs else cur.positionMs
        val nextQueue = if (!cur.isHost && snap.queueVersion >= cur.queueVersion) snap.queue else cur.queue

        val updated = cur.copy(
            room = snap.room,
            members = snap.members,
            currentTrack = nextTrack,
            isPlaying = nextPlaying,
            positionMs = nextPos,
            queue = nextQueue,
            songRequests = mergedRequests,
            controlMode = snap.room.controlMode,
            allowListenerRequests = snap.room.allowListenerRequests,
            playbackSeq = maxOf(cur.playbackSeq, snap.playbackSeq),
            queueVersion = maxOf(cur.queueVersion, snap.queueVersion),
            connectionState = PlaybackConnectionState.CONNECTED,
        )
        _state.value = updated
        persistState(updated)
        if (useRemotePlayback && nextTrack != null) {
            syncEngine.applyAuthoritativePlayback(nextTrack, nextPos, nextPlaying, forceSeek = true)
        }
    }

    private suspend fun flushUnsyncedRequests(roomId: String) {
        val unsynced = runCatching { store?.listUnsyncedRequests(roomId) }.getOrNull().orEmpty()
        for (req in unsynced) {
            val res = if (req.status == SongRequestStatus.PENDING) {
                backend.submitSongRequest(roomId, req)
            } else {
                backend.updateSongRequestStatus(roomId, req.id, req.status)
            }
            if (res.isSuccess) {
                store?.markRequestSynced(req.id)
            }
        }
    }

    private fun dispatchPlaybackEvent(event: PlaybackEvent) {
        scope.launch {
            val res = backend.sendPlaybackEvent(event.roomId, event)
            if (res.isFailure) {
                val cur = _state.value as? PlayTogetherState.InRoom ?: return@launch
                _state.value = cur.copy(connectionState = PlaybackConnectionState.RECONNECTING)
            }
        }
    }

    private fun ensurePlaybackControl(cur: PlayTogetherState.InRoom): Boolean {
        if (RoomSecurityPolicy.canControlPlayback(cur.isHost, cur.controlMode)) return true
        _state.value = cur.copy(
            statusBanner = "Only the host (${cur.room.hostName}) can control playback right now. Send a song request instead!",
        )
        return false
    }

    private fun setStatusOrError(error: String? = null, banner: String? = null) {
        _state.update { cur ->
            when (cur) {
                is PlayTogetherState.InRoom -> cur.copy(
                    errorMessage = error ?: cur.errorMessage,
                    statusBanner = banner ?: cur.statusBanner,
                )
                is PlayTogetherState.Lobby -> cur.copy(errorMessage = error ?: cur.errorMessage)
            }
        }
    }

    private fun persistState(inRoom: PlayTogetherState.InRoom) {
        scope.launch {
            runCatching { store?.saveActiveSession(inRoom) }
                .onFailure { logger?.fork("pt-store")?.log(Logger.Level.WARN, "Failed to persist Play Together state", it) }
        }
    }

    private fun stopRoomJobs() {
        activeRoomJob?.cancel()
        activeRoomJob = null
        presenceJob?.cancel()
        presenceJob = null
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun mergeRequestLists(primary: List<SongRequest>, secondary: List<SongRequest>): List<SongRequest> {
        val byId = LinkedHashMap<String, SongRequest>()
        for (req in secondary) byId[req.id] = req
        for (req in primary) {
            val existing = byId[req.id]
            if (existing == null || req.updatedTimeMs >= existing.updatedTimeMs) {
                byId[req.id] = req
            }
        }
        return byId.values.sortedByDescending { it.requestTimeMs }
    }
}
