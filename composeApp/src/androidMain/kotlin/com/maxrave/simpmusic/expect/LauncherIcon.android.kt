package com.maxrave.simpmusic.expect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.maxrave.logger.Logger
import org.koin.mp.KoinPlatform.getKoin

private const val TAG = "LauncherIconAndroid"

private val ALIAS_MAP = listOf(
    "prvn_logo_01" to "MainActivityAliasLogo01",
    "prvn_logo_02" to "MainActivityAliasLogo02",
    "prvn_logo_03" to "MainActivityAliasLogo03",
    "prvn_logo_04" to "MainActivityAliasLogo04",
    "prvn_logo_05" to "MainActivityAliasLogo05",
    "prvn_logo_06" to "MainActivityAliasLogo06",
    "prvn_logo_07" to "MainActivityAliasLogo07",
    "prvn_logo_08" to "MainActivityAliasLogo08",
    "prvn_logo_09" to "MainActivityAliasLogo09",
    "prvn_logo_10" to "MainActivityAliasLogo10",
    "prvn_logo_11" to "MainActivityAliasLogo11",
    "prvn_logo_12" to "MainActivityAliasLogo12",
    "prvn_logo_14" to "MainActivityAliasLogo14",
    "prvn_logo_15" to "MainActivityAliasLogo15",
    "prvn_logo_16" to "MainActivityAliasLogo16",
    "prvn_logo_17" to "MainActivityAliasLogo17",
)

actual fun setLauncherIcon(logoId: String): Boolean {
    return try {
        val context: Context = getKoin().get()
        val pm = context.packageManager
        val pkg = context.packageName

        val mainActivityComponent = ComponentName(pkg, "com.maxrave.simpmusic.MainActivity")

        if (logoId == "prvn_logo_13") {
            // Default icon (Minimal Neon): enable MainActivity, disable all aliases
            pm.setComponentEnabledSetting(
                mainActivityComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for ((_, alias) in ALIAS_MAP) {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.$alias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } else {
            val targetAlias = ALIAS_MAP.find { it.first == logoId }?.second
            if (targetAlias != null) {
                // Enable target alias first so app icon never disappears
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.$targetAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                // Disable MainActivity
                pm.setComponentEnabledSetting(
                    mainActivityComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                // Disable other aliases
                for ((id, alias) in ALIAS_MAP) {
                    if (id != logoId) {
                        pm.setComponentEnabledSetting(
                            ComponentName(pkg, "$pkg.$alias"),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                    }
                }
            } else {
                Logger.w(TAG, "Unknown logo ID: $logoId, falling back to default")
                return setLauncherIcon("prvn_logo_13")
            }
        }
        Logger.i(TAG, "Switched launcher icon to $logoId")
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to switch launcher icon to $logoId: ${e.message}")
        false
    }
}

actual fun getActiveLauncherIcon(): String {
    return try {
        val context: Context = getKoin().get()
        val pm = context.packageManager
        val pkg = context.packageName

        for ((id, alias) in ALIAS_MAP) {
            val state = pm.getComponentEnabledSetting(ComponentName(pkg, "$pkg.$alias"))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return id
            }
        }
        "prvn_logo_13"
    } catch (e: Exception) {
        "prvn_logo_13"
    }
}
