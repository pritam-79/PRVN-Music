package io.ymusic.ui.lyrics

import io.ymusic.ui.percentEncode
import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.create
import kmphttp.utf8String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

data class LrcLine(
    val timeMs: Long,
    val text: String,
)

data class LrcSongLyrics(
    val id: Long? = null,
    val trackName: String,
    val artistName: String,
    val plainLyrics: String? = null,
    val syncedLines: List<LrcLine> = emptyList(),
)

object LrcLibClient {

    private val client = KmpHttpClient.create {
        defaultExecuteTimeout = 8.seconds
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val LRC_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")

    fun parseLrc(rawLrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (rawLine in rawLrc.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val match = LRC_REGEX.matchEntire(trimmed) ?: continue
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val millis = when (fracStr.length) {
                1 -> fracStr.toLong() * 100L
                2 -> fracStr.toLong() * 10L
                3 -> fracStr.toLong()
                else -> 0L
            }
            val timeMs = minutes * 60_000L + seconds * 1_000L + millis
            val text = match.groupValues[4].trim()
            lines.add(LrcLine(timeMs, text))
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\(official\s*(music)?\s*(video|audio|lyric\s*video)?\)"""), "")
            .replace(Regex("""(?i)\[official\s*(music)?\s*(video|audio|lyric\s*video)?\]"""), "")
            .replace(Regex("""(?i)\(from\s*".*?"\)"""), "")
            .replace(Regex("""(?i)\[from\s*".*?"\]"""), "")
            .replace(Regex("""(?i)\(feat\..*?\)"""), "")
            .replace(Regex("""(?i)\[feat\..*?\]"""), "")
            .replace(Regex("""(?i)\(ft\..*?\)"""), "")
            .replace(Regex("""(?i)\[ft\..*?\]"""), "")
            .replace(Regex("""(?i)\[4k\]"""), "")
            .replace(Regex("""(?i)\[hd\]"""), "")
            .replace(Regex("""(?i)\|.*"""), "")
            .trim()
    }

    private fun cleanArtist(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)\s*vevo$"""), "")
            .trim()
    }

    suspend fun fetchLyrics(
        rawTitle: String,
        rawArtist: String,
        durationSeconds: Int? = null,
    ): LrcSongLyrics? = withContext(Dispatchers.Default) {
        val title = cleanTitle(rawTitle)
        val artist = cleanArtist(rawArtist)

        // 1. Try exact match /api/get
        try {
            var getUrl = "https://lrclib.net/api/get?track_name=${percentEncode(title)}&artist_name=${percentEncode(artist)}"
            if (durationSeconds != null && durationSeconds > 0) {
                getUrl += "&duration=$durationSeconds"
            }
            val req = RequestBuilder().url(getUrl).header("User-Agent", "PRVNMusic/0.0.7").get().build()
            val resp = client.execute(req)
            if (resp.isSuccessful) {
                val body = resp.body.utf8String()
                val parsed = parseJsonResponse(body, title, artist)
                if (parsed != null && (parsed.syncedLines.isNotEmpty() || !parsed.plainLyrics.isNullOrBlank())) {
                    return@withContext parsed
                }
            }
        } catch (_: Exception) {}

        // 2. Try search /api/search?q=
        try {
            val query = "$title $artist".trim()
            val searchUrl = "https://lrclib.net/api/search?q=${percentEncode(query)}"
            val req = RequestBuilder().url(searchUrl).header("User-Agent", "PRVNMusic/0.0.7").get().build()
            val resp = client.execute(req)
            if (resp.isSuccessful) {
                val body = resp.body.utf8String()
                val root = json.parseToJsonElement(body).jsonArray
                var fallbackPlain: LrcSongLyrics? = null
                for (elem in root) {
                    val obj = elem.jsonObject
                    val syncedStr = obj["syncedLyrics"]?.jsonPrimitive?.content
                    val plainStr = obj["plainLyrics"]?.jsonPrimitive?.content
                    val tName = obj["trackName"]?.jsonPrimitive?.content ?: title
                    val aName = obj["artistName"]?.jsonPrimitive?.content ?: artist
                    val idVal = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()

                    if (!syncedStr.isNullOrBlank()) {
                        val lines = parseLrc(syncedStr)
                        if (lines.isNotEmpty()) {
                            return@withContext LrcSongLyrics(
                                id = idVal,
                                trackName = tName,
                                artistName = aName,
                                plainLyrics = plainStr,
                                syncedLines = lines,
                            )
                        }
                    } else if (fallbackPlain == null && !plainStr.isNullOrBlank()) {
                        fallbackPlain = LrcSongLyrics(
                            id = idVal,
                            trackName = tName,
                            artistName = aName,
                            plainLyrics = plainStr,
                            syncedLines = emptyList(),
                        )
                    }
                }
                if (fallbackPlain != null) return@withContext fallbackPlain
            }
        } catch (_: Exception) {}

        null
    }

    private fun parseJsonResponse(body: String, fallbackTitle: String, fallbackArtist: String): LrcSongLyrics? {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val syncedStr = obj["syncedLyrics"]?.jsonPrimitive?.content
            val plainStr = obj["plainLyrics"]?.jsonPrimitive?.content
            val tName = obj["trackName"]?.jsonPrimitive?.content ?: fallbackTitle
            val aName = obj["artistName"]?.jsonPrimitive?.content ?: fallbackArtist
            val idVal = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()

            val lines = if (!syncedStr.isNullOrBlank()) parseLrc(syncedStr) else emptyList()
            LrcSongLyrics(
                id = idVal,
                trackName = tName,
                artistName = aName,
                plainLyrics = plainStr,
                syncedLines = lines,
            )
        } catch (_: Exception) {
            null
        }
    }
}
