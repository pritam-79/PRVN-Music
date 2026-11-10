package io.ymusic.ui.playtogether

import androidx.compose.runtime.compositionLocalOf
import io.ymusic.dynamicengine.yt.YtFeedItem

/**
 * Exposes active Play Together room context to Discover, YT Music, Search, Playlist, and NowPlaying screens
 * so tracks across the whole app share one synchronized pipeline.
 */
data class PlayTogetherUiActions(
    val isInRoom: Boolean = false,
    val roomName: String = "",
    val isHost: Boolean = false,
    val canControl: Boolean = false,
    val allowRequests: Boolean = false,
    val requestInRoom: (YtFeedItem) -> Unit = {},
    val queueInRoom: (YtFeedItem) -> Unit = {},
    val playInRoom: (YtFeedItem) -> Unit = {},
    /** Intercepts direct video clicks in Discover/Search when inside an active Play Together room. */
    val onDirectPlayClick: ((YtFeedItem, fallbackPlay: () -> Unit) -> Unit)? = null,
)

val LocalPlayTogetherActions = compositionLocalOf { PlayTogetherUiActions() }
