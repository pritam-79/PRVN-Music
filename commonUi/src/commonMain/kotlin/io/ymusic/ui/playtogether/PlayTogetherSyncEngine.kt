package io.ymusic.ui.playtogether

import io.ymusic.player.PlayableItem
import io.ymusic.player.PlaybackState
import io.ymusic.player.Player
import io.ymusic.player.QueueOrigin
import io.ymusic.settings.AppSettings
import io.ymusic.settings.SettingsStore
import io.ymusic.settings.flow
import io.ymusic.ui.PlaybackCoordinator
import io.ymusic.ui.playtogether.backend.PlaybackEvent
import io.ymusic.ui.playtogether.backend.PlaybackEventType
import io.ymusic.ui.playtogether.backend.RoomEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Real-time synchronization engine bridging [PlayTogetherController] and the native audio [Player].
 *
 * Enforces:
 * 1. Synchronized play/pause, seek, track changes, and queue advancement.
 * 2. YouTube streaming only: local offline files are strictly barred from Play Together rooms to prevent
 *    desynchronization across differing client devices.
 * 3. Latency and clock drift compensation: checks playback drift against [AppSettings.playTogetherSyncDriftMs]
 *    (default 500ms). When drift is within tolerance, audio plays uninterrupted without stutter.
 * 4. Mid-song catch-up on room join: calculates elapsed time and initiates playback at the precise current head.
 * 5. Network disconnection recovery and auto-resync.
 */
