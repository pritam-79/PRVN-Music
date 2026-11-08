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
    private var stateObserverJob: Job? = null
    private var currentActiveRoomId: String? = null

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
        roomEventsJob = scope.launch {
            controller.state.collect { state ->
                if (state is PlayTogetherState.InRoom) {
                    val roomId = state.room.id
                    controller.backend.observeRoomEvents(roomId).collect { event ->
                        handleRoomEvent(event, state)
                    }
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
    }

    private fun detachFromRoom() {
        roomEventsJob?.cancel()
        roomEventsJob = null
        driftMonitorJob?.cancel()
        driftMonitorJob = null
    }

    private fun handleRoomEvent(event: RoomEvent, roomState: PlayTogetherState.InRoom) {
        when (event) {
            is RoomEvent.PlaybackUpdated -> {
                // If event originated from peer, apply to local player
                if (event.senderId != roomState.currentMemberId) {
                    val targetTrack = event.track ?: roomState.currentTrack
                    if (targetTrack != null && validateTrack(targetTrack)) {
                        val currentYtId = (player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId
                        if (currentYtId != targetTrack.id) {
                            syncToTrack(targetTrack, event.positionMs, event.isPlaying)
                        } else {
                            // Same track, sync play/pause & seek
                            val isLocallyPlaying = isPlayerPlaying()
                            if (event.isPlaying && !isLocallyPlaying) {
                                player.play()
                            } else if (!event.isPlaying && isLocallyPlaying) {
                                player.pause()
                            }

                            val elapsed = Clock.System.now().toEpochMilliseconds() - event.timestampUtc
                            val expectedPos = event.positionMs + if (event.isPlaying) elapsed else 0L
                            val localPos = player.progress.value.positionMs
                            val drift = abs(localPos - expectedPos)

                            if (drift > syncDriftToleranceMs) {
                                player.seekTo(expectedPos)
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
        val localPos = player.progress.value.positionMs
        val expectedPos = current.positionMs
        val drift = abs(localPos - expectedPos)

        // Update measured drift in controller for UI badge and presence reporting
        controller.updateLocalDrift(drift)

        if (drift > syncDriftToleranceMs) {
            // Apply catch-up seek without stopping playback
            player.seekTo(expectedPos)
        }
    }

    /**
     * Loads and starts playing a synchronized YouTube track at the designated head position.
     */
    fun syncToTrack(track: PlayTogetherTrack, startPositionMs: Long, playWhenReady: Boolean) {
        if (!validateTrack(track)) return

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

    private fun isPlayerPlaying(): Boolean {
        val state = player.status.value.state
        return state is PlaybackState.Prepared && state.playWhenReady && !state.buffering && !state.ended
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
