package com.maxrave.simpmusic.expect

/**
 * Result of comprehensive APK verification before launching installation.
 */
data class ApkVerificationResult(
    val isValid: Boolean,
    val isSignatureMatch: Boolean,
    val isPackageMatch: Boolean,
    val isVersionValid: Boolean,
    val isSha256Match: Boolean,
    val errorMessage: String? = null,
    val installedCert: String = "",
    val apkCert: String = "",
)

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
 * Checks whether the application has permission to install unknown apps (Android 8.0+).
 */
expect fun checkInstallPermission(): Boolean

/**
 * Navigates the user to system settings to grant unknown app installation permission.
 */
expect fun openInstallPermissionSettings()

/**
 * Performs rigorous security verification of the downloaded APK:
 * - File existence & size
 * - SHA-256 hash match against manifest
 * - Package ID match (com.prvn.music)
 * - VersionCode continuity (>= installed)
 * - Certificate signature extraction & continuity
 */
expect fun verifyApk(filePath: String, expectedSha256: String? = null): ApkVerificationResult

/**
 * Downloads a file from [url] to [targetPath] with progress, speed, ETA, pause and cancellation support.
 */
expect suspend fun downloadFileWithProgress(
    url: String,
    targetPath: String,
    onProgress: (bytesDownloaded: Long, totalBytes: Long, speedBps: Long, etaSeconds: Long) -> Unit,
    isCancelled: () -> Boolean = { false },
    isPaused: () -> Boolean = { false },
): Boolean
