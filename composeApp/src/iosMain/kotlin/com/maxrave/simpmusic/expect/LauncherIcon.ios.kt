package com.maxrave.simpmusic.expect

private var activeIosLogoId: String = "prvn_logo_13"

actual fun setLauncherIcon(logoId: String): Boolean {
    activeIosLogoId = logoId
    return true
}

actual fun getActiveLauncherIcon(): String = activeIosLogoId
