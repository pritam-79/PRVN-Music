package com.maxrave.simpmusic.expect

/**
 * Installs the downloaded APK on Android via FileProvider, or opens the installer on Desktop.
 * Returns true if the system installer / file was successfully launched.
 */
expect fun installDownloadedApk(filePath: String): Boolean

/**
 * Returns the application cache directory path for storing temporary downloads.
 */
expect fun getAppCacheDir(): String

/**
 * Downloads a file from [url] to [targetPath] with progress reporting.
 */
expect suspend fun downloadFileWithProgress(
    url: String,
    targetPath: String,
    onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
): Boolean
