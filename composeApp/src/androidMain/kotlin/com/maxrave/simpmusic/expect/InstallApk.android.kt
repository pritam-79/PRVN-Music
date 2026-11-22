package com.maxrave.simpmusic.expect

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

private const val TAG = "InstallApkAndroid"

actual fun installDownloadedApk(filePath: String): Boolean {
    return try {
        val context: AppCompatActivity = getKoin().get()
        val file = File(filePath)
        if (!file.exists()) {
            Logger.e(TAG, "File does not exist at $filePath")
            return false
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        Logger.i(TAG, "Launching Android package installer for $filePath")
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to launch package installer: ${e.message}")
        false
    }
}

actual fun getAppCacheDir(): String {
    val context: AppCompatActivity = getKoin().get()
    val cacheDir = context.externalCacheDir ?: context.cacheDir
    return cacheDir.absolutePath
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
