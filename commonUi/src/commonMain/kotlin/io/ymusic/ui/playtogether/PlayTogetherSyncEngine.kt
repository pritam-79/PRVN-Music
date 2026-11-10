package io.ymusic.ui.playtogether

import io.ymusic.player.PlayableItem
import io.ymusic.player.PlaybackState
import io.ymusic.player.Player
import io.ymusic.player.QueueOrigin
import io.ymusic.settings.AppSettings
import io.ymusic.settings.SettingsStore
import io.ymusic.settings.flow
import io.ymusic.ui.PlaybackCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class SyncTelemetry(
    val trackId: String? = null,
    val localPositionMs: Long = 0L,
    val expectedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val driftMs: Long = 0L,
    val isPrepared: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlayingLocally: Boolean = false,
    val isActuallySynced: Boolean = false,
    val playerError: String? = null,
)

interface PlayTogetherSyncCallbacks {
    fun currentRoomSnapshot(): PlayTogetherState.InRoom?
    fun onTelemetryUpdate(telemetry: SyncTelemetry)
    fun onTrackEnded(finishedTrackId: String)
    fun onHostTransportPlayPause(isPlaying: Boolean, positionMs: Long)
    fun onHostTransportSeek(positionMs: Long)
    fun onHostPlayedNewTrack(track: PlayTogetherTrack, positionMs: Long, isPlaying: Boolean)
}

/**
 * Single authoritative bridge between [PlayTogetherController] and the app's single [Player].
 *
 * Guarantees:
 * - Zero duplicate audio engines (drives [PlaybackCoordinator.player] exclusively).
 * - Monotonic-clock playback anchor preventing cross-device wall-clock skew desync.
 * - Buffering-aware, cooldown-guarded drift correction without audio stuttering.
 * - Loop-free bidirectional sync when Host uses MiniPlayer, NowPlaying, Notification, or Discover.
 * - Honest sync telemetry (never reports fake synced status when idle, preparing, buffering, or errored).
 */
