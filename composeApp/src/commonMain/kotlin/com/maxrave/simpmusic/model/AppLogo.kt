package com.maxrave.simpmusic.model

import org.jetbrains.compose.resources.DrawableResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.*

data class AppLogo(
    val id: String,
    val name: String,
    val isSpecial: Boolean = false,
    val isDefault: Boolean = false,
    val drawable: DrawableResource,
)

object AppLogos {
    val ALL: List<AppLogo> = listOf(
        AppLogo(
            id = "prvn_logo_13",
            name = "V1 — Minimal Neon",
            isDefault = true,
            drawable = Res.drawable.prvn_logo_13,
        ),
        AppLogo(
            id = "prvn_logo_14",
            name = "V2 — Audio Wave",
            drawable = Res.drawable.prvn_logo_14,
        ),
        AppLogo(
            id = "prvn_logo_15",
            name = "V3 — Vinyl Classic",
            drawable = Res.drawable.prvn_logo_15,
        ),
        AppLogo(
            id = "prvn_logo_16",
            name = "V4 — Play & Wave",
            drawable = Res.drawable.prvn_logo_16,
        ),
        AppLogo(
            id = "prvn_logo_17",
            name = "V5 — Premium Crown",
            drawable = Res.drawable.prvn_logo_17,
        ),
        AppLogo(
            id = "prvn_logo_01",
            name = "V1 — Aurora Play",
            drawable = Res.drawable.prvn_logo_01,
        ),
        AppLogo(
            id = "prvn_logo_02",
            name = "V2 — Headphone Pulse",
            drawable = Res.drawable.prvn_logo_02,
        ),
        AppLogo(
            id = "prvn_logo_03",
            name = "V3 — Waveform Circle",
            drawable = Res.drawable.prvn_logo_03,
        ),
        AppLogo(
            id = "prvn_logo_04",
            name = "V4 — Vinyl Player",
            drawable = Res.drawable.prvn_logo_04,
        ),
        AppLogo(
            id = "prvn_logo_05",
            name = "V5 — Minimal P",
            drawable = Res.drawable.prvn_logo_05,
        ),
        AppLogo(
            id = "prvn_logo_06",
            name = "V6 — Equalizer",
            drawable = Res.drawable.prvn_logo_06,
        ),
        AppLogo(
            id = "prvn_logo_07",
            name = "V7 — Infinity Sound",
            drawable = Res.drawable.prvn_logo_07,
        ),
        AppLogo(
            id = "prvn_logo_08",
            name = "V8 — Neon Ring",
            drawable = Res.drawable.prvn_logo_08,
        ),
        AppLogo(
            id = "prvn_logo_09",
            name = "V9 — Glassmorphism",
            drawable = Res.drawable.prvn_logo_09,
        ),
        AppLogo(
            id = "prvn_logo_10",
            name = "V10 — Liquid Glass (Special)",
            isSpecial = true,
            drawable = Res.drawable.prvn_logo_10,
        ),
        AppLogo(
            id = "prvn_logo_11",
            name = "V11 — Cosmic",
            drawable = Res.drawable.prvn_logo_11,
        ),
        AppLogo(
            id = "prvn_logo_12",
            name = "V12 — Premium Metal",
            drawable = Res.drawable.prvn_logo_12,
        ),
    )

    const val DEFAULT_LOGO_ID = "prvn_logo_13"

    fun find(id: String): AppLogo = ALL.find { it.id == id } ?: ALL.first { it.isDefault }
}
