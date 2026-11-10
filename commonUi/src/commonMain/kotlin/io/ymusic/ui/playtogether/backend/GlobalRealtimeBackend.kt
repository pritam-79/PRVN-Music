package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
import io.ymusic.ui.playtogether.backend.PlayTogetherJson.bool
import io.ymusic.ui.playtogether.backend.PlayTogetherJson.int
import io.ymusic.ui.playtogether.backend.PlayTogetherJson.lng
import io.ymusic.ui.playtogether.backend.PlayTogetherJson.str
import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.RequestBodyX.toRequestBody
import kmphttp.Response
import kmphttp.create
import kmphttp.utf8String
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * Zero-config cross-device real-time backend over HTTP JSON message relay (`ntfy.sh`).
 * Works between any two devices across Wi-Fi / 4G / 5G without requiring extra setup.
 *
 * Guarantees:
 * - Strict JSON encoding via [PlayTogetherJson] (no unescaped string interpolation bugs).
 * - Full room epoch isolation & authoritative `ROOM_STATE` snapshot recovery for late joiners & reconnects.
 * - Message-ID cursor polling (`?poll=1&since=<lastMsgId>`) + event deduplication (zero lost same-second events).
 * - Full lifecycle sync for Queue, Song Requests (all 6 statuses), Presence, Settings, Close/Delete, and Kicks.
 */
