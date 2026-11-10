package io.ymusic.ui.playtogether.storage

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Persistent SQLite schema for Play Together rooms, shared queues, song requests, participants,
 * and processed event deduplication across reconnects and app restarts.
 */
object PlayTogetherSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.exec(
            """
            CREATE TABLE pt_rooms (
                room_id TEXT PRIMARY KEY NOT NULL,
                code TEXT NOT NULL,
                name TEXT NOT NULL,
                host_name TEXT NOT NULL,
                host_id TEXT NOT NULL,
                visibility TEXT NOT NULL,
                control_mode TEXT NOT NULL,
                allow_requests INTEGER NOT NULL DEFAULT 1,
                current_track_json TEXT,
                is_playing INTEGER NOT NULL DEFAULT 0,
                position_ms INTEGER NOT NULL DEFAULT 0,
                playback_seq INTEGER NOT NULL DEFAULT 0,
                queue_version INTEGER NOT NULL DEFAULT 0,
                playback_start_utc INTEGER NOT NULL DEFAULT 0,
                member_id TEXT NOT NULL,
                member_name TEXT NOT NULL,
                is_host INTEGER NOT NULL DEFAULT 0,
                is_closed INTEGER NOT NULL DEFAULT 0,
                active_session INTEGER NOT NULL DEFAULT 0,
                created_utc INTEGER NOT NULL DEFAULT 0,
                updated_utc INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        driver.exec("CREATE INDEX idx_pt_rooms_active ON pt_rooms(active_session, updated_utc)")
        driver.exec("CREATE INDEX idx_pt_rooms_code ON pt_rooms(code)")

        driver.exec(
            """
            CREATE TABLE pt_queue (
                room_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                track_json TEXT NOT NULL,
                PRIMARY KEY (room_id, position)
            )
            """.trimIndent(),
        )

        driver.exec(
            """
            CREATE TABLE pt_requests (
                request_id TEXT PRIMARY KEY NOT NULL,
                room_id TEXT NOT NULL,
                track_json TEXT NOT NULL,
                requester_name TEXT NOT NULL,
                requester_id TEXT NOT NULL,
                status TEXT NOT NULL,
                request_time_ms INTEGER NOT NULL,
                updated_time_ms INTEGER NOT NULL,
                synced_remote INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        driver.exec("CREATE INDEX idx_pt_requests_room ON pt_requests(room_id, request_time_ms DESC)")

        driver.exec(
            """
            CREATE TABLE pt_members (
                room_id TEXT NOT NULL,
                member_id TEXT NOT NULL,
                name TEXT NOT NULL,
                is_host INTEGER NOT NULL DEFAULT 0,
                sync_state TEXT NOT NULL,
                drift_ms INTEGER NOT NULL DEFAULT 0,
                last_seen_utc INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (room_id, member_id)
            )
            """.trimIndent(),
        )

        driver.exec(
            """
            CREATE TABLE pt_events (
                room_id TEXT NOT NULL,
                event_id TEXT NOT NULL,
                created_utc INTEGER NOT NULL,
                PRIMARY KEY (room_id, event_id)
            )
            """.trimIndent(),
        )
        driver.exec("CREATE INDEX idx_pt_events_created ON pt_events(room_id, created_utc)")

        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
}

private fun SqlDriver.exec(sql: String) {
    execute(null, sql, 0)
}
