package io.ymusic.ui.playtogether

enum class RoomVisibility {
    PUBLIC,
    INVITE_ONLY,
    PRIVATE,
}

enum class ControlMode {
    HOST_ONLY,
    COLLABORATIVE,
}

enum class MemberSyncState {
    SYNCED,
    BUFFERING,
    SYNCING,
    RECONNECTING,
}

enum class SongRequestStatus(val displayLabel: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    ADDED_TO_QUEUE("Added to Queue"),
    PLAYED("Played"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    ;

    val isTerminal: Boolean
        get() = this == PLAYED || this == REJECTED || this == CANCELLED
}

enum class PlaybackConnectionState {
    CONNECTED,
    RECONNECTING,
    OFFLINE,
}

enum class RoomTab(val label: String) {
    NOW_PLAYING("Now Playing"),
    SHARED_QUEUE("Queue"),
    SEARCH_MUSIC("Search"),
    SONG_REQUESTS("Requests"),
    PARTICIPANTS("Participants"),
}

data class PlayTogetherTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val channelUrl: String? = null,
    val isYouTube: Boolean = true,
    val addedBy: String = "Host",
    val isAutoRadio: Boolean = false,
    val validated: Boolean = false,
)

data class SongRequest(
    val id: String,
    val roomId: String = "",
    val track: PlayTogetherTrack,
    val requesterName: String,
    val requesterId: String,
    val requestTimeMs: Long,
    val updatedTimeMs: Long = requestTimeMs,
    val status: SongRequestStatus = SongRequestStatus.PENDING,
)

data class RoomMember(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val syncState: MemberSyncState = MemberSyncState.SYNCING,
    val driftMs: Long = 0L,
    val lastSeenUtc: Long = 0L,
)

data class RoomInfo(
    val id: String,
    val code: String,
    val name: String,
    val hostName: String,
    val hostId: String = "",
    val visibility: RoomVisibility = RoomVisibility.PUBLIC,
    val controlMode: ControlMode = ControlMode.HOST_ONLY,
    val memberCount: Int = 1,
    val currentTrack: PlayTogetherTrack? = null,
    val password: String? = null,
    val isClosed: Boolean = false,
    val createdUtc: Long = 0L,
    val allowListenerRequests: Boolean = true,
)

sealed interface PlayTogetherState {
    data class Lobby(
        val activeRooms: List<RoomInfo> = emptyList(),
        val recentRooms: List<RoomInfo> = emptyList(),
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
        val connectionState: PlaybackConnectionState = PlaybackConnectionState.CONNECTED,
    ) : PlayTogetherState

    data class InRoom(
        val room: RoomInfo,
        val isHost: Boolean,
        val members: List<RoomMember>,
        val currentTrack: PlayTogetherTrack?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val queue: List<PlayTogetherTrack>,
        val songRequests: List<SongRequest> = emptyList(),
        val activeTab: RoomTab = RoomTab.NOW_PLAYING,
        val controlMode: ControlMode = room.controlMode,
        val syncOffsetMs: Long = 0L,
        val networkRttMs: Long = 0L,
        val clockOffsetMs: Long = 0L,
        val isBuffering: Boolean = false,
        val isActuallySynced: Boolean = false,
        val isConnecting: Boolean = false,
        val connectionState: PlaybackConnectionState = PlaybackConnectionState.CONNECTED,
        val errorMessage: String? = null,
        val statusBanner: String? = null,
        val incomingRequestAlert: SongRequest? = null,
        val currentMemberId: String = "member_self",
        val currentUserName: String = "Listener",
        val allowListenerRequests: Boolean = room.allowListenerRequests,
        val isAutoRadio: Boolean = false,
        val playbackSeq: Long = 0L,
        val queueVersion: Long = 0L,
        val playbackStartTimestampUtc: Long = 0L,
        val serverTimestampUtc: Long = 0L,
    ) : PlayTogetherState {
        val pendingRequestCount: Int
            get() = songRequests.count { it.status == SongRequestStatus.PENDING }
    }
}
