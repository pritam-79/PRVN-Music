package io.ymusic.remoteconfig

import kmpcommon.io.buffer
import kmpcommon.log.Logger
import kmpcommon.log.info
import kmphttp.HttpStatusException
import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.asyncSource
import kmphttp.json.JsonReader
import kmphttp.utf8String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import projectcommon.json.long
import projectcommon.json.objects
import projectcommon.json.string
import projectcommon.json.stringOrNull

/**
 * An [ApiService] implementation backed directly by public GitHub Releases.
 * Enables in-app update checks, changelog retrieval, and APK downloads without
 * requiring a private proprietary remote-config backend server.
 */
class GitHubApiService(
    private val client: KmpHttpClient,
    private val repoOwner: String = "pritam-79",
    private val repoName: String = "PRVN-Music",
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logger: Logger? = null,
) : ApiService {

    private val log get() = logger?.fork("github-api")

    override suspend fun getConfig(): RemoteConfigData = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = RequestBuilder()
            .url(url)
            .header("User-Agent", "PRVN-Music-App")
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        val response = client.execute(request)
        if (!response.isSuccessful) {
            log?.info("GitHub release check returned HTTP ${response.code}")
            throw HttpStatusException(response.code)
        }

        val json = JsonReader(response.body.asyncSource().buffer()).readObject()
        val tagName = json.string("tag_name")
        val versionName = tagName.removePrefix("v").removePrefix("V")
        val body = json.stringOrNull("body").orEmpty()
        val htmlUrl = json.string("html_url")
        val assets = json.objects("assets")

        val apkAsset = assets.firstOrNull {
            val name = it.stringOrNull("name").orEmpty()
            name.endsWith("-arm64.apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)
        }

        val appUpdate = if (apkAsset != null) {
            val codeRegex = Regex("""(?:versionCode|Build|Version Code)[:\s]+['"`]?(\d+)['"`]?""", RegexOption.IGNORE_CASE)
            val parsedCode = codeRegex.find(body)?.groupValues?.get(1)?.toIntOrNull()
            val parts = versionName.split('.')
            val derivedCode = when {
                parts.size == 3 -> (parts[0].toIntOrNull() ?: 0) * 10000 + (parts[1].toIntOrNull() ?: 0) * 100 + (parts[2].toIntOrNull() ?: 0)
                parts.size == 2 -> (parts[0].toIntOrNull() ?: 0) * 100 + (parts[1].toIntOrNull() ?: 0)
                else -> versionName.toIntOrNull() ?: 0
            }
            val versionCode = parsedCode ?: derivedCode

            // Attempt to resolve SHA-256 hash from CHECKSUMS.txt asset if bundled
            var sha256 = ""
            val checksumAsset = assets.firstOrNull {
                it.stringOrNull("name")?.equals("CHECKSUMS.txt", ignoreCase = true) == true
            }
            if (checksumAsset != null) {
                try {
                    val csUrl = checksumAsset.string("browser_download_url")
                    val csReq = RequestBuilder()
                        .url(csUrl)
                        .header("User-Agent", "PRVN-Music-App")
                        .get()
                        .build()
                    val csResp = client.execute(csReq)
                    if (csResp.isSuccessful) {
                        val csText = csResp.body.utf8String()
                        val apkName = apkAsset.string("name")
                        val line = csText.lines().firstOrNull {
                            it.contains(apkName, ignoreCase = true) || it.contains(".apk", ignoreCase = true)
                        }
                        if (line != null) {
                            sha256 = line.trim().split(Regex("""\s+""")).firstOrNull().orEmpty()
                        }
                    }
                } catch (e: Exception) {
                    log?.info("Could not retrieve CHECKSUMS.txt: ${e.message}")
                }
            }

            // Fallback: extract SHA-256 from release body notes
            if (sha256.isBlank()) {
                val shaRegex = Regex("""[a-fA-F0-9]{64}""")
                sha256 = shaRegex.find(body)?.value ?: ""
            }

            AppUpdate(
                versionCode = versionCode,
                versionName = versionName,
                notes = body,
                url = apkAsset.string("browser_download_url"),
                size = apkAsset.long("size"),
                sha256 = sha256.lowercase(),
                signature = "",
                changelogUrl = htmlUrl,
                downloadPage = htmlUrl,
                installInApp = true,
            )
        } else {
            null
        }

        val features = Features(
            JsonObject(
                mapOf(
                    "downloadPageUrl" to JsonPrimitive(htmlUrl),
                    "changelogUrl" to JsonPrimitive(htmlUrl),
                )
            )
        )

        RemoteConfigData(
            updatedAt = now(),
            appUpdate = appUpdate,
            features = features,
        )
    }

    override suspend fun pulse(events: List<PulseEvent>) {}
    override suspend fun openDeviceLink(): String = throw UnsupportedOperationException("Device link not supported via GitHub API")
    override suspend fun putDeviceLink(id: String, payload: String): Boolean = false
    override suspend fun takeDeviceLink(id: String): String? = null
    override suspend fun closeDeviceLink(id: String) {}
    override suspend fun getSkipSegments(videoId: String): List<SkipSegment> = emptyList()
}
