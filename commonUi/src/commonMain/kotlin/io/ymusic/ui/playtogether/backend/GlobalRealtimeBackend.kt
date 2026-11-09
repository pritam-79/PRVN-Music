package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.RequestBodyX.toRequestBody
import kmphttp.create
import kmphttp.utf8String
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import projectcommon.json.boolOrNull
import projectcommon.json.intOrNull
import projectcommon.json.longOrNull
import projectcommon.json.objects
import projectcommon.json.stringOrNull
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Universal cross-device real-time backend for PRVN Music Listen Together.
 * Uses a zero-configuration global real-time pub/sub relay (ntfy.sh) over HTTPS.
 * Allows participants on differing networks, mobile data, and Wi-Fi to create rooms,
 * discover public lounges, synchronize playback, reorder shared queues, and manage requests.
 */
class GlobalRealtimeBackend(
    private val client: KmpHttpClient = KmpHttpClient.create { defaultExecuteTimeout = 10.seconds },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val relayUrl: String = "https://ntfy.sh",
) : PlayTogetherBackend {

    private data class CachedRoom(
        val info: RoomInfo,
        val hostId: String,
        val members: MutableList<RoomMember> = mutableListOf(),
        val queue: MutableList<PlayTogetherTrack> = mutableListOf(),
        val songRequests: MutableList<SongRequest> = mutableListOf(),
        var currentTrack: PlayTogetherTrack? = null,
        var isPlaying: Boolean = false,
        var positionMs: Long = 0L,
        var lastUpdatedUtc: Long = Clock.System.now().toEpochMilliseconds(),
    )

    private val localLock = Any()
    private val localRooms = mutableMapOf<String, CachedRoom>()

    private val eventFlows = mutableMapOf<String, MutableSharedFlow<RoomEvent>>()
    private val presenceFlows = mutableMapOf<String, MutableStateFlow<List<RoomMember>>>()
    private val pollJobs = mutableMapOf<String, Job>()
    private var isNetworkAvailable = true

    private fun getOrCreateEventFlow(roomId: String): MutableSharedFlow<RoomEvent> {
        return synchronized(localLock) {
            eventFlows.getOrPut(roomId) { MutableSharedFlow(extraBufferCapacity = 64) }
        }
    }

    private fun getOrCreatePresenceFlow(roomId: String): MutableStateFlow<List<RoomMember>> {
        return synchronized(localLock) {
            presenceFlows.getOrPut(roomId) { MutableStateFlow(emptyList()) }
        }
    }

    private fun topicForRoom(code: String): String = "prvn_music_room_${code.trim()}"
    private val publicDirectoryTopic = "prvn_music_public_rooms_v1"

    override suspend fun listPublicRooms(): Result<List<RoomInfo>> = withContext(Dispatchers.IO) {
        val remoteList = mutableListOf<RoomInfo>()
        val closedCodes = mutableSetOf<String>()
        try {
            val req = RequestBuilder()
                .url("$relayUrl/$publicDirectoryTopic/json?poll=1&since=all")
                .header("User-Agent", "PRVN-Music-App/0.07")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val resp = client.execute(req)
            if (resp.isSuccessful) {
                val body = resp.body.utf8String()
                val now = Clock.System.now().toEpochMilliseconds()
                body.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    try {
                        val root = parseJson(line)
                        val msgText = root.stringOrNull("message").orEmpty()
                        if (msgText.isNotBlank()) {
                            val roomJson = parseJson(msgText)
                            val rCode = roomJson.stringOrNull("code").orEmpty()
                            val isClosed = roomJson.boolOrNull("closed") ?: false
                            if (isClosed && rCode.length == 4) {
                                closedCodes.add(rCode)
                                remoteList.removeAll { it.code == rCode }
                            } else if (rCode.length == 4 && !closedCodes.contains(rCode)) {
                                val rName = roomJson.stringOrNull("name").orEmpty().ifBlank { "PRVN Music Lounge" }
                                val rHost = roomJson.stringOrNull("hostName").orEmpty().ifBlank { "PRVN Host" }
                                val rId = roomJson.stringOrNull("id") ?: "room_$rCode"
                                val time = roomJson.longOrNull("updatedUtc") ?: (root.longOrNull("time") ?: 0L) * 1000L

                                // Keep rooms updated within last 20 minutes (prevents stale zombie rooms)
                                if ((now - time) < 20 * 60 * 1000L) {
                                    val track = extractTrack(roomJson, "currentTrack")
                                    val membersCount = roomJson.intOrNull("memberCount") ?: 1
                                    val modeStr = roomJson.stringOrNull("controlMode") ?: "HOST_ONLY"
                                    val mode = if (modeStr == "COLLABORATIVE") ControlMode.COLLABORATIVE else ControlMode.HOST_ONLY

                                    remoteList.removeAll { it.code == rCode }
                                    remoteList.add(
                                        RoomInfo(
                                            id = rId,
                                            code = rCode,
                                            name = rName,
                                            hostName = rHost,
                                            visibility = RoomVisibility.PUBLIC,
                                            controlMode = mode,
                                            memberCount = membersCount,
                                            currentTrack = track,
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        synchronized(localLock) {
            val localPublic = localRooms.values
                .filter { it.info.visibility == RoomVisibility.PUBLIC }
                .map { r -> r.info.copy(memberCount = r.members.size, currentTrack = r.currentTrack) }
            val merged = (remoteList + localPublic).distinctBy { it.code }.filter { !closedCodes.contains(it.code) }
            Result.success(merged)
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
    ): Result<RoomInfo> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().ifBlank { Random.nextInt(1000, 9999).toString() }
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

        synchronized(localLock) {
            localRooms[cleanCode] = CachedRoom(
                info = roomInfo,
                hostId = hostId,
                members = mutableListOf(hostMember),
                queue = initialQueue.toMutableList(),
                currentTrack = initialTrack,
                isPlaying = initialIsPlaying,
                positionMs = initialPositionMs,
                lastUpdatedUtc = Clock.System.now().toEpochMilliseconds(),
            )
            getOrCreatePresenceFlow(roomId).value = listOf(hostMember)
        }

        // Publish Room State to global relay
        val roomPayload = buildRoomStatePayload(
            info = roomInfo,
            hostId = hostId,
            members = listOf(hostMember),
            track = initialTrack,
            positionMs = initialPositionMs,
            isPlaying = initialIsPlaying,
            queue = initialQueue,
        )

        publishRelayMessage(topicForRoom(cleanCode), "ROOM_STATE", roomPayload)

        if (visibility == RoomVisibility.PUBLIC) {
            val pubSummary = buildPublicDirectoryPayload(roomInfo, initialTrack)
            publishRelayMessage(publicDirectoryTopic, "ROOM_$cleanCode", pubSummary)
        }

        Result.success(roomInfo)
    }

    override suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String?,
    ): Result<JoinRoomResult> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()
        val cleanName = RoomSecurityPolicy.sanitizeUserName(userName)

        // 1. Fetch Room State and Playback Events from Global Relay (since=all retrieves cached events)
        var latestRoomState: JsonObject? = null
        var latestPlayback: JsonObject? = null
        var hasAnyMessage = false
        var isClosed = false

        try {
            val req = RequestBuilder()
                .url("$relayUrl/${topicForRoom(cleanCode)}/json?poll=1&since=all")
                .header("User-Agent", "PRVN-Music-App/0.07")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val resp = client.execute(req)
            if (resp.isSuccessful) {
                val body = resp.body.utf8String()
                body.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    try {
                        val root = parseJson(line)
                        val msg = root.stringOrNull("message").orEmpty()
                        if (msg.isNotBlank()) {
                            val inner = parseJson(msg)
                            val evType = inner.stringOrNull("event") ?: root.stringOrNull("title")
                            when (evType) {
                                "ROOM_CLOSED" -> isClosed = true
                                "ROOM_STATE" -> {
                                    latestRoomState = inner
                                    hasAnyMessage = true
                                }
                                "PLAYBACK_UPDATED" -> {
                                    latestPlayback = inner
                                    hasAnyMessage = true
                                }
                                "PRESENCE", "MEMBER_JOINED", "SONG_REQUEST_ADD" -> {
                                    hasAnyMessage = true
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        // If relay message not immediately received, retry once after a short delay
        if (!hasAnyMessage && !isClosed) {
            try {
                delay(600L)
                val retryReq = RequestBuilder()
                    .url("$relayUrl/${topicForRoom(cleanCode)}/json?poll=1&since=all")
                    .header("User-Agent", "PRVN-Music-App/0.09")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                val retryResp = client.execute(retryReq)
                if (retryResp.isSuccessful) {
                    val body = retryResp.body.utf8String()
                    body.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                        try {
                            val root = parseJson(line)
                            val msg = root.stringOrNull("message").orEmpty()
                            if (msg.isNotBlank()) {
                                val inner = parseJson(msg)
                                val evType = inner.stringOrNull("event") ?: root.stringOrNull("title")
                                when (evType) {
                                    "ROOM_CLOSED" -> isClosed = true
                                    "ROOM_STATE" -> {
                                        latestRoomState = inner
                                        hasAnyMessage = true
                                    }
                                    "PLAYBACK_UPDATED" -> {
                                        latestPlayback = inner
                                        hasAnyMessage = true
                                    }
                                    "PRESENCE", "MEMBER_JOINED", "SONG_REQUEST_ADD" -> {
                                        hasAnyMessage = true
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        if (isClosed) {
            return@withContext Result.failure(Exception("This room session has ended."))
        }

        // Fallback to local room cache if network is unavailable or same device
        val cached = synchronized(localLock) { localRooms[cleanCode] }
        if (!hasAnyMessage && cached == null) {
            return@withContext Result.failure(Exception("Room code $cleanCode not found. Please verify the 4-digit code with the host."))
        }

        val roomId = latestRoomState?.stringOrNull("id") ?: cached?.info?.id ?: "room_$cleanCode"
        val rName = latestRoomState?.stringOrNull("name") ?: cached?.info?.name ?: "PRVN Lounge"
        val rHost = latestRoomState?.stringOrNull("hostName") ?: cached?.info?.hostName ?: "Host"
        val passHash = latestRoomState?.stringOrNull("password") ?: cached?.info?.password
        val visStr = latestRoomState?.stringOrNull("visibility") ?: cached?.info?.visibility?.name ?: "PUBLIC"
        val modeStr = latestRoomState?.stringOrNull("controlMode") ?: cached?.info?.controlMode?.name ?: "HOST_ONLY"
        val visibility = RoomVisibility.entries.firstOrNull { it.name == visStr } ?: RoomVisibility.PUBLIC
        val controlMode = ControlMode.entries.firstOrNull { it.name == modeStr } ?: ControlMode.HOST_ONLY

        if (!passHash.isNullOrBlank() && passHash != password?.trim()) {
            return@withContext Result.failure(Exception("Incorrect room password."))
        }

        // Prefer live track from latestPlayback; fallback to roomState, then cached
        val track = (latestPlayback?.let { extractTrack(it, "track") }
            ?: latestRoomState?.let { extractTrack(it, "currentTrack") })
            ?: cached?.currentTrack

        val isPlaying = latestPlayback?.boolOrNull("isPlaying") ?: latestRoomState?.boolOrNull("isPlaying") ?: cached?.isPlaying ?: false
        val basePos = latestPlayback?.longOrNull("positionMs") ?: latestRoomState?.longOrNull("positionMs") ?: cached?.positionMs ?: 0L
        val lastUpdated = latestPlayback?.longOrNull("timestampUtc") ?: latestRoomState?.longOrNull("timestampUtc") ?: cached?.lastUpdatedUtc ?: Clock.System.now().toEpochMilliseconds()

        val elapsed = if (isPlaying) (Clock.System.now().toEpochMilliseconds() - lastUpdated).coerceAtLeast(0L) else 0L
        val currentPosition = basePos + elapsed

        val queue = (latestPlayback?.let { extractQueue(it, "queue") }
            ?: latestRoomState?.let { extractQueue(it, "queue") })
            ?: cached?.queue?.toList() ?: emptyList()

        val newMemberId = "member_${Random.nextLong(1000, 9999)}"
        val selfMember = RoomMember(
            id = newMemberId,
            name = cleanName,
            isHost = false,
            syncState = MemberSyncState.SYNCED,
            driftMs = 0L,
        )

        val roomInfo = RoomInfo(
            id = roomId,
            code = cleanCode,
            name = rName,
            hostName = rHost,
            visibility = visibility,
            controlMode = controlMode,
            memberCount = 2,
            currentTrack = track,
            password = passHash,
        )

        // Broadcast join to relay so host immediately synchronizes playback with this new member
        val joinPayload = """{"event":"MEMBER_JOINED","memberId":"$newMemberId","name":"$cleanName","isHost":false}"""
        publishRelayMessage(topicForRoom(cleanCode), "MEMBER_JOINED", joinPayload)

        synchronized(localLock) {
            val list = getOrCreatePresenceFlow(roomId).value.toMutableList()
            if (list.none { it.id == newMemberId }) list.add(selfMember)
            getOrCreatePresenceFlow(roomId).value = list
        }

        Result.success(
            JoinRoomResult(
                room = roomInfo,
                isHost = false,
                currentMemberId = newMemberId,
                members = listOf(selfMember),
                currentTrack = track,
                isPlaying = isPlaying,
                positionMs = currentPosition,
                queue = queue,
                songRequests = emptyList(),
                serverTimestampUtc = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    override suspend fun leaveRoom(roomId: String, memberId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val code = codeFromRoomId(roomId)
        val payload = """{"event":"MEMBER_LEFT","memberId":"$memberId"}"""
        publishRelayMessage(topicForRoom(code), "MEMBER_LEFT", payload)

        pollJobs[roomId]?.cancel()
        pollJobs.remove(roomId)
        Result.success(Unit)
    }

    override suspend fun closeRoom(roomId: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val code = codeFromRoomId(roomId)
        val payload = """{"event":"ROOM_CLOSED","reason":"${escape(reason)}"}"""
        publishRelayMessage(topicForRoom(code), "ROOM_CLOSED", payload)
        
        // Publish tombstone to public directory
        val tombstone = """{"code":"$code","closed":true}"""
        publishRelayMessage(publicDirectoryTopic, "ROOM_CLOSED_$code", tombstone)

        getOrCreateEventFlow(roomId).emit(RoomEvent.RoomClosed(reason))
        pollJobs[roomId]?.cancel()
        pollJobs.remove(roomId)
        Result.success(Unit)
    }

    override suspend fun sendPlaybackEvent(roomId: String, event: PlaybackEvent): Result<Unit> = withContext(Dispatchers.IO) {
        val code = codeFromRoomId(roomId)
        val trackJson = event.track?.let { serializeTrack(it) } ?: "null"
        val queueJson = serializeQueue(event.queue)

        val payload = """{
            "event": "PLAYBACK_UPDATED",
            "type": "${event.eventType.name}",
            "isPlaying": ${event.isPlaying},
            "positionMs": ${event.positionMs},
            "senderId": "${event.senderId}",
            "timestampUtc": ${event.timestampUtc},
            "track": $trackJson,
            "queue": $queueJson
        }""".replace(Regex("\\s+"), " ")

        publishRelayMessage(topicForRoom(code), "PLAYBACK_UPDATED", payload)

        // If track changed, update public directory listing
        if (event.track != null) {
            val summary = """{"code":"$code","updatedUtc":${Clock.System.now().toEpochMilliseconds()},"currentTrack":$trackJson}"""
            publishRelayMessage(publicDirectoryTopic, "ROOM_$code", summary)
        }

        // Local loopback for instant self-responsiveness
        getOrCreateEventFlow(roomId).emit(
            RoomEvent.PlaybackUpdated(
                isPlaying = event.isPlaying,
                positionMs = event.positionMs,
                track = event.track,
                timestampUtc = event.timestampUtc,
                senderId = event.senderId,
            )
        )

        Result.success(Unit)
    }

    override suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val code = codeFromRoomId(roomId)
        val payload = """{"event":"PRESENCE","memberId":"$memberId","syncState":"${syncState.name}","driftMs":$driftMs}"""
        publishRelayMessage(topicForRoom(code), "PRESENCE", payload)
        Result.success(Unit)
    }

    override suspend fun submitSongRequest(roomId: String, request: SongRequest): Result<Unit> = withContext(Dispatchers.IO) {
        val code = codeFromRoomId(roomId)
        val trackJson = serializeTrack(request.track)
        val payload = """{
            "event": "SONG_REQUEST_ADD",
            "id": "${request.id}",
            "track": $trackJson,
            "requesterId": "${request.requesterId}",
            "requesterName": "${request.requesterName}",
            "requestTimeMs": ${request.requestTimeMs}
        }""".replace(Regex("\\s+"), " ")

        publishRelayMessage(topicForRoom(code), "SONG_REQUEST_ADD", payload)
        Result.success(Unit)
    }

    override suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val code = codeFromRoomId(roomId)
        val payload = """{"event":"SONG_REQUEST_STATUS","requestId":"$requestId","status":"${status.name}"}"""
        publishRelayMessage(topicForRoom(code), "SONG_REQUEST_STATUS", payload)
        Result.success(Unit)
    }

    override fun observeRoomEvents(roomId: String): Flow<RoomEvent> {
        val flow = getOrCreateEventFlow(roomId)
        startRoomPolling(roomId)
        return flow.asSharedFlow()
    }

    override fun observePresence(roomId: String): Flow<List<RoomMember>> {
        val flow = getOrCreatePresenceFlow(roomId)
        startRoomPolling(roomId)
        return flow.asStateFlow()
    }

    override fun isConnected(): Boolean = isNetworkAvailable

    private fun startRoomPolling(roomId: String) {
        synchronized(localLock) {
            if (pollJobs.containsKey(roomId)) return
            val code = codeFromRoomId(roomId)
            val topic = topicForRoom(code)
            val eventFlow = getOrCreateEventFlow(roomId)
            val presenceFlow = getOrCreatePresenceFlow(roomId)

            val job = scope.launch(Dispatchers.IO) {
                var lastSeenTime = Clock.System.now().toEpochMilliseconds() / 1000L - 10L
                val knownRequests = mutableListOf<SongRequest>()

                while (isActive) {
                    try {
                        val req = RequestBuilder()
                            .url("$relayUrl/$topic/json?poll=1&since=$lastSeenTime")
                            .header("User-Agent", "PRVN-Music-App/0.06")
                            .header("Cache-Control", "no-cache")
                            .get()
                            .build()
                        val resp = client.execute(req)
                        if (resp.isSuccessful) {
                            isNetworkAvailable = true
                            val body = resp.body.utf8String()
                            body.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                try {
                                    val root = parseJson(line)
                                    val msgTime = root.longOrNull("time") ?: 0L
                                    if (msgTime > lastSeenTime) lastSeenTime = msgTime

                                    val msgText = root.stringOrNull("message").orEmpty()
                                    if (msgText.isNotBlank()) {
                                        val inner = parseJson(msgText)
                                        val evType = inner.stringOrNull("event") ?: root.stringOrNull("title")

                                        when (evType) {
                                            "PLAYBACK_UPDATED" -> {
                                                val isPlaying = inner.boolOrNull("isPlaying") ?: false
                                                val pos = inner.longOrNull("positionMs") ?: 0L
                                                val senderId = inner.stringOrNull("senderId").orEmpty()
                                                val ts = inner.longOrNull("timestampUtc") ?: Clock.System.now().toEpochMilliseconds()
                                                val track = extractTrack(inner, "track") ?: extractTrack(inner, "currentTrack")
                                                val queue = extractQueue(inner, "queue")

                                                eventFlow.emit(
                                                    RoomEvent.PlaybackUpdated(
                                                        isPlaying = isPlaying,
                                                        positionMs = pos,
                                                        track = track,
                                                        timestampUtc = ts,
                                                        senderId = senderId,
                                                    )
                                                )

                                                if (queue != null) {
                                                    eventFlow.emit(RoomEvent.QueueChanged(queue = queue, currentTrack = track))
                                                }
                                            }
                                            "MEMBER_JOINED" -> {
                                                val mId = inner.stringOrNull("memberId").orEmpty()
                                                val mName = inner.stringOrNull("name").orEmpty()
                                                val isHost = inner.boolOrNull("isHost") ?: false
                                                val member = RoomMember(id = mId, name = mName, isHost = isHost)

                                                eventFlow.emit(RoomEvent.MemberJoined(member))
                                                val currentMembers = presenceFlow.value.toMutableList()
                                                if (currentMembers.none { it.id == mId }) {
                                                    currentMembers.add(member)
                                                    presenceFlow.value = currentMembers
                                                }
                                            }
                                            "MEMBER_LEFT" -> {
                                                val mId = inner.stringOrNull("memberId").orEmpty()
                                                eventFlow.emit(RoomEvent.MemberLeft(mId, "Member"))
                                                val currentMembers = presenceFlow.value.filter { it.id != mId }
                                                presenceFlow.value = currentMembers
                                            }
                                            "SONG_REQUEST_ADD" -> {
                                                val rId = inner.stringOrNull("id") ?: "req_${Random.nextLong(1000, 9999)}"
                                                val reqTrack = extractTrack(inner, "track")
                                                val reqUser = inner.stringOrNull("requesterName") ?: "Listener"
                                                val reqUserId = inner.stringOrNull("requesterId") ?: "user"
                                                val reqTime = inner.longOrNull("requestTimeMs") ?: Clock.System.now().toEpochMilliseconds()

                                                if (reqTrack != null && knownRequests.none { it.id == rId }) {
                                                    val requestObj = SongRequest(
                                                        id = rId,
                                                        track = reqTrack,
                                                        requesterName = reqUser,
                                                        requesterId = reqUserId,
                                                        requestTimeMs = reqTime,
                                                        status = SongRequestStatus.PENDING,
                                                    )
                                                    knownRequests.add(requestObj)
                                                    eventFlow.emit(RoomEvent.SongRequestsChanged(knownRequests.toList()))
                                                }
                                            }
                                            "SONG_REQUEST_STATUS" -> {
                                                val rId = inner.stringOrNull("requestId").orEmpty()
                                                val stName = inner.stringOrNull("status").orEmpty()
                                                val st = SongRequestStatus.entries.firstOrNull { it.name == stName } ?: SongRequestStatus.PENDING
                                                val idx = knownRequests.indexOfFirst { it.id == rId }
                                                if (idx >= 0) {
                                                    knownRequests[idx] = knownRequests[idx].copy(status = st)
                                                    eventFlow.emit(RoomEvent.SongRequestsChanged(knownRequests.toList()))
                                                }
                                            }
                                            "ROOM_CLOSED" -> {
                                                eventFlow.emit(RoomEvent.RoomClosed("Host has closed the session."))
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        } else {
                            isNetworkAvailable = false
                        }
                    } catch (_: Exception) {
                        isNetworkAvailable = false
                    }
                    delay(2000L) // Safe polling cycle preventing HTTP 429
                }
            }
            pollJobs[roomId] = job
        }
    }

    private suspend fun publishRelayMessage(topic: String, title: String, message: String) {
        try {
            val req = RequestBuilder()
                .url("$relayUrl/$topic")
                .header("Title", title)
                .header("User-Agent", "PRVN-Music-App/0.09")
                .header("Content-Type", "text/plain; charset=utf-8")
                .post(message.toRequestBody(null))
                .build()
            client.execute(req)
        } catch (_: Exception) {}
    }

    private fun extractTrack(json: JsonObject, key: String): PlayTogetherTrack? {
        val element = json[key] ?: return null
        return when (element) {
            is JsonObject -> parseTrack(element)
            is kotlinx.serialization.json.JsonPrimitive -> {
                val content = if (element.isString) element.content else null
                if (content.isNullOrBlank() || content == "null") return null
                try {
                    parseTrack(parseJson(content))
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun extractQueue(json: JsonObject, key: String): List<PlayTogetherTrack>? {
        val element = json[key] ?: return null
        return when (element) {
            is JsonObject -> {
                val items = element.objects("items")
                if (items.isNotEmpty()) items.map { parseTrack(it) } else null
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.mapNotNull { if (it is JsonObject) parseTrack(it) else null }
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                val content = if (element.isString) element.content else null
                if (content.isNullOrBlank() || content == "null") return null
                try {
                    parseQueue(content)
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun codeFromRoomId(roomId: String): String {
        return roomId.removePrefix("room_").substringBefore('_')
    }

    private fun parseJson(text: String): JsonObject {
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun parseTrack(json: JsonObject): PlayTogetherTrack {
        return PlayTogetherTrack(
            id = json.stringOrNull("id").orEmpty(),
            title = json.stringOrNull("title").orEmpty(),
            artist = json.stringOrNull("artist").orEmpty(),
            artworkUrl = json.stringOrNull("artworkUrl").orEmpty(),
            durationMs = json.longOrNull("durationMs") ?: 0L,
            isYouTube = json.boolOrNull("isYouTube") ?: true,
            addedBy = json.stringOrNull("addedBy") ?: "Host",
            isAutoRadio = json.boolOrNull("isAutoRadio") ?: false,
        )
    }

    private fun serializeTrack(track: PlayTogetherTrack): String {
        return """{"id":"${track.id}","title":"${escape(track.title)}","artist":"${escape(track.artist)}","artworkUrl":"${escape(track.artworkUrl.orEmpty())}","durationMs":${track.durationMs},"isYouTube":${track.isYouTube},"addedBy":"${escape(track.addedBy)}","isAutoRadio":${track.isAutoRadio}}"""
    }

    private fun parseQueue(jsonStr: String): List<PlayTogetherTrack> {
        val list = mutableListOf<PlayTogetherTrack>()
        try {
            val root = parseJson("""{"items":$jsonStr}""")
            root.objects("items").forEach { obj ->
                list.add(parseTrack(obj))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeQueue(queue: List<PlayTogetherTrack>): String {
        return "[" + queue.joinToString(",") { serializeTrack(it) } + "]"
    }

    private fun buildRoomStatePayload(
        info: RoomInfo,
        hostId: String,
        members: List<RoomMember>,
        track: PlayTogetherTrack?,
        positionMs: Long,
        isPlaying: Boolean,
        queue: List<PlayTogetherTrack>,
    ): String {
        val trackJson = track?.let { serializeTrack(it) } ?: "null"
        val queueJson = serializeQueue(queue)
        return """{
            "event": "ROOM_STATE",
            "id": "${info.id}",
            "code": "${info.code}",
            "name": "${escape(info.name)}",
            "hostName": "${escape(info.hostName)}",
            "hostId": "$hostId",
            "visibility": "${info.visibility.name}",
            "controlMode": "${info.controlMode.name}",
            "password": ${info.password?.let { "\"$it\"" } ?: "null"},
            "isPlaying": $isPlaying,
            "positionMs": $positionMs,
            "currentTrack": $trackJson,
            "queue": $queueJson,
            "timestampUtc": ${Clock.System.now().toEpochMilliseconds()}
        }""".replace(Regex("\\s+"), " ")
    }

    private fun buildPublicDirectoryPayload(info: RoomInfo, track: PlayTogetherTrack?): String {
        val trackJson = track?.let { serializeTrack(it) } ?: "null"
        return """{
            "id": "${info.id}",
            "code": "${info.code}",
            "name": "${escape(info.name)}",
            "hostName": "${escape(info.hostName)}",
            "memberCount": 1,
            "controlMode": "${info.controlMode.name}",
            "updatedUtc": ${Clock.System.now().toEpochMilliseconds()},
            "currentTrack": $trackJson
        }""".replace(Regex("\\s+"), " ")
    }

    private fun escape(text: String): String {
        return text.replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
    }
}
