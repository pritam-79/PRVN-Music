package com.maxrave.kotlinytmusicscraper.models.simpmusic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawUpdateResponse(
    @SerialName("versionName")
    val versionName: String = "",
    @SerialName("versionCode")
    val versionCode: Int = 0,
    @SerialName("downloadUrl")
    val downloadUrl: String = "",
    @SerialName("releaseNotes")
    val releaseNotes: String = "",
    @SerialName("forceUpdate")
    val forceUpdate: Boolean = false,
)