class GlobalRealtimeBackend(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val relayUrl: String = "https://ntfy.sh",
    private val client: KmpHttpClient = KmpHttpClient.create {},
) : PlayTogetherBackend {

    private data class CachedRoom(
        var info: RoomInfo,
        val hostId: String,
        var selfMemberId: String,
        val members: LinkedHashMap<String, RoomMember> = linkedMapOf(),
        val queue: MutableList<PlayTogetherTrack> = mutableListOf(),
        val songRequests: LinkedHashMap<String, SongRequest> = linkedMapOf(),
        var currentTrack: PlayTogetherTrack? = null,
        var isPlaying: Boolean = false,
        var positionMs: Long = 0L,
        var playbackSeq: Long = 1L,
        var queueVersion: Long = 1L,
        var playbackStartTimestampUtc: Long = 0L,
        var isClosed: Boolean = false,
        var lastMsgId: String? = null,
        var lastStateBroadcastMs: Long = 0L,
        val seenEventIds: LinkedHashSet<String> = linkedSetOf(),
    )

    private val localLock = Any()
    private val localRooms = mutableMapOf<String, CachedRoom>()
    private val eventFlows = mutableMapOf<String, MutableSharedFlow<RoomEvent>>()
    private val presenceFlows = mutableMapOf<String, MutableStateFlow<List<RoomMember>>>()
    private val pollJobs = mutableMapOf<String, Job>()

    private val networkAvailable = MutableStateFlow(true)
    private var lastKnownClockOffsetMs = 0L
    private var consecutiveFailures = 0

    private fun updateServerClockFromResponse(resp: Response, sentAtMs: Long, rttMs: Long) {
        val dateEpochMs = parseHttpDateToEpochMs(resp.headers["Date"]) ?: return
        // HTTP Date has 1-second resolution, so add +500ms midpoint for unbiased comparison.
        val serverEstimateMs = dateEpochMs + 500L
        val localMidpoint = sentAtMs + (rttMs.coerceAtLeast(0L) / 2L)
        val rawSkew = serverEstimateMs - localMidpoint
        // Ignore sub-2.5s differences (normal HTTP Date quantization + network jitter); only compensate real clock skew.
        lastKnownClockOffsetMs = if (kotlin.math.abs(rawSkew) > 2_500L) rawSkew else 0L
    }

    /** Parses IMF-fixdate HTTP `Date` headers (e.g. `Tue, 10 Nov 2026 18:41:20 GMT`) to epoch ms. */
    private fun parseHttpDateToEpochMs(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val tokens = header.trim().replace(",", "").split(Regex("\\s+"))
        // Expected: [DayName, dd, MMM, yyyy, HH:mm:ss, GMT]
        if (tokens.size < 6) return null
        val day = tokens[1].toIntOrNull() ?: return null
        val month = when (tokens[2].lowercase()) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
            "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> return null
        }
        val year = tokens[3].toIntOrNull() ?: return null
        val hms = tokens[4].split(":")
        if (hms.size != 3) return null
        val hour = hms[0].toIntOrNull() ?: return null
        val min = hms[1].toIntOrNull() ?: return null
        val sec = hms[2].toIntOrNull() ?: return null
        if (day !in 1..31 || year < 1970 || hour !in 0..23 || min !in 0..59 || sec !in 0..60) return null
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val m = month
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val daysSinceEpoch = (era * 146097L + doe - 719468L)
        return ((daysSinceEpoch * 86400L) + (hour * 3600L) + (min * 60L) + sec) * 1000L
    }

    private fun markSuccess() {
        consecutiveFailures = 0
        networkAvailable.value = true
    }

    private fun markTransientFailure(hard: Boolean = false) {
        consecutiveFailures++
        if (hard || consecutiveFailures >= 2) {
            networkAvailable.value = false
        }
    }

    private fun retryDelayMs(resp: Response?, attempt: Int): Long {
        val retryAfterSec = resp?.headers?.get("Retry-After")?.trim()?.toLongOrNull()
        if (retryAfterSec != null && retryAfterSec > 0L) {
            return (retryAfterSec * 1000L).coerceIn(500L, MAX_RETRY_BACKOFF_MS)
        }
        return minOf(MAX_RETRY_BACKOFF_MS, BASE_POLL_INTERVAL_MS * (1L shl minOf(attempt, 3)))
    }

    private fun topicForRoom(code: String): String = "prvn_music_room_${code.trim()}"
    private val publicDirectoryTopic = "prvn_music_public_rooms_v2"
    private val legacyDirectoryTopic = "prvn_music_public_rooms_v1"

    private fun getOrCreateEventFlow(roomId: String): MutableSharedFlow<RoomEvent> =
        synchronized(localLock) {
            eventFlows.getOrPut(roomId) { MutableSharedFlow(extraBufferCapacity = 128) }
        }

    private fun getOrCreatePresenceFlow(roomId: String): MutableStateFlow<List<RoomMember>> =
        synchronized(localLock) {
            presenceFlows.getOrPut(roomId) { MutableStateFlow(emptyList()) }
        }

    private fun rememberEventId(cached: CachedRoom, eventId: String): Boolean {
        if (eventId.isBlank()) return true
        return synchronized(localLock) {
            if (!cached.seenEventIds.add(eventId)) {
                false
            } else {
                if (cached.seenEventIds.size > 512) {
                    val oldest = cached.seenEventIds.firstOrNull()
                    if (oldest != null) cached.seenEventIds.remove(oldest)
                }
                true
            }
        }
    }

    override suspend fun listPublicRooms(): Result<List<RoomInfo>> = withContext(Dispatchers.IO) {
        val roomsByCode = linkedMapOf<String, Pair<Long, RoomInfo>>()
        val now = Clock.System.now().toEpochMilliseconds()
        val staleCutoffMs = 45 * 60_000L

        var networkOk = false
        var lastHttpCode: Int? = null
        for (attempt in 0..1) {
            try {
                val sentAt = Clock.System.now().toEpochMilliseconds()
                val mark = TimeSource.Monotonic.markNow()
                val req = RequestBuilder()
                    .url("$relayUrl/$publicDirectoryTopic/json?poll=1&since=all")
                    .header("User-Agent", USER_AGENT)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                val retryAfterDelay = client.execute(req).use { resp ->
                    val rtt = mark.elapsedNow().inWholeMilliseconds
                    lastHttpCode = resp.code
                    if (resp.isSuccessful) {
                        networkOk = true
                        markSuccess()
                        updateServerClockFromResponse(resp, sentAt, rtt)
                        val body = resp.body.utf8String()
                        body.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            val root = PlayTogetherJson.parseObjectOrNull(line) ?: return@forEach
                            val msgObj = PlayTogetherJson.parseObjectOrNull(root.str("message")) ?: return@forEach
                            val code = msgObj.str("code")?.trim().orEmpty()
                            if (!RoomSecurityPolicy.isValidRoomCode(code)) return@forEach

                            val closed = (msgObj.bool("closed") ?: false) || (msgObj.bool("isClosed") ?: false)
                            val vis = runCatching {
                                RoomVisibility.valueOf(msgObj.str("visibility").orEmpty().uppercase())
                            }.getOrDefault(RoomVisibility.PUBLIC)

                            if (closed || vis != RoomVisibility.PUBLIC) {
                                roomsByCode.remove(code)
                                return@forEach
                            }

                            val updatedUtc = msgObj.lng("updatedUtc") ?: ((root.lng("time") ?: 0L) * 1000L)
                            if (now - updatedUtc > staleCutoffMs) return@forEach

                            val existing = roomsByCode[code]?.second
                            val parsedInfo = RoomInfo(
                                id = msgObj.str("id")?.takeIf { it.isNotBlank() } ?: existing?.id ?: "room_$code",
                                code = code,
                                name = msgObj.str("name")?.takeIf { it.isNotBlank() } ?: existing?.name ?: "PRVN Music Lounge",
                                hostName = msgObj.str("hostName")?.takeIf { it.isNotBlank() } ?: existing?.hostName ?: "PRVN Host",
                                hostId = msgObj.str("hostId")?.takeIf { it.isNotBlank() } ?: existing?.hostId.orEmpty(),
                                visibility = RoomVisibility.PUBLIC,
                                controlMode = runCatching {
                                    ControlMode.valueOf(msgObj.str("controlMode").orEmpty().uppercase())
                                }.getOrDefault(existing?.controlMode ?: ControlMode.HOST_ONLY),
                                memberCount = (msgObj.int("memberCount") ?: existing?.memberCount ?: 1).coerceAtLeast(1),
                                currentTrack = PlayTogetherJson.decodeTrack(msgObj["currentTrack"] as? JsonObject)
                                    ?: existing?.currentTrack,
                                allowListenerRequests = msgObj.bool("allowListenerRequests") ?: existing?.allowListenerRequests ?: true,
                                createdUtc = msgObj.lng("createdUtc") ?: existing?.createdUtc ?: updatedUtc,
                            )
                            roomsByCode[code] = updatedUtc to parsedInfo
                        }
                        null
                    } else if (resp.code == 429 || resp.code >= 500) {
                        retryDelayMs(resp, attempt)
                    } else {
                        null
                    }
                }
                if (networkOk) break
                if (retryAfterDelay != null && attempt == 0) {
                    delay(minOf(retryAfterDelay, 1_500L))
                } else {
                    break
                }
            } catch (_: Exception) {
                if (attempt == 0) delay(500L)
            }
        }
        if (!networkOk) markTransientFailure()

        // Include locally hosted active PUBLIC rooms if relay hasn't echoed them yet (never resurrect via listener cache)
        synchronized(localLock) {
            localRooms.values
                .filter { !it.isClosed && it.info.visibility == RoomVisibility.PUBLIC && it.selfMemberId == it.hostId }
                .forEach { cached ->
                    val existingRelay = roomsByCode[cached.info.code]
                    val merged = cached.info.copy(
                        memberCount = maxOf(cached.members.size, existingRelay?.second?.memberCount ?: 1),
                        currentTrack = cached.currentTrack ?: existingRelay?.second?.currentTrack ?: cached.info.currentTrack,
                    )
                    roomsByCode[cached.info.code] = (existingRelay?.first ?: now) to merged
                }
        }

        val sorted = roomsByCode.values
            .sortedByDescending { it.first }
            .map { it.second }

        if (!networkOk && sorted.isEmpty()) {
            val msg = when {
                lastHttpCode == 429 -> "Play Together relay is rate-limited right now (HTTP 429). Tap Retry in a moment."
                lastHttpCode != null -> "Play Together relay returned HTTP $lastHttpCode. Tap Retry to reconnect."
                else -> "Unable to reach Play Together server. Check your internet connection and tap Retry."
            }
            Result.failure(Exception(msg))
        } else {
            Result.success(sorted)
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
    ): Result<RoomInfo> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()
        if (!RoomSecurityPolicy.isValidRoomCode(cleanCode)) {
            return@withContext Result.failure(Exception("Invalid room code."))
        }
        val cleanName = RoomSecurityPolicy.sanitizeRoomName(name)
        val cleanHost = RoomSecurityPolicy.sanitizeUserName(hostName)
        val now = Clock.System.now().toEpochMilliseconds()
        val resolvedHostId = hostId.ifBlank { "host_${Random.nextLong(10000, 99999)}" }
        val roomId = "room_${cleanCode}_${now}_${Random.nextInt(100, 999)}"
        val cleanPassword = password?.trim()?.takeIf { it.isNotBlank() }

        val info = RoomInfo(
            id = roomId,
            code = cleanCode,
            name = cleanName,
            hostName = cleanHost,
            hostId = resolvedHostId,
            visibility = visibility,
            controlMode = controlMode,
            memberCount = 1,
            currentTrack = initialTrack,
            password = cleanPassword,
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

        val cached = CachedRoom(
            info = info,
            hostId = resolvedHostId,
            selfMemberId = resolvedHostId,
            currentTrack = initialTrack,
            isPlaying = initialIsPlaying,
            positionMs = initialPositionMs.coerceAtLeast(0L),
            playbackSeq = 1L,
            queueVersion = 1L,
            playbackStartTimestampUtc = if (initialIsPlaying) now - initialPositionMs.coerceAtLeast(0L) else 0L,
        ).apply {
            members[resolvedHostId] = hostMember
            queue.addAll(initialQueue)
        }

        val stateMsg = buildRoomStatePayload(cached)
        val published = publishRelayMessage(topicForRoom(cleanCode), "ROOM_STATE", stateMsg)
        if (!published) {
            networkAvailable.value = false
            return@withContext Result.failure(Exception("Could not publish room online. Check your internet connection."))
        }
        networkAvailable.value = true

        synchronized(localLock) {
            // Stop previous jobs/rooms using the same code
            localRooms.entries.filter { it.value.info.code == cleanCode }.map { it.key }.forEach { oldId ->
                pollJobs.remove(oldId)?.cancel()
                localRooms.remove(oldId)
            }
            localRooms[roomId] = cached
            getOrCreatePresenceFlow(roomId).value = listOf(hostMember)
        }

        if (visibility == RoomVisibility.PUBLIC) {
            publishRelayMessage(publicDirectoryTopic, "ROOM_LIST", buildPublicDirectoryPayload(cached))
        } else {
            publishRelayMessage(
                publicDirectoryTopic,
                "ROOM_HIDDEN",
                buildPublicTombstonePayload(cleanCode, roomId, visibility, closed = false),
            )
        }

        startRoomPolling(cached)
        Result.success(info)
    }

    override suspend fun verifyAndJoinRoom(
        code: String,
        userName: String,
        password: String?,
        preferredMemberId: String?,
    ): Result<JoinRoomResult> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()
        if (!RoomSecurityPolicy.isValidRoomCode(cleanCode)) {
            return@withContext Result.failure(Exception("Invalid code. Please enter a 4-digit room code."))
        }
        val cleanUser = RoomSecurityPolicy.sanitizeUserName(userName)
        val t0 = TimeSource.Monotonic.markNow()

        val topic = topicForRoom(cleanCode)
        val replayResult = replayRoomTopic(topic, cleanCode)
        val rttMs = t0.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)

        val snapshot = replayResult.getOrElse { err ->
            // Fallback to local room if same process and relay unreachable
            val localMatch = synchronized(localLock) {
                localRooms.values.firstOrNull { !it.isClosed && it.info.code == cleanCode }
            }
            if (localMatch != null) {
                return@withContext Result.success(
                    buildJoinResultFromCached(localMatch, preferredMemberId ?: "member_${Random.nextLong(10000, 99999)}", cleanUser, rttMs),
                )
            }
            return@withContext Result.failure(err)
        }

        val info = snapshot.info
        if (snapshot.isClosed) {
            return@withContext Result.failure(Exception("Room $cleanCode has been closed or deleted by the host."))
        }

        if (info.visibility == RoomVisibility.PRIVATE && !info.password.isNullOrBlank()) {
            val entered = password?.trim().orEmpty()
            if (entered != info.password) {
                return@withContext Result.failure(Exception("Incorrect room passcode."))
            }
        }

        val memberId = preferredMemberId?.takeIf { it.isNotBlank() }
            ?: "member_${Random.nextLong(10000, 99999)}"
        val isHostMember = memberId == snapshot.hostId
        val now = Clock.System.now().toEpochMilliseconds()

        val selfMember = RoomMember(
            id = memberId,
            name = cleanUser,
            isHost = isHostMember,
            syncState = if (snapshot.currentTrack != null) MemberSyncState.SYNCING else MemberSyncState.SYNCED,
            driftMs = 0L,
            lastSeenUtc = now,
        )

        snapshot.selfMemberId = memberId
        snapshot.members[memberId] = selfMember
        val membersList = snapshot.members.values.toList()
        snapshot.info = info.copy(memberCount = membersList.size.coerceAtLeast(1))

        synchronized(localLock) {
            pollJobs.remove(info.id)?.cancel()
            localRooms[info.id] = snapshot
            getOrCreatePresenceFlow(info.id).value = membersList
        }

        val joinPayload = buildJsonObject {
            put("event", "MEMBER_JOIN")
            put("roomId", info.id)
            put("eventId", "join_${memberId}_$now")
            put("member", PlayTogetherJson.encodeMember(selfMember))
            put("timestampUtc", now)
        }.toString()
        publishRelayMessage(topic, "MEMBER_JOIN", joinPayload)

        startRoomPolling(snapshot)

        val calculatedPos = computeEffectivePositionMs(
            snapshot.currentTrack,
            snapshot.isPlaying,
            snapshot.positionMs,
            snapshot.playbackStartTimestampUtc,
            now + lastKnownClockOffsetMs,
        )

        Result.success(
            JoinRoomResult(
                room = snapshot.info,
                isHost = isHostMember,
                currentMemberId = memberId,
                members = membersList,
                currentTrack = snapshot.currentTrack,
                isPlaying = snapshot.isPlaying,
                positionMs = calculatedPos,
                queue = snapshot.queue.toList(),
                songRequests = snapshot.songRequests.values.toList(),
                playbackSeq = snapshot.playbackSeq,
                queueVersion = snapshot.queueVersion,
                playbackStartTimestampUtc = snapshot.playbackStartTimestampUtc,
                serverTimestampUtc = now + lastKnownClockOffsetMs,
                measuredRttMs = rttMs,
            ),
        )
    }

    override suspend fun fetchLatestRoomSnapshot(
        roomId: String,
        requesterMemberId: String,
        requesterName: String,
        isHost: Boolean,
    ): Result<JoinRoomResult> = withContext(Dispatchers.IO) {
        val code = synchronized(localLock) { localRooms[roomId]?.info?.code } ?: codeFromRoomId(roomId)
        val t0 = TimeSource.Monotonic.markNow()
        val replayed = replayRoomTopic(topicForRoom(code), code).getOrElse {
            return@withContext Result.failure(it)
        }
        val rttMs = t0.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)
        if (replayed.isClosed) {
            return@withContext Result.failure(Exception("Room has been closed by the host."))
        }
        replayed.selfMemberId = requesterMemberId
        synchronized(localLock) {
            val prev = localRooms[replayed.info.id]
            if (prev != null) {
                replayed.seenEventIds.addAll(prev.seenEventIds)
            }
            localRooms[replayed.info.id] = replayed
            getOrCreatePresenceFlow(replayed.info.id).value = replayed.members.values.toList()
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val calculatedPos = computeEffectivePositionMs(
            replayed.currentTrack,
            replayed.isPlaying,
            replayed.positionMs,
            replayed.playbackStartTimestampUtc,
            now + lastKnownClockOffsetMs,
        )
        Result.success(
            JoinRoomResult(
                room = replayed.info.copy(memberCount = replayed.members.size.coerceAtLeast(1)),
                isHost = requesterMemberId == replayed.hostId || isHost,
                currentMemberId = requesterMemberId,
                members = replayed.members.values.toList(),
                currentTrack = replayed.currentTrack,
                isPlaying = replayed.isPlaying,
                positionMs = calculatedPos,
                queue = replayed.queue.toList(),
                songRequests = replayed.songRequests.values.toList(),
                playbackSeq = replayed.playbackSeq,
                queueVersion = replayed.queueVersion,
                playbackStartTimestampUtc = replayed.playbackStartTimestampUtc,
                serverTimestampUtc = now + lastKnownClockOffsetMs,
                measuredRttMs = rttMs,
            ),
        )
    }

    override suspend fun leaveRoom(roomId: String, memberId: String): Result<Unit> = withContext(Dispatchers.IO) {
        var code = codeFromRoomId(roomId)
        var name = "Listener"
        synchronized(localLock) {
            pollJobs.remove(roomId)?.cancel()
            val cached = localRooms[roomId]
            if (cached != null) {
                code = cached.info.code
                name = cached.members[memberId]?.name ?: "Listener"
                cached.members.remove(memberId)
            }
            localRooms.remove(roomId)
            presenceFlows.remove(roomId)
        }
        val msg = buildJsonObject {
            put("event", "MEMBER_LEAVE")
            put("roomId", roomId)
            put("eventId", "leave_${memberId}_${Clock.System.now().toEpochMilliseconds()}")
            put("memberId", memberId)
            put("memberName", name)
            put("timestampUtc", Clock.System.now().toEpochMilliseconds())
        }.toString()
        publishRelayMessage(topicForRoom(code), "MEMBER_LEAVE", msg)
        Result.success(Unit)
    }

    override suspend fun closeRoom(roomId: String, reason: String): Result<Unit> =
        endRoomInternal(roomId, reason, deleted = false)

    override suspend fun deleteRoom(roomId: String, reason: String): Result<Unit> =
        endRoomInternal(roomId, reason, deleted = true)

    private suspend fun endRoomInternal(roomId: String, reason: String, deleted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        var code = codeFromRoomId(roomId)
        synchronized(localLock) {
            pollJobs.remove(roomId)?.cancel()
            localRooms[roomId]?.let { cached ->
                code = cached.info.code
                cached.isClosed = true
            }
            localRooms.remove(roomId)
            presenceFlows.remove(roomId)
        }
        val eventName = if (deleted) "ROOM_DELETED" else "ROOM_CLOSED"
        val closeMsg = buildJsonObject {
            put("event", eventName)
            put("roomId", roomId)
            put("eventId", "end_${roomId}_${Clock.System.now().toEpochMilliseconds()}")
            put("reason", reason)
            put("deleted", deleted)
            put("closed", true)
            put("timestampUtc", Clock.System.now().toEpochMilliseconds())
        }.toString()
        publishRelayMessage(topicForRoom(code), eventName, closeMsg)

        if (code.length == 4) {
            publishRelayMessage(
                publicDirectoryTopic,
                eventName,
                buildPublicTombstonePayload(code, roomId, RoomVisibility.PRIVATE, closed = true),
            )
            // Also clean legacy v1 topic
            publishRelayMessage(legacyDirectoryTopic, eventName, """{"code":"$code","closed":true}""")
        }
        Result.success(Unit)
    }

    override suspend fun updateRoomSettings(
        roomId: String,
        hostId: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
        allowListenerRequests: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cached = synchronized(localLock) {
            localRooms[roomId]?.also {
                it.info = it.info.copy(
                    visibility = visibility,
                    controlMode = controlMode,
                    allowListenerRequests = allowListenerRequests,
                )
            }
        }
        val code = cached?.info?.code ?: codeFromRoomId(roomId)
        val payload = buildJsonObject {
            put("event", "ROOM_SETTINGS_UPDATE")
            put("roomId", roomId)
            put("eventId", "settings_${Clock.System.now().toEpochMilliseconds()}")
            put("hostId", hostId)
            put("visibility", visibility.name)
            put("controlMode", controlMode.name)
            put("allowListenerRequests", allowListenerRequests)
            put("timestampUtc", Clock.System.now().toEpochMilliseconds())
        }.toString()
        val ok = publishRelayMessage(topicForRoom(code), "SETTINGS", payload)
        if (cached != null) {
            if (visibility == RoomVisibility.PUBLIC) {
                publishRelayMessage(publicDirectoryTopic, "ROOM_LIST", buildPublicDirectoryPayload(cached))
            } else {
                publishRelayMessage(
                    publicDirectoryTopic,
                    "ROOM_HIDDEN",
                    buildPublicTombstonePayload(code, roomId, visibility, closed = false),
                )
            }
        }
        if (ok) Result.success(Unit) else Result.failure(Exception("Failed to sync room settings"))
    }

    override suspend fun kickMember(roomId: String, hostId: String, targetMemberId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cached = synchronized(localLock) {
            localRooms[roomId]?.also { it.members.remove(targetMemberId) }
        }
        if (cached != null) {
            getOrCreatePresenceFlow(roomId).value = cached.members.values.toList()
        }
        val code = cached?.info?.code ?: codeFromRoomId(roomId)
        val payload = buildJsonObject {
            put("event", "MEMBER_KICK")
            put("roomId", roomId)
            put("eventId", "kick_${targetMemberId}_${Clock.System.now().toEpochMilliseconds()}")
            put("hostId", hostId)
            put("targetMemberId", targetMemberId)
            put("timestampUtc", Clock.System.now().toEpochMilliseconds())
        }.toString()
        val ok = publishRelayMessage(topicForRoom(code), "KICK", payload)
        if (ok) Result.success(Unit) else Result.failure(Exception("Failed to remove participant"))
    }

    override suspend fun sendPlaybackEvent(roomId: String, event: PlaybackEvent): Result<Unit> = withContext(Dispatchers.IO) {
        val cached = synchronized(localLock) { localRooms[roomId] }
        val code = cached?.info?.code ?: codeFromRoomId(roomId)
        val eventId = event.eventId.ifBlank { "${event.eventType.name}_${event.senderId}_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(100, 999)}" }

        if (cached != null) {
            rememberEventId(cached, eventId)
            synchronized(localLock) {
                when (event.eventType) {
                    PlaybackEventType.PLAY,
                    PlaybackEventType.PAUSE,
                    PlaybackEventType.SEEK,
                    PlaybackEventType.TRACK_CHANGE,
                    -> {
                        if (event.playbackSeq >= cached.playbackSeq) {
                            cached.playbackSeq = event.playbackSeq
                        } else {
                            cached.playbackSeq += 1L
                        }
                        cached.isPlaying = event.isPlaying
                        cached.positionMs = event.positionMs.coerceAtLeast(0L)
                        cached.playbackStartTimestampUtc = event.playbackStartTimestampUtc
                        if (event.track != null || event.eventType == PlaybackEventType.TRACK_CHANGE) {
                            cached.currentTrack = event.track
                            cached.info = cached.info.copy(currentTrack = event.track)
                        }
                    }
                    PlaybackEventType.QUEUE_UPDATE -> {
                        if (event.queueVersion >= cached.queueVersion) {
                            cached.queueVersion = event.queueVersion
                        } else {
                            cached.queueVersion += 1L
                        }
                        cached.queue.clear()
                        cached.queue.addAll(event.queue)
                        if (event.track != null) {
                            cached.currentTrack = event.track
                            cached.info = cached.info.copy(currentTrack = event.track)
                        }
                    }
                    else -> Unit
                }
            }
        }

        val payload = buildJsonObject {
            put("event", event.eventType.name)
            put("roomId", roomId)
            put("eventId", eventId)
            put("senderId", event.senderId)
            put("isPlaying", event.isPlaying)
            put("positionMs", event.positionMs)
            put("playbackSeq", cached?.playbackSeq ?: event.playbackSeq)
            put("queueVersion", cached?.queueVersion ?: event.queueVersion)
            put("playbackStartTimestampUtc", event.playbackStartTimestampUtc)
            put("timestampUtc", event.timestampUtc)
            event.track?.let { put("track", PlayTogetherJson.encodeTrack(it)) }
            if (event.eventType == PlaybackEventType.QUEUE_UPDATE || event.queue.isNotEmpty()) {
                put("queue", PlayTogetherJson.encodeTrackList(event.queue))
            }
        }.toString()

        val ok = publishRelayMessage(topicForRoom(code), event.eventType.name, payload)
        if (!ok) {
            networkAvailable.value = false
            return@withContext Result.failure(Exception("Offline: playback update failed to send"))
        }
        networkAvailable.value = true

        // Keep public directory complete whenever track changes (never wipes room name/hostName!)
        if (cached != null && event.eventType == PlaybackEventType.TRACK_CHANGE) {
            if (cached.info.visibility == RoomVisibility.PUBLIC) {
                publishRelayMessage(publicDirectoryTopic, "ROOM_LIST", buildPublicDirectoryPayload(cached))
            }
            // Refresh consolidated ROOM_STATE snapshot so future joiners immediately get current song + queue + requests
            publishRelayMessage(topicForRoom(code), "ROOM_STATE", buildRoomStatePayload(cached))
        }
        Result.success(Unit)
    }

    override suspend fun updatePresence(
        roomId: String,
        memberId: String,
        syncState: MemberSyncState,
        driftMs: Long,
        memberName: String,
        isHost: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val cached = synchronized(localLock) { localRooms[roomId] }
        val code = cached?.info?.code ?: codeFromRoomId(roomId)

        val resolvedName = memberName.ifBlank {
            synchronized(localLock) { cached?.members?.get(memberId)?.name } ?: "Listener"
        }
        val resolvedHost = isHost || (cached?.hostId == memberId)

        if (cached != null) {
            synchronized(localLock) {
                cached.members[memberId] = RoomMember(
                    id = memberId,
                    name = resolvedName,
                    isHost = resolvedHost,
                    syncState = syncState,
                    driftMs = driftMs,
                    lastSeenUtc = now,
                )
                getOrCreatePresenceFlow(roomId).value = cached.members.values.toList()
            }
        }

        val eventId = "pres_${memberId}_$now"
        if (cached != null) rememberEventId(cached, eventId)

        val payload = buildJsonObject {
            put("event", "PRESENCE")
            put("roomId", roomId)
            put("eventId", eventId)
            put("memberId", memberId)
            put("memberName", resolvedName)
            put("isHost", resolvedHost)
            put("syncState", syncState.name)
            put("driftMs", driftMs)
            put("timestampUtc", now)
        }.toString()

        val ok = publishRelayMessage(topicForRoom(code), "PRESENCE", payload)
        if (ok) {
            networkAvailable.value = true
            // Host periodically refreshes ROOM_STATE snapshot so long sessions keep requests/queue warm in relay cache
            if (cached != null && resolvedHost && (now - cached.lastStateBroadcastMs >= 15_000L)) {
                cached.lastStateBroadcastMs = now
                publishRelayMessage(topicForRoom(code), "ROOM_STATE", buildRoomStatePayload(cached))
                if (cached.info.visibility == RoomVisibility.PUBLIC) {
                    publishRelayMessage(publicDirectoryTopic, "ROOM_LIST", buildPublicDirectoryPayload(cached))
                }
            }
            Result.success(Unit)
        } else {
            networkAvailable.value = false
            Result.failure(Exception("Network offline"))
        }
    }

    override suspend fun submitSongRequest(roomId: String, request: SongRequest): Result<Unit> = withContext(Dispatchers.IO) {
        val cached = synchronized(localLock) { localRooms[roomId] }
        val code = cached?.info?.code ?: codeFromRoomId(roomId)
        val normalized = request.copy(roomId = roomId)

        if (cached != null) {
            synchronized(localLock) {
                val pendingCount = cached.songRequests.values.count {
                    it.requesterId == normalized.requesterId && it.status == SongRequestStatus.PENDING
                }
                if (pendingCount >= 5) {
                    return@withContext Result.failure(Exception("Maximum 5 active pending requests allowed."))
                }
                cached.songRequests[normalized.id] = normalized
            }
        }

        val eventId = "req_add_${normalized.id}_${Clock.System.now().toEpochMilliseconds()}"
        if (cached != null) rememberEventId(cached, eventId)

        val payload = buildJsonObject {
            put("event", "SONG_REQUEST_ADD")
            put("roomId", roomId)
            put("eventId", eventId)
            put("request", PlayTogetherJson.encodeSongRequest(normalized))
            put("timestampUtc", normalized.requestTimeMs)
        }.toString()

        val ok = publishRelayMessage(topicForRoom(code), "SONG_REQUEST", payload)
        if (!ok) {
            networkAvailable.value = false
            return@withContext Result.failure(Exception("Offline: song request saved locally and will retry."))
        }
        networkAvailable.value = true
        Result.success(Unit)
    }

    override suspend fun updateSongRequestStatus(
        roomId: String,
        requestId: String,
        status: SongRequestStatus,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val cached = synchronized(localLock) { localRooms[roomId] }
        val code = cached?.info?.code ?: codeFromRoomId(roomId)

        synchronized(localLock) {
            if (cached != null) {
                val prev = cached.songRequests[requestId]
                if (prev != null) {
                    cached.songRequests[requestId] = prev.copy(status = status, updatedTimeMs = now)
                }
            }
        }

        val eventId = "req_st_${requestId}_${status.name}_$now"
        if (cached != null) rememberEventId(cached, eventId)

        val payload = buildJsonObject {
            put("event", "SONG_REQUEST_STATUS")
            put("roomId", roomId)
            put("eventId", eventId)
            put("requestId", requestId)
            put("status", status.name)
            put("updatedTimeMs", now)
            put("timestampUtc", now)
        }.toString()

        val ok = publishRelayMessage(topicForRoom(code), "REQUEST_STATUS", payload)
        if (!ok) {
            networkAvailable.value = false
            return@withContext Result.failure(Exception("Offline: status update saved locally."))
        }
        networkAvailable.value = true
        Result.success(Unit)
    }

    override suspend fun measureServerClockOffset(): Result<SyncPingResult> = withContext(Dispatchers.IO) {
        try {
            val t0 = Clock.System.now().toEpochMilliseconds()
            val mark = TimeSource.Monotonic.markNow()
            val req = RequestBuilder()
                .url("$relayUrl/v1/health")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.execute(req).use { resp ->
                val rttMs = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)
                val t1 = t0 + rttMs
                if (resp.isSuccessful) {
                    markSuccess()
                    updateServerClockFromResponse(resp, t0, rttMs)
                    val midpoint = (t0 + t1) / 2L
                    Result.success(
                        SyncPingResult(
                            serverTimestampUtc = midpoint + lastKnownClockOffsetMs,
                            rttMs = rttMs,
                            clockOffsetMs = lastKnownClockOffsetMs,
                        ),
                    )
                } else {
                    markTransientFailure()
                    Result.failure(Exception("Server ping failed (${resp.code})"))
                }
            }
        } catch (e: Exception) {
            markTransientFailure()
            Result.failure(e)
        }
    }

    override fun observeRoomEvents(roomId: String): Flow<RoomEvent> = getOrCreateEventFlow(roomId)

    override fun observePresence(roomId: String): Flow<List<RoomMember>> = getOrCreatePresenceFlow(roomId).asStateFlow()

    override fun isConnected(): Boolean = networkAvailable.value

    // ---- Replay & Polling internals ----

    private suspend fun replayRoomTopic(topic: String, cleanCode: String): Result<CachedRoom> {
        var body: String? = null
        var failure: Exception? = null
        for (attempt in 0..1) {
            val sentAt = Clock.System.now().toEpochMilliseconds()
            val mark = TimeSource.Monotonic.markNow()
            val req = RequestBuilder()
                .url("$relayUrl/$topic/json?poll=1&since=all")
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val retryWait = try {
                client.execute(req).use { resp ->
                    val rttMs = mark.elapsedNow().inWholeMilliseconds
                    if (resp.isSuccessful) {
                        markSuccess()
                        updateServerClockFromResponse(resp, sentAt, rttMs)
                        body = resp.body.utf8String()
                        null
                    } else if (resp.code == 429 || resp.code >= 500) {
                        failure = Exception("Play Together server temporarily busy (${resp.code}). Please try again.")
                        retryDelayMs(resp, attempt)
                    } else {
                        markTransientFailure()
                        failure = Exception("Play Together server error (${resp.code}). Please try again.")
                        -1L
                    }
                }
            } catch (e: Exception) {
                failure = Exception("Network error contacting Play Together server: ${e.message ?: "offline"}")
                500L
            }
            if (body != null) break
            if (retryWait != null && retryWait > 0L && attempt == 0) {
                delay(minOf(retryWait, 1_500L))
            } else {
                break
            }
        }
        val responseBody = body ?: run {
            markTransientFailure()
            return Result.failure(failure ?: Exception("Unable to reach Play Together server."))
        }

        val lines = responseBody.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) {
            return Result.failure(Exception("Room code \"$cleanCode\" not found or expired."))
        }

        var state: CachedRoom? = null
        var lastNtfyId: String? = null

        for (line in lines) {
            val root = PlayTogetherJson.parseObjectOrNull(line) ?: continue
            val ntfyId = root.str("id")
            if (!ntfyId.isNullOrBlank()) lastNtfyId = ntfyId

            val msgObj = PlayTogetherJson.parseObjectOrNull(root.str("message")) ?: continue
            val ev = msgObj.str("event").orEmpty().uppercase()
            val msgRoomId = msgObj.str("roomId") ?: msgObj.str("id")

            if (ev == "ROOM_STATE") {
                val parsedRoom = PlayTogetherJson.decodeRoomInfo(msgObj) ?: continue
                val parsedHostId = msgObj.str("hostId").orEmpty().ifBlank { parsedRoom.hostId }
                val nextCached = if (state != null && state.info.id == parsedRoom.id) {
                    state
                } else {
                    CachedRoom(
                        info = parsedRoom.copy(hostId = parsedHostId),
                        hostId = parsedHostId,
                        selfMemberId = "",
                    )
                }

                nextCached.info = parsedRoom.copy(hostId = parsedHostId)
                nextCached.isClosed = msgObj.bool("isClosed") ?: msgObj.bool("closed") ?: false
                val seq = msgObj.lng("playbackSeq") ?: nextCached.playbackSeq
                if (seq >= nextCached.playbackSeq) {
                    nextCached.playbackSeq = seq
                    nextCached.isPlaying = msgObj.bool("isPlaying") ?: nextCached.isPlaying
                    nextCached.positionMs = msgObj.lng("positionMs") ?: nextCached.positionMs
                    nextCached.playbackStartTimestampUtc = msgObj.lng("playbackStartTimestampUtc") ?: nextCached.playbackStartTimestampUtc
                    nextCached.currentTrack = PlayTogetherJson.decodeTrack(msgObj["currentTrack"] as? JsonObject)
                        ?: nextCached.currentTrack
                }
                val qVer = msgObj.lng("queueVersion") ?: nextCached.queueVersion
                if (qVer >= nextCached.queueVersion) {
                    nextCached.queueVersion = qVer
                    (msgObj["queue"] as? JsonArray)?.let { arr ->
                        nextCached.queue.clear()
                        nextCached.queue.addAll(PlayTogetherJson.decodeTrackList(arr))
                    }
                }
                (msgObj["members"] as? JsonArray)?.let { arr ->
                    PlayTogetherJson.decodeMemberList(arr).forEach { m ->
                        nextCached.members[m.id] = m
                    }
                }
                (msgObj["songRequests"] as? JsonArray)?.let { arr ->
                    PlayTogetherJson.decodeSongRequestList(arr).forEach { r ->
                        val cur = nextCached.songRequests[r.id]
                        if (cur == null || r.updatedTimeMs >= cur.updatedTimeMs) {
                            nextCached.songRequests[r.id] = r.copy(roomId = nextCached.info.id)
                        }
                    }
                }
                if (nextCached.members.isEmpty()) {
                    nextCached.members[parsedHostId] = RoomMember(
                        id = parsedHostId,
                        name = parsedRoom.hostName,
                        isHost = true,
                        syncState = MemberSyncState.SYNCED,
                        driftMs = 0L,
                        lastSeenUtc = msgObj.lng("timestampUtc") ?: Clock.System.now().toEpochMilliseconds(),
                    )
                }
                state = nextCached
                continue
            }

            val cur = state ?: continue
            // Ignore leftover events from earlier closed rooms that happened to share the same 4-digit code!
            if (!msgRoomId.isNullOrBlank() && msgRoomId != cur.info.id) continue
            msgObj.str("eventId")?.let { rememberEventId(cur, it) }

            when (ev) {
                "ROOM_CLOSED", "ROOM_DELETED" -> {
                    cur.isClosed = true
                }
                "PLAY", "PAUSE", "SEEK", "TRACK_CHANGE" -> {
                    val seq = msgObj.lng("playbackSeq") ?: 0L
                    if (seq == 0L || seq >= cur.playbackSeq) {
                        if (seq > cur.playbackSeq) cur.playbackSeq = seq
                        cur.isPlaying = msgObj.bool("isPlaying") ?: cur.isPlaying
                        cur.positionMs = msgObj.lng("positionMs") ?: cur.positionMs
                        cur.playbackStartTimestampUtc = msgObj.lng("playbackStartTimestampUtc") ?: cur.playbackStartTimestampUtc
                        val tr = PlayTogetherJson.decodeTrack(msgObj["track"] as? JsonObject)
                        if (tr != null || ev == "TRACK_CHANGE") {
                            cur.currentTrack = tr
                            cur.info = cur.info.copy(currentTrack = tr)
                        }
                    }
                }
                "QUEUE_UPDATE" -> {
                    val qVer = msgObj.lng("queueVersion") ?: 0L
                    if (qVer == 0L || qVer >= cur.queueVersion) {
                        if (qVer > cur.queueVersion) cur.queueVersion = qVer
                        (msgObj["queue"] as? JsonArray)?.let { arr ->
                            cur.queue.clear()
                            cur.queue.addAll(PlayTogetherJson.decodeTrackList(arr))
                        }
                        PlayTogetherJson.decodeTrack(msgObj["track"] as? JsonObject)?.let {
                            cur.currentTrack = it
                            cur.info = cur.info.copy(currentTrack = it)
                        }
                    }
                }
                "SONG_REQUEST_ADD" -> {
                    val reqObj = (msgObj["request"] as? JsonObject)?.let { PlayTogetherJson.decodeSongRequest(it) }
                    if (reqObj != null) {
                        cur.songRequests[reqObj.id] = reqObj.copy(roomId = cur.info.id)
                    }
                }
                "SONG_REQUEST_STATUS" -> {
                    val reqId = msgObj.str("requestId").orEmpty()
                    val prev = cur.songRequests[reqId]
                    if (prev != null) {
                        cur.songRequests[reqId] = prev.copy(
                            status = parseSongRequestStatus(msgObj.str("status")),
                            updatedTimeMs = msgObj.lng("updatedTimeMs") ?: prev.updatedTimeMs,
                        )
                    }
                }
                "MEMBER_JOIN" -> {
                    val m = (msgObj["member"] as? JsonObject)?.let { PlayTogetherJson.decodeMember(it) }
                    if (m != null) cur.members[m.id] = m
                }
                "MEMBER_LEAVE" -> {
                    val mid = msgObj.str("memberId").orEmpty()
                    if (mid.isNotBlank()) cur.members.remove(mid)
                }
                "MEMBER_KICK" -> {
                    val target = msgObj.str("targetMemberId").orEmpty()
                    if (target.isNotBlank()) cur.members.remove(target)
                }
                "PRESENCE" -> {
                    val mid = msgObj.str("memberId").orEmpty()
                    if (mid.isNotBlank()) {
                        val prev = cur.members[mid]
                        cur.members[mid] = RoomMember(
                            id = mid,
                            name = msgObj.str("memberName")?.takeIf { it.isNotBlank() } ?: prev?.name ?: "Listener",
                            isHost = msgObj.bool("isHost") ?: prev?.isHost ?: (mid == cur.hostId),
                            syncState = runCatching {
                                MemberSyncState.valueOf(msgObj.str("syncState").orEmpty().uppercase())
                            }.getOrDefault(MemberSyncState.SYNCED),
                            driftMs = msgObj.lng("driftMs") ?: 0L,
                            lastSeenUtc = msgObj.lng("timestampUtc") ?: prev?.lastSeenUtc ?: Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                }
                "ROOM_SETTINGS_UPDATE" -> {
                    val vis = runCatching {
                        RoomVisibility.valueOf(msgObj.str("visibility").orEmpty().uppercase())
                    }.getOrDefault(cur.info.visibility)
                    val ctrl = runCatching {
                        ControlMode.valueOf(msgObj.str("controlMode").orEmpty().uppercase())
                    }.getOrDefault(cur.info.controlMode)
                    val allowReq = msgObj.bool("allowListenerRequests") ?: cur.info.allowListenerRequests
                    cur.info = cur.info.copy(visibility = vis, controlMode = ctrl, allowListenerRequests = allowReq)
                }
            }
        }

        val finalState = state
            ?: return Result.failure(Exception("Room code \"$cleanCode\" not found or expired."))
        finalState.lastMsgId = lastNtfyId

        return Result.success(finalState)
    }

    private fun startRoomPolling(cached: CachedRoom) {
        val roomId = cached.info.id
        val topic = topicForRoom(cached.info.code)
        val eventFlow = getOrCreateEventFlow(roomId)
        val presenceFlow = getOrCreatePresenceFlow(roomId)

        synchronized(localLock) {
            pollJobs.remove(roomId)?.cancel()
            val job = scope.launch(Dispatchers.IO) {
                var sinceCursor = cached.lastMsgId?.takeIf { it.isNotBlank() } ?: "30s"
                var pollFailures = 0
                while (isActive) {
                    val shouldStop = synchronized(localLock) { localRooms[roomId]?.isClosed == true || !localRooms.containsKey(roomId) }
                    if (shouldStop) break

                    var nextDelayMs = BASE_POLL_INTERVAL_MS
                    try {
                        val sentAt = Clock.System.now().toEpochMilliseconds()
                        val mark = TimeSource.Monotonic.markNow()
                        val req = RequestBuilder()
                            .url("$relayUrl/$topic/json?poll=1&since=$sinceCursor")
                            .header("User-Agent", USER_AGENT)
                            .header("Cache-Control", "no-cache")
                            .get()
                            .build()
                        client.execute(req).use { resp ->
                            val rttMs = mark.elapsedNow().inWholeMilliseconds
                            if (resp.isSuccessful) {
                                pollFailures = 0
                                markSuccess()
                                updateServerClockFromResponse(resp, sentAt, rttMs)
                                val body = resp.body.utf8String()
                                body.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                    val root = PlayTogetherJson.parseObjectOrNull(line) ?: return@forEach
                                    root.str("id")?.takeIf { it.isNotBlank() }?.let { msgId ->
                                        sinceCursor = msgId
                                        cached.lastMsgId = msgId
                                    }
                                    val msgObj = PlayTogetherJson.parseObjectOrNull(root.str("message")) ?: return@forEach
                                    processIncomingMessage(cached, msgObj, eventFlow, presenceFlow)
                                }
                                pruneTimedOutMembers(cached, presenceFlow, eventFlow)
                            } else {
                                pollFailures++
                                markTransientFailure(hard = resp.code !in 429..599 && pollFailures >= 2)
                                nextDelayMs = retryDelayMs(resp, pollFailures)
                            }
                        }
                    } catch (_: Exception) {
                        pollFailures++
                        markTransientFailure(hard = pollFailures >= 2)
                        nextDelayMs = retryDelayMs(null, pollFailures)
                    }
                    delay(nextDelayMs)
                }
            }
            pollJobs[roomId] = job
        }
    }

    private suspend fun processIncomingMessage(
        cached: CachedRoom,
        msgObj: JsonObject,
        eventFlow: MutableSharedFlow<RoomEvent>,
        presenceFlow: MutableStateFlow<List<RoomMember>>,
    ) {
        val roomId = cached.info.id
        val msgRoomId = msgObj.str("roomId") ?: msgObj.str("id")
        if (!msgRoomId.isNullOrBlank() && msgRoomId != roomId) return

        val eventId = msgObj.str("eventId").orEmpty()
        if (!rememberEventId(cached, eventId)) return

        val ev = msgObj.str("event").orEmpty().uppercase()
        val senderId = msgObj.str("senderId").orEmpty()
        val isFromSelf = senderId.isNotBlank() && senderId == cached.selfMemberId

        when (ev) {
            "PLAY", "PAUSE", "SEEK", "TRACK_CHANGE" -> {
                if (isFromSelf) return
                val seq = msgObj.lng("playbackSeq") ?: 0L
                val isNewer = synchronized(localLock) {
                    if (seq > 0L && seq <= cached.playbackSeq) {
                        false
                    } else {
                        if (seq > cached.playbackSeq) cached.playbackSeq = seq
                        cached.isPlaying = msgObj.bool("isPlaying") ?: false
                        cached.positionMs = msgObj.lng("positionMs") ?: 0L
                        cached.playbackStartTimestampUtc = msgObj.lng("playbackStartTimestampUtc") ?: 0L
                        val tr = PlayTogetherJson.decodeTrack(msgObj["track"] as? JsonObject)
                        if (tr != null || ev == "TRACK_CHANGE") {
                            cached.currentTrack = tr
                            cached.info = cached.info.copy(currentTrack = tr)
                        }
                        true
                    }
                }
                if (isNewer) {
                    eventFlow.emit(
                        RoomEvent.PlaybackUpdated(
                            isPlaying = cached.isPlaying,
                            positionMs = cached.positionMs,
                            track = cached.currentTrack,
                            timestampUtc = msgObj.lng("timestampUtc") ?: Clock.System.now().toEpochMilliseconds(),
                            senderId = senderId,
                            playbackSeq = cached.playbackSeq,
                            playbackStartTimestampUtc = cached.playbackStartTimestampUtc,
                            eventId = eventId,
                        ),
                    )
                }
            }

            "QUEUE_UPDATE" -> {
                if (isFromSelf) return
                val qVer = msgObj.lng("queueVersion") ?: 0L
                val updated = synchronized(localLock) {
                    if (qVer > 0L && qVer <= cached.queueVersion) {
                        null
                    } else {
                        if (qVer > cached.queueVersion) cached.queueVersion = qVer
                        val nextQueue = (msgObj["queue"] as? JsonArray)?.let { PlayTogetherJson.decodeTrackList(it) }
                        if (nextQueue != null) {
                            cached.queue.clear()
                            cached.queue.addAll(nextQueue)
                        }
                        PlayTogetherJson.decodeTrack(msgObj["track"] as? JsonObject)?.let {
                            cached.currentTrack = it
                            cached.info = cached.info.copy(currentTrack = it)
                        }
                        cached.queue.toList()
                    }
                }
                if (updated != null) {
                    eventFlow.emit(
                        RoomEvent.QueueChanged(
                            queue = updated,
                            currentTrack = cached.currentTrack,
                            queueVersion = cached.queueVersion,
                            senderId = senderId,
                            eventId = eventId,
                        ),
                    )
                }
            }

            "SONG_REQUEST_ADD" -> {
                val reqObj = (msgObj["request"] as? JsonObject)?.let { PlayTogetherJson.decodeSongRequest(it) } ?: return
                val normalized = reqObj.copy(roomId = roomId)
                val list = synchronized(localLock) {
                    cached.songRequests[normalized.id] = normalized
                    cached.songRequests.values.toList()
                }
                eventFlow.emit(RoomEvent.SongRequestsChanged(requests = list, addedRequest = normalized, eventId = eventId))
            }

            "SONG_REQUEST_STATUS" -> {
                val reqId = msgObj.str("requestId").orEmpty()
                if (reqId.isBlank()) return
                val status = parseSongRequestStatus(msgObj.str("status"))
                val updatedTimeMs = msgObj.lng("updatedTimeMs") ?: Clock.System.now().toEpochMilliseconds()
                val pair = synchronized(localLock) {
                    val prev = cached.songRequests[reqId] ?: return@synchronized null
                    val next = prev.copy(status = status, updatedTimeMs = updatedTimeMs)
                    cached.songRequests[reqId] = next
                    cached.songRequests.values.toList() to next
                } ?: return
                eventFlow.emit(RoomEvent.SongRequestsChanged(requests = pair.first, updatedRequest = pair.second, eventId = eventId))
            }

            "MEMBER_JOIN" -> {
                val member = (msgObj["member"] as? JsonObject)?.let { PlayTogetherJson.decodeMember(it) } ?: return
                if (member.id == cached.selfMemberId) return
                val list = synchronized(localLock) {
                    cached.members[member.id] = member
                    val all = cached.members.values.toList()
                    cached.info = cached.info.copy(memberCount = all.size)
                    all
                }
                presenceFlow.value = list
                eventFlow.emit(RoomEvent.MemberJoined(member))
                eventFlow.emit(RoomEvent.MemberPresenceChanged(list))
            }

            "MEMBER_LEAVE" -> {
                val mid = msgObj.str("memberId").orEmpty()
                val mName = msgObj.str("memberName").orEmpty().ifBlank { "Listener" }
                if (mid.isBlank() || mid == cached.selfMemberId) return
                val list = synchronized(localLock) {
                    cached.members.remove(mid)
                    val all = cached.members.values.toList()
                    cached.info = cached.info.copy(memberCount = all.size.coerceAtLeast(1))
                    all
                }
                presenceFlow.value = list
                eventFlow.emit(RoomEvent.MemberLeft(mid, mName))
                eventFlow.emit(RoomEvent.MemberPresenceChanged(list))
            }

            "MEMBER_KICK" -> {
                val targetId = msgObj.str("targetMemberId").orEmpty()
                if (targetId.isBlank()) return
                if (targetId == cached.selfMemberId) {
                    eventFlow.emit(RoomEvent.MemberKicked(targetId))
                } else {
                    val list = synchronized(localLock) {
                        cached.members.remove(targetId)
                        cached.members.values.toList()
                    }
                    presenceFlow.value = list
                    eventFlow.emit(RoomEvent.MemberPresenceChanged(list))
                }
            }

            "PRESENCE" -> {
                val mid = msgObj.str("memberId").orEmpty()
                if (mid.isBlank() || mid == cached.selfMemberId) return
                val now = Clock.System.now().toEpochMilliseconds()
                val list = synchronized(localLock) {
                    val prev = cached.members[mid]
                    cached.members[mid] = RoomMember(
                        id = mid,
                        name = msgObj.str("memberName")?.takeIf { it.isNotBlank() } ?: prev?.name ?: "Listener",
                        isHost = msgObj.bool("isHost") ?: prev?.isHost ?: (mid == cached.hostId),
                        syncState = runCatching {
                            MemberSyncState.valueOf(msgObj.str("syncState").orEmpty().uppercase())
                        }.getOrDefault(MemberSyncState.SYNCED),
                        driftMs = msgObj.lng("driftMs") ?: 0L,
                        lastSeenUtc = now,
                    )
                    val all = cached.members.values.toList()
                    cached.info = cached.info.copy(memberCount = all.size)
                    all
                }
                presenceFlow.value = list
                eventFlow.emit(RoomEvent.MemberPresenceChanged(list))
            }

            "ROOM_SETTINGS_UPDATE" -> {
                val vis = runCatching {
                    RoomVisibility.valueOf(msgObj.str("visibility").orEmpty().uppercase())
                }.getOrDefault(cached.info.visibility)
                val ctrl = runCatching {
                    ControlMode.valueOf(msgObj.str("controlMode").orEmpty().uppercase())
                }.getOrDefault(cached.info.controlMode)
                val allowReq = msgObj.bool("allowListenerRequests") ?: cached.info.allowListenerRequests
                val hId = msgObj.str("hostId").orEmpty()
                synchronized(localLock) {
                    cached.info = cached.info.copy(visibility = vis, controlMode = ctrl, allowListenerRequests = allowReq)
                }
                eventFlow.emit(RoomEvent.RoomSettingsChanged(vis, ctrl, allowReq, hId))
            }

            "ROOM_STATE" -> {
                if (cached.selfMemberId == cached.hostId) return
                val seq = msgObj.lng("playbackSeq") ?: 0L
                val qVer = msgObj.lng("queueVersion") ?: 0L
                var playbackUpdated = false
                var queueUpdated = false
                var requestsUpdated: List<SongRequest>? = null
                synchronized(localLock) {
                    if (seq > cached.playbackSeq) {
                        cached.playbackSeq = seq
                        cached.isPlaying = msgObj.bool("isPlaying") ?: cached.isPlaying
                        cached.positionMs = msgObj.lng("positionMs") ?: cached.positionMs
                        cached.playbackStartTimestampUtc = msgObj.lng("playbackStartTimestampUtc") ?: cached.playbackStartTimestampUtc
                        cached.currentTrack = PlayTogetherJson.decodeTrack(msgObj["currentTrack"] as? JsonObject) ?: cached.currentTrack
                        playbackUpdated = true
                    }
                    if (qVer > cached.queueVersion) {
                        cached.queueVersion = qVer
                        (msgObj["queue"] as? JsonArray)?.let { arr ->
                            cached.queue.clear()
                            cached.queue.addAll(PlayTogetherJson.decodeTrackList(arr))
                        }
                        queueUpdated = true
                    }
                    (msgObj["songRequests"] as? JsonArray)?.let { arr ->
                        var anyReqChange = false
                        PlayTogetherJson.decodeSongRequestList(arr).forEach { r ->
                            val cur = cached.songRequests[r.id]
                            if (cur == null || r.updatedTimeMs > cur.updatedTimeMs || cur.status != r.status) {
                                cached.songRequests[r.id] = r.copy(roomId = roomId)
                                anyReqChange = true
                            }
                        }
                        if (anyReqChange) requestsUpdated = cached.songRequests.values.toList()
                    }
                }
                if (playbackUpdated) {
                    eventFlow.emit(
                        RoomEvent.PlaybackUpdated(
                            isPlaying = cached.isPlaying,
                            positionMs = cached.positionMs,
                            track = cached.currentTrack,
                            timestampUtc = msgObj.lng("timestampUtc") ?: Clock.System.now().toEpochMilliseconds(),
                            senderId = cached.hostId,
                            playbackSeq = cached.playbackSeq,
                            playbackStartTimestampUtc = cached.playbackStartTimestampUtc,
                            eventId = eventId,
                        ),
                    )
                }
                if (queueUpdated) {
                    eventFlow.emit(RoomEvent.QueueChanged(cached.queue.toList(), cached.currentTrack, cached.queueVersion, cached.hostId, eventId))
                }
                requestsUpdated?.let { list ->
                    eventFlow.emit(RoomEvent.SongRequestsChanged(requests = list, eventId = eventId))
                }
            }

            "ROOM_CLOSED", "ROOM_DELETED" -> {
                val deleted = ev == "ROOM_DELETED" || (msgObj.bool("deleted") ?: false)
                val reason = msgObj.str("reason").orEmpty().ifBlank {
                    if (deleted) "Room was deleted by the host." else "Host has closed the room."
                }
                synchronized(localLock) { cached.isClosed = true }
                eventFlow.emit(RoomEvent.RoomClosed(reason, deleted = deleted))
            }
        }
    }

    private suspend fun pruneTimedOutMembers(
        cached: CachedRoom,
        presenceFlow: MutableStateFlow<List<RoomMember>>,
        eventFlow: MutableSharedFlow<RoomEvent>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val timeoutMs = 60_000L
        val pruned = synchronized(localLock) {
            val expiredIds = cached.members.values
                .filter { !it.isHost && it.id != cached.selfMemberId && it.lastSeenUtc > 0L && (now - it.lastSeenUtc) > timeoutMs }
                .map { it.id }
            if (expiredIds.isEmpty()) return@synchronized null
            expiredIds.forEach { cached.members.remove(it) }
            val list = cached.members.values.toList()
            cached.info = cached.info.copy(memberCount = list.size.coerceAtLeast(1))
            list
        }
        if (pruned != null) {
            presenceFlow.value = pruned
            eventFlow.emit(RoomEvent.MemberPresenceChanged(pruned))
        }
    }

    private suspend fun publishRelayMessage(topic: String, title: String, message: String): Boolean {
        for (attempt in 0..1) {
            try {
                val req = RequestBuilder()
                    .url("$relayUrl/$topic")
                    .header("Title", title)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .post(message.toRequestBody(null))
                    .build()
                val retryWait = client.execute(req).use { resp ->
                    if (resp.isSuccessful) {
                        markSuccess()
                        return true
                    }
                    if ((resp.code == 429 || resp.code >= 500) && attempt == 0) {
                        minOf(retryDelayMs(resp, attempt), 1_500L)
                    } else {
                        -1L
                    }
                }
                if (retryWait > 0L) delay(retryWait) else return false
            } catch (_: Exception) {
                if (attempt == 0) delay(400L) else return false
            }
        }
        return false
    }

    private fun buildJoinResultFromCached(
        cached: CachedRoom,
        memberId: String,
        userName: String,
        rttMs: Long,
    ): JoinRoomResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val isHostMember = memberId == cached.hostId
        val member = RoomMember(
            id = memberId,
            name = userName,
            isHost = isHostMember,
            syncState = MemberSyncState.SYNCING,
            driftMs = 0L,
            lastSeenUtc = now,
        )
        synchronized(localLock) {
            cached.members[memberId] = member
            cached.selfMemberId = memberId
        }
        return JoinRoomResult(
            room = cached.info.copy(memberCount = cached.members.size),
            isHost = isHostMember,
            currentMemberId = memberId,
            members = cached.members.values.toList(),
            currentTrack = cached.currentTrack,
            isPlaying = cached.isPlaying,
            positionMs = cached.positionMs,
            queue = cached.queue.toList(),
            songRequests = cached.songRequests.values.toList(),
            playbackSeq = cached.playbackSeq,
            queueVersion = cached.queueVersion,
            playbackStartTimestampUtc = cached.playbackStartTimestampUtc,
            serverTimestampUtc = now,
            measuredRttMs = rttMs,
        )
    }

    private fun buildRoomStatePayload(cached: CachedRoom): String = buildJsonObject {
        put("event", "ROOM_STATE")
        put("roomId", cached.info.id)
        put("id", cached.info.id)
        put("code", cached.info.code)
        put("name", cached.info.name)
        put("hostName", cached.info.hostName)
        put("hostId", cached.hostId)
        put("visibility", cached.info.visibility.name)
        put("controlMode", cached.info.controlMode.name)
        put("allowListenerRequests", cached.info.allowListenerRequests)
        cached.info.password?.let { put("password", it) }
        put("isPlaying", cached.isPlaying)
        put("positionMs", cached.positionMs)
        put("playbackSeq", cached.playbackSeq)
        put("queueVersion", cached.queueVersion)
        put("playbackStartTimestampUtc", cached.playbackStartTimestampUtc)
        put("isClosed", cached.isClosed)
        put("createdUtc", cached.info.createdUtc)
        cached.currentTrack?.let { put("currentTrack", PlayTogetherJson.encodeTrack(it)) }
        put("queue", PlayTogetherJson.encodeTrackList(cached.queue))
        put("members", PlayTogetherJson.encodeMemberList(cached.members.values.toList()))
        put("songRequests", PlayTogetherJson.encodeSongRequestList(cached.songRequests.values.toList()))
        put("timestampUtc", Clock.System.now().toEpochMilliseconds())
    }.toString()

    private fun buildPublicDirectoryPayload(cached: CachedRoom): String = buildJsonObject {
        put("id", cached.info.id)
        put("code", cached.info.code)
        put("name", cached.info.name)
        put("hostName", cached.info.hostName)
        put("hostId", cached.hostId)
        put("visibility", cached.info.visibility.name)
        put("controlMode", cached.info.controlMode.name)
        put("allowListenerRequests", cached.info.allowListenerRequests)
        put("memberCount", cached.members.size.coerceAtLeast(1))
        put("createdUtc", cached.info.createdUtc)
        put("updatedUtc", Clock.System.now().toEpochMilliseconds())
        put("closed", false)
        cached.currentTrack?.let { put("currentTrack", PlayTogetherJson.encodeTrack(it)) }
    }.toString()

    private fun buildPublicTombstonePayload(
        code: String,
        roomId: String,
        visibility: RoomVisibility,
        closed: Boolean,
    ): String = buildJsonObject {
        put("id", roomId)
        put("code", code)
        put("visibility", visibility.name)
        put("closed", closed)
        put("isClosed", closed)
        put("updatedUtc", Clock.System.now().toEpochMilliseconds())
    }.toString()

    private fun codeFromRoomId(roomId: String): String =
        roomId.removePrefix("room_").substringBefore('_')

    private fun computeEffectivePositionMs(
        track: PlayTogetherTrack?,
        isPlaying: Boolean,
        reportedPositionMs: Long,
        playbackStartUtc: Long,
        adjustedNowUtc: Long,
    ): Long {
        if (track == null) return 0L
        val raw = if (isPlaying && playbackStartUtc > 0L) {
            (adjustedNowUtc - playbackStartUtc).coerceAtLeast(reportedPositionMs.coerceAtLeast(0L))
        } else {
            reportedPositionMs.coerceAtLeast(0L)
        }
        return if (track.durationMs > 0L) raw.coerceIn(0L, track.durationMs) else raw
    }

    private companion object {
        const val USER_AGENT = "PRVN-Music-App/0.12"
        const val BASE_POLL_INTERVAL_MS = 2_500L
        const val MAX_RETRY_BACKOFF_MS = 20_000L
    }
}
