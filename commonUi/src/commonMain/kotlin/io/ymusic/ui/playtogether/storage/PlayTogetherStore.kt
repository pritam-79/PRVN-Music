package io.ymusic.ui.playtogether.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import io.ymusic.ui.playtogether.ControlMode
import io.ymusic.ui.playtogether.MemberSyncState
import io.ymusic.ui.playtogether.PlayTogetherState
import io.ymusic.ui.playtogether.PlayTogetherTrack
import io.ymusic.ui.playtogether.RoomInfo
import io.ymusic.ui.playtogether.RoomMember
import io.ymusic.ui.playtogether.RoomVisibility
import io.ymusic.ui.playtogether.SongRequest
import io.ymusic.ui.playtogether.SongRequestStatus
import io.ymusic.ui.playtogether.backend.PlayTogetherJson
import io.ymusic.ui.playtogether.backend.parseSongRequestStatus
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class PersistedRoomSession(
    val room: RoomInfo,
    val isHost: Boolean,
    val memberId: String,
    val memberName: String,
    val currentTrack: PlayTogetherTrack?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val playbackSeq: Long,
    val queueVersion: Long,
    val playbackStartUtc: Long,
    val queue: List<PlayTogetherTrack>,
    val songRequests: List<SongRequest>,
    val members: List<RoomMember>,
)

/**
 * SQLDelight-backed persistent store for Play Together rooms, queues, song requests,
 * participants, and duplicate-event suppression.
 */
