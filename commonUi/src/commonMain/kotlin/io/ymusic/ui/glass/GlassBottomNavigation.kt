package io.ymusic.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.ui.AppSection
import io.ymusic.ui.DesignColors

/**
 * The 4 main bottom navigation items for PRVN Music:
 * Home (YouTube Music feed), Mix (Listen Together / Radio station), Library, and Search.
 */
enum class GlassNavItem(
    val title: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    MIX("Mix", Icons.Outlined.Radio, Icons.Outlined.Radio),
    LIBRARY("Library", Icons.Outlined.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH("Search", Icons.Outlined.Search, Icons.Filled.Search),
}

/**
 * Premium floating Liquid Glass navigation bar matching the design reference:
 * Translucent frosted glass container with blurred backdrop and glowing crimson/pink pill
 * for the currently active tab.
 */
@Composable
fun GlassBottomNavigation(
    currentSection: AppSection,
    onNavigate: (AppSection) -> Unit,
    onSearchClick: () -> Unit,
    isSearchActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val activeItem = if (isSearchActive) {
        GlassNavItem.SEARCH
    } else {
        when (currentSection) {
            AppSection.YtMusic, AppSection.Discover -> GlassNavItem.HOME
            AppSection.PlayTogether -> GlassNavItem.MIX
            AppSection.Library -> GlassNavItem.LIBRARY
            else -> null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Floating glass capsule container
        Row(
            modifier = Modifier
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(36.dp), spotColor = Color(0x66E11D48))
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE61A121E),
                            Color(0xD90E0B14),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x4DFFFFFF),
                            Color(0x1AFFFFFF),
                        ),
                    ),
                    shape = RoundedCornerShape(36.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassNavItem.values().forEach { item ->
                val isSelected = (item == activeItem)
                GlassNavTab(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        when (item) {
                            GlassNavItem.HOME -> onNavigate(AppSection.YtMusic)
                            GlassNavItem.MIX -> onNavigate(AppSection.PlayTogether)
                            GlassNavItem.LIBRARY -> onNavigate(AppSection.Library)
                            GlassNavItem.SEARCH -> onSearchClick()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GlassNavTab(
    item: GlassNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFE11D48),
                                    Color(0xFFBE123C),
                                    Color(0xFF9F1239),
                                ),
                            ),
                            shape = RoundedCornerShape(28.dp),
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x80FFFFFF),
                                    Color(0x20FFFFFF),
                                ),
                            ),
                            shape = RoundedCornerShape(28.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isSelected) item.activeIcon else item.icon,
                contentDescription = item.title,
                tint = if (isSelected) Color.White else Color(0xB3FFFFFF),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0x99FFFFFF),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
