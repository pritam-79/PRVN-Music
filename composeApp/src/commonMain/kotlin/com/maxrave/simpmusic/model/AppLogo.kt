package com.maxrave.simpmusic.model

import org.jetbrains.compose.resources.DrawableResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.*

data class AppLogo(
    val id: String,
    val name: String,
    val sheet: String,
    val isSpecial: Boolean = false,
    val isDefault: Boolean = false,
    val drawable: DrawableResource,
)

object AppLogos {
    const val DEFAULT_LOGO_ID = "prvn_logo_01"

    val ALL: List<AppLogo> = listOf(
        // Concept Sheet 1: Six Original Concepts
        AppLogo(
            id = "prvn_logo_01",
            name = "V1 — Minimal Neon",
            sheet = "Sheet 1 — Original Concepts",
            isDefault = true,
            drawable = Res.drawable.prvn_logo_01,
        ),
        AppLogo(
            id = "prvn_logo_02",
            name = "V2 — Audio Wave",
            sheet = "Sheet 1 — Original Concepts",
            drawable = Res.drawable.prvn_logo_02,
        ),
        AppLogo(
            id = "prvn_logo_03",
            name = "V3 — Vinyl Classic",
            sheet = "Sheet 1 — Original Concepts",
            drawable = Res.drawable.prvn_logo_03,
        ),
        AppLogo(
            id = "prvn_logo_04",
            name = "V4 — Play & Wave",
            sheet = "Sheet 1 — Original Concepts",
            drawable = Res.drawable.prvn_logo_04,
        ),
        AppLogo(
            id = "prvn_logo_05",
            name = "V5 — Premium Crown",
            sheet = "Sheet 1 — Original Concepts",
            drawable = Res.drawable.prvn_logo_05,
        ),
        AppLogo(
            id = "prvn_logo_06",
            name = "V6 — Liquid Glass (Special)",
            sheet = "Sheet 1 — Original Concepts",
            isSpecial = true,
            drawable = Res.drawable.prvn_logo_06,
        ),

        // Concept Sheet 2: Twelve Recent Concepts
        AppLogo(
            id = "prvn_logo_07",
            name = "V1 — Aurora Play",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_07,
        ),
        AppLogo(
            id = "prvn_logo_08",
            name = "V2 — Headphone Pulse",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_08,
        ),
        AppLogo(
            id = "prvn_logo_09",
            name = "V3 — Waveform Circle",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_09,
        ),
        AppLogo(
            id = "prvn_logo_10",
            name = "V4 — Vinyl Player",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_10,
        ),
        AppLogo(
            id = "prvn_logo_11",
            name = "V5 — Minimal P",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_11,
        ),
        AppLogo(
            id = "prvn_logo_12",
            name = "V6 — Equalizer",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_12,
        ),
        AppLogo(
            id = "prvn_logo_13",
            name = "V7 — Infinity Sound",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_13,
        ),
        AppLogo(
            id = "prvn_logo_14",
            name = "V8 — Neon Ring",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_14,
        ),
        AppLogo(
            id = "prvn_logo_15",
            name = "V9 — Glassmorphism",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_15,
        ),
        AppLogo(
            id = "prvn_logo_16",
            name = "V10 — Liquid Glass (Special)",
            sheet = "Sheet 2 — Recent Concepts",
            isSpecial = true,
            drawable = Res.drawable.prvn_logo_16,
        ),
        AppLogo(
            id = "prvn_logo_17",
            name = "V11 — Cosmic",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_17,
        ),
        AppLogo(
            id = "prvn_logo_18",
            name = "V12 — Premium Metal",
            sheet = "Sheet 2 — Recent Concepts",
            drawable = Res.drawable.prvn_logo_18,
        ),
    )

    fun find(id: String): AppLogo {
        // Support legacy v0.15 id mapping
        val normalizedId = when (id) {
            "prvn_logo_13_v14" -> DEFAULT_LOGO_ID
            else -> id
        }
        return ALL.find { it.id == normalizedId } ?: ALL.first { it.isDefault }
    }
}
