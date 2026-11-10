package io.ymusic.ui.playtogether

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import io.ymusic.dynamicengine.DynamicEngineManager
import io.ymusic.dynamicengine.embeddedEngineJsSource
import io.ymusic.dynamicengine.yt.YtFeedItem
import io.ymusic.dynamicengine.yt.YtThumb
import io.ymusic.player.PlayableItem
import io.ymusic.player.PlaybackState
import io.ymusic.player.PlaybackStatus
import io.ymusic.player.Player
import io.ymusic.player.Progress
import io.ymusic.player.QueueOrigin
import io.ymusic.player.QueueSource
import io.ymusic.player.QueueState
import io.ymusic.settings.InMemorySettingsStore
import io.ymusic.ui.AppGraph
import io.ymusic.ui.CannedHttp
import io.ymusic.ui.PlaybackCoordinator
import io.ymusic.ui.StubPlayer
import io.ymusic.ui.playtogether.backend.GlobalRealtimeBackend
import io.ymusic.ui.playtogether.backend.InviteLinkHelper
import io.ymusic.ui.playtogether.backend.LocalSimulatedRealtimeBackend
import io.ymusic.ui.playtogether.backend.PlaybackEvent
import io.ymusic.ui.playtogether.backend.PlaybackEventType
import io.ymusic.ui.playtogether.backend.PlayTogetherBackend
import io.ymusic.ui.playtogether.backend.PlayTogetherJson
import io.ymusic.ui.playtogether.backend.RoomEvent
import io.ymusic.ui.playtogether.storage.PlayTogetherStore
import io.ymusic.ui.playtogether.storage.playTogetherJdbcDriver
import io.ymusic.ui.textResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PlayTogetherTest {

    private class SyncCapableStubPlayer(private val inner: StubPlayer = StubPlayer()) : Player by inner {
        val statusFlow = MutableStateFlow(PlaybackStatus())
        val progressFlow = MutableStateFlow(Progress())
        val queueFlow = inner.queueState

        override val status: StateFlow<PlaybackStatus> = statusFlow
        override val progress: StateFlow<Progress> = progressFlow
        override val queue: StateFlow<QueueState> = queueFlow

        override fun setQueue(
            items: List<PlayableItem>,
            playIndex: Int,
            startPositionMs: Long,
            playWhenReady: Boolean,
            queueId: String?,
            source: QueueSource?,
            origin: QueueOrigin,
        ) {
            inner.setQueue(items, playIndex, startPositionMs, playWhenReady, queueId, source, origin)
            queueFlow.value = QueueState(items = items, currentIndex = if (items.isEmpty()) -1 else playIndex)
            progressFlow.value = Progress(positionMs = startPositionMs, durationMs = (items.getOrNull(playIndex) as? PlayableItem.YtMedia)?.durationMs ?: 0L)
            statusFlow.value = PlaybackStatus(
                state = if (items.isEmpty()) PlaybackState.Idle else PlaybackState.Prepared(playWhenReady = playWhenReady, buffering = false, ended = false),
            )
        }

        override fun play() {
            val prev = statusFlow.value.state
            if (prev is PlaybackState.Prepared) {
                statusFlow.value = PlaybackStatus(state = prev.copy(playWhenReady = true, ended = false))
            }
        }

        override fun pause() {
            val prev = statusFlow.value.state
            if (prev is PlaybackState.Prepared) {
                statusFlow.value = PlaybackStatus(state = prev.copy(playWhenReady = false))
            }
        }

        override fun seekTo(positionMs: Long) {
            progressFlow.value = progressFlow.value.copy(positionMs = positionMs)
        }
    }

    /** In-memory fake ntfy HTTP relay server for cross-device [GlobalRealtimeBackend] unit tests. */
    private class FakeNtfyRelayServer {
        private data class StoredMsg(val id: String, val timeSec: Long, val message: String)

        private val lock = Any()
        private val topics = mutableMapOf<String, MutableList<StoredMsg>>()
        private var counter = 1L

        val client = CannedHttp { request, body ->
            val path = request.url.encodedPath
            when {
                path == "/v1/health" -> textResponse(request, """{"healthy":true}""", "application/json")
                request.method == "POST" -> {
                    val topic = path.trimStart('/')
                    val nowSec = System.currentTimeMillis() / 1000L
                    val msgId = "msg${counter++}"
                    synchronized(lock) {
                        topics.getOrPut(topic) { mutableListOf() }.add(StoredMsg(msgId, nowSec, body.orEmpty()))
                    }
                    textResponse(request, """{"id":"$msgId"}""", "application/json")
                }
                request.method == "GET" && path.endsWith("/json") -> {
                    val topic = path.trimStart('/').removeSuffix("/json")
                    val since = request.url.queryParameter("since")
                    val msgs = synchronized(lock) {
                        val list = topics[topic].orEmpty()
                        when {
                            since == null || since == "all" || since == "30s" -> list.toList()
                            else -> {
                                val idx = list.indexOfFirst { it.id == since }
                                if (idx >= 0) list.drop(idx + 1) else list.toList()
                            }
                        }
                    }
                    val ndjson = msgs.joinToString("\n") { m ->
                        val escaped = kotlinx.serialization.json.JsonPrimitive(m.message).toString()
                        """{"id":"${m.id}","time":${m.timeSec},"event":"message","topic":"$topic","message":$escaped}"""
                    }
                    textResponse(request, ndjson, "application/x-ndjson")
                }
                else -> textResponse(request, "{}", "application/json")
            }
        }
    }

    @Test
    fun sqlite_store_persists_room_queue_requests_offline_queue_and_dedup(): Unit = runBlocking {
        val store = PlayTogetherStore(playTogetherJdbcDriver(":memory:"))
        val t1 = PlayTogetherTrack("dQw4w9WgXcQ", "Test \"Quote\" Song", "Artist", album = "Album", artworkUrl = "https://img/1.jpg", durationMs = 200_000L, validated = true)
        val t2 = PlayTogetherTrack("M7lc1UVf-VE", "Next Track", "Channel B", durationMs = 150_000L, validated = true)
        val req1 = SongRequest(
            id = "req_1",
            roomId = "room_5512_1",
            track = t2,
            requesterName = "Alex \"DJ\"",
            requesterId = "member_9",
            requestTimeMs = 1000L,
            updatedTimeMs = 1000L,
            status = SongRequestStatus.PENDING,
        )
        val session = PlayTogetherState.InRoom(
            room = RoomInfo(id = "room_5512_1", code = "5512", name = "Chill Vibes", hostName = "Host", hostId = "host_1"),
            isHost = true,
            members = listOf(RoomMember("host_1", "Host", isHost = true, syncState = MemberSyncState.SYNCED)),
            currentTrack = t1,
            isPlaying = true,
            positionMs = 85_000L,
            queue = listOf(t2),
            songRequests = listOf(req1),
            currentMemberId = "host_1",
            currentUserName = "Host",
            playbackSeq = 4L,
            queueVersion = 2L,
        )

        store.saveActiveSession(session)
        store.saveOrUpdateRequest(req1, syncedRemote = false)

        val restored = store.getActiveSession()
        assertNotNull(restored)
        assertEquals("5512", restored.room.code)
        assertEquals("Test \"Quote\" Song", restored.currentTrack?.title)
        assertEquals("Album", restored.currentTrack?.album)
        assertEquals(85_000L, restored.positionMs)
        assertEquals(listOf(t2.id), restored.queue.map { it.id })
        assertEquals(1, restored.songRequests.size)
        assertEquals("Alex \"DJ\"", restored.songRequests.first().requesterName)

        assertEquals(1, store.listUnsyncedRequests("room_5512_1").size)
        store.markRequestSynced("req_1")
        assertEquals(0, store.listUnsyncedRequests("room_5512_1").size)

        assertTrue(store.recordEventIfNew("room_5512_1", "ev_abc"))
        assertFalse(store.recordEventIfNew("room_5512_1", "ev_abc"))

        store.markRoomClosed("room_5512_1")
        assertNull(store.getActiveSession())
        assertTrue(store.listRecentRooms().isEmpty())
    }

    @Test
    fun deeplink_and_track_mapping_helpers_work_uniformly() {
        assertEquals("5512", InviteLinkHelper.parseRoomCode("prvn://playtogether?code=5512"))
        assertEquals("8821", InviteLinkHelper.parseRoomCode("https://prvn.music/together?code=8821&ref=share"))
        assertEquals("4401", InviteLinkHelper.parseRoomCode("4401"))
        assertNull(InviteLinkHelper.parseRoomCode("https://youtube.com/watch?v=dQw4w9WgXcQ"))

        assertTrue(TrackResolver.isValidYouTubeId("dQw4w9WgXcQ"))
        assertFalse(TrackResolver.isValidYouTubeId("content://media/1"))
        assertTrue(TrackResolver.isLocalTrackId("content://media/external/audio/media/42"))
        assertTrue(TrackResolver.isLocalTrackId("file:///sdcard/song.mp3"))

        val feedItem = YtFeedItem(
            kind = YtFeedItem.KIND_VIDEO,
            id = "dQw4w9WgXcQ",
            title = "Discover Song",
            subtitleLine1 = "Synth Artist • Retro LP • 3:33",
            channelTitle = "Synth Artist",
            channelUrl = "/channel/UC123",
            duration = "3:33",
            durationMs = 213_000L,
            thumbs = listOf(YtThumb("https://img/low.jpg", 120, 90), YtThumb("https://img/hd.jpg", 1280, 720)),
        )
        val ptTrack = TrackResolver.fromYtFeedItem(feedItem, addedBy = "Pritam", validated = true)
        assertEquals("Discover Song", ptTrack.title)
        assertEquals("Synth Artist", ptTrack.artist)
        assertEquals("Retro LP", ptTrack.album)
        assertEquals("https://img/hd.jpg", ptTrack.artworkUrl)
        assertEquals(213_000L, ptTrack.durationMs)

        val encoded = PlayTogetherJson.encodeTrack(ptTrack)
        val decoded = PlayTogetherJson.decodeTrack(encoded)
        assertEquals(ptTrack, decoded)
    }

    @Test
    fun global_relay_preserves_public_room_metadata_requests_presence_and_deletion(): Unit = runBlocking {
        val relay = FakeNtfyRelayServer()
        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val friendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hostBackend = GlobalRealtimeBackend(scope = hostScope, relayUrl = "https://ntfy.test", client = relay.client)
        val friendBackend = GlobalRealtimeBackend(scope = friendScope, relayUrl = "https://ntfy.test", client = relay.client)

        try {
            val track1 = PlayTogetherTrack("dQw4w9WgXcQ", "First Song", "Artist A", album = "Album A", artworkUrl = "https://img/1.jpg", durationMs = 200_000L, validated = true)
            val track2 = PlayTogetherTrack("M7lc1UVf-VE", "Track \"Quoted\" Two", "Artist B", durationMs = 180_000L, validated = true)

            val room = hostBackend.createRoom(
                name = "Night Drive",
                code = "5512",
                hostName = "Host \"One\"",
                visibility = RoomVisibility.PUBLIC,
                controlMode = ControlMode.HOST_ONLY,
                initialTrack = track1,
                initialPositionMs = 85_000L,
                initialIsPlaying = true,
                hostId = "host_fixed_1",
            ).getOrThrow()

            // Public listing shows real room name & track
            val publicBefore = friendBackend.listPublicRooms().getOrThrow()
            assertEquals(1, publicBefore.size)
            assertEquals("Night Drive", publicBefore.first().name)
            assertEquals("Host \"One\"", publicBefore.first().hostName)

            // Friend joins and sees host + self + current track at active playback head
            val joinRes = friendBackend.verifyAndJoinRoom("5512", "Friend \"Two\"").getOrThrow()
            assertEquals(room.id, joinRes.room.id)
            assertEquals(2, joinRes.members.size)
            assertTrue(joinRes.members.any { it.isHost && it.id == "host_fixed_1" })
            assertEquals("First Song", joinRes.currentTrack?.title)
            assertTrue(joinRes.positionMs >= 85_000L)

            // Observe incoming events on Host
            val hostEvents = CopyOnWriteArrayList<RoomEvent>()
            val collectorJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                hostBackend.observeRoomEvents(room.id).collect { hostEvents.add(it) }
            }

            // Friend submits a song request -> Host receives real-time SongRequestsChanged
            val request = SongRequest(
                id = "req_live_1",
                roomId = room.id,
                track = track2,
                requesterName = "Friend \"Two\"",
                requesterId = joinRes.currentMemberId,
                requestTimeMs = System.currentTimeMillis(),
            )
            friendBackend.submitSongRequest(room.id, request).getOrThrow()

            withTimeout(6_000L) {
                while (hostEvents.none { it is RoomEvent.SongRequestsChanged && it.addedRequest?.id == "req_live_1" }) {
                    delay(100L)
                }
            }

            // Track change keeps room name intact in Public Directory (Bug 2 regression test!)
            hostBackend.sendPlaybackEvent(
                room.id,
                PlaybackEvent(
                    roomId = room.id,
                    senderId = "host_fixed_1",
                    eventType = PlaybackEventType.TRACK_CHANGE,
                    track = track2,
                    positionMs = 0L,
                    isPlaying = true,
                    playbackSeq = 2L,
                ),
            ).getOrThrow()

            val publicAfterTrack = friendBackend.listPublicRooms().getOrThrow()
            assertEquals(1, publicAfterTrack.size)
            assertEquals("Night Drive", publicAfterTrack.first().name)
            assertEquals("Host \"One\"", publicAfterTrack.first().hostName)
            assertEquals("Track \"Quoted\" Two", publicAfterTrack.first().currentTrack?.title)

            // Late-joining peer also receives songRequests in snapshot!
            val friend3Backend = GlobalRealtimeBackend(scope = friendScope, relayUrl = "https://ntfy.test", client = relay.client)
            val join3 = friend3Backend.verifyAndJoinRoom("5512", "Friend 3").getOrThrow()
            assertTrue(join3.songRequests.any { it.id == "req_live_1" && it.track.id == track2.id })

            // Deleting room hides it from Public Directory & rejects new joins
            hostBackend.deleteRoom(room.id).getOrThrow()
            val publicAfterDelete = friendBackend.listPublicRooms().getOrThrow()
            assertTrue(publicAfterDelete.isEmpty(), "Deleted room must be purged from public list: $publicAfterDelete")
            val lateJoinAfterDelete = friend3Backend.verifyAndJoinRoom("5512", "Late").exceptionOrNull()
            assertNotNull(lateJoinAfterDelete)

            collectorJob.cancel()
        } finally {
            hostScope.cancel()
            friendScope.cancel()
        }
    }

    @Test
    fun controller_transfers_current_song_clears_queue_cleanly_and_handles_requests(): Unit = runBlocking {
        val client = CannedHttp { request, _ -> textResponse(request, "{}", "application/json") }
        val engines = DynamicEngineManager(client, appVersion = 1, jsSource = embeddedEngineJsSource())
        engines.start()
        val store = PlayTogetherStore(playTogetherJdbcDriver(":memory:"))
        val player = SyncCapableStubPlayer()
        val settings = InMemorySettingsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val backend: PlayTogetherBackend = LocalSimulatedRealtimeBackend(scope)
        val controller = PlayTogetherController(
            coordinator = PlaybackCoordinator(AppGraph(settings = settings, engines = engines, player = player, playerSurface = {}), scope),
            trackResolver = TrackResolver(engines),
            store = store,
            settings = settings,
            scope = scope,
            backendProvider = { backend },
        )

        try {
            // Preload existing playing track at 01:25 before creating room!
            val currentItem = PlayableItem.YtMedia(
                videoId = "dQw4w9WgXcQ",
                title = "Playing Now",
                artist = "Original Artist",
                durationMs = 212_000L,
            )
            player.setQueue(listOf(currentItem), startPositionMs = 85_000L, playWhenReady = true)

            controller.createRoom(
                roomName = "Unified Room",
                hostName = "DJ Host",
                visibility = RoomVisibility.PUBLIC,
                transferCurrentMusic = true,
            )

            withTimeout(5_000L) {
                controller.state.first { it is PlayTogetherState.InRoom }
            }
            val roomState = controller.state.value as PlayTogetherState.InRoom
            assertEquals("dQw4w9WgXcQ", roomState.currentTrack?.id)
            assertEquals(85_000L, roomState.positionMs)

            // Queue two songs & verify clearQueue leaves it completely EMPTY (Bug 4 regression test!)
            val qTrack = PlayTogetherTrack("M7lc1UVf-VE", "Queued Track", "Artist Q", durationMs = 160_000L, validated = true)
            controller.addToQueue(qTrack)
            withTimeout(5_000L) {
                controller.state.first { it is PlayTogetherState.InRoom && it.queue.size == 1 }
            }
            controller.clearQueue()
            withTimeout(5_000L) {
                controller.state.first { it is PlayTogetherState.InRoom && it.queue.isEmpty() }
            }
            assertTrue((controller.state.value as PlayTogetherState.InRoom).queue.isEmpty())

            // Duplicate song request rejected when track is currently playing!
            controller.requestSong(PlayTogetherTrack("dQw4w9WgXcQ", "Playing Now", "Original Artist", validated = true))
            withTimeout(3_000L) {
                controller.state.first { it is PlayTogetherState.InRoom && it.statusBanner?.contains("already playing", ignoreCase = true) == true }
            }

            // Valid song request -> Host approves & adds to queue -> Player/State reflect it cleanly
            controller.requestSong(qTrack)
            withTimeout(5_000L) {
                controller.state.first { it is PlayTogetherState.InRoom && it.songRequests.any { r -> r.track.id == qTrack.id && r.status == SongRequestStatus.PENDING } }
            }
            val reqId = (controller.state.value as PlayTogetherState.InRoom).songRequests.first { it.track.id == qTrack.id }.id
            controller.approveSongRequest(reqId, playNow = true)
            withTimeout(5_000L) {
                controller.state.first {
                    it is PlayTogetherState.InRoom &&
                        it.currentTrack?.id == qTrack.id &&
                        it.songRequests.firstOrNull { r -> r.id == reqId }?.status == SongRequestStatus.ADDED_TO_QUEUE
                }
            }
            assertEquals(qTrack.id, (player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        } finally {
            scope.cancel()
            engines.close()
        }
    }

    @Test
    fun compose_screen_renders_lobby_and_all_five_room_tabs() {
        val client = CannedHttp { request, _ -> textResponse(request, "{}", "application/json") }
        val engines = DynamicEngineManager(client, appVersion = 1, jsSource = embeddedEngineJsSource())
        runBlocking { engines.start() }
        val settings = InMemorySettingsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val backend = LocalSimulatedRealtimeBackend(scope)
        val player = SyncCapableStubPlayer()
        val store = PlayTogetherStore(playTogetherJdbcDriver(":memory:"))
        val graph = AppGraph(settings = settings, engines = engines, player = player, playTogetherStore = store, playerSurface = {})
        val coordinator = PlaybackCoordinator(graph, scope)
        val controller = PlayTogetherController(
            coordinator = coordinator,
            trackResolver = graph.trackResolver,
            store = store,
            settings = settings,
            scope = scope,
            backendProvider = { backend },
        )

        try {
            runComposeUiTest {
                setContent {
                    PlayTogetherScreen(graph = graph, coordinator = coordinator, controller = controller)
                }
                waitUntilAtLeastOneExists(hasText("Listen in Real-Time Sync"), 5_000)
                onAllNodesWithText("Create Room").onLast().performClick()
                waitUntilAtLeastOneExists(hasText("Create Play Together Room"), 5_000)
                onAllNodesWithText("Create Room").onLast().performClick()

                // Verify exact 5 tabs appear once inside room
                waitUntilAtLeastOneExists(hasText("Now Playing"), 5_000)
                waitUntilAtLeastOneExists(hasText("Queue"), 5_000)
                waitUntilAtLeastOneExists(hasText("Search"), 5_000)
                waitUntilAtLeastOneExists(hasText("Requests"), 5_000)
                waitUntilAtLeastOneExists(hasText("Participants"), 5_000)
            }
        } finally {
            scope.cancel()
            runBlocking { engines.close() }
        }
    }
}
