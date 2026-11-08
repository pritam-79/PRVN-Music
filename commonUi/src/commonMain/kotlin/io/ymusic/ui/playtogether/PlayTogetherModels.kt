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
}

data class PlayTogetherTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 210000L,
    val isYouTube: Boolean = true,
    val addedBy: String = "Host",
)

data class RoomMember(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val syncState: MemberSyncState = MemberSyncState.SYNCED,
    val driftMs: Long = 12L,
)

data class RoomInfo(
    val id: String,
    val code: String,
    val name: String,
    val hostName: String,
    val visibility: RoomVisibility = RoomVisibility.PUBLIC,
    val controlMode: ControlMode = ControlMode.HOST_ONLY,
    val memberCount: Int = 1,
    val currentTrack: PlayTogetherTrack? = null,
)

sealed interface PlayTogetherState {
    data class Lobby(
        val activeRooms: List<RoomInfo> = emptyList(),
        val recentRooms: List<RoomInfo> = emptyList(),
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
    ) : PlayTogetherState

    data class InRoom(
        val room: RoomInfo,
        val isHost: Boolean,
        val members: List<RoomMember>,
        val currentTrack: PlayTogetherTrack?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val queue: List<PlayTogetherTrack>,
        val controlMode: ControlMode = room.controlMode,
        val syncOffsetMs: Long = 14L,
        val isConnecting: Boolean = false,
        val errorMessage: String? = null,
        val currentMemberId: String = "member_self",
    ) : PlayTogetherState
}