class PlayTogetherSyncEngine(
    private val coordinator: PlaybackCoordinator,
    private val trackResolver: TrackResolver,
    private val settings: SettingsStore,
    private val callbacks: PlayTogetherSyncCallbacks,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val player: Player get() = coordinator.player

    private var statusObserverJob: Job? = null
    private var driftLoopJob: Job? = null
    private var seekDebounceJob: Job? = null
    private var attachedRoomId: String? = null

    private var applyingRemoteUntilMs: Long = 0L
    private var lastSeekCorrectionMs: Long = 0L
    private var lastReportedEndedKey: String? = null
    private var lastObservedPlayWhenReady: Boolean? = null
    private var lastObservedVideoId: String? = null
    private var lastProgressSamplePosMs: Long = 0L
    private var lastProgressSampleAtMs: Long = 0L

    // Monotonic clock anchor for rock-solid drift estimation independent of timezone/wall-clock skew
    private var anchorTrackId: String? = null
    private var anchorBasePositionMs: Long = 0L
    private var anchorIsPlaying: Boolean = false
    private var anchorMarkedAt: TimeMark = TimeSource.Monotonic.markNow()

    private val syncToleranceMs: Long
        get() = settings.flow(AppSettings.playTogetherSyncDriftMs).value.toLong().coerceAtLeast(250L)

    fun attachRoom(roomId: String, initialTrack: PlayTogetherTrack?, initialPosMs: Long, isPlaying: Boolean) {
        if (attachedRoomId != roomId) {
            attachedRoomId = roomId
            lastReportedEndedKey = null
            lastSeekCorrectionMs = 0L
            startObservers()
        }
        updateMonotonicAnchor(initialTrack?.id, initialPosMs, isPlaying)
        if (initialTrack != null) {
            val currentItem = player.queue.value.currentItem as? PlayableItem.YtMedia
            val alreadyLoadedSame = currentItem?.videoId == initialTrack.id
            // Host creating room from currently playing music keeps playing smoothly without reset glitch!
            if (alreadyLoadedSame) {
                val st = player.status.value
                lastObservedVideoId = initialTrack.id
                lastObservedPlayWhenReady = st.playWhenReady
                updateMonotonicAnchor(initialTrack.id, player.progress.value.positionMs, st.playWhenReady)
                emitTelemetryNow()
            } else {
                applyAuthoritativePlayback(
                    track = initialTrack,
                    targetPositionMs = initialPosMs,
                    playWhenReady = isPlaying,
                    forceSeek = true,
                )
            }
        } else {
            emitTelemetryNow()
        }
    }

    fun detachRoom() {
        attachedRoomId = null
        statusObserverJob?.cancel()
        statusObserverJob = null
        driftLoopJob?.cancel()
        driftLoopJob = null
        seekDebounceJob?.cancel()
        seekDebounceJob = null
        lastReportedEndedKey = null
        lastObservedPlayWhenReady = null
        lastObservedVideoId = null
        anchorTrackId = null
    }

    /** Update expected room timeline anchor whenever authoritative state changes. */
    fun updateMonotonicAnchor(trackId: String?, basePositionMs: Long, isPlaying: Boolean) {
        anchorTrackId = trackId
        anchorBasePositionMs = basePositionMs.coerceAtLeast(0L)
        anchorIsPlaying = isPlaying
        anchorMarkedAt = TimeSource.Monotonic.markNow()
    }

    fun expectedPositionMs(durationLimitMs: Long = 0L): Long {
        if (anchorTrackId == null) return 0L
        val elapsed = if (anchorIsPlaying) anchorMarkedAt.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L) else 0L
        val raw = (anchorBasePositionMs + elapsed).coerceAtLeast(0L)
        return if (durationLimitMs > 0L) raw.coerceIn(0L, durationLimitMs) else raw
    }

    /**
     * Applies authoritative track/play/pause/seek directly to [Player] without firing host bounce-back loops.
     */
    fun applyAuthoritativePlayback(
        track: PlayTogetherTrack?,
        targetPositionMs: Long,
        playWhenReady: Boolean,
        forceSeek: Boolean = false,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        applyingRemoteUntilMs = now + 700L

        if (track == null) {
            updateMonotonicAnchor(null, 0L, false)
            player.pause()
            lastObservedPlayWhenReady = false
            emitTelemetryNow()
            return
        }

        if (!TrackResolver.isValidYouTubeId(track.id) || TrackResolver.isLocalTrackId(track.id)) {
            emitTelemetryNow()
            return
        }

        updateMonotonicAnchor(track.id, targetPositionMs, playWhenReady)

        val currentItem = player.queue.value.currentItem as? PlayableItem.YtMedia
        val status = player.status.value
        val state = status.state

        if (currentItem?.videoId != track.id) {
            lastReportedEndedKey = null
            lastObservedVideoId = track.id
            lastObservedPlayWhenReady = playWhenReady
            lastProgressSamplePosMs = targetPositionMs
            lastProgressSampleAtMs = now

            player.setQueue(
                items = listOf(trackResolver.toPlayableMedia(track)),
                playIndex = 0,
                startPositionMs = targetPositionMs.coerceAtLeast(0L),
                playWhenReady = playWhenReady,
                origin = QueueOrigin.Single,
            )
        } else {
            lastObservedVideoId = track.id
            val isPrepared = state is PlaybackState.Prepared
            val isBuffering = isPrepared && state.buffering

            if (forceSeek || (isPrepared && !isBuffering && abs(player.progress.value.positionMs - targetPositionMs) > maxOf(syncToleranceMs, 1000L))) {
                lastSeekCorrectionMs = now
                lastProgressSamplePosMs = targetPositionMs
                lastProgressSampleAtMs = now
                player.seekTo(targetPositionMs.coerceAtLeast(0L))
            }

            if (playWhenReady && !status.playWhenReady) {
                lastObservedPlayWhenReady = true
                player.play()
            } else if (!playWhenReady && status.playWhenReady) {
                lastObservedPlayWhenReady = false
                player.pause()
            }
        }
        emitTelemetryNow()
    }

    fun forceResyncToRoomHead() {
        val room = callbacks.currentRoomSnapshot() ?: return
        val track = room.currentTrack ?: return
        val expected = expectedPositionMs(track.durationMs)
        applyAuthoritativePlayback(
            track = track,
            targetPositionMs = expected,
            playWhenReady = room.isPlaying,
            forceSeek = true,
        )
    }

    fun isPlayerBuffering(): Boolean {
        val s = player.status.value.state
        return s is PlaybackState.Prepared && s.buffering
    }

    fun currentPlayerPositionMs(): Long = player.progress.value.positionMs

    fun currentPlayerDurationMs(): Long = player.progress.value.durationMs

    private fun startObservers() {
        statusObserverJob?.cancel()
        driftLoopJob?.cancel()

        statusObserverJob = scope.launch {
            combine(player.status, player.queue) { status, queue -> status to queue }
                .collect { (status, queue) ->
                    val room = callbacks.currentRoomSnapshot() ?: return@collect
                    val state = status.state
                    val now = Clock.System.now().toEpochMilliseconds()
                    val isApplyingRemote = now <= applyingRemoteUntilMs
                    val canControl = room.isHost || room.controlMode == ControlMode.COLLABORATIVE

                    // 1. Track ended detection (triggers host/collaborative next song or auto-radio)
                    val endedTrackId = (queue.currentItem as? PlayableItem.YtMedia)?.videoId
                    if (state is PlaybackState.Prepared && state.ended && endedTrackId != null) {
                        val endKey = "${room.room.id}_$endedTrackId"
                        if (lastReportedEndedKey != endKey && endedTrackId == room.currentTrack?.id) {
                            lastReportedEndedKey = endKey
                            if (canControl) {
                                callbacks.onTrackEnded(endedTrackId)
                            }
                        }
                    } else if (state is PlaybackState.Prepared && !state.ended) {
                        lastReportedEndedKey = null
                    }

                    // 2. Listener buffer-ready immediate catch-up (only on remote room playback)
                    if (!room.isHost && !isApplyingRemote && state is PlaybackState.Prepared && !state.buffering && room.isPlaying) {
                        val expected = expectedPositionMs(room.currentTrack?.durationMs ?: 0L)
                        val localPos = player.progress.value.positionMs
                        val drift = abs(localPos - expected)
                        if (endedTrackId == room.currentTrack?.id && drift > maxOf(syncToleranceMs, 1500L) && (now - lastSeekCorrectionMs > 3000L)) {
                            lastSeekCorrectionMs = now
                            applyingRemoteUntilMs = now + 450L
                            player.seekTo(expected)
                        }
                    }

                    // 3. Detect local user interactions from MiniPlayer / NowPlaying / Lockscreen / Discover
                    val curYt = queue.currentItem as? PlayableItem.YtMedia
                    if (!isApplyingRemote && canControl) {
                        if (curYt != null && curYt.videoId != lastObservedVideoId && curYt.videoId != room.currentTrack?.id) {
                            lastObservedVideoId = curYt.videoId
                            lastObservedPlayWhenReady = status.playWhenReady
                            val track = TrackResolver.fromPlayableItem(curYt, addedBy = room.currentUserName)
                            if (track != null) {
                                val pos = player.progress.value.positionMs
                                updateMonotonicAnchor(track.id, pos, status.playWhenReady)
                                callbacks.onHostPlayedNewTrack(track, pos, status.playWhenReady)
                            }
                        } else if (room.currentTrack != null && curYt?.videoId == room.currentTrack.id) {
                            val prevIntent = lastObservedPlayWhenReady
                            if (prevIntent != null && prevIntent != status.playWhenReady) {
                                lastObservedPlayWhenReady = status.playWhenReady
                                val pos = player.progress.value.positionMs
                                updateMonotonicAnchor(room.currentTrack.id, pos, status.playWhenReady)
                                callbacks.onHostTransportPlayPause(status.playWhenReady, pos)
                            }
                        }
                    } else if (curYt != null) {
                        lastObservedVideoId = curYt.videoId
                        lastObservedPlayWhenReady = status.playWhenReady
                    }

                    emitTelemetryNow()
                }
        }

        driftLoopJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val room = callbacks.currentRoomSnapshot() ?: continue
                val now = Clock.System.now().toEpochMilliseconds()
                val curYt = player.queue.value.currentItem as? PlayableItem.YtMedia
                val state = player.status.value.state
                val isPrepared = state is PlaybackState.Prepared
                val isBuffering = isPrepared && state.buffering
                val localPos = player.progress.value.positionMs

                // Detect manual seek jumps on Host / Collaborative controllers from NowPlaying / Notification slider
                val canControl = room.isHost || room.controlMode == ControlMode.COLLABORATIVE
                if (canControl && curYt?.videoId == room.currentTrack?.id && isPrepared && !isBuffering && now > applyingRemoteUntilMs) {
                    val elapsedSinceSample = (now - lastProgressSampleAtMs).coerceAtLeast(0L)
                    if (lastProgressSampleAtMs > 0L && elapsedSinceSample < 4000L) {
                        val expectedNatural = if (player.status.value.playWhenReady) {
                            lastProgressSamplePosMs + elapsedSinceSample
                        } else {
                            lastProgressSamplePosMs
                        }
                        if (abs(localPos - expectedNatural) > 1800L) {
                            scheduleDebouncedHostSeek(localPos, player.status.value.playWhenReady)
                        }
                    }
                    lastProgressSamplePosMs = localPos
                    lastProgressSampleAtMs = now
                } else {
                    lastProgressSamplePosMs = localPos
                    lastProgressSampleAtMs = now
                }

                // Controlled seek correction for non-host listeners when playback is ready
                if (!room.isHost && room.isPlaying && room.currentTrack != null && curYt?.videoId == room.currentTrack.id) {
                    if (isPrepared && !isBuffering && now > applyingRemoteUntilMs) {
                        val expected = expectedPositionMs(room.currentTrack.durationMs)
                        val drift = abs(localPos - expected)
                        val effectiveTolerance = maxOf(syncToleranceMs, 1500L)
                        if (drift > effectiveTolerance && (now - lastSeekCorrectionMs > 4500L)) {
                            lastSeekCorrectionMs = now
                            applyingRemoteUntilMs = now + 500L
                            player.seekTo(expected)
                        }
                    }
                }

                emitTelemetryNow()
            }
        }
    }

    private fun scheduleDebouncedHostSeek(positionMs: Long, isPlaying: Boolean) {
        seekDebounceJob?.cancel()
        seekDebounceJob = scope.launch {
            delay(250L)
            val room = callbacks.currentRoomSnapshot() ?: return@launch
            if (room.currentTrack == null) return@launch
            val curPos = player.progress.value.positionMs
            updateMonotonicAnchor(room.currentTrack.id, curPos, isPlaying)
            callbacks.onHostTransportSeek(curPos)
        }
    }

    private fun emitTelemetryNow() {
        val room = callbacks.currentRoomSnapshot() ?: return
        val status = player.status.value
        val state = status.state
        val progress = player.progress.value
        val curYt = player.queue.value.currentItem as? PlayableItem.YtMedia
        val isPrepared = state is PlaybackState.Prepared
        val isBuffering = (isPrepared && state.buffering) || (state is PlaybackState.Uninitialized && state.isInitializing)
        val errMsg = status.error?.message
        val roomTrack = room.currentTrack

        val duration = progress.durationMs.takeIf { it > 0L } ?: roomTrack?.durationMs ?: 0L
        val expected = if (roomTrack != null) expectedPositionMs(duration) else 0L
        val localPos = progress.positionMs
        val drift = if (room.isHost || roomTrack == null) 0L else abs(localPos - expected)

        val sameTrack = roomTrack == null || curYt?.videoId == roomTrack.id
        val tolerance = maxOf(syncToleranceMs, 1800L)
        val synced = room.connectionState == PlaybackConnectionState.CONNECTED &&
            errMsg == null &&
            sameTrack &&
            !isBuffering &&
            (roomTrack == null || (isPrepared && (room.isHost || !room.isPlaying || drift <= tolerance)))

        callbacks.onTelemetryUpdate(
            SyncTelemetry(
                trackId = curYt?.videoId,
                localPositionMs = localPos,
                expectedPositionMs = expected,
                durationMs = duration,
                driftMs = drift,
                isPrepared = isPrepared,
                isBuffering = isBuffering,
                isPlayingLocally = status.playWhenReady && isPrepared,
                isActuallySynced = synced,
                playerError = errMsg,
            ),
        )
    }
}