class PlayTogetherStore(
    private val driver: SqlDriver,
) {
    private val io = playTogetherDispatcher()

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    suspend fun saveActiveSession(state: PlayTogetherState.InRoom) = withContext(io) {
        val now = nowMs()
        val room = state.room
        // Ensure only one room is marked active at a time
        driver.execute(null, "UPDATE pt_rooms SET active_session = 0 WHERE room_id != ?", 1) {
            bindString(0, room.id)
        }
        driver.execute(
            null,
            """
            INSERT OR REPLACE INTO pt_rooms(
                room_id, code, name, host_name, host_id, visibility, control_mode,
                allow_requests, current_track_json, is_playing, position_ms,
                playback_seq, queue_version, playback_start_utc,
                member_id, member_name, is_host, is_closed, active_session,
                created_utc, updated_utc
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
            """.trimIndent(),
            20,
        ) {
            bindString(0, room.id)
            bindString(1, room.code)
            bindString(2, room.name)
            bindString(3, room.hostName)
            bindString(4, room.hostId)
            bindString(5, room.visibility.name)
            bindString(6, state.controlMode.name)
            bindLong(7, if (state.allowListenerRequests) 1L else 0L)
            bindString(8, state.currentTrack?.let { PlayTogetherJson.encodeTrack(it).toString() })
            bindLong(9, if (state.isPlaying) 1L else 0L)
            bindLong(10, state.positionMs)
            bindLong(11, state.playbackSeq)
            bindLong(12, state.queueVersion)
            bindLong(13, state.playbackStartTimestampUtc)
            bindString(14, state.currentMemberId)
            bindString(15, state.currentUserName)
            bindLong(16, if (state.isHost) 1L else 0L)
            bindLong(17, if (room.isClosed) 1L else 0L)
            bindLong(18, if (room.createdUtc > 0L) room.createdUtc else now)
            bindLong(19, now)
        }

        // Persist ordered queue
        driver.execute(null, "DELETE FROM pt_queue WHERE room_id = ?", 1) {
            bindString(0, room.id)
        }
        state.queue.forEachIndexed { idx, track ->
            driver.execute(
                null,
                "INSERT INTO pt_queue(room_id, position, track_json) VALUES (?, ?, ?)",
                3,
            ) {
                bindString(0, room.id)
                bindLong(1, idx.toLong())
                bindString(2, PlayTogetherJson.encodeTrack(track).toString())
            }
        }

        // Persist members snapshot
        driver.execute(null, "DELETE FROM pt_members WHERE room_id = ?", 1) {
            bindString(0, room.id)
        }
        state.members.forEach { member ->
            driver.execute(
                null,
                """
                INSERT INTO pt_members(room_id, member_id, name, is_host, sync_state, drift_ms, last_seen_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                7,
            ) {
                bindString(0, room.id)
                bindString(1, member.id)
                bindString(2, member.name)
                bindLong(3, if (member.isHost) 1L else 0L)
                bindString(4, member.syncState.name)
                bindLong(5, member.driftMs)
                bindLong(6, member.lastSeenUtc)
            }
        }

        // Upsert all song requests without overwriting pending local unsynced statuses blindly
        state.songRequests.forEach { req ->
            upsertRequestInternal(req.copy(roomId = room.id), syncedRemote = true)
        }
    }

    suspend fun getActiveSession(): PersistedRoomSession? = withContext(io) {
        val row = driver.executeQuery(
            null,
            """
            SELECT room_id, code, name, host_name, host_id, visibility, control_mode,
                   allow_requests, current_track_json, is_playing, position_ms,
                   playback_seq, queue_version, playback_start_utc,
                   member_id, member_name, is_host, is_closed, created_utc
            FROM pt_rooms
            WHERE active_session = 1 AND is_closed = 0
            ORDER BY updated_utc DESC
            LIMIT 1
            """.trimIndent(),
            { cursor ->
                if (!cursor.next().value) return@executeQuery QueryResult.Value(null)
                val roomId = cursor.getString(0) ?: return@executeQuery QueryResult.Value(null)
                val code = cursor.getString(1).orEmpty()
                val name = cursor.getString(2).orEmpty()
                val hostName = cursor.getString(3).orEmpty()
                val hostId = cursor.getString(4).orEmpty()
                val visibility = runCatching {
                    RoomVisibility.valueOf(cursor.getString(5).orEmpty())
                }.getOrDefault(RoomVisibility.PUBLIC)
                val controlMode = runCatching {
                    ControlMode.valueOf(cursor.getString(6).orEmpty())
                }.getOrDefault(ControlMode.HOST_ONLY)
                val allowRequests = (cursor.getLong(7) ?: 1L) != 0L
                val currentTrack = PlayTogetherJson.decodeTrack(PlayTogetherJson.parseObjectOrNull(cursor.getString(8)))
                val isPlaying = (cursor.getLong(9) ?: 0L) != 0L
                val positionMs = cursor.getLong(10) ?: 0L
                val playbackSeq = cursor.getLong(11) ?: 0L
                val queueVersion = cursor.getLong(12) ?: 0L
                val playbackStartUtc = cursor.getLong(13) ?: 0L
                val memberId = cursor.getString(14).orEmpty()
                val memberName = cursor.getString(15).orEmpty()
                val isHost = (cursor.getLong(16) ?: 0L) != 0L
                val isClosed = (cursor.getLong(17) ?: 0L) != 0L
                val createdUtc = cursor.getLong(18) ?: 0L

                val room = RoomInfo(
                    id = roomId,
                    code = code,
                    name = name,
                    hostName = hostName,
                    hostId = hostId,
                    visibility = visibility,
                    controlMode = controlMode,
                    memberCount = 1,
                    currentTrack = currentTrack,
                    isClosed = isClosed,
                    createdUtc = createdUtc,
                    allowListenerRequests = allowRequests,
                )
                QueryResult.Value(
                    PersistedRoomSession(
                        room = room,
                        isHost = isHost,
                        memberId = memberId,
                        memberName = memberName,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        playbackSeq = playbackSeq,
                        queueVersion = queueVersion,
                        playbackStartUtc = playbackStartUtc,
                        queue = emptyList(),
                        songRequests = emptyList(),
                        members = emptyList(),
                    ),
                )
            },
            0,
        ).value ?: return@withContext null

        val queue = readQueueInternal(row.room.id)
        val requests = readRequestsInternal(row.room.id)
        val members = readMembersInternal(row.room.id)
        row.copy(
            room = row.room.copy(memberCount = members.size.coerceAtLeast(1)),
            queue = queue,
            songRequests = requests,
            members = members,
        )
    }

    suspend fun clearActiveSession(roomId: String? = null) = withContext(io) {
        if (roomId.isNullOrBlank()) {
            driver.execute(null, "UPDATE pt_rooms SET active_session = 0", 0)
        } else {
            driver.execute(null, "UPDATE pt_rooms SET active_session = 0 WHERE room_id = ?", 1) {
                bindString(0, roomId)
            }
        }
    }

    suspend fun markRoomClosed(roomId: String) = withContext(io) {
        driver.execute(
            null,
            "UPDATE pt_rooms SET is_closed = 1, active_session = 0, updated_utc = ? WHERE room_id = ?",
            2,
        ) {
            bindLong(0, nowMs())
            bindString(1, roomId)
        }
    }

    suspend fun deleteRoom(roomId: String) = withContext(io) {
        driver.execute(null, "DELETE FROM pt_queue WHERE room_id = ?", 1) { bindString(0, roomId) }
        driver.execute(null, "DELETE FROM pt_requests WHERE room_id = ?", 1) { bindString(0, roomId) }
        driver.execute(null, "DELETE FROM pt_members WHERE room_id = ?", 1) { bindString(0, roomId) }
        driver.execute(null, "DELETE FROM pt_events WHERE room_id = ?", 1) { bindString(0, roomId) }
        driver.execute(null, "DELETE FROM pt_rooms WHERE room_id = ?", 1) { bindString(0, roomId) }
    }

    suspend fun listRecentRooms(limit: Int = 8): List<RoomInfo> = withContext(io) {
        driver.executeQuery(
            null,
            """
            SELECT room_id, code, name, host_name, host_id, visibility, control_mode,
                   allow_requests, current_track_json, created_utc
            FROM pt_rooms
            WHERE is_closed = 0
            ORDER BY updated_utc DESC
            LIMIT ?
            """.trimIndent(),
            { cursor ->
                val list = mutableListOf<RoomInfo>()
                while (cursor.next().value) {
                    val roomId = cursor.getString(0) ?: continue
                    val code = cursor.getString(1) ?: continue
                    list += RoomInfo(
                        id = roomId,
                        code = code,
                        name = cursor.getString(2).orEmpty(),
                        hostName = cursor.getString(3).orEmpty(),
                        hostId = cursor.getString(4).orEmpty(),
                        visibility = runCatching {
                            RoomVisibility.valueOf(cursor.getString(5).orEmpty())
                        }.getOrDefault(RoomVisibility.PUBLIC),
                        controlMode = runCatching {
                            ControlMode.valueOf(cursor.getString(6).orEmpty())
                        }.getOrDefault(ControlMode.HOST_ONLY),
                        memberCount = 1,
                        currentTrack = PlayTogetherJson.decodeTrack(PlayTogetherJson.parseObjectOrNull(cursor.getString(8))),
                        createdUtc = cursor.getLong(9) ?: 0L,
                        allowListenerRequests = (cursor.getLong(7) ?: 1L) != 0L,
                    )
                }
                QueryResult.Value(list)
            },
            1,
        ) { bindLong(0, limit.toLong()) }.value
    }

    suspend fun saveOrUpdateRequest(request: SongRequest, syncedRemote: Boolean = true) = withContext(io) {
        upsertRequestInternal(request, syncedRemote)
    }

    suspend fun listRequests(roomId: String): List<SongRequest> = withContext(io) {
        readRequestsInternal(roomId)
    }

    suspend fun listUnsyncedRequests(roomId: String): List<SongRequest> = withContext(io) {
        driver.executeQuery(
            null,
            """
            SELECT request_id, room_id, track_json, requester_name, requester_id, status, request_time_ms, updated_time_ms
            FROM pt_requests
            WHERE room_id = ? AND synced_remote = 0
            ORDER BY request_time_ms ASC
            """.trimIndent(),
            { cursor ->
                val out = mutableListOf<SongRequest>()
                while (cursor.next().value) {
                    cursorToSongRequest(cursor)?.let { out += it }
                }
                QueryResult.Value(out)
            },
            1,
        ) { bindString(0, roomId) }.value
    }

    suspend fun markRequestSynced(requestId: String) = withContext(io) {
        driver.execute(null, "UPDATE pt_requests SET synced_remote = 1 WHERE request_id = ?", 1) {
            bindString(0, requestId)
        }
    }

    /** Returns true if this event ID had never been recorded before for [roomId]. */
    suspend fun recordEventIfNew(roomId: String, eventId: String): Boolean = withContext(io) {
        if (eventId.isBlank()) return@withContext true
        val exists = driver.executeQuery(
            null,
            "SELECT 1 FROM pt_events WHERE room_id = ? AND event_id = ? LIMIT 1",
            { cursor -> QueryResult.Value(cursor.next().value) },
            2,
        ) {
            bindString(0, roomId)
            bindString(1, eventId)
        }.value
        if (exists) return@withContext false

        driver.execute(
            null,
            "INSERT OR IGNORE INTO pt_events(room_id, event_id, created_utc) VALUES (?, ?, ?)",
            3,
        ) {
            bindString(0, roomId)
            bindString(1, eventId)
            bindLong(2, nowMs())
        }
        driver.execute(
            null,
            """
            DELETE FROM pt_events
            WHERE room_id = ? AND event_id NOT IN (
                SELECT event_id FROM pt_events WHERE room_id = ? ORDER BY created_utc DESC LIMIT 400
            )
            """.trimIndent(),
            2,
        ) {
            bindString(0, roomId)
            bindString(1, roomId)
        }
        true
    }

    private fun upsertRequestInternal(request: SongRequest, syncedRemote: Boolean) {
        if (request.id.isBlank() || request.roomId.isBlank()) return
        driver.execute(
            null,
            """
            INSERT OR REPLACE INTO pt_requests(
                request_id, room_id, track_json, requester_name, requester_id,
                status, request_time_ms, updated_time_ms, synced_remote
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            9,
        ) {
            bindString(0, request.id)
            bindString(1, request.roomId)
            bindString(2, PlayTogetherJson.encodeTrack(request.track).toString())
            bindString(3, request.requesterName)
            bindString(4, request.requesterId)
            bindString(5, request.status.name)
            bindLong(6, request.requestTimeMs)
            bindLong(7, request.updatedTimeMs)
            bindLong(8, if (syncedRemote) 1L else 0L)
        }
    }

    private fun readQueueInternal(roomId: String): List<PlayTogetherTrack> =
        driver.executeQuery(
            null,
            "SELECT track_json FROM pt_queue WHERE room_id = ? ORDER BY position ASC",
            { cursor ->
                val out = mutableListOf<PlayTogetherTrack>()
                while (cursor.next().value) {
                    PlayTogetherJson.decodeTrack(PlayTogetherJson.parseObjectOrNull(cursor.getString(0)))?.let { out += it }
                }
                QueryResult.Value(out)
            },
            1,
        ) { bindString(0, roomId) }.value

    private fun readRequestsInternal(roomId: String): List<SongRequest> =
        driver.executeQuery(
            null,
            """
            SELECT request_id, room_id, track_json, requester_name, requester_id, status, request_time_ms, updated_time_ms
            FROM pt_requests
            WHERE room_id = ?
            ORDER BY request_time_ms DESC
            """.trimIndent(),
            { cursor ->
                val out = mutableListOf<SongRequest>()
                while (cursor.next().value) {
                    cursorToSongRequest(cursor)?.let { out += it }
                }
                QueryResult.Value(out)
            },
            1,
        ) { bindString(0, roomId) }.value

    private fun readMembersInternal(roomId: String): List<RoomMember> =
        driver.executeQuery(
            null,
            "SELECT member_id, name, is_host, sync_state, drift_ms, last_seen_utc FROM pt_members WHERE room_id = ?",
            { cursor ->
                val out = mutableListOf<RoomMember>()
                while (cursor.next().value) {
                    val memberId = cursor.getString(0) ?: continue
                    val syncState = runCatching {
                        MemberSyncState.valueOf(cursor.getString(3).orEmpty())
                    }.getOrDefault(MemberSyncState.SYNCING)
                    out += RoomMember(
                        id = memberId,
                        name = cursor.getString(1).orEmpty(),
                        isHost = (cursor.getLong(2) ?: 0L) != 0L,
                        syncState = syncState,
                        driftMs = cursor.getLong(4) ?: 0L,
                        lastSeenUtc = cursor.getLong(5) ?: 0L,
                    )
                }
                QueryResult.Value(out)
            },
            1,
        ) { bindString(0, roomId) }.value

    private fun cursorToSongRequest(cursor: app.cash.sqldelight.db.SqlCursor): SongRequest? {
        val reqId = cursor.getString(0) ?: return null
        val roomId = cursor.getString(1).orEmpty()
        val track = PlayTogetherJson.decodeTrack(PlayTogetherJson.parseObjectOrNull(cursor.getString(2))) ?: return null
        return SongRequest(
            id = reqId,
            roomId = roomId,
            track = track,
            requesterName = cursor.getString(3).orEmpty(),
            requesterId = cursor.getString(4).orEmpty(),
            status = parseSongRequestStatus(cursor.getString(5)),
            requestTimeMs = cursor.getLong(6) ?: 0L,
            updatedTimeMs = cursor.getLong(7) ?: 0L,
        )
    }
}
