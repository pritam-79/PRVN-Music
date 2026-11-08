package io.ymusic.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Manages dynamic launcher icon switching via Android activity-alias components.
 * Only the active alias is enabled; others are disabled with DONT_KILL_APP to avoid
 * process termination during active playback.
 */
object AppIconManager {
    enum class AppIcon(val key: String, val aliasName: String) {
        ORIGINAL("original", "io.ymusic.android.MainActivityOriginal"),
        PURPLE("purple", "io.ymusic.android.MainActivityPurple"),
        NEON("neon", "io.ymusic.android.MainActivityNeon"),
        MINIMAL("minimal", "io.ymusic.android.MainActivityMinimal"),
        DJ("dj", "io.ymusic.android.MainActivityDj");

        companion object {
            fun fromKey(key: String): AppIcon = entries.firstOrNull { it.key == key } ?: ORIGINAL
        }
    }

    fun setLauncherIcon(context: Context, iconKey: String) {
        val targetIcon = AppIcon.fromKey(iconKey)
        val pm = context.packageManager
        val packageName = context.packageName

        for (icon in AppIcon.entries) {
            val componentName = ComponentName(packageName, icon.aliasName)
            val currentState = pm.getComponentEnabledSetting(componentName)
            val desiredState = if (icon == targetIcon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (currentState != desiredState) {
                try {
                    pm.setComponentEnabledSetting(
                        componentName,
                        desiredState,
                        PackageManager.DONT_KILL_APP,
                    )
                    Log.i("AppIconManager", "Switched ${icon.aliasName} to $desiredState")
                } catch (t: Throwable) {
                    Log.w("AppIconManager", "Failed to set state for ${icon.aliasName}", t)
                }
            }
        }
    }
}
