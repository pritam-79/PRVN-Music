package io.ymusic.ui.playtogether.backend

import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.TrackResolver
import io.ymusic.ui.youtubeThumbUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Shared JSON serialization/deserialization helpers for Play Together relay payloads & SQLite snapshots.
 * Guarantees safe escaping for arbitrary track titles, artist names, album names, and user names.
 */
object PlayTogetherJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseObjectOrNull(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    }

    fun encodeTrack(t: PlayTogetherTrack): JsonObject = buildJsonObject {
        put("id", t.id)
        put("title", t.title)
        put("artist", t.artist)
        t.album?.let { put("album", it) }
        t.artworkUrl?.let { put("artworkUrl", it) }
        put("durationMs", t.durationMs)
        t.channelUrl?.let { put("channelUrl", it) }
        put("isYouTube", t.isYouTube)
        put("addedBy", t.addedBy)
        put("isAutoRadio", t.isAutoRadio)
        put("validated", t.validated)
    }

    fun decodeTrack(obj: JsonObject?): PlayTogetherTrack? {
        if (obj == null) return null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val title = obj.str("title")?.takeIf { it.isNotBlank() } ?: "Unknown Track"
        val artist = obj.str("artist")?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val rawArt = obj.str("artworkUrl")?.takeIf { it.isNotBlank() && it != "null" }
        val resolvedArt = rawArt ?: if (TrackResolver.isValidYouTubeId(id)) youtubeThumbUrl(id) else null
        return PlayTogetherTrack(
            id = id,
            title = title,
            artist = artist,
            album = obj.str("album")?.takeIf { it.isNotBlank() && it != "null" },
            artworkUrl = resolvedArt,
            durationMs = obj.lng("durationMs")?.coerceAtLeast(0L) ?: 0L,
            channelUrl = obj.str("channelUrl")?.takeIf { it.isNotBlank() && it != "null" },
            isYouTube = obj.bool("isYouTube") ?: TrackResolver.isValidYouTubeId(id),
            addedBy = obj.str("addedBy")?.takeIf { it.isNotBlank() } ?: "Host",
            isAutoRadio = obj.bool("isAutoRadio") ?: false,
            validated = obj.bool("validated") ?: TrackResolver.isValidYouTubeId(id),
        )
    }

    fun encodeTrackList(list: List<PlayTogetherTrack>): JsonArray = buildJsonArray {
        list.forEach { add(encodeTrack(it)) }
    }

    fun decodeTrackList(arr: JsonArray?): List<PlayTogetherTrack> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el -> decodeTrack(el as? JsonObject) }
    }

    fun encodeSongRequest(req: SongRequest): JsonObject = buildJsonObject {
        put("id", req.id)
        put("roomId", req.roomId)
        put("track", encodeTrack(req.track))
        put("requesterName", req.requesterName)
        put("requesterId", req.requesterId)
        put("requestTimeMs", req.requestTimeMs)
        put("updatedTimeMs", req.updatedTimeMs)
        put("status", req.status.name)
    }

    fun decodeSongRequest(obj: JsonObject?): SongRequest? {
        if (obj == null) return null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val track = decodeTrack(obj["track"] as? JsonObject) ?: return null
        val reqTime = obj.lng("requestTimeMs") ?: 0L
        return SongRequest(
            id = id,
            roomId = obj.str("roomId").orEmpty(),
            track = track,
            requesterName = obj.str("requesterName")?.takeIf { it.isNotBlank() } ?: "Listener",
            requesterId = obj.str("requesterId").orEmpty(),
            requestTimeMs = reqTime,
            updatedTimeMs = obj.lng("updatedTimeMs") ?: reqTime,
            status = parseSongRequestStatus(obj.str("status")),
        )
    }

    fun encodeSongRequestList(list: List<SongRequest>): JsonArray = buildJsonArray {
        list.forEach { add(encodeSongRequest(it)) }
    }

    fun decodeSongRequestList(arr: JsonArray?): List<SongRequest> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el -> decodeSongRequest(el as? JsonObject) }
    }

    fun encodeMember(m: RoomMember): JsonObject = buildJsonObject {
        put("id", m.id)
        put("name", m.name)
        put("isHost", m.isHost)
        put("syncState", m.syncState.name)
        put("driftMs", m.driftMs)
        put("lastSeenUtc", m.lastSeenUtc)
    }

    fun decodeMember(obj: JsonObject?): RoomMember? {
        if (obj == null) return null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val syncState = runCatching {
            MemberSyncState.valueOf(obj.str("syncState").orEmpty().uppercase())
        }.getOrDefault(MemberSyncState.SYNCING)
        return RoomMember(
            id = id,
            name = obj.str("name")?.takeIf { it.isNotBlank() } ?: "Listener",
            isHost = obj.bool("isHost") ?: false,
            syncState = syncState,
            driftMs = obj.lng("driftMs") ?: 0L,
            lastSeenUtc = obj.lng("lastSeenUtc") ?: 0L,
        )
    }

    fun encodeMemberList(list: List<RoomMember>): JsonArray = buildJsonArray {
        list.forEach { add(encodeMember(it)) }
    }

    fun decodeMemberList(arr: JsonArray?): List<RoomMember> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el -> decodeMember(el as? JsonObject) }
    }

    fun encodeRoomInfo(r: RoomInfo, includePasswordHashOrRaw: Boolean = false): JsonObject = buildJsonObject {
        put("id", r.id)
        put("code", r.code)
        put("name", r.name)
        put("hostName", r.hostName)
        put("hostId", r.hostId)
        put("visibility", r.visibility.name)
        put("controlMode", r.controlMode.name)
        put("memberCount", r.memberCount)
        put("allowListenerRequests", r.allowListenerRequests)
        put("isClosed", r.isClosed)
        put("createdUtc", r.createdUtc)
        r.currentTrack?.let { put("currentTrack", encodeTrack(it)) }
        if (includePasswordHashOrRaw && !r.password.isNullOrBlank()) {
            put("password", r.password)
        }
    }

    fun decodeRoomInfo(obj: JsonObject?): RoomInfo? {
        if (obj == null) return null
        val code = obj.str("code")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: "room_$code"
        val visibility = runCatching {
            RoomVisibility.valueOf(obj.str("visibility").orEmpty().uppercase())
        }.getOrDefault(RoomVisibility.PUBLIC)
        val controlMode = runCatching {
            ControlMode.valueOf(obj.str("controlMode").orEmpty().uppercase())
        }.getOrDefault(ControlMode.HOST_ONLY)
        return RoomInfo(
            id = id,
            code = code,
            name = obj.str("name")?.takeIf { it.isNotBlank() } ?: "PRVN Music Lounge",
            hostName = obj.str("hostName")?.takeIf { it.isNotBlank() } ?: "PRVN Host",
            hostId = obj.str("hostId").orEmpty(),
            visibility = visibility,
            controlMode = controlMode,
            memberCount = obj.int("memberCount") ?: 1,
            currentTrack = decodeTrack(obj["currentTrack"] as? JsonObject),
            password = obj.str("password")?.takeIf { it.isNotBlank() },
            isClosed = obj.bool("isClosed") ?: false,
            createdUtc = obj.lng("createdUtc") ?: 0L,
            allowListenerRequests = obj.bool("allowListenerRequests") ?: true,
        )
    }

    fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.lng(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}