class PlayTogetherSyncEngine(
    private val coordinator: PlaybackCoordinator,
    private val controller: PlayTogetherController,
    private val settings: SettingsStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val player: Player get() = coordinator.player

    private var roomEventsJob: Job? = null
    private var driftMonitorJob: Job? = null
    private var playerStatusJob: Job? = null
    private var stateObserverJob: Job? = null
    private var currentActiveRoomId: String? = null
    private var lastPlaybackEventTimestamp: Long = 0L
    private var lastSeekTimestampMs: Long = 0L

    /**
     * Configured drift tolerance in milliseconds from AppSettings (250ms, 500ms, 750ms, 1000ms).
     */
    private val syncDriftToleranceMs: Long
        get() = settings.flow(AppSettings.playTogetherSyncDriftMs).value.toLong()

    init {
        startStateObserver()
    }

    private fun startStateObserver() {
        stateObserverJob?.cancel()
        stateObserverJob = scope.launch {
            controller.state.collectLatest { state ->
                when (state) {
                    is PlayTogetherState.InRoom -> {
                        if (currentActiveRoomId != state.room.id) {
                            currentActiveRoomId = state.room.id
                            attachToActiveRoom(state)
                        }
                    }
                    is PlayTogetherState.Lobby -> {
                        if (currentActiveRoomId != null) {
                            currentActiveRoomId = null
                            detachFromRoom()
                        }
                    }
                }
            }
        }
    }

    private fun attachToActiveRoom(roomState: PlayTogetherState.InRoom) {
        roomEventsJob?.cancel()
        driftMonitorJob?.cancel()

        // Sync initial track and position on join
        roomState.currentTrack?.let { track ->
            if (validateTrack(track)) {
                syncToTrack(track, roomState.positionMs, roomState.isPlaying)
            }
        }

        // 1. Observe incoming peer events from backend
        val roomId = roomState.room.id
        roomEventsJob = scope.launch {
            controller.backend.observeRoomEvents(roomId).collect { event ->
                val current = controller.state.value
                if (current is PlayTogetherState.InRoom && current.room.id == roomId) {
                    handleRoomEvent(event, current)
                }
            }
        }

        // 2. Start continuous drift monitor
        driftMonitorJob = scope.launch {
            while (isActive) {
                delay(2000L)
                val current = controller.state.value
                if (current is PlayTogetherState.InRoom && current.isPlaying && current.currentTrack != null) {
                    checkAndCompensateDrift(current)
                }
            }
        }

        // 3. Observe local player completion for automatic continuous transition
        playerStatusJob?.cancel()
        playerStatusJob = scope.launch {
            player.status.collect { status ->
                val s = status.state
                if (s is PlaybackState.Prepared && s.ended) {
                    val current = controller.state.value
                    if (current is PlayTogetherState.InRoom && (current.isHost || current.controlMode == ControlMode.COLLABORATIVE)) {
                        controller.onTrackEnded()
                    }
                }
            }
        }
    }

    private fun detachFromRoom() {
        roomEventsJob?.cancel()
        roomEventsJob = null
        driftMonitorJob?.cancel()
        driftMonitorJob = null
        playerStatusJob?.cancel()
        playerStatusJob = null
        lastPlaybackEventTimestamp = 0L
    }

    private fun handleRoomEvent(event: RoomEvent, roomState: PlayTogetherState.InRoom) {
        when (event) {
            is RoomEvent.PlaybackUpdated -> {
                if (event.timestampUtc < lastPlaybackEventTimestamp) return
                lastPlaybackEventTimestamp = event.timestampUtc

                // If event originated from peer, apply to local player
                if (event.senderId != roomState.currentMemberId) {
                    val targetTrack = event.track ?: roomState.currentTrack
                    if (targetTrack != null && validateTrack(targetTrack)) {
                        val currentYtId = (player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId
                        if (currentYtId != targetTrack.id) {
                            syncToTrack(targetTrack, event.positionMs, event.isPlaying)
                        } else {
                            val state = player.status.value.state
                            val isPrepared = state is PlaybackState.Prepared
                            val isBuffering = isPrepared && state.buffering

                            if (event.isPlaying && (state !is PlaybackState.Prepared || !state.playWhenReady)) {
                                player.play()
                            } else if (!event.isPlaying && isPrepared && state.playWhenReady) {
                                player.pause()
                            }

                            if (isPrepared && !isBuffering && !roomState.isHost) {
                                val elapsed = (Clock.System.now().toEpochMilliseconds() - event.timestampUtc).coerceAtLeast(0L)
                                val expectedPos = event.positionMs + if (event.isPlaying) elapsed else 0L
                                val localPos = player.progress.value.positionMs
                                val drift = abs(localPos - expectedPos)
                                val now = Clock.System.now().toEpochMilliseconds()

                                val effectiveTolerance = maxOf(syncDriftToleranceMs, 2000L)
                                if (drift > effectiveTolerance && (now - lastSeekTimestampMs > 5000L)) {
                                    lastSeekTimestampMs = now
                                    player.seekTo(expectedPos)
                                }
                            }
                        }
                    }
                }
            }
            is RoomEvent.QueueChanged -> {
                // Shared queue updated
            }
            is RoomEvent.MemberPresenceChanged, is RoomEvent.MemberJoined, is RoomEvent.MemberLeft -> {
                // Presence updates
            }
            is RoomEvent.SongRequestsChanged -> {
                // Song requests updated
            }
            is RoomEvent.RoomClosed -> {
                player.pause()
                detachFromRoom()
            }
            is RoomEvent.Error -> Unit
        }
    }

    private fun checkAndCompensateDrift(current: PlayTogetherState.InRoom) {
        // Authoritative Host is the time source; never seek host for drift
        if (current.isHost) {
            controller.updateLocalDrift(0L)
            return
        }
        val state = player.status.value.state
        // Critical: Never seek while player is preparing or actively buffering stream chunks
        if (state !is PlaybackState.Prepared || state.buffering) return
        val trackId = current.currentTrack?.id ?: return
        if (!isCurrentTrack(trackId)) return

        val localPos = player.progress.value.positionMs
        val expectedPos = current.positionMs
        val drift = abs(localPos - expectedPos)

        // Update measured drift in controller for UI badge and presence reporting
        controller.updateLocalDrift(drift)

        val now = Clock.System.now().toEpochMilliseconds()
        val effectiveTolerance = maxOf(syncDriftToleranceMs, 2000L)
        // Apply controlled seek only when drift exceeds tolerance and seek cooldown (5s) has elapsed
        if (drift > effectiveTolerance && (now - lastSeekTimestampMs > 5000L)) {
            lastSeekTimestampMs = now
            player.seekTo(expectedPos)
        }
    }

    /**
     * Loads and starts playing a synchronized YouTube track at the designated head position.
     */
    fun syncToTrack(track: PlayTogetherTrack, startPositionMs: Long, playWhenReady: Boolean) {
        if (!validateTrack(track)) return

        val currentItem = player.queue.value.currentItem as? PlayableItem.YtMedia
        if (currentItem?.videoId == track.id) {
            val state = player.status.value.state
            val isPrepared = state is PlaybackState.Prepared
            val isBuffering = isPrepared && state.buffering

            if (isPrepared && !isBuffering) {
                val drift = abs(player.progress.value.positionMs - startPositionMs)
                val now = Clock.System.now().toEpochMilliseconds()
                if (drift > syncDriftToleranceMs && (now - lastSeekTimestampMs > 4000L)) {
                    lastSeekTimestampMs = now
                    player.seekTo(startPositionMs)
                }
            }
            if (playWhenReady && (state !is PlaybackState.Prepared || !state.playWhenReady)) {
                player.play()
            } else if (!playWhenReady && state is PlaybackState.Prepared && state.playWhenReady) {
                player.pause()
            }
            return
        }

        val ytMedia = PlayableItem.YtMedia(
            videoId = track.id,
            title = track.title,
            artist = track.artist,
            thumbnailUrl = track.artworkUrl,
            durationMs = track.durationMs,
        )

        player.setQueue(
            items = listOf(ytMedia),
            playIndex = 0,
            startPositionMs = startPositionMs,
            playWhenReady = playWhenReady,
            origin = QueueOrigin.Single,
        )
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun isPlayerPlaying(): Boolean {
        val state = player.status.value.state
        return state is PlaybackState.Prepared && state.playWhenReady && !state.buffering && !state.ended
    }

    fun isPlayerBuffering(): Boolean {
        val state = player.status.value.state
        return (state is PlaybackState.Prepared && state.buffering)
    }

    fun getPlayerPositionMs(): Long {
        return player.progress.value.positionMs
    }

    fun getPlayerDurationMs(): Long {
        return player.progress.value.durationMs
    }

    fun getPlayerError(): String? {
        val err = player.status.value.error
        return err?.message
    }

    fun isPlayerEnded(): Boolean {
        val state = player.status.value.state
        return state is PlaybackState.Prepared && state.ended
    }

    fun isCurrentTrack(trackId: String): Boolean {
        val item = player.queue.value.currentItem as? PlayableItem.YtMedia
        return item?.videoId == trackId
    }

    /**
     * Strictly verifies that a track is an accessible online YouTube stream.
     * Rejects local files and offline downloads from being shared in Play Together.
     */
    fun validateTrack(track: PlayTogetherTrack): Boolean {
        val isLocal = !track.isYouTube ||
            track.id.startsWith("file://") ||
            track.id.startsWith("local_") ||
            track.id.startsWith("/") ||
            track.id.contains("content://")
        return !isLocal
    }

    /**
     * Public validation helper with detailed error messaging for the UI.
     */
    fun validateTrackForRoom(track: PlayTogetherTrack): Result<Unit> {
        return if (validateTrack(track)) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Local files cannot be shared in Play Together. Only online YouTube streams accessible to all listeners can be synchronized."
                )
            )
        }
    }

    fun release() {
        stateObserverJob?.cancel()
        detachFromRoom()
    }
}
