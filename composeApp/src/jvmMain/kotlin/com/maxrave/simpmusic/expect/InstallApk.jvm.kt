package com.maxrave.simpmusic.expect

import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

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

actual suspend fun downloadFileWithProgress(
    url: String,
    targetPath: String,
    onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
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

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            bytesDownloaded += bytesRead
            onProgress(bytesDownloaded, totalBytes)
        }

        output.flush()
        output.close()
        input.close()
        connection.disconnect()
        Logger.i(TAG, "Download finished successfully: $targetPath ($bytesDownloaded bytes)")
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Download failed with exception: ${e.message}")
        false
    }
}
