package com.maxrave.simpmusic.expect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

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

actual fun checkInstallPermission(): Boolean {
    val context: AppCompatActivity = getKoin().get()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

actual fun openInstallPermissionSettings() {
    try {
        val context: AppCompatActivity = getKoin().get()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to open install permission settings: ${e.message}")
    }
}

private fun sha256Hex(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

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

private fun getInstalledSignatures(context: Context): List<String> {
    return try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = pkgInfo.signingInfo
            val signatures = if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else null
            signatures?.map { sha256Hex(it.toByteArray()) } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.map { sha256Hex(it.toByteArray()) } ?: emptyList()
        }
    } catch (e: Exception) {
        Logger.w(TAG, "Failed to get installed signatures: ${e.message}")
        emptyList()
    }
}

private fun getArchiveSignatures(pkgInfo: PackageInfo): List<String> {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            val signatures = if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else null
            signatures?.map { sha256Hex(it.toByteArray()) } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.map { sha256Hex(it.toByteArray()) } ?: emptyList()
        }
    } catch (e: Exception) {
        Logger.w(TAG, "Failed to get archive signatures: ${e.message}")
        emptyList()
    }
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
            errorMessage = "Downloaded update file is missing or empty."
        )
    }

    // 1. SHA-256 verification if expected checksum provided
    var isSha256Match = true
    if (!expectedSha256.isNullOrBlank()) {
        val actualSha256 = computeFileSha256(file)
        if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            isSha256Match = false
            return ApkVerificationResult(
                isValid = false,
                isSignatureMatch = false,
                isPackageMatch = false,
                isVersionValid = false,
                isSha256Match = false,
                errorMessage = "Checksum mismatch: expected $expectedSha256, actual $actualSha256"
            )
        }
    }

    val context: AppCompatActivity = getKoin().get()
    val pm = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES or PackageManager.GET_ACTIVITIES
    }

    val pkgInfo = pm.getPackageArchiveInfo(filePath, flags)
        ?: return ApkVerificationResult(
            isValid = false,
            isSignatureMatch = false,
            isPackageMatch = false,
            isVersionValid = false,
            isSha256Match = isSha256Match,
            errorMessage = "APK parsing failed: file appears corrupt or incomplete."
        )

    // 2. Package ID check
    val isPackageMatch = (pkgInfo.packageName == "com.prvn.music")
    if (!isPackageMatch) {
        return ApkVerificationResult(
            isValid = false,
            isSignatureMatch = false,
            isPackageMatch = false,
            isVersionValid = false,
            isSha256Match = isSha256Match,
            errorMessage = "Package ID mismatch: APK is for '${pkgInfo.packageName}', expected 'com.prvn.music'"
        )
    }

    // 3. Version code check
    val targetVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode.toLong()
    }

    val installedVersionCode = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
    } catch (_: Exception) {
        0L
    }

    val isVersionValid = targetVersionCode >= installedVersionCode
    if (!isVersionValid) {
        return ApkVerificationResult(
            isValid = false,
            isSignatureMatch = false,
            isPackageMatch = true,
            isVersionValid = false,
            isSha256Match = isSha256Match,
            errorMessage = "Version downgrade rejected: installed build is $installedVersionCode, update is $targetVersionCode."
        )
    }

    // 4. Signing certificate verification
    val installedSignatures = getInstalledSignatures(context)
    val apkSignatures = getArchiveSignatures(pkgInfo)

    val installedCert = installedSignatures.firstOrNull() ?: ""
    val apkCert = apkSignatures.firstOrNull() ?: ""

    val isSignatureMatch = if (installedCert.isNotEmpty() && apkCert.isNotEmpty()) {
        installedCert.equals(apkCert, ignoreCase = true)
    } else {
        true
    }

    if (!isSignatureMatch) {
        return ApkVerificationResult(
            isValid = false,
            isSignatureMatch = false,
            isPackageMatch = true,
            isVersionValid = isVersionValid,
            isSha256Match = isSha256Match,
            errorMessage = "Signature mismatch: Update signature ($apkCert) does not match installed signature ($installedCert).",
            installedCert = installedCert,
            apkCert = apkCert,
        )
    }

    return ApkVerificationResult(
        isValid = true,
        isSignatureMatch = true,
        isPackageMatch = true,
        isVersionValid = true,
        isSha256Match = isSha256Match,
        installedCert = installedCert,
        apkCert = apkCert,
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
                Logger.i(TAG, "Download cancelled by user")
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
