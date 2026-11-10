package io.ymusic.ui.playtogether

import io.ymusic.dynamicengine.DynamicEngineManager
import io.ymusic.dynamicengine.yt.YtChip
import io.ymusic.dynamicengine.yt.YtFeedItem
import io.ymusic.dynamicengine.yt.YtFeedPage
import io.ymusic.dynamicengine.yt.YtLinkTarget
import io.ymusic.dynamicengine.yt.extractMusicCharts
import io.ymusic.dynamicengine.yt.extractMusicHome
import io.ymusic.dynamicengine.yt.extractVideoDetails
import io.ymusic.dynamicengine.yt.musicSearch
import io.ymusic.dynamicengine.yt.resolveLink
import io.ymusic.dynamicengine.yt.search
import io.ymusic.dynamicengine.yt.startRadioQueue
import io.ymusic.dynamicengine.yt.watchNext
import io.ymusic.player.PlayableItem
import io.ymusic.ui.playhistory.PlayHistoryStore
import io.ymusic.ui.youtubeThumbUrl
import kmpcommon.log.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Search source categories used inside Play Together Search.
 */
enum class PlayTogetherSearchSource(val label: String) {
    MUSIC_SONGS("Songs"),
    MUSIC_ALL("YT Music"),
    YOUTUBE_VIDEOS("YouTube"),
    DISCOVER_PICKS("Discover"),
}

data class PlayTogetherSearchResult(
    val tracks: List<PlayTogetherTrack>,
    val chips: List<YtChip> = emptyList(),
    val continuation: String? = null,
    val errorMessage: String? = null,
)

class UnavailableTrackException(message: String) : IllegalArgumentException(message)

/**
 * Unified Play Together <-> PRVN Discover/Search track resolver, validator, and radio source.
 * Uses the exact same [DynamicEngineManager] & [PlayableItem.YtMedia] pipeline as Discover & Home.
 */
