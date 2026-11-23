package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.maxrave.simpmusic.expect.openUrl
import com.maxrave.simpmusic.ui.icon.Headphones
import com.maxrave.simpmusic.ui.icon.GraphicEq
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Sync
import com.maxrave.simpmusic.ui.icon.TipsAndUpdates
import com.maxrave.simpmusic.ui.icon.Update
import com.maxrave.simpmusic.ui.theme.typo

private val BrandPurple = Color(0xFFA855F7)
private val BrandCyan = Color(0xFF06B6D4)
private val DarkDialogBg = Color(0xFF131722)
private val CardBg = Color(0xFF1E2433)
private val HighContrastTextPrimary = Color.White
private val HighContrastTextSecondary = Color(0xFFE2E8F0)
private val HighContrastTextMuted = Color(0xFF94A3B8)

@Composable
fun ReleaseUpdatesDialog(
    versionName: String = "0.16.2",
    onDismiss: () -> Unit,
) {
    AlertDialog(
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        onDismissRequest = onDismiss,
        containerColor = DarkDialogBg,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(BrandPurple, BrandCyan))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SimpIcons.TipsAndUpdates,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "What's New in PRVN Music",
                    style = typo().titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastTextPrimary,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandPurple.copy(alpha = 0.2f))
                        .border(1.dp, BrandPurple.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Version v$versionName",
                        style = typo().labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandCyan,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReleaseHighlightCard(
                    icon = SimpIcons.Headphones,
                    title = "Group Listening & Room Permissions",
                    description = "Listen Together now offers 3 room permission tiers (Host Only, Everyone, Approved Members) with sub-500ms real-time sync and zero audio drops.",
                    tint = BrandCyan,
                )

                ReleaseHighlightCard(
                    icon = SimpIcons.GraphicEq,
                    title = "18 Pixel-Accurate PRVN Logos",
                    description = "Full 18-icon suite extracted directly from official reference artwork with real-time Android launcher dynamic icon switching across all devices.",
                    tint = BrandPurple,
                )

                ReleaseHighlightCard(
                    icon = SimpIcons.Sync,
                    title = "Universal Cloud Synchronization",
                    description = "Seamlessly sync your playlists, favorites, listening history, queue, and playback state across Android, iPhone, and Desktop.",
                    tint = Color(0xFF38BDF8),
                )

                ReleaseHighlightCard(
                    icon = SimpIcons.Update,
                    title = "PRVN Music for iPhone v0.01",
                    description = "Initial iOS preview with native background playback, system lock-screen media controls, and seamless headphone disconnect handling.",
                    tint = Color(0xFFA78BFA),
                )

                ReleaseHighlightCard(
                    icon = SimpIcons.TipsAndUpdates,
                    title = "Clean Experience & Native Updater",
                    description = "Permanently purged all third-party star popups and external banners. Official developer: PRVN Music — Developed by Pritam.",
                    tint = Color(0xFF10B981),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPurple,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Got It!", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    openUrl("https://github.com/pritam-79/PRVN-Music/releases/tag/v$versionName")
                },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyan.copy(alpha = 0.5f)),
            ) {
                Text("Release Notes", color = BrandCyan, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun ReleaseHighlightCard(
    icon: ImageVector,
    title: String,
    description: String,
    tint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo().bodyMedium,
                fontWeight = FontWeight.Bold,
                color = HighContrastTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = typo().bodySmall,
                color = HighContrastTextSecondary,
                lineHeight = 16.sp,
            )
        }
    }
}
