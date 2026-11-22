package com.maxrave.simpmusic.expect

private var activeJvmLogoId: String = "prvn_logo_13"

actual fun setLauncherIcon(logoId: String): Boolean {
    activeJvmLogoId = logoId
    return true
}

actual fun getActiveLauncherIcon(): String = activeJvmLogoId
