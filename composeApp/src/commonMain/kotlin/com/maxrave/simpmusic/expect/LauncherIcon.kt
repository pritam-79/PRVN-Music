package com.maxrave.simpmusic.expect

/**
 * Dynamic launcher icon switcher for PRVN Music 17 Official Logo Collection.
 * On Android, toggles MainActivity and activity aliases via PackageManager.
 * On Desktop / JVM, tracks the current selected logo ID.
 */
expect fun setLauncherIcon(logoId: String): Boolean

expect fun getActiveLauncherIcon(): String
