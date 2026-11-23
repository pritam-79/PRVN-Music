package com.maxrave.domain.repository

import com.maxrave.domain.data.model.cloudsync.CloudSyncAccount
import com.maxrave.domain.data.model.cloudsync.CloudSyncPayload
import com.maxrave.domain.data.model.cloudsync.CloudSyncStatus
import com.maxrave.domain.data.model.cloudsync.SyncedPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface UniversalCloudSyncRepository {
    val syncStatus: StateFlow<CloudSyncStatus>
    val currentAccount: StateFlow<CloudSyncAccount?>
    val lastPlaybackState: StateFlow<SyncedPlaybackState?>

    suspend fun signIn(username: String, email: String, token: String)
    suspend fun signOut()
    suspend fun triggerSync(): Boolean
    suspend fun updatePlaybackPosition(
        videoId: String,
        title: String,
        artist: String,
        positionMs: Long,
        isPlaying: Boolean,
    )
    suspend fun getLocalSnapshot(): CloudSyncPayload
    suspend fun applyRemoteSnapshot(payload: CloudSyncPayload)
}
