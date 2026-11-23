package com.maxrave.domain.data.model.cloudsync

import kotlinx.serialization.Serializable

@Serializable
data class SyncedDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String, // "Android", "iOS", "Desktop"
    val lastActiveAt: Long,
    val isCurrentDevice: Boolean = false,
)

@Serializable
data class SyncedPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class SyncedHistoryItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val playedAt: Long,
)

@Serializable
data class SyncedPlaybackState(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val updatedAt: Long = 0L,
    val activeDeviceId: String = "",
)

@Serializable
data class CloudSyncPayload(
    val version: Int = 1,
    val deviceId: String = "",
    val timestamp: Long = 0L,
    val favorites: List<String> = emptyList(),
    val playlists: List<SyncedPlaylist> = emptyList(),
    val history: List<SyncedHistoryItem> = emptyList(),
    val savedAlbums: List<String> = emptyList(),
    val savedArtists: List<String> = emptyList(),
    val playbackPosition: SyncedPlaybackState? = null,
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class CloudSyncAccount(
    val userId: String,
    val username: String,
    val email: String,
    val authToken: String,
    val lastSyncTime: Long = 0L,
    val pairedDevices: List<SyncedDevice> = emptyList(),
)

sealed interface CloudSyncStatus {
    data object Idle : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val timestamp: Long, val deviceCount: Int) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}