class TrackResolver(
    private val engines: DynamicEngineManager,
    private val playHistory: PlayHistoryStore? = null,
    private val logger: Logger? = null,
) {
    private val cacheMutex = Mutex()
    private val validatedCache = LinkedHashMap<String, PlayTogetherTrack>()
    private val maxCacheSize = 256

    companion object {
        private val YT_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

        fun isValidYouTubeId(id: String?): Boolean =
            !id.isNullOrBlank() && YT_ID_REGEX.matches(id.trim())

        fun isLocalTrackId(id: String?): Boolean {
            if (id.isNullOrBlank()) return false
            return id.startsWith("content://") ||
                id.startsWith("file://") ||
                id.startsWith("/") ||
                id.startsWith("local:")
        }

        fun formatDurationMs(durationMs: Long): String? {
            if (durationMs <= 0L) return null
            val totalSeconds = durationMs / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            val secStr = seconds.toString().padStart(2, '0')
            return if (hours > 0L) {
                val minStr = minutes.toString().padStart(2, '0')
                "$hours:$minStr:$secStr"
            } else {
                "$minutes:$secStr"
            }
        }

        fun parseDurationMs(text: String?): Long {
            val clean = text?.trim().orEmpty()
            if (clean.isEmpty()) return 0L
            val parts = clean.split(':').mapNotNull { it.trim().toLongOrNull() }
            return when (parts.size) {
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                else -> 0L
            }
        }

        /** Pick the highest-resolution usable artwork URL for a [YtFeedItem]. */
        fun bestThumbUrl(item: YtFeedItem): String? {
            val best = item.thumbs.maxByOrNull { (it.w ?: 0) * (it.h ?: 0) }?.url
                ?: item.thumbs.lastOrNull()?.url
            if (!best.isNullOrBlank()) return best
            return if (isValidYouTubeId(item.id)) youtubeThumbUrl(item.id) else null
        }

        /** Convert any Discover / Home / Search [YtFeedItem] cleanly to [PlayTogetherTrack]. */
        fun fromYtFeedItem(
            item: YtFeedItem,
            addedBy: String = "Discover",
            isAutoRadio: Boolean = false,
            validated: Boolean = false,
        ): PlayTogetherTrack {
            val cleanId = item.id.trim()
            val artistText = item.channelTitle?.takeIf { it.isNotBlank() }
                ?: item.subtitleLine1?.substringBefore(" • ")?.trim()?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val albumText = parseAlbumFromSubtitles(item.subtitleLine1, item.subtitleLine2, artistText)
            val resolvedDurationMs = item.durationMs?.takeIf { it > 0L } ?: parseDurationMs(item.duration)
            return PlayTogetherTrack(
                id = cleanId,
                title = item.title.ifBlank { cleanId },
                artist = artistText,
                album = albumText,
                artworkUrl = bestThumbUrl(item),
                durationMs = resolvedDurationMs,
                channelUrl = item.channelUrl,
                isYouTube = isValidYouTubeId(cleanId) && !isLocalTrackId(cleanId),
                addedBy = addedBy,
                isAutoRadio = isAutoRadio,
                validated = validated,
            )
        }

        private fun parseAlbumFromSubtitles(line1: String?, line2: String?, artist: String): String? {
            val candidates = buildList {
                if (!line2.isNullOrBlank()) addAll(line2.split(" • "))
                if (!line1.isNullOrBlank()) addAll(line1.split(" • "))
            }
            for (raw in candidates) {
                val part = raw.trim()
                if (part.isEmpty()) continue
                if (part.equals(artist, ignoreCase = true)) continue
                if (Regex("^[0-9]+:[0-9]{2}(:[0-9]{2})?$").matches(part)) continue
                if (part.contains("plays", ignoreCase = true) || part.contains("views", ignoreCase = true)) continue
                if (part.equals("Song", ignoreCase = true) || part.equals("Video", ignoreCase = true)) continue
                return part
            }
            return null
        }

        /** Convert any currently playing [PlayableItem] to a transferable [PlayTogetherTrack]. */
        fun fromPlayableItem(item: PlayableItem, addedBy: String = "Host"): PlayTogetherTrack? {
            return when (item) {
                is PlayableItem.YtMedia -> PlayTogetherTrack(
                    id = item.videoId.trim(),
                    title = item.title?.takeIf { it.isNotBlank() } ?: item.videoId,
                    artist = item.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                    album = null,
                    artworkUrl = item.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: if (isValidYouTubeId(item.videoId)) youtubeThumbUrl(item.videoId) else null,
                    durationMs = item.durationMs?.takeIf { it > 0L } ?: parseDurationMs(item.duration),
                    channelUrl = item.channelUrl,
                    isYouTube = isValidYouTubeId(item.videoId),
                    addedBy = addedBy,
                    isAutoRadio = false,
                    validated = true,
                )
                is PlayableItem.LocalMedia -> {
                    // Local tracks cannot be streamed across remote peers without an online match.
                    null
                }
            }
        }
    }

    /** Convert [PlayTogetherTrack] directly into the centralized [PlayableItem.YtMedia]. */
    fun toPlayableMedia(track: PlayTogetherTrack): PlayableItem.YtMedia {
        val cleanId = track.id.trim()
        return PlayableItem.YtMedia(
            videoId = cleanId,
            title = track.title.takeIf { it.isNotBlank() } ?: cleanId,
            artist = track.artist.takeIf { it.isNotBlank() },
            channelUrl = track.channelUrl,
            thumbnailUrl = track.artworkUrl?.takeIf { it.isNotBlank() }
                ?: if (isValidYouTubeId(cleanId)) youtubeThumbUrl(cleanId) else null,
            duration = formatDurationMs(track.durationMs),
            durationMs = track.durationMs.takeIf { it > 0L },
            live = false,
        )
    }

    /** Convert back to [YtFeedItem] so Discover/Download/Details sheets can consume it natively. */
    fun toFeedItem(track: PlayTogetherTrack): YtFeedItem {
        val art = track.artworkUrl?.takeIf { it.isNotBlank() }
            ?: if (isValidYouTubeId(track.id)) youtubeThumbUrl(track.id) else null
        return YtFeedItem(
            kind = YtFeedItem.KIND_VIDEO,
            id = track.id.trim(),
            title = track.title.ifBlank { track.id },
            subtitleLine1 = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album).joinToString(" • ").ifBlank { null },
            channelTitle = track.artist,
            channelUrl = track.channelUrl,
            duration = formatDurationMs(track.durationMs),
            durationMs = track.durationMs.takeIf { it > 0L },
            thumbs = art?.let { listOf(io.ymusic.dynamicengine.yt.YtThumb(it)) } ?: emptyList(),
        )
    }

    /**
     * If a host is playing a local song (not shareable across devices), searches YT Music for the
     * nearest matching official song track so Transfer Current Music still works cleanly when possible.
     */
    suspend fun resolvePlayableFromLocal(local: PlayableItem.LocalMedia, addedBy: String): Result<PlayTogetherTrack> {
        val query = listOfNotNull(local.title?.takeIf { it.isNotBlank() }, local.artist?.takeIf { it.isNotBlank() }).joinToString(" ").trim()
        if (query.isBlank()) {
            return Result.failure(UnavailableTrackException("Local file has no title to match online"))
        }
        val searchRes = searchCatalog(query, PlayTogetherSearchSource.MUSIC_SONGS)
        val firstMatch = searchRes.tracks.firstOrNull { isValidYouTubeId(it.id) }
            ?: return Result.failure(UnavailableTrackException("Local audio \"${local.title}\" is not available for cross-device Play Together"))
        return resolveAndValidate(firstMatch.copy(addedBy = addedBy))
    }

    /**
     * Validates track ID, checks cached verification, or verifies existence & full metadata via InnerTube `yt/video-details` + `yt/next`.
     */
    suspend fun resolveAndValidate(track: PlayTogetherTrack): Result<PlayTogetherTrack> {
        val cleanId = track.id.trim()
        if (isLocalTrackId(cleanId)) {
            return Result.failure(UnavailableTrackException("Local audio files cannot be streamed across devices"))
        }
        if (!isValidYouTubeId(cleanId)) {
            return Result.failure(UnavailableTrackException("Invalid YouTube track ID: \"$cleanId\""))
        }

        val cached = cacheMutex.withLock { validatedCache[cleanId] }
        if (cached != null) {
            return Result.success(
                cached.copy(
                    addedBy = track.addedBy.ifBlank { cached.addedBy },
                    isAutoRadio = track.isAutoRadio,
                    validated = true,
                ),
            )
        }

        // Fast path if already explicitly resolved & enriched with complete metadata
        if (track.validated && track.title.isNotBlank() && track.title != cleanId && !track.artworkUrl.isNullOrBlank()) {
            cacheTrack(track)
            return Result.success(track)
        }

        return runCatching {
            val details = engines.extractVideoDetails(cleanId, logger?.fork("pt-validate"))
            val resolvedTitle = details.title?.takeIf { it.isNotBlank() }
                ?: track.title.takeIf { it.isNotBlank() && it != cleanId }
            val resolvedArtist = details.channelTitle?.takeIf { it.isNotBlank() }
                ?: track.artist.takeIf { it.isNotBlank() && it != "Unknown Artist" }

            // Fallback check via watchNext when extractVideoDetails didn't return title (e.g. removed/private video)
            var nextDurationMs = track.durationMs
            var finalTitle = resolvedTitle
            var finalArtist = resolvedArtist
            var finalArt = track.artworkUrl?.takeIf { it.isNotBlank() } ?: youtubeThumbUrl(cleanId)

            if (finalTitle == null || finalArtist == null || nextDurationMs <= 0L) {
                val nextPage = runCatching { engines.watchNext(videoId = cleanId, logger = logger?.fork("pt-next")) }.getOrNull()
                val matchingNext = nextPage?.sections
                    ?.flatMap { it.items }
                    ?.firstOrNull { it.kind == YtFeedItem.KIND_VIDEO && it.id == cleanId }
                if (matchingNext != null) {
                    if (finalTitle == null) finalTitle = matchingNext.title.takeIf { it.isNotBlank() }
                    if (finalArtist == null) finalArtist = matchingNext.channelTitle?.takeIf { it.isNotBlank() }
                    if (nextDurationMs <= 0L) {
                        nextDurationMs = matchingNext.durationMs ?: parseDurationMs(matchingNext.duration)
                    }
                    finalArt = bestThumbUrl(matchingNext) ?: finalArt
                }
            }

            if (finalTitle.isNullOrBlank()) {
                throw UnavailableTrackException("Track \"$cleanId\" is unavailable or restricted")
            }

            val enriched = PlayTogetherTrack(
                id = cleanId,
                title = finalTitle,
                artist = finalArtist ?: "YouTube Music",
                album = track.album,
                artworkUrl = finalArt,
                durationMs = nextDurationMs.coerceAtLeast(0L),
                channelUrl = details.channelUrl ?: track.channelUrl,
                isYouTube = true,
                addedBy = track.addedBy.ifBlank { "Host" },
                isAutoRadio = track.isAutoRadio,
                validated = true,
            )
            cacheTrack(enriched)
            runCatching {
                playHistory?.saveYoutubeMetadata(
                    videoId = enriched.id,
                    title = enriched.title,
                    artist = enriched.artist,
                    thumbnailUrl = enriched.artworkUrl,
                    duration = formatDurationMs(enriched.durationMs),
                    durationMs = enriched.durationMs.takeIf { it > 0L },
                    live = false,
                )
            }
            enriched
        }.recoverCatching { err ->
            // If network validation threw because InnerTube returned explicit error vs network offline:
            val msg = err.message.orEmpty()
            if (err is UnavailableTrackException) throw err
            // When engine succeeded far enough to have client-supplied valid ID + real metadata, keep playable rather than failing on details parse edge case:
            if (track.title.isNotBlank() && track.title != cleanId && !msg.contains("unavailable", ignoreCase = true)) {
                val safeFallback = track.copy(
                    id = cleanId,
                    artworkUrl = track.artworkUrl?.takeIf { it.isNotBlank() } ?: youtubeThumbUrl(cleanId),
                    validated = true,
                )
                cacheTrack(safeFallback)
                safeFallback
            } else {
                throw UnavailableTrackException(msg.ifBlank { "Unable to verify track \"$cleanId\"" })
            }
        }
    }

    /** Resolve a pasted YouTube / YT Music link (`youtube.com/watch?v=...`, `youtu.be/...`, `music.youtube.com/...`). */
    suspend fun resolveFromUrl(url: String, addedBy: String): Result<PlayTogetherTrack> = runCatching {
        val target = engines.resolveLink(url.trim(), logger?.fork("pt-link"))
            ?: throw UnavailableTrackException("Could not recognize a playable music link")
        when (target) {
            is YtLinkTarget.Video -> resolveAndValidate(fromYtFeedItem(target.item, addedBy = addedBy, validated = true)).getOrThrow()
            else -> throw UnavailableTrackException("Please enter a song or video link (playlists cannot be added as a single track)")
        }
    }

    /**
     * Searches the centralized PRVN Discover / YT Music / YouTube catalog via [DynamicEngineManager].
     * Empty query loads live Discover & YT Music home picks instead of any hardcoded catalog.
     */
    suspend fun searchCatalog(
        query: String,
        source: PlayTogetherSearchSource = PlayTogetherSearchSource.MUSIC_SONGS,
        filterTokenOrParams: String? = null,
        continuation: String? = null,
        addedBy: String = "Search",
    ): PlayTogetherSearchResult {
        val trimmed = query.trim()

        // URL paste direct support
        if (continuation == null && (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("youtu"))) {
            val resolved = resolveFromUrl(trimmed, addedBy)
            if (resolved.isSuccess) {
                return PlayTogetherSearchResult(tracks = listOf(resolved.getOrThrow()))
            }
        }

        if (trimmed.isEmpty() || source == PlayTogetherSearchSource.DISCOVER_PICKS) {
            return runCatching {
                val page = if (continuation != null) {
                    engines.extractMusicHome(continuation = continuation, logger = logger?.fork("pt-discover"))
                } else {
                    engines.extractMusicHome(logger = logger?.fork("pt-discover"))
                }
                val tracks = extractTracksFromPage(page, addedBy)
                if (tracks.isEmpty() && continuation == null) {
                    val charts = engines.extractMusicCharts(logger = logger?.fork("pt-charts"))
                    return@runCatching PlayTogetherSearchResult(
                        tracks = extractTracksFromPage(charts, addedBy),
                        chips = charts.chips,
                        continuation = charts.continuation,
                    )
                }
                PlayTogetherSearchResult(
                    tracks = tracks,
                    chips = page.chips,
                    continuation = page.continuation,
                )
            }.getOrElse { err ->
                PlayTogetherSearchResult(
                    tracks = emptyList(),
                    errorMessage = err.message ?: "Unable to load Discover feed",
                )
            }
        }

        return runCatching {
            val page: YtFeedPage = when (source) {
                PlayTogetherSearchSource.MUSIC_SONGS -> {
                    val base = if (continuation != null) {
                        engines.musicSearch(continuation = continuation, logger = logger?.fork("pt-msearch"))
                    } else if (!filterTokenOrParams.isNullOrBlank()) {
                        engines.musicSearch(query = trimmed, params = filterTokenOrParams, logger = logger?.fork("pt-msearch"))
                    } else {
                        val initial = engines.musicSearch(query = trimmed, logger = logger?.fork("pt-msearch"))
                        // Prefer the explicit "Songs" filter chip params when available on initial musicSearch!
                        val songsChip = initial.chips.firstOrNull {
                            it.title.equals("Songs", ignoreCase = true) && !it.token.isNullOrBlank()
                        }
                        if (songsChip?.token != null) {
                            engines.musicSearch(query = trimmed, continuation = songsChip.token, logger = logger?.fork("pt-msearch"))
                        } else {
                            initial
                        }
                    }
                    base
                }
                PlayTogetherSearchSource.MUSIC_ALL -> {
                    if (continuation != null) {
                        engines.musicSearch(continuation = continuation, logger = logger?.fork("pt-msearch-all"))
                    } else if (!filterTokenOrParams.isNullOrBlank()) {
                        engines.musicSearch(query = trimmed, continuation = filterTokenOrParams, logger = logger?.fork("pt-msearch-all"))
                    } else {
                        engines.musicSearch(query = trimmed, logger = logger?.fork("pt-msearch-all"))
                    }
                }
                PlayTogetherSearchSource.YOUTUBE_VIDEOS -> {
                    if (continuation != null) {
                        engines.search(continuation = continuation, logger = logger?.fork("pt-ytsearch"))
                    } else {
                        engines.search(query = trimmed, params = filterTokenOrParams, logger = logger?.fork("pt-ytsearch"))
                    }
                }
                PlayTogetherSearchSource.DISCOVER_PICKS -> engines.extractMusicHome(logger = logger?.fork("pt-discover"))
            }
            val tracks = extractTracksFromPage(page, addedBy)
            tracks.take(10).forEach { runCatching { cacheTrack(it) } }
            PlayTogetherSearchResult(
                tracks = tracks,
                chips = page.chips,
                continuation = page.continuation,
            )
        }.getOrElse { err ->
            PlayTogetherSearchResult(
                tracks = emptyList(),
                errorMessage = err.message ?: "Search failed",
            )
        }
    }

    /**
     * Pulls the next real radio track using YouTube's watch-next / queue radio for [seedVideoId],
     * replacing all hardcoded mock catalogs.
     */
    suspend fun nextRadioTrack(
        seedVideoId: String?,
        excludeIds: Set<String>,
    ): PlayTogetherTrack? {
        if (!isValidYouTubeId(seedVideoId)) {
            // Seed from real Discover feed if no active video ID
            val discover = searchCatalog(query = "", source = PlayTogetherSearchSource.DISCOVER_PICKS, addedBy = "Auto-Radio")
            return discover.tracks.firstOrNull { it.id !in excludeIds }
                ?.copy(isAutoRadio = true, validated = true)
        }
        val cleanSeed = seedVideoId!!.trim()
        val page = runCatching { engines.startRadioQueue(cleanSeed, logger?.fork("pt-radio")) }.getOrNull()
            ?: runCatching { engines.watchNext(videoId = cleanSeed, logger = logger?.fork("pt-radio-next")) }.getOrNull()
        val candidates = page?.let { extractTracksFromPage(it, addedBy = "Auto-Radio", isAutoRadio = true) }.orEmpty()
        val fresh = candidates.firstOrNull { it.id != cleanSeed && it.id !in excludeIds }
            ?: candidates.firstOrNull { it.id != cleanSeed }
        if (fresh != null) {
            cacheTrack(fresh)
            return fresh
        }
        val fallbackDiscover = searchCatalog(query = "", source = PlayTogetherSearchSource.DISCOVER_PICKS, addedBy = "Auto-Radio")
        return fallbackDiscover.tracks.firstOrNull { it.id !in excludeIds }?.copy(isAutoRadio = true, validated = true)
    }

    private fun extractTracksFromPage(
        page: YtFeedPage,
        addedBy: String,
        isAutoRadio: Boolean = false,
    ): List<PlayTogetherTrack> {
        val out = LinkedHashMap<String, PlayTogetherTrack>()
        for (section in page.sections) {
            for (item in section.items) {
                if (item.kind != YtFeedItem.KIND_VIDEO || item.live) continue
                if (!isValidYouTubeId(item.id)) continue
                if (!out.containsKey(item.id)) {
                    out[item.id] = fromYtFeedItem(
                        item = item,
                        addedBy = addedBy,
                        isAutoRadio = isAutoRadio,
                        validated = true,
                    )
                }
            }
        }
        return out.values.toList()
    }

    private suspend fun cacheTrack(track: PlayTogetherTrack) {
        if (!isValidYouTubeId(track.id)) return
        cacheMutex.withLock {
            if (validatedCache.size >= maxCacheSize) {
                val oldestKey = validatedCache.keys.firstOrNull()
                if (oldestKey != null) validatedCache.remove(oldestKey)
            }
            validatedCache[track.id] = track
        }
    }
}
