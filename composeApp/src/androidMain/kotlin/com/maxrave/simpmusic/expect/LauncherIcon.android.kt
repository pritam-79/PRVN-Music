package com.maxrave.simpmusic.expect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.maxrave.logger.Logger
import org.koin.mp.KoinPlatform.getKoin

private const val TAG = "LauncherIconAndroid"

/**
 * Authoritative mapping for all 18 PRVN Music Official Reference Logos
 * to their respective Android launcher Activity Aliases.
 */
private val ALIAS_MAP = mapOf(
    "prvn_logo_01" to "MainActivityAliasLogo01", // Default: V1 — Minimal Neon
    "prvn_logo_02" to "MainActivityAliasLogo02", // V2 — Audio Wave
    "prvn_logo_03" to "MainActivityAliasLogo03", // V3 — Vinyl Classic
    "prvn_logo_04" to "MainActivityAliasLogo04", // V4 — Play & Wave
    "prvn_logo_05" to "MainActivityAliasLogo05", // V5 — Premium Crown
    "prvn_logo_06" to "MainActivityAliasLogo06", // V6 — Liquid Glass (Special) [Sheet 1]
    "prvn_logo_07" to "MainActivityAliasLogo07", // V1 — Aurora Play
    "prvn_logo_08" to "MainActivityAliasLogo08", // V2 — Headphone Pulse
    "prvn_logo_09" to "MainActivityAliasLogo09", // V3 — Waveform Circle
    "prvn_logo_10" to "MainActivityAliasLogo10", // V4 — Vinyl Player
    "prvn_logo_11" to "MainActivityAliasLogo11", // V5 — Minimal P
    "prvn_logo_12" to "MainActivityAliasLogo12", // V6 — Equalizer
    "prvn_logo_13" to "MainActivityAliasLogo13", // V7 — Infinity Sound
    "prvn_logo_14" to "MainActivityAliasLogo14", // V8 — Neon Ring
    "prvn_logo_15" to "MainActivityAliasLogo15", // V9 — Glassmorphism
    "prvn_logo_16" to "MainActivityAliasLogo16", // V10 — Liquid Glass (Special) [Sheet 2]
    "prvn_logo_17" to "MainActivityAliasLogo17", // V11 — Cosmic
    "prvn_logo_18" to "MainActivityAliasLogo18", // V12 — Premium Metal
)

actual fun setLauncherIcon(logoId: String): Boolean {
    return try {
        val context: Context = getKoin().get()
        val pm = context.packageManager
        val pkg = context.packageName // "com.prvn.music"

        // Map v0.15 legacy ID if encountered
        val effectiveId = when (logoId) {
            "prvn_logo_13_v14" -> "prvn_logo_01"
            else -> logoId
        }

        val targetAlias = ALIAS_MAP[effectiveId] ?: ALIAS_MAP["prvn_logo_01"]!!
        val targetComponent = ComponentName(pkg, "$pkg.$targetAlias")

        Logger.i(TAG, "Requesting launcher icon switch to $effectiveId ($targetAlias)")

        // 1. Enable target alias FIRST so the launcher icon never disappears from home screen/drawer
        pm.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        // 2. Disable ONLY the previously enabled alias(es) to avoid flooding the launcher with package changed broadcasts
        for ((_, alias) in ALIAS_MAP) {
            if (alias != targetAlias) {
                val comp = ComponentName(pkg, "$pkg.$alias")
                val state = pm.getComponentEnabledSetting(comp)
                if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }

        // 3. Ensure MainActivity itself is in DEFAULT state (never disabled, preserving deep links and services)
        val mainActivityComponent = ComponentName(pkg, "com.maxrave.simpmusic.MainActivity")
        val mainState = pm.getComponentEnabledSetting(mainActivityComponent)
        if (mainState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            pm.setComponentEnabledSetting(
                mainActivityComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }

        Logger.i(TAG, "Successfully updated PackageManager component state to $targetAlias")
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to switch launcher icon to $logoId: ${e.message}", e)
        false
    }
}

actual fun getActiveLauncherIcon(): String {
    return try {
        val context: Context = getKoin().get()
        val pm = context.packageManager
        val pkg = context.packageName

        for ((id, alias) in ALIAS_MAP) {
            val comp = ComponentName(pkg, "$pkg.$alias")
            val state = pm.getComponentEnabledSetting(comp)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return id
            }
        }
        // If no alias explicitly enabled (e.g. fresh install), Logo01 is enabled by default in manifest
        "prvn_logo_01"
    } catch (e: Exception) {
        Logger.e(TAG, "Error querying active launcher icon: ${e.message}")
        "prvn_logo_01"
    }
}
