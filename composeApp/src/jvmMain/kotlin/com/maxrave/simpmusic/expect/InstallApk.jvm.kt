package com.maxrave.simpmusic.expect

import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

private const val TAG = "InstallApkJvm"

actual fun installDownloadedApk(filePath: String): Boolean {
    return try {
        val file = File(filePath)
        if (!file.exists()) {
            Logger.e(TAG, "File does not exist: $filePath")
            return false
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
            true
        } else {
            Logger.w(TAG, "Desktop API OPEN not supported")
            false
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to open installer: ${e.message}")
        false
    }
}

actual fun getAppCacheDir(): String {
    return System.getProperty("java.io.tmpdir") ?: "."
}

actual fun checkInstallPermission(): Boolean = true

actual fun openInstallPermissionSettings() = Unit

private fun computeFileSha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(32768)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

actual fun verifyApk(filePath: String, expectedSha256: String?): ApkVerificationResult {
    val file = File(filePath)
    if (!file.exists() || file.length() <= 0L) {
        return ApkVerificationResult(
            isValid = false,
            isSignatureMatch = false,
            isPackageMatch = false,
            isVersionValid = false,
            isSha256Match = false,
            errorMessage = "File missing or empty."
        )
    }

    if (!expectedSha256.isNullOrBlank()) {
        val actualSha = computeFileSha256(file)
        if (!actualSha.equals(expectedSha256.trim(), ignoreCase = true)) {
            return ApkVerificationResult(
                isValid = false,
                isSignatureMatch = false,
                isPackageMatch = false,
                isVersionValid = false,
                isSha256Match = false,
                errorMessage = "Checksum mismatch: expected $expectedSha256, actual $actualSha"
            )
        }
    }

    return ApkVerificationResult(
        isValid = true,
        isSignatureMatch = true,
        isPackageMatch = true,
        isVersionValid = true,
        isSha256Match = true,
    )
}

actual suspend fun downloadFileWithProgress(
    url: String,
    targetPath: String,
    onProgress: (bytesDownloaded: Long, totalBytes: Long, speedBps: Long, etaSeconds: Long) -> Unit,
    isCancelled: () -> Boolean,
    isPaused: () -> Boolean,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val targetFile = File(targetPath)
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }

        var currentUrl = url
        var connection: HttpURLConnection
        var redirectCount = 0

        while (true) {
            connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "PRVN-Music")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308
            ) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (!newUrl.isNullOrEmpty() && redirectCount < 5) {
                    currentUrl = newUrl
                    redirectCount++
                    continue
                }
            }
            break
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            Logger.e(TAG, "Download failed with HTTP status $responseCode")
            connection.disconnect()
            return@withContext false
        }

        val totalBytes = connection.contentLengthLong.let { if (it > 0) it else -1L }
        val input = connection.inputStream.buffered()
        val output = targetFile.outputStream().buffered()

        val buffer = ByteArray(32768)
        var bytesDownloaded = 0L
        var bytesRead: Int

        var lastProgressTime = System.currentTimeMillis()
        var bytesSinceLastCheck = 0L
        var currentSpeed = 0L

        while (true) {
            if (isCancelled()) {
                output.close()
                input.close()
                connection.disconnect()
                targetFile.delete()
                return@withContext false
            }

            while (isPaused()) {
                if (isCancelled()) {
                    output.close()
                    input.close()
                    connection.disconnect()
                    targetFile.delete()
                    return@withContext false
                }
                delay(200)
                lastProgressTime = System.currentTimeMillis()
                bytesSinceLastCheck = 0L
            }

            bytesRead = input.read(buffer)
            if (bytesRead == -1) break

            output.write(buffer, 0, bytesRead)
            bytesDownloaded += bytesRead
            bytesSinceLastCheck += bytesRead

            val now = System.currentTimeMillis()
            val timeDiff = now - lastProgressTime
            if (timeDiff >= 350) {
                currentSpeed = if (timeDiff > 0) (bytesSinceLastCheck * 1000L) / timeDiff else 0L
                lastProgressTime = now
                bytesSinceLastCheck = 0L
                val remainingBytes = if (totalBytes > bytesDownloaded) totalBytes - bytesDownloaded else 0L
                val etaSeconds = if (currentSpeed > 0) remainingBytes / currentSpeed else -1L
                onProgress(bytesDownloaded, totalBytes, currentSpeed, etaSeconds)
            }
        }

        output.flush()
        output.close()
        input.close()
        connection.disconnect()

        onProgress(bytesDownloaded, totalBytes, currentSpeed, 0L)
        Logger.i(TAG, "Download finished successfully: $targetPath ($bytesDownloaded bytes)")
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Download failed with exception: ${e.message}")
        false
    }
}
