package com.maxrave.data.repository

import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.repository.UpdateRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.kotlinytmusicscraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class UpdateRepositoryImpl(
    private val youTube: YouTube,
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            val githubResult = youTube.checkForGithubReleaseUpdate()
            if (githubResult.isSuccess) {
                val response = githubResult.getOrThrow()
                val apkAsset = response.assets?.filterNotNull()?.firstOrNull { asset ->
                    val name = asset.name?.lowercase() ?: ""
                    name.endsWith(".apk") && (name.contains("universal") || name.contains("arm64"))
                } ?: response.assets?.filterNotNull()?.firstOrNull {
                    it.name?.endsWith(".apk", ignoreCase = true) == true
                }
                val tagName = response.tagName ?: ""
                val cleanTag = if (tagName.startsWith("v")) tagName else "v$tagName"
                val fallbackDownloadUrl = "https://github.com/pritam-79/PRVN-Music/releases/download/$cleanTag/PRVN-Music-${cleanTag.removePrefix("v")}-universal-release.apk"

                emit(
                    Resource.Success(
                        UpdateData(
                            tagName = tagName,
                            releaseTime = response.publishedAt ?: "",
                            body = response.body ?: "",
                            downloadUrl = apkAsset?.browserDownloadUrl ?: fallbackDownloadUrl,
                            size = apkAsset?.size?.toLong(),
                        ),
                    ),
                )
            } else {
                val rawResult = youTube.checkForRawUpdateJson()
                if (rawResult.isSuccess) {
                    val raw = rawResult.getOrThrow()
                    emit(
                        Resource.Success(
                            UpdateData(
                                tagName = raw.versionName,
                                releaseTime = null,
                                body = raw.releaseNotes,
                                downloadUrl = raw.downloadUrl,
                                size = null,
                            ),
                        ),
                    )
                } else {
                    emit(Resource.Error(githubResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"))
                }
            }
        }.flowOn(Dispatchers.IO)

    override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> =
        flow {
            youTube
                .checkForFdroidUpdate()
                .onSuccess { response ->
                    val latestVersion = response.packages.maxBy { it.versionCode }
                    emit(
                        Resource.Success(
                            UpdateData(
                                tagName = latestVersion.versionName,
                                releaseTime = null,
                                body =
                                    $$"""
                                    ### Update via F-Droid, changelogs: 
                                    - https://github.com/maxrave-dev/SimpMusic/blob/dev/fastlane/metadata/android/en-US/changelogs/$${latestVersion.versionCode}.txt
                                    """.trimIndent(),
                            ),
                        ),
                    )
                }.onFailure {
                    emit(Resource.Error<UpdateData>(it.localizedMessage ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)

    override fun getFdroidSigningKeys(): Flow<Resource<List<String>>> =
        flow {
            youTube
                .getFdroidSigningKeys()
                .onSuccess { emit(Resource.Success(it)) }
                .onFailure { emit(Resource.Error<List<String>>(it.localizedMessage ?: "Unknown error")) }
        }.flowOn(Dispatchers.IO)
}