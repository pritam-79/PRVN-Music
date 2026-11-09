package io.ymusic.remoteconfig

import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.asyncSource
import okio.Buffer
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer

/**
 * Thrown when a downloaded installer does not match what the service published: it took more
 * bytes than [AppUpdate.size], or its final SHA-256 hash does not match [AppUpdate.sha256].
 */
class DownloadMismatchException(message: String) : Exception(message)

/**
 * Downloads the installer at [url] into [part], resuming from what is already there if the server
 * answers 206 Partial Content. If the server does not accept Range, [part] is deleted and the
 * download starts from the beginning.
 *
 * An installer that is larger than [size] or whose finished hash does not match [sha256] throws
 * [DownloadMismatchException] and [part] is deleted, so the next attempt starts clean. Any other
 * failure leaves [part] for the next attempt to resume. Name [part] after the release's version
 * AND hash: bytes of one release must never be continued with another's.
 */
suspend fun downloadResumable(
    client: KmpHttpClient,
    url: String,
    part: Path,
    fileSystem: FileSystem,
    size: Long,
    sha256: String,
    onProgress: (done: Long) -> Unit,
) {
    var expectedSize = size
    var offset = fileSystem.metadataOrNull(part)?.size ?: 0L
    if (expectedSize > 0L && offset > expectedSize) {
        fileSystem.delete(part)
        offset = 0L
    }
    if (expectedSize > 0L && offset == expectedSize) {
        if (sha256.isEmpty() || sha256Of(fileSystem, part) == sha256) return
        fileSystem.delete(part)
        offset = 0L
    }

    // no-store: the shared client's disk cache must not keep a copy of the installer.
    suspend fun request(from: Long) = client.execute(
        RequestBuilder().url(url).header("Cache-Control", "no-store")
            .apply { if (from > 0) header("Range", "bytes=$from-") }
            .get().build(),
    )

    var response = request(offset)
    if (offset > 0 && response.code != 206) {
        response.close()
        fileSystem.delete(part)
        offset = 0L
        response = request(0L)
    }
    try {
        check(response.isSuccessful) { "HTTP ${response.code} fetching $url" }
        if (expectedSize <= 0L) {
            val cl = response.header("Content-Length")?.toLongOrNull() ?: 0L
            if (cl > 0L) expectedSize = offset + cl
        }
        val body = response.body.asyncSource()
        (if (offset > 0) fileSystem.appendingSink(part) else fileSystem.sink(part)).buffer().use { out ->
            val chunk = Buffer()
            var done = offset
            onProgress(done)
            while (true) {
                val read = body.read(chunk, 64 * 1024L)
                if (read == -1L) break
                out.write(chunk, read)
                done += read
                if (expectedSize > 0L && done > expectedSize) throw DownloadMismatchException("more than the published $expectedSize bytes")
                onProgress(done)
            }
        }
    } catch (mismatch: DownloadMismatchException) {
        fileSystem.delete(part) // after the sink closed: Windows cannot delete an open file
        throw mismatch
    } finally {
        response.close()
    }

    val hashed = sha256Of(fileSystem, part)
    if (sha256.isNotEmpty() && hashed != sha256) {
        fileSystem.delete(part)
        throw DownloadMismatchException("hashed $hashed, published $sha256")
    }
}

private fun sha256Of(fileSystem: FileSystem, file: Path): String {
    val hashing = HashingSink.sha256(blackholeSink())
    hashing.buffer().use { out -> fileSystem.source(file).use { out.writeAll(it) } }
    return hashing.hash.hex()
}
