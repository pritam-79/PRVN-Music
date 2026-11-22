package com.maxrave.simpmusic.expect

private var activeJvmLogoId: String = "prvn_logo_01"

actual fun setLauncherIcon(logoId: String): Boolean {
    activeJvmLogoId = when (logoId) {
        "prvn_logo_13_v14" -> "prvn_logo_01"
        else -> logoId
    }
    return true
}

actual fun getActiveLauncherIcon(): String = activeJvmLogoId
